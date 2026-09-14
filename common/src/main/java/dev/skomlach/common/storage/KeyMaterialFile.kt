package dev.skomlach.common.storage

import java.io.File
import java.io.FileOutputStream

/** Never replace existing key material after an I/O, unwrap or validation failure. */
internal object KeyMaterialFile {
    @Synchronized
    fun readOrCreate(
        file: File,
        size: Int,
        create: () -> ByteArray,
        decode: (ByteArray) -> ByteArray = { it },
        encode: (ByteArray) -> ByteArray = { it }
    ): ByteArray {
        try {
            if (file.exists()) {
                val bytes = try {
                    file.setReadable(true, true)
                    decode(file.readBytes())
                } finally {
                    file.setReadable(false, false)
                }
                check(bytes.size == size) { "Invalid protected key material length" }
                return bytes
            }
            val bytes = create()
            check(bytes.size == size) { "Invalid new key material length" }
            // Encrypt before touching the destination; a failed Keystore call writes nothing.
            val encoded = encode(bytes)
            val temporary = File.createTempFile(file.name, ".tmp", file.parentFile)
            try {
                FileOutputStream(temporary).use { output ->
                    output.write(encoded)
                    output.fd.sync()
                }
                check(!file.exists() && temporary.renameTo(file)) { "Cannot persist key material" }
                file.setReadable(false, false)
                file.setExecutable(false, false)
                file.setReadOnly()
            } finally {
                temporary.delete()
            }
            return bytes
        } catch (error: Exception) {
            throw ProtectedStorageUnavailableException("Cannot access protected key material", error)
        }
    }
}
