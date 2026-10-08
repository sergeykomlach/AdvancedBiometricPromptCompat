package dev.skomlach.common.translate

import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Includes the limit itself; an oversized response never becomes a partial success. */
internal object BoundedWebResponse {
    const val MAX_BYTES = 10 * 1024 * 1024

    fun readUtf8(input: InputStream): String? {
        val buffer = ByteArray(8 * 1024)
        ByteArrayOutputStream().use { result ->
            while (true) {
                // Read at most one byte beyond the limit, including for chunked responses.
                val length = input.read(buffer, 0, minOf(buffer.size, MAX_BYTES - result.size() + 1))
                if (length == -1) return result.toString("UTF-8")
                if (result.size() + length > MAX_BYTES) return null
                result.write(buffer, 0, length)
            }
        }
    }
}
