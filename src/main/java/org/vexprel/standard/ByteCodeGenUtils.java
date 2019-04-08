package org.vexprel.standard;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.MethodCall;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatchers;
import org.vexprel.exceptions.ExpressionExecutionException;

final class ByteCodeGenUtils {

    private static final AtomicInteger index = new AtomicInteger(0);

    private static final String OBJECT_PROPERTY_ACTION_CLASS_NAME_FORMAT =
            StandardExpressionAction.class.getPackage().getName() + ".%s_%s_%d";


    private static int nextIndex() {
        int value,next;
        do {
            value = index.get();
            next = (value < Integer.MAX_VALUE? value + 1 : 0);
        } while (!index.compareAndSet(value, next));
        return value;
    }



    private static String computeClassName(final Class<?> targetClass, final String getterMethodName) {
        return String.format(OBJECT_PROPERTY_ACTION_CLASS_NAME_FORMAT, targetClass.getName(), getterMethodName, nextIndex());
    }



    static StandardExpressionAction buildObjectPropertyAction(
            final Class<?> targetClass, final String getterMethodName) {

        final Method getterMethod;
        try {
            getterMethod = targetClass.getMethod(getterMethodName);
        } catch (final NoSuchMethodException e) {
            throw new ExpressionExecutionException(
                    String.format("Could not find method %s in class %s", getterMethodName, targetClass.getName()), e);
        }

        final Class<?> actionClass =
                new ByteBuddy()
                        .subclass(Object.class)
                        .implement(StandardExpressionAction.class)
                        .name(computeClassName(targetClass, getterMethodName))
                        .method(ElementMatchers.named("execute"))
                        .intercept(
                                MethodCall.invoke(getterMethod)
                                        .onArgument(0)
                                        .withAssigner(Assigner.DEFAULT, Assigner.Typing.DYNAMIC))
                        .make()
                        .load(ByteCodeGenUtils.class.getClassLoader(), ClassLoadingStrategy.Default.WRAPPER)
                        .getLoaded();

        try {
            return (StandardExpressionAction) actionClass.getConstructor(null).newInstance(null);
        } catch (final NoSuchMethodException e) {
            throw new ExpressionExecutionException(
                    String.format("Generated bytecode for class %s does not contain an empty constructor", actionClass.getName()), e);
        } catch (final IllegalAccessException|InstantiationException|InvocationTargetException e) {
            throw new ExpressionExecutionException(
                    String.format("Exception thrown while executing constructor for bytecode-generated class %s", actionClass.getName()), e);
        }

    }




    private ByteCodeGenUtils() {
        super();
    }


}
