package dev.skomlach.biometric.compat.utils;

import android.content.Context;
import android.content.pm.PackageManager;

import androidx.annotation.RestrictTo;
import androidx.core.os.BuildCompat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.WeakHashMap;

import dalvik.system.PathClassLoader;
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl;
import dev.skomlach.common.contextprovider.AndroidContext;

@RestrictTo(RestrictTo.Scope.LIBRARY)
public class ReflectionTools {

    private static final WeakHashMap<String, PathClassLoader> cache = new WeakHashMap<>();

    private static PathClassLoader getPathClassLoaderForPkg(String pkg) throws Exception {
        PathClassLoader pathClassLoader = cache.get(pkg);
        if (pathClassLoader == null) {
            String apkName = null;
            try {
                apkName = AndroidContext.getAppContext().getPackageManager().getApplicationInfo(
                        pkg, 0).sourceDir;
            } catch (Throwable e) {
                if (BuildCompat.isAtLeastN())
                    apkName = AndroidContext.getAppContext().getPackageManager().getApplicationInfo(
                            pkg, PackageManager.MATCH_SYSTEM_ONLY).sourceDir;
            }
            pathClassLoader = new PathClassLoader(apkName,
                    ClassLoader.getSystemClassLoader());
            cache.put(pkg, pathClassLoader);
        }

        return pathClassLoader;
    }

    public static Class<?> getClassFromPkg(String pkg, String cls) throws ClassNotFoundException {
        try {
            return Class.forName(cls, true, getPathClassLoaderForPkg(pkg));
        } catch (Throwable e) {
            throw new ClassNotFoundException("Class '" + pkg + "/" + cls + "' not found", e);
        }
    }

    public static boolean checkBooleanMethodForSignature(Class<?> clazz, Object managerObject, String... keywords) {
        try {
            Method[] allMethods = clazz.getMethods();
            for (Method m : allMethods) {
                try {

                    boolean isReturnBoolean = boolean.class.getName().equals(m.getReturnType().getName()) || Boolean.class.getName().equals(m.getReturnType().getName());

                    boolean containsKeyword = false;
                    String name = m.getName();
                    for (String s : keywords) {
                        if (name.contains(s)) {
                            containsKeyword = true;
                            break;
                        }
                    }

                    if (Modifier.isPublic(m.getModifiers()) && isReturnBoolean && containsKeyword) {
                        BiometricLoggerImpl.d("Method: " + m.getName());

                        if (m.getParameterTypes().length == 0)
                            try {
                                if (boolean.class.getName().equals(m.getReturnType().getName())) {
                                    return (boolean) m.invoke(managerObject);
                                } else {
                                    return (Boolean) m.invoke(managerObject);
                                }
                            } catch (Throwable e) {
                                BiometricLoggerImpl.e(e);
                            }
                    }
                } catch (Throwable e) {
                    BiometricLoggerImpl.e(e);
                }
            }
        } catch (Throwable e) {
            BiometricLoggerImpl.e(e);
        }
        return false;
    }

    public static int checkIntMethodForSignature(Class<?> clazz, Object managerObject, String startWith) {
        try {
            Method[] allMethods = clazz.getMethods();
            for (Method m : allMethods) {
                try {

                    boolean isReturnInt = int.class.getName().equals(m.getReturnType().getName()) || Integer.class.getName().equals(m.getReturnType().getName());

                    if (Modifier.isPublic(m.getModifiers()) && isReturnInt && m.getName().startsWith(startWith)) {
                        BiometricLoggerImpl.d("Method: " + m.getName());

                        if (m.getParameterTypes().length == 0)
                            try {
                                if (int.class.getName().equals(m.getReturnType().getName())) {
                                    return (int) m.invoke(managerObject);
                                } else {
                                    return (Integer) m.invoke(managerObject);
                                }
                            } catch (Throwable e) {
                                BiometricLoggerImpl.e(e);
                            }
                    }
                } catch (Throwable e) {
                    BiometricLoggerImpl.e(e);
                }
            }
        } catch (Throwable e) {
            BiometricLoggerImpl.e(e);
        }
        return -1;
    }

    public static Object callGetOrCreateInstance(Class<?> target) {

        try {
            Method[] array = target.getMethods();
            for (Method m : array) {
                try {
                    if (Modifier.isPublic(m.getModifiers()) && Modifier.isStatic(m.getModifiers())) {
                        Class<?> returnedType = m.getReturnType();
                        if (returnedType == Void.TYPE || returnedType == Object.class)
                            continue;
                        if (returnedType == target || returnedType == target.getSuperclass() || Arrays.asList(target.getInterfaces()).contains(returnedType)) {
                            BiometricLoggerImpl.d("Method: " + m.getName());
                            try {
                                if (m.getParameterTypes().length == 1 && m
                                        .getParameterTypes()[0].getName().equals(Context.class.getName())) {
                                    //Case for SomeManager.getInstance(Context)
                                    return m.invoke(null, AndroidContext.getAppContext());
                                } else if (m.getParameterTypes().length == 0) {
                                    //Case for SomeManager.getInstance()
                                    return m.invoke(null);
                                }
                            } catch (Throwable e) {
                                //TODO:
                                //Deal with Caused by: java.lang.SecurityException: Permission Denial: get/set setting for user asks to run as user -2 but is calling from user 0; this requires android.permission.INTERACT_ACROSS_USERS_FULL
                                BiometricLoggerImpl.e(e);
                            }
                        }
                    }
                } catch (Throwable e) {
                    BiometricLoggerImpl.e(e);
                }
            }
        } catch (Throwable e) {
            BiometricLoggerImpl.e(e);
        }
        try {
            Constructor<?>[] array = target.getConstructors();
            for (Constructor<?> c : array) {
                try {
                    if (Modifier.isPublic(c.getModifiers())) {
                        BiometricLoggerImpl.d("Constructor: " + c.getName());
                        try {
                            if (c.getParameterTypes().length == 1 && c
                                    .getParameterTypes()[0].getName().equals(Context.class.getName())) {
                                //Case for new SomeManager(Context)
                                return c.newInstance(AndroidContext.getAppContext());
                            } else if (c.getParameterTypes().length == 0) {
                                //Case for new SomeManager()
                                return c.newInstance();
                            }
                        } catch (Throwable e) {
                            //TODO:
                            //Deal with Caused by: java.lang.SecurityException: Permission Denial: get/set setting for user asks to run as user -2 but is calling from user 0; this requires android.permission.INTERACT_ACROSS_USERS_FULL
                            BiometricLoggerImpl.e(e);
                        }
                    }
                } catch (Throwable e) {
                    BiometricLoggerImpl.e(e);
                }
            }
        } catch (Throwable e) {
            BiometricLoggerImpl.e(e);
        }
        return null;
    }
}
