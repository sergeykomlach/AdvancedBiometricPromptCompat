package dev.skomlach.biometric.compat.engine.internal.face.tensorflow

import dev.skomlach.biometric.compat.BiometricType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TensorFlowProviderTest {
    @Test
    fun moduleIdPreservesPreviouslyStoredState() {
        // Literal historical name: a future class rename must not change the persisted namespace.
        val previousId = "dev.skomlach.biometric.compat.engine.internal.face.tensorflow.TensorFlowFaceUnlockManager".hashCode()

        assertEquals(previousId, TensorFlowProvider().moduleId)
    }


    @Test
    fun faceTfProviderExposesFacePromptFactory() {
        val factory = TensorFlowProvider().getPromptFactory()

        assertNotNull(factory)
        assertEquals(BiometricType.BIOMETRIC_FACE, factory.biometricType)
        assertTrue(factory is TensorFlowFacePromptFactory)
    }
}
