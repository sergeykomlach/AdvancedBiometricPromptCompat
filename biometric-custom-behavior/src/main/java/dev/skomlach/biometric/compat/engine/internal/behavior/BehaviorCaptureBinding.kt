package dev.skomlach.biometric.compat.engine.internal.behavior

import android.os.Bundle
import dev.skomlach.biometric.compat.BehaviorAuthMode
import dev.skomlach.biometric.compat.BiometricPromptCompat

/**
 * Capture in caller-owned views with caller-owned submit/restart actions and no library window.
 * Keep this binding open until authentication finishes, then close to restore input settings.
 */
class BehaviorCaptureBinding(
    private val builder: BiometricPromptCompat.Builder,
    private val mode: BehaviorMode,
    private val enroll: Boolean = false,
    private val onReady: (Bundle) -> Unit,
    private val onError: (CharSequence) -> Unit = {}
) : AutoCloseable {
    private var originalAuthMode: BehaviorAuthMode? = null
    private var controller: BehaviorCaptureController? = null
    private var closed = false

    fun start() {
        check(!closed) { "Capture binding is closed" }
        if (controller != null) return
        val root = builder.getBehaviorTypingView() ?: builder.getBehaviorSignatureContainer()
            ?: throw IllegalArgumentException("Consumer input views are required")
        val capture = BehaviorCaptureController(root, builder, enroll,
            onReady = { builder.getExtras()?.let(onReady) }, onInputError = onError)
        controller = capture
        try {
            capture.installConsumer(mode)
            originalAuthMode = builder.getBehaviorAuthMode()
            builder.setBehaviorAuthMode(BehaviorAuthMode.PASSIVE)
        } catch (error: Exception) {
            close()
            throw error
        }
    }

    fun submit(): Boolean = controller?.submitConsumer() == true
    fun restart() { controller?.restartConsumer() }

    override fun close() {
        if (closed) return
        closed = true
        controller?.dispose()
        controller = null
        originalAuthMode?.let {
            if (builder.getBehaviorAuthMode() == BehaviorAuthMode.PASSIVE) builder.setBehaviorAuthMode(it)
        }
    }
}
