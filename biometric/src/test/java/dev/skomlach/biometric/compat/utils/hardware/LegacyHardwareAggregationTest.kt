package dev.skomlach.biometric.compat.utils.hardware

import dev.skomlach.biometric.compat.engine.core.interfaces.BiometricModuleState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyHardwareAggregationTest {

    @Test
    fun aggregateLegacyAuthStateStopsAfterFirstUsableModule() {
        var calls = 0

        val state = aggregateLegacyAuthState(
            listOf(
                {
                    calls++
                    moduleState(
                        hardwarePresent = true,
                        enrolled = true,
                        lockedOut = false,
                        permanentlyLocked = false
                    )
                },
                {
                    calls++
                    throw AssertionError("next module should not be probed after usable state")
                }
            )
        )

        assertEquals(1, calls)
        assertTrue(state.hardwareDetected)
        assertTrue(state.enrolled)
        assertFalse(state.lockedOut)
        assertFalse(state.permanentlyLocked)
    }

    @Test
    fun aggregateLegacyAuthStateContinuesWhenEarlierModuleIsNotUsable() {
        var calls = 0

        val state = aggregateLegacyAuthState(
            listOf(
                {
                    calls++
                    moduleState(
                        hardwarePresent = true,
                        enrolled = false,
                        lockedOut = false,
                        permanentlyLocked = false
                    )
                },
                {
                    calls++
                    moduleState(
                        hardwarePresent = true,
                        enrolled = true,
                        lockedOut = false,
                        permanentlyLocked = false
                    )
                }
            )
        )

        assertEquals(2, calls)
        assertTrue(state.hardwareDetected)
        assertTrue(state.enrolled)
        assertFalse(state.lockedOut)
        assertFalse(state.permanentlyLocked)
    }

    private fun moduleState(
        hardwarePresent: Boolean,
        enrolled: Boolean,
        lockedOut: Boolean,
        permanentlyLocked: Boolean
    ) = BiometricModuleState(
        managerAccessible = true,
        hardwarePresent = hardwarePresent,
        enrolled = enrolled,
        lockedOut = lockedOut,
        permanentlyLocked = permanentlyLocked
    )
}
