/*
 *  Copyright (c) 2021 Sergey Komlach aka Salat-Cx65; Original project https://github.com/Salat-Cx65/AdvancedBiometricPromptCompat
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

/**Original project: https://github.com/Salat-Cx65/AdvancedBiometricPromptCompat
 * Licensed under the Apache License, Version 2.0 (the "License");
 * http://www.apache.org/licenses/LICENSE-2.0
 * @author s.komlach
 * @date 2021/3/1
 */

package com.vivo.framework.facedetect

object FaceDetectManager {
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


    //https://github.com/SivanLiu/VivoFramework/blob/8d31381ecc788afb023960535bafbfa3b7df7d9b/Vivo_y93/src/main/java/com/vivo/framework/facedetect/FaceDetectManager.java
    const val FACE_DETECT_BUSY = -3
    const val FACE_DETECT_FAILED = -1
    const val FACE_DETECT_NO_FACE = -2
    const val FACE_DETECT_SUCEESS = 0

    @Synchronized
    @JvmStatic
    fun getInstance(): FaceDetectManager? {
        return null
    }

}