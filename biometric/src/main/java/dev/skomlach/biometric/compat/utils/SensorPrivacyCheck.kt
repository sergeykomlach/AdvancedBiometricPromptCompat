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

package dev.skomlach.biometric.compat.utils

import android.Manifest
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.hardware.SensorPrivacyManager
import android.os.Build
import android.os.Process
import androidx.core.app.AppOpsManagerCompat
import dev.skomlach.common.contextprovider.AndroidContext
import dev.skomlach.common.misc.Utils
import dev.skomlach.common.permissions.AppOpCompatConstants
import dev.skomlach.common.permissions.PermissionUtils

@TargetApi(Build.VERSION_CODES.S)

object SensorPrivacyCheck {
    fun isMicrophoneBlocked(): Boolean {
        return Utils.isAtLeastS && checkIsPrivacyToggled(SensorPrivacyManager.Sensors.MICROPHONE)
    }

    fun isCameraBlocked(): Boolean {
        return Utils.isAtLeastS && checkIsPrivacyToggled(SensorPrivacyManager.Sensors.CAMERA)
    }

    @SuppressLint("PrivateApi", "BlockedPrivateApi")
    private fun checkIsPrivacyToggled(sensor: Int): Boolean {
        val sensorPrivacyManager: SensorPrivacyManager =
            AndroidContext.appContext.getSystemService(SensorPrivacyManager::class.java)
        if (sensorPrivacyManager.supportsSensorToggle(sensor)) {
            try {
                val permissionToOp: String =
                    AppOpCompatConstants.getAppOpFromPermission(
                        if (sensor == SensorPrivacyManager.Sensors.CAMERA)
                            Manifest.permission.CAMERA else Manifest.permission.RECORD_AUDIO
                    ) ?: return false

                val noteOp: Int = try {
                    AppOpsManagerCompat.noteOpNoThrow(
                        AndroidContext.appContext,
                        permissionToOp,
                        Process.myUid(),
                        AndroidContext.appContext.packageName
                    )
                } catch (ignored: Throwable) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
                        PermissionUtils.appOpPermissionsCheckMiui(
                            permissionToOp,
                            Process.myUid(),
                            AndroidContext.appContext.packageName
                        ) else AppOpsManagerCompat.MODE_IGNORED
                }
                return noteOp != AppOpsManagerCompat.MODE_ALLOWED
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
        return false
    }
}