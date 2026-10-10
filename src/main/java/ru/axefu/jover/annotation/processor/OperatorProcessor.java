package ru.axefu.jover.annotation.processor;

import com.sun.source.util.Trees;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.comp.Attr;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.*;

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
public class OperatorProcessor extends AbstractProcessor {

    private Trees trees;
    private TreeMaker make;
    private Names names;
    private Types types;
    private Attr attr;
    private Log log;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        ProcessingEnvironment unwrappedEnv = jbUnwrap(ProcessingEnvironment.class, processingEnv);
        trees = Trees.instance(unwrappedEnv);
        Context context = ((JavacProcessingEnvironment)unwrappedEnv).getContext();
        make = TreeMaker.instance(context);
        names = Names.instance(context);
        types = Types.instance(context);
        attr = Attr.instance(context);
        log = Log.instance(context);
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (!roundEnv.processingOver()) {
            for (Element element : roundEnv.getRootElements()) {
                if (element.getKind() == ElementKind.CLASS) {
                    JCTree tree = (JCTree) trees.getTree(element);
                    attribClass(element);
                    tree.accept(new TreeTranslator() {
                        @Override
                        public void visitAssignop(JCAssignOp jcAssignOp) {
                            super.visitAssignop(jcAssignOp);
                            if (isPrimitive(jcAssignOp.lhs)) return;

                            JCMethodInvocation methodCall = createMethod(jcAssignOp.lhs, getMethodName(jcAssignOp.getTag()), jcAssignOp.rhs);
                            if (methodCall == null) return;
                            result = make.Assign(jcAssignOp.lhs, methodCall);
                            result.type = methodCall.type;
                        }

                        @Override
                        public void visitBinary(JCBinary jcBinary) {
                            super.visitBinary(jcBinary);
                            if (isPrimitive(jcBinary.lhs)) return;

                            JCMethodInvocation methodCall = createMethod(jcBinary.lhs, getMethodName(jcBinary.getTag()), jcBinary.rhs);
                            if (methodCall == null) return;
                            result = methodCall;
                        }
                    });
                }
            }
            return true;
        }
        return false;
    }

    private void attribClass(Element element) {
        Log.DiscardDiagnosticHandler handler = log.new DiscardDiagnosticHandler();
        try {
            attr.attribClass((JCTree) trees.getTree(element), (Symbol.ClassSymbol) element);
        } finally {
            log.popDiagnosticHandler(handler);
        }
    }

    private boolean isPrimitive(JCExpression expression) {
        return expression == null || expression.type == null ||
                expression.type.getKind() != TypeKind.DECLARED ||
                expression.type.toString().equals("java.lang.String");
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
        for (Symbol symbol : clazz.members().getSymbolsByName(methodName)) {
            if (!(symbol instanceof Symbol.MethodSymbol method)) {
                continue;
            }

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
        return switch (operation) {
            case PLUS_ASG, PLUS -> "add";
            case MINUS_ASG, MINUS -> "subtract";
            case MUL_ASG, MUL -> "multiply";
            case DIV_ASG, DIV -> "divide";
            default -> "";
        };
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
