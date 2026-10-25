package dev.skomlach.biometric.app.signature

import android.view.WindowManager
import androidx.navigation.findNavController
import dev.skomlach.biometric.app.MainActivity
import dev.skomlach.biometric.app.R
import dev.skomlach.biometric.compat.BiometricPromptCompat
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.ConscryptMode(org.robolectric.annotation.ConscryptMode.Mode.OFF)
@Config(sdk = [29], application = android.app.Application::class, manifest = Config.NONE)
class SignatureSecureWindowTest {
    @Test fun actualActivityFocusUpdatesKeepSignatureSecureAndExitRestoresUserPreference() {
        BiometricPromptCompat.apiEnabled(false)
        val application = RuntimeEnvironment.getApplication()
        // Manifest-free fixture still hosts FirstFragment's ordinary connectivity receiver.
        shadowOf(application).grantPermissions(application.packageName + ".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
        try {
            listOf(false, true).forEach { preference ->
                RuntimeEnvironment.getApplication().getSharedPreferences("app_settings", 0).edit()
                    .putBoolean("checkboxWindowSecure", preference).commit()
                val activity = Robolectric.buildActivity(MainActivity::class.java)
                activity.get().setTheme(R.style.Theme_MyApplication)
                activity.setup()
                try {
                    val nav = activity.get().findNavController(R.id.nav_host_fragment_content_main)
                    nav.navigate(R.id.action_FirstFragment_to_SignatureSystemFragment)
                    shadowOf(android.os.Looper.getMainLooper()).idle()
                    activity.get().onWindowFocusChanged(true)
                    assertTrue("Focus return must preserve signature protection",
                        activity.get().window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
                    nav.popBackStack()
                    shadowOf(android.os.Looper.getMainLooper()).idle()
                    activity.get().onWindowFocusChanged(true)
                    assertEquals(preference,
                        activity.get().window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
                } finally { activity.pause().stop().destroy() }
            }
        } finally { BiometricPromptCompat.apiEnabled(true) }
    }
}
