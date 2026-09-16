package dev.skomlach.biometric.compat.utils

import android.annotation.SuppressLint
import android.os.Build
import android.view.View
import androidx.annotation.DoNotInline
import androidx.annotation.RequiresApi
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl
import java.lang.reflect.Field

internal object AccessibilityDelegateAccess {
    @SuppressLint("NewApi")
    fun get(view: View): View.AccessibilityDelegate? = readPlatformOrLegacy(
        platformAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q,
        platformRead = { Api29.get(view) },
        legacyRead = { Legacy.get(view) },
        onLinkageError = { BiometricLoggerImpl.e(it, "AccessibilityDelegateAccess") }
    )

    @RequiresApi(Build.VERSION_CODES.Q)
    private object Api29 {
        @DoNotInline
        fun get(view: View): View.AccessibilityDelegate? = view.accessibilityDelegate
    }

    // Older Android has no public getter. Keep exact delegate restoration for that path.
    @SuppressLint("DiscouragedPrivateApi", "PrivateApi")
    private object Legacy {
        private val delegateField: Field? by lazy {
            runCatching {
                View::class.java.getDeclaredField("mAccessibilityDelegate").apply {
                    isAccessible = true
                }
            }.onFailure { BiometricLoggerImpl.e(it, "AccessibilityDelegateAccess.Legacy") }
                .getOrNull()
        }

        fun get(view: View): View.AccessibilityDelegate? = runCatching {
            delegateField?.get(view) as? View.AccessibilityDelegate
        }.onFailure { BiometricLoggerImpl.e(it, "AccessibilityDelegateAccess.Legacy") }
            .getOrNull()
    }
}
