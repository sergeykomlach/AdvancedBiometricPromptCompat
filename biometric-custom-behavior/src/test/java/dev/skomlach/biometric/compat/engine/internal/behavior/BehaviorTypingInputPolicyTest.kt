package dev.skomlach.biometric.compat.engine.internal.behavior

import android.app.Activity
import android.graphics.Typeface
import android.text.InputType
import android.text.method.DigitsKeyListener
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implements
import org.robolectric.annotation.Implementation
import org.robolectric.shadows.ShadowInputMethodManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE, shadows = [RecordingInputMethodManager::class])
class BehaviorTypingInputPolicyTest {
    @Test fun captureTextIsExcludedFromSavedViewStateUntilPolicyCloses() {
        val input = EditText(context).apply { id = 1234; setText("capture-secret") }
        val original = input.isSaveEnabled
        BehaviorTypingInputPolicy(input).use {
            val state = android.util.SparseArray<android.os.Parcelable>()
            input.saveHierarchyState(state)
            assertEquals("Raw input must not enter instance state", 0, state.size())
        }
        assertEquals(original, input.isSaveEnabled)
        val restored = android.util.SparseArray<android.os.Parcelable>()
        input.saveHierarchyState(restored)
        assertTrue(restored.size() > 0)
    }
    private val context = Robolectric.buildActivity(Activity::class.java).get()

    @Test fun passwordAndMultilineKeepTransformationFontTextAndSelection() {
        val input = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
            typeface = Typeface.MONOSPACE
            setText("private input"); setSelection(3)
        }
        val type = input.inputType
        val listener = input.keyListener
        val transformation = input.transformationMethod
        val font = input.typeface
        BehaviorTypingInputPolicy(input).use {
            assertEquals(type and InputType.TYPE_MASK_VARIATION, input.inputType and InputType.TYPE_MASK_VARIATION)
            assertSame(transformation, input.transformationMethod)
            assertSame(font, input.typeface)
            assertEquals(3, input.selectionStart)
            assertEquals("private input", input.text.toString())
        }
        assertEquals(type, input.inputType)
        assertSame(listener, input.keyListener)
        assertSame(transformation, input.transformationMethod)
    }

    @Test fun numericListenerAndInputClassRemainUnchanged() {
        val input = EditText(context).apply {
            keyListener = DigitsKeyListener.getInstance(true, true)
            setText("-42.5")
        }
        val listener = input.keyListener
        val type = input.inputType
        BehaviorTypingInputPolicy(input).use {
            assertEquals(type, input.inputType)
            assertSame(listener, input.keyListener)
        }
        assertEquals(type, input.inputType)
        assertSame(listener, input.keyListener)
    }

    @Test fun autofillAndImeLearningAreDisabledWhileKeepingImeActionThenRestored() {
        val input = EditText(context).apply {
            imeOptions = EditorInfo.IME_ACTION_NEXT
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES
        }
        val policy = BehaviorTypingInputPolicy(input)
        assertEquals(EditorInfo.IME_ACTION_NEXT, input.imeOptions and EditorInfo.IME_MASK_ACTION)
        assertTrue(input.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0)
        assertEquals(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS, input.importantForAutofill)
        policy.close(); policy.close()
        assertEquals(EditorInfo.IME_ACTION_NEXT, input.imeOptions)
        assertEquals(View.IMPORTANT_FOR_AUTOFILL_YES, input.importantForAutofill)
    }

    @Test fun adapterRestorationPreservesCallerReplacementAndDoesNotChangeItsData() {
        val original = ArrayAdapter(context, android.R.layout.simple_list_item_1, listOf("original"))
        val replacement = ArrayAdapter(context, android.R.layout.simple_list_item_1, listOf("replacement"))
        val input = AutoCompleteTextView(context).apply { setAdapter(original); setText("value", false) }
        assertEquals("original", original.getItem(0))
        val policy = BehaviorTypingInputPolicy(input)
        assertNull(input.adapter)
        input.setAdapter(replacement)
        policy.close()
        assertSame(replacement, input.adapter)
        assertEquals("original", original.getItem(0))
        assertEquals("value", input.text.toString())
    }

    @Test fun alreadyFocusedEditorRefreshesImeConfigurationOnStartAndRestore() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val input = EditText(activity.get())
            activity.get().setContentView(input)
            input.requestFocus()
            assertTrue(input.isAttachedToWindow && input.hasFocus())
            RecordingInputMethodManager.restarts.clear()
            RecordingInputMethodManager.configurations.clear()
            val policy = BehaviorTypingInputPolicy(input)
            assertTrue(RecordingInputMethodManager.restarts.isNotEmpty())
            assertEquals(input.inputType to input.imeOptions, RecordingInputMethodManager.configurations.last())
            val started = RecordingInputMethodManager.restarts.size
            policy.close()
            assertTrue(RecordingInputMethodManager.restarts.size > started)
            assertEquals(input.inputType to input.imeOptions, RecordingInputMethodManager.configurations.last())
        } finally { activity.pause().stop().destroy() }
    }
}

@Implements(InputMethodManager::class)
class RecordingInputMethodManager : ShadowInputMethodManager() {
    @Implementation
    public override fun restartInput(view: View) {
        restarts.add(view)
        if (view is TextView) configurations.add(view.inputType to view.imeOptions)
        super.restartInput(view)
    }
    companion object {
        val restarts = mutableListOf<View>()
        val configurations = mutableListOf<Pair<Int, Int>>()
    }
}
