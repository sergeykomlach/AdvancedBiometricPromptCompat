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

package com.huawei.facerecognition

import android.content.Context
import android.view.Surface

class FaceRecognizeManager(context: Context?, callback: FaceRecognizeCallback?) {
    interface AcquireInfo {
        companion object {
            const val FACE_UNLOCK_FACE_BAD_QUALITY = 4
            const val FACE_UNLOCK_FACE_BLUR = 28
            const val FACE_UNLOCK_FACE_DARKLIGHT = 30
            const val FACE_UNLOCK_FACE_DOWN = 18
            const val FACE_UNLOCK_FACE_EYE_CLOSE = 22
            const val FACE_UNLOCK_FACE_EYE_OCCLUSION = 21
            const val FACE_UNLOCK_FACE_HALF_SHADOW = 32
            const val FACE_UNLOCK_FACE_HIGHTLIGHT = 31
            const val FACE_UNLOCK_FACE_KEEP = 19
            const val FACE_UNLOCK_FACE_MOUTH_OCCLUSION = 23
            const val FACE_UNLOCK_FACE_MULTI = 27
            const val FACE_UNLOCK_FACE_NOT_COMPLETE = 29
            const val FACE_UNLOCK_FACE_NOT_FOUND = 5
            const val FACE_UNLOCK_FACE_OFFSET_BOTTOM = 11
            const val FACE_UNLOCK_FACE_OFFSET_LEFT = 8
            const val FACE_UNLOCK_FACE_OFFSET_RIGHT = 10
            const val FACE_UNLOCK_FACE_OFFSET_TOP = 9
            const val FACE_UNLOCK_FACE_RISE = 16
            const val FACE_UNLOCK_FACE_ROTATED_LEFT = 15
            const val FACE_UNLOCK_FACE_ROTATED_RIGHT = 17
            const val FACE_UNLOCK_FACE_SCALE_TOO_LARGE = 7
            const val FACE_UNLOCK_FACE_SCALE_TOO_SMALL = 6
            const val FACE_UNLOCK_FAILURE = 3
            const val FACE_UNLOCK_IMAGE_BLUR = 20
            const val FACE_UNLOCK_INVALID_ARGUMENT = 1
            const val FACE_UNLOCK_INVALID_HANDLE = 2
            const val FACE_UNLOCK_LIVENESS_FAILURE = 14
            const val FACE_UNLOCK_LIVENESS_WARNING = 13
            const val FACE_UNLOCK_OK = 0
            const val MG_UNLOCK_COMPARE_FAILURE = 12
        }
    }

    interface FaceErrorCode {
        companion object {
            const val ALGORITHM_NOT_INIT = 5
            const val CANCELED = 2
            const val COMPARE_FAIL = 3
            const val FAILED = 1
            const val HAL_INVALIDE = 6
            const val INVALID_PARAMETERS = 9
            const val IN_LOCKOUT_MODE = 8
            const val NO_FACE_DATA = 10
            const val OVER_MAX_FACES = 7
            const val SUCCESS = 0
            const val TIMEOUT = 4
            const val UNKNOWN = 100
        }
    }

    interface FaceRecognizeCallback {
        fun onCallbackEvent(reqId: Int, type: Int, code: Int, errorCode: Int)
    }

    fun authenticate(reqId: Int, flags: Int, preview: Surface?): Int {
        return 0
    }

    fun cancelAuthenticate(reqId: Int): Int {
        return 0
    }

    fun init(): Int {
        return 0
    }

    fun release(): Int {
        return 0
    }

    val enrolledFaceIDs: IntArray?
        get() = null
    val hardwareSupportType: Int
        get() = 0

    companion object {
        const val CODE_CALLBACK_ACQUIRE = 3
        const val CODE_CALLBACK_BUSY = 4
        const val CODE_CALLBACK_CANCEL = 2
        const val CODE_CALLBACK_OUT_OF_MEM = 5
        const val CODE_CALLBACK_RESULT = 1
        const val REQUEST_OK = 0
        const val TYPE_CALLBACK_AUTH = 2
        const val TYPE_CALLBACK_ENROLL = 1
        const val TYPE_CALLBACK_REMOVE = 3
    }
}