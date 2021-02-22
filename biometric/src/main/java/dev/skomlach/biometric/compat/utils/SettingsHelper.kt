package dev.skomlach.biometric.compat.utils;

import android.content.Context;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

@RestrictTo(RestrictTo.Scope.LIBRARY)
public class SettingsHelper {
    public static int getInt(Context context, String secureSettingKey, int defaultValue) {
        return (int) getLong(context, secureSettingKey, defaultValue);
    }

    public static long getLong(Context context, String secureSettingKey, long defaultValue) {
        long result = getLongInternal(context, secureSettingKey, defaultValue);
        if (result == defaultValue) {
            result = getIntInternal(context, secureSettingKey, (int) defaultValue);
        }
        return result;
    }

    public static String getString(Context context, String secureSettingKey, @NonNull String defaultValue) {

        try {
            String result = Settings.Secure.getString(context.getContentResolver(), secureSettingKey);

            if (!defaultValue.equals(result))
                return result;
        } catch (Throwable e) {

        }
        //fallback
        try {
            String result = Settings.System.getString(context.getContentResolver(), secureSettingKey);

            if (!defaultValue.equals(result))
                return result;
        } catch (Throwable e) {

        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1)
            try {
                String result = Settings.Global.getString(context.getContentResolver(), secureSettingKey);

                if (!defaultValue.equals(result))
                    return result;
            } catch (Throwable e) {

            }
        return defaultValue;
    }

    private static long getLongInternal(Context context, String secureSettingKey, long defaultValue) {
        try {
            long result = Settings.Secure.getLong(context.getContentResolver(), secureSettingKey);

            if (result != defaultValue)
                return result;
        } catch (Throwable e) {

        }
        //fallback
        try {
            long result = Settings.System.getLong(context.getContentResolver(), secureSettingKey);

            if (result != defaultValue)
                return result;
        } catch (Throwable e) {

        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1)
            try {
                long result = Settings.Global.getLong(context.getContentResolver(), secureSettingKey);

                if (result != defaultValue)
                    return result;
            } catch (Throwable e) {

            }
        return defaultValue;
    }

    private static int getIntInternal(Context context, String secureSettingKey, int defaultValue) {
        try {
            int result = Settings.Secure.getInt(context.getContentResolver(), secureSettingKey);

            if (result != defaultValue)
                return result;
        } catch (Throwable e) {

        }
        //fallback
        try {
            int result = Settings.System.getInt(context.getContentResolver(), secureSettingKey);

            if (result != defaultValue)
                return result;
        } catch (Throwable e) {

        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1)
            try {
                int result = Settings.Global.getInt(context.getContentResolver(), secureSettingKey);

                if (result != defaultValue)
                    return result;
            } catch (Throwable e) {

            }
        return defaultValue;
    }
}
