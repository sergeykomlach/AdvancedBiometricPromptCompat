package dev.skomlach.biometric.compat

import org.junit.Assert.*
import org.junit.Test
import javax.crypto.Cipher

class HookDetectionResultTest {
    @Test fun detectedHookBecomesAnExplicitFailureWithoutCrypto() {
        val result = AuthenticationResult(BiometricType.BIOMETRIC_FINGERPRINT,
            cryptoObject = BiometricCryptoObject(cipher = Cipher.getInstance("AES/GCM/NoPadding")),
            cryptoSecurityLevel = CryptoSecurityLevel.APP_FLOW_NOT_BIOMETRIC_BOUND)
        val rejected = hookDetectionFailure(setOf(result), true)!!.single()
        assertEquals(result.type, rejected.type)
        assertEquals(AuthenticationFailureReason.HOOK_DETECTED, rejected.reason)
        assertNull(rejected.cryptoObject)
        assertEquals(CryptoSecurityLevel.NONE, rejected.cryptoSecurityLevel)
    }

    @Test fun legitimateSuccessIsNotRewritten() {
        assertNull(hookDetectionFailure(emptySet(), false))
    }

    @Test fun knownHookRejectsSoftwareProviderSuccess() {
        val failure = hookDetectionFailure(setOf(AuthenticationResult(BiometricType.BIOMETRIC_BEHAVIOR)),
            true)
        assertNotNull(failure)
        assertEquals(AuthenticationFailureReason.HOOK_DETECTED, failure!!.single().reason)
    }

    @Test fun emptySuccessStillProducesOneTerminalRejection() {
        val result = hookDetectionFailure(emptySet(), true)!!.single()
        assertNull(result.type)
        assertEquals(AuthenticationFailureReason.HOOK_DETECTED, result.reason)
    }

    @Test fun acceptedDecisionRemainsConsistentWithEnrollmentCleanup() {
        val queue = mutableListOf<() -> Unit>()
        val events = mutableListOf<String>()
        var detected = false
        val confirmed = setOf(AuthenticationResult(BiometricType.BIOMETRIC_FINGERPRINT))
        val failure = hookDetectionFailure(confirmed, detected)
        val completion = AuthFlowCompletion(
            post = { queue += it }, ownsFlow = { true },
            cleanup = { events += if (failure == null) "commit" else "rollback" },
            release = { events += "release"; true }, onClosed = { events += "closed" }
        )
        completion.finish { events += if (failure == null) "success" else "failure" }
        detected = true
        assertNotNull(hookDetectionFailure(confirmed, detected))
        queue.single().invoke()
        assertEquals(listOf("commit", "release", "success", "closed"), events)
    }

    @Test fun hookRejectionRollsBackBeforeFailureAndCannotTurnIntoSuccess() {
        val queue = mutableListOf<() -> Unit>()
        val events = mutableListOf<String>()
        val failure = hookDetectionFailure(emptySet(), true)!!
        val completion = AuthFlowCompletion(
            post = { queue += it }, ownsFlow = { true },
            cleanup = { events += "rollback" }, release = { events += "release"; true },
            onClosed = { events += "closed" }
        )
        completion.finish {
            assertEquals(AuthenticationFailureReason.HOOK_DETECTED, failure.single().reason)
            events += "failure"
        }
        completion.finish { fail("a duplicate terminal result must not replace hook rejection") }
        queue.single().invoke()
        assertEquals(listOf("rollback", "release", "failure", "closed"), events)
    }
}
