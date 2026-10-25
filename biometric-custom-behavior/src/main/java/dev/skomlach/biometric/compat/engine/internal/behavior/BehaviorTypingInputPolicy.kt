package dev.skomlach.biometric.compat.engine.internal.behavior

import android.os.Build
import android.content.Context
import android.text.InputType
import android.text.method.TextKeyListener
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.AutoCompleteTextView
import android.widget.ArrayAdapter
import android.widget.Filterable
import android.widget.TextView

/** Temporary input configuration; never changes caller text or its input class/variation. */
internal class BehaviorTypingInputPolicy(private val view: TextView) : AutoCloseable {
    private val originalType = view.inputType
    private val originalIme = view.imeOptions
    private val originalListener = view.keyListener
    private val originalFocusable = view.isFocusable
    private val originalTouchFocusable = view.isFocusableInTouchMode
    private val originalClickable = view.isClickable
    private val originalLongClickable = view.isLongClickable
    private val originalSaveEnabled = view.isSaveEnabled
    private val originalSaveFromParent = view.isSaveFromParentEnabled
    private val originalAutofill = if (Build.VERSION.SDK_INT >= 26) view.importantForAutofill else null
    private val completionView = view as? AutoCompleteTextView
    private val originalAdapter = completionView?.adapter
    private var closed = false

    init {
        // EditText freezes its text into Activity/Fragment saved state by default.
        view.isSaveEnabled = false
        view.isSaveFromParentEnabled = false
        val text = originalType and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_TEXT
        if (text && originalListener is TextKeyListener) {
            val capitalization = when {
                originalType and InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS != 0 -> TextKeyListener.Capitalize.CHARACTERS
                originalType and InputType.TYPE_TEXT_FLAG_CAP_WORDS != 0 -> TextKeyListener.Capitalize.WORDS
                originalType and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES != 0 -> TextKeyListener.Capitalize.SENTENCES
                else -> TextKeyListener.Capitalize.NONE
            }
            view.keyListener = TextKeyListener.getInstance(false, capitalization)
        }
        val captureType = if (text) (originalType and
            (InputType.TYPE_TEXT_FLAG_AUTO_CORRECT or InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE).inv()) or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS else originalType
        view.setRawInputType(captureType)
        restoreInteractionFlags()
        if (Build.VERSION.SDK_INT >= 26) {
            view.imeOptions = originalIme or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            view.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        }
        completionView?.dismissDropDown()
        completionView?.setAdapter<ArrayAdapter<String>>(null)
        refreshFocusedEditor()
    }

    override fun close() {
        if (closed) return
        closed = true
        view.keyListener = originalListener
        view.setRawInputType(originalType)
        view.imeOptions = originalIme
        view.isSaveEnabled = originalSaveEnabled
        view.isSaveFromParentEnabled = originalSaveFromParent
        restoreInteractionFlags()
        if (Build.VERSION.SDK_INT >= 26 && originalAutofill != null) view.importantForAutofill = originalAutofill
        // Do not overwrite a replacement explicitly installed by the caller during capture.
        if (completionView?.adapter == null) {
            val adapter = originalAdapter
            if (adapter is Filterable) completionView?.setAdapter(adapter)
        }
        refreshFocusedEditor()
    }

    private fun refreshFocusedEditor() {
        if (view.isAttachedToWindow && view.hasFocus()) {
            (view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.restartInput(view)
        }
    }

    private fun restoreInteractionFlags() {
        view.isFocusableInTouchMode = originalTouchFocusable
        view.isFocusable = originalFocusable
        view.isClickable = originalClickable
        view.isLongClickable = originalLongClickable
    }
}
