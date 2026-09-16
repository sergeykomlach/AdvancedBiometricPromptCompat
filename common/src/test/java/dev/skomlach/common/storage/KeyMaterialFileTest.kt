package dev.skomlach.common.storage

import java.security.ProviderException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class KeyMaterialFileTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun `unwrap outage preserves bytes and recovery reads the same key`() {
        val file = temporary.newFile("bio_key_v2")
        val wrapped = byteArrayOf(9, 8, 7)
        file.writeBytes(wrapped)
        var creates = 0
        val failure = ProviderException("Keystore unavailable")
        val error = assertThrows(ProtectedStorageUnavailableException::class.java) {
            KeyMaterialFile.readOrCreate(file, 3, { creates++; byteArrayOf(1, 2, 3) },
                decode = { throw failure })
        }
        assertSame(failure, error.cause)
        file.setReadable(true, true)
        assertArrayEquals(wrapped, file.readBytes())
        val recovered = KeyMaterialFile.readOrCreate(file, 3, { creates++; byteArrayOf(0, 0, 0) })
        assertArrayEquals(wrapped, recovered)
        assertEquals(0, creates)
    }

    @Test
    fun `invalid length is preserved rather than regenerated`() {
        val file = temporary.newFile("bio_hash_v2").apply { writeBytes(byteArrayOf(3)) }
        assertThrows(ProtectedStorageUnavailableException::class.java) {
            KeyMaterialFile.readOrCreate(file, 128, { throw AssertionError("Must not regenerate") })
        }
        file.setReadable(true, true)
        assertArrayEquals(byteArrayOf(3), file.readBytes())
    }

    @Test
    fun `encryption failure leaves no destination and a later attempt can succeed`() {
        val file = java.io.File(temporary.root, "bio_key_v2")
        assertThrows(ProtectedStorageUnavailableException::class.java) {
            KeyMaterialFile.readOrCreate(file, 3, { byteArrayOf(1, 2, 3) },
                encode = { throw ProviderException("Keystore unavailable") })
        }
        assertFalse(file.exists())
        assertArrayEquals(byteArrayOf(1, 2, 3),
            KeyMaterialFile.readOrCreate(file, 3, { byteArrayOf(1, 2, 3) }))
        file.setReadable(true, true)
        assertArrayEquals(byteArrayOf(1, 2, 3), file.readBytes())
    }
}
