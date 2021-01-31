package dev.skomlach.biometric.compat.engine.internal.face.miui.impl;

import android.content.Context;

import java.lang.reflect.Field;
import java.util.Collections;

import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl;
import dev.skomlach.common.contextprovider.AndroidContext;
import me.weishu.reflection.Reflection;

public class MiuiCodeToString {

    static {
        Reflection.unseal(AndroidContext.getAppContext(), Collections.singletonList("com.android.internal"));
    }
    private static String getString(String s) {
        try {
            Field[] fields = Class.forName("com.android.internal.R$string").getDeclaredFields();
            for (Field field : fields) {
                if (s.equals(field.getName())) {
                    boolean isAccessible = field.isAccessible();
                    try {
                        if (!isAccessible)
                            field.setAccessible(true);
                        return AndroidContext.getAppContext().getResources().getString((int) field.get(null));
                    } finally {
                        if (!isAccessible)
                            field.setAccessible(false);
                    }
                }
            }
        } catch (Throwable e) {
            BiometricLoggerImpl.e(e);
        }

        return null;
    }
    private static String[] getStringArray(String s) {
        try {
            Field[] fields = Class.forName("com.android.internal.R$array").getDeclaredFields();
            for (Field field : fields) {
                if (s.equals(field.getName())) {
                    boolean isAccessible = field.isAccessible();
                    try {
                        if (!isAccessible)
                            field.setAccessible(true);
                        return AndroidContext.getAppContext().getResources().getStringArray((int) field.get(null));
                    } finally {
                        if (!isAccessible)
                            field.setAccessible(false);
                    }
                }
            }
        } catch (Throwable e) {
            BiometricLoggerImpl.e(e);
        }

        return null;
    }
    public static String getErrorString(int errMsg, int vendorCode) {
        switch (errMsg) {
            case 1:
                return getString( "face_error_hw_not_available");
            case 2:
                return getString( "face_error_unable_to_process");
            case 3:
                return getString( "face_error_timeout");
            case 4:
                return getString( "face_error_no_space");
            case 5:
                return getString( "face_error_canceled");
            case 7:
                return getString( "face_error_lockout");
            case 8:
                String[] msgArray = getStringArray("face_error_vendor");
                if (msgArray!=null && vendorCode < msgArray.length) {
                    return msgArray[vendorCode];
                }
                break;
            case 9:
                return getString( "face_error_lockout_permanent");
            case 10:
                return getString( "face_error_user_canceled");
            case 11:
                return getString( "face_error_not_enrolled");
            case 12:
                return getString( "face_error_hw_not_present");
        }
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("Invalid error message: ");
        stringBuilder.append(errMsg);
        stringBuilder.append(", ");
        stringBuilder.append(vendorCode);
        BiometricLoggerImpl.d(stringBuilder.toString());
        return null;
    }

    public static String getAcquiredString(int acquireInfo, int vendorCode) {
        switch (acquireInfo) {
            case 0:
                return null;
            case 1:
                return getString( "face_acquired_insufficient");
            case 2:
                return getString( "face_acquired_too_bright");
            case 3:
                return getString( "face_acquired_too_dark");
            case 4:
                return getString( "face_acquired_too_close");
            case 5:
                return getString( "face_acquired_too_far");
            case 6:
                return getString( "face_acquired_too_high");
            case 7:
                return getString( "face_acquired_too_low");
            case 8:
                return getString( "face_acquired_too_right");
            case 9:
                return getString( "face_acquired_too_left");
            case 10:
                return getString( "face_acquired_poor_gaze");
            case 11:
                return getString( "face_acquired_not_detected");
            case 12:
                return getString( "face_acquired_too_much_motion");
            case 13:
                return getString( "face_acquired_recalibrate");
            case 14:
                return getString( "face_acquired_too_different");
            case 15:
                return getString( "face_acquired_too_similar");
            case 16:
                return getString( "face_acquired_pan_too_extreme");
            case 17:
                return getString( "face_acquired_tilt_too_extreme");
            case 18:
                return getString( "face_acquired_roll_too_extreme");
            case 19:
                return getString( "face_acquired_obscured");
            case 20:
                return null;
            case 21:
                return getString( "face_acquired_sensor_dirty");
            case 22:
                String[] msgArray = getStringArray("face_acquired_vendor");
                if (msgArray != null && vendorCode < msgArray.length) {
                    return msgArray[vendorCode];
                }
                break;
        }
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("Invalid acquired message: ");
        stringBuilder.append(acquireInfo);
        stringBuilder.append(", ");
        stringBuilder.append(vendorCode);
        BiometricLoggerImpl.d(stringBuilder.toString());
        return null;
    }

    public static int getMappedAcquiredInfo(int acquireInfo, int vendorCode) {
        if (acquireInfo == 22) {
            return vendorCode + 1000;
        }
        switch (acquireInfo) {
            case 0:
                return 0;
            case 1:
            case 2:
            case 3:
                return 2;
            case 4:
            case 5:
            case 6:
            case 7:
            case 8:
            case 9:
                return 1;
            case 10:
            case 11:
            case 12:
            case 13:
                return 2;
            default:
                return 0;
        }
    }

}
