package dev.skomlach.biometric.compat.utils

import android.database.Cursor
import android.net.Uri
import android.util.Base64
import androidx.annotation.RestrictTo
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.d
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.e
import dev.skomlach.common.contextprovider.AndroidContext.appContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.regex.Matcher
import java.util.regex.Pattern

//Dev tool
@RestrictTo(RestrictTo.Scope.LIBRARY)
object DeviceSettings {
    private val pattern = Pattern.compile("\\[(.+)\\]: \\[(.+)\\]")
    fun printAll() {
        printSetting()
        printProperties()
    }

    private fun printSetting() {
        try {
            val subSettings = arrayOf("system", "global", "secure")
            for (sub in subSettings) {
                val u = Uri.parse("content://settings/$sub")
                val mCur = appContext
                    .contentResolver
                    .query(
                        u, null,
                        null,
                        null,
                        null
                    )
                if (mCur != null) {
                    mCur.moveToFirst()
                    while (!mCur.isAfterLast) {
                        val nameIndex = mCur
                            .getColumnIndexOrThrow("name")
                        val valueIndex = mCur
                            .getColumnIndexOrThrow("values")
                        if (!mCur.isNull(nameIndex)) {
                            val type = mCur.getType(valueIndex)
                            val name = mCur.getString(nameIndex)
                            when (type) {
                                Cursor.FIELD_TYPE_BLOB -> d(
                                    "SystemSettings: $sub - $name:" + Base64.encodeToString(
                                        mCur.getBlob(valueIndex),
                                        Base64.DEFAULT
                                    )
                                )
                                Cursor.FIELD_TYPE_FLOAT -> d(
                                    "SystemSettings: $sub - $name:" + mCur.getFloat(
                                        valueIndex
                                    )
                                )
                                Cursor.FIELD_TYPE_INTEGER -> d(
                                    "SystemSettings: $sub - $name:" + mCur.getInt(
                                        valueIndex
                                    )
                                )
                                Cursor.FIELD_TYPE_NULL -> d("SystemSettings: $sub - $name:NULL")
                                Cursor.FIELD_TYPE_STRING -> d(
                                    "SystemSettings: $sub - $name:" + mCur.getString(
                                        valueIndex
                                    )
                                )
                                else -> d("SystemSettings: $sub - $name: unknown type - $type")
                            }
                        }
                        mCur.moveToNext()
                    }
                    mCur.close()
                }
            }
        } catch (e: Throwable) {
            e(e, "SystemSettings")
        }
    }

    private fun printProperties() {
        var line: String
        var m: Matcher
        try {
            val p = Runtime.getRuntime().exec("getprop")
            val input = BufferedReader(InputStreamReader(p.inputStream))
            while (input.readLine().also { line = it } != null) {
                m = pattern.matcher(line)
                if (m.find()) {
                    val result = m.toMatchResult()
                    val key = result.group(1)
                    val value = result.group(2)
                    d("SystemProperties: $line")
                }
            }
            input.close()
        } catch (e: Throwable) {
            e(e, "SystemProperties")
        }
    }
}