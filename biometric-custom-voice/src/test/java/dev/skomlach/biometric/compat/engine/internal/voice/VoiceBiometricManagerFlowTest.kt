package dev.skomlach.biometric.compat.engine.internal.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceBiometricManagerFlowTest {
    @Test
    fun successPathDoesNotRequireFixedHalfSecondDelay() {
        assertEquals(0L, VoiceBiometricManager.successResultDelayMsForTest())
    }
}
