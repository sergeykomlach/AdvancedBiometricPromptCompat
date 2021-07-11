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
package com.huawei.facerecognition;

import android.content.Context;
import android.view.Surface;

public class FaceRecognizeManager {
    public static final int CODE_CALLBACK_ACQUIRE = 3;
    public static final int CODE_CALLBACK_BUSY = 4;
    public static final int CODE_CALLBACK_CANCEL = 2;
    public static final int CODE_CALLBACK_OUT_OF_MEM = 5;
    public static final int CODE_CALLBACK_RESULT = 1;
    public static final int REQUEST_OK = 0;
    public static final int TYPE_CALLBACK_AUTH = 2;
    public static final int TYPE_CALLBACK_ENROLL = 1;
    public static final int TYPE_CALLBACK_REMOVE = 3;

    public FaceRecognizeManager(Context context, final FaceRecognizeCallback callback) {

    }

    public int authenticate(int reqId, int flags, Surface preview) {
        return 0;
    }

    public int cancelAuthenticate(int reqId) {
        return 0;
    }

    public int init() {
        return 0;
    }

    public int release() {
        return 0;
    }

    public int[] getEnrolledFaceIDs() {
        return null;
    }

    public int getHardwareSupportType() {
        return 0;
    }

    public interface AcquireInfo {
        int FACE_UNLOCK_FACE_BAD_QUALITY = 4;
        int FACE_UNLOCK_FACE_BLUR = 28;
        int FACE_UNLOCK_FACE_DARKLIGHT = 30;
        int FACE_UNLOCK_FACE_DOWN = 18;
        int FACE_UNLOCK_FACE_EYE_CLOSE = 22;
        int FACE_UNLOCK_FACE_EYE_OCCLUSION = 21;
        int FACE_UNLOCK_FACE_HALF_SHADOW = 32;
        int FACE_UNLOCK_FACE_HIGHTLIGHT = 31;
        int FACE_UNLOCK_FACE_KEEP = 19;
        int FACE_UNLOCK_FACE_MOUTH_OCCLUSION = 23;
        int FACE_UNLOCK_FACE_MULTI = 27;
        int FACE_UNLOCK_FACE_NOT_COMPLETE = 29;
        int FACE_UNLOCK_FACE_NOT_FOUND = 5;
        int FACE_UNLOCK_FACE_OFFSET_BOTTOM = 11;
        int FACE_UNLOCK_FACE_OFFSET_LEFT = 8;
        int FACE_UNLOCK_FACE_OFFSET_RIGHT = 10;
        int FACE_UNLOCK_FACE_OFFSET_TOP = 9;
        int FACE_UNLOCK_FACE_RISE = 16;
        int FACE_UNLOCK_FACE_ROTATED_LEFT = 15;
        int FACE_UNLOCK_FACE_ROTATED_RIGHT = 17;
        int FACE_UNLOCK_FACE_SCALE_TOO_LARGE = 7;
        int FACE_UNLOCK_FACE_SCALE_TOO_SMALL = 6;
        int FACE_UNLOCK_FAILURE = 3;
        int FACE_UNLOCK_IMAGE_BLUR = 20;
        int FACE_UNLOCK_INVALID_ARGUMENT = 1;
        int FACE_UNLOCK_INVALID_HANDLE = 2;
        int FACE_UNLOCK_LIVENESS_FAILURE = 14;
        int FACE_UNLOCK_LIVENESS_WARNING = 13;
        int FACE_UNLOCK_OK = 0;
        int MG_UNLOCK_COMPARE_FAILURE = 12;
    }

    public interface FaceErrorCode {
        int ALGORITHM_NOT_INIT = 5;
        int CANCELED = 2;
        int COMPARE_FAIL = 3;
        int FAILED = 1;
        int HAL_INVALIDE = 6;
        int INVALID_PARAMETERS = 9;
        int IN_LOCKOUT_MODE = 8;
        int NO_FACE_DATA = 10;
        int OVER_MAX_FACES = 7;
        int SUCCESS = 0;
        int TIMEOUT = 4;
        int UNKNOWN = 100;
    }

    public interface FaceRecognizeCallback {
        void onCallbackEvent(int reqId, int type, int code, int errorCode);
    }
}
