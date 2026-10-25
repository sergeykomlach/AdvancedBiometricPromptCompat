package dev.skomlach.biometric.compat.engine.internal.behavior

import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.FragmentActivity
import dev.skomlach.biometric.compat.BiometricAuthRequest
import dev.skomlach.biometric.compat.BiometricPromptCompat
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE, qualifiers = "en")
class BehaviorCaptureFragmentTest {
    @Test fun restoredPromptWithNoOwnerCancelsAndDoesNotRestoreCaptureData() {
        BiometricPromptCompat.apiEnabled(false)
        val activity = Robolectric.buildActivity(BehaviorSecurityHostActivity::class.java).setup()
        val builder = BiometricPromptCompat.Builder(BiometricAuthRequest.default())
        val prompt = dev.skomlach.biometric.compat.impl.dialogs.BiometricPromptCompatDialog.getFragment(false)
        var ready = 0
        try {
            prompt.setOnShowListener {
                val root = prompt.rootView!!
                val auth = root.findViewById<View>(dev.skomlach.biometric.compat.R.id.auth_content_container)
                val parent = auth.parent as LinearLayout
                val content = root.findViewById<FrameLayout>(dev.skomlach.biometric.compat.R.id.software_capture_content)
                    ?: androidx.fragment.app.FragmentContainerView(root.context).also {
                        it.id = dev.skomlach.biometric.compat.R.id.software_capture_content
                        parent.addView(it)
                    }
                content.visibility = View.VISIBLE
                val capture = BehaviorCaptureFragment().also {
                    it.controller = BehaviorCaptureController(root, builder, false) { ready++ }
                }
                prompt.childFragmentManager.beginTransaction().add(content.id, capture, "capture").commitNow()
            }
            prompt.showNow(activity.get().supportFragmentManager, "parent-capture")
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            assertNotNull(prompt.childFragmentManager.findFragmentByTag("capture"))
            activity.recreate()
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            assertNull("An ownerless prompt must close after recreation",
                activity.get().supportFragmentManager.findFragmentByTag("parent-capture"))
            assertEquals(0, ready)
            assertNull(builder.getExtras()?.get(BehaviorSample.EXTRA_BEHAVIOR_PHRASE))
        } finally {
            builder.release(); activity.pause().stop().destroy(); BiometricPromptCompat.apiEnabled(true)
        }
    }

    @Test fun fragmentAutomaticallyRendersAndDestroyRestoresConsumerInput() {
        BiometricPromptCompat.apiEnabled(false)
        val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup()
        val host = activity.get()
        val root = FrameLayout(host).apply { id = View.generateViewId() }
        host.setContentView(root)
        val input = EditText(host).apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE }
        val original = input.inputType
        val pad = FrameLayout(host)
        val builder = BiometricPromptCompat.Builder(BiometricAuthRequest.default())
            .setBehaviorTypingView(input).setBehaviorSignatureContainer(pad)
        try {
            val controller = BehaviorCaptureController(root, builder, false) {}
            val fragment = BehaviorCaptureFragment().also { it.controller = controller }
            host.supportFragmentManager.beginTransaction().add(root.id, fragment).commitNow()
            fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup)
                (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
            assertTrue(descendants(root).filterIsInstance<Button>().any { it.text.toString() == "Verify" })
            assertFalse(descendants(root).filterIsInstance<TextView>().any { it.text.toString() == "B" })
            assertEquals(0, input.inputType and InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE)
            assertEquals(1, pad.childCount)
            host.supportFragmentManager.beginTransaction().remove(fragment).commitNow()
            assertEquals(original, input.inputType)
            assertEquals(0, pad.childCount)
            assertNull(fragment.controller)
        } finally {
            builder.release(); activity.pause().stop().destroy(); BiometricPromptCompat.apiEnabled(true)
        }
    }
}

class BehaviorSecurityHostActivity : FragmentActivity() {
    override fun onCreate(state: android.os.Bundle?) {
        setTheme(androidx.appcompat.R.style.Theme_AppCompat)
        super.onCreate(state)
    }
}
