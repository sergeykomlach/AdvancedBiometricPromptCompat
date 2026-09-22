package dev.skomlach.biometric.compat.utils.activityView

import android.view.View
import dev.skomlach.biometric.compat.BiometricType
import dev.skomlach.biometric.compat.custom.SoftwarePromptStatus
import dev.skomlach.common.misc.ExecutorHelper

/** One outer authentication flow. Posted events capture this object, never the Builder's next flow. */
internal class ForegroundFeedbackSession(
    private val post: (() -> Unit) -> Unit = { task -> ExecutorHelper.post { task() } }
) {
    val state = ForegroundFeedbackState()
    @Volatile private var closed = false
    @Volatile private var stage = 0L
    @Volatile private var systemPromptActive = false
    var onChanged: (() -> Unit)? = null
    var dialogView: View? = null
        private set
    private var inlineFeedback: ((SoftwarePromptStatus?) -> Unit)? = null
    private val dialogLayoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> notifyChanged() }

    /** Set by the UI owner, never by an individual legacy/software provider. */
    fun setSystemPromptActive(active: Boolean) {
        if (!closed) systemPromptActive = active
    }

    fun show(source: BiometricType?, status: SoftwarePromptStatus) {
        val expectedStage = stage
        // Every legacy help/status is its source's current instruction while native UI owns
        // the prompt. Providers need no persistence opt-in and keep their original status.
        val snapshot = status.copy(
            primaryText = status.primaryText.toString(),
            secondaryText = status.secondaryText?.toString(),
            persistent = status.persistent || (systemPromptActive && !status.terminal)
        )
        post {
            if (!closed && stage == expectedStage && state.show(source, snapshot) != null) notifyChanged()
        }
    }

    fun finishSource(source: BiometricType?, status: SoftwarePromptStatus? = null) {
        val expectedStage = stage
        val snapshot = status?.copy(primaryText = status.primaryText.toString(), secondaryText = status.secondaryText?.toString())
        post {
            if (!closed && stage == expectedStage) { state.finishSource(source, snapshot); notifyChanged() }
        }
    }

    fun clearSource(source: BiometricType?) {
        val expectedStage = stage
        post {
            if (!closed && stage == expectedStage) { state.clearSource(source); notifyChanged() }
        }
    }

    fun clear() {
        stage++
        systemPromptActive = false
        if (!closed) { state.clear(); notifyChanged() }
    }

    fun attachDialog(view: View, inline: (SoftwarePromptStatus?) -> Unit) {
        if (closed) return
        dialogView?.removeOnLayoutChangeListener(dialogLayoutListener)
        dialogView = view
        view.addOnLayoutChangeListener(dialogLayoutListener)
        inlineFeedback = inline
        notifyChanged()
    }

    fun detachDialog(view: View?) {
        if (dialogView !== view) return
        dialogView?.removeOnLayoutChangeListener(dialogLayoutListener)
        dialogView = null
        inlineFeedback = null
        notifyChanged()
    }

    fun showInline(status: SoftwarePromptStatus?) { if (!closed) inlineFeedback?.invoke(status) }
    fun notifyChanged() {
        if (closed) return
        val listener = onChanged
        if (listener != null) listener() else inlineFeedback?.invoke(state.current?.status)
    }

    fun close() {
        closed = true
        stage++
        systemPromptActive = false
        state.close()
        onChanged?.invoke()
        onChanged = null
        dialogView?.removeOnLayoutChangeListener(dialogLayoutListener)
        dialogView = null
        inlineFeedback = null
    }
}
