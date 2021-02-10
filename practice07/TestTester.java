package practice07;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class TestTester {

    public static final int MAX_PRIORITY = 10;

    public static void main(String[] args) {
        start(TestForTest.class);
    }

    public static void start(Class testClass) {

        Method[] methods = testClass.getMethods();

        Method before = null;
        Method after = null;

        for (int i = 0; i < methods.length; i++) {
            if(methods[i].isAnnotationPresent(BeforeSuite.class)) {
                if (before != null) throw new RuntimeException("More than one BeforeSuit annotation in class");
                else before = methods[i];
            }
            else if (methods[i].isAnnotationPresent(AfterSuite.class)) {
                if (after != null) throw new RuntimeException("More than one AfterSuit annotation in class");
                else after = methods[i];
            }
        }

        try {
            if (before != null) before.invoke(null);
            invokeTests(methods);
            if (after != null) after.invoke(null);
        } catch (IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    private static void invokeTests(Method[] methods) {
        for (int i = 1; i <= MAX_PRIORITY; i++) {
            for (int j = 0; j < methods.length; j++) {
                if (methods[j].isAnnotationPresent(Test.class) && methods[j].getAnnotation(Test.class).priority() == i) {
                    try {
                        methods[j].invoke(null);
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
}
