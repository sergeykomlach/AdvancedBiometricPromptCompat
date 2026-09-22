package dev.skomlach.biometric.compat.engine.internal.sherpaonnx

import android.content.SharedPreferences
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class VoiceTemplateStoreIdentityTest {
    @Test fun publicEnrollmentStartedBeforeRemovalCannotRestoreProfile() {
        val preferences = memoryPreferences()
        val maintenance = VoiceTemplateStore({ preferences }, null)
        val engine = Engine().apply { onAvailability = { maintenance.remove(null) } }
        val activeStore = VoiceTemplateStore({ preferences }, engine)
        assertThrows(java.util.concurrent.CancellationException::class.java) {
            activeStore.save("user", null, embedding(1f, 0f))
        }
        assertTrue(activeStore.templateNames().isEmpty())
    }


    @Test fun otherStoreRemovalRevokesQueuedSuccessAndLateEnrollmentCommit() {
        val preferences = memoryPreferences()
        val engine = Engine()
        val activeStore = VoiceTemplateStore({ preferences }, engine)
        val maintenanceStore = VoiceTemplateStore({ preferences }, null)
        activeStore.save("user", null, embedding(1f, 0f))
        val session = activeStore.newWorkSession()
        val queue = java.util.ArrayDeque<() -> Unit>()
        val events = mutableListOf<String>()
        val delivery = dev.skomlach.biometric.compat.custom.SoftwareBiometricWorkerCallback(
            session, object : dev.skomlach.biometric.compat.custom.AbstractSoftwareBiometricManager.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: dev.skomlach.biometric.compat.custom.AbstractSoftwareBiometricManager.AuthenticationResult?) {
                    events += "success"
                }
                override fun onAuthenticationCancelled() { events += "cancel" }
            }
        ) { queue.addLast(it) }
        delivery.onAuthenticationSucceeded(null)

        maintenanceStore.remove("user")

        val commit = session.runIfActive {
            activeStore.saveTrained("user", listOf(VoiceTemplate("user", null, embedding(1f, 0f))),
                "voice-consensus-v2:model-a")
        }
        while (queue.isNotEmpty()) queue.removeFirst().invoke()
        assertNull("Removed enrollment must not be resurrected by another instance", commit)
        assertEquals(listOf("cancel"), events)
        assertTrue(activeStore.templateNames().isEmpty())
        assertFalse(session.isActive)
    }

    @Test fun removalBeforeCallbackBindingStillDeliversCancellation() {
        val preferences = memoryPreferences()
        val activeStore = VoiceTemplateStore({ preferences }, Engine())
        val session = activeStore.newWorkSession()
        VoiceTemplateStore({ preferences }, null).remove(null)
        val events = mutableListOf<String>()
        val delivery = dev.skomlach.biometric.compat.custom.SoftwareBiometricWorkerCallback(
            session, object : dev.skomlach.biometric.compat.custom.AbstractSoftwareBiometricManager.AuthenticationCallback() {
                override fun onAuthenticationCancelled() { events += "cancel" }
                override fun onAuthenticationSucceeded(result: dev.skomlach.biometric.compat.custom.AbstractSoftwareBiometricManager.AuthenticationResult?) {
                    events += "success"
                }
            }
        ) { it() }
        delivery.onAuthenticationSucceeded(null)
        assertEquals(listOf("cancel"), events)
        val replacement = activeStore.newWorkSession()
        assertTrue(replacement.isActive)
        replacement.cancel()
    }
    @Test fun concurrentReenrollmentCannotLabelOldPayloadWithNewIdentity() {
        val payloadRead = CountDownLatch(1)
        val releaseRead = CountDownLatch(1)
        val writing = CountDownLatch(1)
        var intercept = false
        val preferences = memoryPreferences { key ->
            if (intercept && Thread.currentThread().name == "template-reader" && key == "template_user") {
                payloadRead.countDown()
                check(releaseRead.await(5, TimeUnit.SECONDS))
            }
        }
        val oldEngine = Engine()
        val newEngine = Engine().apply { identity = "model-b" }
        val readerStore = VoiceTemplateStore({ preferences }, oldEngine)
        val writerStore = VoiceTemplateStore({ preferences }, newEngine)
        readerStore.save("user", null, embedding(1f, 0f))
        intercept = true
        var loaded: List<VoiceTemplate>? = null
        val errors = java.util.concurrent.ConcurrentLinkedQueue<Throwable>()
        val reader = Thread({
            try { loaded = readerStore.loadTemplates("voice-consensus-v2:model-b") }
            catch (error: Throwable) { errors.add(error) }
        }, "template-reader")
        val writer = Thread({
            writing.countDown()
            try { writerStore.save("user", null, embedding(0f, 1f)) }
            catch (error: Throwable) { errors.add(error) }
        }, "template-writer")
        reader.start()
        try {
            assertTrue(payloadRead.await(5, TimeUnit.SECONDS))
            writer.start()
            assertTrue(writing.await(5, TimeUnit.SECONDS))
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (writer.isAlive && writer.state != Thread.State.BLOCKED && System.nanoTime() < deadline) {
                Thread.yield()
            }
            assertTrue(!writer.isAlive || writer.state == Thread.State.BLOCKED)
        } finally {
            releaseRead.countDown()
            reader.join(5_000)
            if (writer.state != Thread.State.NEW) writer.join(5_000)
        }
        assertFalse(reader.isAlive)
        assertFalse(writer.isAlive)
        assertTrue(errors.toString(), errors.isEmpty())
        assertEquals(emptyList<VoiceTemplate>(), loaded)
        assertArrayEquals(embedding(0f, 1f),
            readerStore.loadTemplates("voice-consensus-v2:model-b").single().embedding, 0.0001f)
    }

    @Test fun boundPublicSaveProducesAnAuthenticatableProfileAndPreservesItOnUnboundWrite() {
        val preferences = memoryPreferences()
        val engine = Engine()
        val bound = VoiceTemplateStore({ preferences }, engine)
        assertEquals("user", bound.save("user", null, embedding(1f, 0f)))
        assertEquals(VoiceEnrollmentStatus.READY, bound.enrollmentStatus("voice-consensus-v2:model-a"))
        assertEquals(1, bound.loadTemplates("voice-consensus-v2:model-a").size)
        val before = preferences.all
        val unbound = VoiceTemplateStore({ preferences }, null)
        assertThrows(IllegalStateException::class.java) {
            unbound.saveAll("user", null, listOf(embedding(0f, 1f)))
        }
        assertEquals(before, preferences.all)
    }

    @Test fun absentOrUnpreparedIdentityFailsBeforeAnyStorageWrite() {
        val preferences = memoryPreferences()
        val engine = Engine()
        val store = VoiceTemplateStore({ preferences }, engine)
        engine.identity = null
        assertThrows(IllegalStateException::class.java) {
            store.save(null, null, embedding(1f, 0f))
        }
        engine.identity = "model-a"
        engine.available = false
        assertThrows(IllegalStateException::class.java) {
            store.save(null, null, embedding(1f, 0f))
        }
        assertTrue(preferences.all.isEmpty())
    }

    @Test fun modelChangeRequiresReenrollmentWithoutDeletingOldProfiles() {
        val preferences = memoryPreferences()
        val engine = Engine()
        val store = VoiceTemplateStore({ preferences }, engine)
        store.save("old", null, embedding(1f, 0f))
        engine.identity = "model-b"
        assertEquals(VoiceEnrollmentStatus.REENROLLMENT_REQUIRED,
            store.enrollmentStatus("voice-consensus-v2:model-b"))
        store.save("new", null, embedding(0f, 1f))
        assertEquals(listOf("new", "old"), store.templateNames().toList())
        assertEquals(listOf("new"), store.templateNames("voice-consensus-v2:model-b").toList())
    }

    private fun embedding(first: Float, second: Float) =
        floatArrayOf(first, second, 0f, 0f, 0f, 0f, 0f, 0f)

    private class Engine : VoiceEngine, VoiceTemplateIdentityProvider {
        var onAvailability: () -> Unit = {}
        var identity: String? = "model-a"
        var available = true
        override val templateIdentity get() = identity
        override fun isAvailable(): Boolean { onAvailability(); return available }
        override fun extractEmbedding(sample: VoiceSample): VoiceEmbeddingResult? = null
    }

    // Only the Android persistence boundary is fake: production training, serialization,
    // identity filtering and public store entry points are exercised unchanged.
    private fun memoryPreferences(afterStringRead: (String) -> Unit = {}): SharedPreferences {
        val values = linkedMapOf<String, Any>()
        return Proxy.newProxyInstance(SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java)) { _, method, args ->
            when (method.name) {
                "getAll" -> values.toMap()
                "getString" -> {
                    val value = values[args!![0]] ?: args[1]
                    afterStringRead(args[0] as String)
                    value
                }
                "contains" -> values.containsKey(args!![0])
                "edit" -> {
                    val writes = linkedMapOf<String, Any?>()
                    Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader,
                        arrayOf(SharedPreferences.Editor::class.java)) { editor, editMethod, editArgs ->
                        when (editMethod.name) {
                            "putString" -> { writes[editArgs!![0] as String] = editArgs[1]; editor }
                            "remove" -> { writes[editArgs!![0] as String] = null; editor }
                            "commit" -> {
                                writes.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }
                                true
                            }
                            else -> error("Unexpected editor call: ${editMethod.name}")
                        }
                    }
                }
                else -> error("Unexpected preferences call: ${method.name}")
            }
        } as SharedPreferences
    }
}
