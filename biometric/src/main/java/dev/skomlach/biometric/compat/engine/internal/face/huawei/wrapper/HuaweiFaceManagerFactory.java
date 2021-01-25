package dev.skomlach.biometric.compat.engine.internal.face.huawei.wrapper;

import android.content.Context;

import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl;

public class HuaweiFaceManagerFactory {
    private static final String TAG = "HuaweiFaceManagerFactory";
    private static HuaweiFaceManagerV1Impl mFaceImplV1;

    public static HuaweiFaceManager getHuaweiFaceManager(Context context) {
        BiometricLoggerImpl.d(TAG, "HuaweiManager getHuaweiFaceManager");
        if (mFaceImplV1 == null) {
            mFaceImplV1 = new HuaweiFaceManagerV1Impl(context);
        }
        return mFaceImplV1;
    }
}
