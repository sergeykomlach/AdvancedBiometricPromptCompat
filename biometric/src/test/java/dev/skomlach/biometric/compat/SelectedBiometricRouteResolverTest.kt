package dev.skomlach.biometric.compat

import android.Manifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectedBiometricRouteResolverTest {

    @Test
    fun `auto route prefers biometric prompt hardware face before software fallback`() {
        val biometricPromptRoute = SelectedBiometricRoute(
            type = BiometricType.BIOMETRIC_FACE,
            provider = BiometricProviderType.HARDWARE,
            usesBiometricPromptHardware = true,
            permissions = listOf("com.samsung.android.bio.face.permission.USE_FACE")
        )
        val softwareFallbackRoute = SelectedBiometricRoute(
            type = BiometricType.BIOMETRIC_FACE,
            provider = BiometricProviderType.SOFTWARE,
            usesBiometricPromptHardware = false,
            permissions = listOf(Manifest.permission.CAMERA)
        )

        val route = pickSelectedBiometricRoute(
            requestApi = BiometricApi.AUTO,
            preferSystemFaceHardware = true,
            preferHighPrioritySoftware = false,
            biometricPromptRoute = biometricPromptRoute,
            legacyHardwareRoute = null,
            fallbackRoute = softwareFallbackRoute
        )

        assertEquals(biometricPromptRoute, route)
    }

    @Test
    fun `auto route prefers legacy hardware face before software fallback`() {
        val legacyHardwareRoute = SelectedBiometricRoute(
            type = BiometricType.BIOMETRIC_FACE,
            provider = BiometricProviderType.HARDWARE,
            usesBiometricPromptHardware = false,
            permissions = listOf("com.samsung.android.bio.face.permission.USE_FACE")
        )
        val softwareFallbackRoute = SelectedBiometricRoute(
            type = BiometricType.BIOMETRIC_FACE,
            provider = BiometricProviderType.SOFTWARE,
            usesBiometricPromptHardware = false,
            permissions = listOf(Manifest.permission.CAMERA)
        )

        val route = pickSelectedBiometricRoute(
            requestApi = BiometricApi.AUTO,
            preferSystemFaceHardware = true,
            preferHighPrioritySoftware = false,
            biometricPromptRoute = null,
            legacyHardwareRoute = legacyHardwareRoute,
            fallbackRoute = softwareFallbackRoute
        )

        assertEquals(legacyHardwareRoute, route)
    }

    @Test
    fun `legacy MIUI face route avoids a software camera fallback`() {
        val miuiHardwareRoute = SelectedBiometricRoute(
            type = BiometricType.BIOMETRIC_FACE,
            provider = BiometricProviderType.HARDWARE,
            usesBiometricPromptHardware = false,
            permissions = emptyList()
        )
        val softwareFallbackRoute = SelectedBiometricRoute(
            type = BiometricType.BIOMETRIC_FACE,
            provider = BiometricProviderType.SOFTWARE,
            usesBiometricPromptHardware = false,
            permissions = listOf(Manifest.permission.CAMERA)
        )

        val route = pickSelectedBiometricRoute(
            requestApi = BiometricApi.AUTO,
            preferSystemFaceHardware = false,
            preferHighPrioritySoftware = false,
            biometricPromptRoute = null,
            legacyHardwareRoute = miuiHardwareRoute,
            fallbackRoute = softwareFallbackRoute
        )

        assertEquals(miuiHardwareRoute, route)
        assertFalse(route!!.permissions.contains(Manifest.permission.CAMERA))
    }

    @Test
    fun `hardware face route is kept for enroll filtering`() {
        val route = SelectedBiometricRoute(
            type = BiometricType.BIOMETRIC_FACE,
            provider = BiometricProviderType.HARDWARE,
            usesBiometricPromptHardware = true,
            permissions = listOf("com.samsung.android.bio.face.permission.USE_FACE")
        )

        assertTrue(shouldKeepSystemEnrollType(route))
    }

    @Test
    fun `software face route does not keep system type for enroll filtering`() {
        val route = SelectedBiometricRoute(
            type = BiometricType.BIOMETRIC_FACE,
            provider = BiometricProviderType.SOFTWARE,
            usesBiometricPromptHardware = false,
            permissions = listOf(Manifest.permission.CAMERA)
        )

        assertFalse(shouldKeepSystemEnrollType(route))
    }

    @Test
    fun `hardware face route permissions do not include camera`() {
        val route = SelectedBiometricRoute(
            type = BiometricType.BIOMETRIC_FACE,
            provider = BiometricProviderType.HARDWARE,
            usesBiometricPromptHardware = true,
            permissions = listOf("com.samsung.android.bio.face.permission.USE_FACE")
        )

        assertEquals(
            listOf("com.samsung.android.bio.face.permission.USE_FACE"),
            route.permissions
        )
        assertFalse(route.permissions.contains(Manifest.permission.CAMERA))
    }

    @Test
    fun `legacy route keeps above-system fingerprint provider as software enrollment target`() {
        val hardware = SelectedBiometricRoute(
            type = BiometricType.BIOMETRIC_FINGERPRINT,
            provider = BiometricProviderType.HARDWARE,
            usesBiometricPromptHardware = false,
            permissions = emptyList()
        )
        val zkFinger = hardware.copy(provider = BiometricProviderType.SOFTWARE)

        val route = pickSelectedBiometricRoute(
            requestApi = BiometricApi.LEGACY_API,
            preferSystemFaceHardware = false,
            preferHighPrioritySoftware = true,
            biometricPromptRoute = null,
            legacyHardwareRoute = hardware,
            fallbackRoute = zkFinger
        )

        assertEquals(zkFinger, route)
        assertEquals(
            BiometricSetupContinuation.ENROLL_SOFTWARE,
            resolveBiometricSetupContinuation(
                hardwareEnrollmentStillRequired = false,
                hasSoftwareEnrollmentTargets = route?.provider == BiometricProviderType.SOFTWARE,
                hardwareEnrolledThisRun = false
            )
        )
    }

    @Test
    fun autoFingerprintKeepsAboveSystemProviderWithOrWithoutSystemPrompt() {
        val hardware = fingerprintRoute(BiometricProviderType.HARDWARE)
        val zkFinger = fingerprintRoute(BiometricProviderType.SOFTWARE)
        for (requestType in listOf(BiometricType.BIOMETRIC_ANY, BiometricType.BIOMETRIC_FINGERPRINT)) {
            for (systemPrompt in listOf(hardware.copy(usesBiometricPromptHardware = true), null)) {
                val route = pickSelectedBiometricRoute(
                    requestApi = BiometricApi.AUTO,
                    preferSystemFaceHardware = false,
                    preferHighPrioritySoftware = true,
                    biometricPromptRoute = systemPrompt,
                    legacyHardwareRoute = hardware,
                    fallbackRoute = zkFinger,
                    requestType = requestType
                )

                assertEquals(zkFinger, route)
                assertEquals(
                    BiometricSetupContinuation.ENROLL_SOFTWARE,
                    resolveBiometricSetupContinuation(false, route?.provider == BiometricProviderType.SOFTWARE, false)
                )
            }
        }
    }

    @Test
    fun typedFingerprintKeepsSensorSpecificHardwareWithoutAboveSystemProvider() {
        val hardware = fingerprintRoute(BiometricProviderType.HARDWARE)
        val systemPrompt = hardware.copy(usesBiometricPromptHardware = true)
        val lowPrioritySoftware = fingerprintRoute(BiometricProviderType.SOFTWARE)
        for (fallback in listOf(lowPrioritySoftware, hardware, null)) {
            val route = pickSelectedBiometricRoute(
                requestApi = BiometricApi.AUTO,
                preferSystemFaceHardware = false,
                preferHighPrioritySoftware = false,
                biometricPromptRoute = systemPrompt,
                legacyHardwareRoute = hardware,
                fallbackRoute = fallback,
                requestType = BiometricType.BIOMETRIC_FINGERPRINT
            )

            assertEquals(hardware, route)
            assertEquals(
                BiometricSetupContinuation.CONFIRM_HARDWARE,
                resolveBiometricSetupContinuation(false, route?.provider == BiometricProviderType.SOFTWARE, false)
            )
        }
    }

    @Test
    fun anyRequestRetainsSystemPromptWithoutAboveSystemProvider() {
        val hardware = fingerprintRoute(BiometricProviderType.HARDWARE)
        val systemPrompt = hardware.copy(usesBiometricPromptHardware = true)

        assertEquals(
            systemPrompt,
            pickSelectedBiometricRoute(
                requestApi = BiometricApi.AUTO,
                preferSystemFaceHardware = false,
                preferHighPrioritySoftware = false,
                biometricPromptRoute = systemPrompt,
                legacyHardwareRoute = hardware,
                fallbackRoute = fingerprintRoute(BiometricProviderType.SOFTWARE)
            )
        )
    }

    @Test
    fun explicitBiometricApiKeepsSystemPromptDespiteAboveSystemProvider() {
        val systemPrompt = fingerprintRoute(BiometricProviderType.HARDWARE)
            .copy(usesBiometricPromptHardware = true)

        assertEquals(
            systemPrompt,
            pickSelectedBiometricRoute(
                requestApi = BiometricApi.BIOMETRIC_API,
                preferSystemFaceHardware = false,
                preferHighPrioritySoftware = true,
                biometricPromptRoute = systemPrompt,
                legacyHardwareRoute = null,
                fallbackRoute = fingerprintRoute(BiometricProviderType.SOFTWARE)
            )
        )
    }

    private fun fingerprintRoute(provider: BiometricProviderType) = SelectedBiometricRoute(
        type = BiometricType.BIOMETRIC_FINGERPRINT,
        provider = provider,
        usesBiometricPromptHardware = false,
        permissions = emptyList()
    )
}
