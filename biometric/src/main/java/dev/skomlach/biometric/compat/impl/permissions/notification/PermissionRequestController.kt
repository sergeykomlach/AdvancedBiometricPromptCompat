package dev.skomlach.biometric.compat.impl.permissions.notification

import android.content.Context
import androidx.fragment.app.FragmentActivity
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.d
import dev.skomlach.common.contextprovider.AndroidContext
import dev.skomlach.common.permissions.PermissionUtils

object PermissionRequestController {
    enum class PermissionType {
        GENERAL_PERMISSION, CHANNEL_PERMISSION
    }

    fun askNotificationsPermission(context: Context, okResult: Runnable, failResult: Runnable) {
        d("PermissionRequestController", "askNotificationsPermission from $context")
        startActivityAndWait(
            context,
            PermissionType.GENERAL_PERMISSION, null, object : PermissionGrantedCallback {
                override val isGranted: Boolean
                    get() = PermissionUtils.isAllowedNotificationsPermission
            }, okResult, failResult
        )
    }

    fun askNotificationsChannelsPermission(
        context: Context,
        channelId: String?,
        okResult: Runnable,
        failResult: Runnable
    ) {

        d("PermissionRequestController", "askNotificationsChannelsPermission from $context")
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
        context: Context,
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
        AndroidContext.activity?.apply {
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