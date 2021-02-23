package dev.skomlach.biometric.compat.engine.internal.face.huawei.impl

import android.content.Context
import dev.skomlach.biometric.compat.engine.BiometricCodes
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.d
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.e

class HuaweiFaceManagerV1Impl(context: Context) : HuaweiFaceManagerV1() {

    companion object {
        private const val FACE_AUTH_VERSION_V1 = 1
        private const val HUAWEI_OP_FAIL = -1
        private const val HUAWEI_OP_SUCCESS = 0
        private const val TAG = "HuaweiFaceManagerV1Impl"
    }

    init {
        HuaweiFaceRecognizeManager.createInstance(context)
    }

    override fun authenticate(reqID: Int, flag: Int, callback: AuthenticatorCallback?) {
        val frManager: HuaweiFaceRecognizeManager? =
            HuaweiFaceRecognizeManager.instance
        if (frManager == null) {
            e(TAG, "HuaweiFaceRecognizeManager is null")
        } else if (callback == null) {
            e(TAG, "callback empty")
        } else {
            frManager.setAuthCallback(callback)
            val str = TAG
            val stringBuilder = StringBuilder()
            stringBuilder.append("reqID is ")
            stringBuilder.append(reqID)
            stringBuilder.append("flag is ")
            stringBuilder.append(flag)
            d(str, stringBuilder.toString())
            val ret = frManager.init()
            if (ret != HUAWEI_OP_SUCCESS) {
                val str2 = TAG
                val stringBuilder2 = StringBuilder()
                stringBuilder2.append("init failed returning ")
                stringBuilder2.append(ret)
                e(str2, stringBuilder2.toString())
                callback.onAuthenticationError(BiometricCodes.BIOMETRIC_ERROR_HW_UNAVAILABLE)
                return
            }
            d(TAG, "authenicating... ")
            HuaweiFaceRecognizeManager.fRManager?.authenticate(reqID, flag, null)
        }
    }

    override fun cancel(reqID: Int): Int {
        d(TAG, "canceling...")
        if (HuaweiFaceRecognizeManager.instance == null) {
            e(TAG, "HuaweiFaceRecognizeManager is null")
            return -1
        }
        HuaweiFaceRecognizeManager.fRManager?.cancelAuthenticate(reqID)
        return 0
    }

    override val version: Int
        get() = 1
    override val isHardwareDetected: Boolean
        get() = HuaweiFaceRecognizeManager.fRManager?.hardwareSupportType ?: 0 and 1 != 0

    override fun hasEnrolledTemplates(): Boolean {
        return HuaweiFaceRecognizeManager.fRManager?.enrolledFaceIDs?.isNotEmpty() == true
    }
}