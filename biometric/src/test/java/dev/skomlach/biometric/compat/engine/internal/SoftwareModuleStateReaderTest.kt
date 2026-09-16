package dev.skomlach.biometric.compat.engine.internal

import dev.skomlach.biometric.compat.custom.AbstractSoftwareBiometricManager.Companion.CUSTOM_BIOMETRIC_ERROR_HW_UNAVAILABLE
import dev.skomlach.common.storage.ProtectedStorageUnavailableException
import org.junit.Assert.*
import org.junit.Test

class SoftwareModuleStateReaderTest {
    @Test
    fun `unreadable enrollment is unavailable and can recover in same process`() {
        var unavailable = true
        var errors = 0
        fun read() = readSoftwareModuleState(true, false, { true }, {
            if (unavailable) throw ProtectedStorageUnavailableException()
            true
        }, { null }, { errors++ })
        val failed = read()
        assertFalse(failed.managerAccessible)
        assertTrue(failed.lockedOut)
        assertFalse(failed.permanentlyLocked)
        unavailable = false
        val recovered = read()
        assertTrue(recovered.managerAccessible)
        assertTrue(recovered.enrolled)
        assertFalse(recovered.lockedOut)
        assertEquals(1, errors)
    }

    @Test
    fun `unavailable lockout also blocks new enrollment`() {
        val state = readSoftwareModuleState(true, false, { true }, { false },
            { CUSTOM_BIOMETRIC_ERROR_HW_UNAVAILABLE }, { throw AssertionError(it) })
        assertFalse(state.managerAccessible)
        assertTrue(state.lockedOut)
        assertFalse(state.permanentlyLocked)
    }

    @Test
    fun `absent hardware does not initialize protected storage`() {
        val state = readSoftwareModuleState(true, false, { false },
            { throw AssertionError("Enrollment probed") },
            { throw AssertionError("Lockout probed") }, { throw AssertionError(it) })
        assertFalse(state.hardwarePresent)
        assertFalse(state.enrolled)
    }
}
