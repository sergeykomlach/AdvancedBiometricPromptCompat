package dev.skomlach.biometric.compat.engine.internal.behavior

import dev.skomlach.biometric.compat.BiometricType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class BehaviorProviderTest {
    @Test
    fun moduleIdPreservesPreviouslyStoredState() {
        // Literal historical name: a future class rename must not change the persisted namespace.
        val previousId = "dev.skomlach.biometric.compat.engine.internal.behavior.BehaviorBiometricManager".hashCode()

        assertEquals(previousId, BehaviorProvider().moduleId)
    }

    @Test
    fun exposesBehaviorPromptFactory() {
        val factory = BehaviorProvider().getPromptFactory()

        assertNotNull(factory)
        assertEquals(BiometricType.BIOMETRIC_BEHAVIOR, factory.biometricType)
    }
}
