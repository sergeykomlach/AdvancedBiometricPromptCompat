package dev.skomlach.biometric.compat.utils

import java.security.MessageDigest

/** One versioned preference per capability; its input signature invalidates stale decisions. */
internal class BiometricCapabilityCache(
    private val read: (String) -> String?,
    private val write: (String, String) -> Unit
) {
    fun get(capability: String, inputs: List<String>): String? {
        val prefix = signature(inputs) + ":"
        return read(key(capability))?.takeIf { it.startsWith(prefix) }?.removePrefix(prefix)
    }

    fun put(capability: String, inputs: List<String>, value: String) {
        val key = key(capability)
        val record = signature(inputs) + ":" + value
        if (read(key) != record) write(key, record)
    }

    private fun key(capability: String) = "$capability-evidence-v3"

    private fun signature(inputs: List<String>): String {
        // Length prefixes preserve field boundaries, including separators inside metadata.
        val data = inputs.joinToString("") { "${it.length}:$it" }.toByteArray(Charsets.UTF_8)
        val hex = "0123456789abcdef"
        return buildString(64) {
            MessageDigest.getInstance("SHA-256").digest(data).forEach { byte ->
                val value = byte.toInt() and 0xff
                append(hex[value ushr 4]); append(hex[value and 0x0f])
            }
        }
    }
}
