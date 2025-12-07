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

package dev.skomlach.biometric.compat.engine.internal.face.miui.impl

import android.annotation.SuppressLint
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.d
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.e
import dev.skomlach.common.contextprovider.AndroidContext
import dev.skomlach.common.misc.SystemStringsHelper
import java.lang.reflect.Field

@SuppressLint("StaticFieldLeak")
object MiuiCodeToString {

    private var stringArrayFields: Array<Field>? = null


    init {
        try {
            stringArrayFields = Class.forName("com.android.internal.R\$array").declaredFields
        } catch (e: Throwable) {
            e(e)
        }
    }


    private fun getStringArray(s: String): Array<String>? {
        stringArrayFields?.let {
            try {
                for (field in it) {
                    if (s == field.name) {
                        val isAccessible = field.isAccessible
                        return try {
                            if (!isAccessible) field.isAccessible = true
                            AndroidContext.appContext.resources.getStringArray(field[null] as Int)
                        } finally {
                            if (!isAccessible) field.isAccessible = false
                        }
                    }
                }
            } catch (e: Throwable) {
                e(e)
            }
        }
        return null
    }

    fun getErrorString(errMsg: Int, vendorCode: Int): String? {
        val context = AndroidContext.appContext
        when (errMsg) {
            1 -> return SystemStringsHelper.getFromSystem(context, "face_error_hw_not_available")
            2 -> return SystemStringsHelper.getFromSystem(context, "face_error_unable_to_process")
            3 -> return SystemStringsHelper.getFromSystem(context, "face_error_timeout")
            4 -> return SystemStringsHelper.getFromSystem(context, "face_error_no_space")
            5 -> return SystemStringsHelper.getFromSystem(context, "face_error_canceled")
            7 -> return SystemStringsHelper.getFromSystem(context, "face_error_lockout")
            8 -> {
                try {
                    val msgArray = getStringArray("face_error_vendor")
                    if (msgArray != null && vendorCode < msgArray.size) {
                        return msgArray[vendorCode]
                    }
                } catch (_: Exception) {
                }
            }

            9 -> return SystemStringsHelper.getFromSystem(context, "face_error_lockout_permanent")
            10 -> return SystemStringsHelper.getFromSystem(context, "face_error_user_canceled")
            11 -> return SystemStringsHelper.getFromSystem(context, "face_error_not_enrolled")
            12 -> return SystemStringsHelper.getFromSystem(context, "face_error_hw_not_present")
        }
        val stringBuilder = StringBuilder()
        stringBuilder.append("Invalid error message: ")
        stringBuilder.append(errMsg)
        stringBuilder.append(", ")
        stringBuilder.append(vendorCode)
        d(stringBuilder.toString())
        return null
    }

    fun getAcquiredString(acquireInfo: Int, vendorCode: Int): String? {
        val context = AndroidContext.appContext
        when (acquireInfo) {
            0 -> return null
            1 -> return SystemStringsHelper.getFromSystem(context, "face_acquired_insufficient")
            2 -> return SystemStringsHelper.getFromSystem(context, "face_acquired_too_bright")
            3 -> return SystemStringsHelper.getFromSystem(context, "face_acquired_too_dark")
            4 -> return SystemStringsHelper.getFromSystem(context, "face_acquired_too_close")
            5 -> return SystemStringsHelper.getFromSystem(context, "face_acquired_too_far")
            6 -> return SystemStringsHelper.getFromSystem(context, "face_acquired_too_high")
            7 -> return SystemStringsHelper.getFromSystem(context, "face_acquired_too_low")
            8 -> return SystemStringsHelper.getFromSystem(context, "face_acquired_too_right")
            9 -> return SystemStringsHelper.getFromSystem(context, "face_acquired_too_left")
            10 -> return SystemStringsHelper.getFromSystem(context, "face_acquired_poor_gaze")
            11 -> return SystemStringsHelper.getFromSystem(context, "face_acquired_not_detected")
            12 -> return SystemStringsHelper.getFromSystem(context, "face_acquired_too_much_motion")
            13 -> return SystemStringsHelper.getFromSystem(context, "face_acquired_recalibrate")
            14 -> return SystemStringsHelper.getFromSystem(context, "face_acquired_too_different")
            15 -> return SystemStringsHelper.getFromSystem(context, "face_acquired_too_similar")
            16 -> return SystemStringsHelper.getFromSystem(context, "face_acquired_pan_too_extreme")
            17 -> return SystemStringsHelper.getFromSystem(
                context,
                "face_acquired_tilt_too_extreme"
            )

            18 -> return SystemStringsHelper.getFromSystem(
                context,
                "face_acquired_roll_too_extreme"
            )

            19 -> return SystemStringsHelper.getFromSystem(context, "face_acquired_obscured")
            20 -> return null
            21 -> return SystemStringsHelper.getFromSystem(context, "face_acquired_sensor_dirty")
            22 -> {
                try {
                    val msgArray = getStringArray("face_acquired_vendor")
                    if (msgArray != null && vendorCode < msgArray.size) {
                        return msgArray[vendorCode]
                    }
                } catch (_: Exception) {
                }
            }
        }
        val stringBuilder = StringBuilder()
        stringBuilder.append("Invalid acquired message: ")
        stringBuilder.append(acquireInfo)
        stringBuilder.append(", ")
        stringBuilder.append(vendorCode)
        d(stringBuilder.toString())
        return null
    }
}