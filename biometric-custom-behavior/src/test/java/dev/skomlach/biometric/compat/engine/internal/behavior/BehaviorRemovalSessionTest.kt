package dev.skomlach.biometric.compat.engine.internal.behavior

import android.content.SharedPreferences
import dev.skomlach.biometric.compat.custom.AbstractSoftwareBiometricManager
import dev.skomlach.biometric.compat.custom.SoftwareBiometricWorkerCallback
import dev.skomlach.common.storage.ProtectedStorageUnavailableException
import java.lang.reflect.Proxy
import java.util.ArrayDeque
import org.junit.Assert.*
import org.junit.Test

class BehaviorRemovalSessionTest {
    private class Storage {
        val values = mutableMapOf<String, Any>("template_one" to "synthetic", "other" to "kept")
        var commitSucceeds = true
        val prefs = Proxy.newProxyInstance(SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java)) { _, method, _ ->
            when (method.name) {
                "getAll" -> values.toMap()
                "edit" -> editor()
                else -> error("Unused preference operation: ${method.name}")
            }
        } as SharedPreferences

        private fun editor(): SharedPreferences.Editor {
            val removals = mutableListOf<String>()
            lateinit var editor: SharedPreferences.Editor
            editor = Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader,
                arrayOf(SharedPreferences.Editor::class.java)) { _, method, args ->
                when (method.name) {
                    "remove" -> { removals += args!![0] as String; editor }
                    "commit" -> commitSucceeds.also { if (it) removals.forEach(values::remove) }
                    else -> error("Unused editor operation: ${method.name}")
                }
            } as SharedPreferences.Editor
            return editor
        }
    }

    private class Recorder : AbstractSoftwareBiometricManager.AuthenticationCallback() {
        var successes = 0
        var cancellations = 0
        var errors = 0
        override fun onAuthenticationSucceeded(result: AbstractSoftwareBiometricManager.AuthenticationResult?) { successes++ }
        override fun onAuthenticationCancelled() { cancellations++ }
        override fun onAuthenticationError(errMsgId: Int, errString: CharSequence?) { errors++ }
    }

    private class Attempt(store: BehaviorTemplateStore) {
        val queue = ArrayDeque<() -> Unit>()
        val session = store.newWorkSession()
        val result = Recorder()
        val callback = SoftwareBiometricWorkerCallback(session, result, enqueue = { queue.add(it) })
        fun drain() { while (queue.isNotEmpty()) queue.removeFirst().invoke() }
    }

    @Test fun publicRemovalByAnotherStoreRevokesAlreadyQueuedSuccess() {
        val storage = Storage()
        val attempt = Attempt(BehaviorTemplateStore { storage.prefs })
        attempt.callback.onAuthenticationSucceeded(null)
        BehaviorTemplateStore { storage.prefs }.remove(null)
        attempt.drain()
        assertEquals(0, attempt.result.successes)
        assertEquals(1, attempt.result.cancellations)
        assertEquals(0, attempt.result.errors)
        assertFalse(attempt.session.isActive)
        var wrote = false
        attempt.session.runIfActive { wrote = true }
        assertFalse(wrote)
        assertFalse(storage.values.containsKey("template_one"))
        assertEquals("kept", storage.values["other"])
    }

    @Test fun failedProtectedDeletionStillRevokesQueuedSuccess() {
        val storage = Storage().apply { commitSucceeds = false }
        val store = BehaviorTemplateStore { storage.prefs }
        val attempt = Attempt(store)
        attempt.callback.onAuthenticationSucceeded(null)
        assertThrows(ProtectedStorageUnavailableException::class.java) { store.remove("one") }
        attempt.drain()
        assertEquals(0, attempt.result.successes)
        assertEquals(1, attempt.result.cancellations)
        assertEquals(0, attempt.result.errors)
        assertTrue(storage.values.containsKey("template_one"))
    }

    @Test fun removalRevokesQueuedErrorAndIsIdempotent() {
        val storage = Storage()
        val store = BehaviorTemplateStore { storage.prefs }
        val attempt = Attempt(store)
        attempt.callback.onAuthenticationError(1, "synthetic")
        store.remove("one")
        store.remove("one")
        attempt.callback.onAuthenticationCancelled()
        attempt.drain()
        assertEquals(0, attempt.result.successes)
        assertEquals(1, attempt.result.cancellations)
        assertEquals(0, attempt.result.errors)
    }

    @Test fun deliveredSuccessCompletesOnceBeforeLaterRemoval() {
        val storage = Storage()
        val store = BehaviorTemplateStore { storage.prefs }
        val attempt = Attempt(store)
        attempt.callback.onAuthenticationSucceeded(null)
        attempt.drain()
        store.remove(null)
        attempt.callback.onAuthenticationSucceeded(null)
        attempt.drain()
        assertEquals(1, attempt.result.successes)
        assertEquals(0, attempt.result.cancellations)
    }
}
