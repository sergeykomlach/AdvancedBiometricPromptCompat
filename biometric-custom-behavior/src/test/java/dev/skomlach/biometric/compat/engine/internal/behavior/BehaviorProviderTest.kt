package dev.skomlach.biometric.compat.engine.internal.behavior

import dev.skomlach.biometric.compat.BiometricType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class BehaviorProviderTest {
    @Test
    fun exposesBehaviorPromptFactory() {
        val factory = BehaviorProvider().getPromptFactory()

        assertNotNull(factory)
        assertEquals(BiometricType.BIOMETRIC_BEHAVIOR, factory.biometricType)
    }
}
