package dev.skomlach.biometric.compat.engine

import androidx.core.os.CancellationSignal
import dev.skomlach.biometric.compat.AuthenticationFailureReason
import dev.skomlach.biometric.compat.AuthenticationResult
import dev.skomlach.biometric.compat.BiometricCryptoObject
import dev.skomlach.biometric.compat.BiometricProviderType
import dev.skomlach.biometric.compat.BiometricType
import dev.skomlach.biometric.compat.engine.core.Core
import dev.skomlach.biometric.compat.engine.core.interfaces.AuthenticationListener
import dev.skomlach.biometric.compat.engine.core.interfaces.BiometricModule
import dev.skomlach.biometric.compat.engine.core.interfaces.RestartPredicate
import dev.skomlach.biometric.compat.impl.PendingAuthStart
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class LegacyBiometricCancellationTest {

    @Test
    fun `late routes join live core modules without restart and all signals cancel`() {
        val modules = moduleMap()
        val original = synchronized(modules) { modules.toMap() }
        val logging = BiometricLoggerImpl.DEBUG
        val finger = RecordingModule(BiometricMethod.FINGERPRINT_API23.id)
        val face = RecordingModule(BiometricMethod.FACE_MIUI.id)
        val iris = RecordingModule(BiometricMethod.IRIS_SAMSUNG.id)
        val owner = Any()
        fun join(type: BiometricType) = LegacyBiometric.authenticateInSession(
            owner, null, null, listOf(type), NoOpLegacyListener, null,
            BiometricProviderType.COMBINED, emptySet(), false
        )
        try {
            BiometricLoggerImpl.DEBUG = false
            LegacyBiometric.cancelAuthentication()
            Core.cleanModules()
            synchronized(modules) {
                modules.clear()
                modules[BiometricModuleKey.hardware(BiometricMethod.FINGERPRINT_API23)] = finger
                modules[BiometricModuleKey.hardware(BiometricMethod.FACE_MIUI)] = face
                modules[BiometricModuleKey.hardware(BiometricMethod.IRIS_SAMSUNG)] = iris
            }
            join(BiometricType.BIOMETRIC_FINGERPRINT)
            assertEquals(1, finger.starts)
            val originalSignal = finger.signal!!
            join(BiometricType.BIOMETRIC_FACE)
            assertEquals(1, finger.starts)
            assertFalse(originalSignal.isCanceled)
            assertEquals(1, face.starts)
            // An ALL request can continue after one module succeeds and releases the old gate.
            finger.listener!!.onSuccess(finger.tag(), null)
            join(BiometricType.BIOMETRIC_IRIS)
            join(BiometricType.BIOMETRIC_FACE)
            assertEquals(1, iris.starts)
            assertEquals(1, face.starts)
            assertFalse(face.signal!!.isCanceled)
            LegacyBiometric.cancelAuthentication()
            assertTrue(originalSignal.isCanceled)
            assertTrue(face.signal!!.isCanceled)
            assertTrue(iris.signal!!.isCanceled)
        } finally {
            LegacyBiometric.cancelAuthentication()
            Core.cleanModules()
            synchronized(modules) {
                modules.clear()
                modules.putAll(original)
            }
            BiometricLoggerImpl.DEBUG = logging
        }
    }

    @Test
    fun `a module cancellation releases legacy authentication for the next route`() {
        val modules = moduleMap()
        val authInProgress = authInProgress()
        val originalModules = synchronized(modules) { modules.toMap() }
        val originalLogging = BiometricLoggerImpl.DEBUG
        try {
            BiometricLoggerImpl.DEBUG = false
            Core.cleanModules()
            synchronized(modules) {
                modules.clear()
                modules[BiometricModuleKey.hardware(BiometricMethod.FACE_MIUI)] = CancelingModule
            }
            authInProgress.set(false)

            LegacyBiometric.authenticate(
                biometricCryptographyPurpose = null,
                targetView = null,
                requestedMethods = listOf(BiometricType.BIOMETRIC_FACE),
                listener = NoOpLegacyListener,
                bundle = null,
                provider = BiometricProviderType.COMBINED
            )

            assertTrue(NoOpLegacyListener.canceled)
            assertFalse(authInProgress.get())
        } finally {
            Core.cleanModules()
            synchronized(modules) {
                modules.clear()
                modules.putAll(originalModules)
            }
            authInProgress.set(false)
            NoOpLegacyListener.canceled = false
            BiometricLoggerImpl.DEBUG = originalLogging
        }
    }

    @Test
    fun `success then cancel stops every remaining core signal even when start gate is free`() {
        val authInProgress = authInProgress()
        val previousGate = authInProgress.get()
        val previousLogging = BiometricLoggerImpl.DEBUG
        val first = RecordingModule(301)
        val second = RecordingModule(302)
        try {
            BiometricLoggerImpl.DEBUG = false
            Core.cleanModules()
            Core.registerModule(first)
            Core.registerModule(second)
            Core.authenticate(null, first, null, null)
            Core.authenticate(null, second, null, null)
            // Mirrors the state after LegacyBiometric.onSuccess/onCanceled releases the gate.
            authInProgress.set(false)
            LegacyBiometric.cancelAuthentication()
            assertTrue(first.signal?.isCanceled == true)
            assertTrue(second.signal?.isCanceled == true)
            LegacyBiometric.cancelAuthentication()
        } finally {
            Core.cleanModules()
            authInProgress.set(previousGate)
            BiometricLoggerImpl.DEBUG = previousLogging
        }
    }

    @Test
    fun `canceled delayed failure cannot notify or stop the replacement core session`() {
        val previousLogging = BiometricLoggerImpl.DEBUG
        val previousGate = authInProgress().get()
        val queue = mutableListOf<Runnable>()
        val pendingFailure = PendingAuthStart({ task, _ -> queue += task }, { queue.remove(it) })
        val replacementModule = RecordingModule(303)
        var failureDelivered = false
        try {
            BiometricLoggerImpl.DEBUG = false
            Core.cleanModules()
            pendingFailure.schedule(2000) {
                failureDelivered = true
                LegacyBiometric.cancelAuthentication()
            }
            // A callback already taken out of the queue must also become inert on cancellation.
            val staleFailure = queue.removeAt(0)
            pendingFailure.cancel()

            Core.registerModule(replacementModule)
            Core.authenticate(null, replacementModule, null, null)
            assertTrue(replacementModule.signal != null)
            staleFailure.run()
            assertFalse(failureDelivered)
            assertFalse(replacementModule.signal!!.isCanceled)

            // The replacement session must still be able to deliver its own failure and stop.
            pendingFailure.schedule(2000) {
                failureDelivered = true
                LegacyBiometric.cancelAuthentication()
            }
            queue.removeAt(0).run()
            assertTrue(failureDelivered)
            assertTrue(replacementModule.signal!!.isCanceled)
        } finally {
            pendingFailure.cancel()
            Core.cleanModules()
            authInProgress().set(previousGate)
            BiometricLoggerImpl.DEBUG = previousLogging
        }
    }

    private class RecordingModule(private val id: Int) : BiometricModule {
        var signal: CancellationSignal? = null
        var starts = 0
        var listener: AuthenticationListener? = null
        override val isManagerAccessible = true
        override val isHardwarePresent = true
        override val isLockOut = false
        override val isUserAuthCanByUsedWithCrypto = false
        override val hasEnrolled = true
        @Deprecated("Unused in tests")
        override val isBiometricEnrollChanged = false
        override fun tag() = id
        override fun authenticate(biometricCryptoObject: BiometricCryptoObject?, cancellationSignal: CancellationSignal?, listener: AuthenticationListener?, restartPredicate: RestartPredicate?) {
            starts++
            this.listener = listener
            signal = cancellationSignal
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun moduleMap(): MutableMap<BiometricModuleKey, BiometricModule> {
        val field = LegacyBiometric::class.java.getDeclaredField("moduleHashMap")
        field.isAccessible = true
        return field.get(LegacyBiometric) as MutableMap<BiometricModuleKey, BiometricModule>
    }

    private fun authInProgress(): AtomicBoolean {
        val field = LegacyBiometric::class.java.getDeclaredField("authInProgress")
        field.isAccessible = true
        return field.get(LegacyBiometric) as AtomicBoolean
    }

    private object CancelingModule : BiometricModule {
        override val isManagerAccessible: Boolean = true
        override val isHardwarePresent: Boolean = true
        override val isLockOut: Boolean = false
        override val isUserAuthCanByUsedWithCrypto: Boolean = false
        override val hasEnrolled: Boolean = true

        @Deprecated("Unused in tests")
        override val isBiometricEnrollChanged: Boolean = false

        override fun authenticate(
            biometricCryptoObject: BiometricCryptoObject?,
            cancellationSignal: CancellationSignal?,
            listener: AuthenticationListener?,
            restartPredicate: RestartPredicate?
        ) {
            listener?.onCanceled(tag(), AuthenticationFailureReason.CANCELED, null)
        }

        override fun tag(): Int = BiometricMethod.FACE_MIUI.id
    }

    private object NoOpLegacyListener : LegacyBiometricAuthenticationListener {
        var canceled = false

        override fun onSuccess(result: AuthenticationResult) = Unit

        override fun onHelp(msg: CharSequence?) = Unit

        override fun onFailure(result: AuthenticationResult) = Unit

        override fun onCanceled(result: AuthenticationResult) {
            canceled = true
        }
    }
}
