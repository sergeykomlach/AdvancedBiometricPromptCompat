package dev.skomlach.biometric.compat

import dev.skomlach.biometric.compat.custom.*
import org.junit.Assert.*
import org.junit.Test

class BiometricTrustPolicyTest {
    private val behavior = BiometricType.BIOMETRIC_BEHAVIOR
    private val face = BiometricType.BIOMETRIC_FACE
    private val controlled = SoftwareBiometricSecurityProfile(face, SoftwareBiometricAssuranceLevel.PASSIVE_MATCH,
        true, false, false, 30_000L)

    @Test fun defaultRejectsCompatibilitySoftwareButAdmitsControlledCapture() {
        val policy = BiometricTrustPolicy()
        assertFalse(policy.permitsAuthentication(face, false, SoftwareBiometricSecurityProfile.passiveCompatibility(face), false))
        assertTrue(policy.permitsAuthentication(face, false, controlled, true))
        assertFalse(policy.permitsAuthentication(face, false, controlled, false))
    }

    @Test fun behaviorNeedsBothExplicitRoleAndCompatibleFloor() {
        val profile = SoftwareBiometricSecurityProfile.passiveCompatibility(behavior)
        assertFalse(BiometricTrustPolicy(minimumTrust = BiometricTrustLevel.COMPATIBILITY)
            .permitsAuthentication(behavior, false, profile, false))
        assertFalse(BiometricTrustPolicy(allowBehaviorOnlySuccess = true)
            .permitsAuthentication(behavior, false, profile, false))
        assertTrue(BiometricTrustPolicy(BiometricTrustLevel.COMPATIBILITY, true)
            .permitsAuthentication(behavior, false, profile, false))
    }

    @Test fun systemMinimumRejectsVendorClaimsAndAllowsHardwareRoutes() {
        val policy = BiometricTrustPolicy(BiometricTrustLevel.SYSTEM_BIOMETRIC)
        assertFalse(policy.permitsAuthentication(face, false,
            controlled.copy(assurance = SoftwareBiometricAssuranceLevel.VENDOR_BACKED), true))
        assertTrue(policy.permitsAuthentication(face, true, null, false))
    }

    @Test fun invalidUnknownOrMismatchedSoftwareCannotMeetAnyFloor() {
        for (level in BiometricTrustLevel.entries) {
            val policy = BiometricTrustPolicy(level, true)
            assertFalse(policy.permitsAuthentication(face, false, null, true))
            assertFalse(policy.permitsAuthentication(face, false, controlled.copy(maxCaptureDurationMs = 0L), true))
            assertFalse(policy.permitsAuthentication(face, false, controlled.copy(biometricType = behavior), true))
        }
    }

    @Test fun higherAssuranceEnumDoesNotReplaceCaptureRequirements() {
        val vendor = controlled.copy(assurance = SoftwareBiometricAssuranceLevel.VENDOR_BACKED)
        assertFalse(BiometricTrustPolicy().permitsAuthentication(face, false, vendor, false))
        assertFalse(BiometricTrustPolicy().permitsAuthentication(face, false,
            vendor.copy(allowsCompatibilityCapture = true), true))
        assertFalse(BiometricTrustPolicy().permitsAuthentication(face, false,
            vendor.copy(requiresTrustedCapture = false), true))
    }

    @Test fun supplementalBehaviorCanBeMeasuredWithoutBecomingAnAuthenticator() {
        val policy = BiometricTrustPolicy(BiometricTrustLevel.SYSTEM_BIOMETRIC)
        val profile = SoftwareBiometricSecurityProfile.passiveCompatibility(behavior)
        assertTrue(policy.permitsExecution(behavior, false, profile, false, supplemental = true))
        assertFalse(policy.permitsExecution(face, false, controlled, true, supplemental = true))
        assertFalse(policy.permitsAuthentication(behavior, false, profile, false))
    }

    @Test fun allRetainsItsAdmittedProvidersWhenLiveAvailabilityShrinks() {
        val required = setOf(face, BiometricType.BIOMETRIC_FINGERPRINT)
        assertEquals(required, resolveAuthenticationTrustScope(BiometricConfirmation.ALL, required,
            setOf(BiometricType.BIOMETRIC_FINGERPRINT), null))
        assertEquals(setOf(face), resolveAuthenticationTrustScope(BiometricConfirmation.ALL, required,
            emptySet(), setOf(face)))
    }

    @Test fun anyCanUseLiveEligibleRoutesWithoutChangingTheFrozenAllRule() {
        val initial = setOf(face, BiometricType.BIOMETRIC_FINGERPRINT)
        val live = setOf(BiometricType.BIOMETRIC_FINGERPRINT)
        assertEquals(live, resolveAuthenticationTrustScope(BiometricConfirmation.ANY, initial, live, null))
        assertTrue(resolveAuthenticationTrustScope(BiometricConfirmation.ALL, emptySet(), live, null).isEmpty())
    }
}
