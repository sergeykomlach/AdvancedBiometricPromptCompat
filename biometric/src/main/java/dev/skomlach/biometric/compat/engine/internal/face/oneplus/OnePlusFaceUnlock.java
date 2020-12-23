package dev.skomlach.biometric.compat.engine.internal.face.oneplus;

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
import android.os.Process;
import android.os.RemoteException;
import android.text.TextUtils;

import androidx.annotation.RestrictTo;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;

import dev.skomlach.biometric.compat.utils.ReflectionTools;
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl;
import dev.skomlach.common.misc.ExecutorHelper;

import static dev.skomlach.biometric.compat.utils.ReflectionTools.getClassFromPkg;

@SuppressLint("PrivateApi")
@RestrictTo(RestrictTo.Scope.LIBRARY)
public class OnePlusFaceUnlock {

    private static final String TAG = "OnePlusFaceUnlock";

    //https://github.com/xayron/OPSystemUI/tree/d805abc13d081bd3579355a1075d4ae5e8be9270/sources/com/oneplus/faceunlock/internal

    //String FACEUNLOCK_PACKAGE = "com.oneplus.faceunlock";
    //String FACEUNLOCK_SERVICE = "com.oneplus.faceunlock.FaceUnlockService";
    //"com.oneplus.faceunlock.internal.IOPFacelockService"

    private static final String FACELOCK_INTERFACE = "com.oneplus.faceunlock.internal.IOPFacelockService";
    private static final String FACELOCK_CALLBACK = "com.oneplus.faceunlock.internal.IOPFacelockCallback";

    private final Context mContext;
    protected Object mFaceLockService;
    protected ServiceConnectionWrapper mServiceConnection;
    protected HashMap<IOPFacelockCallback, Object> mMap = new HashMap<>();
    private ComponentName SERVICE_PKG = null;
    private Class<?> flInterface;
    private Class<?> flInterfaceStub;
    private Class<?> flCallbackInterface;
    private Class<?> flCallbackInterfaceStub;

    public OnePlusFaceUnlock(Context context) throws Exception {
        mContext = context;
        PackageManager pm = context.getPackageManager();
        List<ResolveInfo> resolveInfo = pm.queryIntentServices(new Intent().setPackage("com.oneplus.faceunlock"), 0);

        if (resolveInfo != null) {
            BiometricLoggerImpl.d(TAG + " services list - "+resolveInfo.size());
            for (ResolveInfo info : resolveInfo) {
                try {
                    String pkg = !TextUtils.isEmpty(info.resolvePackageName) ? info.resolvePackageName : info.serviceInfo.packageName;

                    if(flCallbackInterfaceStub == null) {
                            flInterface = getClassFromPkg(pkg, FACELOCK_INTERFACE);
                            flInterfaceStub = getClassFromPkg(pkg, FACELOCK_INTERFACE + "$Stub");
                            flCallbackInterface = getClassFromPkg(pkg, FACELOCK_CALLBACK);
                            flCallbackInterfaceStub = getClassFromPkg(pkg, FACELOCK_CALLBACK + "$Stub");
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

    public void startFaceUnlock()
            throws RemoteException, IllegalArgumentException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        BiometricLoggerImpl.d(TAG + " startUi");

        Method method = flInterface.getMethod("startFaceUnlock", int.class);
        method.invoke(mFaceLockService, Process.myUid());
    }

    public void stopFaceUnlock()
            throws RemoteException, IllegalArgumentException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        BiometricLoggerImpl.d(TAG + " stopUi");

        if (flInterface != null)
            flInterface.getMethod("stopFaceUnlock", int.class).invoke(mFaceLockService, Process.myUid());
    }

    public void registerCallback(IOPFacelockCallback cb)
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
                if (flInterface != null)
                    flInterface.getMethod("prepare").invoke(mFaceLockService);

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
            try {
                if (flInterface != null)
                    flInterface.getMethod("release").invoke(mFaceLockService);
            } catch (Throwable e) {
                BiometricLoggerImpl.e(e, TAG + e.getMessage());
            }
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
