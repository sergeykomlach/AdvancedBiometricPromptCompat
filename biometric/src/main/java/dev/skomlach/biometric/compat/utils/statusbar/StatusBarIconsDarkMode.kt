package dev.skomlach.biometric.compat.utils.statusbar;

import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.RestrictTo;

import java.lang.reflect.Field;
@RestrictTo(RestrictTo.Scope.LIBRARY)
public class StatusBarIconsDarkMode {
    private static int SYSTEM_UI_FLAG_LIGHT_STATUS_BAR = 0x00002000;
    private static int SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR = 0x00000010;

    static {
        try {
            //Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
            Field field = View.class.getField("SYSTEM_UI_FLAG_LIGHT_STATUS_BAR");
            SYSTEM_UI_FLAG_LIGHT_STATUS_BAR = field.getInt(null);
        } catch (Exception e) {
            SYSTEM_UI_FLAG_LIGHT_STATUS_BAR = -1;
        }
        try {
            //Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            Field field = View.class.getField("SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR");
            SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR = field.getInt(null);
        } catch (Exception e) {
            SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR = -1;
        }
    }

    private static boolean setMiuiIconDarkMode(Window window, boolean dark, BarType type) {
        try {
            //constants for MIUI similar to "SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR" stored in separate class
            Class<?> layoutParams = Class.forName("android.view.MiuiWindowManager$LayoutParams");

            Field[] allFields = layoutParams.getFields();
            for (Field field : allFields) {
                String name = field.getName();

                if (!name.contains("_DARK_"))
                    continue;

                if (type == BarType.STATUSBAR && !name.toLowerCase().contains("status")) {
                    continue;
                }
                if (type == BarType.NAVBAR && !name.toLowerCase().contains("nav")) {
                    continue;
                }
                int darkModeFlag = field.getInt(null);   //because its static fields - access without object
                return HelperTool.setMIUIFlag(window, dark, darkModeFlag);
            }
        } catch (Throwable e) {

        }
        return false;
    }

    private static boolean setFlymeIconDarkMode(Window window, boolean dark, BarType type) {
        try {
            //FlymeOS expand WindowManager.LayoutParams class and add some private fields
            Field[] allFields = WindowManager.LayoutParams.class.getDeclaredFields();
            for (Field field : allFields) {
                String name = field.getName();

                if (!name.contains("_DARK_"))
                    continue;

                if (type == BarType.STATUSBAR && !name.toLowerCase().contains("status")) {
                    continue;
                }
                if (type == BarType.NAVBAR && !name.toLowerCase().contains("nav")) {
                    continue;
                }
                field.setAccessible(true);

                int bits = field.getInt(null);

                return HelperTool.setFlameFlag(window, dark, bits);
            }
        } catch (Throwable e) {

        }
        return false;
    }

    public static boolean setDarkIconMode(Window window, boolean dark, BarType type) {
        //Android6+ should deal with DarkIcons without problems

        final int bits = (type == BarType.STATUSBAR ? SYSTEM_UI_FLAG_LIGHT_STATUS_BAR : SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                bits != -1 && HelperTool.setUIVisibilityFlag(window, dark, bits))
            return true;

        //in other case - try to use OEM solutions
        if (setFlymeIconDarkMode(window, dark, type)) {
            return true;
        } else return setMiuiIconDarkMode(window, dark, type);
    }
}
