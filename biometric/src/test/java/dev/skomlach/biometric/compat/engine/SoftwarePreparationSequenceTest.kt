package dev.skomlach.biometric.compat.engine

import dev.skomlach.biometric.compat.custom.AbstractSoftwareBiometricManager
import dev.skomlach.biometric.compat.custom.AbstractSoftwareBiometricManager.PreparationCallback
import dev.skomlach.biometric.compat.isAuthFlowActive
import org.junit.Assert.assertEquals
import org.junit.Test

class SoftwarePreparationSequenceTest {
    @Test
    fun `inactive flow starts no provider and delivers no completion`() {
        val run = Harness()
        run.active = false
        run.start()
        assertEquals(emptyList<String>(), run.started)
        assertEquals(emptyList<String>(), run.events)
    }

    @Test
    fun `late success after cancel cannot start the next provider`() {
        val run = Harness()
        run.start()
        run.active = false
        run.callbacks.getValue("voice").onPrepared()
        assertEquals(listOf("voice"), run.started)
        assertEquals(emptyList<String>(), run.events)
    }

    @Test
    fun `late error after cancel cannot select fallback or report failure`() {
        for (error in listOf(skippable, terminal)) {
            val run = Harness()
            run.start()
            run.active = false
            run.callbacks.getValue("voice").onPreparationError(error, "failure")
            assertEquals(listOf("voice"), run.started)
            assertEquals(emptyList<String>(), run.events)
        }
    }

    @Test
    fun `late cancellation after cancel is ignored`() {
        val run = Harness()
        run.start()
        run.active = false
        run.callbacks.getValue("voice").onPreparationCanceled()
        assertEquals(emptyList<String>(), run.events)
    }

    @Test
    fun `replacement flow remains usable after stale completion`() {
        var generation = 7L
        val old = Harness {
            isAuthFlowActive(7L, generation, inProgress = true, canceled = false)
        }
        old.start()
        generation = 8L
        val replacement = Harness {
            isAuthFlowActive(8L, generation, inProgress = true, canceled = false)
        }
        replacement.start()
        old.callbacks.getValue("voice").onPrepared()
        replacement.callbacks.getValue("voice").onPrepared()
        replacement.callbacks.getValue("fingerprint").onPrepared()
        assertEquals(listOf("voice"), old.started)
        assertEquals(emptyList<String>(), old.events)
        assertEquals(listOf("voice", "fingerprint"), replacement.started)
        assertEquals(listOf("ready"), replacement.events)
    }

    @Test
    fun `cancel inside skip callback prevents fallback lookup`() {
        val run = Harness()
        run.onSkip = { run.active = false }
        run.start()
        run.callbacks.getValue("voice").onPreparationError(skippable, null)
        assertEquals(listOf("voice"), run.started)
        assertEquals(listOf("skip:voice"), run.events)
    }

    @Test
    fun `fallback retains position and completes remaining providers`() {
        val run = Harness()
        run.start()
        run.callbacks.getValue("voice").onPreparationError(skippable, null)
        run.callbacks.getValue("fallback").onPrepared()
        run.callbacks.getValue("fingerprint").onPrepared()
        assertEquals(listOf("voice", "fallback", "fingerprint"), run.started)
        assertEquals(listOf("skip:voice", "fallback:voice", "ready"), run.events)
    }

    @Test
    fun `duplicate and contradictory callbacks cannot advance an attempt twice`() {
        val run = Harness()
        run.start()
        val voice = run.callbacks.getValue("voice")
        voice.onPrepared()
        voice.onPrepared()
        voice.onPreparationError(skippable, null)
        voice.onPreparationCanceled()
        val fingerprint = run.callbacks.getValue("fingerprint")
        fingerprint.onPrepared()
        fingerprint.onPrepared()
        assertEquals(listOf("voice", "fingerprint"), run.started)
        assertEquals(listOf("ready"), run.events)
    }

    @Test
    fun `terminal error and cancellation never advance to next provider`() {
        for (cancel in listOf(false, true)) {
            val run = Harness()
            run.start()
            val voice = run.callbacks.getValue("voice")
            if (cancel) voice.onPreparationCanceled()
            else voice.onPreparationError(terminal, "failure")
            voice.onPrepared()
            assertEquals(listOf("voice"), run.started)
            assertEquals(listOf(if (cancel) "canceled" else "error:$terminal:failure"), run.events)
        }
    }

    @Test
    fun `empty sequence completes an active flow`() {
        val run = Harness()
        run.start(emptyList())
        assertEquals(listOf("ready"), run.events)
    }

    private class Harness(private val ownsFlow: () -> Boolean = { true }) {
        var active = true
        var onSkip: () -> Unit = {}
        val started = mutableListOf<String>()
        val events = mutableListOf<String>()
        val callbacks = mutableMapOf<String, PreparationCallback>()

        fun start(modules: List<String> = listOf("voice", "fingerprint")) {
            prepareSoftwareSequence(
                modules = modules,
                prepare = { module, callback ->
                    started += module
                    callbacks[module] = callback
                },
                onModuleSkipped = { module -> events += "skip:$module"; onSkip() },
                callback = object : PreparationCallback() {
                    override fun onPrepared() { events += "ready" }
                    override fun onPreparationError(errMsgId: Int, errString: CharSequence?) {
                        events += "error:$errMsgId:$errString"
                    }
                    override fun onPreparationCanceled() { events += "canceled" }
                },
                isActive = { active && ownsFlow() },
                fallbackFor = { module -> events += "fallback:$module"; "fallback" }
            )
        }
    }

    companion object {
        private const val skippable = AbstractSoftwareBiometricManager.CUSTOM_BIOMETRIC_ERROR_HW_UNAVAILABLE
        private const val terminal = AbstractSoftwareBiometricManager.CUSTOM_BIOMETRIC_ERROR_UNABLE_TO_PROCESS
    }
}
