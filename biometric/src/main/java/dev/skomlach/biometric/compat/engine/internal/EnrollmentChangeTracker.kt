package dev.skomlach.biometric.compat.engine.internal

import dev.skomlach.common.misc.HexUtils
import java.security.MessageDigest

internal enum class EnrollmentChange {
    UNCHANGED, CHANGED, UNAVAILABLE, UNSUPPORTED
}

private const val BASELINE_HEADER = "v1\n"
private const val SHA256_HEX_LENGTH = 64

// EncryptedSharedPreferences.getStringSet collapses an empty set into its default value.
// Use an explicit header so a persisted empty baseline remains distinct from a missing key.
internal fun encodeEnrollmentBaseline(hashes: Set<String>): String =
    hashes.sorted().joinToString(separator = "\n", prefix = BASELINE_HEADER)

internal fun decodeEnrollmentBaseline(value: Any?): Set<String> {
    require(value is String && value.startsWith(BASELINE_HEADER)) { "Invalid enrollment baseline" }
    val payload = value.removePrefix(BASELINE_HEADER)
    if (payload.isEmpty()) return emptySet()
    val hashes = payload.split('\n')
    require(hashes.all { hash ->
        hash.length == SHA256_HEX_LENGTH && hash.all { it in '0'..'9' || it in 'A'..'F' }
    } && hashes.distinct().size == hashes.size) { "Invalid enrollment hashes" }
    return hashes.toSet()
}

/** Keeps legacy snapshots intact until a comparable snapshot or an explicit acknowledgement exists. */
internal class EnrollmentChangeTracker(
    private val readSnapshot: () -> EnrollmentSnapshot,
    private val readBaseline: () -> Set<String>?,
    private val readLegacyBaseline: () -> Set<String>?,
    private val writeBaseline: (Set<String>) -> Unit,
    private val onError: (Throwable) -> Unit
) {
    @Volatile
    var lastConfirmedChange: Boolean = false
        private set

    private var writeFailed = false

    @Synchronized
    fun check(): EnrollmentChange = safely {
        when (val snapshot = readSnapshot()) {
            EnrollmentSnapshot.Unsupported -> EnrollmentChange.UNSUPPORTED
            is EnrollmentSnapshot.Unavailable -> EnrollmentChange.UNAVAILABLE
            is EnrollmentSnapshot.Available -> {
                if (writeFailed) return@safely EnrollmentChange.UNAVAILABLE
                val current = hashes(snapshot.ids)
                val baseline = readBaseline()
                if (baseline != null) {
                    confirmed(current != baseline)
                } else {
                    val legacy = readLegacyBaseline()
                    if (legacy != null && legacy != current) {
                        // Do not silently accept a different or incomparable legacy snapshot.
                        confirmed(true)
                    } else {
                        // Fresh install or an exactly matching legacy set; preserve the legacy key.
                        persist(current)
                        confirmed(false)
                    }
                }
            }
        }
    }

    @Synchronized
    fun acknowledge(): EnrollmentChange = safely {
        when (val snapshot = readSnapshot()) {
            EnrollmentSnapshot.Unsupported -> EnrollmentChange.UNSUPPORTED
            is EnrollmentSnapshot.Unavailable -> EnrollmentChange.UNAVAILABLE
            is EnrollmentSnapshot.Available -> {
                persist(hashes(snapshot.ids))
                confirmed(false)
            }
        }
    }

    private fun persist(snapshot: Set<String>) {
        // A failed preferences commit can already have changed its in-memory map.
        writeFailed = true
        writeBaseline(snapshot)
        writeFailed = false
    }

    private fun confirmed(changed: Boolean): EnrollmentChange {
        lastConfirmedChange = changed
        return if (changed) EnrollmentChange.CHANGED else EnrollmentChange.UNCHANGED
    }

    private fun safely(action: () -> EnrollmentChange): EnrollmentChange = try {
        action()
    } catch (error: Exception) {
        onError(error)
        EnrollmentChange.UNAVAILABLE
    } catch (error: LinkageError) {
        onError(error)
        EnrollmentChange.UNAVAILABLE
    }

    private fun hashes(ids: Set<String>): Set<String> {
        val digest = MessageDigest.getInstance("SHA-256")
        // Match legacy hashes for plain string IDs; class names and iteration order are irrelevant.
        return ids.mapTo(HashSet()) { HexUtils.bytesToHex(digest.digest(it.toByteArray(Charsets.UTF_8))) }
    }
}
