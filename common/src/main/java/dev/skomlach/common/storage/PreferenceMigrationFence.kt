package dev.skomlach.common.storage

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.KeyGenerator

/** null = never begun; SHA-256 = immutable source authorized for retry; COMMITTED = no legacy. */
internal interface PreferenceMigrationFence {
    fun state(): String?
    fun begin(sourceDigest: String)
    fun complete()

    companion object { const val COMMITTED = "committed" }
}

/** Alias state cannot be rolled back by replacing the app's preference XML or key-material files. */
internal class KeyStorePreferenceMigrationFence(fileIdentity: String) : PreferenceMigrationFence {
    private val prefix = "BiometricCompat.Prefs.v3." + sha256(fileIdentity.toByteArray(Charsets.UTF_8))
    private val pendingPrefix = "$prefix.pending."
    private val committed = "$prefix.committed"

    override fun state(): String? = protected {
        val store = store()
        if (store.containsAlias(committed)) return@protected PreferenceMigrationFence.COMMITTED
        val pending = store.aliases().toList().filter { it.startsWith(pendingPrefix) }
        check(pending.size <= 1) { "Ambiguous preference migration fence" }
        pending.singleOrNull()?.removePrefix(pendingPrefix)?.also {
            check(it.matches(Regex("[a-f0-9]{64}"))) { "Invalid preference migration fence" }
        }
    }

    override fun begin(sourceDigest: String) = protected {
        check(sourceDigest.matches(Regex("[a-f0-9]{64}")))
        when (val previous = state()) {
            null -> create(pendingPrefix + sourceDigest)
            sourceDigest -> Unit
            else -> throw ProtectedStorageUnavailableException("Preference migration source changed: $previous")
        }
    }

    override fun complete() = protected { create(committed) }

    private fun store() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private fun create(alias: String) {
        if (store().containsAlias(alias)) return
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).build())
        }.generateKey()
    }

    private inline fun <T> protected(action: () -> T): T = try { action() } catch (error: Exception) {
        if (error is ProtectedStorageUnavailableException) throw error
        throw ProtectedStorageUnavailableException("Cannot establish protected preference migration fence", error)
    }
}

internal fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
