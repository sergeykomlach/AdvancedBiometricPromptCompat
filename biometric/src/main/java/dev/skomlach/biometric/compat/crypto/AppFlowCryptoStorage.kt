package dev.skomlach.biometric.compat.crypto

import android.util.Base64
import dev.skomlach.common.contextprovider.AndroidContext.appContext
import dev.skomlach.common.storage.SharedPreferenceProvider
import java.security.SecureRandom

object AppFlowCryptoStorage {

    private const val PREFS_NAME = "app_flow_crypto_storage"
    private const val SALT_PREFIX = "salt."
    private val failedSaltWrites = HashSet<String>()

    private val prefs by lazy {
        appContext.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
    }

    private val secretPrefs by lazy {
        SharedPreferenceProvider.getProtectedPreferences("app_flow_crypto_secrets_v3")
    }
    private val secrets = AppFlowSecretStore(
        read = { secretPrefs.getString(it, null) },
        write = { name, secret -> secretPrefs.edit().putString(name, secret).commit() }
    )

    @Synchronized
    internal fun getKeyMaterial(keyName: String, create: Boolean, allowLegacy: Boolean): Pair<CharArray, ByteArray> {
        val salt = if (create) getOrCreateSalt(keyName) else requireSalt(keyName)
        return secrets.getSecret(keyName, create, allowLegacy) to salt
    }

    @Synchronized
    fun getOrCreateSalt(keyName: String): ByteArray {
        readSalt(keyName)?.let { return it }
        val salt = ByteArray(32).also(SecureRandom()::nextBytes)
        updateSalt(keyName, Base64.encodeToString(salt, Base64.NO_WRAP))
        return salt
    }

    @Synchronized
    internal fun requireSalt(keyName: String): ByteArray =
        checkNotNull(readSalt(keyName)) { "App-flow salt is missing" }

    private fun readSalt(keyName: String): ByteArray? {
        check(keyName !in failedSaltWrites) { "App-flow salt was not persisted" }
        return prefs.getString(SALT_PREFIX + keyName, null)?.let {
            Base64.decode(it, Base64.NO_WRAP).also { salt ->
                check(salt.size == 32) { "Invalid app-flow salt length" }
            }
        }
    }

    private fun updateSalt(keyName: String, encodedSalt: String?) {
        // SharedPreferences may publish the edit in memory even when the disk write fails.
        failedSaltWrites.add(keyName)
        check(prefs.edit().putString(SALT_PREFIX + keyName, encodedSalt).commit()) {
            "Unable to persist app-flow salt"
        }
        failedSaltWrites.remove(keyName)
    }

    @Synchronized
    fun delete(keyName: String) {
        check(secretPrefs.edit().remove(keyName).commit()) { "Unable to delete app-flow key" }
        updateSalt(keyName, null)
    }
}
