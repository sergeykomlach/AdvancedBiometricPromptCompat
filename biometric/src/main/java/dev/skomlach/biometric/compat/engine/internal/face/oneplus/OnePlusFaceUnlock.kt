/*
 *  Copyright (c) 2021 Sergey Komlach aka Salat-Cx65; Original project https://github.com/Salat-Cx65/AdvancedBiometricPromptCompat
 *  All rights reserved.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package dev.skomlach.biometric.compat.engine.internal.face.oneplus;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;

import androidx.annotation.RestrictTo;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;

import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl;
import dev.skomlach.common.misc.ExecutorHelper;

import static dev.skomlach.biometric.compat.utils.ReflectionTools.getClassFromPkg;

@SuppressLint("PrivateApi")
@RestrictTo(RestrictTo.Scope.LIBRARY)
public class OnePlusFaceUnlock {

    private static final String TAG = OnePlusFaceUnlock.class.getSimpleName();

    //https://github.com/xayron/OPSystemUI/tree/d805abc13d081bd3579355a1075d4ae5e8be9270/sources/com/oneplus/faceunlock/internal

    private final Context mContext;
    private final String pkg = "com.oneplus.faceunlock";
    private final Class<?> flInterface;
    private final Class<?> flInterfaceStub;
    private final Class<?> flCallbackInterface;
    private final Class<?> flCallbackInterfaceStub;
    protected Object mFaceLockService;
    protected ServiceConnectionWrapper mServiceConnection;
    protected HashMap<IOPFacelockCallback, Object> mMap = new HashMap<>();
    private final OnePlusFaceSettings onePlusFaceSettings;

    public OnePlusFaceUnlock(Context context) throws Exception {
        mContext = context;
        final String FACELOCK_INTERFACE = "com.oneplus.faceunlock.internal.IOPFacelockService";
        final String FACELOCK_CALLBACK = "com.oneplus.faceunlock.internal.IOPFacelockCallback";

        try {
            flInterface = getClassFromPkg(pkg, FACELOCK_INTERFACE);
            flInterfaceStub = getClassFromPkg(pkg, FACELOCK_INTERFACE + "$Stub");
            flCallbackInterface = getClassFromPkg(pkg, FACELOCK_CALLBACK);
            flCallbackInterfaceStub = getClassFromPkg(pkg, FACELOCK_CALLBACK + "$Stub");
            onePlusFaceSettings = new OnePlusFaceSettings(context);
            return;
        } catch (Throwable ignored) {}

        throw new RuntimeException(TAG + " not supported");
    }

    public int checkState() {
        try {
            return onePlusFaceSettings.checkState();
        } catch (Throwable ignored) {}
        return -1;
    }

    public boolean bind(ServiceConnection connection) {
        BiometricLoggerImpl.d(TAG + " bind to service");

        if (mServiceConnection != null) {
            return false;
        }
        mServiceConnection = new ServiceConnectionWrapper(connection);
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(pkg, "com.oneplus.faceunlock.FaceUnlockService"));
        return mContext
                .bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE);
    }

    public void unbind() {
        BiometricLoggerImpl.d(TAG + " unbind from service");
        mContext.unbindService(mServiceConnection);
        mServiceConnection = null;
    }

    public void startFaceUnlock()
            throws RemoteException, IllegalArgumentException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        BiometricLoggerImpl.d(TAG + " startUi");

        try {
            if (flInterface != null)
                flInterface.getMethod("prepare").invoke(mFaceLockService);

            Method method = flInterface.getMethod("startFaceUnlock", int.class);
            method.invoke(mFaceLockService, 0);
        } catch (Throwable ignore) { }
    }

    public void stopFaceUnlock()
            throws RemoteException, IllegalArgumentException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        BiometricLoggerImpl.d(TAG + " stopUi");
        try {
            if (flInterface != null)
                flInterface.getMethod("stopFaceUnlock", int.class).invoke(mFaceLockService, 0);
            if (flInterface != null)
                flInterface.getMethod("release").invoke(mFaceLockService);
        } catch (Throwable ignore) { }
    }

    public void registerCallback(IOPFacelockCallback cb)
            throws NoSuchMethodException, ClassNotFoundException, IllegalArgumentException, IllegalAccessException, InvocationTargetException, InstantiationException {
        BiometricLoggerImpl.d(TAG + " registerCallback");

        Constructor<?> c = getClassFromPkg(pkg, flCallbackInterface.getName() + "$Stub$Proxy")
                .getDeclaredConstructor(IBinder.class);
        boolean isAccessible = c.isAccessible();
        try {
            if (!isAccessible)
                c.setAccessible(true);
            Object callbacks = c.newInstance(new CallBackBinder(cb));

            flInterface.getMethod("registerCallback", flCallbackInterface)
                    .invoke(mFaceLockService, callbacks);
            mMap.put(cb, callbacks);
        } finally {
            if (!isAccessible)
                c.setAccessible(false);
        }
    }

    public void unregisterCallback(IOPFacelockCallback cb)
            throws RemoteException, IllegalArgumentException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        BiometricLoggerImpl.d(TAG + " unregisterCallback");
        flInterface.getMethod("unregisterCallback", flCallbackInterface)
                .invoke(mFaceLockService, mMap.get(cb));
        mMap.remove(cb);
    }

    private class ServiceConnectionWrapper implements ServiceConnection {

        private final ServiceConnection mServiceConnection;

        ServiceConnectionWrapper(ServiceConnection sc) {
            mServiceConnection = sc;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {

            BiometricLoggerImpl.d(TAG + " service connected");

            try {
                mFaceLockService = flInterfaceStub.getMethod("asInterface", IBinder.class)
                        .invoke(null, service);
                mServiceConnection.onServiceConnected(name, service);
            } catch (IllegalArgumentException e) {
                mFaceLockService = null;
                BiometricLoggerImpl.e(e, TAG + e.getMessage());
            } catch (IllegalAccessException e) {
                mFaceLockService = null;
                BiometricLoggerImpl.e(e, TAG + e.getMessage());
            } catch (InvocationTargetException e) {
                mFaceLockService = null;
                BiometricLoggerImpl.e(e, TAG + e.getMessage());
            } catch (NoSuchMethodException e) {
                mFaceLockService = null;
                BiometricLoggerImpl.e(e, TAG + e.getMessage());
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            BiometricLoggerImpl.d(TAG + " service disconnected");
            mServiceConnection.onServiceDisconnected(name);
            mFaceLockService = null;
        }
    }

    private class CallBackBinder extends Binder {

        private final IOPFacelockCallback mCallback;
        private final HashMap<Integer, String> mMap = new HashMap<>();

        CallBackBinder(IOPFacelockCallback callback) {
            mCallback = callback;

            // Find matching TRANSACTION_**** values
            Method[] methods = IOPFacelockCallback.class.getMethods();

            for (Method m : methods) {
                try {
                    Field f = flCallbackInterfaceStub.getDeclaredField("TRANSACTION_" + m.getName());
                    boolean isAccessible = f.isAccessible();
                    try {
                        if (!isAccessible)
                            f.setAccessible(true);
                        f.setAccessible(true);
                        mMap.put((Integer) f.get(null), m.getName());
                    } finally {
                        if (!isAccessible)
                            f.setAccessible(false);
                    }
                } catch (NoSuchFieldException ignore) {
                } catch (IllegalArgumentException e) {
                    BiometricLoggerImpl.e(e, TAG);
                } catch (IllegalAccessException e) {
                    BiometricLoggerImpl.e(e, TAG);
                }
            }
        }

        @Override
        protected boolean onTransact(final int code, Parcel data, Parcel reply,
                                     int flags) throws RemoteException {

            if (mMap.containsKey(code)) {
                BiometricLoggerImpl.d(TAG + (" onTransact " + mMap.get(code)));

                // Callback may be called outside the UI thread
                ExecutorHelper.INSTANCE.getHandler().post(() -> {
                    try {
                        IOPFacelockCallback.class.getMethod(mMap.get(code)).invoke(mCallback);
                    } catch (IllegalArgumentException e) {

                        BiometricLoggerImpl.e(e, TAG + e.getMessage());
                    } catch (IllegalAccessException e) {

                        BiometricLoggerImpl.e(e, TAG + e.getMessage());
                    } catch (InvocationTargetException e) {

                        BiometricLoggerImpl.e(e, TAG + e.getMessage());
                    } catch (NoSuchMethodException e) {

                        BiometricLoggerImpl.e(e, TAG + e.getMessage());
                    }
                });

                return true;
            } else {
                BiometricLoggerImpl.d(TAG + (" unknown transact : " + code));
            }

            return super.onTransact(code, data, reply, flags);
        }
    }
}
