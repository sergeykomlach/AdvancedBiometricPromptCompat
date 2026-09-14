package dev.skomlach.common.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class DeviceSensorMetadataTest {
    private val cached = DeviceInfo("Honor 8 Lite", "Honor 8 Lite", emptySet(), 123L)

    @Test fun missingCachedSensorsAreCompletedWithoutRefreshingTheirTimestamp() {
        val corrected = DeviceSensorMetadata.complete("HUAWEI", "PRA-LX1", cached)
        assertEquals(cached.copy(sensors = setOf("Fingerprint (rear-mounted)")), corrected)
        assertSame(corrected, DeviceSensorMetadata.complete("HUAWEI", "PRA-LX1", corrected))
    }

    @Test fun unrelatedSensorsArePreserved() {
        val info = cached.copy(sensors = setOf("Accelerometer"))
        assertEquals(setOf("Accelerometer", "Fingerprint (rear-mounted)"),
            DeviceSensorMetadata.complete("Huawei", "PRA-LX1", info).sensors)
    }

    @Test fun exactHardwareIdentityIsRequiredEvenWithTheSameMarketingName() {
        assertSame(cached, DeviceSensorMetadata.complete("HUAWEI", "PRA-LX2", cached))
        assertSame(cached, DeviceSensorMetadata.complete("HUAWEI", "Honor 8 Lite", cached))
        assertSame(cached, DeviceSensorMetadata.complete("other", "PRA-LX1", cached))
    }

    @Test fun existingFingerprintMetadataAndEmulatorsAreNeverOverridden() {
        for (sensor in listOf("Fingerprint", "Fingerprint (side-mounted)", "Fingerprint (under display)")) {
            val info = cached.copy(sensors = setOf(sensor))
            assertSame(info, DeviceSensorMetadata.complete("HUAWEI", "PRA-LX1", info))
        }
        val emulated = cached.copy(emulatorKind = EmulatorKind.entries.first())
        assertSame(emulated, DeviceSensorMetadata.complete("HUAWEI", "PRA-LX1", emulated))
    }
}
