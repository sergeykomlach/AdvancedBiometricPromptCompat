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

package dev.skomlach.biometric.compat.utils

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.hardware.SensorPrivacyManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Process
import androidx.core.app.AppOpsManagerCompat
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl
import dev.skomlach.common.contextprovider.AndroidContext.appContext
import dev.skomlach.common.misc.ExecutorHelper
import dev.skomlach.common.misc.Utils
import dev.skomlach.common.permissions.AppOpCompatConstants
import dev.skomlach.common.permissions.PermissionUtils
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@SuppressLint("NewApi")
object SensorPrivacyCheck {
    const val CHECK_TIMEOUT = 1_000L


    private var isCameraInUse = Pair(0L, false)
    private var isCameraBlocked = Pair(0L, false)
    private val selfCamUse = AtomicInteger(0)
    fun notifySelfCameraOpened() = selfCamUse.incrementAndGet()
    fun notifySelfCameraClosed() {
        if (selfCamUse.decrementAndGet() < 0) selfCamUse.set(0)
    }

    fun isCameraInUse(): Boolean = selfCamUse.get() == 0 && isCameraInUseInternal()
    private fun isCameraInUseInternal(): Boolean {
        if (System.currentTimeMillis() - isCameraInUse.first <= CHECK_TIMEOUT) {
            return isCameraInUse.second
        }
        val ts = System.currentTimeMillis()
        val delay =
            CHECK_TIMEOUT
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
                Thread.sleep(10)
            } catch (ignore: InterruptedException) {
            }
        }

        return isCameraInUse.second
    }

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
                    CHECK_TIMEOUT
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
                try {
                    super.onCameraAvailable(cameraId)
                    cameraManager?.getCameraCharacteristics(cameraId)?.let {
                        if (it.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
                            try {
                                isCameraInUse = Pair(System.currentTimeMillis(), false)
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
                try {
                    super.onCameraUnavailable(cameraId)
                    if (selfCamUse.get() > 0) {
                        unregisterListener()
                        return
                    }
                    cameraManager?.getCameraCharacteristics(cameraId)?.let {
                        if (it.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
                            try {
                                isCameraInUse = Pair(System.currentTimeMillis(), true)
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

    fun isCameraBlocked(): Boolean {
        return selfCamUse.get() == 0 && if (Utils.isAtLeastS && !DevicesWithKnownBugs.systemDealWithBiometricPrompt) {
            if (System.currentTimeMillis() - isCameraBlocked.first <= CHECK_TIMEOUT) {
                return isCameraBlocked.second
            }
            val isBlocked = checkIsPrivacyToggled(SensorPrivacyManager.Sensors.CAMERA)
            isCameraBlocked = Pair(System.currentTimeMillis(), isBlocked)
            isBlocked
        } else
            false
    }


    private fun checkIsPrivacyToggled(sensor: Int): Boolean {
        try {
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
                        AppOpsManagerCompat.checkOrNoteProxyOp(
                            appContext,
                            Process.myUid(),
                            permissionToOp,
                            appContext.packageName
                        )
                    } catch (_: Throwable) {
                        PermissionUtils.INSTANCE.appOpPermissionsCheckMiui(
                            permissionToOp,
                            Process.myUid(),
                            appContext.packageName
                        )
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