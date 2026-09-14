package dev.skomlach.biometric.compat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthRequestSupportPolicyTest {
    private fun request(api: BiometricApi, type: BiometricType) = BiometricAuthRequest.default().withApi(api).withType(type)
    @Test fun nativeSpecificFaceAndFingerAreNotAdvertised() {
        for (type in listOf(BiometricType.BIOMETRIC_FACE, BiometricType.BIOMETRIC_FINGERPRINT))
            assertFalse(isAuthRequestRouteSupported(request(BiometricApi.BIOMETRIC_API, type), true, true, true, false, false))
    }
    @Test fun pixelAutoFaceIsExcludedButAutoFingerprintIsKept() {
        assertFalse(isAuthRequestRouteSupported(request(BiometricApi.AUTO, BiometricType.BIOMETRIC_FACE), true, false, false, true, false))
        assertTrue(isAuthRequestRouteSupported(request(BiometricApi.AUTO, BiometricType.BIOMETRIC_FINGERPRINT), true, true, true, false, false))
    }
    @Test fun anyRetainsSystemFaceCapability() {
        assertTrue(isAuthRequestRouteSupported(BiometricAuthRequest.default(), true, false, false, true, false))
    }
    @Test fun realLegacyFaceAndSoftwareVoiceRemain() {
        assertTrue(isAuthRequestRouteSupported(request(BiometricApi.AUTO, BiometricType.BIOMETRIC_FACE), true, true, true, true, false))
        assertTrue(isAuthRequestRouteSupported(request(BiometricApi.LEGACY_API, BiometricType.BIOMETRIC_VOICE), false, false, true, false, false))
    }
    @Test fun softwareOnlyDeviceOffersFaceAndVoiceWithoutSystemOrLegacyHardware() {
        for (api in listOf(BiometricApi.AUTO, BiometricApi.LEGACY_API)) {
            for (type in listOf(BiometricType.BIOMETRIC_FACE, BiometricType.BIOMETRIC_VOICE)) {
                assertTrue(isAuthRequestRouteSupported(request(api, type), false, false, true, false, false))
                assertFalse(isAuthRequestRouteSupported(request(api, type), false, false, false, false, false))
            }
        }
    }
    @Test fun priorityDoesNotOverridePreferredNativeFace() {
        assertFalse(isAuthRequestRouteSupported(request(BiometricApi.AUTO, BiometricType.BIOMETRIC_FACE), true, false, true, true, true))
        assertTrue(isAuthRequestRouteSupported(request(BiometricApi.AUTO, BiometricType.BIOMETRIC_VOICE), true, false, true, false, true))
    }
}
