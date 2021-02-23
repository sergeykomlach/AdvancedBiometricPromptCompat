package dev.skomlach.biometric.compat.engine.internal.face.miui.impl

import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.d
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.e
import dev.skomlach.common.contextprovider.AndroidContext.appContext
import me.weishu.reflection.Reflection.unseal
import java.lang.reflect.Field

object MiuiCodeToString {
    private var stringFields: Array<Field>? = null
    private var stringArrayFields: Array<Field>? = null

    init {
        unseal(appContext, listOf("com.android.internal"))
        try {
            stringFields = Class.forName("com.android.internal.R\$string").declaredFields
            stringArrayFields = Class.forName("com.android.internal.R\$array").declaredFields
        } catch (e: Throwable) {
            e(e)
        }
    }

    private fun getString(s: String): String? {
        stringFields?.let {
            try {
                for (field in it) {
                    if (s == field.name) {
                        val isAccessible = field.isAccessible
                        return try {
                            if (!isAccessible) field.isAccessible = true
                            appContext.resources.getString(field[null] as Int)
                        } finally {
                            if (!isAccessible) field.isAccessible = false
                        }
                    }
                }
            } catch (e: Throwable) {
                e(e)
            }
        }
        return null
    }

    private fun getStringArray(s: String): Array<String>? {
        stringArrayFields?.let {
            try {
                for (field in it) {
                    if (s == field.name) {
                        val isAccessible = field.isAccessible
                        return try {
                            if (!isAccessible) field.isAccessible = true
                            appContext.resources.getStringArray(field[null] as Int)
                        } finally {
                            if (!isAccessible) field.isAccessible = false
                        }
                    }
                }
            } catch (e: Throwable) {
                e(e)
            }
        }
        return null
    }

    fun getErrorString(errMsg: Int, vendorCode: Int): String? {
        when (errMsg) {
            1 -> return getString("face_error_hw_not_available")
            2 -> return getString("face_error_unable_to_process")
            3 -> return getString("face_error_timeout")
            4 -> return getString("face_error_no_space")
            5 -> return getString("face_error_canceled")
            7 -> return getString("face_error_lockout")
            8 -> {
                val msgArray = getStringArray("face_error_vendor")
                if (msgArray != null && vendorCode < msgArray.size) {
                    return msgArray[vendorCode]
                }
            }
            9 -> return getString("face_error_lockout_permanent")
            10 -> return getString("face_error_user_canceled")
            11 -> return getString("face_error_not_enrolled")
            12 -> return getString("face_error_hw_not_present")
        }
        val stringBuilder = StringBuilder()
        stringBuilder.append("Invalid error message: ")
        stringBuilder.append(errMsg)
        stringBuilder.append(", ")
        stringBuilder.append(vendorCode)
        d(stringBuilder.toString())
        return null
    }

    fun getAcquiredString(acquireInfo: Int, vendorCode: Int): String? {
        when (acquireInfo) {
            0 -> return null
            1 -> return getString("face_acquired_insufficient")
            2 -> return getString("face_acquired_too_bright")
            3 -> return getString("face_acquired_too_dark")
            4 -> return getString("face_acquired_too_close")
            5 -> return getString("face_acquired_too_far")
            6 -> return getString("face_acquired_too_high")
            7 -> return getString("face_acquired_too_low")
            8 -> return getString("face_acquired_too_right")
            9 -> return getString("face_acquired_too_left")
            10 -> return getString("face_acquired_poor_gaze")
            11 -> return getString("face_acquired_not_detected")
            12 -> return getString("face_acquired_too_much_motion")
            13 -> return getString("face_acquired_recalibrate")
            14 -> return getString("face_acquired_too_different")
            15 -> return getString("face_acquired_too_similar")
            16 -> return getString("face_acquired_pan_too_extreme")
            17 -> return getString("face_acquired_tilt_too_extreme")
            18 -> return getString("face_acquired_roll_too_extreme")
            19 -> return getString("face_acquired_obscured")
            20 -> return null
            21 -> return getString("face_acquired_sensor_dirty")
            22 -> {
                val msgArray = getStringArray("face_acquired_vendor")
                if (msgArray != null && vendorCode < msgArray.size) {
                    return msgArray[vendorCode]
                }
            }
        }
        val stringBuilder = StringBuilder()
        stringBuilder.append("Invalid acquired message: ")
        stringBuilder.append(acquireInfo)
        stringBuilder.append(", ")
        stringBuilder.append(vendorCode)
        d(stringBuilder.toString())
        return null
    }
}