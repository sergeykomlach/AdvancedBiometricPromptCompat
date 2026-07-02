package dev.skomlach.biometric.compat.engine.internal.face.tensorflow

import org.junit.Assert.assertNull
import org.junit.Test

class TensorFlowProviderTest {

    @Test
    fun promptFactoryIsNull() {
        assertNull(TensorFlowProvider().getPromptFactory())
    }
}
