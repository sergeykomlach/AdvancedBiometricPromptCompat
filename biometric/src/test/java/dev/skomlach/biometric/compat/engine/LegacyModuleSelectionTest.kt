package dev.skomlach.biometric.compat.engine

import androidx.core.os.CancellationSignal
import dev.skomlach.biometric.compat.BiometricCryptoObject
import dev.skomlach.biometric.compat.engine.core.interfaces.AuthenticationListener
import dev.skomlach.biometric.compat.engine.core.interfaces.BiometricModule
import dev.skomlach.biometric.compat.engine.core.interfaces.BiometricModuleState
import dev.skomlach.biometric.compat.engine.core.interfaces.RestartPredicate
import org.junit.Assert.assertSame
import org.junit.Test

class LegacyModuleSelectionTest {

    @Test
    fun `above-system module wins during auth even when only system hardware is enrolled`() {
        val zkModule = FakeModule(priority = BiometricModule.PRIORITY_ABOVE_SYSTEM_HARDWARE, tag = 1)
        val systemModule = FakeModule(priority = BiometricModule.PRIORITY_SYSTEM_HARDWARE, tag = 2)

        val selected = selectPreferredBiometricModule(
            listOf(
                zkModule to moduleState(enrolled = false),
                systemModule to moduleState(enrolled = true)
            ),
            enroll = false
        )

        assertSame(zkModule, selected)
    }

    @Test
    fun `auth falls back to enrolled system module when no above-system module exists`() {
        val systemModule = FakeModule(priority = BiometricModule.PRIORITY_SYSTEM_HARDWARE, tag = 2)
        val fallbackModule = FakeModule(priority = BiometricModule.PRIORITY_BELOW_SYSTEM_HARDWARE, tag = 3)

        val selected = selectPreferredBiometricModule(
            listOf(
                systemModule to moduleState(enrolled = true),
                fallbackModule to moduleState(enrolled = false)
            ),
            enroll = false
        )

        assertSame(systemModule, selected)
    }

    private fun moduleState(enrolled: Boolean): BiometricModuleState {
        return BiometricModuleState(
            managerAccessible = true,
            hardwarePresent = true,
            enrolled = enrolled,
            lockedOut = false,
            permanentlyLocked = false
        )
    }

    private class FakeModule(
        override val priority: Int,
        private val tag: Int
    ) : BiometricModule {
        override val isManagerAccessible: Boolean = true
        override val isHardwarePresent: Boolean = true
        override val isLockOut: Boolean = false
        override val isUserAuthCanByUsedWithCrypto: Boolean = false
        override val hasEnrolled: Boolean = false

        @Deprecated("Unused in tests")
        override val isBiometricEnrollChanged: Boolean = false

        override fun authenticate(
            biometricCryptoObject: BiometricCryptoObject?,
            cancellationSignal: CancellationSignal?,
            listener: AuthenticationListener?,
            restartPredicate: RestartPredicate?
        ) = Unit

        override fun tag(): Int = tag
    }
}
