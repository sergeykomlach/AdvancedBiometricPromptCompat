package dev.skomlach.common.storage

import java.util.concurrent.ConcurrentHashMap

internal class PreferenceKeyResolver {
    private val plainToEncrypted = ConcurrentHashMap<String, String>()

    fun resolve(
        plainKey: String,
        directLookup: () -> String?,
        scanStoredKeys: () -> Sequence<String>,
        decryptStoredKey: (String) -> String?
    ): String? {
        plainToEncrypted[plainKey]?.let { return it }

        directLookup()?.let { encryptedKey ->
            remember(plainKey, encryptedKey)
            return encryptedKey
        }

        val resolved = scanStoredKeys().firstOrNull { storedKey ->
            decryptStoredKey(storedKey) == plainKey
        }
        if (resolved != null) {
            remember(plainKey, resolved)
        }
        return resolved
    }

    fun remember(plainKey: String, encryptedKey: String) {
        plainToEncrypted[plainKey] = encryptedKey
    }

    fun forget(plainKey: String) {
        plainToEncrypted.remove(plainKey)
    }

    fun clear() {
        plainToEncrypted.clear()
    }
}
