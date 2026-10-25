package dev.skomlach.biometric.compat.engine.internal.behavior

import android.os.Bundle
import android.os.Handler
import android.os.CancellationSignal
import dev.skomlach.biometric.compat.*
import dev.skomlach.biometric.compat.custom.*
import dev.skomlach.biometric.compat.engine.BiometricMethod
import dev.skomlach.biometric.compat.engine.internal.SoftwareBiometricModule
import dev.skomlach.biometric.compat.engine.core.interfaces.AuthenticationListener
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class AuthenticationTrustIntegrationTest {
    private lateinit var builder: BiometricPromptCompat.Builder
    private val contextClass = Class.forName("dev.skomlach.biometric.compat.AuthenticationTrustContext")
    private val context = contextClass.getDeclaredField("INSTANCE").get(null)
    private val behavior = SoftwareBiometricSecurityProfile.passiveCompatibility(BiometricType.BIOMETRIC_BEHAVIOR)
    private val face = SoftwareBiometricSecurityProfile(BiometricType.BIOMETRIC_FACE,
        SoftwareBiometricAssuranceLevel.PASSIVE_MATCH, true, false, false, 30_000L)

    @Before fun setup() {
        BiometricPromptCompat.apiEnabled(false)
        builder = BiometricPromptCompat.Builder(BiometricAuthRequest.default())
    }
    @After fun cleanup() { builder.release(); BiometricPromptCompat.apiEnabled(true) }

    private fun permits(bundle: Bundle, profile: SoftwareBiometricSecurityProfile, trusted: Boolean = false): Boolean =
        contextClass.getDeclaredMethod("permits", Bundle::class.java, SoftwareBiometricSecurityProfile::class.java,
            Boolean::class.javaPrimitiveType).invoke(context, bundle, profile, trusted) as Boolean

    private fun flow(method: String, id: Long) {
        builder.javaClass.declaredMethods.single { it.name.substringBefore('$') == method }
            .apply { isAccessible = true }.invoke(builder, id)
    }

    @Test fun callerAndPreparedExtrasCannotDowngradePolicyOrChangeEnrollmentRole() {
        builder.setBiometricTrustPolicy(BiometricTrustPolicy(BiometricTrustLevel.SYSTEM_BIOMETRIC))
        val attack = Bundle().apply {
            putString("biometric.internal.trust.minimum", "COMPATIBILITY")
            putBoolean("biometric.internal.trust.behavior_authorizer", true)
            putBoolean("biometric.internal.trust.supplemental", true)
            putBoolean("biometric.internal.trust.enrollment", true)
            putBoolean(BundleBuilder.ENROLL, true)
            putBoolean("is_enrollment", true)
            putString("caller.payload", "preserved")
        }
        builder.setExtras(attack)
        val bundle = BundleBuilder.create(builder, attack)
        assertEquals("SYSTEM_BIOMETRIC", bundle.getString("biometric.internal.trust.minimum"))
        assertFalse(bundle.getBoolean(BundleBuilder.ENROLL))
        assertFalse(bundle.getBoolean("is_enrollment"))
        assertEquals("preserved", bundle.getString("caller.payload"))
        assertFalse(permits(bundle, behavior))
        assertFalse(permits(bundle, face, true))
    }

    @Test fun activePolicyIsFrozenAndOlderFlowCannotClearIt() {
        flow("beginAuthFlow", 11L)
        val weaker = BiometricTrustPolicy(BiometricTrustLevel.COMPATIBILITY, true)
        builder.setBiometricTrustPolicy(weaker)
        assertEquals(BiometricTrustPolicy(), builder.getBiometricTrustPolicy())
        val active = BundleBuilder.create(builder)
        assertFalse(permits(active, behavior))
        flow("endAuthFlow", 10L)
        assertEquals(BiometricTrustPolicy(), builder.getBiometricTrustPolicy())
        flow("endAuthFlow", 11L)
        assertEquals(weaker, builder.getBiometricTrustPolicy())
        assertTrue(permits(BundleBuilder.create(builder), behavior))
        assertFalse(permits(active, behavior))
    }

    @Test fun creatingAnotherBuilderCannotForgetKnownRuntimeCompromise() {
        val flag = BiometricPromptCompat::class.java.getDeclaredField("reference")
            .apply { isAccessible = true }.get(null) as java.util.concurrent.atomic.AtomicBoolean
        val original = flag.get()
        var next: BiometricPromptCompat.Builder? = null
        try {
            flag.set(true)
            next = BiometricPromptCompat.Builder(BiometricAuthRequest.default())
            assertTrue("A new flow cannot clear an already detected hook", flag.get())
        } finally { next?.release(); flag.set(original) }
    }

    @Test fun actualManagerProfileAndCaptureMustStillMeetTheFloor() {
        val bundle = BundleBuilder.create(builder)
        assertTrue(permits(bundle, face, true))
        assertFalse(permits(bundle, face, false))
        assertFalse(permits(bundle, SoftwareBiometricSecurityProfile.passiveCompatibility(BiometricType.BIOMETRIC_FACE)))
    }

    @Test fun malformedOrPartialContextFailsClosed() {
        val original = BundleBuilder.create(builder)
        assertFalse(permits(Bundle(original).apply { remove("biometric.internal.trust.supplemental") }, face, true))
        assertFalse(permits(Bundle(original).apply { putString("biometric.internal.trust.minimum", "UNKNOWN") }, face, true))
        assertFalse(permits(Bundle(original).apply { putString("biometric.internal.trust.behavior_authorizer", "true") }, face, true))
    }

    @Test fun supplementalContextCannotAuthorizeAnotherModality() {
        builder.javaClass.getDeclaredField("authenticationStageTypes").apply { isAccessible = true }
            .set(builder, setOf(BiometricType.BIOMETRIC_BEHAVIOR))
        val bundle = BundleBuilder.create(builder)
        assertTrue(permits(bundle, behavior))
        assertFalse(permits(bundle, face, true))
    }

    @Test fun admittedBehaviorCannotChangeFromPrimaryToSupplementalAfterDowngrade() {
        builder.setBiometricTrustPolicy(BiometricTrustPolicy(allowBehaviorOnlySuccess = true))
        val admitted = setOf(BiometricType.BIOMETRIC_BEHAVIOR)
        builder.javaClass.getDeclaredField("admittedAuthenticationTypes").apply { isAccessible = true }
            .set(builder, admitted)
        builder.javaClass.getDeclaredField("authenticationStageTypes").apply { isAccessible = true }
            .set(builder, admitted)
        // Live admission is now empty, as after a formerly trusted behavior provider downgraded.
        val bundle = BundleBuilder.create(builder)
        assertFalse(bundle.getBoolean("biometric.internal.trust.supplemental"))
        assertFalse(permits(bundle, behavior))
    }

    @Test fun enrollmentKeepsItsExistingProviderCaptureContract() {
        builder.setBiometricTrustPolicy(BiometricTrustPolicy(BiometricTrustLevel.SYSTEM_BIOMETRIC))
        builder.javaClass.getDeclaredField("enroll").apply { isAccessible = true }.setBoolean(builder, true)
        assertTrue(permits(BundleBuilder.create(builder), behavior))
    }

    @Test fun actualModuleRejectsDowngradedFallbackBeforeCallingItsSdk() {
        val manager = MutableManager(face)
        val module = module(manager)
        val policy = BundleBuilder.create(builder)
        assertTrue(permits(policy, manager.securityProfile, true))
        manager.profile = SoftwareBiometricSecurityProfile.passiveCompatibility(BiometricType.BIOMETRIC_FACE)
        module.bundle = policy
        val listener = RecordingListener()
        val cancel = androidx.core.os.CancellationSignal()
        try {
            module.authenticate(null, cancel, listener, null)
            assertEquals(0, manager.calls)
            assertEquals(0, listener.successes)
            assertTrue(listener.failures.contains(AuthenticationFailureReason.INSUFFICIENT_TRUST))
        } finally { cancel.cancel() }
    }

    @Test fun enrollmentRollbackDoesNotLogProviderSecrets() {
        val module = module(MutableManager(face))
        module.javaClass.getDeclaredField("enrollBundle").apply { isAccessible = true }.set(module,
            Bundle().apply { putString(BehaviorSample.EXTRA_BEHAVIOR_PHRASE, "never-log-this-phrase") })
        val logger = dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl
        val original = logger.DEBUG
        try {
            logger.DEBUG = true
            org.robolectric.shadows.ShadowLog.clear()
            module.rollbackLastEnroll()
            assertFalse(org.robolectric.shadows.ShadowLog.getLogs().any {
                it.msg.contains("never-log-this-phrase")
            })
        } finally { logger.DEBUG = original }
    }

    @Test fun actualModuleRechecksTrustBeforeDeliveringDelayedSuccess() {
        val manager = MutableManager(face)
        val module = module(manager).apply { bundle = BundleBuilder.create(builder) }
        val listener = RecordingListener()
        val cancel = androidx.core.os.CancellationSignal()
        try {
            module.authenticate(null, cancel, listener, null)
            assertEquals(1, manager.calls)
            manager.profile = SoftwareBiometricSecurityProfile.passiveCompatibility(BiometricType.BIOMETRIC_FACE)
            manager.pending!!.onAuthenticationSucceeded(AbstractSoftwareBiometricManager.AuthenticationResult(null))
            assertEquals(0, listener.successes)
            assertTrue(listener.failures.contains(AuthenticationFailureReason.INSUFFICIENT_TRUST))
        } finally { cancel.cancel() }
    }

    @Test fun providerCannotLowerTheFrozenPolicyByMutatingItsExtras() {
        val manager = MutableManager(face)
        val module = module(manager).apply { bundle = BundleBuilder.create(builder) }
        val listener = RecordingListener()
        val cancel = androidx.core.os.CancellationSignal()
        try {
            module.authenticate(null, cancel, listener, null)
            assertEquals(1, manager.calls)
            manager.receivedExtras!!.putString("biometric.internal.trust.minimum", "COMPATIBILITY")
            manager.profile = SoftwareBiometricSecurityProfile.passiveCompatibility(BiometricType.BIOMETRIC_FACE)
            manager.pending!!.onAuthenticationSucceeded(AbstractSoftwareBiometricManager.AuthenticationResult(null))
            assertEquals(0, listener.successes)
            assertTrue(listener.failures.contains(AuthenticationFailureReason.INSUFFICIENT_TRUST))
        } finally { cancel.cancel() }
    }

    private fun module(manager: MutableManager): SoftwareBiometricModule {
        val runtimeClass = Class.forName("dev.skomlach.biometric.compat.custom.SoftwareBiometricRuntime")
        val runtime = runtimeClass.declaredConstructors.single { it.parameterCount == 4 }
            .apply { isAccessible = true }.newInstance(977001, manager, null, 0)
        return SoftwareBiometricModule::class.java.declaredConstructors.single { it.parameterCount == 3 }
            .apply { isAccessible = true }.newInstance(BiometricMethod.CUSTOM_FACE, runtime, null) as SoftwareBiometricModule
    }

    private class MutableManager(var profile: SoftwareBiometricSecurityProfile) : AbstractSoftwareBiometricManager() {
        override val biometricType = BiometricType.BIOMETRIC_FACE
        override val securityProfile get() = profile
        override val trustedCaptureForAuthentication = true
        var calls = 0
        var pending: AuthenticationCallback? = null
        var receivedExtras: Bundle? = null
        override fun getTimeoutMessage() = "timeout"
        override fun resetLockOut() {}
        override fun resetPermanentLockOut() {}
        override fun getPermissions() = emptyList<String>()
        override fun isHardwareDetected() = true
        override fun hasEnrolledBiometric() = true
        override fun getManagers(): Set<Any> = setOf(this)
        override fun remove(extra: Bundle?) {}
        override fun getEnrollBundle(name: String?) = Bundle()
        override fun getEnrolls() = listOf("profile")
        override fun getEnrollmentSnapshot() = SoftwareBiometricEnrollment.Available(listOf("profile"))
        override fun authenticate(crypto: CryptoObject?, flags: Int, cancel: CancellationSignal?,
                                  callback: AuthenticationCallback?, handler: Handler?, extra: Bundle?) {
            calls++; pending = callback; receivedExtras = extra
        }
    }

    private class RecordingListener : AuthenticationListener {
        var successes = 0
        val failures = mutableListOf<AuthenticationFailureReason?>()
        override fun onHelp(msg: CharSequence?) {}
        override fun onSuccess(moduleTag: Int, biometricCryptoObject: BiometricCryptoObject?) { successes++ }
        override fun onFailure(moduleTag: Int, reason: AuthenticationFailureReason?, description: CharSequence?) { failures.add(reason) }
        override fun onCanceled(moduleTag: Int, reason: AuthenticationFailureReason?, description: CharSequence?) {}
    }
}
