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
}
