package dev.skomlach.biometric.compat.custom

import androidx.annotation.RestrictTo
import java.util.ServiceConfigurationError

/** Shared provider construction boundary for optional biometric services. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
object SoftwareBiometricServices {
    fun <T, R : Any> collect(
        providers: Iterable<T>,
        onError: (Throwable) -> Unit,
        transform: (T) -> R?
    ): List<R> {
        val result = mutableListOf<R>()
        val iterator = providers.iterator()
        var consecutiveFailures = 0
        while (true) {
            val provider = try {
                if (!iterator.hasNext()) break
                iterator.next()
            } catch (error: Throwable) {
                rethrowFatal(error)
                onError(error)
                // A malformed descriptor may fail in hasNext; a constructor may fail in next.
                // Retry either boundary, but never spin forever on a non-advancing loader.
                if (++consecutiveFailures >= 32) {
                    throw ServiceConfigurationError("Biometric service discovery could not advance", error)
                }
                continue
            }
            consecutiveFailures = 0
            try { transform(provider)?.let(result::add) }
            catch (error: Throwable) { rethrowFatal(error); onError(error) }
        }
        return result
    }

    private fun rethrowFatal(error: Throwable) {
        if (error is VirtualMachineError || error is ThreadDeath) throw error
    }
}
