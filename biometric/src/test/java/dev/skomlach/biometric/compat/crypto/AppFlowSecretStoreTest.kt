package dev.skomlach.biometric.compat.crypto

import org.junit.Assert.*
import org.junit.Test

class AppFlowSecretStoreTest {
    @Test fun unavailableStoreNeverCreatesReplacementOrUsesLegacySecret() {
        var unavailable = true
        var writes = 0
        val store = AppFlowSecretStore({
            if (unavailable) throw dev.skomlach.common.storage.ProtectedStorageUnavailableException()
            "existing-secret"
        }, { _, _ -> writes++; true })
        for (create in listOf(true, false)) {
            assertThrows(dev.skomlach.common.storage.ProtectedStorageUnavailableException::class.java) {
                store.getSecret("key", create, allowLegacy = true)
            }
        }
        assertEquals(0, writes)
        unavailable = false
        assertEquals("existing-secret", String(store.getSecret("key", true)))
        assertEquals(0, writes)
    }

    @Test fun randomSecretsPersistPerKeyAndAreNotDerivedFromPublicNames() {
        val storage = mutableMapOf<String, String>()
        val store = AppFlowSecretStore(storage::get) { name, value -> storage[name] = value; true }
        val name = "BiometricModule1"
        val first = store.getSecret(name, true)
        assertEquals(64, first.size)
        assertNotEquals(name.reversed(), String(first))
        assertArrayEquals(first, store.getSecret(name, false))
        assertNotEquals(String(first), String(store.getSecret("BiometricModule2", true)))
        first.fill('\u0000')
        assertEquals(storage[name], String(store.getSecret(name, false)))
    }

    @Test
    fun decryptFallsBackToLegacySecretWhenProtectedSecretIsMissing() {
        val keyName = "BiometricModule1"
        val secret = AppFlowSecretStore(
            read = { null },
            write = { _, _ -> fail("decryption must not persist before ciphertext is verified"); false }
        ).getSecret(keyName, false)

        assertArrayEquals(keyName.toCharArray().reversedArray(), secret)
    }

    @Test(expected = IllegalStateException::class)
    fun persistenceFailureCannotReturnAnEphemeralEncryptionKey() {
        AppFlowSecretStore({ null }, { _, _ -> false }).getSecret("key", true)
    }
}
