/*
 *  Copyright (c) 2023 Sergey Komlach aka Salat-Cx65; Original project https://github.com/Salat-Cx65/AdvancedBiometricPromptCompat
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

package dev.skomlach.biometric.compat.engine.internal.face.huawei.impl

import android.view.Surface
import com.huawei.facerecognition.FaceRecognizeManager
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.d
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.e

class HuaweiFaceManagerV1Impl : HuaweiFaceManagerV1() {

    companion object {
        private const val FACE_AUTH_VERSION_V1 = 1
        private const val HUAWEI_OP_FAIL = -1
        private const val HUAWEI_OP_SUCCESS = 0
        private const val TAG = "HuaweiFaceManagerV1Impl"
        private const val REQ_ID = 0
        private const val TYPE_AUTH = FaceRecognizeManager.TYPE_CALLBACK_AUTH
    }

    init {
        HuaweiFaceRecognizeManager.createInstance()
    }

    override fun authenticate(callback: AuthenticatorCallback?, surface: Surface?) {
        val reqID = REQ_ID
        val type = TYPE_AUTH
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
            stringBuilder.append(" flag is ")
            stringBuilder.append(type)
            d(str, stringBuilder.toString())
            val ret = frManager.init()
            if (ret != HUAWEI_OP_SUCCESS) {
                val str2 = TAG
                val stringBuilder2 = StringBuilder()
                stringBuilder2.append("init failed returning ")
                stringBuilder2.append(ret)
                e(str2, stringBuilder2.toString())
                callback.onAuthenticationError(HuaweiFaceRecognizeManager.HUAWEI_FACE_AUTH_ERROR_HW_UNAVAILABLE)
                return
            }
            d(TAG, "authenicating... ")
            HuaweiFaceRecognizeManager.fRManager?.authenticate(reqID, type, surface)
        }
    }

    override fun cancel(): Int {
        d(TAG, "canceling...")
        if (HuaweiFaceRecognizeManager.instance == null) {
            e(TAG, "HuaweiFaceRecognizeManager is null")
            return -1
        }
        HuaweiFaceRecognizeManager.fRManager?.cancelAuthenticate(REQ_ID)
        HuaweiFaceRecognizeManager.fRManager?.release()
        HuaweiFaceRecognizeManager.createInstance()
        return HUAWEI_OP_SUCCESS
    }

    override val version: Int
        get() = FACE_AUTH_VERSION_V1
    override val isHardwareDetected: Boolean
        get() = try {
            try {
                HuaweiFaceRecognizeManager.fRManager?.faceRecognitionAbility?.isFaceRecognitionSupport == true
            } catch (e: Throwable) {
                HuaweiFaceRecognizeManager.fRManager?.let {
                    return (it.hardwareSupportType and 1) !== 0
                }
                false
            }
        } catch (ignore: Throwable) {
            false
        }

    override fun hasEnrolledTemplates(): Boolean {
        return getEnrolledTemplates()?.isNotEmpty() == true
    }

    override fun getEnrolledTemplates(): IntArray? {
        return try {
            HuaweiFaceRecognizeManager.fRManager?.getEnrolledFaceIDs()
        } catch (ignore: Throwable) {
            null
        }
    }
}