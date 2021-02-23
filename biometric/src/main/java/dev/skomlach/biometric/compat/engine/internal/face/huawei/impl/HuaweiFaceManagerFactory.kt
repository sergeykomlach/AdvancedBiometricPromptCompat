package dev.skomlach.biometric.compat.engine.internal.face.huawei.impl

import android.content.Context
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.d

object HuaweiFaceManagerFactory {
    private const val TAG = "HuaweiFaceManagerFactory"
    private var mFaceImplV1: HuaweiFaceManagerV1Impl? = null
    fun getHuaweiFaceManager(context: Context): HuaweiFaceManager? {
        d(TAG, "HuaweiManager getHuaweiFaceManager")
        if (mFaceImplV1 == null) {
            mFaceImplV1 = HuaweiFaceManagerV1Impl(context)
        }
        return mFaceImplV1
    }
}