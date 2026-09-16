package dev.skomlach.biometric.compat.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FingerprintSensorPolicyTest {
    private val unknown = configuredFingerprintSensor(null, null, false)
    private fun placement(vararg sensors: String) = resolveFingerprintSensor(unknown, sensors.toSet()).placement

    @Test fun emulatedModelCannotEstablishPhysicalSensorPlacement() {
        for (sensor in listOf("Fingerprint (rear-mounted)", "Fingerprint (side-mounted)", "Fingerprint (under display)")) {
            assertEquals(FingerprintSensorPlacement.UNKNOWN,
                resolveFingerprintSensor(unknown, setOf(sensor), isEmulator = true).placement)
        }
    }

    @Test fun emulationDoesNotDiscardPositivePlatformConfiguration() {
        val configured = configuredFingerprintSensor(intArrayOf(540, 1900, 80), false, false)
        assertEquals(configured, resolveFingerprintSensor(configured, emptySet(), isEmulator = true))
    }

    @Test fun validUdfpsConfigurationOverridesStaleDeviceMetadata() {
        val configured = configuredFingerprintSensor(intArrayOf(540, 1900, 80), false, false)
        assertEquals(FingerprintSensorPlacement.UNDER_DISPLAY,
            resolveFingerprintSensor(configured, setOf("Fingerprint (rear-mounted)")).placement)
    }

    @Test fun sideSensorConfigurationOverridesStaleUnderDisplayMetadata() {
        for (configured in listOf(configuredFingerprintSensor(null, true, false),
            configuredFingerprintSensor(null, false, true))) {
            assertEquals(FingerprintSensorPlacement.SIDE,
                resolveFingerprintSensor(configured, setOf("Fingerprint (under display, optical)")).placement)
        }
    }

    @Test fun emptyPixelHalResourcesDoNotImplyAbsenceOfUdfps() {
        val configured = configuredFingerprintSensor(intArrayOf(), false, false)
        assertEquals(FingerprintSensorPlacement.UNKNOWN, configured.placement)
        assertEquals(FingerprintSensorPlacement.UNDER_DISPLAY,
            resolveFingerprintSensor(configured, setOf("Fingerprint (under display, optical)")).placement)
        assertEquals(FingerprintSensorPlacement.SIDE,
            resolveFingerprintSensor(configured, setOf("Fingerprint (side-mounted)")).placement)
    }

    @Test fun partialOrMalformedCoordinatesCannotDeclareUdfps() {
        for (props in listOf(intArrayOf(540, 1900), intArrayOf(540, 1900, 0),
            intArrayOf(0, 1900, 80), intArrayOf(-1, 1900, 80), intArrayOf(540, 1900, 600),
            intArrayOf(540, 1900, 80, 1))) {
            assertEquals(FingerprintSensorPlacement.UNKNOWN, configuredFingerprintSensor(props, false, false).placement)
        }
    }

    @Test fun conflictingConfiguredSensorTypesRemainUnknown() {
        val configured = configuredFingerprintSensor(intArrayOf(540, 1900, 80), true, false)
        assertTrue(configured.conflicting)
        assertEquals(FingerprintSensorPlacement.UNKNOWN,
            resolveFingerprintSensor(configured, setOf("Fingerprint (under display)")).placement)
    }

    @Test fun missingOrGenericFingerprintMetadataRemainsUnknown() {
        assertEquals(FingerprintSensorPlacement.UNKNOWN, placement())
        assertEquals(FingerprintSensorPlacement.UNKNOWN, placement("Fingerprint"))
        assertEquals(FingerprintSensorPlacement.UNKNOWN, placement("Face ID", "Accelerometer"))
        assertEquals(FingerprintSensorPlacement.UNKNOWN, placement("Fingerprint (optical)"))
    }

    @Test fun frontFacingButtonIsNotAnUnderDisplaySensor() {
        assertEquals(FingerprintSensorPlacement.OTHER, placement("Fingerprint (front-mounted)"))
        assertEquals(FingerprintSensorPlacement.OTHER, placement("Fingerprint (rear-mounted)"))
    }

    @Test fun explicitDatabaseSpellingsAreRecognized() {
        for (sensor in listOf("Fingerprint (under-display)", "Fingerprint (in-screen)", "FINGERPRINT UDFPS")) {
            assertEquals(FingerprintSensorPlacement.UNDER_DISPLAY, placement(sensor))
        }
        assertEquals(FingerprintSensorPlacement.SIDE, placement("Fingerprint (power button)"))
    }

    @Test fun conflictingMetadataDoesNotArbitrarilyPickOneSensor() {
        assertEquals(FingerprintSensorPlacement.UNKNOWN,
            placement("Fingerprint (side-mounted)", "Fingerprint (under display)"))
    }

    @Test fun metadataArrivingAfterStartupChangesTheDecision() {
        assertEquals(FingerprintSensorPlacement.UNKNOWN, resolveFingerprintSensor(unknown, emptySet()).placement)
        assertEquals(FingerprintSensorPlacement.UNDER_DISPLAY,
            resolveFingerprintSensor(unknown, setOf("Fingerprint (under display)")).placement)
    }
}
