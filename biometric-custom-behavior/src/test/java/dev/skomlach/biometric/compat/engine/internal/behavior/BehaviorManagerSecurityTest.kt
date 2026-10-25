package dev.skomlach.biometric.compat.engine.internal.behavior

import android.content.Context
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Looper
import android.os.SystemClock
import dev.skomlach.biometric.compat.BiometricPromptCompat
import dev.skomlach.biometric.compat.custom.AbstractSoftwareBiometricManager
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class BehaviorManagerSecurityTest {
    private val context = RuntimeEnvironment.getApplication()
    private val cancel = CancellationSignal()
    private val flag = BiometricPromptCompat::class.java.getDeclaredField("reference")
        .apply { isAccessible = true }.get(null) as java.util.concurrent.atomic.AtomicBoolean
    private val originalFlag = flag.get()

    private fun sample(phrase: String) = BehaviorSample(BehaviorMode.TYPING, phrase,
        listOf(0L, 120L, 270L, 420L, 610L), listOf(60L, 190L, 345L, 500L, 705L), emptyList())

    private fun manager(sample: BehaviorSample): BehaviorBiometricManager {
        val id = java.util.UUID.randomUUID().toString()
        val store = BehaviorTemplateStore { context.getSharedPreferences("templates_$id", Context.MODE_PRIVATE) }
        store.save("profile", sample)
        return BehaviorBiometricManager(context, store).also { manager ->
            // Exercise the real manager with real preferences, without pretending to emulate Keystore.
            manager.javaClass.getDeclaredField("prefs\$delegate").apply { isAccessible = true }.set(manager,
                lazy { context.getSharedPreferences("lockout_$id", Context.MODE_PRIVATE) })
        }
    }

    private fun prepared(sample: BehaviorSample, enrollment: Boolean = false): Bundle {
        val token = BehaviorCaptureSessionRegistry.start(SystemClock.elapsedRealtime(), SystemClock.uptimeMillis())
        org.robolectric.shadows.ShadowSystemClock.advanceBy(Duration.ofMillis(800))
        return sealBehaviorExtras(null, sample, enrollment, token, SystemClock.elapsedRealtime())!!
    }

    private class Result : AbstractSoftwareBiometricManager.AuthenticationCallback() {
        var successes = 0
        var errors = 0
        override fun onAuthenticationSucceeded(result: AbstractSoftwareBiometricManager.AuthenticationResult?) { successes++ }
        override fun onAuthenticationError(errMsgId: Int, errString: CharSequence?) { errors++ }
    }

    @After fun cleanup() { cancel.cancel(); flag.set(originalFlag); shadowOf(Looper.getMainLooper()).idle() }

    @Test fun realManagerUsesSealedSampleInsteadOfSubstitutedRawInput() {
        val enrolled = sample("alpha")
        val extras = prepared(enrolled).apply {
            putString(BehaviorSample.EXTRA_BEHAVIOR_MODE, "TYPING")
            putString(BehaviorSample.EXTRA_BEHAVIOR_PHRASE, "wrong")
            putLongArray(BehaviorSample.EXTRA_BEHAVIOR_KEY_DOWNS, longArrayOf(0, 0, 0))
        }
        val result = Result()
        manager(enrolled).authenticate(null, 0, cancel, result, null, extras)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
        assertEquals(1, result.successes)
        assertEquals(0, result.errors)
    }

    @Test fun roleMismatchCannotFallbackToValidRawInput() {
        val enrolled = sample("bravo")
        val extras = buildBehaviorExtras(null, enrolled.mode, enrolled.phrase,
            enrolled.keyDownTimesMs.toLongArray(), enrolled.keyUpTimesMs.toLongArray(), floatArrayOf(), false)
        // buildBehaviorExtras removes handles for the legacy path; retain the stolen enrollment handle explicitly.
        val sealed = prepared(enrolled, true)
        extras.putLong(EXTRA_BEHAVIOR_SESSION_NONCE, sealed.getLong(EXTRA_BEHAVIOR_SESSION_NONCE))
        val result = Result()
        manager(enrolled).authenticate(null, 0, cancel, result, null, extras)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
        assertEquals(0, result.successes)
        assertEquals(1, result.errors)
    }

    @Test fun knownHookBetweenMatchingAndDelayedDeliveryRejectsSuccess() {
        val enrolled = sample("delta")
        val result = Result()
        manager(enrolled).authenticate(null, 0, cancel, result, null, prepared(enrolled))
        assertEquals(0, result.errors)
        flag.set(true)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
        assertEquals(0, result.successes)
        assertEquals(1, result.errors)
    }
}
