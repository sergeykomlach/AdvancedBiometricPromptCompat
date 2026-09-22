package dev.skomlach.biometric.compat

import org.junit.Assert.*
import org.junit.Test
import javax.crypto.Cipher

class HookDetectionResultTest {
    @Test fun detectedFastSuccessBecomesAnExplicitFailureWithoutCrypto() {
        val result = AuthenticationResult(BiometricType.BIOMETRIC_FINGERPRINT,
            cryptoObject = BiometricCryptoObject(cipher = Cipher.getInstance("AES/GCM/NoPadding")),
            cryptoSecurityLevel = CryptoSecurityLevel.APP_FLOW_NOT_BIOMETRIC_BOUND)
        val rejected = hookDetectionFailure(setOf(result), true, 0, 200)!!.single()
        assertEquals(result.type, rejected.type)
        assertEquals(AuthenticationFailureReason.HOOK_DETECTED, rejected.reason)
        assertNull(rejected.cryptoObject)
        assertEquals(CryptoSecurityLevel.NONE, rejected.cryptoSecurityLevel)
    }

    @Test fun legitimateSuccessIsNotRewritten() {
        assertNull(hookDetectionFailure(emptySet(), false, 0, 200))
        assertNull(hookDetectionFailure(emptySet(), true, 201, 200))
    }

    @Test fun emptySuccessStillProducesOneTerminalRejection() {
        val result = hookDetectionFailure(emptySet(), true, 200, 200)!!.single()
        assertNull(result.type)
        assertEquals(AuthenticationFailureReason.HOOK_DETECTED, result.reason)
    }
}
