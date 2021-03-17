package org.chickenhook.restrictionbypass

import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.e
import java.util.*

object Unseal {
    private val alreadyUnseal: MutableSet<String> = HashSet()
    fun unseal(packages: List<String?>) {
        try {
            val strings: MutableList<String> = ArrayList()
            for (p in packages) {
                var pkg = p
                if (!(pkg == null || pkg.isEmpty())) {
                    if (pkg.endsWith("/")) pkg = pkg.substring(0, pkg.length - 1)
                    strings.add("L" + pkg.replace(".", "/"))
                }
            }
            unsealUseList(strings)
        } catch (e: Throwable) {
            e(e)
        }
    }

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
        list.removeAll(alreadyUnseal)
        if (list.isEmpty()) //already unsealed
            return
        alreadyUnseal.addAll(list)
        val getRuntime = RestrictionBypass.getDeclaredMethod(
            Class.forName("dalvik.system.VMRuntime"),
            "getRuntime"
        )
        getRuntime!!.isAccessible = true
        val vmRuntime = getRuntime.invoke(null)
        val setHiddenApiExemptions = RestrictionBypass.getDeclaredMethod(
            vmRuntime.javaClass,
            "setHiddenApiExemptions",
            Array<String>::class.java
        )
        setHiddenApiExemptions!!.isAccessible = true
        val args = arrayOfNulls<Any>(1)
        args[0] = list.toTypedArray()
        setHiddenApiExemptions.invoke(vmRuntime, *args)
        // setHiddenApiExemptions
    }
}