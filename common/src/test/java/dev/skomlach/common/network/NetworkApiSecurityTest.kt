package dev.skomlach.common.network

import org.junit.Assert.assertThrows
import org.junit.Test

class NetworkApiSecurityTest {
    @Test
    fun createConnectionRejectsNonHttpUrls() {
        assertThrows(IllegalArgumentException::class.java) {
            NetworkApi.createConnection("file:///data/local/tmp/payload.json", 1000)
        }
    }
}
