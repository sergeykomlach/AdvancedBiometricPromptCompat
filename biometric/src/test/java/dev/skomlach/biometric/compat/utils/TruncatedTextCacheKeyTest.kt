package dev.skomlach.biometric.compat.utils

import org.junit.Assert.assertNotEquals
import org.junit.Test

class TruncatedTextCacheKeyTest {

    @Test
    fun sameConfigurationUsesDifferentCacheKeysForDifferentWindowSizes() {
        val phoneWindow = buildTruncatedTextCacheKey(
            configurationKey = "mcc310-mnc260-port-sw411dp",
            windowWidthPx = 1080,
            windowHeightPx = 2400
        )
        val bubbleWindow = buildTruncatedTextCacheKey(
            configurationKey = "mcc310-mnc260-port-sw411dp",
            windowWidthPx = 540,
            windowHeightPx = 640
        )

        assertNotEquals(phoneWindow, bubbleWindow)
    }
}
