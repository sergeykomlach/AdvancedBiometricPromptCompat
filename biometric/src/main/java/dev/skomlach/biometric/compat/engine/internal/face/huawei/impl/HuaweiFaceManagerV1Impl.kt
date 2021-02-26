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