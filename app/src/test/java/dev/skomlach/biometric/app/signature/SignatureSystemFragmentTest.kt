package dev.skomlach.biometric.app.signature

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.fragment.app.FragmentActivity
import dev.skomlach.biometric.app.R
import dev.skomlach.biometric.compat.SupplementalBiometricOutcome
import dev.skomlach.biometric.compat.engine.internal.behavior.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.ConscryptMode(org.robolectric.annotation.ConscryptMode.Mode.OFF)
@Config(sdk = [29], application = android.app.Application::class, manifest = Config.NONE)
class SignatureSystemFragmentTest {
    @Test fun screenOwnsItsSignatureViewAndStopCancelsTheAttemptAndRejectsLateCallbacks() {
        val activity = Robolectric.buildActivity(SignatureSampleHostActivity::class.java).setup()
        val root = FrameLayout(activity.get()).apply { id = View.generateViewId() }
        activity.get().setContentView(root)
        val originalFlags = activity.get().window.attributes.flags
        val fragment = SignatureSystemFragment()
        try {
            activity.get().supportFragmentManager.beginTransaction().add(root.id, fragment).commitNow()
            assertTrue(fragment.requireView().findViewById<View>(R.id.signature_pad) is SignatureCaptureView)
            assertTrue(activity.get().window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
            val field = fragment.javaClass.getDeclaredField("flow").apply { isAccessible = true }
            (field.get(fragment) as SignatureSystemFlow).close()
            lateinit var callback: (SupplementalBiometricOutcome) -> Unit
            var canceled = 0
            var systemCalls = 0
            val flow = SignatureSystemFlow({ _, result -> callback = result; AutoCloseable { canceled++ } },
                { systemCalls++; AutoCloseable {} }, {})
            field.set(fragment, flow)
            flow.submit(BehaviorSample(BehaviorMode.SIGNATURE, null, emptyList(), emptyList(), emptyList()))
            activity.pause().stop()
            callback(SupplementalBiometricOutcome.MATCHED)
            assertEquals(1, canceled)
            assertEquals(0, systemCalls)
            assertFalse(flow.state.authorized)
            activity.get().supportFragmentManager.beginTransaction().remove(fragment).commitNowAllowingStateLoss()
            assertNull(field.get(fragment))
            assertEquals(originalFlags and WindowManager.LayoutParams.FLAG_SECURE,
                activity.get().window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE)
        } finally { activity.destroy() }
    }
}

class SignatureSampleHostActivity : FragmentActivity() {
    override fun onCreate(state: Bundle?) { setTheme(R.style.Theme_MyApplication); super.onCreate(state) }
}
