package dev.skomlach.common.permissionui

import android.content.pm.PackageManager
import androidx.fragment.app.Fragment
import dev.skomlach.common.misc.Utils

fun resolveApplicationTitle(fragment: Fragment): CharSequence {
    val activity = fragment.requireActivity()
    return try {
        val appInfo = if (Utils.isAtLeastT) {
            activity.packageManager.getApplicationInfo(
                activity.application.packageName,
                PackageManager.ApplicationInfoFlags.of(0L)
            )
        } else {
            activity.packageManager.getApplicationInfo(
                activity.application.packageName,
                0
            )
        }
        activity.packageManager.getApplicationLabel(appInfo).ifEmpty {
            activity.packageName
        }
    } catch (_: Throwable) {
        activity.packageName
    }
}
