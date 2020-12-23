/*
See original thread
https://forum.xda-developers.com/showthread.php?p=25572510#post25572510
*/

package dev.skomlach.biometric.compat.engine.internal.face.facelock;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.text.TextUtils;

import androidx.annotation.RestrictTo;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;

import dev.skomlach.biometric.compat.utils.LockType;
import dev.skomlach.biometric.compat.utils.ReflectionTools;
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl;
import dev.skomlach.common.misc.ExecutorHelper;

import static dev.skomlach.biometric.compat.utils.ReflectionTools.getClassFromPkg;

@SuppressLint("PrivateApi")
@RestrictTo(RestrictTo.Scope.LIBRARY)
public class FaceLock {

    private static final String TAG = "FaceId";

    //START u0 {act=miui.intent.action.CHECK_ACCESS_CONTROL flg=0x18800000 pkg=com.miui.securitycenter cmp=com.miui.securitycenter/com.miui.applicationlock.ConfirmAccessControl (has extras)} from uid 10060

    //Intent intent = new Intent("com.xiaomi.biometric.BiometricService");
    //intent.setPackage("com.xiaomi.biometric");


    private static final String FACELOCK_INTERFACE = "com.android.internal.policy.IFaceLockInterface";
    private static final String FACELOCK_CALLBACK = "com.android.internal.policy.IFaceLockCallback";

    private static final String TRUSTEDFACE_INTERFACE = "com.android.facelock.ITrustedFaceInterface";
    private static final String TRUSTEDFACE_CALLBACK = "com.android.facelock.ITrustedFaceCallback";
    private final Context mContext;
    protected Object mFaceLockService;
    protected ServiceConnectionWrapper mServiceConnection;
    protected HashMap<IFaceLockCallback, Object> mMap = new HashMap<>();
    private ComponentName SERVICE_PKG = null;
    private Class<?> flInterface;
    private Class<?> flInterfaceStub;
    private Class<?> flCallbackInterface;
    private Class<?> flCallbackInterfaceStub;


    public FaceLock(Context context) throws Exception {
        mContext = context;
        // Retrieve all services that can match the given intent
        PackageManager pm = context.getPackageManager();
        List<ResolveInfo> resolveInfo = pm.queryIntentServices(new Intent().setPackage("com.android.facelock"), 0);
        if (resolveInfo != null) {
            BiometricLoggerImpl.d(TAG + " services list - "+resolveInfo.size());
            for (ResolveInfo info : resolveInfo) {
                try {
                    String pkg = !TextUtils.isEmpty(info.resolvePackageName) ? info.resolvePackageName : info.serviceInfo.packageName;

                    if(flCallbackInterfaceStub == null) {
                        try {
                            flInterface = getClassFromPkg(pkg, FACELOCK_INTERFACE);
                            flInterfaceStub = getClassFromPkg(pkg, FACELOCK_INTERFACE + "$Stub");
                            flCallbackInterface = getClassFromPkg(pkg, FACELOCK_CALLBACK);
                            flCallbackInterfaceStub = getClassFromPkg(pkg, FACELOCK_CALLBACK + "$Stub");
                        } catch (Throwable ignored) {
                            flInterface = getClassFromPkg(pkg, TRUSTEDFACE_INTERFACE);
                            flInterfaceStub = getClassFromPkg(pkg, TRUSTEDFACE_INTERFACE + "$Stub");
                            flCallbackInterface = getClassFromPkg(pkg, TRUSTEDFACE_CALLBACK);
                            flCallbackInterfaceStub = getClassFromPkg(pkg, TRUSTEDFACE_CALLBACK + "$Stub");
                        }
                    }
                    Class<?> serviceClazz = ReflectionTools.getClassFromPkg(pkg, info.serviceInfo.name);
                    Field[] fields = serviceClazz.getDeclaredFields();
                    for (Field f : fields) {
                        Class<?> type = f.getType();
                        //private final com.android.internal.policy.IFaceLockInterface$Stub binder;
                        boolean isAccessible = f.isAccessible();
                        try {
                            if (!isAccessible)
                                f.setAccessible(true);
                            if (type.equals(flInterface) ||
                                    type.equals(flInterfaceStub) ||
                                    type.equals(flCallbackInterface) ||
                                    type.equals(flCallbackInterfaceStub)) {
                                SERVICE_PKG = new ComponentName(pkg, info.serviceInfo.name);
                                return;
                            }
                        } finally {
                            if (!isAccessible)
                                f.setAccessible(false);
                        }
                    }
                } catch (Throwable e) {
                    BiometricLoggerImpl.e(e);
                }
            }
        } else
            BiometricLoggerImpl.d(TAG + " services list is empty");

        throw new RuntimeException(TAG + " not supported");
    }

    public boolean bind(ServiceConnection connection) {
        BiometricLoggerImpl.d(TAG + " bind to service");

        if (mServiceConnection != null) {
            return false;
        }
        mServiceConnection = new ServiceConnectionWrapper(connection);
        Intent intent = new Intent();
        intent.setComponent(SERVICE_PKG);
        return mContext
                .bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE);
    }

    public void unbind() {
        BiometricLoggerImpl.d(TAG + " unbind from service");
        mContext.unbindService(mServiceConnection);
        mServiceConnection = null;
    }

    public void startUi(IBinder token, int x, int y, int width, int height)
            throws android.os.RemoteException, IllegalArgumentException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        BiometricLoggerImpl.d(TAG + " startUi");

        try {
            Method method = flInterface.getMethod("start");
            method.invoke(mFaceLockService);
            return;
        } catch (InvocationTargetException e) {}
        //newer API
        try {
            Method method = flInterface.getMethod("startUi", IBinder.class, int.class, int.class, int.class, int.class,
                    boolean.class);
            method.invoke(mFaceLockService, token, x, y, width, height, LockType.isBiometricWeakLivelinessEnabled(mContext));
            return;
        } catch (InvocationTargetException ignore) {}
        try {
            //older API's
            Method method = flInterface.getMethod("startUi", IBinder.class, int.class, int.class, int.class, int.class);
            method.invoke(mFaceLockService, token, x, y, width, height);
        } catch (InvocationTargetException e) { }
    }

    public void stopUi()
            throws android.os.RemoteException, IllegalArgumentException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        BiometricLoggerImpl.d(TAG + " stopUi");
        try {
            if (flInterface != null)
                flInterface.getMethod("stop").invoke(mFaceLockService);
            return;
        } catch (InvocationTargetException e) {}

        if (flInterface != null)
            flInterface.getMethod("stopUi").invoke(mFaceLockService);
    }

    public void registerCallback(IFaceLockCallback cb)
            throws NoSuchMethodException, ClassNotFoundException, IllegalArgumentException, IllegalAccessException, InvocationTargetException, InstantiationException {
        BiometricLoggerImpl.d(TAG + " registerCallback");

        Constructor<?> c = getClassFromPkg(SERVICE_PKG.getPackageName(), FACELOCK_CALLBACK + "$Stub$Proxy")
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

    public void unregisterCallback(IFaceLockCallback cb)
            throws android.os.RemoteException, IllegalArgumentException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
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

        private final IFaceLockCallback mCallback;
        private final HashMap<Integer, String> mMap = new HashMap<>();

        CallBackBinder(IFaceLockCallback callback) {
            mCallback = callback;

            // Find matching TRANSACTION_**** values
            Method[] methods = IFaceLockCallback.class.getMethods();

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
                    BiometricLoggerImpl.e(e);
                } catch (IllegalAccessException e) {
                    BiometricLoggerImpl.e(e);
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
                        IFaceLockCallback.class.getMethod(mMap.get(code)).invoke(mCallback);
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
