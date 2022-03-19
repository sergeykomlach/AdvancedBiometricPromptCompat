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
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Process
import androidx.core.app.AppOpsManagerCompat
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl
import dev.skomlach.common.contextprovider.AndroidContext
import dev.skomlach.common.misc.ExecutorHelper
import dev.skomlach.common.misc.Utils
import dev.skomlach.common.permissions.AppOpCompatConstants
import dev.skomlach.common.permissions.PermissionUtils
import java.util.concurrent.atomic.AtomicBoolean

@SuppressLint("NewApi")
object SensorPrivacyCheck {
// TODO: Just a references to system resources
//   [`sensor_privacy_start_use_camera_notification_content_title`->`Unblock device camera`]
//   [`sensor_privacy_start_use_dialog_turn_on_button`->`Unblock`]
//   [`sensor_privacy_start_use_mic_notification_content_title`->`Unblock device microphone`]
//   [`face_sensor_privacy_enabled`->`To use Face Unlock, turn on Camera access in Settings > Privacy`]

    private var isCameraInUse = AtomicBoolean(false)

    fun isCameraInUse(): Boolean {
        val ts = System.currentTimeMillis()
        val delay = AndroidContext.appContext.resources.getInteger(android.R.integer.config_shortAnimTime)
            .toLong()
        val isDone = AtomicBoolean(false)
        //Fix for `Non-fatal Exception: java.lang.IllegalArgumentException: No handler given, and current thread has no looper!`
        ExecutorHelper.startOnBackground {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    val cameraManager =
                        AndroidContext.appContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                    cameraManager.registerAvailabilityCallback(
                        ExecutorHelper.backgroundExecutor,
                        getCameraCallback(cameraManager, isDone)
                    )
                }
            } catch (e: Throwable) {
                BiometricLoggerImpl.e(e)
            }
        }
        while (!isDone.get() && System.currentTimeMillis() -  ts <= delay) {
            try {
                Thread.sleep(20)
            } catch (ignore: InterruptedException) {
            }
        }
        return isCameraInUse.get()
    }


    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private fun getCameraCallback(
        cameraManager: CameraManager?,
        isDone: AtomicBoolean
    ): CameraManager.AvailabilityCallback {
        return object : CameraManager.AvailabilityCallback() {
            init {
                ExecutorHelper.startOnBackground(
                    {
                        try {
                            cameraManager?.unregisterAvailabilityCallback(this)
                        } catch (e: Throwable) {
                            BiometricLoggerImpl.e(e)
                        } finally {
                            isDone.set(true)
                        }
                    },
                    AndroidContext.appContext.resources.getInteger(android.R.integer.config_shortAnimTime)
                        .toLong()
                )
            }

            private fun unregisterListener() {
                //Fix for `Non-fatal Exception: java.lang.IllegalArgumentException: No handler given, and current thread has no looper!`
                ExecutorHelper.startOnBackground {
                    try {
                        cameraManager?.unregisterAvailabilityCallback(this)
                    } catch (e: Throwable) {
                        BiometricLoggerImpl.e(e)
                    } finally {
                        isDone.set(true)
                    }
                }
            }

            override fun onCameraAvailable(cameraId: String) {
                if (isCameraBlocked()) {
                    try {
                        isCameraInUse.set(false)
                    } finally {
                        unregisterListener()
                    }
                    return
                }
                try {
                    super.onCameraAvailable(cameraId)
                    cameraManager?.getCameraCharacteristics(cameraId)?.let {
                        if (it.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
                            try {
                                isCameraInUse.set(false)
                            } finally {
                                unregisterListener()
                            }
                        }
                    }
                } catch (e: Throwable) {
                    //Caused by android.hardware.camera2.CameraAccessException: CAMERA_DISCONNECTED (2): Camera service is currently unavailable
//                    if (e is CameraAccessException &&
//                        e.message?.contains("CAMERA_DISCONNECTED ($cameraId): Camera service is currently unavailable") == true &&
//                        cameraId == facingCamera
//                    ) {
//                        isCameraInUse = true
//                    } else
                    BiometricLoggerImpl.e(e)
                }
            }

            override fun onCameraUnavailable(cameraId: String) {
                if (isCameraBlocked()) {
                    try {
                        isCameraInUse.set(false)
                    } finally {
                        unregisterListener()
                    }
                    return
                }
                try {
                    super.onCameraUnavailable(cameraId)
                    cameraManager?.getCameraCharacteristics(cameraId)?.let {
                        if (it.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
                            try {
                                isCameraInUse.set(true)
                            } finally {
                                unregisterListener()
                            }
                        }
                    }
                } catch (e: Throwable) {
                    //Caused by android.hardware.camera2.CameraAccessException: CAMERA_DISCONNECTED (2): Camera service is currently unavailable
//                    if (e is CameraAccessException &&
//                        e.message?.contains("CAMERA_DISCONNECTED ($cameraId): Camera service is currently unavailable") == true &&
//                        cameraId == facingCamera
//                    ) {
//                        isCameraInUse = true
//                    } else
                    BiometricLoggerImpl.e(e)
                }
            }
        }
    }

    //Android 12 stuff
    fun isCameraBlocked(): Boolean {
        return Utils.isAtLeastS && checkIsPrivacyToggled(SensorPrivacyManager.Sensors.CAMERA)
    }

    @TargetApi(Build.VERSION_CODES.S)
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


}