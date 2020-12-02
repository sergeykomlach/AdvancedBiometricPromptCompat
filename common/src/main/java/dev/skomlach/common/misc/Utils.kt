package dev.skomlach.common.misc

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.os.BuildCompat
import timber.log.Timber
import java.util.*

object Utils {
    @JvmStatic
    val isAtLeastR: Boolean
        get() = (BuildCompat.isAtLeastR() //check only Preview
                || Build.VERSION.SDK_INT >= 30) //check also release

    @JvmStatic
    fun startActivity(intent: Intent, context: Context): Boolean {
        try {
            if (intentCanBeResolved(intent, context)) {
                context.startActivity(
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                return true
            }
        } catch (throwable: Throwable) {
            Timber.e(throwable)
        }
        return false
    }

    private fun intentCanBeResolved(intent: Intent, context: Context): Boolean {
        val pm = context.packageManager
        val pkgAppsList = pm.queryIntentActivities(intent, 0)
        return pkgAppsList.size > 0
    }

    @JvmStatic
    fun checkClass(className: String): Boolean {
        try {
            return Class.forName(className) != null
        } catch (e: Throwable) {
        }
        return false
    }
}