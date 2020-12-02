package dev.skomlach.common.misc

import android.app.Activity
import android.content.Context
import android.os.Build
import android.view.View
import android.view.Window

fun isActivityFinished(context: Context?): Boolean {
    if (context == null) return true
    return if (context is Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            context.isDestroyed || context.isFinishing
        } else {
            context.isFinishing
        }
    } else false
}

fun hasWindowFocus(w: Activity?): Boolean {
    return !isActivityFinished(w) && hasWindowFocus(w?.window)
}

fun hasWindowFocus(w: Window?): Boolean {
    if (w == null)
        return false
    return w.findViewById<View>(Window.ID_ANDROID_CONTENT).hasWindowFocus()
}