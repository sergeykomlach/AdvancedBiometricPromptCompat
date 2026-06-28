package dev.skomlach.biometric.compat

import dev.skomlach.biometric.compat.engine.core.interfaces.BiometricModuleState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BiometricAuthStateAggregationTest {

    @Test
    fun typedAutoPrefersLegacyEnrollmentWhenLegacyRouteOwnsTypedModality() {
        val legacyFaceState = BiometricAuthState(
            hardwareDetected = true,
            enrolled = false,
            lockedOut = false,
            permanentlyLocked = false
        )
        val genericHardwareState = BiometricAuthState(
            hardwareDetected = true,
            enrolled = true,
            lockedOut = false,
            permanentlyLocked = false
        )

        val state = aggregateTypedAutoBiometricState(
            legacyFaceState,
            genericHardwareState,
            preferLegacyEnrollment = true
        )

        assertTrue(state.hardwareDetected)
        assertFalse(state.enrolled)
        assertFalse(state.available)
    }

    @Test
    fun typedAutoUsesEitherRouteWhenLegacyRouteDoesNotOwnTypedModality() {
        val legacyFaceState = BiometricAuthState(
            hardwareDetected = false,
            enrolled = false,
            lockedOut = false,
            permanentlyLocked = false
        )
        val hardwareFaceState = BiometricAuthState(
            hardwareDetected = true,
            enrolled = true,
            lockedOut = false,
            permanentlyLocked = false
        )

        val state = aggregateTypedAutoBiometricState(
            legacyFaceState,
            hardwareFaceState,
            preferLegacyEnrollment = true
        )

        assertTrue(state.enrolled)
        assertTrue(state.available)
    }

    @Test
    fun typedAutoCanPreferLegacyEnrollmentForHigherPriorityFingerprintRoute() {
        val legacyFingerprintState = BiometricAuthState(
            hardwareDetected = true,
            enrolled = false,
            lockedOut = false,
            permanentlyLocked = false
        )
        val hardwareFingerprintState = BiometricAuthState(
            hardwareDetected = true,
            enrolled = true,
            lockedOut = false,
            permanentlyLocked = false
        )

        val state = aggregateTypedAutoBiometricState(
            legacyFingerprintState,
            hardwareFingerprintState,
            preferLegacyEnrollment = true
        )

        assertTrue(state.hardwareDetected)
        assertFalse(state.enrolled)
        assertFalse(state.available)
        assertTrue(state.readyForEnroll)
    }

    @Test
    fun anyBiometricKeepsBroadEnrollmentSemantics() {
        val faceState = BiometricAuthState(
            hardwareDetected = true,
            enrolled = false,
            lockedOut = false,
            permanentlyLocked = false
        )
        val fingerprintState = BiometricAuthState(
            hardwareDetected = true,
            enrolled = true,
            lockedOut = false,
            permanentlyLocked = false
        )

        val state = aggregateAnyBiometricState(listOf(faceState, fingerprintState))

        assertTrue(state.enrolled)
        assertTrue(state.available)
    }

    @Test
    fun snapshotExposesAggregatedStateAndRoutesForOneRequest() {
        val request = BiometricAuthRequest.default()
            .withApi(BiometricApi.AUTO)
            .withType(BiometricType.BIOMETRIC_FACE)
        val route = BiometricAuthRouteState(
            request = request.withApi(BiometricApi.LEGACY_API),
            source = BiometricAuthRouteSource.LEGACY,
            state = BiometricAuthState(
                hardwareDetected = true,
                enrolled = false,
                lockedOut = false,
                permanentlyLocked = false
            )
        )
        val snapshot = BiometricAuthSnapshot(
            request = request,
            routes = listOf(route),
            state = route.state
        )

        assertTrue(snapshot.readyForEnroll)
        assertFalse(snapshot.available)
        assertTrue(snapshot.routes.single().source == BiometricAuthRouteSource.LEGACY)
    }

    @Test
    fun setupRouteKeepsAlreadyEnrolledHardwareFallback() {
        val fingerprintState = BiometricAuthState(
            hardwareDetected = true,
            enrolled = true,
            lockedOut = false,
            permanentlyLocked = false
        )

        assertTrue(isSetupRouteSelectable(fingerprintState, null, preferModule = false))
    }

    @Test
    fun setupRouteIgnoresNonPreferredSoftwareModuleState() {
        val hardwareFaceState = BiometricAuthState(
            hardwareDetected = true,
            enrolled = true,
            lockedOut = false,
            permanentlyLocked = false
        )
        val softwareFaceState = BiometricModuleState(
            managerAccessible = true,
            hardwarePresent = true,
            enrolled = false,
            lockedOut = true,
            permanentlyLocked = false
        )

        assertTrue(
            isSetupRouteSelectable(
                hardwareFaceState,
                softwareFaceState,
                preferModule = false
            )
        )
    }

    @Test
    fun setupRouteKeepsAlreadyEnrolledPreferredModule() {
        val faceState = BiometricModuleState(
            managerAccessible = true,
            hardwarePresent = true,
            enrolled = true,
            lockedOut = false,
            permanentlyLocked = false
        )

        assertTrue(
            isSetupRouteSelectable(
                BiometricAuthState(
                    hardwareDetected = false,
                    enrolled = false,
                    lockedOut = false,
                    permanentlyLocked = false
                ),
                faceState,
                preferModule = true
            )
        )
    }

    @Test
    fun setupRouteRejectsUnavailableOrLockedRoute() {
        assertFalse(
            isSetupRouteSelectable(
                BiometricAuthState(
                    hardwareDetected = true,
                    enrolled = false,
                    lockedOut = true,
                    permanentlyLocked = false
                ),
                null,
                preferModule = false
            )
        )
    }
}
