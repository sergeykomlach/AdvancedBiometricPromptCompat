package dev.skomlach.biometric.compat.utils;

import android.database.Cursor;
import android.net.Uri;
import android.util.Base64;

import androidx.annotation.RestrictTo;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl;
import dev.skomlach.common.contextprovider.AndroidContext;

//Dev tool
@RestrictTo(RestrictTo.Scope.LIBRARY)
public class DeviceSettings {

    private static final Pattern pattern = Pattern.compile("\\[(.+)\\]: \\[(.+)\\]");

    public static void printAll() {
        printSetting();
        printProperties();
    }

    private static void printSetting() {

        try {
            final String[] subSettings = {"system", "global", "secure"};

            for (String sub : subSettings) {
                Uri u = Uri.parse("content://settings/" + sub);
                Cursor mCur = AndroidContext.getAppContext()
                        .getContentResolver()
                        .query(u, null,
                                null,
                                null,
                                null);
                if (mCur != null) {

                    for (mCur.moveToFirst(); !mCur.isAfterLast(); mCur.moveToNext()) {
                        int nameIndex = mCur
                                .getColumnIndexOrThrow("name");
                        int valueIndex = mCur
                                .getColumnIndexOrThrow("values");
                        if (!mCur.isNull(nameIndex)) {
                            int type = mCur.getType(valueIndex);

                            String name = mCur.getString(nameIndex);

                            switch (type) {
                                case Cursor.FIELD_TYPE_BLOB:
                                    BiometricLoggerImpl.d("SystemSettings: " + sub + " - " + name + ":" + Base64.encodeToString(mCur.getBlob(valueIndex), Base64.DEFAULT));
                                    break;
                                case Cursor.FIELD_TYPE_FLOAT:
                                    BiometricLoggerImpl.d("SystemSettings: " + sub + " - " + name + ":" + mCur.getFloat(valueIndex));
                                    break;
                                case Cursor.FIELD_TYPE_INTEGER:
                                    BiometricLoggerImpl.d("SystemSettings: " + sub + " - " + name + ":" + mCur.getInt(valueIndex));
                                    break;
                                case Cursor.FIELD_TYPE_NULL:
                                    BiometricLoggerImpl.d("SystemSettings: " + sub + " - " + name + ":NULL");
                                    break;
                                case Cursor.FIELD_TYPE_STRING:
                                    BiometricLoggerImpl.d("SystemSettings: " + sub + " - " + name + ":" + mCur.getString(valueIndex));
                                    break;
                                default:
                                    BiometricLoggerImpl.d("SystemSettings: " + sub + " - " + name + ": unknown type - " + type);
                                    break;
                            }
                        }
                    }

                    mCur.close();
                }
            }
        } catch (Throwable e) {
            BiometricLoggerImpl.e(e, "SystemSettings");
        }
    }

    private static void printProperties() {
        String line;
        Matcher m;
        try {
            Process p = Runtime.getRuntime().exec("getprop");
            BufferedReader input = new BufferedReader(new InputStreamReader(p.getInputStream()));
            while ((line = input.readLine()) != null) {
                m = pattern.matcher(line);
                if (m.find()) {
                    MatchResult result = m.toMatchResult();
                    String key = result.group(1);
                    String value = result.group(2);

                    BiometricLoggerImpl.d("SystemProperties: " + line);
                }
            }
            input.close();
        } catch (Throwable e) {
            BiometricLoggerImpl.e(e, "SystemProperties");
        }
    }
}
