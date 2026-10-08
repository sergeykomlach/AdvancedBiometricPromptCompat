package dev.skomlach.common.network

/** Decoded manifest settings; successfully loaded settings are cached for the process. */
internal data class NetworkAccessPolicy(
    val networkEnabled: Boolean,
    val deviceDatasetRefreshEnabled: Boolean
) {
    companion object {
        const val NETWORK_ENABLED = "dev.skomlach.common.NETWORK_ENABLED"
        const val DEVICE_DATASET_REFRESH_ENABLED = "dev.skomlach.common.DEVICE_DATASET_REFRESH_ENABLED"
        val DISABLED = NetworkAccessPolicy(networkEnabled = false, deviceDatasetRefreshEnabled = false)

        fun read(readBoolean: (String, Boolean) -> Boolean): NetworkAccessPolicy = try {
            val enabled = readBoolean(NETWORK_ENABLED, true)
            NetworkAccessPolicy(enabled, enabled && readBoolean(DEVICE_DATASET_REFRESH_ENABLED, false))
        } catch (_: Exception) {
            // Malformed settings must not silently bypass an application's offline policy.
            DISABLED
        }
    }
}
