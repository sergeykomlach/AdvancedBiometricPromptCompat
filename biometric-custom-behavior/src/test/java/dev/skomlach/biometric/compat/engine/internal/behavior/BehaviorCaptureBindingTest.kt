package dev.skomlach.biometric.compat.engine.internal.behavior

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import dev.skomlach.biometric.compat.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class BehaviorCaptureBindingTest {
    @Test fun concurrentBindingsCannotRestorePrivacySettingsWhileAnotherCaptureUsesTheField() {
        BiometricPromptCompat.apiEnabled(false)
        val activity = Robolectric.buildActivity(Activity::class.java).setup()
        val input = EditText(activity.get()).apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE }
        val original = input.inputType
        val firstBuilder = BiometricPromptCompat.Builder(BiometricAuthRequest.default()).setBehaviorTypingView(input)
        val secondBuilder = BiometricPromptCompat.Builder(BiometricAuthRequest.default()).setBehaviorTypingView(input)
        val first = BehaviorCaptureBinding(firstBuilder, BehaviorMode.TYPING, onReady = {})
        val second = BehaviorCaptureBinding(secondBuilder, BehaviorMode.TYPING, onReady = {})
        try {
            first.start()
            assertThrows(IllegalStateException::class.java) { second.start() }
            second.close()
            assertFalse(input.isSaveEnabled)
            assertEquals(0, input.inputType and InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE)
            first.close()
            assertTrue(input.isSaveEnabled)
            assertEquals(original, input.inputType)
        } finally {
            first.close(); second.close(); firstBuilder.release(); secondBuilder.release()
            activity.pause().stop().destroy(); BiometricPromptCompat.apiEnabled(true)
        }
    }

    @Test fun signatureContainerHasOneCaptureOwnerAndFailedSecondStartDoesNotDetachIt() {
        BiometricPromptCompat.apiEnabled(false)
        val activity = Robolectric.buildActivity(Activity::class.java).setup()
        val pad = android.widget.FrameLayout(activity.get())
        val builder = BiometricPromptCompat.Builder(BiometricAuthRequest.default()).setBehaviorSignatureContainer(pad)
        val first = BehaviorCaptureBinding(builder, BehaviorMode.SIGNATURE, onReady = {})
        val second = BehaviorCaptureBinding(builder, BehaviorMode.SIGNATURE, onReady = {})
        try {
            first.start()
            assertThrows(IllegalStateException::class.java) { second.start() }
            assertEquals(1, pad.childCount)
            assertEquals(BehaviorAuthMode.PASSIVE, builder.getBehaviorAuthMode())
            first.close()
            assertEquals(0, pad.childCount)
        } finally {
            first.close(); second.close(); builder.release(); activity.pause().stop().destroy()
            BiometricPromptCompat.apiEnabled(true)
        }
    }

    @Test fun consumerCanCaptureAndSubmitWithoutLibraryUi() {
        BiometricPromptCompat.apiEnabled(false)
        val activity = Robolectric.buildActivity(Activity::class.java).setup()
        val input = EditText(activity.get()).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
        }
        activity.get().setContentView(input)
        val original = input.inputType
        val builder = BiometricPromptCompat.Builder(BiometricAuthRequest.default()).setBehaviorTypingView(input)
        var payload: Bundle? = null
        var calls = 0
        try {
            BehaviorCaptureBinding(builder, BehaviorMode.TYPING, onReady = { payload = it; calls++ }).use { binding ->
                binding.start()
                assertEquals(0, input.inputType and InputType.TYPE_TEXT_FLAG_AUTO_CORRECT)
                "abcde".forEachIndexed { index, _ ->
                    ShadowSystemClock.advanceBy(Duration.ofMillis(110L + index * 17L))
                    input.append("abcde"[index].toString())
                }
                assertTrue(binding.submit())
                binding.submit()
                assertEquals(1, calls)
                assertFalse("Capture transport must not expose the phrase", payload!!.containsKey(BehaviorSample.EXTRA_BEHAVIOR_PHRASE))
                assertNotNull(BehaviorCaptureSessionRegistry.token(payload!!.getLong(EXTRA_BEHAVIOR_SESSION_NONCE)))
                assertEquals("abcde", BehaviorCaptureSessionRegistry.consumeSample(
                    payload!!.getLong(EXTRA_BEHAVIOR_SESSION_NONCE), android.os.SystemClock.elapsedRealtime(), false)!!.phrase)
                assertEquals(BehaviorAuthMode.PASSIVE, builder.getBehaviorAuthMode())
            }
            assertEquals(original, input.inputType)
            assertEquals("abcde", input.text.toString())
            assertEquals(BehaviorAuthMode.EXPLICIT, builder.getBehaviorAuthMode())
        } finally {
            builder.release(); activity.pause().stop().destroy(); BiometricPromptCompat.apiEnabled(true)
        }
    }
}
