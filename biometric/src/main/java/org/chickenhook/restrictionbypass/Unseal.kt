package org.chickenhook.restrictionbypass

import android.os.Build
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.e
import java.util.*

object Unseal {

    fun unsealAll() {
        try {
            val list: MutableList<String> = ArrayList()
            list.add("L")
            unsealUseList(list)
        } catch (e: Throwable) {
            e(e)
        }
    }

    @Throws(Exception::class)
    private fun unsealUseList(list: MutableList<String>) {
        if (Build.VERSION.SDK_INT < 28) {
            return
        }
        val getRuntime = RestrictionBypass.getDeclaredMethod(
            Class.forName("dalvik.system.VMRuntime"),
            "getRuntime"
        )
        getRuntime?.isAccessible = true
        val vmRuntime = getRuntime?.invoke(null)
        val setHiddenApiExemptions = RestrictionBypass.getDeclaredMethod(
            vmRuntime?.javaClass,
            "setHiddenApiExemptions",
            Array<String>::class.java
        )
        setHiddenApiExemptions?.isAccessible = true
        val args = arrayOfNulls<Any>(1)
        args[0] = list.toTypedArray()
        setHiddenApiExemptions?.invoke(vmRuntime, *args)
        // setHiddenApiExemptions
    }
}