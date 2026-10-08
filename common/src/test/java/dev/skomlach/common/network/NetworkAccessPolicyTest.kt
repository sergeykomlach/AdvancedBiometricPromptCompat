package dev.skomlach.common.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkAccessPolicyTest {
    @Test
    fun defaultPreservesNetworkUtilitiesButDoesNotRefreshDeviceDatasets() {
        val policy = NetworkAccessPolicy.read { _, default -> default }
        assertTrue(policy.networkEnabled)
        assertFalse(policy.deviceDatasetRefreshEnabled)
    }

    @Test
    fun datasetRefreshRequiresExplicitOptIn() {
        val policy = NetworkAccessPolicy.read { name, default ->
            if (name == NetworkAccessPolicy.DEVICE_DATASET_REFRESH_ENABLED) true else default
        }
        assertTrue(policy.networkEnabled)
        assertTrue(policy.deviceDatasetRefreshEnabled)
    }

    @Test
    fun offlineTakesPrecedenceOverDatasetOptIn() {
        val policy = NetworkAccessPolicy.read { name, _ -> name != NetworkAccessPolicy.NETWORK_ENABLED }
        assertFalse(policy.networkEnabled)
        assertFalse(policy.deviceDatasetRefreshEnabled)
    }

    @Test
    fun malformedSettingFailsClosed() {
        val policy = NetworkAccessPolicy.read { _, _ -> throw IllegalArgumentException("Expected Boolean metadata") }
        assertFalse(policy.networkEnabled)
        assertFalse(policy.deviceDatasetRefreshEnabled)
    }
}
