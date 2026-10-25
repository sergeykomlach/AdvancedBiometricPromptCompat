package dev.skomlach.biometric.compat.engine.core

import androidx.core.os.CancellationSignal
import dev.skomlach.biometric.compat.AuthenticationFailureReason
import dev.skomlach.biometric.compat.BiometricCryptoObject
import dev.skomlach.biometric.compat.engine.core.interfaces.AuthenticationListener
import dev.skomlach.biometric.compat.engine.core.interfaces.BiometricModule
import dev.skomlach.biometric.compat.engine.core.interfaces.RestartPredicate
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.*
import org.junit.Test

class CoreAuthenticationRequestTest {
    @Test fun supersededErrorsAndCancellationCannotReachTheConsumer() = withCore {
        val module = RecordingModule(4001)
        val oldEvents = mutableListOf<String>()
        val currentEvents = mutableListOf<String>()
        Core.authenticate(null, module, recorder(oldEvents), null)
        val old = module.listener!!
        Core.authenticate(null, module, recorder(currentEvents), null)
        old.onFailure(module.tag(), AuthenticationFailureReason.LOCKED_OUT, "old")
        old.onCanceled(module.tag(), AuthenticationFailureReason.CANCELED, "old")
        assertTrue(oldEvents.isEmpty())
        assertFalse(module.signal!!.isCanceled)
        module.listener!!.onFailure(module.tag(), AuthenticationFailureReason.HARDWARE_UNAVAILABLE, "current")
        assertEquals(listOf("failure:HARDWARE_UNAVAILABLE"), currentEvents)
    }

    @Test fun canceledCurrentRequestStillDeliversItsStorageError() = withCore {
        val module = RecordingModule(4002)
        val events = mutableListOf<String>()
        Core.authenticate(null, module, recorder(events), null)
        Core.cancelAuthentication(module)
        module.listener!!.onFailure(module.tag(), AuthenticationFailureReason.HARDWARE_UNAVAILABLE, "storage")
        module.listener!!.onCanceled(module.tag(), AuthenticationFailureReason.CANCELED, null)
        assertEquals(listOf("failure:HARDWARE_UNAVAILABLE", "canceled"), events)
    }

    @Test fun originCleanupAfterReentrantConsumerCannotCancelReplacement() = withCore {
        val module = RecordingModule(4007)
        val events = mutableListOf<String>()
        Core.authenticate(null, module, object : AuthenticationListener by recorder(events) {
            override fun onCanceled(moduleTag: Int, reason: AuthenticationFailureReason?, description: CharSequence?) {
                Core.authenticate(null, module, recorder(events), null)
            }
        }, null)
        val old = module.listener as ModuleFeedbackListener
        old.onCanceled(module.tag(), AuthenticationFailureReason.CANCELED, null)
        val replacement = module.signal!!
        old.cancelAuthentication()
        old.onFailure(module.tag(), AuthenticationFailureReason.LOCKED_OUT, "late")
        assertTrue(events.isEmpty())
        assertFalse(replacement.isCanceled)
        Core.cancelAuthentication(module)
        assertTrue(replacement.isCanceled)
    }

    @Test fun startupFailureAfterReentrantReplacementCannotCancelOrNotifyTheNewRequest() = withCore {
        val module = RecordingModule(4008)
        val oldEvents = mutableListOf<String>()
        module.onStart = {
            module.onStart = null
            Core.authenticate(null, module, null, null)
            throw IllegalStateException("old startup failed")
        }
        Core.authenticate(null, module, recorder(oldEvents), null)
        assertEquals(2, module.starts)
        assertTrue(oldEvents.isEmpty())
        assertFalse(module.signal!!.isCanceled)
        Core.cancelAuthentication(module)
        assertTrue(module.signal!!.isCanceled)
    }

    @Test fun reentrantStartDuringCancelRemainsTracked() = withCore {
        val module = RecordingModule(4003)
        Core.authenticate(null, module, null, null)
        val oldSignal = module.signal!!
        oldSignal.setOnCancelListener { Core.authenticate(null, module, null, null) }
        Core.cancelAuthentication(module)
        val replacement = module.signal!!
        assertNotSame(oldSignal, replacement)
        assertFalse(replacement.isCanceled)
        Core.cancelAuthentication(module)
        assertTrue(replacement.isCanceled)
    }

    @Test fun newerReentrantStartSupersedesTheOuterReplacement() = withCore {
        val module = RecordingModule(4004)
        Core.authenticate(null, module, null, null)
        module.signal!!.setOnCancelListener { Core.authenticate(null, module, null, null) }
        Core.authenticate(null, module, null, null)
        // Initial start and newest nested start only; the older reserved start stays canceled.
        assertEquals(2, module.starts)
        assertFalse(module.signal!!.isCanceled)
        val latest = module.signal!!
        Core.cancelAuthentication(module)
        assertTrue(latest.isCanceled)
    }

    @Test fun cancelAllStopsItsSnapshotAndPreservesReentrantReplacement() = withCore {
        val modules = listOf(RecordingModule(4005), RecordingModule(4006))
        modules.forEach { Core.registerModule(it); Core.authenticate(null, it, null, null) }
        val oldSignals = modules.associateWith { it.signal!! }
        val order = mutableListOf<String>()
        val replaced = AtomicBoolean(false)
        var replacementModule: RecordingModule? = null
        modules.forEach { module ->
            oldSignals.getValue(module).setOnCancelListener {
                order += "old-stopped:${module.tag()}"
                if (replaced.compareAndSet(false, true)) {
                    val other = modules.first { it !== module }
                    replacementModule = other
                    other.onStart = { order += "new-started:${other.tag()}" }
                    Core.authenticate(null, other, null, null)
                }
            }
        }
        Core.cancelAuthentication()
        val replacement = replacementModule!!
        val first = modules.first { it !== replacement }
        assertEquals(listOf("old-stopped:${first.tag()}", "old-stopped:${replacement.tag()}",
            "new-started:${replacement.tag()}"), order)
        assertTrue(oldSignals.values.all { it.isCanceled })
        assertNotSame(oldSignals.getValue(replacement), replacement.signal)
        assertFalse(replacement.signal!!.isCanceled)
        Core.cancelAuthentication()
        assertTrue(replacement.signal!!.isCanceled)
    }

    private fun recorder(events: MutableList<String>) = object : AuthenticationListener {
        override fun onHelp(msg: CharSequence?) = Unit
        override fun onSuccess(moduleTag: Int, biometricCryptoObject: BiometricCryptoObject?) { events += "success" }
        override fun onFailure(moduleTag: Int, reason: AuthenticationFailureReason?, description: CharSequence?) { events += "failure:$reason" }
        override fun onCanceled(moduleTag: Int, reason: AuthenticationFailureReason?, description: CharSequence?) { events += "canceled" }
    }

    private class RecordingModule(private val id: Int) : BiometricModule {
        var signal: CancellationSignal? = null
        var listener: AuthenticationListener? = null
        var starts = 0
        var onStart: (() -> Unit)? = null
        override val isManagerAccessible = true
        override val isHardwarePresent = true
        override val isLockOut = false
        override val isUserAuthCanByUsedWithCrypto = false
        override val hasEnrolled = true
        @Deprecated("Unused in tests") override val isBiometricEnrollChanged = false
        override fun tag() = id
        override fun authenticate(biometricCryptoObject: BiometricCryptoObject?, cancellationSignal: CancellationSignal?, listener: AuthenticationListener?, restartPredicate: RestartPredicate?) {
            starts++
            signal = cancellationSignal
            this.listener = listener
            onStart?.invoke()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun coreModules(): MutableMap<Int, BiometricModule> = Core::class.java
        .getDeclaredField("reprintModuleHashMap").apply { isAccessible = true }.get(Core) as MutableMap<Int, BiometricModule>

    private fun withCore(block: () -> Unit) {
        val modules = coreModules()
        val previous = synchronized(modules) { modules.toMap() }
        val logging = BiometricLoggerImpl.DEBUG
        try {
            BiometricLoggerImpl.DEBUG = false
            Core.cleanModules()
            block()
        } finally {
            Core.cleanModules()
            synchronized(modules) { modules.putAll(previous) }
            BiometricLoggerImpl.DEBUG = logging
        }
    }
}
