package com.vivo.framework.facedetect;

public class FaceDetectManager {
    //https://github.com/SivanLiu/VivoFramework/blob/8d31381ecc788afb023960535bafbfa3b7df7d9b/Vivo_y93/src/main/java/com/vivo/framework/facedetect/FaceDetectManager.java
    public static final int FACE_DETECT_BUSY = -3;
    public static final int FACE_DETECT_FAILED = -1;
    public static final int FACE_DETECT_NO_FACE = -2;
    public static final int FACE_DETECT_SUCEESS = 0;

    public static abstract class FaceAuthenticationCallback {
        public void onFaceAuthenticationResult(int errorCode, int retry_times) {
        }
    }

    private FaceDetectManager() {

    }

    public static FaceDetectManager getInstance() {
        return new FaceDetectManager();
    }

    public boolean isFaceUnlockEnable() {
        return false;
    }

    public boolean isFastUnlockEnable() {

        return false;
    }

    public boolean hasFaceID() {
        return false;
    }

    public void startFaceUnlock(FaceAuthenticationCallback callback) {

    }

    public void stopFaceUnlock() {

    }

    public void release() {

    }
}