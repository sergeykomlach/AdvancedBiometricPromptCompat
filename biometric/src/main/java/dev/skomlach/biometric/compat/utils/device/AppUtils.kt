package dev.skomlach.biometric.compat.utils.device

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.e
import java.util.*

object AppUtils {


    fun createIntent(): Intent {
        val intent = Intent()
        intent.addCategory(Intent.CATEGORY_DEFAULT)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return intent
    }

    fun isSystemPkgInstalled(ctx: Context, pkg: String): Boolean {
        return try {
            val mgr = ctx.packageManager
            mgr.getApplicationInfo(
                pkg,
                0
            ).flags and ApplicationInfo.FLAG_SYSTEM != ApplicationInfo.FLAG_SYSTEM
        } catch (e: Throwable) {
            false
        }
    }

    fun isIntentAvailable(ctx: Context, intent: Intent): Boolean {
        return try {
            val mgr = ctx.packageManager
            val resolveInfoList: MutableList<ResolveInfo> = ArrayList()
            val list = mgr.queryIntentActivities(intent, 0)
            for (resolveInfo in list) {
                if (resolveInfo.activityInfo != null && resolveInfo.activityInfo.exported) {
                    resolveInfoList.add(resolveInfo)
                }
            }
            resolveInfoList.size > 0
        } catch (e: Throwable) {
            e(e)
            false
        }
    }
}