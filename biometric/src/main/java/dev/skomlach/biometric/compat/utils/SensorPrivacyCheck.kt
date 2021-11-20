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
import android.content.Context
import android.hardware.SensorPrivacyManager
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.os.Build
import android.os.Process
import androidx.core.app.AppOpsManagerCompat
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl
import dev.skomlach.common.contextprovider.AndroidContext
import dev.skomlach.common.misc.Utils
import dev.skomlach.common.permissions.AppOpCompatConstants
import dev.skomlach.common.permissions.PermissionUtils
import android.hardware.camera2.CameraCharacteristics


object SensorPrivacyCheck {
    private var cameraManager: CameraManager? = null
    private var cameraCallback: CameraManager.AvailabilityCallback? = null

    private var audioManager: AudioManager? = null
    private var micCallback: AudioManager.AudioRecordingCallback? = null
    private var isCameraInUse = false
    private var isMicInUse = false

    init {
        startCallBacks(AndroidContext.appContext)
    }

    fun isMicrophoneInUse(): Boolean {
        return isMicInUse
    }

    fun isCameraInUse(): Boolean {
        return isCameraInUse
    }

    fun isMicrophoneBlocked(): Boolean {
        return Utils.isAtLeastS && checkIsPrivacyToggled(SensorPrivacyManager.Sensors.MICROPHONE)
    }

    fun isCameraBlocked(): Boolean {
        return Utils.isAtLeastS && checkIsPrivacyToggled(SensorPrivacyManager.Sensors.CAMERA)
    }

    @TargetApi(Build.VERSION_CODES.S)
    @SuppressLint("PrivateApi", "BlockedPrivateApi")
    private fun checkIsPrivacyToggled(sensor: Int): Boolean {
        try {
            val sensorPrivacyManager: SensorPrivacyManager? =
                AndroidContext.appContext.getSystemService(SensorPrivacyManager::class.java)
            if (sensorPrivacyManager?.supportsSensorToggle(sensor) == true) {
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
                    BiometricLoggerImpl.e(e)
                }
            }
        } catch (e: Throwable) {
            BiometricLoggerImpl.e(e)
        }
        return false
    }

    private fun startCallBacks(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (cameraManager == null) cameraManager =
                context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            cameraManager?.registerAvailabilityCallback(getCameraCallback(), null)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (audioManager == null) audioManager =
                context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager?.registerAudioRecordingCallback(getMicCallback(), null)
        }

    }

    private fun stopCallBacks() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            unRegisterCameraCallBack()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            unRegisterMicCallback()
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private fun getCameraCallback(): CameraManager.AvailabilityCallback {
        cameraCallback = object : CameraManager.AvailabilityCallback() {
            override fun onCameraAvailable(cameraId: String) {
                super.onCameraAvailable(cameraId)
                cameraManager?.getCameraCharacteristics(cameraId)?.let {
                    val cOrientation = it.get(CameraCharacteristics.LENS_FACING);
                    if(cOrientation == CameraCharacteristics.LENS_FACING_FRONT) {
                        isCameraInUse = false
                    }
                }
            }

            override fun onCameraUnavailable(cameraId: String) {
                super.onCameraUnavailable(cameraId)
                cameraManager?.getCameraCharacteristics(cameraId)?.let {
                    val cOrientation = it.get(CameraCharacteristics.LENS_FACING);
                    if(cOrientation == CameraCharacteristics.LENS_FACING_FRONT) {
                        isCameraInUse = true
                    }
                }
            }
        }
        return cameraCallback as CameraManager.AvailabilityCallback
    }

    @TargetApi(Build.VERSION_CODES.N)
    private fun getMicCallback(): AudioManager.AudioRecordingCallback {
        micCallback = object : AudioManager.AudioRecordingCallback() {
            override fun onRecordingConfigChanged(configs: List<AudioRecordingConfiguration>) {
                super.onRecordingConfigChanged(configs)
                isMicInUse = configs.isNotEmpty()
            }
        }
        return micCallback as AudioManager.AudioRecordingCallback
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private fun unRegisterCameraCallBack() {
        cameraManager?.unregisterAvailabilityCallback(cameraCallback ?: return)
    }

    @TargetApi(Build.VERSION_CODES.N)
    private fun unRegisterMicCallback() {
        audioManager?.unregisterAudioRecordingCallback(micCallback ?: return)
    }

}