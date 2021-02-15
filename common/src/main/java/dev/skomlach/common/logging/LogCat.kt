package dev.skomlach.common.logging

import android.util.Log
import dev.skomlach.common.BuildConfig

object LogCat {
    var DEBUG = BuildConfig.DEBUG
    private val method: String
        get() {
            val elements = Thread.currentThread().stackTrace
            val el = elements[3]
            return el.className + ":" + el.methodName + ", " + el.fileName + ":" + el.lineNumber
        }

    @JvmStatic
    fun log(msg: String?) {
        if (DEBUG) {
            Log.d(method, msg ?: "")
        }
    }

    @JvmStatic
    fun logError(msg: String?) {
        if (DEBUG) {
            Log.e(method, msg ?: "")
        }
    }

    @JvmStatic
    fun logException(e: Throwable) {
        if (DEBUG) Log.e(method, e.message, e)
    }

    @JvmStatic
    fun logException(msg: String?, e: Throwable?) {
        if (DEBUG) Log.e(method, msg, e)
    }
}