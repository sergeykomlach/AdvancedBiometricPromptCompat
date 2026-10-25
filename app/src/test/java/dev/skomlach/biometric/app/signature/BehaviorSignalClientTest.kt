package dev.skomlach.biometric.app.signature

import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import dev.skomlach.biometric.compat.*
import dev.skomlach.biometric.compat.custom.AbstractSoftwareBiometricManager
import dev.skomlach.biometric.compat.engine.internal.behavior.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.ConscryptMode(org.robolectric.annotation.ConscryptMode.Mode.OFF)
@Config(sdk = [29], application = android.app.Application::class, manifest = Config.NONE)
class BehaviorSignalClientTest {
    private val sample = BehaviorSample(BehaviorMode.SIGNATURE, null, emptyList(), emptyList(),
        (0 until 20).map { BehaviorPoint(it * 7f, (it % 3) * 40f, it * 35L, .5f, .4f) })
    private class Sdk : AbstractSoftwareBiometricManager() {
        override val biometricType = BiometricType.BIOMETRIC_BEHAVIOR
        var parsed: BehaviorSample? = null
        var received: Bundle? = null
        var rawPoints: FloatArray? = null
        lateinit var callback: AuthenticationCallback
        override fun authenticate(crypto: CryptoObject?, flags: Int, cancel: CancellationSignal?, callback: AuthenticationCallback?, handler: Handler?, extra: Bundle?) {
            this.callback = callback!!
            received = extra
            rawPoints = extra!!.getFloatArray(BehaviorSample.EXTRA_BEHAVIOR_POINTS)
            parsed = BehaviorSample.fromBundle(extra)
        }
        override fun getTimeoutMessage() = "timeout"
        override fun resetLockOut() {}
        override fun resetPermanentLockOut() {}
        override fun getPermissions() = emptyList<String>()
        override fun isHardwareDetected() = true
        override fun hasEnrolledBiometric() = true
        override fun getManagers(): Set<Any> = setOf(this)
        override fun remove(extra: Bundle?) {}
        override fun getEnrollBundle(name: String?) = Bundle()
        override fun getEnrolls() = listOf("test")
    }

    @Test fun temporaryRawPayloadIsClearedAfterSynchronousProviderParsing() {
        val sdk = Sdk()
        val results = mutableListOf<SupplementalBiometricOutcome>()
        BehaviorSignalClient(RuntimeEnvironment.getApplication(), sdk).evaluate(sample, results::add).use {
            assertEquals(sample, sdk.parsed)
            assertTrue(sdk.received!!.isEmpty)
            assertTrue(sdk.rawPoints!!.all { point -> point == 0f })
            sdk.callback.onAuthenticationSucceeded(AbstractSoftwareBiometricManager.AuthenticationResult(null))
            sdk.callback.onAuthenticationError(1, "late")
            assertEquals(listOf(SupplementalBiometricOutcome.MATCHED), results)
        }
    }

    @Test fun providerErrorsAreOnlySignalsAndCallbacksAfterCloseAreIgnored() {
        val sdk = Sdk()
        val results = mutableListOf<SupplementalBiometricOutcome>()
        val source = BehaviorSignalClient(RuntimeEnvironment.getApplication(), sdk)
        val handle = source.evaluate(sample, results::add)
        sdk.callback.onAuthenticationError(AbstractSoftwareBiometricManager.CUSTOM_BIOMETRIC_ERROR_VENDOR, "mismatch")
        assertEquals(listOf(SupplementalBiometricOutcome.NOT_MATCHED), results)
        handle.close()
        val next = source.evaluate(sample, results::add)
        next.close()
        sdk.callback.onAuthenticationSucceeded(AbstractSoftwareBiometricManager.AuthenticationResult(null))
        assertEquals(1, results.size)
    }

    @Test fun nullProviderSuccessIsUnavailableRatherThanAMatch() {
        val sdk = Sdk()
        val results = mutableListOf<SupplementalBiometricOutcome>()
        BehaviorSignalClient(RuntimeEnvironment.getApplication(), sdk).evaluate(sample, results::add).use {
            sdk.callback.onAuthenticationSucceeded(null)
            assertEquals(listOf(SupplementalBiometricOutcome.UNAVAILABLE), results)
        }
    }
}
