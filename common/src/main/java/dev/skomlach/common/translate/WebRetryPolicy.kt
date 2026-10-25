package dev.skomlach.common.translate

/** Bound server-controlled waits before converting seconds to milliseconds. */
internal fun webRetryDelayMillis(retryAfter: String?, retryCount: Int): Long {
    val seconds = retryAfter?.trim()?.toLongOrNull()?.takeIf { it > 0 }
        ?: (1L shl retryCount.coerceIn(0, 2))
    return seconds.coerceAtMost(5L) * 1_000L
}
