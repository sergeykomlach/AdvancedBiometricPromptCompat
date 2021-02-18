package dev.skomlach.biometric.compat.utils.statusbar

import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.annotation.RestrictTo
import java.util.*

@RestrictTo(RestrictTo.Scope.LIBRARY)
object StatusBarIconsDarkMode {
    private var SYSTEM_UI_FLAG_LIGHT_STATUS_BAR = 0x00002000
    private var SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR = 0x00000010

    init {
        SYSTEM_UI_FLAG_LIGHT_STATUS_BAR = try {
            //Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
             View::class.java.getField("SYSTEM_UI_FLAG_LIGHT_STATUS_BAR").getInt(null)
        } catch (e: Exception) {
            0x00002000
        }
        SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR = try {
            //Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
           View::class.java.getField("SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR").getInt(null)
        } catch (e: Exception) {
            0x00000010
        }
    }
    private fun setMiuiIconDarkMode(window: Window, dark: Boolean, type: BarType): Boolean {
        try {
            //constants for MIUI similar to "SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR" stored in separate class
            val layoutParams = Class.forName("android.view.MiuiWindowManager\$LayoutParams")
            val allFields = layoutParams.fields
            for (field in allFields) {
                val name = field.name
                if (!name.contains("_DARK_")) continue
                if (type == BarType.STATUSBAR && !name.toLowerCase(Locale.US).contains("status")) {
                    continue
                }
                if (type == BarType.NAVBAR && !name.toLowerCase(Locale.US).contains("nav")) {
                    continue
                }
                val darkModeFlag =
                    field.getInt(null) //because its static fields - access without object
                return HelperTool.setMIUIFlag(window, dark, darkModeFlag)
            }
        } catch (e: Throwable) {
        }
        return false
    }

    private fun setFlymeIconDarkMode(window: Window, dark: Boolean, type: BarType): Boolean {
        try {
            //FlymeOS expand WindowManager.LayoutParams class and add some private fields
            val allFields = WindowManager.LayoutParams::class.java.declaredFields
            for (field in allFields) {
                val name = field.name
                if (!name.contains("_DARK_")) continue
                if (type == BarType.STATUSBAR && !name.toLowerCase(Locale.US).contains("status")) {
                    continue
                }
                if (type == BarType.NAVBAR && !name.toLowerCase(Locale.US).contains("nav")) {
                    continue
                }
                field.isAccessible = true
                val bits = field.getInt(null)
                return HelperTool.setFlameFlag(window, dark, bits)
            }
        } catch (e: Throwable) {
        }
        return false
    }

    fun setDarkIconMode(window: Window, dark: Boolean, type: BarType): Boolean {
        //Android6+ should deal with DarkIcons without problems
        val bits =
            if (type == BarType.STATUSBAR) SYSTEM_UI_FLAG_LIGHT_STATUS_BAR else SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && bits != -1 && HelperTool.setUIVisibilityFlag(
                window,
                dark,
                bits
            )
        ) return true

        //in other case - try to use OEM solutions
        return if (setFlymeIconDarkMode(window, dark, type)) {
            true
        } else setMiuiIconDarkMode(window, dark, type)
    }

}