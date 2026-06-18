package dev.skomlach.biometric.compat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

class AuthFlowGateTest {

    @Test
    fun tryStartAuthFlowAllowsOnlyFirstCaller() {
        val inProgress = AtomicBoolean(false)

        assertTrue(inProgress.tryStartAuthFlow())
        assertFalse(inProgress.tryStartAuthFlow())
    }
}
