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
import dev.skomlach.biometric.compat.R
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.d
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.e
import dev.skomlach.common.contextprovider.AndroidContext
import dev.skomlach.common.translate.LocalizationHelper

@SuppressLint("StaticFieldLeak")
object MiuiCodeToString {

    @SuppressLint("DiscouragedApi")
    private fun getStringArray(name: String): Array<String>? = try {
        val resources = AndroidContext.appContext.resources
        val id = resources.getIdentifier(name, "array", "android")
        if (id == 0) null else resources.getStringArray(id)
    } catch (error: Exception) {
        e(error)
        null
    }

    fun getErrorString(errMsg: Int, vendorCode: Int): String? {
        val context = AndroidContext.appContext
        when (errMsg) {
            1 -> return LocalizationHelper.getLocalizedString(
                context,
                R.string.biometriccompat_face_error_hw_not_available
            )

            2 -> return LocalizationHelper.getLocalizedString(
                context,
                R.string.biometriccompat_face_error_unable_to_process
            )

            3 -> return LocalizationHelper.getLocalizedString(
                context,
                R.string.biometriccompat_face_error_timeout
            )

            4 -> return LocalizationHelper.getLocalizedString(
                context,
                R.string.biometriccompat_face_error_no_space
            )

            5 -> return LocalizationHelper.getLocalizedString(
                context,
                R.string.biometriccompat_face_error_canceled
            )

            7 -> return LocalizationHelper.getLocalizedString(
                context,
                R.string.biometriccompat_face_error_lockout
            )

            8 -> {
                try {
                    val msgArray = getStringArray("face_error_vendor")
                    if (msgArray != null && vendorCode in msgArray.indices) {
                        return msgArray[vendorCode]
                    }
                } catch (_: Exception) {
                }
                return LocalizationHelper.getLocalizedString(
                    context,
                    R.string.biometriccompat_face_error_vendor_unknown
                )
            }

            9 -> return LocalizationHelper.getLocalizedString(
                context,
                R.string.biometriccompat_face_error_lockout_permanent
            )

            10 -> return LocalizationHelper.getLocalizedString(
                context,
                R.string.biometriccompat_face_error_user_canceled
            )

            11 -> return LocalizationHelper.getLocalizedString(
                context,
                R.string.biometriccompat_face_error_not_enrolled
            )

            12 -> return LocalizationHelper.getLocalizedString(
                context,
                R.string.biometriccompat_face_error_hw_not_present
            )
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
            1 -> return LocalizationHelper.getLocalizedString(
                context,
                R.string.biometriccompat_face_acquired_insufficient
            )

            2 -> return LocalizationHelper.getLocalizedString(
                context,
                R.string.biometriccompat_face_acquired_too_bright
            )

            3 -> return LocalizationHelper.getLocalizedString(
                context,
                R.string.biometriccompat_face_acquired_too_dark
            )

            4 -> return LocalizationHelper.getLocalizedString(
                context,
                R.string.biometriccompat_face_acquired_too_close
            )

            5 -> return LocalizationHelper.getLocalizedString(
                context,
                R.string.biometriccompat_face_acquired_too_far
            )

            6 -> return LocalizationHelper.getLocalizedString(
                context,
                R.string.biometriccompat_face_acquired_too_high
            )

            7 -> return LocalizationHelper.getLocalizedString(
                context,
                R.string.biometriccompat_face_acquired_too_low
            )

            8 -> return LocalizationHelper.getLocalizedString(
                context,
                R.string.biometriccompat_face_acquired_too_right
            )

            9 -> return LocalizationHelper.getLocalizedString(
                context,
                R.string.biometriccompat_face_acquired_too_left
            )

            10 -> return LocalizationHelper.getLocalizedString(
                context,
                R.string.biometriccompat_face_acquired_poor_gaze
            )

            11 -> return LocalizationHelper.getLocalizedString(
                context,
                R.string.biometriccompat_face_acquired_not_detected
            )

            12 -> return LocalizationHelper.getLocalizedString(
                context,
                R.string.biometriccompat_face_acquired_too_much_motion
            )

            13 -> return LocalizationHelper.getLocalizedString(
                context,
                R.string.biometriccompat_face_acquired_recalibrate
            )

            14 -> return LocalizationHelper.getLocalizedString(
                context,
                R.string.biometriccompat_face_acquired_too_different
            )

            15 -> return LocalizationHelper.getLocalizedString(
                context,
                R.string.biometriccompat_face_acquired_too_similar
            )

            16 -> return LocalizationHelper.getLocalizedString(
                context,
                R.string.biometriccompat_face_acquired_pan_too_extreme
            )

            17 -> return LocalizationHelper.getLocalizedString(
                context,
                R.string.biometriccompat_face_acquired_tilt_too_extreme
            )

            18 -> return LocalizationHelper.getLocalizedString(
                context,
                R.string.biometriccompat_face_acquired_roll_too_extreme
            )

            19 -> return LocalizationHelper.getLocalizedString(
                context,
                R.string.biometriccompat_face_acquired_obscured
            )

            20 -> return null
            21 -> return LocalizationHelper.getLocalizedString(
                context,
                R.string.biometriccompat_face_acquired_sensor_dirty
            )

            22 -> {
                try {
                    val msgArray = getStringArray("face_acquired_vendor")
                    if (msgArray != null && vendorCode in msgArray.indices) {
                        return msgArray[vendorCode]
                    }
                } catch (_: Exception) {
                }
                return null
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