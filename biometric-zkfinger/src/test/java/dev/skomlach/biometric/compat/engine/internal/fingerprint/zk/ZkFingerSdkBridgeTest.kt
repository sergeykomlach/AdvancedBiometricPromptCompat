package dev.skomlach.biometric.compat.engine.internal.fingerprint.zk

import org.junit.Assert.assertNull
import org.junit.Test

class ZkFingerSdkBridgeTest {
    @Test
    fun degradesToUnavailableWhenConsumerDidNotPackageZkRuntime() {
        val bridge = safelyCreateZkFingerSdkBridge {
            throw NoClassDefFoundError(
                "com/zkteco/android/biometric/module/fingerprintreader/FingprintFactory"
            )
        }

        assertNull(bridge)
    }
}
