package dev.skomlach.common.storage

import android.content.SharedPreferences
import java.lang.reflect.Proxy
import java.security.ProviderException
import org.junit.Assert.*
import org.junit.Test

class ProtectedStorageFailureTest {
    @Test
    fun `unavailable reads never become missing defaults and writes never touch backing store`() {
        var accesses = 0
        var attempts = 0
        val backing = Proxy.newProxyInstance(javaClass.classLoader,
            arrayOf(SharedPreferences::class.java)) { _, _, _ ->
            accesses++
            throw AssertionError("Backing preferences accessed while Keystore unavailable")
        } as SharedPreferences
        val cause = ProviderException("temporarily unavailable")
        val prefs = EncryptedSharedPreferences(backing) {
            attempts++
            throw cause
        }
        val operations: List<() -> Any?> = listOf(
            { prefs.getString("secret", "default") },
            { prefs.getInt("permanent_lockout_count", 0) },
            { prefs.getLong("lockout_end_timestamp", 0) },
            { prefs.getBoolean("enrolled", false) },
            { prefs.getFloat("score", 0f) },
            { prefs.getStringSet("templates", emptySet()) },
            { prefs.all }, { prefs.contains("secret") },
            { prefs.edit().remove("secret").commit() },
            { prefs.edit().clear().apply() }
        )
        operations.forEach { operation ->
            val error = assertThrows(ProtectedStorageUnavailableException::class.java) { operation() }
            assertSame(cause, error.cause)
        }
        assertEquals(0, accesses)
        // Lazy initialization failures are retried, not cached as an empty store.
        assertEquals(operations.size, attempts)
    }

    @Test
    fun `optional reset failure preserves success and does not skip later modules`() {
        var failures = 0
        var secondReset = false
        var success = false
        listOf<() -> Unit>(
            { throw ProtectedStorageUnavailableException() },
            { secondReset = true }
        ).forEach { action -> runProtectedStorageMaintenance({ failures++ }, action) }
        success = true
        assertTrue(success)
        assertTrue(secondReset)
        assertEquals(1, failures)
    }

    @Test
    fun `failed template commit is not reported as success`() {
        val editor = Proxy.newProxyInstance(javaClass.classLoader,
            arrayOf(SharedPreferences.Editor::class.java)) { proxy, method, _ ->
            if (method.name == "commit") false else proxy
        } as SharedPreferences.Editor
        val prefs = Proxy.newProxyInstance(javaClass.classLoader,
            arrayOf(SharedPreferences::class.java)) { _, _, _ -> editor } as SharedPreferences
        var success = false
        assertThrows(ProtectedStorageUnavailableException::class.java) {
            prefs.editProtected { putString("template", "ciphertext") }
            success = true
        }
        assertFalse(success)
    }
}
