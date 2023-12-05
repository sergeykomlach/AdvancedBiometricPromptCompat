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

package dev.skomlach.common.permissionui.notification

import android.app.Activity
import android.content.Context
import androidx.fragment.app.FragmentActivity
import dev.skomlach.common.contextprovider.AndroidContext
import dev.skomlach.common.logging.LogCat
import dev.skomlach.common.permissions.PermissionUtils

object PermissionRequestController {
    enum class PermissionType {
        GENERAL_PERMISSION, CHANNEL_PERMISSION
    }

    fun askNotificationsPermission(context: Activity?, okResult: Runnable, failResult: Runnable) {
        LogCat.log("PermissionRequestController", "askNotificationsPermission from $context")
        startActivityAndWait(
            context,
            PermissionType.GENERAL_PERMISSION, null, object : PermissionGrantedCallback {
                override val isGranted: Boolean
                    get() = PermissionUtils.isAllowedNotificationsPermission
            }, okResult, failResult
        )
    }

    fun askNotificationsChannelsPermission(
        context: Activity?,
        channelId: String?,
        okResult: Runnable,
        failResult: Runnable
    ) {

        LogCat.log(
            "PermissionRequestController",
            "askNotificationsChannelsPermission from $context"
        )
        startActivityAndWait(
            context,
            PermissionType.CHANNEL_PERMISSION, channelId, object : PermissionGrantedCallback {
                override val isGranted: Boolean
                    get() = PermissionUtils.isAllowedNotificationsChannelPermission(
                        channelId
                    )
            }, okResult, failResult
        )
    }


    private fun startActivityAndWait(
        activity: Activity?,
        type: PermissionType,
        channelId: String?,
        permissionGrantedCallback: PermissionGrantedCallback,
        okResult: Runnable,
        failResult: Runnable
    ) {

        val callback = Runnable {
            if (permissionGrantedCallback.isGranted)
                okResult.run()
            else
                failResult.run()
        }
        activity?.apply {
            if (this !is FragmentActivity) {
                callback.run()
                return
            }
            NotificationPermissionsFragment.askForPermissions(this, type, channelId, callback)
            return
        }
        callback.run()
    }

    interface PermissionGrantedCallback {
        val isGranted: Boolean
    }


}