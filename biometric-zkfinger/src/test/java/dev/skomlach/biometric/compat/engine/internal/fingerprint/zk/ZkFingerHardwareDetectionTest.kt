package dev.skomlach.biometric.compat.engine.internal.fingerprint.zk

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZkFingerHardwareDetectionTest {

    @Test
    fun `hardware is not detected when usb host exists but supported device is absent`() {
        assertFalse(resolveZkHardwareDetected(usbHostAvailable = true, supportedDeviceConnected = false))
    }

    @Test
    fun `hardware is detected only when usb host and supported device are both present`() {
        assertTrue(resolveZkHardwareDetected(usbHostAvailable = true, supportedDeviceConnected = true))
    }
}
