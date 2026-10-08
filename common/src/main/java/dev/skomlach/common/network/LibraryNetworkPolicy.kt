package dev.skomlach.common.network

import android.content.pm.PackageManager
import dev.skomlach.common.contextprovider.AndroidContext

internal object LibraryNetworkPolicy {
    private val cache = NetworkAccessPolicyCache {
        // Lookup failures remain retryable; malformed values below resolve to a disabled policy.
        val context = AndroidContext.appContext
        val metadata = context.packageManager.getApplicationInfo(
            context.packageName, PackageManager.GET_META_DATA
        ).metaData
        NetworkAccessPolicy.read { name, default ->
            if (metadata?.containsKey(name) == true) {
                // Reject a malformed value instead of silently enabling network traffic.
                @Suppress("DEPRECATION")
                metadata.get(name) as? Boolean ?: throw IllegalArgumentException("Expected Boolean metadata")
            } else default
        }
    }

    val resolvedPolicy get() = cache.states
    val networkEnabled: Boolean get() = cache.read().networkEnabled
    val deviceDatasetRefreshEnabled: Boolean get() = cache.read().deviceDatasetRefreshEnabled
}
