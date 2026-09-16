package dev.skomlach.biometric.compat.engine.internal.fingerprint.zk

import dev.skomlach.biometric.compat.BiometricType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ZkFingerProviderTest {
    @Test
    fun moduleIdPreservesPreviouslyStoredState() {
        // Literal historical name: a future class rename must not change the persisted namespace.
        val previousId = "dev.skomlach.biometric.compat.engine.internal.fingerprint.zk.ZkFingerUnlockManager".hashCode()

        assertEquals(previousId, ZkFingerProvider().moduleId)
    }


    @Test
    fun zkFingerProviderExposesFingerprintPromptFactory() {
        val factory = ZkFingerProvider().getPromptFactory()

        assertNotNull(factory)
        assertEquals(BiometricType.BIOMETRIC_FINGERPRINT, factory.biometricType)
        assertTrue(factory is ZkFingerPromptFactory)
    }
}
