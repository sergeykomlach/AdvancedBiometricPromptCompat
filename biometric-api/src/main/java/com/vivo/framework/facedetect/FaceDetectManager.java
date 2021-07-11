/*
 *  Copyright (c) 2021 Sergey Komlach aka Salat-Cx65; Original project: https://github.com/Salat-Cx65/AdvancedBiometricPromptCompat
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
package com.vivo.framework.facedetect;

public class FaceDetectManager {
    //https://github.com/SivanLiu/VivoFramework/blob/8d31381ecc788afb023960535bafbfa3b7df7d9b/Vivo_y93/src/main/java/com/vivo/framework/facedetect/FaceDetectManager.java
    public static final int FACE_DETECT_BUSY = -3;
    public static final int FACE_DETECT_FAILED = -1;
    public static final int FACE_DETECT_NO_FACE = -2;
    public static final int FACE_DETECT_SUCEESS = 0;

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

    public static abstract class FaceAuthenticationCallback {
        public void onFaceAuthenticationResult(int errorCode, int retry_times) {
        }
    }
}