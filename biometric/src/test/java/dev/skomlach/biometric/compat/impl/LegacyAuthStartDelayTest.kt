package dev.skomlach.biometric.compat.impl

import org.junit.Assert.assertEquals
import org.junit.Test

class LegacyAuthStartDelayTest {

    @Test
    fun hiddenUnderDisplayFingerprintDialogStartsLegacyAuthImmediately() {
        assertEquals(0L, legacyAuthStartDelayMillis(hideCompatDialog = true))
    }

    @Test
    fun visibleCompatDialogKeepsRenderDelayBeforeLegacyAuth() {
        assertEquals(500L, legacyAuthStartDelayMillis(hideCompatDialog = false))
    }
}
