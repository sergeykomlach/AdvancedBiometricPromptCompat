package dev.skomlach.biometric.compat.utils.activityView

import dev.skomlach.biometric.compat.BiometricType
import dev.skomlach.biometric.compat.custom.SoftwarePromptStatus

/** Main-thread, request-owned state. Never queue obsolete transient hints from other sensors. */
internal class ForegroundFeedbackState {
    data class Message(val id: Long, val source: BiometricType?, val status: SoftwarePromptStatus)
    private var sequence = 0L
    private var closed = false
    private val instructions = linkedMapOf<BiometricType?, Message>()
    private val finishedSources = mutableSetOf<BiometricType?>()
    var current: Message? = null
        private set

    fun show(source: BiometricType?, status: SoftwarePromptStatus): Message? {
        if (closed || source in finishedSources || status.asLegacyHelpMessage().isBlank()) return null
        // Snapshot mutable Spannables; biometric phrases must not change underneath a queued event.
        val snapshot = status.copy(primaryText = status.primaryText.toString(), secondaryText = status.secondaryText?.toString())
        if (current?.source == source && current?.status == snapshot) return null
        val message = Message(++sequence, source, snapshot)
        instructions.remove(source)
        if (snapshot.persistent && !snapshot.terminal) instructions[source] = message
        if (current?.status?.terminal == true && current?.source != source && !snapshot.terminal) return null
        current = message
        return message
    }

    fun expire(id: Long) {
        if (closed || current?.id != id || current?.status?.persistent == true && current?.status?.terminal != true) return
        current = instructions.values.lastOrNull()
    }

    fun clearSource(source: BiometricType?) {
        instructions.remove(source)
        if (current?.source == source) current = instructions.values.lastOrNull()
    }

    /** Completion/cancellation without text must also remove the old instruction. */
    fun finishSource(source: BiometricType?, status: SoftwarePromptStatus? = null) {
        if (closed || source in finishedSources) return
        clearSource(source)
        status?.let { show(source, it.copy(terminal = true)) }
        finishedSources.add(source)
    }

    fun clear() { instructions.clear(); finishedSources.clear(); current = null }
    fun close() { closed = true; clear() }
}
