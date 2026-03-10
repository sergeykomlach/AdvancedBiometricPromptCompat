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
import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Process
import androidx.core.app.AppOpsManagerCompat
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl
import dev.skomlach.common.contextprovider.AndroidContext.appContext
import dev.skomlach.common.permissions.AppOpCompatConstants
import dev.skomlach.common.permissions.PermissionUtils
import java.util.concurrent.atomic.AtomicInteger


object SensorPrivacyCheck {
    const val CHECK_TIMEOUT = 500L


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
        isCameraInUse = Pair(System.currentTimeMillis(), isCameraServiceReportingBusy())
        return isCameraInUse.second
    }

    fun isCameraBlocked(): Boolean {
        if (selfCamUse.get() > 0) return false

        if (System.currentTimeMillis() - isCameraBlocked.first <= CHECK_TIMEOUT) {
            return isCameraBlocked.second
        }
        val isBlocked = isSensorOperationallyBlocked(Manifest.permission.CAMERA)
        isCameraBlocked = Pair(System.currentTimeMillis(), isBlocked)
        return isBlocked

    }

    private fun isCameraServiceReportingBusy(): Boolean {
        val cameraManager =
            appContext.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return false
        return try {
            val ids = cameraManager.cameraIdList
            if (ids.isEmpty()) return true
            val faceCameraId = ids.firstOrNull { id ->
                val facing = cameraManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.LENS_FACING)
                facing == CameraCharacteristics.LENS_FACING_FRONT
            } ?: ids[0]
            cameraManager.getCameraCharacteristics(faceCameraId)
            false
        } catch (e: CameraAccessException) {
            e.reason == CameraAccessException.CAMERA_DISABLED || e.reason == CameraAccessException.CAMERA_IN_USE
        } catch (e: Throwable) {
            false
        }
    }

    private fun isSensorOperationallyBlocked(permission: String): Boolean {
        try {
            //If permission not granted - AppOpp always blocked
            if (!PermissionUtils.INSTANCE.hasSelfPermissions(permission)) return false

            val permissionToOp: String =
                AppOpCompatConstants.getAppOpFromPermission(permission) ?: return false

            val noteOp: Int = PermissionUtils.INSTANCE.appOpPermissionsCheck(
                permissionToOp,
                Process.myUid(),
                appContext.packageName
            )
            return noteOp != AppOpsManagerCompat.MODE_ALLOWED

        } catch (e: Throwable) {
            BiometricLoggerImpl.e(e)
        }
        return false
    }


}