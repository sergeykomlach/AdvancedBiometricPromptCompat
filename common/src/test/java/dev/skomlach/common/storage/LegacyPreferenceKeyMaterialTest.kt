package dev.skomlach.common.storage

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LegacyPreferenceKeyMaterialTest {
    @get:Rule val directory = TemporaryFolder()

    @Test fun saltOnlyDeviceIdStateIsValidAndMissingLegacyFilesAreNeverCreated() {
        assertNull(SharedPreferenceProvider.EncryptionConfig.existingLegacyFileConfig(directory.root))
        assertNull(SharedPreferenceProvider.EncryptionConfig.existingLegacySalt(directory.root))
        assertTrue(directory.root.listFiles()!!.isEmpty())
        val salt = File(directory.root, "bio_hash").apply { writeBytes(ByteArray(128) { it.toByte() }) }
        assertNull(SharedPreferenceProvider.EncryptionConfig.existingLegacyFileConfig(directory.root))
        assertArrayEquals(salt.readBytes(), SharedPreferenceProvider.EncryptionConfig.existingLegacySalt(directory.root))
        assertEquals(listOf(salt), directory.root.listFiles()!!.toList())
    }

    @Test fun completePairIsReadWithoutModificationAndKeyWithoutSaltIsNotRepaired() {
        val password = File(directory.root, "bio_key").apply { writeBytes(ByteArray(32) { (it + 1).toByte() }) }
        assertThrows(IllegalStateException::class.java) {
            SharedPreferenceProvider.EncryptionConfig.existingLegacyFileConfig(directory.root)
        }
        assertFalse(File(directory.root, "bio_hash").exists())
        val salt = File(directory.root, "bio_hash").apply { writeBytes(ByteArray(128) { it.toByte() }) }
        val config = SharedPreferenceProvider.EncryptionConfig.existingLegacyFileConfig(directory.root)!!
        assertArrayEquals(password.readBytes(), config.password)
        assertArrayEquals(salt.readBytes(), config.salt)
        assertEquals(32L, password.length())
        assertEquals(128L, salt.length())
    }
}
