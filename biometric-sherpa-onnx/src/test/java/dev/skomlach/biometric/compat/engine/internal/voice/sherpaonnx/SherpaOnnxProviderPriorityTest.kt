package dev.skomlach.biometric.compat.engine.internal.sherpaonnx

import dev.skomlach.biometric.compat.engine.core.interfaces.BiometricModule
import org.junit.Assert.assertTrue
import org.junit.Test

class SherpaOnnxProviderPriorityTest {
    @Test
    fun sherpaUsesTheHighPrioritySoftwareVoiceSlot() {
        assertTrue(SherpaOnnxProvider().promptFactoryPriority > 0)
        assertTrue(SherpaOnnxProvider.MODULE_PRIORITY > BiometricModule.PRIORITY_BELOW_SYSTEM_HARDWARE)
    }
}
