package dev.skomlach.biometric.compat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Api28StartAuthPlanTest {

    @Test
    fun `mixed stage defers software secondary that requires prepared extras`() {
        val voiceRoute = SelectedBiometricRoute(
            type = BiometricType.BIOMETRIC_VOICE,
            provider = BiometricProviderType.SOFTWARE,
            usesBiometricPromptHardware = false,
            permissions = emptyList()
        )
        val fingerprintRoute = SelectedBiometricRoute(
            type = BiometricType.BIOMETRIC_FINGERPRINT,
            provider = BiometricProviderType.HARDWARE,
            usesBiometricPromptHardware = false,
            permissions = emptyList()
        )

        val plan = planApi28StartAuthStage(
            remainingPrimaryTypes = listOf(BiometricType.BIOMETRIC_FACE),
            remainingSecondaryTypes = listOf(
                BiometricType.BIOMETRIC_VOICE,
                BiometricType.BIOMETRIC_FINGERPRINT
            ),
            routeForType = { type: BiometricType ->
                when (type) {
                    BiometricType.BIOMETRIC_VOICE -> voiceRoute
                    BiometricType.BIOMETRIC_FINGERPRINT -> fingerprintRoute
                    else -> null
                }
            },
            requiresReadyExtrasBeforeAuthentication = { type: BiometricType ->
                type == BiometricType.BIOMETRIC_VOICE
            }
        )

        assertTrue(plan.shouldShowSystemPrompt)
        assertEquals(listOf(BiometricType.BIOMETRIC_FINGERPRINT), plan.legacyAuthTypes)
    }

    @Test
    fun `mixed stage keeps software secondary that can start without prepared extras`() {
        val faceRoute = SelectedBiometricRoute(
            type = BiometricType.BIOMETRIC_FACE,
            provider = BiometricProviderType.SOFTWARE,
            usesBiometricPromptHardware = false,
            permissions = emptyList()
        )

        val plan = planApi28StartAuthStage(
            remainingPrimaryTypes = listOf(BiometricType.BIOMETRIC_IRIS),
            remainingSecondaryTypes = listOf(BiometricType.BIOMETRIC_FACE),
            routeForType = { _: BiometricType -> faceRoute },
            requiresReadyExtrasBeforeAuthentication = { false }
        )

        assertTrue(plan.shouldShowSystemPrompt)
        assertEquals(listOf(BiometricType.BIOMETRIC_FACE), plan.legacyAuthTypes)
    }

    @Test
    fun `legacy only stage runs deferred software secondary after prompt ready`() {
        val voiceRoute = SelectedBiometricRoute(
            type = BiometricType.BIOMETRIC_VOICE,
            provider = BiometricProviderType.SOFTWARE,
            usesBiometricPromptHardware = false,
            permissions = emptyList()
        )

        val plan = planApi28StartAuthStage(
            remainingPrimaryTypes = emptyList<BiometricType>(),
            remainingSecondaryTypes = listOf(BiometricType.BIOMETRIC_VOICE),
            routeForType = { _: BiometricType -> voiceRoute },
            requiresReadyExtrasBeforeAuthentication = { true }
        )

        assertFalse(plan.shouldShowSystemPrompt)
        assertEquals(listOf(BiometricType.BIOMETRIC_VOICE), plan.legacyAuthTypes)
    }

    @Test
    fun `any confirmation does not start fallback while system prompt is active`() {
        val plan = planApi28StartAuthStage(
            confirmation = BiometricConfirmation.ANY,
            remainingPrimaryTypes = listOf(BiometricType.BIOMETRIC_FACE),
            remainingSecondaryTypes = listOf(BiometricType.BIOMETRIC_FINGERPRINT),
            routeForType = { null },
            requiresReadyExtrasBeforeAuthentication = { false }
        )

        assertTrue(plan.shouldShowSystemPrompt)
        assertTrue(plan.legacyAuthTypes.isEmpty())
    }
}
