package dev.skomlach.biometric.compat

import dev.skomlach.biometric.compat.utils.hardware.BiometricPromptHardware
import dev.skomlach.biometric.compat.utils.hardware.shouldTrustSystemFaceHardwareSignal
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SamsungFaceHardwareRoutingTest {

    @Test
    fun `samsung model codes are recognized as samsung devices`() {
        assertTrue(isSamsungDeviceModel("SM-S928B"))
        assertTrue(isSamsungDeviceModel("Galaxy S24 Ultra"))
        assertTrue(isSamsungDeviceModel("Samsung Galaxy S24 Ultra"))
        assertFalse(isSamsungDeviceModel("Pixel 8 Pro"))
    }

    @Test
    fun `system confirmed samsung face signal survives missing face sensor hint`() {
        val result = BiometricPromptHardware.BiometricModalityDetector.Result(
            type = BiometricType.BIOMETRIC_FACE,
            confidence = BiometricPromptHardware.BiometricModalityDetector.Confidence.LIKELY,
            hardwarePresent = true,
            enrolledLikely = true,
            reasons = listOf("feature:android.hardware.biometrics.face")
        )

        assertTrue(
            shouldTrustSystemFaceHardwareSignal(
                model = "SM-S928B",
                hasFaceSensorHint = false,
                modalityResult = result
            )
        )
    }

    @Test
    fun `non samsung face hint alone does not fabricate system hardware face`() {
        val result = BiometricPromptHardware.BiometricModalityDetector.Result(
            type = BiometricType.BIOMETRIC_FACE,
            confidence = BiometricPromptHardware.BiometricModalityDetector.Confidence.NONE,
            hardwarePresent = false,
            enrolledLikely = true
        )

        assertFalse(
            shouldTrustSystemFaceHardwareSignal(
                model = "Pixel 7a",
                hasFaceSensorHint = true,
                modalityResult = result
            )
        )
    }
}
