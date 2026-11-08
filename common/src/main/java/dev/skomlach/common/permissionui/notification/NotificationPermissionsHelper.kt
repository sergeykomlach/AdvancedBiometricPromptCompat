package dev.skomlach.common.permissionui.notification

import android.app.Activity
import androidx.annotation.RestrictTo
import androidx.fragment.app.FragmentActivity
import dev.skomlach.common.misc.ExecutorHelper
import dev.skomlach.common.permissions.PermissionUtils

object NotificationPermissionsHelper {
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    fun checkOwned(host: FragmentActivity, channelId: String, complete: (Boolean) -> Unit): () -> Unit {
        var cancelCurrent: (() -> Unit)? = null
        fun checkChannel() {
            if (PermissionUtils.INSTANCE.isAllowedNotificationsChannelPermission(channelId)) {
                complete(true)
            } else {
                cancelCurrent = NotificationPermissionsFragment.launchOwned(
                    host, PermissionRequestController.PermissionType.CHANNEL_PERMISSION, channelId, complete
                )
            }
        }
        val start = Runnable {
            if (host.isDestroyed || host.isFinishing || host.supportFragmentManager.isStateSaved) {
                complete(false)
                return@Runnable
            }
            if (PermissionUtils.INSTANCE.isAllowedNotificationsPermission) {
                checkChannel()
            } else {
                cancelCurrent = NotificationPermissionsFragment.launchOwned(
                    host, PermissionRequestController.PermissionType.GENERAL_PERMISSION, null
                ) { returned ->
                    if (returned && PermissionUtils.INSTANCE.isAllowedNotificationsPermission) checkChannel()
                    else complete(returned)
                }
            }
        }
        ExecutorHelper.post(start)
        return {
            ExecutorHelper.removeCallbacks(start)
            cancelCurrent?.invoke()
        }
    }

    fun checkNotificationPermissions(
        context: Activity?,
        channelId: String,
        runnableOk: Runnable,
        runnableFailed: Runnable? = null
    ) {
        if (!PermissionUtils.INSTANCE.isAllowedNotificationsPermission) {
            PermissionRequestController.askNotificationsPermission(context, {
                checkNotificationChannelPermissions(
                    context, channelId, runnableOk, runnableFailed
                )
            }) {
                runnableFailed?.run()
            }
        } else {
            checkNotificationChannelPermissions(context, channelId, runnableOk, runnableFailed)
        }
    }

    private fun checkNotificationChannelPermissions(
        context: Activity?, channelId: String, runnableOk: Runnable, runnableFailed: Runnable?
    ) {
        if (!PermissionUtils.INSTANCE.isAllowedNotificationsChannelPermission(channelId)) {
            PermissionRequestController.askNotificationsChannelsPermission(
                context,
                channelId,
                { runnableOk.run() }) {
                runnableFailed?.run()
            }
        } else {
            runnableOk.run()
        }
    }

}
