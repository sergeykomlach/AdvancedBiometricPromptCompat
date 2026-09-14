package dev.skomlach.biometric.compat.utils

internal enum class BiometricUiAvailability { AVAILABLE, UNAVAILABLE, UNKNOWN }

internal fun resolveBiometricUiAvailability(
    providerEnabled: Boolean?,
    readablePromptLayout: Boolean
): BiometricUiAvailability = when {
    providerEnabled == false -> BiometricUiAvailability.UNAVAILABLE
    readablePromptLayout -> BiometricUiAvailability.AVAILABLE
    else -> BiometricUiAvailability.UNKNOWN
}

internal enum class FingerprintSensorPlacement { UNDER_DISPLAY, SIDE, OTHER, UNKNOWN }

internal data class FingerprintSensorEvidence(
    val placement: FingerprintSensorPlacement,
    val source: String,
    val conflicting: Boolean = false
)

/** Empty/default resource values are also shipped on devices whose properties come from the HAL. */
internal fun configuredFingerprintSensor(
    udfpsProperties: IntArray?,
    powerButtonSensor: Boolean?,
    hasSideSensorLocation: Boolean
): FingerprintSensorEvidence {
    val underDisplay = udfpsProperties?.let {
        it.size == 3 && it[0] > 0 && it[1] > 0 && it[2] > 0 &&
            it[2] < it[0] && it[2] < it[1]
    } == true
    val side = powerButtonSensor == true || hasSideSensorLocation
    return when {
        underDisplay && side -> FingerprintSensorEvidence(FingerprintSensorPlacement.UNKNOWN,
            "conflicting-framework-config", conflicting = true)
        underDisplay -> FingerprintSensorEvidence(FingerprintSensorPlacement.UNDER_DISPLAY, "framework-udfps-config")
        side -> FingerprintSensorEvidence(FingerprintSensorPlacement.SIDE, "framework-side-config")
        else -> FingerprintSensorEvidence(FingerprintSensorPlacement.UNKNOWN, "no-explicit-framework-config")
    }
}

internal fun resolveFingerprintSensor(
    configured: FingerprintSensorEvidence,
    deviceSensors: Set<String>,
    isEmulator: Boolean = false
): FingerprintSensorEvidence {
    if (configured.conflicting || configured.placement != FingerprintSensorPlacement.UNKNOWN) return configured
    // Emulators can report a real phone's model while emulating entirely different hardware.
    if (isEmulator) return FingerprintSensorEvidence(FingerprintSensorPlacement.UNKNOWN, "emulated-device-metadata")
    val placements = deviceSensors.mapNotNull { sensor ->
        val name = sensor.lowercase().replace('-', ' ').replace('_', ' ')
        if ("fingerprint" !in name) return@mapNotNull null
        when {
            listOf("under display", "under screen", "in display", "in screen", "udfps").any { it in name } ->
                FingerprintSensorPlacement.UNDER_DISPLAY
            "side" in name || "power button" in name -> FingerprintSensorPlacement.SIDE
            "rear" in name || "back" in name || "front" in name -> FingerprintSensorPlacement.OTHER
            else -> FingerprintSensorPlacement.UNKNOWN
        }
    }.toSet()
    // Generic "Fingerprint", missing metadata, or conflicting placements do not prove UDFPS.
    val placement = placements.singleOrNull()?.takeIf { it != FingerprintSensorPlacement.UNKNOWN }
        ?: return FingerprintSensorEvidence(FingerprintSensorPlacement.UNKNOWN, "unknown-sensor-placement")
    return FingerprintSensorEvidence(placement, "device-database-placement")
}
