package dev.skomlach.biometric.compat.utils

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.annotation.RestrictTo

@RestrictTo(RestrictTo.Scope.LIBRARY)
object SettingsHelper {
    @JvmStatic
    fun getInt(context: Context, secureSettingKey: String?, defaultValue: Int): Int {
        return getLong(context, secureSettingKey, defaultValue.toLong()).toInt()
    }
    @JvmStatic
    fun getLong(context: Context, secureSettingKey: String?, defaultValue: Long): Long {
        var result = getLongInternal(context, secureSettingKey, defaultValue)
        if (result == defaultValue) {
            result = getIntInternal(context, secureSettingKey, defaultValue.toInt()).toLong()
        }
        return result
    }
    @JvmStatic
    fun getString(context: Context, secureSettingKey: String?, defaultValue: String): String {
        try {
            val result = Settings.Secure.getString(context.contentResolver, secureSettingKey)
            if (defaultValue != result) return result
        } catch (e: Throwable) {
        }
        //fallback
        try {
            val result = Settings.System.getString(context.contentResolver, secureSettingKey)
            if (defaultValue != result) return result
        } catch (e: Throwable) {
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) try {
            val result = Settings.Global.getString(context.contentResolver, secureSettingKey)
            if (defaultValue != result) return result
        } catch (e: Throwable) {
        }
        return defaultValue
    }

    private fun getLongInternal(
        context: Context,
        secureSettingKey: String?,
        defaultValue: Long
    ): Long {
        try {
            val result = Settings.Secure.getLong(context.contentResolver, secureSettingKey)
            if (result != defaultValue) return result
        } catch (e: Throwable) {
        }
        //fallback
        try {
            val result = Settings.System.getLong(context.contentResolver, secureSettingKey)
            if (result != defaultValue) return result
        } catch (e: Throwable) {
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) try {
            val result = Settings.Global.getLong(context.contentResolver, secureSettingKey)
            if (result != defaultValue) return result
        } catch (e: Throwable) {
        }
        return defaultValue
    }

    private fun getIntInternal(
        context: Context,
        secureSettingKey: String?,
        defaultValue: Int
    ): Int {
        try {
            val result = Settings.Secure.getInt(context.contentResolver, secureSettingKey)
            if (result != defaultValue) return result
        } catch (e: Throwable) {
        }
        //fallback
        try {
            val result = Settings.System.getInt(context.contentResolver, secureSettingKey)
            if (result != defaultValue) return result
        } catch (e: Throwable) {
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) try {
            val result = Settings.Global.getInt(context.contentResolver, secureSettingKey)
            if (result != defaultValue) return result
        } catch (e: Throwable) {
        }
        return defaultValue
    }
}