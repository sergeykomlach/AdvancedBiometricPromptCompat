package dev.skomlach.common.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Caches a resolved policy, but lets later accesses retry a failed metadata lookup. */
internal class NetworkAccessPolicyCache(private val load: () -> NetworkAccessPolicy) {
    @Volatile private var policy: NetworkAccessPolicy? = null
    private val resolved = MutableStateFlow<NetworkAccessPolicy?>(null)
    val states: StateFlow<NetworkAccessPolicy?> = resolved.asStateFlow()

    fun read(): NetworkAccessPolicy {
        val loaded = policy ?: synchronized(this) {
            policy ?: try {
                load().also { policy = it }
            } catch (_: Exception) {
                null
            }
        }
        // Publish outside the lookup lock: a collector may resume immediately.
        if (loaded != null) resolved.value = loaded
        // Block this access without making a temporary lookup failure permanent.
        return loaded ?: NetworkAccessPolicy.DISABLED
    }
}
