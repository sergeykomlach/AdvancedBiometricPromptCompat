package dev.skomlach.biometric.app.signature

import androidx.fragment.app.FragmentActivity
import dev.skomlach.biometric.compat.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.ConscryptMode(org.robolectric.annotation.ConscryptMode.Mode.OFF)
@Config(sdk = [29], application = android.app.Application::class, manifest = Config.NONE)
class SignatureSystemPolicyTest {
    @Test fun requestRequiresNativeHardwareWithoutCredentialsOrBehaviorAuthorization() {
        BiometricPromptCompat.apiEnabled(false)
        val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup()
        activity.get().getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE).edit()
            .putBoolean("silent", true).putBoolean("crypto", true).putBoolean("allowDeviceCredentials", true).commit()
        val builder = SignatureSystemPolicy.builder(activity.get())
        try {
            val request = builder.getBiometricAuthRequest()
            assertEquals(BiometricApi.BIOMETRIC_API, request.api)
            assertEquals(BiometricProviderType.HARDWARE, request.provider)
            assertEquals(BiometricTrustLevel.SYSTEM_BIOMETRIC, builder.getBiometricTrustPolicy().minimumTrust)
            assertFalse(builder.getBiometricTrustPolicy().allowBehaviorOnlySuccess)
            assertFalse(builder.isDeviceCredentialFallbackAllowed())
            assertFalse(builder.isSilentAuthEnabled())
            assertNull(builder.getCryptographyPurpose())
        } finally { builder.release(); activity.pause().stop().destroy(); BiometricPromptCompat.apiEnabled(true) }
    }

    @Test fun onlyValidResultsFromTheOwnedSystemRequestCanCompleteTheDemo() {
        assertTrue(SignatureSystemPolicy.accepts(setOf(AuthenticationResult(BiometricType.BIOMETRIC_ANY))))
        assertTrue(SignatureSystemPolicy.accepts(setOf(AuthenticationResult(BiometricType.BIOMETRIC_FINGERPRINT))))
        assertFalse(SignatureSystemPolicy.accepts(emptySet()))
        assertFalse(SignatureSystemPolicy.accepts(setOf(AuthenticationResult(BiometricType.BIOMETRIC_BEHAVIOR))))
        assertFalse(SignatureSystemPolicy.accepts(setOf(AuthenticationResult(null))))
        assertFalse(SignatureSystemPolicy.accepts(setOf(AuthenticationResult(BiometricType.BIOMETRIC_ANY,
            reason = AuthenticationFailureReason.AUTHENTICATION_FAILED))))
    }
}
