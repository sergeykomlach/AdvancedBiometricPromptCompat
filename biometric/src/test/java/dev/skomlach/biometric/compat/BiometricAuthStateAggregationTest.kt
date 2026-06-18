package dev.skomlach.biometric.compat

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
}
