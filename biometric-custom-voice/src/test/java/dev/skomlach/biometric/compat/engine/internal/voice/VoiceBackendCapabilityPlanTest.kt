package dev.skomlach.biometric.compat.engine.internal.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceBackendCapabilityPlanTest {
    @Test
    fun defaultPlanKeepsHeuristicOnly() {
        val plan = VoiceBackendPlan.default()

        assertEquals(VoiceBackendType.HEURISTIC, plan.primary)
        assertTrue(plan.optional.isEmpty())
        assertFalse(plan.isEnabled(VoiceBackendType.SILERO_VAD))
    }

    @Test
    fun sileroVadCanBeAddedAsOptionalBackend() {
        val plan = VoiceBackendPlan.default().withOptional(VoiceBackendType.SILERO_VAD)

        assertEquals(VoiceBackendType.HEURISTIC, plan.primary)
        assertTrue(plan.isEnabled(VoiceBackendType.SILERO_VAD))
        assertTrue(plan.optional.contains(VoiceBackendType.SILERO_VAD))
    }
}
