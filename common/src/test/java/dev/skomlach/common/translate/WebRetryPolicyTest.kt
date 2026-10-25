package dev.skomlach.common.translate

import org.junit.Assert.assertEquals
import org.junit.Test

class WebRetryPolicyTest {
    @Test fun boundsServerDelayWithoutOverflow() {
        assertEquals(2_000L, webRetryDelayMillis("2", 0))
        assertEquals(5_000L, webRetryDelayMillis("999999", 0))
        assertEquals(5_000L, webRetryDelayMillis(Long.MAX_VALUE.toString(), 0))
    }

    @Test fun missingOrInvalidHeaderUsesBoundedBackoff() {
        assertEquals(1_000L, webRetryDelayMillis(null, 0))
        assertEquals(2_000L, webRetryDelayMillis("invalid", 1))
        assertEquals(4_000L, webRetryDelayMillis("-1", 2))
        assertEquals(4_000L, webRetryDelayMillis("0", Int.MAX_VALUE))
        assertEquals(1_000L, webRetryDelayMillis("999999999999999999999999", 0))
    }
}
