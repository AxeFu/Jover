package ru.axefu.jover.annotation.processor;

import com.sun.source.tree.*;
import com.sun.source.util.Trees;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.comp.Attr;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;

import static com.sun.tools.javac.tree.JCTree.*;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.tools.Diagnostic;
import java.lang.reflect.Method;
import java.util.Set;

@SupportedAnnotationTypes("*")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class OperatorProcessor extends AbstractProcessor {

    private Trees trees;
    private TreeMaker make;
    private Names names;
    private Attr attr;
    private Types types;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        ProcessingEnvironment unwrappedEnv = jbUnwrap(ProcessingEnvironment.class, processingEnv);
        trees = Trees.instance(unwrappedEnv);
        Context context = ((JavacProcessingEnvironment)unwrappedEnv).getContext();
        make = TreeMaker.instance(context);
        names = Names.instance(context);
        attr = Attr.instance(context);
        types = Types.instance(context);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (!roundEnv.processingOver()) {
            for (Element element : roundEnv.getRootElements()) {
                if (element.getKind() == ElementKind.CLASS) {
                    attr.attribClass(null, (Symbol.ClassSymbol) element);
                    JCTree tree = (JCTree) trees.getTree(element);
                    tree.accept(new TreeTranslator() {
                        @Override
                        public void visitAssignop(JCAssignOp tree) {
                            super.visitAssignop(tree);
                            if (tree.lhs == null || tree.lhs.type == null) return;
                            if (tree.lhs.type.getKind() != TypeKind.DECLARED) return;
                            if (tree.lhs.type.toString().equals("java.lang.String")) return;

                            JCMethodInvocation methodCall = createMethod(tree.lhs, getMethodName(tree.getTag()), tree.rhs);
                            if (methodCall == null) return;
                            result = make.Assign(tree.lhs, methodCall);
                            result.type = methodCall.type;
                        }

                        @Override
                        public void visitBinary(JCBinary tree) {
                            super.visitBinary(tree);
                            if (tree.lhs == null || tree.lhs.type == null) return;
                            if (tree.lhs.type.getKind() != TypeKind.DECLARED) return;
                            if (tree.lhs.type.toString().equals("java.lang.String")) return;
                            result = createMethod(tree.lhs, getMethodName(tree.getTag()), tree.rhs);
                        }
                    });
                }
            }
            return true;
        }
        return false;
    }

    private JCMethodInvocation createMethod(JCExpression lhs, String methodName, JCExpression rhs) {
        Symbol.MethodSymbol methodSymbol = findMethod(lhs.type, names.fromString(methodName), rhs.type);
        if (methodSymbol == null) return null;
        JCFieldAccess select = make.Select(lhs, names.fromString(methodName));
        select.sym = methodSymbol;
        select.type = methodSymbol.type;
        JCMethodInvocation result = make.Apply(List.nil(), select, List.of(rhs));
        result.type = select.type.getReturnType();
        return result;
    }

    private Symbol.MethodSymbol findMethod(Type recieverType, Name methodName, Type argumentType) {
        if (methodName.isEmpty()) return null;
        Symbol.ClassSymbol clazz = (Symbol.ClassSymbol) recieverType.tsym;
        for (Symbol symbol : clazz.members().getElementsByName(methodName)) {
            if (!(symbol instanceof Symbol.MethodSymbol)) {
                continue;
            }

            Symbol.MethodSymbol method = (Symbol.MethodSymbol) symbol;
            Type.MethodType mt = (Type.MethodType) method.type;
            if (mt.argtypes.size() != 1) {
                continue;
            }

            Type parameterType = mt.argtypes.head;

            if (types.isAssignable(argumentType, parameterType)) {
                return method;
            }
        }
        return null;
    }

    private String getMethodName(Tag operation) {
        switch (operation) {
            case PLUS_ASG:
            case PLUS: return "add";
            case MINUS_ASG:
            case MINUS: return "subtract";
            case MUL_ASG:
            case MUL: return "multiply";
            case DIV_ASG:
            case DIV: return "divide";
        }
        return "";
    }

    private static <T> T jbUnwrap(@SuppressWarnings("SpellCheckingInspection") Class<? extends T> iface, T wrapper) {
        T unwrapped = null;
        try {
            final Class<?> apiWrappers = wrapper.getClass().getClassLoader().loadClass("org.jetbrains.jps.javac.APIWrappers");
            final Method unwrapMethod = apiWrappers.getDeclaredMethod("unwrap", Class.class, Object.class);
            unwrapped = iface.cast(unwrapMethod.invoke(null, iface, wrapper));
        }
        catch (Throwable ignored) {}
        return unwrapped != null ? unwrapped : wrapper;
    }
}
