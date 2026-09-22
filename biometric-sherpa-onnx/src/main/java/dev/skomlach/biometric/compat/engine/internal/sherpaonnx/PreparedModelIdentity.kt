package dev.skomlach.biometric.compat.engine.internal.sherpaonnx

import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

/** Explicit worker-side preparation; UI getters never perform I/O or wait for the digest lock. */
internal class PreparedModelIdentity(private val openModel: () -> InputStream) {
    @Volatile var value: String? = null
        private set

    @Synchronized
    fun prepare(): String? {
        value?.let { return it }
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            openModel().use { input ->
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            ("sherpa-speaker:waveform-v1:" + digest.digest().joinToString("") {
                "%02x".format(it)
            }).also { value = it }
        } catch (_: IOException) {
            null
        }
    }
}
