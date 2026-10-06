package ru.axefu.jover.annotation.processor;

import com.sun.source.tree.*;
import com.sun.source.util.Trees;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.comp.Attr;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Names;

import static com.sun.tools.javac.tree.JCTree.*;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
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

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        ProcessingEnvironment unwrappedEnv = jbUnwrap(ProcessingEnvironment.class, processingEnv);
        trees = Trees.instance(unwrappedEnv);
        Context context = ((JavacProcessingEnvironment)unwrappedEnv).getContext();
        make = TreeMaker.instance(context);
        names = Names.instance(context);
        attr = Attr.instance(context);
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
                            if (jcBinary.lhs.type.getKind() == TypeKind.DECLARED && !jcBinary.lhs.type.toString().equals("java.lang.String")) {
                                String method = "";
                                switch (jcBinary.getTag()) {
                                    case PLUS: method = "add"; break;
                                    case MINUS: method = "subtract"; break;
                                    case MUL: method = "multiply"; break;
                                    case DIV: method = "divide"; break;
                                }
                                result = make.Apply(List.nil(), make.Select(jcBinary.lhs, names.fromString(method)), List.of(jcBinary.rhs));
                            }
                        }
                    });
                }
            }
            return true;
        }
        return false;
    }

    private static <T> T jbUnwrap(Class<? extends T> iface, T wrapper) {
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
