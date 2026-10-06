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
                        public void visitBinary(JCBinary jcBinary) {
                            super.visitBinary(jcBinary);
                            if (jcBinary.lhs == null || jcBinary.lhs.type == null) return;
                            if (jcBinary.lhs.type.getKind() != TypeKind.DECLARED) return;
                            if (jcBinary.lhs.type.toString().equals("java.lang.String")) return;

                            String method = getMethodName(jcBinary.getTag());
                            Symbol.MethodSymbol methodSymbol = findMethod(
                                    jcBinary.lhs.type,
                                    names.fromString(method),
                                    jcBinary.rhs.type
                            );

                            if (methodSymbol == null)
                                return;

                            JCFieldAccess select = make.Select(jcBinary.lhs, names.fromString(method));
                            select.sym = methodSymbol;
                            select.type = methodSymbol.type;

                            result = make.Apply(List.nil(), select, List.of(jcBinary.rhs));
                            result.type = select.type.getReturnType();
                        }
                    });
                }
            }
            return true;
        }
        return false;
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
            case PLUS: return "add";
            case MINUS: return "subtract";
            case MUL: return "multiply";
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
