package dev.skomlach.biometric.compat.utils;

import androidx.annotation.RestrictTo;

import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

@RestrictTo(RestrictTo.Scope.LIBRARY)
public class ReflectionUtils {
    public static void printClass(String name) {
        try {
            // print class name and superclass name (if != Object)
            Class<?> cl = Class.forName(name);
            printClass(cl);
        } catch (ClassNotFoundException ignore) {

        }
    }

    public static void printClass(Class cl) {
        try {
            Class<?> supercl = cl.getSuperclass();
            String modifiers = Modifier.toString(cl.getModifiers());
            if (modifiers.length() > 0) {
                System.err.print(modifiers + " ");
            }
            System.err.print("class " + cl.getName());
            if (supercl != null && supercl != Object.class) {
                System.err.print(" extends "
                        + supercl.getName());
            }

            System.err.print("\n{\n");
            printConstructors(cl);
            System.err.println();
            printMethods(cl);
            System.err.println();
            printFields(cl);
            System.err.println("}");
        } catch (Exception e) {
            BiometricLoggerImpl.e(e);
        }
    }

    /**
     * Prints all constructors of a class
     *
     * @param cl a class
     */
    public static void printConstructors(Class<?> cl) {
        Constructor<?>[] constructors = cl.getDeclaredConstructors();

        for (Constructor<?> c : constructors) {
            String name = c.getName();
            System.err.print("   ");
            String modifiers = Modifier.toString(c.getModifiers());
            if (modifiers.length() > 0) {
                System.err.print(modifiers + " ");
            }
            System.err.print(name + "(");

            // print parameter types
            Class<?>[] paramTypes = c.getParameterTypes();
            for (int j = 0; j < paramTypes.length; j++) {
                if (j > 0) {
                    System.err.print(", ");
                }
                System.err.print(paramTypes[j].getName());
            }
            System.err.println(");");
        }
    }

    /**
     * Prints all methods of a class
     *
     * @param cl a class
     */
    public static void printMethods(Class<?> cl) {
        Method[] methods = cl.getDeclaredMethods();

        for (Method m : methods) {
            Class<?> retType = m.getReturnType();
            String name = m.getName();

            System.err.print("   ");
            // print modifiers, return type and method name
            String modifiers = Modifier.toString(m.getModifiers());
            if (modifiers.length() > 0) {
                System.err.print(modifiers + " ");
            }
            System.err.print(retType.getName() + " " + name + "(");

            // print parameter types
            Class<?>[] paramTypes = m.getParameterTypes();
            for (int j = 0; j < paramTypes.length; j++) {
                if (j > 0) {
                    System.err.print(", ");
                }
                System.err.print(paramTypes[j].getName());
            }
            System.err.println(");");
        }
    }

    /**
     * Prints all fields of a class
     *
     * @param cl a class
     */
    public static void printFields(Class<?> cl) throws IllegalAccessException {
        Field[] fields = cl.getDeclaredFields();

        for (Field f : fields) {
            boolean isAccessible = f.isAccessible();
            if (!isAccessible) {
                f.setAccessible(true);
            }
            Class<?> type = f.getType();
            String name = f.getName();
            System.err.print("   ");
            String modifiers = Modifier.toString(f.getModifiers());
            if (modifiers.length() > 0) {
                System.err.print(modifiers + " ");
            }
            if (Modifier.isStatic(f.getModifiers())) {
                if (type.equals(String.class))
                    System.err.println(type.getName() + " " + name + " = \"" + f.get(null) + "\";");
                else
                    System.err.println(type.getName() + " " + name + " = " + f.get(null) + ";");
            } else
                System.err.println(type.getName() + " " + name + ";");

            if (!isAccessible) {
                f.setAccessible(true);
            }
        }
    }
}
