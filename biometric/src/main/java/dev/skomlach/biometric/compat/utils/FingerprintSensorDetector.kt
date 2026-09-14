package dev.skomlach.biometric.compat.utils

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.util.TypedValue

/** Reads configured hardware placement, never infers hardware from shared UDFPS UI assets. */
internal object FingerprintSensorDetector {
    private data class Cached(val configuration: Configuration, val evidence: FingerprintSensorEvidence)
    @Volatile private var cached: Cached? = null

    fun detect(context: Context, deviceSensors: Set<String>, isEmulator: Boolean): FingerprintSensorEvidence {
        val res = (context.applicationContext ?: context).resources
        val configuration = Configuration(res.configuration)
        val configured = cached?.takeIf { it.configuration == configuration }?.evidence ?: read(res).also {
            cached = Cached(configuration, it)
        }
        // Device metadata arrives asynchronously. Do not persist or cache its initial absence.
        return resolveFingerprintSensor(configured, deviceSensors, isEmulator)
    }

    private fun read(res: Resources): FingerprintSensorEvidence {
        fun id(name: String, type: String) = res.getIdentifier(name, type, "android")
        val udfps = attempt {
            val resourceId = id("config_udfps_sensor_props", "array")
            if (resourceId == 0) null else res.obtainTypedArray(resourceId).let { a ->
                try {
                    if (a.length() != 3 || (0..2).any {
                            a.peekValue(it)?.type !in TypedValue.TYPE_FIRST_INT..TypedValue.TYPE_LAST_INT
                        }) null
                    else IntArray(3) { a.getInt(it, -1) }
                } finally { a.recycle() }
            }
        }
        val powerButton = attempt {
            id("config_is_powerbutton_fps", "bool").takeIf { it != 0 }?.let(res::getBoolean)
        }
        val sideLocation = attempt {
            val resourceId = id("config_sfps_sensor_props", "array")
            if (resourceId == 0) false else res.obtainTypedArray(resourceId).let { outer ->
                try {
                    // AOSP: array of [displayId, x, y, radius] arrays; the outer array may be empty.
                    outer.length() in 1..16 && (0 until outer.length()).all { i ->
                        val nestedId = outer.getResourceId(i, 0)
                        if (nestedId == 0) false else res.obtainTypedArray(nestedId).let { inner ->
                            try {
                                inner.length() == 4 && inner.getString(0) != null &&
                                    (1..3).all { inner.peekValue(it)?.type in TypedValue.TYPE_FIRST_INT..TypedValue.TYPE_LAST_INT } &&
                                    inner.getInt(1, -1) >= 0 && inner.getInt(2, -1) >= 0 && inner.getInt(3, 0) > 0
                            } finally { inner.recycle() }
                        }
                    }
                } finally { outer.recycle() }
            }
        } == true
        return configuredFingerprintSensor(udfps, powerButton, sideLocation)
    }

    private fun <T> attempt(read: () -> T?): T? = try { read() }
    catch (_: Exception) { null } catch (_: LinkageError) { null }
}
