package dev.skomlach.biometric.compat.engine.internal.behavior

import android.app.Activity
import android.os.Bundle
import android.os.SystemClock
import android.text.InputType
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import dev.skomlach.biometric.compat.BiometricAuthRequest
import dev.skomlach.biometric.compat.BiometricPromptCompat
import dev.skomlach.biometric.compat.utils.ScreenProtection
import dev.skomlach.biometric.custom.behavior.R
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE, qualifiers = "en")
class BehaviorCaptureControllerTest {
    private val activityController = Robolectric.buildActivity(Activity::class.java).setup()
    private val context = activityController.get()
    private val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val host = LinearLayout(context).apply { id = R.id.dialogLayout }
    private val container = FrameLayout(context).apply { id = R.id.auth_content_container }
    // Isolate capture UI from global hardware discovery and background provider warmup.
    private val builder = run {
        BiometricPromptCompat.apiEnabled(false)
        BiometricPromptCompat.Builder(BiometricAuthRequest.default())
    }
    private lateinit var controller: BehaviorCaptureController
    private var readyCount = 0

    private fun install(externalInput: TextView? = null, externalPad: ViewGroup? = null) {
        host.addView(container, ViewGroup.LayoutParams(600, 160))
        root.addView(host)
        context.setContentView(root)
        builder.setBehaviorTypingView(externalInput)
        builder.setBehaviorSignatureContainer(externalPad)
        controller = BehaviorCaptureController(root, builder, false) { readyCount++ }
        controller.install()
    }

    @After fun dispose() {
        if (::controller.isInitialized) controller.dispose()
        builder.release()
        activityController.pause().stop().destroy()
        BiometricPromptCompat.apiEnabled(true)
    }

    @Test fun automaticallyShowsCaptureWithoutActivationButtonOrHidingHost() {
        install()
        assertEquals(View.VISIBLE, host.visibility)
        assertFalse(views().filterIsInstance<TextView>().any { it.text.toString() == "B" })
        assertTrue(views().filterIsInstance<Button>().any { it.text.toString() == "Verify" })
    }

    @Test fun knownCompromiseStopsCaptureInsteadOfCollectingSensitiveInput() {
        val flag = BiometricPromptCompat::class.java.getDeclaredField("reference")
            .apply { isAccessible = true }.get(null) as java.util.concurrent.atomic.AtomicBoolean
        val original = flag.get()
        try {
            install()
            flag.set(true)
            touch(key("a"), MotionEvent.ACTION_DOWN)
            touch(key("a"), MotionEvent.ACTION_UP)
            assertEquals("", views().filterIsInstance<EditText>().single().text.toString())
        } finally { flag.set(original) }
    }

    @Test fun typingCaptureDisablesCompletionAndRestoresCallerConfiguration() {
        val original = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_AUTO_CORRECT or InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE
        val input = EditText(context).apply { inputType = original; setText("caller text") }
        install(externalInput = input)
        assertEquals(0, input.inputType and InputType.TYPE_TEXT_FLAG_AUTO_CORRECT)
        assertEquals(0, input.inputType and InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE)
        assertTrue(input.inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS != 0)
        assertTrue(input.inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0)
        controller.dispose()
        assertEquals(original, input.inputType)
        assertEquals("caller text", input.text.toString())
    }

    @Test fun typingCaptureDetachesAndRestoresAutoCompleteAdapter() {
        val adapter = ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, listOf("alpha", "beta"))
        val input = AutoCompleteTextView(context).apply { setAdapter(adapter); setText("al") }
        install(externalInput = input)
        assertNull(input.adapter)
        controller.dispose()
        assertSame(adapter, input.adapter)
        assertEquals("al", input.text.toString())
    }

    @Test fun closeAndReopenDoesNotReuseAnAttachedInputView() {
        install()
        controller.dispose()
        controller = BehaviorCaptureController(root, builder, false) { readyCount++ }
        controller.install()
        assertEquals(1, views().filterIsInstance<EditText>().size)
        assertTrue(views().filterIsInstance<Button>().any { it.text.toString() == "Verify" })
    }

    @Test fun compactWindowKeepsSubmitOutsideScrollableBody() {
        install()
        root.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.AT_MOST))
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        val submit = views().filterIsInstance<Button>().first { it.text.toString() == "Verify" }
        val scroll = views().filterIsInstance<ScrollView>().single()
        assertTrue(root.measuredHeight <= 320)
        assertTrue(scroll.height > 0)
        assertTrue(submit.isShown)
        assertFalse(generateSequence(submit.parent) { it.parent }.any { it === scroll })
        val top = IntArray(2).also { root.getLocationOnScreen(it) }[1]
        val bottom = IntArray(2).also { submit.getLocationOnScreen(it) }[1] + submit.height
        assertTrue("Submit must fit in the compact host", bottom <= top + root.height)
    }

    @Test fun cancelledKeyInvalidatesCaptureAndModeChangeKeepsErrorVisible() {
        install()
        val key = key("a")
        touch(key, MotionEvent.ACTION_DOWN)
        touch(key, MotionEvent.ACTION_CANCEL)
        val error = views().filterIsInstance<TextView>().firstOrNull {
            it.visibility == View.VISIBLE && it.text.toString().contains("interrupted")
        }
        assertNotNull("Cancellation must visibly require restart", error)
        views().filterIsInstance<RadioButton>().first { it.text.toString() == "Signature" }.performClick()
        assertEquals(View.VISIBLE, error!!.visibility)
        assertEquals("", views().filterIsInstance<EditText>().single().text.toString())
    }

    @Test fun restartClearsInputAndAllowsNewTouchAfterInvalidation() {
        install()
        touch(key("a"), MotionEvent.ACTION_DOWN)
        touch(key("a"), MotionEvent.ACTION_CANCEL)
        views().filterIsInstance<Button>().first { it.text.toString() == "Restart" }.performClick()
        touch(key("b"), MotionEvent.ACTION_DOWN)
        touch(key("b"), MotionEvent.ACTION_UP)
        assertEquals("b", views().filterIsInstance<EditText>().single().text.toString())
    }

    @Test fun fullyObscuredTouchInvalidatesBeforeFrameworkDiscardsEvent() {
        install()
        touch(key("a"), MotionEvent.ACTION_DOWN, MotionEvent.FLAG_WINDOW_IS_OBSCURED)
        touch(key("b"), MotionEvent.ACTION_DOWN)
        touch(key("b"), MotionEvent.ACTION_UP)
        assertEquals("", views().filterIsInstance<EditText>().single().text.toString())
        assertTrue(views().filterIsInstance<TextView>().any {
            it.visibility == View.VISIBLE && it.text.toString().contains("obscured")
        })
    }

    @Test fun signatureStrokeReservesScrollingUntilPenIsLifted() {
        var disallowed = false
        val externalPad = object : FrameLayout(context) {
            override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
                disallowed = disallowIntercept
                super.requestDisallowInterceptTouchEvent(disallowIntercept)
            }
        }
        install(externalPad = externalPad)
        val pad = externalPad.getChildAt(0)
        touch(pad, MotionEvent.ACTION_DOWN)
        assertTrue(disallowed)
        touch(pad, MotionEvent.ACTION_MOVE)
        assertTrue(disallowed)
        touch(pad, MotionEvent.ACTION_UP)
        assertFalse(disallowed)
    }

    @Test fun expiredCaptureCanRestartAndCollectFreshInput() {
        install()
        ShadowSystemClock.advanceBy(Duration.ofSeconds(31))
        touch(key("a"), MotionEvent.ACTION_DOWN)
        assertTrue(views().filterIsInstance<TextView>().any {
            it.visibility == View.VISIBLE && it.text.toString().contains("timed out")
        })
        // The root must still dispatch touches to recovery controls after invalidation.
        MotionEvent.obtain(0L, SystemClock.uptimeMillis(), MotionEvent.ACTION_DOWN, 1f, 1f, 0).let {
            try { assertTrue(overlay().onFilterTouchEventForSecurity(it)) } finally { it.recycle() }
        }
        views().filterIsInstance<Button>().first { it.text.toString() == "Restart" }.performClick()
        touch(key("a"), MotionEvent.ACTION_DOWN)
        touch(key("a"), MotionEvent.ACTION_UP)
        assertEquals("a", views().filterIsInstance<EditText>().single().text.toString())
    }

    @Test fun closingRestoresHostVisibilityAndDisposeIsIdempotent() {
        install()
        assertEquals(View.VISIBLE, host.visibility)
        controller.dispose()
        controller.dispose()
        assertEquals(0, container.childCount)
    }

    @Test fun validTypingSubmitsOnceAndDisposalClearsOnlyOwnedPayload() {
        builder.setExtras(Bundle().apply { putString("caller", "preserved") })
        install()
        views().filterIsInstance<RadioButton>().first { it.text.toString() == "Typing" }.performClick()
        "abcde".forEachIndexed { index, char ->
            val key = key(char.toString())
            touch(key, MotionEvent.ACTION_DOWN)
            ShadowSystemClock.advanceBy(Duration.ofMillis(60L + index * 7L))
            touch(key, MotionEvent.ACTION_UP)
            ShadowSystemClock.advanceBy(Duration.ofMillis(80L + index * 11L))
        }
        val submit = views().filterIsInstance<Button>().first { it.text.toString() == "Verify" }
        submit.performClick()
        submit.performClick()
        assertEquals(1, readyCount)
        val payload = builder.getExtras()!!
        val nonce = payload.getLong(EXTRA_BEHAVIOR_SESSION_NONCE)
        assertNotNull(BehaviorCaptureSessionRegistry.token(nonce))
        assertNull(BehaviorSample.fromBundle(payload))
        val sample = BehaviorCaptureSessionRegistry.consumeSample(nonce, SystemClock.elapsedRealtime(), false)!!
        assertEquals("abcde", sample.phrase)
        assertEquals(5, sample.keyDownTimesMs.size)
        assertEquals(BehaviorQualityIssue.NONE, sample.qualityIssue())
        controller.dispose()
        assertNull(BehaviorCaptureSessionRegistry.token(nonce))
        assertEquals("preserved", builder.getExtras()!!.getString("caller"))
        assertFalse(builder.getExtras()!!.containsKey(BehaviorSample.EXTRA_BEHAVIOR_PHRASE))
        // Retained core/caller extras cannot recover a consumed or cancelled raw sample.
        assertNull(BehaviorSample.fromBundle(payload))
        assertNull(BehaviorCaptureSessionRegistry.consumeSample(nonce, SystemClock.elapsedRealtime(), false))
    }

    @Test fun disposeRestoresCallerTouchFilterAndPreservesExistingProtection() {
        val input = EditText(context).apply { filterTouchesWhenObscured = false }
        val pad = FrameLayout(context).apply { filterTouchesWhenObscured = true }
        ScreenProtection.applyProtectionInView(input)
        install(input, pad)
        controller.dispose()
        assertFalse(input.filterTouchesWhenObscured)
        assertTrue(pad.filterTouchesWhenObscured)
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS, input.importantForAccessibility)
        assertEquals(0, pad.childCount)
    }

    private fun overlay(): View = container.getChildAt(0)

    private fun key(label: String): TextView = views().filterIsInstance<TextView>().first { it.text.toString() == label }

    private fun views(): List<View> {
        fun descendants(view: View): List<View> = listOf(view) +
            if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
        return descendants(root)
    }

    private fun touch(view: View, action: Int, flags: Int = 0) {
        val now = SystemClock.uptimeMillis()
        val properties = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_FINGER })
        val coords = arrayOf(MotionEvent.PointerCoords().apply { x = 10f; y = 10f; pressure = .5f; size = .4f })
        MotionEvent.obtain(now, now, action, 1, properties, coords, 0, 0, 1f, 1f, 0, 0,
            InputDevice.SOURCE_TOUCHSCREEN, flags).let { event ->
            try { view.dispatchTouchEvent(event) } finally { event.recycle() }
        }
    }
}
