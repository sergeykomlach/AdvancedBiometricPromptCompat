package dev.skomlach.biometric.compat.utils;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.UserHandle;
import android.text.TextUtils;

import androidx.annotation.RestrictTo;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import dev.skomlach.common.contextprovider.AndroidContext;

@RestrictTo(RestrictTo.Scope.LIBRARY)
public class LockType {

    /**
     * The bit in LOCK_BIOMETRIC_WEAK_FLAGS to be used to indicate whether liveliness should be used
     */
    public static final int FLAG_BIOMETRIC_WEAK_LIVELINESS = 0x1;
    /**
     * A flag containing settings used for biometric weak
     *
     * @hide
     */
    private static final String LOCK_BIOMETRIC_WEAK_FLAGS =
            "lock_biometric_weak_flags";

    private final static String PASSWORD_TYPE_KEY = "lockscreen.password_type";

    private static final String PASSWORD_TYPE_ALTERNATE_KEY = "lockscreen.password_type_alternate";

    /**
     * @return Whether the biometric weak liveliness is enabled.
     */
    public static boolean isBiometricWeakLivelinessEnabled(Context context) {
        long currentFlag = SettingsHelper.getLong(context, LOCK_BIOMETRIC_WEAK_FLAGS, 0L);
        return ((currentFlag & FLAG_BIOMETRIC_WEAK_LIVELINESS) != 0);
    }

    public static boolean isBiometricWeakEnabled(Context context) {

        try {

            int mode;

            Class<?> lockUtilsClass = Class.forName("com.android.internal.widget.LockPatternUtils");
            Method method = null;
            Object lockUtils = lockUtilsClass.getConstructor(Context.class).newInstance(AndroidContext.getAppContext());
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                method = lockUtilsClass.getMethod("getActivePasswordQuality");
                mode = Integer.valueOf(String.valueOf(method.invoke(lockUtils)));
            } else {
                method = lockUtilsClass.getMethod("getActivePasswordQuality", int.class);
                int userid = (int) UserHandle.class.getMethod("getUserId", int.class).invoke(null, android.os.Process.myUid());
                mode = Integer.valueOf(String.valueOf(method.invoke(lockUtils, userid)));
            }

            return mode == DevicePolicyManager.PASSWORD_QUALITY_BIOMETRIC_WEAK;
        } catch (Throwable ignore) {
            return isBiometricEnabledInSettings(context);
        }
    }

    public static boolean isBiometricEnabledInSettings(Context context, String type) {

        try {

            List<String> keyValue = new ArrayList<>();

            Uri u = Uri.parse("content://settings/secure");

            Cursor mCur = context
                    .getContentResolver()
                    .query(u, null,
                            null,
                            null,
                            null);
            if (mCur != null) {

                for (mCur.moveToFirst(); !mCur.isAfterLast(); mCur.moveToNext()) {
                    int nameIndex = mCur
                            .getColumnIndex("name");
                    if (!mCur.isNull(nameIndex)) {
                        String name = mCur.getString(nameIndex);
                        if (TextUtils.isEmpty(name))
                            continue;

                        String s = name.toLowerCase(Locale.ROOT);

                        if (s.contains(type)) {
                            if (s.contains("_unl") && s.contains("_enable")) {
                                keyValue.add(name);
                            }
                        }
                    }
                }

                mCur.close();
                mCur = null;
            }

            for (String s : keyValue) {
                if (SettingsHelper.getInt(context, s, -1) == 1) {
                    return true;
                }
            }
        } catch (Throwable ignored) {

        }
        return false;
    }

    private static boolean isBiometricEnabledInSettings(Context context) {

        try {

            List<String> keyValue = new ArrayList<>();

            Uri u = Uri.parse("content://settings/secure");

            Cursor mCur = context
                    .getContentResolver()
                    .query(u, null,
                            null,
                            null,
                            null);
            if (mCur != null) {

                for (mCur.moveToFirst(); !mCur.isAfterLast(); mCur.moveToNext()) {
                    int nameIndex = mCur
                            .getColumnIndex("name");
                    if (!mCur.isNull(nameIndex)) {
                        String name = mCur.getString(nameIndex);
                        if (TextUtils.isEmpty(name))
                            continue;

                        String s = name.toLowerCase(Locale.ROOT);

                        if (s.contains("fingerprint")
                                || s.contains("face")
                                || s.contains("iris")
                                || s.contains("biometric")

                        ) {
                            if (s.contains("_unl") && s.contains("_enable")) {
                                keyValue.add(name);
                            }
                        }
                    }
                }

                mCur.close();
                mCur = null;
            }

            for (String s : keyValue) {
                if (SettingsHelper.getInt(context, s, -1) == 1) {
                    return true;
                }
            }
        } catch (Throwable ignored) {

        }
        long pwrdType = SettingsHelper.getLong(context, PASSWORD_TYPE_KEY, DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED);
        long pwrdAltType = SettingsHelper.getLong(context, PASSWORD_TYPE_ALTERNATE_KEY, DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED);

        return (pwrdType == DevicePolicyManager.PASSWORD_QUALITY_BIOMETRIC_WEAK) ||
                (pwrdAltType == DevicePolicyManager.PASSWORD_QUALITY_BIOMETRIC_WEAK);
    }
}