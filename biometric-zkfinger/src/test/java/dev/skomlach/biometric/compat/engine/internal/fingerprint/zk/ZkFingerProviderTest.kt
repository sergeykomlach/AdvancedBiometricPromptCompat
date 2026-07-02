package dev.skomlach.biometric.compat.engine.internal.fingerprint.zk

import org.junit.Assert.assertNull
import org.junit.Test

class ZkFingerProviderTest {

    @Test
    fun promptFactoryIsNull() {
        assertNull(ZkFingerProvider().getPromptFactory())
    }
}
