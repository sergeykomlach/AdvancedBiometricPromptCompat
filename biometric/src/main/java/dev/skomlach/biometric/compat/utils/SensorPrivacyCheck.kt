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
import dev.skomlach.biometric.compat.impl.SensorBlockedFallbackFragment
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl
import dev.skomlach.common.contextprovider.AndroidContext
import dev.skomlach.common.misc.ExecutorHelper
import dev.skomlach.common.misc.Utils
import dev.skomlach.common.permissions.AppOpCompatConstants
import dev.skomlach.common.permissions.PermissionUtils
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

@SuppressLint("NewApi")
object SensorPrivacyCheck {
    const val CHECK_TIMEOUT = 5_000L
    private val appContext = AndroidContext.appContext
    private var isCameraInUseTime = AtomicLong(0)
    private var isCameraInUse = AtomicBoolean(false)

    //Workaround that allow do not spam the user
    private var isUiRequested = AtomicBoolean(false)
    private var lastCheckedTime = AtomicLong(0)
    private var lastKnownState = AtomicBoolean(false)
    fun isCameraInUse(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (System.currentTimeMillis() - isCameraInUseTime.get() <= CHECK_TIMEOUT) {
                return isCameraInUse.get()
            }
            val ts = System.currentTimeMillis()
            val delay =
                appContext.resources.getInteger(android.R.integer.config_longAnimTime)
                    .toLong()
            val isDone = AtomicBoolean(false)
            //Fix for `Non-fatal Exception: java.lang.IllegalArgumentException: No handler given, and current thread has no looper!`
            ExecutorHelper.startOnBackground {
                try {

                    val cameraManager =
                        appContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                    cameraManager.registerAvailabilityCallback(
                        ExecutorHelper.backgroundExecutor,
                        getCameraCallback(cameraManager, isDone)
                    )

                } catch (e: Throwable) {
                    BiometricLoggerImpl.e(e)
                }
            }
            while (!isDone.get() && System.currentTimeMillis() - ts <= delay) {
                try {
                    Thread.sleep(20)
                } catch (ignore: InterruptedException) {
                }
            }
        }
        isCameraInUseTime.set(System.currentTimeMillis())
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
                    appContext.resources.getInteger(android.R.integer.config_longAnimTime)
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
            if (System.currentTimeMillis() - lastCheckedTime.get() <= CHECK_TIMEOUT) {
                return lastKnownState.get()
            } else if (isUiRequested.get() &&
                SensorBlockedFallbackFragment.isUnblockDialogShown()
            ) {
                lastKnownState.set(true)
                lastCheckedTime.set(System.currentTimeMillis())
                return lastKnownState.get()
            }

            isUiRequested.set(false)
            val sensorPrivacyManager: SensorPrivacyManager? =
                appContext.getSystemService(SensorPrivacyManager::class.java)
            if (sensorPrivacyManager?.supportsSensorToggle(sensor) == true) {
                try {
                    val permissionToOp: String =
                        AppOpCompatConstants.getAppOpFromPermission(
                            if (sensor == SensorPrivacyManager.Sensors.CAMERA)
                                Manifest.permission.CAMERA else Manifest.permission.RECORD_AUDIO
                        ) ?: return false

                    val noteOp: Int = try {
                        AppOpsManagerCompat.noteOpNoThrow(
                            appContext,
                            permissionToOp,
                            Process.myUid(),
                            appContext.packageName
                        )
                    } catch (ignored: Throwable) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
                            PermissionUtils.appOpPermissionsCheckMiui(
                                permissionToOp,
                                Process.myUid(),
                                appContext.packageName
                            ) else AppOpsManagerCompat.MODE_IGNORED
                    }
                    return (noteOp != AppOpsManagerCompat.MODE_ALLOWED).also {
                        lastKnownState.set(it)
                        lastCheckedTime.set(System.currentTimeMillis())
                        if (it) {
                            isUiRequested.set(true)
                            if (sensor == SensorPrivacyManager.Sensors.CAMERA)
                                SensorBlockedFallbackFragment.askForCameraUnblock()
                            else
                                SensorBlockedFallbackFragment.askForMicUnblock()
                        }
                    }
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