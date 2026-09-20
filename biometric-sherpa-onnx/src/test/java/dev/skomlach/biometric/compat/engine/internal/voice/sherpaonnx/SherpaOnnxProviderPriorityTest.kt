package dev.skomlach.biometric.compat.engine.internal.voice.sherpaonnx

import dev.skomlach.biometric.compat.engine.core.interfaces.BiometricModule
import dev.skomlach.biometric.compat.engine.internal.voice.VoiceProvider
import org.junit.Assert.assertTrue
import org.junit.Test

class SherpaOnnxProviderPriorityTest {
    @Test
    fun sherpaWinsPromptAndModuleSelectionOverVoiceAuth() {
        assertTrue(SherpaOnnxProvider().promptFactoryPriority > VoiceProvider().promptFactoryPriority)
        assertTrue(SherpaOnnxProvider.MODULE_PRIORITY > BiometricModule.PRIORITY_BELOW_SYSTEM_HARDWARE)
    }
}
