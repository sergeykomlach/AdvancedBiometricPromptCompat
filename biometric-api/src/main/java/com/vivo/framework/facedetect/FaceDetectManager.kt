package com.vivo.framework.facedetect

class FaceDetectManager private constructor() {
    abstract class FaceAuthenticationCallback {
        open fun onFaceAuthenticationResult(errorCode: Int, retry_times: Int) {}
    }

    val isFaceUnlockEnable: Boolean
        get() = false
    val isFastUnlockEnable: Boolean
        get() = false

    fun hasFaceID(): Boolean {
        return false
    }

    fun startFaceUnlock(callback: FaceAuthenticationCallback?) {}
    fun stopFaceUnlock() {}
    fun release() {}

    companion object {
        //https://github.com/SivanLiu/VivoFramework/blob/8d31381ecc788afb023960535bafbfa3b7df7d9b/Vivo_y93/src/main/java/com/vivo/framework/facedetect/FaceDetectManager.java
        const val FACE_DETECT_BUSY = -3
        const val FACE_DETECT_FAILED = -1
        const val FACE_DETECT_NO_FACE = -2
        const val FACE_DETECT_SUCEESS = 0
        @JvmStatic
        fun getInstance(): FaceDetectManager? {
           return null
        }
    }
}