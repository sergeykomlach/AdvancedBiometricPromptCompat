package dev.skomlach.biometric.compat.engine.internal

import dev.skomlach.biometric.compat.custom.AbstractSoftwareBiometricManager
import dev.skomlach.biometric.compat.custom.SoftwareBiometricWorkSession
import dev.skomlach.biometric.compat.custom.SoftwareBiometricWorkerCallback
import org.junit.Assert.*
import org.junit.Test

class EnrollmentRollbackSessionTest {
    private fun transaction() = EnrollmentRollbackSession<String>(
        start = { EnrollmentRollbackScope() },
        finish = { _, scope, succeeded -> scope.finish(succeeded) }
    )

    @Test fun cancelAfterCommitBeforeSuccessDeliveryRollsBackAnUnconfirmedStage() {
        val templates = mutableSetOf("existing")
        val transaction = transaction()
        val scope = transaction.track("voice")
        val operation = SoftwareBiometricWorkSession()
        val queue = ArrayDeque<() -> Unit>()
        var successes = 0
        val callback = SoftwareBiometricWorkerCallback(operation,
            object : AbstractSoftwareBiometricManager.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: AbstractSoftwareBiometricManager.AuthenticationResult?) {
                    successes++
                }
            }, enqueue = { queue.addLast(it) })
        val tag = provisionalEnrollment { it }
        scope.record { templates.remove(tag) }
        operation.runIfActive { templates.add(tag) }
        callback.onAuthenticationSucceeded(null)
        operation.cancel()
        // No confirmed-success bookkeeping occurred. This is still owned by the transaction.
        transaction.finish(succeeded = false)
        while (queue.isNotEmpty()) queue.removeFirst().invoke()
        assertEquals(setOf("existing"), templates)
        assertEquals(0, successes)
    }

    @Test fun failedWholeSetupRemovesAllStartedStagesAndRetriesOnly() {
        val templates = mutableSetOf("existing", "voice-1", "voice-retry", "finger")
        val transaction = transaction()
        val voice = transaction.track("voice")
        voice.record { templates.remove("voice-1") }
        assertSame(voice, transaction.track("voice"))
        transaction.track("voice").record { templates.remove("voice-retry") }
        transaction.track("finger").record { templates.remove("finger") }
        transaction.finish(false)
        transaction.finish(false)
        assertEquals(setOf("existing"), templates)
    }

    @Test fun successfulWholeSetupKeepsTemplatesAndCannotBeRolledBackAgain() {
        val templates = mutableSetOf("existing", "new")
        val transaction = transaction()
        transaction.track("voice").record { templates.remove("new") }
        transaction.finish(true)
        transaction.finish(false)
        assertEquals(setOf("existing", "new"), templates)
        assertThrows(IllegalStateException::class.java) { transaction.track("late-stage") }
    }
}
