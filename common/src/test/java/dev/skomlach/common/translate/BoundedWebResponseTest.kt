package dev.skomlach.common.translate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream

class BoundedWebResponseTest {
    @Test
    fun acceptsTenMiBWithoutTruncatingTheLastByte() {
        val bytes = ByteArray(10 * 1024 * 1024) { 'a'.code.toByte() }
        bytes[bytes.lastIndex] = 'z'.code.toByte()
        assertEquals(String(bytes, Charsets.UTF_8), BoundedWebResponse.readUtf8(ByteArrayInputStream(bytes)))
    }

    @Test
    fun rejectsOneByteOverTenMiBWithoutReadingTheRest() {
        val input = ByteArrayInputStream(ByteArray(10 * 1024 * 1024 + 100))
        assertNull(BoundedWebResponse.readUtf8(input))
        assertEquals(99, input.available())
    }

    @Test
    fun decodesUtf8AcrossShortReadsAndAcceptsEmptyResponses() {
        val text = "Український переклад"
        val input = object : ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)) {
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
                super.read(buffer, offset, minOf(length, 1))
        }
        assertEquals(text, BoundedWebResponse.readUtf8(input))
        assertEquals("", BoundedWebResponse.readUtf8(ByteArrayInputStream(byteArrayOf())))
    }
}
