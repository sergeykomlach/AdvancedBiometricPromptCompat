package dev.skomlach.biometric.compat.custom

import dev.skomlach.biometric.compat.BiometricType
import org.junit.Assert.assertEquals
import org.junit.Test

class SoftwareBiometricSecurityPolicyTest {

    private val profile = SoftwareBiometricSecurityProfile(
        biometricType = BiometricType.BIOMETRIC_FACE,
        assurance = SoftwareBiometricAssuranceLevel.ACTIVE_CHALLENGE,
        requiresTrustedCapture = true,
        allowsCompatibilityCapture = false,
        supportsCryptoObject = false,
        maxCaptureDurationMs = 30_000L
    )

    @Test
    fun defaultSoftwareProfileAllowsOrdinaryAuthentication() {
        assertEquals(
            SoftwareBiometricSecurityDecision.ALLOW,
            SoftwareBiometricSecurityPolicy.evaluate(
                profile = profile,
                requestedType = BiometricType.BIOMETRIC_FACE,
                cryptoObject = null,
                trustedCapture = true,
                compatibilityCapture = false
            )
        )
    }

    @Test
    fun softwareProfileRejectsCryptoObjectWhenNotCryptographicallyCapable() {
        val crypto = AbstractSoftwareBiometricManager.CryptoObject(null as java.security.Signature?)

        assertEquals(
            SoftwareBiometricSecurityDecision.REJECT_CRYPTO,
            SoftwareBiometricSecurityPolicy.evaluate(
                profile = profile,
                requestedType = BiometricType.BIOMETRIC_FACE,
                cryptoObject = crypto,
                trustedCapture = true,
                compatibilityCapture = false
            )
        )
    }

    @Test
    fun strictProfileRejectsCompatibilityCapture() {
        assertEquals(
            SoftwareBiometricSecurityDecision.REJECT_COMPATIBILITY_CAPTURE,
            SoftwareBiometricSecurityPolicy.evaluate(
                profile = profile,
                requestedType = BiometricType.BIOMETRIC_FACE,
                cryptoObject = null,
                trustedCapture = false,
                compatibilityCapture = true
            )
        )
    }

    @Test
    fun profileRejectsMismatchedModality() {
        assertEquals(
            SoftwareBiometricSecurityDecision.REJECT_MODALITY,
            SoftwareBiometricSecurityPolicy.evaluate(
                profile = profile,
                requestedType = BiometricType.BIOMETRIC_VOICE,
                cryptoObject = null,
                trustedCapture = true,
                compatibilityCapture = false
            )
        )
    }

    @Test
    fun invalidProfileFailsClosed() {
        val invalid = profile.copy(maxCaptureDurationMs = 0L)

        assertEquals(
            SoftwareBiometricSecurityDecision.REJECT_INVALID_PROFILE,
            SoftwareBiometricSecurityPolicy.evaluate(
                profile = invalid,
                requestedType = BiometricType.BIOMETRIC_FACE,
                cryptoObject = null,
                trustedCapture = true,
                compatibilityCapture = false
            )
        )
    }
}
