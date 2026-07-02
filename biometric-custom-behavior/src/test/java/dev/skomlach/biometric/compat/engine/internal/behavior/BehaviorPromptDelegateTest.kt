package dev.skomlach.biometric.compat.engine.internal.behavior

import dev.skomlach.biometric.compat.BehaviorAuthMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BehaviorPromptDelegateTest {

    @Test
    fun explicitPromptRequiresControllerAndPreparedPayload() {
        assertTrue(shouldInstallBehaviorPrompt(BehaviorAuthMode.EXPLICIT, hasController = true))
        assertFalse(
            isBehaviorPromptReady(
                authMode = BehaviorAuthMode.EXPLICIT,
                hasController = true,
                hasPreparedPayload = false
            )
        )
        assertTrue(
            isBehaviorPromptReady(
                authMode = BehaviorAuthMode.EXPLICIT,
                hasController = true,
                hasPreparedPayload = true
            )
        )
    }

    @Test
    fun passiveOrMissingControllerSkipsPromptReadinessGate() {
        assertFalse(shouldInstallBehaviorPrompt(BehaviorAuthMode.PASSIVE, hasController = true))
        assertTrue(
            isBehaviorPromptReady(
                authMode = BehaviorAuthMode.PASSIVE,
                hasController = true,
                hasPreparedPayload = false
            )
        )
        assertFalse(shouldInstallBehaviorPrompt(BehaviorAuthMode.EXPLICIT, hasController = false))
        assertTrue(
            isBehaviorPromptReady(
                authMode = BehaviorAuthMode.EXPLICIT,
                hasController = false,
                hasPreparedPayload = false
            )
        )
    }
}
