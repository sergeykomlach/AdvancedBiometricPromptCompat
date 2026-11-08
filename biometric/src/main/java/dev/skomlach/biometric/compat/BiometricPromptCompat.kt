/*
 *  Copyright (c) 2023 Sergey Komlach aka Salat-Cx65; Original project https://github.com/Salat-Cx65/AdvancedBiometricPromptCompat
 *  All rights reserved.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package dev.skomlach.biometric.compat

import android.Manifest
import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.CallSuper
import androidx.annotation.ColorInt
import androidx.annotation.MainThread
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Observer
import dev.skomlach.biometric.compat.crypto.CryptographyManager
import dev.skomlach.biometric.compat.custom.AbstractSoftwareBiometricManager
import dev.skomlach.biometric.compat.custom.SoftwareBiometricPromptRegistry
import dev.skomlach.biometric.compat.engine.BiometricMethod
import dev.skomlach.biometric.compat.engine.LegacyBiometric
import dev.skomlach.biometric.compat.engine.LegacyBiometricInitListener
import dev.skomlach.biometric.compat.engine.core.interfaces.BiometricModule
import dev.skomlach.biometric.compat.engine.internal.EnrollmentRollbackSession
import dev.skomlach.biometric.compat.engine.internal.SoftwareBiometricModule
import dev.skomlach.biometric.compat.impl.BiometricPromptApi28Impl
import dev.skomlach.biometric.compat.impl.BiometricPromptGenericImpl
import dev.skomlach.biometric.compat.impl.BehaviorFirstBiometricPromptImpl
import dev.skomlach.biometric.compat.impl.BiometricPromptSilentImpl
import dev.skomlach.biometric.compat.impl.IBiometricPromptImpl
import dev.skomlach.biometric.compat.impl.canPrepareSoftwareAlongsideNative
import dev.skomlach.biometric.compat.impl.credentials.CredentialsRequestFragment
import dev.skomlach.biometric.compat.impl.dialogs.UntrustedAccessibilityFragment
import dev.skomlach.biometric.compat.impl.permissions.InitiateSystemBiometricEnrollFragment
import dev.skomlach.biometric.compat.utils.BiometricErrorLockoutPermanentFix
import dev.skomlach.biometric.compat.utils.BiometricTitle
import dev.skomlach.biometric.compat.utils.DeviceUnlockedReceiver
import dev.skomlach.biometric.compat.utils.DevicesWithKnownBugs
import dev.skomlach.biometric.compat.utils.DialogMainColor
import dev.skomlach.biometric.compat.utils.HardwareAccessImpl
import dev.skomlach.biometric.compat.utils.SensorPrivacyCheck
import dev.skomlach.biometric.compat.utils.TruncatedTextFix
import dev.skomlach.biometric.compat.impl.dialogs.SystemBiometricDialogResources
import dev.skomlach.biometric.compat.utils.WideGamutBug
import dev.skomlach.biometric.compat.utils.activityView.ActivityViewWatcher
import dev.skomlach.biometric.compat.utils.activityView.ForegroundFeedbackSession
import dev.skomlach.biometric.compat.custom.BiometricFeedbackOptions
import dev.skomlach.biometric.compat.utils.activityView.IconStateHelper
import dev.skomlach.biometric.compat.utils.appstate.AppBackgroundDetector
import dev.skomlach.biometric.compat.utils.hardware.BiometricPromptHardware
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl
import dev.skomlach.biometric.compat.utils.notification.BiometricNotificationManager
import dev.skomlach.biometric.compat.utils.themes.DarkLightThemes
import dev.skomlach.common.contextprovider.AndroidContext
import dev.skomlach.common.device.DeviceInfo
import dev.skomlach.common.logging.LogCat
import dev.skomlach.common.misc.ExecutorHelper
import dev.skomlach.common.misc.Utils
import dev.skomlach.common.multiwindow.MultiWindowSupport
import dev.skomlach.common.permissions.PermissionUtils
import dev.skomlach.common.permissionui.PermissionsFragment
import dev.skomlach.common.permissionui.notification.NotificationPermissionsHelper
import dev.skomlach.common.protection.A11yDetection
import dev.skomlach.common.protection.HookDetection
import dev.skomlach.common.storage.SharedPreferenceProvider
import dev.skomlach.common.statusbar.StatusBarTools
import dev.skomlach.common.translate.LocalizationHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.nio.charset.Charset
import java.util.Collections
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class BiometricPromptCompat private constructor(private val builder: Builder) {
    companion object {
        private const val EXTRA_VOICE_SAMPLE_RATE = "voice.sample_rate"
        private const val EXTRA_VOICE_PCM_FLOAT = "voice.pcm_float"
        private const val EXTRA_VOICE_EMBEDDING = "voice.embedding"
        private const val EXTRA_VOICE_PHRASE = "voice.phrase"
        private const val EXTRA_VOICE_SAMPLE_COUNT = "voice.sample_count"
        private const val MAX_VOICE_SAMPLE_COUNT = 5
        private val reference = AtomicBoolean(false)

        /** Best-effort local signal, not attestation. A positive detection lasts for this process. */
        @JvmStatic
        fun hasDetectedRuntimeHooks(): Boolean = reference.get()

        private fun scanRuntimeHooks(onComplete: () -> Unit = {}) {
            HookDetection.detect(object : HookDetection.HookDetectionListener {
                override fun onDetected(flag: Boolean) {
                    if (flag) reference.set(true)
                    onComplete()
                }
            })
        }
        var API_ENABLED = true
            private set

        init {
            if (API_ENABLED) {
                if (!AppCompatDelegate.isCompatVectorFromResourcesEnabled())
                    AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)
            }
        }

        @JvmStatic
        fun apiEnabled(enabled: Boolean) {
            API_ENABLED = enabled
        }

        private val availableAuthRequestsLock = Any()
        private var availableAuthRequests: List<BiometricAuthRequest>? = null

        internal fun invalidateAvailableAuthRequests() {
            synchronized(availableAuthRequestsLock) {
                availableAuthRequests = null
            }
        }

        private fun discoverAvailableAuthRequests(): List<BiometricAuthRequest> {
            val requests = HashSet<BiometricAuthRequest>()
            var biometricAuthRequest = BiometricAuthRequest.default()
            if (BiometricManagerCompat.isHardwareDetected(biometricAuthRequest)) {
                requests.add(biometricAuthRequest)
            }
            for (api in BiometricApi.entries) {
                if (api == BiometricApi.AUTO) continue
                for (type in BiometricType.entries) {
                    if (type == BiometricType.BIOMETRIC_ANY) continue
                    biometricAuthRequest = BiometricAuthRequest.default().withApi(api).withType(type)
                    if (BiometricManagerCompat.isHardwareDetected(biometricAuthRequest)) {
                        requests.add(
                            BiometricAuthRequest.default().withApi(BiometricApi.AUTO).withType(type)
                        )
                        requests.add(biometricAuthRequest)
                    }
                }
            }
            return requests.toList()
        }

        @JvmStatic
        fun getAvailableAuthRequests(): List<BiometricAuthRequest> {
            if (!API_ENABLED) return emptyList()
            val candidates = synchronized(availableAuthRequestsLock) {
                availableAuthRequests ?: discoverAvailableAuthRequests().also {
                    // An early caller can see cached hardware state before software is registered.
                    // Keep that provisional result out of the process-wide candidate cache.
                    if (isInitialized && it.isNotEmpty()) availableAuthRequests = it
                }
            }
            return candidates.filter { request ->
                if (request.type == BiometricType.BIOMETRIC_ANY) return@filter true
                val hardware = LegacyBiometric.getSelectedBiometricModule(
                    request.type, BiometricProviderType.HARDWARE, enroll = true
                )
                val fallback = LegacyBiometric.getSelectedBiometricModule(
                    request.type, request.provider, enroll = true
                )
                val systemHardware = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                    BiometricManagerCompat.getAuthSnapshot(request.withApi(BiometricApi.BIOMETRIC_API)
                        .withProvider(BiometricProviderType.HARDWARE)).state.hardwareDetected
                val preferSystemFace = request.type == BiometricType.BIOMETRIC_FACE &&
                    deviceInfo?.model?.let { isSamsungDeviceModel(it) ||
                        BiometricPromptHardware.PixelModelChecker.isPixel8OrNewer(it) } == true
                isAuthRequestRouteSupported(
                    request, systemHardware, hardware != null, fallback != null,
                    preferSystemFace, (fallback?.priority ?: Int.MIN_VALUE) > BiometricModule.PRIORITY_SYSTEM_HARDWARE
                )
            }.sortedBy {
                it.toString()
            }
        }

        private fun isHardwareEnrollmentNeeded(request: BiometricAuthRequest): Boolean {
            val snapshot = BiometricManagerCompat.getAuthSnapshot(
                request.withProvider(provider = BiometricProviderType.HARDWARE)
            )
            return snapshot.readyForEnroll && !snapshot.state.enrolled
        }

        private val automaticSystemEnrollment = ThreadLocal<Boolean>()

        private fun deferSystemEnrollmentDecision(): Boolean {
            // Kotlin evaluates the Boolean default before calling setupBiometric on this thread.
            automaticSystemEnrollment.set(true)
            return true
        }

        @JvmStatic
        fun logging(
            enabled: Boolean
        ) {
            if (!API_ENABLED)
                return
//            AbstractBiometricModule.DEBUG_MANAGERS = enabled
            LogCat.DEBUG = enabled
            BiometricLoggerImpl.DEBUG = enabled
        }

        private val pendingTasks: MutableList<Runnable?> =
            Collections.synchronizedList(ArrayList<Runnable?>())
        private var isBiometricInit = AtomicBoolean(false)
        var isInitialized = false
            get() = isBiometricInit.get()
            private set
        private var initInProgress = AtomicBoolean(false)
        var deviceInfo: DeviceInfo? = null
        private var authFlowInProgress = AtomicBoolean(false)
        private val authFlowGeneration = AtomicLong(0)
        var initStart = System.currentTimeMillis()
        private val configurationObserverRegistered = AtomicBoolean(false)
        private val prefetchLock = Any()
        private var prefetchJob: Job? = null

        internal fun init(execute: Runnable? = null) {
            if (!API_ENABLED)
                return
            if (Looper.getMainLooper().thread !== Thread.currentThread())
                throw IllegalThreadStateException("Main Thread required")

            SystemBiometricDialogResources.warmUp(AndroidContext.appContext)

            if (isInitialized) {
                BiometricLoggerImpl.d("BiometricPromptCompat.init() - ready")
                execute?.let { ExecutorHelper.post(it) }
            } else {
                if (initInProgress.get()) {
                    BiometricLoggerImpl.d("BiometricPromptCompat.init() - pending")
                    pendingTasks.add(execute)
                } else {
                    initStart = System.currentTimeMillis()
                    isBiometricInit.set(false)
                    initInProgress.set(true)
                    pendingTasks.add(execute)
                    BiometricLoggerImpl.d("BiometricPromptCompat.init() for ${AndroidContext.appContext.packageName}")
                    scanRuntimeHooks()
                    startBiometricInit()
                    ExecutorHelper.startOnBackground {
                        DeviceUnlockedReceiver.registerDeviceUnlockListener()
                    }
                    SharedPreferenceProvider.warmUpProtectedStorage()
                    prefetchLocalizationStrings()
                }
            }
        }

        private fun prefetchLocalizationStrings() {
            try {
                val stringIds: Array<Int> =
                    R.string::class.java
                        .fields
                        .asSequence()
                        .filter { it.type == Int::class.javaPrimitiveType }
                        .filter { it.name.startsWith("biometriccompat_") }
                        .mapNotNull { field ->
                            try {
                                field.getInt(null)
                            } catch (_: Throwable) {
                                null
                            }
                        }
                        .toList()
                        .toTypedArray()
                LogCat.log("BiometricPromptCompat", "LocalizationHelper.prefetch")
                scheduleLocalizationPrefetch(stringIds)
                if (configurationObserverRegistered.compareAndSet(false, true)) {
                    ExecutorHelper.post {
                        AndroidContext.configurationLiveData.observeForever {
                            SystemBiometricDialogResources.warmUp(AndroidContext.appContext)
                            LogCat.log(
                                "BiometricPromptCompat",
                                "observeForever -> LocalizationHelper.prefetch"
                            )
                            scheduleLocalizationPrefetch(stringIds)
                        }
                    }
                }
            } catch (e: Throwable) {
                LogCat.logException(e)
            }
        }

        private fun scheduleLocalizationPrefetch(stringIds: Array<Int>) {
            synchronized(prefetchLock) {
                prefetchJob?.cancel()
                prefetchJob = ExecutorHelper.scope.launch {
                    try {
                        LocalizationHelper.prefetch(
                            AndroidContext.appContext,
                            *stringIds
                        )
                    } catch (e: Throwable) {
                        LogCat.logException(e, "BiometricPromptCompat.prefetch")
                    }
                }
            }
        }

        @MainThread
        @JvmStatic
        private fun startBiometricInit() {
            LegacyBiometric.init(object : LegacyBiometricInitListener {
                override fun initFinished(
                    method: BiometricMethod,
                    module: BiometricModule?
                ) {
                }

                override fun onBiometricReady() {
                    completeBiometricInitialization(
                        dispatchBackground = ExecutorHelper::startOnBackground,
                        dispatchMain = ExecutorHelper::post,
                        loadSoftware = {
                            BiometricManagerCompat.loadNonHardwareBiometrics()
                            runCatching { BiometricErrorLockoutPermanentFix.resetIfRebooted() }
                                .onFailure { BiometricLoggerImpl.e(it) }
                        }
                    ) {
                        BiometricLoggerImpl.e("BiometricPromptCompat initialized in ${System.currentTimeMillis() - initStart} ms")
                        isBiometricInit.set(true)
                        initInProgress.set(false)

                        val tasks = synchronized(pendingTasks) {
                            pendingTasks.toList().also { pendingTasks.clear() }
                        }
                        for (task in tasks) {
                            task?.let { ExecutorHelper.post(it) }
                        }
                    }
                }
            })
        }
    }


    private lateinit var oldDescription: CharSequence
    private lateinit var oldTitle: CharSequence
    private val implementationCache = AuthFlowRouteCache<Unit, IBiometricPromptImpl>()
    private val backgroundDetectorCache = AuthFlowRouteCache<Unit, AppBackgroundDetector>()
    private val impl: IBiometricPromptImpl get() = implementationCache.getOrPut(Unit) {
        if (!builder.enroll && BiometricType.BIOMETRIC_BEHAVIOR in builder.getAuthenticationExecutionTypes()) {
            BehaviorFirstBiometricPromptImpl(builder, ::createBackend)
        } else createBackend()
    }

    private fun createBackend(): IBiometricPromptImpl {
        val isBiometricPrompt = shouldUseBiometricPromptImpl()
        BiometricLoggerImpl.d(
            "BiometricPromptCompat.IBiometricPromptImpl - " +
                    "$isBiometricPrompt"
        )
        val iBiometricPromptImpl = if (builder.isSilentAuthEnabled())
            BiometricPromptSilentImpl(builder)
        else if (isBiometricPrompt) {
            BiometricPromptApi28Impl(builder)
        } else {
            BiometricPromptGenericImpl(builder)
        }
        return iBiometricPromptImpl
    }
    private val appBackgroundDetector: AppBackgroundDetector get() = backgroundDetectorCache.getOrPut(Unit) {
        AppBackgroundDetector(impl) {
            if (!builder.forceDeviceCredential()) {
                BiometricLoggerImpl.e("BiometricPromptCompat.AppBackgroundDetector()")
                cancelAuthentication()
            }
        }
    }
    private var startTs = 0L
    private var startTsImpl = 0L
    private val authCanceled = AtomicBoolean(false)
    private val ownedAuthFlowGeneration = AtomicLong(-1L)
    private var activeCompletion: AuthFlowCompletion? = null
    private var activeAuthCallback: AuthenticationCallback? = null
    private var implementationStarted = false
    private var awaitingSystemEnrollment = false
    private var cancelOwnedUi: (() -> Unit)? = null


    fun setupBiometric(
        callbackOuter: AuthenticationCallback,
        enrollNewHardwareBiometric: Boolean = deferSystemEnrollmentDecision()
    ) {
        val decideSystemEnrollment = automaticSystemEnrollment.get() == true
        automaticSystemEnrollment.remove()
        val callback = callbackOuter.withMissingPermissionDescriptions()
        if (!authFlowInProgress.tryStartAuthFlow()) {
            callback.onCanceled(builder.availableTypesForCancellation().map {
                AuthenticationResult(
                    it,
                    reason = AuthenticationFailureReason.BIOMETRIC_ALREADY_STARTED
                )
            }.toSet())
            return
        }
        val authFlowId = authFlowGeneration.incrementAndGet()
        ownedAuthFlowGeneration.set(authFlowId)
        implementationCache.beginFlow(authFlowId)
        backgroundDetectorCache.beginFlow(authFlowId)
        authCanceled.set(false)
        // Configure mutable builder state only after this call owns the shared flow gate.
        builder.enroll = true
        awaitingSystemEnrollment = false
        builder.resetEnrollSessionState()
        builder.beginAuthFlow(authFlowId)
        builder.authFlowActive = { isCurrentAuthFlow(authFlowId) }
        activeAuthCallback = callback
        activeCompletion = null
        implementationStarted = false
        if (!API_ENABLED) {
            val failureResults = builder.availableTypesForCancellation().map {
                    AuthenticationResult(
                        it,
                        reason = AuthenticationFailureReason.NO_HARDWARE,
                        description = biometricApiDisabledDescription()
                    )
                }.toSet()
            dispatchAfterFlowFinished(
                finishFlow = { finishAuthFlow(authFlowId) },
                dispatch = { callback.onFailed(failureResults) }
            )
            return
        }
        readFlowStatus(authFlowId, callback, read = {
            val request = builder.getBiometricAuthRequest()
            val openSettings = if (decideSystemEnrollment) {
                isHardwareEnrollmentNeeded(request)
            } else {
                enrollNewHardwareBiometric &&
                        BiometricManagerCompat.getAuthSnapshot(
                            request.withProvider(BiometricProviderType.HARDWARE)
                        ).state.hardwareDetected
            }
            val enrolled = if (openSettings) builder.getEnrolledHardwareScopeTypes() else emptySet()
            openSettings to enrolled
        }) { (openSettings, enrolled) ->
            awaitingSystemEnrollment = openSettings
            continueSetupWhenReady(callback, authFlowId, openSettings, enrolled)
        }
    }

    private fun continueSetupWhenReady(
        callback: AuthenticationCallback,
        authFlowId: Long,
        requestSystemEnrollment: Boolean,
        enrolledHardwareBeforeSystemSetup: Set<BiometricType>
    ) {
        val continueSetup: () -> Unit = {
            builder.invalidateSelectedRoutes()
            awaitingSystemEnrollment = false
            readFlowStatus(authFlowId, callback, read = {
                val enrolled = builder.getEnrolledHardwareScopeTypes()
                enrolled to builder.hasSoftwareEnrollTargets()
            }) { (enrolledHardware, hasSoftwareTargets) ->
                val newlyEnrolledHardware = if (requestSystemEnrollment) {
                    enrolledHardware - enrolledHardwareBeforeSystemSetup
                } else emptySet()
                val continuation = resolveBiometricSetupContinuation(
                    hardwareEnrollmentStillRequired = requestSystemEnrollment && enrolledHardware.isEmpty(),
                    hasSoftwareEnrollmentTargets = hasSoftwareTargets,
                    hardwareEnrolledThisRun = newlyEnrolledHardware.isNotEmpty(),
                    requiresCrypto = builder.getCryptographyPurpose() != null
                )
                BiometricLoggerImpl.d("BiometricPromptCompat.setup continuation=$continuation")
                when (continuation) {
                    BiometricSetupContinuation.CANCELED -> {
                        val results = emptyEffectiveBiometricCancellationResults(builder.getEnrollScopeTypes())
                        dispatchAfterFlowFinished(
                            finishFlow = { finishAuthFlow(authFlowId) },
                            dispatch = { callback.onCanceled(results) }
                        )
                    }
                    BiometricSetupContinuation.COMPLETE_SYSTEM_ENROLLMENT -> {
                        builder.markEnrollConfirmedTypes(newlyEnrolledHardware)
                        finishAndDispatchEnrollTerminalOutcome(
                            authFlowId = authFlowId,
                            callback = callback,
                            successResults = enrolledHardware.mapTo(LinkedHashSet()) { AuthenticationResult(it) }
                        )
                    }
                    BiometricSetupContinuation.ENROLL_SOFTWARE,
                    BiometricSetupContinuation.CONFIRM_HARDWARE -> {
                        // Hardware setup confirms existing templates using the ordinary auth route.
                        // Only software providers receive the flag that creates a new enrollment.
                        builder.enroll = continuation == BiometricSetupContinuation.ENROLL_SOFTWARE
                        builder.invalidateSelectedRoutes()
                        runAuthPreflight(
                            callback = callback,
                            authFlowId = authFlowId,
                            authTask = {
                                // A denied software permission may leave only hardware confirmation.
                                if (builder.enroll && !builder.hasSoftwareEnrollTargets()) {
                                    builder.enroll = false
                                    builder.invalidateSelectedRoutes()
                                    readFlowStatus(authFlowId, callback, builder::prepareRoutes) {
                                        startAuth(callback, authFlowId, preflightCompleted = true)
                                    }
                                } else {
                                    startAuth(callback, authFlowId, preflightCompleted = true)
                                }
                            },
                            requestNotificationPermission = true
                        )
                    }
                }
            }
        }
        waitUntilReadyForAuth(callback, System.currentTimeMillis(), authFlowId) { _, _ ->
            if (requestSystemEnrollment) {
                val activity = builder.getActivity()
                if (activity == null) {
                    cancelAuthentication()
                } else {
                    cancelOwnedUi = InitiateSystemBiometricEnrollFragment.launchOwned(
                        activity, builder.getBiometricAuthRequest()
                    ) { returned ->
                        if (isCurrentAuthFlow(authFlowId)) {
                            cancelOwnedUi = null
                            if (returned) continueSetup() else cancelAuthentication()
                        }
                    }
                }
            } else {
                continueSetup.invoke()
            }
        }
    }


    private fun finishAndDispatchEnrollTerminalOutcome(
        authFlowId: Long,
        callback: AuthenticationCallback,
        failureResults: Set<AuthenticationResult> = emptySet(),
        canceledResults: Set<AuthenticationResult> = emptySet(),
        successResults: Set<AuthenticationResult>? = null
    ) {
        val confirmedTypes = builder.getConfirmedEnrollTypes()
        val rollbackEligibleTypes = builder.getRollbackEligibleEnrollTypes()
        readFlowStatus(authFlowId, callback, read = {
            resolveEnrollSessionOutcome(
                confirmation = builder.getBiometricAuthRequest().confirmation,
                scopeTypes = builder.getCurrentEnrollCompletionTypes().ifEmpty { builder.getEnrollScopeTypes() },
                successResults = successResults ?: builder.getPreSatisfiedEnrollResults(),
                confirmedTypes = confirmedTypes,
                failureResults = failureResults,
                canceledResults = canceledResults,
                rollbackEligibleTypes = rollbackEligibleTypes,
                terminal = true
            )
        }) { outcome ->
            val dispatchOutcome = {
                if (isCurrentAuthFlow(authFlowId)) {
                    val hookFailure = if (outcome.status == EnrollTerminalStatus.SUCCEEDED) {
                        callback.hookFailureOrNull(outcome.results)
                    } else null
                    dispatchAfterFlowFinished(
                        finishFlow = { finishAuthFlow(authFlowId) },
                        dispatch = {
                            when (outcome.status) {
                                EnrollTerminalStatus.SUCCEEDED -> {
                                    if (hookFailure != null) callback.onFailed(hookFailure)
                                    else callback.onSucceeded(outcome.results)
                                }
                                EnrollTerminalStatus.FAILED -> callback.onFailed(outcome.results)
                                EnrollTerminalStatus.CANCELED -> callback.onCanceled(outcome.results)
                                EnrollTerminalStatus.CONTINUE -> callback.onFailed(canceledResults)
                            }
                        }
                    )
                }
            }
            if (outcome.status == EnrollTerminalStatus.SUCCEEDED) scanRuntimeHooks(dispatchOutcome)
            else dispatchOutcome()
        }
    }

    fun authenticate(callbackOuter: AuthenticationCallback) {
        val authStartedAt = SystemClock.uptimeMillis()
        BiometricLoggerImpl.d("BiometricPromptCompat.authenticate() stage1")
        startTs = System.currentTimeMillis()
        val callback = callbackOuter.withMissingPermissionDescriptions()
        if (!authFlowInProgress.tryStartAuthFlow()) {
            callback.onCanceled(builder.availableTypesForCancellation().map {
                AuthenticationResult(
                    it,
                    reason = AuthenticationFailureReason.BIOMETRIC_ALREADY_STARTED
                )
            }.toSet())
            return
        }
        val authFlowId = authFlowGeneration.incrementAndGet()
        BiometricLoggerImpl.d {
            "BiometricDialogTiming: auth_start uptimeMs=$authStartedAt flow=$authFlowId"
        }
        ownedAuthFlowGeneration.set(authFlowId)
        implementationCache.beginFlow(authFlowId)
        backgroundDetectorCache.beginFlow(authFlowId)
        authCanceled.set(false)
        builder.enroll = false
        awaitingSystemEnrollment = false
        builder.beginAuthFlow(authFlowId)
        builder.authFlowActive = { isCurrentAuthFlow(authFlowId) }
        activeAuthCallback = callback
        activeCompletion = null
        implementationStarted = false

        if (!API_ENABLED) {
            val failureResults = builder.availableTypesForCancellation().map {
                    AuthenticationResult(
                        it,
                        reason = AuthenticationFailureReason.NO_HARDWARE,
                        description = biometricApiDisabledDescription()
                    )
                }.toSet()
            dispatchAfterFlowFinished(
                finishFlow = { finishAuthFlow(authFlowId) },
                dispatch = { callback.onFailed(failureResults) }
            )
            return
        }
        if (builder.getActivity() == null) {
            BiometricLoggerImpl.e(
                IllegalStateException(),
                LocalizationHelper.getLocalizedString(
                    builder.getContext(),
                    R.string.biometriccompat_window_error
                )

            )
            val failureResults = builder.availableTypesForCancellation().map {
                AuthenticationResult(
                    it,
                    reason = AuthenticationFailureReason.INTERNAL_ERROR,
                    description = LocalizationHelper.getLocalizedString(
                        builder.getContext(),
                        R.string.biometriccompat_window_error
                    )
                )
            }.toSet()
            dispatchAfterFlowFinished(
                finishFlow = { finishAuthFlow(authFlowId) },
                dispatch = { callback.onFailed(failureResults) }
            )
            return
        }
        BiometricLoggerImpl.d("BiometricPromptCompat.authenticate() stage2")
        waitUntilReadyForAuth(callback, System.currentTimeMillis(), authFlowId)
    }

    private fun waitUntilReadyForAuth(
        callback: AuthenticationCallback,
        startTime: Long,
        authFlowId: Long,
        onReady: (AuthenticationCallback, Long) -> Unit = { readyCallback, readyFlowId ->
            startAuth(readyCallback, readyFlowId)
        }
    ) {
        if (authFlowGeneration.get() != authFlowId) {
            return
        }
        if (!isCurrentAuthFlow(authFlowId)) {
            dispatchAfterFlowFinished(
                finishFlow = { finishAuthFlow(authFlowId) },
                dispatch = { callback.onCanceled(emptyEffectiveBiometricCancellationResults(builder.availableTypesForCancellation())) }
            )
            return
        }
        val timeout = System.currentTimeMillis() - startTime >= TimeUnit.SECONDS.toMillis(5)
        if (!timeout && !isInitialized) {
            ExecutorHelper.postDelayed(
                { waitUntilReadyForAuth(callback, startTime, authFlowId, onReady) },
                50
            )
            return
        }
        readFlowStatus(authFlowId, callback, read = {
            TruncatedTextFix.prepareDiskCache()
            builder.getAllAvailableTypes() to builder.readCredentialStatus()
        }) { (types, credentialStatus) ->
            builder.acceptPreparedStatus(types, credentialStatus)
            if (!timeout && types.isNotEmpty() && !builder.isTruncateChecked()) {
                ExecutorHelper.postDelayed(
                    { waitUntilReadyForAuth(callback, startTime, authFlowId, onReady) }, 50
                )
            } else {
                if (!builder.enroll && BiometricType.BIOMETRIC_FINGERPRINT in types &&
                    WideGamutBug.unsupportedColorMode(builder.getActivity())
                ) {
                    val description = LocalizationHelper.getLocalizedString(
                        builder.getContext(), R.string.biometriccompat_widegamut_error
                    )
                    val failures = types.mapTo(LinkedHashSet()) {
                        AuthenticationResult(it, reason = AuthenticationFailureReason.HARDWARE_UNAVAILABLE, description = description)
                    }
                    dispatchAfterFlowFinished(
                        finishFlow = { finishAuthFlow(authFlowId) },
                        dispatch = { callback.onFailed(failures) }
                    )
                } else {
                    continueAuthenticationAfterReadiness(callback, timeout, authFlowId, onReady)
                }
            }
        }
    }

    private fun <T> readFlowStatus(
        authFlowId: Long,
        callback: AuthenticationCallback,
        read: () -> T,
        onSuccess: (T) -> Unit
    ) {
        readOwnedAuthStatus(ExecutorHelper::startOnBackground, ExecutorHelper::post,
            { isCurrentAuthFlow(authFlowId) }, read) { result ->
            result.fold(onSuccess) { error ->
                BiometricLoggerImpl.e(error)
                val failure = setOf(AuthenticationResult(
                    builder.getBiometricAuthRequest().type,
                    reason = AuthenticationFailureReason.INTERNAL_ERROR,
                    description = biometricInternalErrorDescription()
                ))
                val completion = activeCompletion
                if (completion != null) {
                    completion.finish { callback.onFailed(failure) }
                } else {
                    dispatchAfterFlowFinished({ finishAuthFlow(authFlowId) }, { callback.onFailed(failure) })
                }
            }
        }
    }

    private fun continueAuthenticationAfterReadiness(
        callback: AuthenticationCallback,
        timeout: Boolean,
        authFlowId: Long,
        onReady: (AuthenticationCallback, Long) -> Unit
    ) {
        if (timeout || builder.getBiometricAuthRequest().provider == BiometricProviderType.HARDWARE) {
            continueAfterInitialSoftwarePreparation(callback, timeout, authFlowId, onReady)
            return
        }
        ExecutorHelper.startOnBackground {
            if (!isCurrentAuthFlow(authFlowId)) return@startOnBackground
            if (canStartNativeBeforeSoftwarePreparation()) {
                ExecutorHelper.post {
                    if (!isCurrentAuthFlow(authFlowId)) return@post
                    builder.deferSoftwarePreparation = true
                    continueAfterInitialSoftwarePreparation(callback, false, authFlowId, onReady)
                }
                return@startOnBackground
            }
            SoftwareBiometricPromptRegistry.prepareInitial(
                builder.getBiometricAuthRequest().type,
                builder.getContext(),
                { isCurrentAuthFlow(authFlowId) }
            ) {
                ExecutorHelper.post {
                    if (isCurrentAuthFlow(authFlowId)) {
                        builder.invalidateAvailableTypes()
                        continueAfterInitialSoftwarePreparation(callback, false, authFlowId, onReady)
                    }
                }
            }
        }
    }

    private data class InitialAvailability(
        val types: Set<BiometricType>,
        val blocker: AuthenticationFailureReason?,
        val lockedOut: Boolean
    )

    private fun continueAfterInitialSoftwarePreparation(
        callback: AuthenticationCallback,
        timeout: Boolean,
        authFlowId: Long,
        onReady: (AuthenticationCallback, Long) -> Unit
    ) {
        readFlowStatus(authFlowId, callback, read = {
            val types = builder.prepareRoutes()
            val blocker = if (types.isEmpty()) checkHardware().takeUnless {
                it == AuthenticationFailureReason.UNKNOWN ||
                    (it == AuthenticationFailureReason.HARDWARE_UNAVAILABLE && builder.forceDeviceCredential())
            } else null
            InitialAvailability(types, blocker, !timeout && blocker == null && builder.areSelectedTypesLockedOut())
        }) { availability ->
            val blocker = availability.blocker
            val failureResults = when {
                blocker != null -> setOf(AuthenticationResult(
                    BiometricType.BIOMETRIC_ANY,
                    reason = blocker,
                    description = if (blocker == AuthenticationFailureReason.MISSING_PERMISSIONS_ERROR) {
                        getMissingPermissionsDescription()
                    } else null
                ))
                failIfNoEffectiveBiometrics(callback, authFlowId) -> return@readFlowStatus
                timeout -> availability.types.mapTo(LinkedHashSet()) {
                    AuthenticationResult(
                        it,
                        reason = AuthenticationFailureReason.NOT_INITIALIZED_ERROR,
                        description = LocalizationHelper.getLocalizedString(
                            builder.getContext(), R.string.biometriccompat_long_init_error
                        )
                    )
                }
                availability.lockedOut -> availability.types.mapTo(LinkedHashSet()) {
                    AuthenticationResult(it, reason = AuthenticationFailureReason.LOCKED_OUT)
                }
                else -> {
                    onReady(callback, authFlowId)
                    return@readFlowStatus
                }
            }
            dispatchAfterFlowFinished(
                finishFlow = { finishAuthFlow(authFlowId) },
                dispatch = { callback.onFailed(failureResults) }
            )
        }
    }

    private fun AuthenticationCallback.withMissingPermissionDescriptions(): AuthenticationCallback {
        val delegate = this
        return object : AuthenticationCallback() {
            override fun onSucceeded(confirmed: Set<AuthenticationResult>) {
                // The flow owner has already decided before crypto/enrollment and cleanup.
                // Do not reevaluate a mutable detector after committing that decision.
                delegate.onSucceeded(confirmed)
            }

            override fun onCanceled(canceled: Set<AuthenticationResult>) {
                delegate.onCanceled(canceled.withMissingPermissionDescriptions())
            }

            override fun onFailed(failure: Set<AuthenticationResult>) {
                delegate.onFailed(failure.withMissingPermissionDescriptions())
            }

            override fun onSupplementalResult(result: SupplementalBiometricResult) {
                delegate.onSupplementalResult(result)
            }

            override fun onUIOpened() {
                delegate.onUIOpened()
            }

            override fun onUIClosed() {
                delegate.onUIClosed()
            }
        }
    }

    private fun Set<AuthenticationResult>.withMissingPermissionDescriptions(): Set<AuthenticationResult> {
        if (none { it.reason == AuthenticationFailureReason.MISSING_PERMISSIONS_ERROR && it.description.isNullOrBlank() }) {
            return this
        }
        val description = getMissingPermissionsDescription()
        return map { it.withMissingPermissionDescription(description) }.toSet()
    }

    private fun checkHardware(): AuthenticationFailureReason {
        val biometricAuthRequest = builder.getBiometricAuthRequest()
        val authSnapshot = BiometricManagerCompat.getAuthSnapshot(
            biometricAuthRequest,
            ignoreCameraCheck = false
        )
        val ignoreMissedSoftwareBiometric =
            builder.enroll && BiometricManagerCompat.isBiometricReadyForEnroll(
                biometricAuthRequest
                    .withProvider(provider = BiometricProviderType.SOFTWARE)
            )

        if (!authSnapshot.state.hardwareDetected) {
            BiometricLoggerImpl.e("BiometricPromptCompat.checkHardware - isHardwareDetected")
            return AuthenticationFailureReason.NO_HARDWARE
        } else if (!ignoreMissedSoftwareBiometric && !authSnapshot.state.enrolled
        ) {
            BiometricLoggerImpl.e("BiometricPromptCompat.checkHardware - hasEnrolled")
            return AuthenticationFailureReason.NO_BIOMETRICS_REGISTERED
        } else if (!PermissionUtils.INSTANCE.hasSelfPermissions(
                getUsedPermissionsForSelectedModules()
            )
        ) {
            BiometricLoggerImpl.e("BiometricPromptCompat.checkHardware - missed permissions")
            return AuthenticationFailureReason.MISSING_PERMISSIONS_ERROR
        } else if (authSnapshot.state.lockedOut
        ) {
            BiometricLoggerImpl.e("BiometricPromptCompat.checkHardware - isLockOut")
            return AuthenticationFailureReason.LOCKED_OUT
        } else if (authSnapshot.state.permanentlyLocked
        ) {
            BiometricLoggerImpl.e("BiometricPromptCompat.checkHardware - isBiometricSensorPermanentlyLocked")
            return AuthenticationFailureReason.HARDWARE_UNAVAILABLE
        } else
            return AuthenticationFailureReason.UNKNOWN
    }

    private fun startAuth(
        callbackOuter: AuthenticationCallback,
        authFlowId: Long,
        preflightCompleted: Boolean = false
    ) {
        if (!isCurrentAuthFlow(authFlowId)) {
            return
        }
        if (builder.getActivity() == null) {
            BiometricLoggerImpl.e(
                IllegalStateException(),
                LocalizationHelper.getLocalizedString(
                    builder.getContext(),
                    R.string.biometriccompat_window_error
                )

            )
            val failureResults = builder.getAllAvailableTypes().map {
                AuthenticationResult(
                    it,
                    reason = AuthenticationFailureReason.INTERNAL_ERROR,
                    description = LocalizationHelper.getLocalizedString(
                        builder.getContext(),
                        R.string.biometriccompat_window_error
                    )
                )
            }.toSet()
            dispatchAfterFlowFinished(
                finishFlow = { finishAuthFlow(authFlowId) },
                dispatch = { callbackOuter.onFailed(failureResults) }
            )
            return
        }
        val authTask = authTask@{
            if (!isCurrentAuthFlow(authFlowId)) {
                return@authTask
            }
            if (builder.getActivity() == null) {
                BiometricLoggerImpl.e(
                    IllegalStateException(),
                    LocalizationHelper.getLocalizedString(
                        builder.getContext(),
                        R.string.biometriccompat_window_error
                    )
                )
                val failureResults = builder.getAllAvailableTypes().map {
                    AuthenticationResult(
                        it,
                        reason = AuthenticationFailureReason.INTERNAL_ERROR,
                        description = LocalizationHelper.getLocalizedString(
                            builder.getContext(),
                            R.string.biometriccompat_window_error
                        )
                    )
                }.toSet()
                dispatchAfterFlowFinished(
                    finishFlow = { finishAuthFlow(authFlowId) },
                    dispatch = { callbackOuter.onFailed(failureResults) }
                )
            } else {
                BiometricLoggerImpl.d("BiometricPromptCompat.startAuth")
                if (failIfNoEffectiveBiometrics(callbackOuter, authFlowId)) return@authTask
                builder.freezeAuthenticationRequirements()
                builder.systemPromptOwnsUi = when (val implementation = impl) {
                    is BiometricPromptApi28Impl -> !DevicesWithKnownBugs.isMissedBiometricUI
                    is BiometricPromptGenericImpl -> {
                        implementation.prepareUiSession()
                        implementation.systemPromptOwnsUi
                    }
                    else -> false
                }
                val foregroundFeedback = ForegroundFeedbackSession()
                builder.foregroundFeedback = foregroundFeedback
                val credentialHost = WeakReference(builder.getActivity())
                fun createActivityViewWatcher(hostWindow: View? = null) = try {
                    if (!builder.isSilentAuthEnabled()) ActivityViewWatcher(
                        builder,
                        object : ActivityViewWatcher.ForceToCloseCallback {
                            override fun onCloseBiometric() {
                                BiometricLoggerImpl.e("BiometricPromptCompat.onCloseBiometric")
                                cancelAuthentication()
                            }
                        }, hostWindow) else null
                } catch (e: Throwable) {
                    BiometricLoggerImpl.e(e)
                    null
                }
                var activityViewWatcher = createActivityViewWatcher()

                var hadUi = false
                var enrollmentSucceeded = false
                var credentialReset: Job? = null
                val lastKnownOrientation = AtomicInteger(0)
                val orientationLocked = AtomicBoolean(false)
                val completion = AuthFlowCompletion(
                    post = { task -> ExecutorHelper.post { task() } },
                    ownsFlow = { authFlowGeneration.get() == authFlowId && authFlowInProgress.get() },
                    cleanup = {
                        runCatching { foregroundFeedback.close() }.onFailure { BiometricLoggerImpl.e(it) }
                        if (builder.foregroundFeedback === foregroundFeedback) builder.foregroundFeedback = null
                        // All engine/UI teardown belongs to this generation. No cleanup is
                        // queued after release: client callbacks may immediately start again.
                        runCatching { if (implementationStarted) impl.cancelAuthentication() }
                            .onFailure { BiometricLoggerImpl.e(it) }
                        runCatching { LegacyBiometric.cancelAuthentication() }
                            .onFailure { BiometricLoggerImpl.e(it) }
                        runCatching { activityViewWatcher?.resetListeners() }.onFailure { BiometricLoggerImpl.e(it) }
                        builder.isUIOpened.set(false)
                        builder.release()
                        if (orientationLocked.getAndSet(false)) {
                            builder.getActivity()?.requestedOrientation = lastKnownOrientation.get()
                        }
                        if (hadUi) appBackgroundDetector.detachListeners()
                        if (hadUi && !builder.isSilentAuthEnabled()) {
                            builder.getActivity()?.let {
                                StatusBarTools.setNavBarAndStatusBarColors(
                                    it.window, builder.getNavBarColor(),
                                    builder.getDividerColor(), builder.getStatusBarColor()
                                )
                            }
                            if (DevicesWithKnownBugs.hasUnderDisplayFingerprint && builder.isNotificationEnabled()) {
                                BiometricNotificationManager.dismissAll()
                            }
                        }
                    },
                    release = { finishAuthFlow(authFlowId) },
                    onClosed = { if (hadUi) callbackOuter.onUIClosed() },
                    afterCleanup = { complete ->
                        val succeeded = enrollmentSucceeded && !authCanceled.get()
                        val pendingReset = credentialReset
                        if (pendingReset != null || succeeded || builder.hasTrackedEnrollments()) {
                            ExecutorHelper.scope.launch {
                                try {
                                    // Cancellation must keep this generation reserved while its reset runs.
                                    pendingReset?.join()
                                    if (succeeded) {
                                        runCatching {
                                            val request = builder.getBiometricAuthRequest()
                                            if (request.api != BiometricApi.AUTO) {
                                                HardwareAccessImpl.getInstance(request).updateBiometricEnrollChanged()
                                            } else {
                                                HardwareAccessImpl.getInstance(request.withApi(BiometricApi.BIOMETRIC_API))
                                                    .updateBiometricEnrollChanged()
                                                HardwareAccessImpl.getInstance(request.withApi(BiometricApi.LEGACY_API))
                                                    .updateBiometricEnrollChanged()
                                            }
                                        }.onFailure { BiometricLoggerImpl.e(it) }
                                    }
                                    builder.finishEnrollSession(succeeded)
                                } finally {
                                    ExecutorHelper.post { complete() }
                                }
                            }
                        } else {
                            builder.finishEnrollSession(succeeded)
                            complete()
                        }
                    }
                )
                activeCompletion = completion
                val callback = object : AuthenticationCallback() {
                    private var restarting = false
                    private fun postWhileActive(task: () -> Unit) {
                        ExecutorHelper.post {
                            if (isCurrentAuthFlow(authFlowId) && !completion.isFinishing()) task()
                        }
                    }

                    private fun restartWithCurrentFlow(beforeRestart: () -> Unit = {}) {
                        restarting = true
                        ExecutorHelper.postDelayed({
                            if (isCurrentAuthFlow(authFlowId) && !completion.isFinishing()) {
                                beforeRestart()
                                restarting = false
                                if (isCurrentAuthFlow(authFlowId)) authenticateInternal(this)
                            }
                        }, builder.getContext().resources.getInteger(android.R.integer.config_shortAnimTime).toLong())
                    }

                    override fun onSucceeded(result: Set<AuthenticationResult>) {
                        if (!isCurrentAuthFlow(authFlowId) || completion.isFinishing() || restarting) return
                        val confirmed = result.toSet()
                        // The platform can close its prompt before the off-main check returns.
                        restarting = true
                        appBackgroundDetector.pauseFragmentDismiss()
                        scanRuntimeHooks {
                            if (!isCurrentAuthFlow(authFlowId) || completion.isFinishing()) return@scanRuntimeHooks
                            restarting = false
                            acceptSuccess(confirmed)
                        }
                    }

                    private fun acceptSuccess(result: Set<AuthenticationResult>) {
                        hookFailureOrNull(result)?.let { failure ->
                            // Terminal rejection, before enrollment commit or crypto use. Do not
                            // route through onFailed's device-credential restart heuristic.
                            completion.finish { callbackOuter.onFailed(failure) }
                            return
                        }
                        val afterCredential = builder.credentialConfirmed
                        val checkForBiometricRetry = builder.forceDeviceCredential()
                        if (afterCredential || checkForBiometricRetry) {
                            builder.credentialConfirmed = false
                            restarting = true
                            // The credential stage is closing while the protected reset runs.
                            activityViewWatcher?.resetListeners()
                            activityViewWatcher = null
                            credentialReset = ExecutorHelper.scope.launch {
                                var retryBiometrics = false
                                try {
                                    if (isCurrentAuthFlow(authFlowId)) {
                                        if (afterCredential) {
                                            runCatching {
                                                BiometricErrorLockoutPermanentFix.resetBiometricSensorPermanentlyLocked()
                                            }.onFailure { BiometricLoggerImpl.e(it) }
                                        }
                                        if (checkForBiometricRetry && isCurrentAuthFlow(authFlowId)) {
                                            retryBiometrics = checkHardware() == AuthenticationFailureReason.UNKNOWN
                                        }
                                    }
                                } catch (e: Exception) {
                                    BiometricLoggerImpl.e(e)
                                } finally {
                                    postWhileActive {
                                        restarting = false
                                        if (activeCredentialHost() != null) {
                                            finishSuccess(result, retryBiometrics)
                                        } else {
                                            cancelAuthentication()
                                        }
                                    }
                                }
                            }
                        } else {
                            finishSuccess(result)
                        }
                    }

                    private fun activeCredentialHost(): FragmentActivity? =
                        credentialHost.get()?.takeIf {
                            it === builder.getActivity() &&
                                    it.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
                        }

                    private fun finishSuccess(
                        result: Set<AuthenticationResult>,
                        retryBiometrics: Boolean = false
                    ) {
                        if (retryBiometrics) {
                            // The stopped credential prompt must not dismiss its replacement.
                            val detector = appBackgroundDetector
                            detector.cancelPendingFragmentDismiss()
                            builder.setForceDeviceCredentials(false)
                            if (::oldTitle.isInitialized) builder.setTitle(oldTitle)
                            if (::oldDescription.isInitialized) builder.setDescription(oldDescription)
                            // The old fragment can also stop during the retry animation.
                            restartWithCurrentFlow {
                                detector.cancelPendingFragmentDismiss()
                                detector.resumeFragmentDismiss()
                                rebindActivityViewWatcher()
                            }
                            return
                        }
                        if (builder.enroll) builder.markEnrollConfirmedResults(result)
                        if (builder.shouldAutoVerifyCryptoAfterSuccess()) {
                            restarting = true
                            val confirmed = result.toSet()
                            readFlowStatus(authFlowId, callbackOuter, {
                                CryptographyManager.encryptData(
                                    AndroidContext.appContext.packageName.toByteArray(Charset.forName("UTF-8")),
                                    confirmed
                                ) != null
                            }) { verified ->
                                restarting = false
                                if (verified) {
                                    deliverSuccess(confirmed.mapTo(LinkedHashSet()) { AuthenticationResult(it.type) })
                                } else {
                                    onCanceled(builder.availableTypesForCancellation().mapTo(LinkedHashSet()) {
                                        AuthenticationResult(
                                            it, reason = AuthenticationFailureReason.CRYPTO_ERROR,
                                            description = LocalizationHelper.getLocalizedString(
                                                builder.getContext(), R.string.biometriccompat_cryptography_failed_error
                                            )
                                        )
                                    })
                                }
                            }
                            return
                        }
                        deliverSuccess(result.toSet())
                    }

                    private fun deliverSuccess(delivered: Set<AuthenticationResult>) {
                        enrollmentSucceeded = true
                        completion.finish { callbackOuter.onSucceeded(delivered) }
                    }

                    private fun rebindActivityViewWatcher() {
                        val host = activeCredentialHost()
                        if (host == null) {
                            cancelAuthentication()
                        } else {
                            activityViewWatcher = createActivityViewWatcher(host.window.decorView)
                            if (hadUi) activityViewWatcher?.setupListeners()
                        }
                    }

                    override fun onCanceled(canceled: Set<AuthenticationResult>) {
                        if (!isCurrentAuthFlow(authFlowId) || completion.isFinishing()) return
                        completion.finish {
                            if (canceled.any { it.reason == AuthenticationFailureReason.INTERNAL_ERROR }) {
                                callbackOuter.onFailed(canceled)
                            } else {
                                callbackOuter.onCanceled(canceled)
                            }
                        }
                    }

                    override fun onFailed(canceled: Set<AuthenticationResult>) {
                        if (!isCurrentAuthFlow(authFlowId) || completion.isFinishing() || restarting) return
                        if (builder.isUIOpened.get() &&
                            (builder.nativeErrorReceivedAt ?: System.currentTimeMillis()) - startTsImpl <=
                                builder.getContext().resources.getInteger(android.R.integer.config_longAnimTime) &&
                            builder.isDeviceCredentialFallbackAllowed() && !builder.forceDeviceCredential()
                        ) {
                            restarting = true
                            val detector = appBackgroundDetector
                            detector.pauseFragmentDismiss()
                            activityViewWatcher?.resetListeners()
                            activityViewWatcher = null
                            val previousLockout = builder.wasPermanentlyLocked()
                            ExecutorHelper.startOnBackground {
                                if (!isCurrentAuthFlow(authFlowId)) return@startOnBackground
                                val retryWithCredential = runCatching {
                                    previousLockout != BiometricManagerCompat.isBiometricSensorPermanentlyLocked(
                                        builder.getBiometricAuthRequest()
                                    )
                                }.onFailure { BiometricLoggerImpl.e(it) }.getOrDefault(false)
                                postWhileActive {
                                    detector.resumeFragmentDismiss()
                                    if (retryWithCredential) {
                                        builder.setForceDeviceCredentials(true)
                                        restartWithCurrentFlow(::rebindActivityViewWatcher)
                                    } else {
                                        completion.finish { callbackOuter.onFailed(canceled) }
                                    }
                                }
                            }
                            return
                        }
                        completion.finish { callbackOuter.onFailed(canceled) }
                    }

                    override fun onSupplementalResult(result: SupplementalBiometricResult) {
                        postWhileActive {
                            runCatching { callbackOuter.onSupplementalResult(result) }
                                .onFailure { BiometricLoggerImpl.e(it) }
                        }
                    }

                    override fun onUIOpened() {
                        if (isCurrentAuthFlow(authFlowId) && !completion.isFinishing() && !builder.isUIOpened.get()) {
                            hadUi = true
                            builder.isUIOpened.set(true)
                            val multiWindowSupport = builder.getMultiWindowSupport()
                            if (DevicesWithKnownBugs.hasUnderDisplayFingerprint &&
                                multiWindowSupport.canLockCurrentOrientation
                            ) {
                                lastKnownOrientation.set(
                                    builder.getActivity()?.requestedOrientation
                                        ?: multiWindowSupport.requestedScreenOrientation
                                )
                                orientationLocked.set(true)
                                builder.getActivity()?.requestedOrientation =
                                    multiWindowSupport.requestedScreenOrientation
                            }
                            BiometricLoggerImpl.d("BiometricPromptCompat.AuthenticationCallback.onUIOpened")
                            val s =
                                "BiometricOpeningTime: onUIOpened << ${System.currentTimeMillis() - startTs} ms"
                            BiometricLoggerImpl.d("BiometricPromptCompat $s")
                            postWhileActive {
                                appBackgroundDetector.attachListeners()
                            }
                            ExecutorHelper.post {
                                // Preserve the opened/closed callback pair even for synchronous success.
                                if (authFlowGeneration.get() == authFlowId && authFlowInProgress.get()) {
                                    callbackOuter.onUIOpened()
                                }
                            }
                            if (!builder.isSilentAuthEnabled()) {
                                postWhileActive { activityViewWatcher?.setupListeners() }
                                postWhileActive {
                                    builder.getActivity()?.let {
                                        StatusBarTools.setNavBarAndStatusBarColors(
                                            it.window,
                                            DialogMainColor.getColor(
                                                builder.getContext(),
                                                DarkLightThemes.isNightModeCompatWithInscreen(
                                                    builder.getContext()
                                                )
                                            ),
                                            DialogMainColor.getColor(
                                                builder.getContext(),
                                                !DarkLightThemes.isNightModeCompatWithInscreen(
                                                    builder.getContext()
                                                )
                                            ),
                                            builder.getStatusBarColor()
                                        )
                                    }
                                    if (DevicesWithKnownBugs.hasUnderDisplayFingerprint && builder.isNotificationEnabled()) {
                                        BiometricNotificationManager.showNotification(
                                            builder
                                        )
                                    }

                                }
                            }
                        }
                    }

                    override fun onUIClosed() {
                        if (!restarting) completion.finish()
                    }
                }
                authenticateInternal(callback)
            }
        }
        if (preflightCompleted) {
            authTask.invoke()
        } else {
            runAuthPreflight(callbackOuter, authFlowId, authTask)
        }
    }

    private fun runAuthPreflight(
        callback: AuthenticationCallback,
        authFlowId: Long,
        authTask: () -> Unit,
        requestNotificationPermission: Boolean = builder.enroll,
        shouldPrepareModules: () -> Boolean = { true }
    ) {
        val runPreflight = {
            if (isCurrentAuthFlow(authFlowId)) {
                runAuthPreflightStages(
                    shouldPrepareModules = shouldPrepareModules,
                    checkPermissions = { next ->
                        readFlowStatus(authFlowId, callback, builder::prepareRoutes) {
                            checkPermissions(callback, authFlowId, next)
                        }
                    },
                    checkSensor = { next ->
                        checkSensor(callback, authFlowId) {
                            readFlowStatus(authFlowId, callback, builder::prepareRoutes) {
                                if (!failIfNoActiveBiometrics(callback, authFlowId) &&
                                    !failIfNoEffectiveBiometrics(callback, authFlowId)
                                ) {
                                    next()
                                }
                            }
                        }
                    },
                    prepareModulesTask = { next ->
                        checkModulePreparation(callback, authFlowId) {
                            if (builder.enroll) builder.invalidateSelectedRoutes()
                            next()
                        }
                    },
                    authenticate = {
                        readFlowStatus(authFlowId, callback, builder::prepareRoutes) {
                            if (!failIfNoActiveBiometrics(callback, authFlowId) &&
                                !failIfNoEffectiveBiometrics(callback, authFlowId)
                            ) {
                                authTask()
                            }
                        }
                    }
                )
            }
        }
        if (requestNotificationPermission) {
            checkNotificationPermissions(authFlowId, runPreflight)
        } else {
            runPreflight.invoke()
        }
    }

    private fun failIfNoActiveBiometrics(
        callback: AuthenticationCallback,
        authFlowId: Long
    ): Boolean {
        if (builder.getAllAvailableTypes().isNotEmpty()) {
            return false
        }
        val canceledResults = setOf(
            AuthenticationResult(
                BiometricType.BIOMETRIC_ANY,
                reason = AuthenticationFailureReason.MISSING_PERMISSIONS_ERROR,
                description = getMissingPermissionsDescription()
            )
        )
        dispatchAfterFlowFinished(
            finishFlow = { finishAuthFlow(authFlowId) },
            dispatch = { callback.onCanceled(canceledResults) }
        )
        return true
    }

    private fun failIfNoEffectiveBiometrics(
        callback: AuthenticationCallback,
        authFlowId: Long
    ): Boolean {
        if (builder.enroll && awaitingSystemEnrollment) return false
        if (!builder.enroll && builder.getAllAvailableTypes().isNotEmpty() &&
            builder.getEligibleAuthenticationTypes().isEmpty()) {
            val failures = builder.getAllAvailableTypes().mapTo(LinkedHashSet()) {
                AuthenticationResult(it, reason = AuthenticationFailureReason.INSUFFICIENT_TRUST,
                    description = builder.getContext().getString(R.string.biometriccompat_authentication_trust_required))
            }
            dispatchAfterFlowFinished({ finishAuthFlow(authFlowId) }, { callback.onFailed(failures) })
            return true
        }
        if (if (builder.enroll) builder.getPendingEnrollTypes().isNotEmpty() else builder.getEffectiveAvailableTypes().isNotEmpty()) {
            return false
        }
        if (builder.enroll) {
            finishAndDispatchEnrollTerminalOutcome(
                authFlowId = authFlowId,
                callback = callback,
                canceledResults = emptyEffectiveBiometricCancellationResults(builder.getEnrollScopeTypes())
            )
        } else {
            val canceledResults =
                emptyEffectiveBiometricCancellationResults(builder.getAllAvailableTypes())
            dispatchAfterFlowFinished(
                finishFlow = { finishAuthFlow(authFlowId) },
                dispatch = { callback.onCanceled(canceledResults) }
            )
        }
        return true
    }

    private fun checkNotificationPermissions(authFlowId: Long, authTask: () -> Unit) {
        if (!builder.isSilentAuthEnabled() && DevicesWithKnownBugs.hasUnderDisplayFingerprint &&
            builder.isNotificationEnabled()
        ) {
            BiometricNotificationManager.initNotificationsPreferences()
            val host = builder.getActivity()
            if (host != null) {
                cancelOwnedUi = NotificationPermissionsHelper.checkOwned(host, BiometricNotificationManager.CHANNEL_ID) {
                    if (isCurrentAuthFlow(authFlowId)) {
                        cancelOwnedUi = null
                        if (it) authTask() else cancelAuthentication()
                    }
                }
                return
            }
        }
        authTask()
    }

    private fun checkPermissions(
        callback: AuthenticationCallback,
        authFlowId: Long,
        authTask: () -> Unit
    ) {
        readFlowStatus(authFlowId, callback, {
            val permissionsMap = getUsedPermissionsMapForSelectedModules()
            permissionsMap to deniedPermissions(permissionsMap)
        }) { (permissionsMap, denied) ->
            if (denied.isEmpty()) {
                authTask()
                return@readFlowStatus
            }
            if (!builder.enroll) {
                // Authentication only uses routes that are already permitted.
                if (disablePermissionDeniedModules(permissionsMap, denied)) {
                    readFlowStatus(authFlowId, callback, builder::prepareRoutes) {
                        if (!failIfNoActiveBiometrics(callback, authFlowId) &&
                            !failIfNoEffectiveBiometrics(callback, authFlowId)) authTask()
                    }
                } else authTask()
                return@readFlowStatus
            }

            fun continueAfterPermissionCheck() {
                readFlowStatus(authFlowId, callback, { deniedPermissions(permissionsMap) }) { stillDenied ->
                    val hasGrantedRoute = permissionsMap.any { (_, permissions) -> permissions.none(stillDenied::contains) }
                    disablePermissionDeniedModules(permissionsMap, stillDenied)
                    readFlowStatus(authFlowId, callback, builder::prepareRoutes) {
                        if (!stopAfterPermissionDenied(callback, stillDenied.toList(), authFlowId) &&
                            (hasGrantedRoute || !failIfNoActiveBiometrics(callback, authFlowId))
                        ) authTask()
                    }
                }
            }
            val host = builder.getActivity()
            if (host == null) continueAfterPermissionCheck()
            else cancelOwnedUi = PermissionsFragment.launchOwned(host, denied.toList()) { returned ->
                if (!isCurrentAuthFlow(authFlowId)) return@launchOwned
                cancelOwnedUi = null
                if (returned) continueAfterPermissionCheck() else cancelAuthentication()
            }
        }
    }

    private fun deniedPermissions(permissionsMap: List<Pair<BiometricType, List<String>>>): Set<String> =
        permissionsMap.flatMap { it.second }.toSet().filterTo(LinkedHashSet()) {
            !PermissionUtils.INSTANCE.hasSelfPermissions(it)
        }

    private fun stopAfterPermissionDenied(
        callback: AuthenticationCallback,
        deniedPermissions: List<String>,
        authFlowId: Long
    ): Boolean {
        if (!shouldStopAfterPermissionDenied(
                builder.enroll,
                deniedPermissions,
                hasUsableBiometricRoute(
                    builder.getAllAvailableTypes().map { builder.selectedRoute(it) }
                )
            )
        ) {
            return false
        }

        val canceledResults = setOf(
            AuthenticationResult(
                BiometricType.BIOMETRIC_ANY,
                reason = AuthenticationFailureReason.MISSING_PERMISSIONS_ERROR,
                description = getMissingPermissionsDescription()
            )
        )
        dispatchAfterFlowFinished(
            finishFlow = { finishAuthFlow(authFlowId) },
            dispatch = { callback.onCanceled(canceledResults) }
        )
        return true
    }

    private fun disablePermissionDeniedModules(
        permissionsMap: List<Pair<BiometricType, List<String>>>,
        denied: Set<String>
    ): Boolean {
        val deniedModules = permissionsMap
            .filter { (_, permissions) -> permissions.any(denied::contains) }
            .mapNotNull { (type, _) -> getSelectedBiometricModule(type) }
        deniedModules.forEach(builder::disableBiometricModule)
        return deniedModules.isNotEmpty()
    }

    private fun checkModulePreparation(
        callback: AuthenticationCallback,
        authFlowId: Long,
        authTask: () -> Unit
    ) {
        if (builder.deferSoftwarePreparation) {
            // Permissions/sensor checks may have removed the native route since readiness.
            if (canStartNativeBeforeSoftwarePreparation()) {
                authTask()
            } else {
                builder.deferSoftwarePreparation = false
                ExecutorHelper.startOnBackground {
                    SoftwareBiometricPromptRegistry.prepareInitial(
                        builder.getBiometricAuthRequest().type, builder.getContext(),
                        { isCurrentAuthFlow(authFlowId) }
                    ) {
                        ExecutorHelper.post {
                            if (isCurrentAuthFlow(authFlowId)) {
                                builder.invalidateAvailableTypes()
                                runAuthPreflight(callback, authFlowId, authTask,
                                    requestNotificationPermission = false)
                            }
                        }
                    }
                }
            }
            return
        }
        BiometricLoggerImpl.e("BiometricPromptCompat.checkModulePreparation")
        LegacyBiometric.prepareSoftwareModulesForAuthentication(
            builder.getBiometricAuthRequest(),
            getSoftwarePreparationTypes(),
            builder.enroll,
            builder.getDisabledModuleTags(),
            isActive = { isCurrentAuthFlow(authFlowId) },
            onModuleSkipped = { module ->
                if (isCurrentAuthFlow(authFlowId)) {
                    builder.disableBiometricModule(module)
                }
            },
            callback = object : AbstractSoftwareBiometricManager.PreparationCallback() {
                override fun onPrepared() {
                    if (isCurrentAuthFlow(authFlowId)) {
                        authTask.invoke()
                    }
                }

                override fun onPreparationError(errMsgId: Int, errString: CharSequence?) {
                    if (!isCurrentAuthFlow(authFlowId)) {
                        return
                    }
                    val reason = mapPreparationError(errMsgId)
                    val canceledResults = builder.getAllAvailableTypes().map { type ->
                            AuthenticationResult(
                                type,
                                reason = reason,
                                description = errString?.takeIf { it.isNotBlank() }
                                    ?: if (reason == AuthenticationFailureReason.MISSING_PERMISSIONS_ERROR) {
                                        getMissingPermissionsDescription()
                                    } else {
                                        null
                                    }
                            )
                        }.toSet()
                    dispatchAfterFlowFinished(
                        finishFlow = { finishAuthFlow(authFlowId) },
                        dispatch = { callback.onCanceled(canceledResults) }
                    )
                }

                override fun onPreparationCanceled() {
                    if (!isCurrentAuthFlow(authFlowId)) {
                        return
                    }
                    val canceledResults = builder.getAllAvailableTypes().map { type ->
                            AuthenticationResult(
                                type,
                                reason = AuthenticationFailureReason.CANCELED
                            )
                        }.toSet()
                    dispatchAfterFlowFinished(
                        finishFlow = { finishAuthFlow(authFlowId) },
                        dispatch = { callback.onCanceled(canceledResults) }
                    )

                }
            }
        )
    }

    private fun mapPreparationError(errMsgId: Int): AuthenticationFailureReason {
        return when (if (errMsgId < 1000) errMsgId else errMsgId % 1000) {
            AbstractSoftwareBiometricManager.CUSTOM_BIOMETRIC_ERROR_NO_PERMISSIONS ->
                AuthenticationFailureReason.MISSING_PERMISSIONS_ERROR

            AbstractSoftwareBiometricManager.CUSTOM_BIOMETRIC_ERROR_HW_NOT_PRESENT ->
                AuthenticationFailureReason.NO_HARDWARE

            AbstractSoftwareBiometricManager.CUSTOM_BIOMETRIC_ERROR_HW_UNAVAILABLE ->
                AuthenticationFailureReason.HARDWARE_UNAVAILABLE

            AbstractSoftwareBiometricManager.CUSTOM_BIOMETRIC_ERROR_LOCKOUT ->
                AuthenticationFailureReason.LOCKED_OUT

            AbstractSoftwareBiometricManager.CUSTOM_BIOMETRIC_ERROR_LOCKOUT_PERMANENT ->
                AuthenticationFailureReason.HARDWARE_UNAVAILABLE

            else -> AuthenticationFailureReason.UNKNOWN
        }
    }

    private fun getMissingPermissionsDescription(): CharSequence {
        val permissions = getUsedPermissionsForSelectedModules().ifEmpty {
            BiometricManagerCompat.getUsedPermissions(listOf(builder.getBiometricAuthRequest().type))
        }
        val message = if (permissions.contains(Manifest.permission.CAMERA)) {
            dev.skomlach.common.R.string.biometriccompat_camera_permission_required
        } else {
            dev.skomlach.common.R.string.biometriccompat_permissions_request_failed
        }
        return LocalizationHelper.getLocalizedString(builder.getContext(), message)
    }

    private fun checkSensor(
        callback: AuthenticationCallback,
        authFlowId: Long,
        authTask: () -> Unit
    ) {

        if (!isCurrentAuthFlow(authFlowId)) {
            return
        }

        readFlowStatus(authFlowId, callback, read = {
            val permissions = getUsedPermissionsForSelectedModules()
            resolveCameraSensorBlock(isCameraSensorBlockedForPermissions(permissions, SensorPrivacyCheck::isCameraBlocked))
        }) { decision ->
            if (decision == CameraSensorBlockAction.DISABLE_CAMERA_ROUTES) {
                val blockedTypes = disableTypesRequiringPermission(Manifest.permission.CAMERA)
                BiometricLoggerImpl.e("BiometricPromptCompat.checkSensor camera routes skipped")
                readFlowStatus(authFlowId, callback, builder::prepareRoutes) { types ->
                    if (types.isEmpty()) {
                        val canceledResults = blockedTypes.mapTo(LinkedHashSet()) { type ->
                            AuthenticationResult(
                                type,
                                reason = AuthenticationFailureReason.HARDWARE_UNAVAILABLE,
                                description = LocalizationHelper.getLocalizedString(
                                    builder.getContext(), R.string.biometriccompat_camera_blocked
                                )
                            )
                        }
                        dispatchAfterFlowFinished(
                            finishFlow = { finishAuthFlow(authFlowId) },
                            dispatch = { callback.onCanceled(canceledResults) }
                        )
                    } else {
                        authTask()
                    }
                }
            } else {
                authTask()
            }
        }
    }

    private fun disableTypesRequiringPermission(permission: String): Set<BiometricType> {
        return biometricTypesUsingPermission(
            getUsedPermissionsMapForSelectedModules(),
            permission
        ).onEach(builder::disableBiometricType)
    }

    private fun canStartNativeBeforeSoftwarePreparation(): Boolean =
        canPrepareSoftwareAlongsideNative(
            builder.enroll,
            builder.isSilentAuthEnabled() || builder.forceDeviceCredential() || DevicesWithKnownBugs.isMissedBiometricUI,
            builder.getBiometricAuthRequest().confirmation,
            builder.getPrimaryAvailableTypes().any {
                builder.selectedRoute(it)?.usesBiometricPromptHardware == true
            },
            shouldUseBiometricPromptImpl()
        )

    private fun shouldUseBiometricPromptImpl(): Boolean {
        if (builder.authenticationStageTypes != null && builder.getAuthenticationExecutionTypes().none {
                builder.selectedRoute(it)?.usesBiometricPromptHardware == true
            }) return false
        if (builder.getBiometricAuthRequest().provider == BiometricProviderType.SOFTWARE) {
            return false
        }

        if (builder.hasSoftwareEnrollTargets()) return false

        if (isHigherPrioritySoftwareSelectedThanBiometricPrompt()) {
            return false
        }

        if (builder.getBiometricAuthRequest().api == BiometricApi.BIOMETRIC_API) {
            return true
        }

        if (builder.getBiometricAuthRequest().api == BiometricApi.AUTO &&
            !HardwareAccessImpl.getInstance(builder.getBiometricAuthRequest()).isNewBiometricApi
        ) {
            return false
        }

        return builder.getAuthenticationExecutionTypes().any { type ->
            isSelectedBiometricPromptHardwareType(type)
        }
    }

    private fun isHigherPrioritySoftwareSelectedThanBiometricPrompt(): Boolean {
        val selectedRoutes = builder.getAuthenticationExecutionTypes()
            .mapNotNull { type -> builder.selectedRoute(type) }
        val bestSoftwarePriority = selectedRoutes
            .filter { it.provider == BiometricProviderType.SOFTWARE }
            .mapNotNull { it.module?.priority }
            .maxOrNull() ?: return false
        val hasBiometricPromptHardwareRoute = selectedRoutes.any { route ->
            route.usesBiometricPromptHardware
        }

        return hasBiometricPromptHardwareRoute &&
                bestSoftwarePriority > BiometricModule.PRIORITY_SYSTEM_HARDWARE
    }

    private fun getUsedPermissionsMapForSelectedModules(): List<Pair<BiometricType, List<String>>> {
        val types = if (builder.enroll) builder.getAllAvailableTypes() else builder.getAuthenticationExecutionTypes()
        return types.map { type ->
            val permissions = builder.getSelectedTypePermissions(type)
            Pair(
                type,
                permissions
            )
        }
    }

    private fun getUsedPermissionsForSelectedModules(): List<String> {
        return getUsedPermissionsMapForSelectedModules()
            .flatMap { it.second }
            .distinct()
    }

    private fun getSoftwarePreparationTypes(): Set<BiometricType> {
        return builder.getAuthenticationExecutionTypes()
            .filter { type -> isSelectedSoftwareType(type) }
            .toSet()
    }

    private fun isSelectedBiometricPromptHardwareType(type: BiometricType): Boolean {
        return builder.selectedRoute(type)?.usesBiometricPromptHardware == true
    }

    private fun isSelectedSoftwareType(type: BiometricType): Boolean {
        return builder.selectedRoute(type)?.provider == BiometricProviderType.SOFTWARE
    }

    private fun getSelectedBiometricModule(type: BiometricType): BiometricModule? {
        return builder.selectedRoute(type)?.module
    }


    private fun authenticateInternal(
        callback: AuthenticationCallback
    ) {
        val authFlowId = ownedAuthFlowGeneration.get()
        if (!isCurrentAuthFlow(authFlowId)) return
        BiometricLoggerImpl.e("BiometricPromptCompat.authenticateInternal();  isDeviceCredentialFallbackAllowed=${builder.isDeviceCredentialFallbackAllowed()}; forceDeviceCredential=${builder.forceDeviceCredential()}")
        if (builder.getActivity() == null) {
            BiometricLoggerImpl.e(
                IllegalStateException(),
                LocalizationHelper.getLocalizedString(
                    builder.getContext(),
                    R.string.biometriccompat_window_error
                )

            )
            callback.onFailed(builder.getAllAvailableTypes().map {
                AuthenticationResult(
                    it,
                    reason = AuthenticationFailureReason.INTERNAL_ERROR,
                    description = LocalizationHelper.getLocalizedString(
                        builder.getContext(),
                        R.string.biometriccompat_window_error
                    )
                )
            }.toSet())
            return
        }
        try {
            BiometricLoggerImpl.d("BiometricPromptCompat.authenticateInternal() - impl.authenticate $impl")


            if (!builder.forceDeviceCredential()) {
                BiometricLoggerImpl.d("BiometricPromptCompat BiometricOpeningTime: authenticateInternal >> regular ${System.currentTimeMillis() - startTs} ms")
                startTsImpl = System.currentTimeMillis()
                implementationStarted = true
                impl.authenticate(callback)
            } else {
                BiometricLoggerImpl.d("BiometricPromptCompat BiometricOpeningTime: authenticateInternal >> credentials ${System.currentTimeMillis() - startTs} ms")
                if (builder.getCryptographyPurpose() != null) {
                    callback.onFailed(builder.getAllAvailableTypes().map {
                        AuthenticationResult(
                            it,
                            reason = AuthenticationFailureReason.CRYPTO_ERROR,
                            description = LocalizationHelper.getLocalizedString(
                                builder.getContext(),
                                R.string.biometriccompat_cryptography_not_supported_error
                            )
                        )
                    }.toSet())
                    return
                }
                if (!::oldTitle.isInitialized) {
                    oldTitle = builder.getTitle() ?: ""
                }
                if (!::oldDescription.isInitialized) {
                    oldDescription = builder.getDescription() ?: ""
                }
                val secureScreenDialog = { title: CharSequence ->
                    builder.setTitle(title)
                    builder.setDescription(
                        LocalizationHelper.getLocalizedString(
                            builder.getActivity() ?: builder.getContext(),
                            R.string.biometriccompat_use_devicecredentials
                        )
                    )
                    if (impl is BiometricPromptApi28Impl) {//BiometricPrompt deal with credentials natively
                        implementationStarted = true
                        impl.authenticate(callback)
                    } else {
                        val activity = builder.getActivity()
                        if (activity == null) {
                            BiometricLoggerImpl.e(
                                LocalizationHelper.getLocalizedString(
                                    builder.getContext(),
                                    R.string.biometriccompat_window_error
                                ),
                                IllegalStateException()
                            )
                            callback.onFailed(builder.getAllAvailableTypes().map {
                                AuthenticationResult(
                                    it,
                                    reason = AuthenticationFailureReason.INTERNAL_ERROR,
                                    description = LocalizationHelper.getLocalizedString(
                                        builder.getContext(),
                                        R.string.biometriccompat_window_error
                                    )
                                )
                            }.toSet())
                        } else {
                            cancelOwnedUi = CredentialsRequestFragment.launchOwned(
                                activity,
                                null,
                                builder.getDescription()
                            ) {
                                if (!isCurrentAuthFlow(authFlowId)) return@launchOwned
                                cancelOwnedUi = null
                                if (it) {
                                    builder.credentialConfirmed = true
                                    callback.onSucceeded(
                                        mutableSetOf(
                                            AuthenticationResult(
                                                BiometricType.BIOMETRIC_ANY
                                            )
                                        )
                                    )
                                } else {
                                    callback.onCanceled(builder.getAllAvailableTypes().map {
                                        AuthenticationResult(
                                            it,
                                            reason = AuthenticationFailureReason.CANCELED_BY_USER,
                                            description = LocalizationHelper.getLocalizedString(
                                                builder.getContext(),
                                                R.string.biometriccompat_credentials_error
                                            )
                                        )
                                    }.toSet())
                                }
                            }
                            callback.onUIOpened()
                        }
                    }
                }
                val context = builder.getContext()
                val previousTitle = oldTitle
                readFlowStatus(authFlowId, callback, {
                    val app = AndroidContext.appContext
                    val title = runCatching {
                        val info = app.applicationInfo
                        app.packageManager.getApplicationLabel(info).ifEmpty { app.getString(info.labelRes) }
                    }.getOrDefault(previousTitle)
                    A11yDetection.shouldWeTrustA11y(context) to title
                }) { (trusted, title) ->
                    val host = builder.getActivity()
                    if (host == null) {
                        cancelAuthentication()
                    } else if (trusted) {
                        secureScreenDialog(title)
                    } else {
                        cancelOwnedUi = UntrustedAccessibilityFragment.launchOwned(host) { proceed ->
                            if (!isCurrentAuthFlow(authFlowId)) return@launchOwned
                            cancelOwnedUi = null
                            if (proceed) secureScreenDialog(title)
                            else callback.onCanceled(builder.availableTypesForCancellation().mapTo(LinkedHashSet()) {
                                AuthenticationResult(it, reason = AuthenticationFailureReason.CANCELED_BY_USER,
                                    description = LocalizationHelper.getLocalizedString(context,
                                        R.string.biometriccompat_untrusted_a11y_error))
                            })
                        }
                        callback.onUIOpened()
                    }
                }
            }
        } catch (e: IllegalStateException) {
            callback.onFailed(builder.getAllAvailableTypes().map {
                AuthenticationResult(
                    it,
                    reason = AuthenticationFailureReason.INTERNAL_ERROR,
                    description = biometricInternalErrorDescription()
                )
            }.toSet())
        }
    }

    fun cancelAuthentication() {
        val authFlowId = ownedAuthFlowGeneration.get()
        if (authFlowId < 0L || authFlowGeneration.get() != authFlowId || !authFlowInProgress.get()) return
        if (activeCompletion?.isFinishing() == true) return
        authCanceled.set(true)
        ExecutorHelper.post {
            if (authFlowGeneration.get() != authFlowId || !authFlowInProgress.get()) return@post
            val callback = activeAuthCallback
            val results = emptyEffectiveBiometricCancellationResults(builder.availableTypesForCancellation())
            val completion = activeCompletion
            if (completion != null) {
                completion.finish { callback?.onCanceled(results) }
            } else {
                dispatchAfterFlowFinished(
                    finishFlow = { finishAuthFlow(authFlowId) },
                    dispatch = { callback?.onCanceled(results) }
                )
            }
        }
    }

    private fun isCurrentAuthFlow(authFlowId: Long): Boolean {
        return isAuthFlowActive(
            expectedGeneration = authFlowId,
            currentGeneration = authFlowGeneration.get(),
            inProgress = authFlowInProgress.get(),
            canceled = authCanceled.get()
        )
    }

    private fun finishAuthFlow(authFlowId: Long): Boolean {
        if (ownedAuthFlowGeneration.get() == authFlowId) {
            cancelOwnedUi?.invoke()
            cancelOwnedUi = null
            awaitingSystemEnrollment = false
        }
        val finished = authFlowInProgress.finishAuthFlowIfCurrent(
            expectedGeneration = authFlowId,
            currentGeneration = authFlowGeneration.get()
        )
        if (finished) {
            ownedAuthFlowGeneration.compareAndSet(authFlowId, -1L)
            builder.endAuthFlow(authFlowId)
            activeCompletion = null
            activeAuthCallback = null
            implementationStarted = false
        }
        return finished
    }

    @ColorInt
    fun getDialogMainColor(): Int {
        if (!API_ENABLED)
            return ContextCompat.getColor(builder.getContext(), R.color.material_grey_50)
        return DialogMainColor.getColor(
            builder.getContext(),
            DarkLightThemes.isNightModeCompatWithInscreen(builder.getContext())
        )
    }

    abstract class AuthenticationCallback {
        // Reject a known local compromise before dispatch; this cannot attest an unmodified process.
        internal fun hookFailureOrNull(results: Set<AuthenticationResult>): Set<AuthenticationResult>? =
            hookDetectionFailure(results, reference.get())

        @MainThread
        @CallSuper
        @Throws(BiometricAuthException::class) // Retained for Java source compatibility; no longer thrown here.
        open fun onSucceeded(confirmed: Set<AuthenticationResult>) {
            // Rejection happens before dispatch, even when a consumer does not call super.
        }

        @MainThread
        open fun onCanceled(canceled: Set<AuthenticationResult>) {
        }

        @MainThread
        open fun onFailed(
            canceled: Set<AuthenticationResult>
        ) {
        }

        /** Advisory signal only: it neither authorizes nor denies the operation by itself. */
        @MainThread
        open fun onSupplementalResult(result: SupplementalBiometricResult) {}

        @MainThread
        open fun onUIOpened() {
        }

        @MainThread
        open fun onUIClosed() {
        }
    }

    class Builder(
        private val biometricAuthRequest: BiometricAuthRequest,
        private val activity: FragmentActivity? = null
    ) {
        companion object {
            init {
                //lazy init
                init()
            }
        }

        private val availableTypeCache = AuthFlowRouteCache<Boolean, HashSet<BiometricType>>(serializeCalculation = false)
        private val filteredTypeCache = AuthFlowRouteCache<Boolean, Set<BiometricType>>(serializeCalculation = false)
        private val disabledModuleTags = Collections.synchronizedSet(HashSet<Int>())
        private val disabledBiometricTypes = Collections.synchronizedSet(HashSet<BiometricType>())
        internal var deferSoftwarePreparation = false
        internal var nativeErrorReceivedAt: Long? = null
        @Volatile internal var authFlowActive: () -> Boolean = { true }
        private val selectedRouteCache =
            AuthFlowRouteCache<BiometricType, SelectedBiometricRoute?>(serializeCalculation = false)
        private val legacyPriorityCache = AuthFlowRouteCache<BiometricType, Boolean>(serializeCalculation = false)
        private val legacyModuleCache = AuthFlowRouteCache<BiometricType, BiometricModule?>(serializeCalculation = false)
        private val effectiveEnrollTypesCache = AuthFlowRouteCache<Unit, Set<BiometricType>>(serializeCalculation = false)
        private val enrollmentStateCache = AuthFlowRouteCache<BiometricType, BiometricAuthState>(serializeCalculation = false)
        private val systemEnrollmentHardwareCache = AuthFlowRouteCache<BiometricType, Boolean>(serializeCalculation = false)
        private data class DeferredRoutes(
            val admitted: Map<BiometricType, SelectedBiometricRoute> = emptyMap(),
            val excluded: Set<BiometricType> = emptySet()
        )
        @Volatile private var deferredRoutes = DeferredRoutes()
        private val primaryAvailableTypes: HashSet<BiometricType>
            get() = availableTypeCache.getOrPut(true) {
            val types = HashSet<BiometricType>()
            val isNewBiometric =
                HardwareAccessImpl.getInstance(biometricAuthRequest).isNewBiometricApi
            val api =
                if (isNewBiometric) BiometricApi.BIOMETRIC_API else BiometricApi.LEGACY_API
            if (biometricAuthRequest.type == BiometricType.BIOMETRIC_ANY) {
                for (type in BiometricType.entries) {
                    if (type == BiometricType.BIOMETRIC_ANY)
                        continue
                    val request = biometricAuthRequest.withApi(api).withType(type)
                    if (enroll) {
                        val combinedReady = !isNewBiometric &&
                                isAnyEnrollTypeReady(
                                    type,
                                    BiometricManagerCompat.getAuthSnapshot(
                                        request.withProvider(
                                            provider = BiometricProviderType.COMBINED
                                        )
                                    )
                                )
                        val hardwareReady = !combinedReady &&
                                isAnyEnrollTypeReady(
                                    type,
                                    BiometricManagerCompat.getAuthSnapshot(
                                        request.withProvider(
                                            provider = BiometricProviderType.HARDWARE
                                        )
                                    )
                                )
                        if (combinedReady || hardwareReady) {
                            types.add(type)
                        }
                    } else if (BiometricManagerCompat.getAuthSnapshot(request).available) {
                        types.add(type)
                    }
                }
            } else {
                if (enroll) {
                    val combinedReady = !isNewBiometric &&
                            isAnyEnrollTypeReady(
                                biometricAuthRequest.type,
                                BiometricManagerCompat.getAuthSnapshot(
                                    biometricAuthRequest.withProvider(provider = BiometricProviderType.COMBINED)
                                )
                            )
                    val hardwareReady = !combinedReady &&
                            isAnyEnrollTypeReady(
                                biometricAuthRequest.type,
                                BiometricManagerCompat.getAuthSnapshot(
                                    biometricAuthRequest.withProvider(provider = BiometricProviderType.HARDWARE)
                                )
                            )
                    if (combinedReady || hardwareReady)
                        types.add(biometricAuthRequest.type)
                } else if (BiometricManagerCompat.getAuthSnapshot(biometricAuthRequest).available)
                    types.add(biometricAuthRequest.type)

            }
            types
        }
        private val secondaryAvailableTypes: HashSet<BiometricType>
            get() = availableTypeCache.getOrPut(false) {
            val types = HashSet<BiometricType>()
            if (HardwareAccessImpl.getInstance(biometricAuthRequest).isNewBiometricApi) {
                if (biometricAuthRequest.type == BiometricType.BIOMETRIC_ANY) {
                    for (type in BiometricType.entries) {
                        if (type == BiometricType.BIOMETRIC_ANY)
                            continue
                        val request =
                            biometricAuthRequest.withApi(BiometricApi.LEGACY_API).withType(type)
                        val combinedReady = enroll &&
                                isAnyEnrollTypeReady(
                                    type,
                                    BiometricManagerCompat.getAuthSnapshot(
                                        request.withProvider(
                                            provider = BiometricProviderType.COMBINED
                                        )
                                    )
                                )
                        if (combinedReady) {
                            types.add(type)
                        } else if (!enroll &&
                            BiometricManagerCompat.getAuthSnapshot(request).available
                        ) {
                            types.add(type)
                        }
                }
                } else {
                    val combinedReady = enroll &&
                            isAnyEnrollTypeReady(
                                biometricAuthRequest.type,
                                BiometricManagerCompat.getAuthSnapshot(
                                    biometricAuthRequest.withProvider(provider = BiometricProviderType.COMBINED)
                                )
                            )
                    if (combinedReady) {
                        types.add(biometricAuthRequest.type)
                    } else if (BiometricManagerCompat.getAuthSnapshot(biometricAuthRequest).available)
                        types.add(biometricAuthRequest.type)
                }
                types.removeAll(primaryAvailableTypes)
            }
            types
        }

        private fun isAnyEnrollTypeReady(
            type: BiometricType,
            snapshot: BiometricAuthSnapshot
        ): Boolean {
            val module = LegacyBiometric.getSelectedBiometricModule(
                type,
                snapshot.request.provider,
                enroll,
                getDisabledModuleTags()
            )
            val moduleState = module?.getModuleState()
            val shouldPreferModule = snapshot.request.provider == BiometricProviderType.SOFTWARE ||
                    !hasBiometricPromptHardwareType(type) ||
                    (!shouldPreferSystemHardwareFace(type) &&
                            (module?.priority ?: BiometricModule.PRIORITY_SYSTEM_HARDWARE) >
                            BiometricModule.PRIORITY_SYSTEM_HARDWARE)
            return isSetupRouteSelectable(snapshot.state, moduleState, shouldPreferModule)
        }

        private var silentAuth = false
        private var authWindowSec = 30

        private var dialogTitle: CharSequence? = null
            get() {
                if (field.isNullOrEmpty())
                    field = BiometricTitle.getRelevantTitle(
                        getActivity() ?: getContext(),
                        if (trustPolicyFlowId == -1L) getAllAvailableTypes() else preparedAvailableTypes
                    )
                return field
            }

        private var dialogSubtitle: CharSequence? = null

        private var dialogDescription: CharSequence? = null

        private var negativeButtonText: CharSequence? = null

        private lateinit var multiWindowSupport: MultiWindowSupport
        private var inBubbleTask = false

        private var notificationEnabled = false

        private var backgroundBiometricIconsEnabled = true
        internal var foregroundFeedback: ForegroundFeedbackSession? = null
        private var biometricFeedbackOptions = BiometricFeedbackOptions()

        /** Configure the app-window message card. Does not alter biometric routing or trust. */
        fun setBiometricFeedbackOptions(options: BiometricFeedbackOptions): Builder {
            biometricFeedbackOptions = options
            return this
        }

        fun getBiometricFeedbackOptions(): BiometricFeedbackOptions = biometricFeedbackOptions

        internal var systemPromptOwnsUi = false
        internal var frameworkFingerprintUiOwner = AuthenticationUiOwner.UNKNOWN

        /**
         * Presentation override for a framework FingerprintManager stage only.
         * Set SYSTEM only after verifying that this runtime/backend supplies a complete prompt.
         * UNKNOWN keeps automatic compatibility behavior; it does not probe by window focus.
         * Mixed requests run a system-owned fingerprint stage before remaining compat routes.
         * Software and known missing-system-UI exceptions retain compat UI.
         * This setting never changes authentication routing or accepted sensor/crypto results.
         */
        fun setFrameworkFingerprintUiOwner(owner: AuthenticationUiOwner): Builder {
            frameworkFingerprintUiOwner = owner
            return this
        }

        private var biometricCryptographyPurpose: BiometricCryptographyPurpose? = null
        private var cryptoFallbackAllowed: Boolean = false

        @ColorInt
        private var colorNavBar: Int = Color.TRANSPARENT

        @ColorInt
        private var dividerColor: Int = Color.TRANSPARENT

        @ColorInt
        private var colorStatusBar: Int = Color.TRANSPARENT

        @Volatile private var isTruncateChecked: Boolean? = null


        private var isDeviceCredentialFallbackAllowed: Boolean = true
        private var forcedCredentialOverride: Boolean? = null
        @Volatile private var lastKnownPermanentLockout = false
        @Volatile private var preparedCredentialStatus: CredentialStatus? = null
        @Volatile private var preparedAvailableTypes: Set<BiometricType> = emptySet()

        internal data class CredentialStatus(val deviceSecure: Boolean, val permanentlyLocked: Boolean)

        internal fun readCredentialStatus(): CredentialStatus {
            return CredentialStatus(
                BiometricManagerCompat.isDeviceSecureAvailable(),
                BiometricManagerCompat.isBiometricSensorPermanentlyLocked(biometricAuthRequest)
            )
        }

        internal fun acceptPreparedStatus(types: Set<BiometricType>, status: CredentialStatus) {
            preparedAvailableTypes = types
            preparedCredentialStatus = status
            lastKnownPermanentLockout = status.permanentlyLocked
        }

        internal fun availableTypesForCancellation(): Set<BiometricType> =
            preparedAvailableTypes.ifEmpty { setOf(biometricAuthRequest.type) }

        internal fun wasPermanentlyLocked(): Boolean = preparedCredentialStatus?.permanentlyLocked == true
        internal var enroll: Boolean = false
        internal var extras: Bundle? = null
        internal var behaviorAuthMode: BehaviorAuthMode = BehaviorAuthMode.EXPLICIT
        private var behaviorTypingView = WeakReference<TextView?>(null)
        private var behaviorSignatureContainer = WeakReference<ViewGroup?>(null)
        private val confirmedEnrollTypes = Collections.synchronizedSet(LinkedHashSet<BiometricType>())
        private val rollbackEligibleEnrollTypes = LinkedHashSet<BiometricType>()
        private fun newEnrollmentSession() = EnrollmentRollbackSession<SoftwareBiometricModule>(
            start = { it.trackEnrollmentRollback() },
            finish = { module, scope, succeeded -> module.finishEnrollmentRollback(scope, succeeded) }
        )
        private var softwareEnrollments = newEnrollmentSession()
        internal var voicePhrase: CharSequence? = null
        internal var isUIOpened = AtomicBoolean(false)
        internal var credentialConfirmed = false
        private var observer: Observer<Activity?>? = null

        private fun observeWindowColors() {
            if (observer != null) return
            val activityObserver = object : Observer<Activity?> {
                override fun onChanged(context: Activity?) {
                    val host = context ?: return
                    if (host !== getActivity()) return
                    colorNavBar = host.window.navigationBarColor
                    colorStatusBar = host.window.statusBarColor
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        dividerColor = host.window.navigationBarDividerColor
                    }
                    if (!isSilentAuthEnabled() && isUIOpened.get()) {
                        ExecutorHelper.post {
                            if (observer !== this || !isUIOpened.get() || host !== getActivity()) return@post
                            StatusBarTools.setNavBarAndStatusBarColors(
                                host.window,
                                DialogMainColor.getColor(
                                    getContext(),
                                    DarkLightThemes.isNightModeCompatWithInscreen(getContext())
                                ),
                                DialogMainColor.getColor(
                                    getContext(),
                                    !DarkLightThemes.isNightModeCompatWithInscreen(getContext())
                                ),
                                getStatusBarColor()
                            )
                        }
                    }
                }
            }
            observer = activityObserver
            ExecutorHelper.post {
                if (observer === activityObserver) {
                    getActivity()?.let { AndroidContext.resumedActivityLiveData.observe(it, activityObserver) }
                }
            }
        }

        init {

            scanRuntimeHooks()
            getActivity()?.let { context ->
                this.colorNavBar = context.window.navigationBarColor
                this.colorStatusBar = context.window.statusBarColor
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    dividerColor = context.window.navigationBarDividerColor
                }
            }
            if (API_ENABLED) {
                multiWindowSupport = activity?.let { MultiWindowSupport.get(it) } ?: MultiWindowSupport.get()
            }
        }

        constructor(dummy_reference: FragmentActivity) : this(
            BiometricAuthRequest.default(), dummy_reference
        )

        /* Access modifiers changed, original: protected */
        @Throws(Throwable::class)
        fun finalize() {
            release()
        }

        fun release() {
            val releasedObserver = observer
            observer = null
            ExecutorHelper.post {
                releasedObserver?.let(AndroidContext.resumedActivityLiveData::removeObserver)
            }
        }

        fun isSilentAuthEnabled(): Boolean {
            return silentAuth
        }

        fun getAuthWindow(): Int {
            return authWindowSec
        }

        fun enableSilentAuth(authWindowSec: Int = 30) {
            if (authWindowSec <= 0) throw IllegalArgumentException("AuthWindow can't be less then 0")
            BiometricLoggerImpl.e(
                "WARNING!!!\n" +
                        "Keep in mind - some devices use the own built-in animations " +
                        "(camera animation for Face/Iris) or other type of UI " +
                        "(Fingerprint dialog and/or under-screen recognition animation)" +
                        " and this leads to the uselessness of this function. " +
                        "Use BiometricManagerCompat.isSilentAuthAvailable() to check"
            )
            this.authWindowSec = authWindowSec
            this.silentAuth = true
        }

        fun shouldAutoVerifyCryptoAfterSuccess(): Boolean {
            //Known issue: at least "OnePlus 9" call onSuccess when "Cancel" button clicked,
            //so checking the crypto is only the way to check real reason - it's Canceled or Success

            //Due to limitations, applicable only for Fingerprint
            //https://github.com/sergeykomlach/AdvancedBiometricPromptCompat/issues/305
            return when (biometricCryptographyPurpose?.purpose) {
                null if deviceInfo?.model?.startsWith(
                    "OnePlus 9",
                    ignoreCase = true
                ) == true &&
                        Build.VERSION.SDK_INT in Build.VERSION_CODES.S..Build.VERSION_CODES.S_V2 &&
                        getAllAvailableTypes().contains(BiometricType.BIOMETRIC_FINGERPRINT)
                    -> {
                    biometricCryptographyPurpose =
                        BiometricCryptographyPurpose(BiometricCryptographyPurpose.ENCRYPT)
                    true
                }

                BiometricCryptographyPurpose.ENCRYPT if deviceInfo?.model?.startsWith(
                    "OnePlus 9",
                    ignoreCase = true
                ) == true &&
                        Build.VERSION.SDK_INT in Build.VERSION_CODES.S..Build.VERSION_CODES.S_V2 &&
                        getAllAvailableTypes().contains(BiometricType.BIOMETRIC_FINGERPRINT)
                    -> {
                    true
                }

                else -> false
            }
        }

        fun getTitle(): CharSequence? {
            return dialogTitle
        }

        fun getSubtitle(): CharSequence? {
            return dialogSubtitle
        }

        fun getDescription(): CharSequence? {
            return dialogDescription
        }

        fun getNavBarColor(): Int {
            return colorNavBar
        }

        fun getStatusBarColor(): Int {
            return colorStatusBar
        }

        fun getDividerColor(): Int {
            return dividerColor
        }

        fun forceDeviceCredential(): Boolean {
            if (!isDeviceCredentialFallbackAllowed()) return false
            return forcedCredentialOverride ?: preparedCredentialStatus?.permanentlyLocked
                ?: lastKnownPermanentLockout
        }

        fun isDeviceCredentialFallbackAllowed(): Boolean {
            return isDeviceCredentialFallbackAllowed && (preparedCredentialStatus?.deviceSecure
                ?: (trustPolicyFlowId == -1L && BiometricManagerCompat.isDeviceSecureAvailable()))
        }


        fun isBackgroundBiometricIconsEnabled(): Boolean {
            return backgroundBiometricIconsEnabled
        }

        fun isNotificationEnabled(): Boolean {
            return notificationEnabled
        }

        fun isTruncateChecked(): Boolean {
            if (isTruncateChecked == null) {
                isTruncateChecked = false
                val check = Runnable {
                    TruncatedTextFix.recalculateTexts(
                        this,
                        object : TruncatedTextFix.OnTruncateChecked {
                            override fun onDone() {
                                isTruncateChecked = true
                            }
                        })
                }
                // Avoid an extra 50 ms readiness poll when preparation completes on this thread.
                if (Looper.myLooper() == Looper.getMainLooper()) check.run()
                else ExecutorHelper.post(check)
            }
            return isTruncateChecked == true
        }

        fun getPrimaryAvailableTypes(): Set<BiometricType> {
            return getEnabledTypes(primary = true)
                .filterNotTo(HashSet()) { shouldRouteLegacyBeforeSystemHardware(it) }
        }

        fun getSecondaryAvailableTypes(): Set<BiometricType> {
            val types = getEnabledTypes(primary = false).toHashSet()
            getEnabledTypes(primary = true)
                .filterTo(types) { shouldRouteLegacyBeforeSystemHardware(it) }
            deferredRoutes.admitted.keys.filterTo(types) { admittedDeferredRoute(it) != null }
            return types
        }

        fun getAllAvailableTypes(): Set<BiometricType> {
            val types = HashSet<BiometricType>()
            types.addAll(getPrimaryAvailableTypes())
            types.addAll(getSecondaryAvailableTypes())
            return types
        }

        internal fun getEffectiveAvailableTypes(): Set<BiometricType> {
            val allTypes = getAllAvailableTypes()
            if (!enroll) {
                return allTypes
            }

            return effectiveEnrollTypesCache.getOrPut(Unit) {
                resolveEffectiveEnrollTypes(
                    types = allTypes,
                    hasSystemHardware = ::hasSystemEnrollmentHardware,
                    keepSystemType = { type ->
                        shouldKeepSystemEnrollType(selectedRoute(type)) ||
                                shouldRouteLegacyBeforeSystemHardware(type)
                    },
                    isActive = { type ->
                        val state = getEnrollmentState(type)
                        state.hardwareDetected && !state.lockedOut && !state.permanentlyLocked
                    }
                ).toSet()
            }
        }

        internal fun hasSystemEnrollmentHardware(type: BiometricType): Boolean =
            systemEnrollmentHardwareCache.getOrPut(type) {
                BiometricManagerCompat.getAuthSnapshot(
                    BiometricAuthRequest.default().withType(type).withProvider(BiometricProviderType.HARDWARE)
                ).state.hardwareDetected
            }

        private fun getEnrollmentState(type: BiometricType): BiometricAuthState =
            enrollmentStateCache.getOrPut(type) { selectedTypeSnapshot(type, ignoreCameraCheck = false).state }

        internal fun prepareRoutes(): Set<BiometricType> {
            val types = getAllAvailableTypes()
            types.forEach {
                selectedRoute(it)
                preparedLegacyModule(it)
            }
            if (enroll) {
                types.forEach { hasSystemEnrollmentHardware(it) }
                types.forEach { getEnrollmentState(it) }
                getEffectiveAvailableTypes()
            }
            return types
        }

        internal var authenticationStageTypes: Set<BiometricType>? = null
        private var configuredTrustPolicy = BiometricTrustPolicy()
        private var activeTrustPolicy: BiometricTrustPolicy? = null
        private var trustPolicyFlowId = -1L
        private var admittedAuthenticationTypes: Set<BiometricType>? = null

        /** Changes apply to the next operation; an active flow keeps its original policy. */
        fun setBiometricTrustPolicy(policy: BiometricTrustPolicy): Builder {
            configuredTrustPolicy = policy
            if (activeTrustPolicy == null) selectedRouteCache.invalidate()
            return this
        }

        fun getBiometricTrustPolicy(): BiometricTrustPolicy = activeTrustPolicy ?: configuredTrustPolicy

        internal fun getEligibleAuthenticationTypes(): Set<BiometricType> =
            getEffectiveAvailableTypes().filterTo(LinkedHashSet()) { type ->
                val route = selectedRoute(type) ?: return@filterTo false
                val manager = (route.module as? SoftwareBiometricModule)?.manager
                runCatching {
                    getBiometricTrustPolicy().permitsAuthentication(type, route.provider == BiometricProviderType.HARDWARE,
                        manager?.securityProfile, manager?.trustedCaptureForAuthentication == true)
                }.getOrDefault(false)
            }

        internal fun freezeAuthenticationRequirements() {
            if (!enroll && admittedAuthenticationTypes == null) {
                admittedAuthenticationTypes = getEligibleAuthenticationTypes().toSet()
            }
        }

        internal fun getRequiredAuthenticationTypes(): Set<BiometricType> =
            admittedAuthenticationTypes ?: getEligibleAuthenticationTypes()

        internal fun getAuthenticationCompletionTypes(): Set<BiometricType> {
            if (isBehaviorFirstStage()) return setOf(BiometricType.BIOMETRIC_BEHAVIOR)
            return resolveAuthenticationTrustScope(biometricAuthRequest.confirmation, admittedAuthenticationTypes,
                getEligibleAuthenticationTypes(), authenticationStageTypes)
        }

        internal fun missingAuthenticationTrustResults(): Set<AuthenticationResult> {
            if (enroll || biometricAuthRequest.confirmation != BiometricConfirmation.ALL) return emptySet()
            return (getAuthenticationCompletionTypes() - getAuthenticationExecutionTypes()).mapTo(LinkedHashSet()) {
                AuthenticationResult(it, reason = AuthenticationFailureReason.INSUFFICIENT_TRUST,
                    description = getContext().getString(R.string.biometriccompat_authentication_trust_required))
            }
        }

        internal fun getAuthenticationExecutionTypes(): Set<BiometricType> {
            val available = getEffectiveAvailableTypes()
            if (enroll) return available
            val eligible = getEligibleAuthenticationTypes()
            val executable = available.filterTo(LinkedHashSet()) { type ->
                type in eligible || (type == BiometricType.BIOMETRIC_BEHAVIOR && selectedRoute(type) != null)
            }
            return authenticationStageTypes?.let { executable.intersect(it) } ?: executable
        }

        internal fun isBehaviorFirstStage(): Boolean = !enroll &&
            authenticationStageTypes == setOf(BiometricType.BIOMETRIC_BEHAVIOR)

        internal fun getEnrollScopeTypes(): Set<BiometricType> {
            return getAllAvailableTypes()
        }

        internal fun getCurrentEnrollCompletionTypes(): Set<BiometricType> {
            if (!enroll) {
                return getAllAvailableTypes()
            }
            val effectiveTypes = getEffectiveAvailableTypes()
            return getEnrollScopeTypes().filterTo(LinkedHashSet()) { type ->
                if (confirmedEnrollTypes.contains(type)) {
                    return@filterTo true
                }
                val state = getEnrollmentState(type)
                state.hardwareDetected && !state.lockedOut && !state.permanentlyLocked &&
                        (state.enrolled || effectiveTypes.contains(type))
            }
        }

        internal fun getPendingEnrollTypes(): Set<BiometricType> {
            return if (!enroll) {
                getAllAvailableTypes()
            } else {
                val effectiveTypes = getEffectiveAvailableTypes()
                val softwareTypes = effectiveTypes.filterTo(LinkedHashSet()) {
                    selectedRoute(it)?.provider == BiometricProviderType.SOFTWARE
                }
                if (softwareTypes.isNotEmpty()) return softwareTypes
                if (effectiveTypes.isNotEmpty()) {
                    effectiveTypes
                } else {
                    getCurrentEnrollCompletionTypes().filterTo(LinkedHashSet()) { type ->
                        !confirmedEnrollTypes.contains(type)
                    }
                }
            }
        }

        internal fun hasSoftwareEnrollTargets(): Boolean = enroll &&
                getPendingEnrollTypes().any { selectedRoute(it)?.provider == BiometricProviderType.SOFTWARE }

        internal fun getPreSatisfiedEnrollResults(): Set<AuthenticationResult> {
            if (!enroll) {
                return emptySet()
            }
            return resolvePreSatisfiedEnrollResults(
                scopeTypes = getCurrentEnrollCompletionTypes(),
                pendingTypes = getPendingEnrollTypes()
            ) { type ->
                getEnrollmentState(type).enrolled
            }
        }

        internal fun resetEnrollSessionState() {
            softwareEnrollments.finish(succeeded = false)
            softwareEnrollments = newEnrollmentSession()
            confirmedEnrollTypes.clear()
            rollbackEligibleEnrollTypes.clear()
        }

        internal fun markEnrollConfirmedResults(results: Collection<AuthenticationResult>) {
            results.forEach { result ->
                val type = result.type ?: return@forEach
                if (!getEnrollScopeTypes().contains(type)) {
                    return@forEach
                }
                confirmedEnrollTypes.add(type)
                val route = selectedRoute(type)
                if (route?.provider == BiometricProviderType.SOFTWARE) {
                    rollbackEligibleEnrollTypes.add(type)
                }
            }
        }

        internal fun markEnrollConfirmedTypes(types: Collection<BiometricType>) {
            types.forEach { type ->
                if (getEnrollScopeTypes().contains(type)) {
                    confirmedEnrollTypes.add(type)
                }
            }
        }

        internal fun getConfirmedEnrollTypes(): Set<BiometricType> {
            return synchronized(confirmedEnrollTypes) { LinkedHashSet(confirmedEnrollTypes) }
        }

        internal fun getRollbackEligibleEnrollTypes(): Set<BiometricType> {
            return LinkedHashSet(rollbackEligibleEnrollTypes)
        }

        internal fun trackSoftwareEnrollment(type: BiometricType) {
            if (!enroll) return
            val module = selectedRoute(type)?.module as? SoftwareBiometricModule ?: return
            softwareEnrollments.track(module)
        }

        internal fun hasTrackedEnrollments(): Boolean = softwareEnrollments.hasTrackedProviders()

        internal fun finishEnrollSession(succeeded: Boolean) {
            // Persistence can precede success delivery in every UI path, including staged
            // and silent setup. Commit only when the entire outer setup succeeds.
            softwareEnrollments.finish(succeeded)
        }

        internal fun getEnrolledHardwareScopeTypes(): Set<BiometricType> {
            return getEnrollScopeTypes().filterTo(LinkedHashSet()) { type ->
                BiometricManagerCompat.getAuthSnapshot(
                    BiometricAuthRequest.default()
                        .withType(type)
                        .withProvider(BiometricProviderType.HARDWARE)
                ).state.enrolled
            }
        }

        internal fun areSelectedTypesLockedOut(ignoreCameraCheck: Boolean = true): Boolean {
            val types = getEffectiveAvailableTypes()
            return types.isNotEmpty() && types.all { isSelectedTypeLockedOut(it, ignoreCameraCheck) }
        }

        internal fun getSelectedTypePermissions(type: BiometricType): List<String> {
            return selectedRoute(type)?.permissions ?: emptyList()
        }

        internal fun isSelectedTypeAvailable(type: BiometricType, ignoreCameraCheck: Boolean): Boolean {
            val snapshot = selectedTypeSnapshot(type, ignoreCameraCheck)
            return snapshot.available &&
                    !snapshot.state.lockedOut &&
                    !snapshot.state.permanentlyLocked
        }

        internal fun getDisabledModuleTags(): Set<Int> {
            val excluded = HashSet(disabledModuleTags)
            if (enroll) return excluded
            val policy = getBiometricTrustPolicy()
            for (type in BiometricType.entries) {
                if (type == BiometricType.BIOMETRIC_ANY) continue
                for (module in LegacyBiometric.getAvailableBiometricModules(type, biometricAuthRequest.provider)) {
                    val software = module as? SoftwareBiometricModule ?: continue
                    val manager = software.manager
                    val allowed = runCatching {
                        manager != null && policy.permitsExecution(type, false,
                            manager.securityProfile, manager.trustedCaptureForAuthentication,
                            supplemental = type == BiometricType.BIOMETRIC_BEHAVIOR)
                    }.getOrDefault(false)
                    if (!allowed) excluded.add(module.tag())
                }
            }
            return excluded
        }

        internal fun beginAuthFlow(authFlowId: Long) {
            deferredRoutes = DeferredRoutes()
            observeWindowColors()
            preparedCredentialStatus = null
            preparedAvailableTypes = emptySet()
            credentialConfirmed = false
            admittedAuthenticationTypes = null
            activeTrustPolicy = configuredTrustPolicy
            trustPolicyFlowId = authFlowId
            authenticationStageTypes = null
            deferSoftwarePreparation = false
            disabledModuleTags.clear()
            disabledBiometricTypes.clear()
            selectedRouteCache.beginFlow(authFlowId)
            availableTypeCache.beginFlow(authFlowId)
            filteredTypeCache.beginFlow(authFlowId)
            legacyPriorityCache.beginFlow(authFlowId)
            legacyModuleCache.beginFlow(authFlowId)
            effectiveEnrollTypesCache.beginFlow(authFlowId)
            enrollmentStateCache.beginFlow(authFlowId)
            systemEnrollmentHardwareCache.beginFlow(authFlowId)
        }

        internal fun endAuthFlow(authFlowId: Long) {
            if (trustPolicyFlowId == authFlowId) {
                release()
                preparedCredentialStatus = null
                authFlowActive = { false }
                deferredRoutes = DeferredRoutes()
                preparedAvailableTypes = emptySet()
                admittedAuthenticationTypes = null
                activeTrustPolicy = null
                trustPolicyFlowId = -1L
            }
            authenticationStageTypes = null
            selectedRouteCache.endFlow(authFlowId)
            availableTypeCache.endFlow(authFlowId)
            filteredTypeCache.endFlow(authFlowId)
            legacyPriorityCache.endFlow(authFlowId)
            legacyModuleCache.endFlow(authFlowId)
            effectiveEnrollTypesCache.endFlow(authFlowId)
            enrollmentStateCache.endFlow(authFlowId)
            systemEnrollmentHardwareCache.endFlow(authFlowId)
        }

        internal fun invalidateAvailableTypes() {
            availableTypeCache.invalidate()
            isTruncateChecked = null
            invalidateSelectedRoutes()
        }

        internal fun invalidateSelectedRoutes() {
            selectedRouteCache.invalidate()
            filteredTypeCache.invalidate()
            legacyPriorityCache.invalidate()
            legacyModuleCache.invalidate()
            effectiveEnrollTypesCache.invalidate()
            enrollmentStateCache.invalidate()
            systemEnrollmentHardwareCache.invalidate()
            IconStateHelper.refreshAvailability()
        }

        internal fun disableBiometricModule(module: BiometricModule) {
            disabledModuleTags.add(module.tag())
            invalidateSelectedRoutes()
            BiometricLoggerImpl.d("BiometricPromptCompat.Builder disabled module=${module.javaClass.simpleName}")
        }

        internal fun disableBiometricType(type: BiometricType) {
            disabledBiometricTypes.add(type)
            invalidateSelectedRoutes()
            BiometricLoggerImpl.d("BiometricPromptCompat.Builder disabled biometric type=$type")
        }

        internal fun disableBiometricForPermissionFailure(result: AuthenticationResult): Boolean {
            if (result.reason != AuthenticationFailureReason.MISSING_PERMISSIONS_ERROR) {
                return false
            }
            val type = result.type ?: return false
            val module = selectedRoute(type)?.module ?: return false
            disableBiometricModule(module)
            return true
        }

        private fun getEnabledTypes(primary: Boolean): Set<BiometricType> =
            readEnabledTypes(primary) - deferredRoutes.excluded

        private fun readEnabledTypes(primary: Boolean): Set<BiometricType> = filteredTypeCache.getOrPut(primary) {
            val types = if (primary) primaryAvailableTypes else secondaryAvailableTypes
            val enabledTypes = types.filterNotTo(HashSet()) { disabledBiometricTypes.contains(it) }
            // Availability describes hardware/providers, not authorization policy.
            val disabled = HashSet(disabledModuleTags)
            if (disabled.isEmpty()) {
                return@getOrPut enabledTypes
            }
            enabledTypes.filterTo(HashSet()) { type ->
                LegacyBiometric.getSelectedBiometricModule(
                    type,
                    biometricAuthRequest.provider,
                    enroll,
                    disabled
                ) != null || hasBiometricPromptHardwareType(type)
            }
        }

        private fun hasBiometricPromptHardwareType(type: BiometricType): Boolean {
            if (biometricAuthRequest.provider == BiometricProviderType.SOFTWARE ||
                Build.VERSION.SDK_INT < Build.VERSION_CODES.P
            ) {
                return false
            }
            val request = biometricAuthRequest
                .withApi(BiometricApi.BIOMETRIC_API)
                .withType(type)
                .withProvider(BiometricProviderType.HARDWARE)
            val snapshot = BiometricManagerCompat.getAuthSnapshot(request)
            return if (enroll) {
                snapshot.readyForEnroll
            } else {
                snapshot.available
            }
        }

        private fun isSelectedTypeLockedOut(
            type: BiometricType,
            ignoreCameraCheck: Boolean
        ): Boolean {
            return selectedTypeSnapshot(type, ignoreCameraCheck).state.lockedOut
        }

        private fun selectedTypeSnapshot(
            type: BiometricType,
            ignoreCameraCheck: Boolean
        ): BiometricAuthSnapshot {
            return BiometricManagerCompat.getAuthSnapshot(routeAwareRequest(type), ignoreCameraCheck)
        }

        private fun routeAwareRequest(type: BiometricType): BiometricAuthRequest {
            val route = selectedRoute(type) ?: return biometricAuthRequest.withType(type)
            return biometricAuthRequest.withType(type)
                .withApi(route.api)
                .withProvider(route.provider)
        }

        internal fun selectedRoute(type: BiometricType): SelectedBiometricRoute? {
            val deferred = deferredRoutes
            if (type in deferred.excluded) return null
            if (type in deferred.admitted) return admittedDeferredRoute(type)
            return selectedRouteCache.getOrPut(type) {
                readSelectedRoute(type, getDisabledModuleTags(), shouldRouteLegacyBeforeSystemHardware(type))
            }
        }

        internal fun preferredPreparedLegacyModule(types: Collection<BiometricType>): Pair<BiometricType, BiometricModule>? =
            types.mapNotNull { type -> preparedLegacyModule(type)?.let { type to it } }
                .minWithOrNull(compareByDescending<Pair<BiometricType, BiometricModule>> { it.second.priority }
                    .thenBy { it.first.ordinal })

        private fun preparedLegacyModule(type: BiometricType): BiometricModule? {
            val deferred = deferredRoutes
            if (type in deferred.excluded) return null
            if (type in deferred.admitted) return admittedDeferredRoute(type)?.module
            return legacyModuleCache.getOrPut(type) {
                LegacyBiometric.getSelectedBiometricModule(type, biometricAuthRequest.provider, enroll, getDisabledModuleTags())
            }
        }

        private fun admittedDeferredRoute(type: BiometricType): SelectedBiometricRoute? =
            deferredRoutes.admitted[type]?.takeUnless {
                type in disabledBiometricTypes || it.module?.tag() in disabledModuleTags
            }

        internal fun publishDeferredRoutes(
            admitted: Map<BiometricType, SelectedBiometricRoute>,
            excluded: Set<BiometricType>
        ) {
            deferredRoutes = DeferredRoutes(admitted, excluded)
            IconStateHelper.refreshAvailability()
        }

        internal fun readDeferredSoftwareRoutes(
            nativeTypes: Set<BiometricType>,
            skippedModuleTags: Set<Int>
        ): Map<BiometricType, SelectedBiometricRoute> {
            val excluded = getDisabledModuleTags() + skippedModuleTags
            val requestType = biometricAuthRequest.type
            val stageTypes = authenticationStageTypes
            val types = if (requestType == BiometricType.BIOMETRIC_ANY) BiometricType.entries
                .filterNot { it == BiometricType.BIOMETRIC_ANY } else listOf(requestType)
            return types.filter {
                it !in nativeTypes && it !in disabledBiometricTypes && (stageTypes == null || it in stageTypes)
            }.mapNotNull { type ->
                val route = readSelectedRoute(type, excluded, readLegacyRoutePriority(type, excluded))
                val manager = (route?.module as? SoftwareBiometricModule)?.manager
                if (route?.provider == BiometricProviderType.SOFTWARE &&
                    (type == BiometricType.BIOMETRIC_BEHAVIOR || getBiometricTrustPolicy().permitsAuthentication(
                        type, false, manager?.securityProfile, manager?.trustedCaptureForAuthentication == true))
                ) type to route else null
            }.toMap()
        }

        private fun readSelectedRoute(
            type: BiometricType,
            excluded: Set<Int>,
            preferLegacy: Boolean
        ): SelectedBiometricRoute? {
                val biometricPromptRoute = biometricPromptRoute(type)
                val legacyHardwareRoute = legacyHardwareRoute(type, excluded)
                val fallbackRoute = fallbackRoute(type, excluded)
                return pickSelectedBiometricRoute(
                    requestApi = biometricAuthRequest.api,
                    preferSystemFaceHardware = shouldPreferSystemHardwareFace(type),
                    preferHighPrioritySoftware = preferLegacy,
                    biometricPromptRoute = biometricPromptRoute,
                    legacyHardwareRoute = legacyHardwareRoute,
                    fallbackRoute = fallbackRoute,
                    requestType = biometricAuthRequest.type
                )
        }

        private fun biometricPromptRoute(type: BiometricType): SelectedBiometricRoute? {
            if (!hasBiometricPromptHardwareType(type)) {
                return null
            }
            val request = biometricAuthRequest
                .withApi(BiometricApi.BIOMETRIC_API)
                .withType(type)
                .withProvider(BiometricProviderType.HARDWARE)
            return SelectedBiometricRoute(
                type = type,
                provider = BiometricProviderType.HARDWARE,
                usesBiometricPromptHardware = true,
                permissions = BiometricManagerCompat.getUsedPermissions(listOf(type), request, enroll),
                api = BiometricApi.BIOMETRIC_API
            )
        }

        private fun legacyHardwareRoute(type: BiometricType, excluded: Set<Int>): SelectedBiometricRoute? {
            if (biometricAuthRequest.api == BiometricApi.BIOMETRIC_API ||
                biometricAuthRequest.provider == BiometricProviderType.SOFTWARE
            ) {
                return null
            }
            val module = LegacyBiometric.getSelectedBiometricModule(
                type,
                BiometricProviderType.HARDWARE,
                enroll,
                excluded
            ) ?: return null
            if (!isLegacyModuleRouteAvailable(module)) {
                return null
            }
            return SelectedBiometricRoute(
                type = type,
                provider = BiometricProviderType.HARDWARE,
                usesBiometricPromptHardware = false,
                permissions = BiometricManagerCompat.getUsedPermissions(module),
                api = BiometricApi.LEGACY_API,
                module = module
            )
        }

        private fun fallbackRoute(type: BiometricType, excluded: Set<Int>): SelectedBiometricRoute? {
            if (biometricAuthRequest.provider == BiometricProviderType.HARDWARE) {
                return legacyHardwareRoute(type, excluded)
            }
            val module = LegacyBiometric.getSelectedBiometricModule(
                type,
                biometricAuthRequest.provider,
                enroll,
                excluded
            ) ?: return null
            if (!isLegacyModuleRouteAvailable(module)) {
                return null
            }
            val provider = if (module is SoftwareBiometricModule) {
                BiometricProviderType.SOFTWARE
            } else {
                BiometricProviderType.HARDWARE
            }
            return SelectedBiometricRoute(
                type = type,
                provider = provider,
                usesBiometricPromptHardware = false,
                permissions = BiometricManagerCompat.getUsedPermissions(module),
                api = BiometricApi.LEGACY_API,
                module = module
            )
        }

        private fun isLegacyModuleRouteAvailable(module: BiometricModule): Boolean {
            val state = module.getModuleState()
            return if (enroll) {
                state.hardwarePresent && !state.lockedOut && !state.permanentlyLocked
            } else {
                state.hardwarePresent &&
                        state.enrolled &&
                        !state.lockedOut &&
                        !state.permanentlyLocked
            }
        }

        private fun shouldRouteLegacyBeforeSystemHardware(type: BiometricType): Boolean =
            legacyPriorityCache.getOrPut(type) { readLegacyRoutePriority(type) }

        private fun readLegacyRoutePriority(type: BiometricType, excluded: Set<Int> = getDisabledModuleTags()): Boolean {
            if (biometricAuthRequest.provider == BiometricProviderType.HARDWARE ||
                shouldPreferSystemHardwareFace(type)
            ) {
                return false
            }
            val module = LegacyBiometric.getSelectedBiometricModule(
                type,
                biometricAuthRequest.provider,
                enroll,
                excluded
            ) ?: return false
            return module.priority > BiometricModule.PRIORITY_SYSTEM_HARDWARE
        }

        private fun shouldPreferSystemHardwareFace(type: BiometricType): Boolean {
            if (type != BiometricType.BIOMETRIC_FACE) {
                return false
            }
            val model = deviceInfo?.model ?: return false
            return isSamsungDeviceModel(model) ||
                    BiometricPromptHardware.PixelModelChecker.isPixel8OrNewer(model)
        }

        private fun verifyActivity(activity: FragmentActivity?): Boolean {
            return !(activity?.isDestroyed == true || activity?.isFinishing == true || activity?.supportFragmentManager?.isStateSaved == true)
        }

        fun getActivity(): FragmentActivity? {
            return if (verifyActivity(activity)) activity
            else {
                val act = AndroidContext.activity as? FragmentActivity
                if (verifyActivity(act)) act else null
            }
        }

        fun getContext(): Context {
            return AndroidContext.appContext
        }

        fun getCryptographyPurpose(): BiometricCryptographyPurpose? {
            return biometricCryptographyPurpose
        }

        fun isCryptoFallbackAllowed(): Boolean {
            return cryptoFallbackAllowed
        }

        fun getBiometricAuthRequest(): BiometricAuthRequest {
            return biometricAuthRequest
        }

        fun getMultiWindowSupport(): MultiWindowSupport {
            return getActivity()?.let { MultiWindowSupport.get(it, inBubbleTask) } ?: multiWindowSupport
        }

        /** Presentation hint for child Activities in an app-managed Bubble task. */
        fun setInBubbleTask(value: Boolean): Builder {
            inBubbleTask = value
            return this
        }

        fun setCryptographyPurpose(
            biometricCryptographyPurpose: BiometricCryptographyPurpose
        ): Builder {
            this.biometricCryptographyPurpose = biometricCryptographyPurpose
            return this
        }

        /**
         * Allows compatibility crypto for biometric providers that cannot bind authentication to
         * Android Keystore auth-per-use keys. Results from this path are marked as
         * [CryptoSecurityLevel.APP_FLOW_NOT_BIOMETRIC_BOUND] and are not treated as hardware-backed
         * by [dev.skomlach.biometric.compat.crypto.CryptographyManager].
         */
        fun setCryptoFallbackAllowed(enabled: Boolean): Builder {
            this.cryptoFallbackAllowed = enabled
            return this
        }

        fun setForceDeviceCredentials(enabled: Boolean): Builder {
            if (this.isDeviceCredentialFallbackAllowed) {
                forcedCredentialOverride = enabled
            }
            return this
        }

        fun setDeviceCredentialFallbackAllowed(enabled: Boolean): Builder {
            this.isDeviceCredentialFallbackAllowed = enabled
            forcedCredentialOverride = null
            return this
        }

        fun setEnabledBackgroundBiometricIcons(enabled: Boolean): Builder {
            this.backgroundBiometricIconsEnabled = enabled
            return this
        }

        fun setEnabledNotification(enabled: Boolean): Builder {
            this.notificationEnabled = enabled
            return this
        }

        fun setTitle(dialogTitle: CharSequence?): Builder {
            this.dialogTitle = dialogTitle
            return this
        }

        fun setTitle(@StringRes dialogTitleRes: Int): Builder {
            dialogTitle = (getActivity() ?: getContext()).getString(dialogTitleRes)
            return this
        }

        fun setSubtitle(dialogSubtitle: CharSequence?): Builder {
            this.dialogSubtitle = dialogSubtitle
            return this
        }

        fun setSubtitle(@StringRes dialogSubtitleRes: Int): Builder {
            dialogSubtitle = (getActivity() ?: getContext()).getString(dialogSubtitleRes)
            return this
        }

        fun setDescription(dialogDescription: CharSequence?): Builder {
            this.dialogDescription = dialogDescription
            return this
        }

        fun setDescription(@StringRes dialogDescriptionRes: Int): Builder {
            dialogDescription = (getActivity() ?: getContext()).getString(dialogDescriptionRes)
            return this
        }

        fun setExtras(extras: Bundle?): Builder {
            this.extras = extras?.let { Bundle(it) }
            return this
        }

        fun getExtras(): Bundle? {
            return extras?.let { Bundle(it) }
        }

        fun setBehaviorTypingView(view: TextView?): Builder {
            behaviorTypingView = WeakReference(view)
            return this
        }

        fun getBehaviorTypingView(): TextView? {
            return behaviorTypingView.get()
        }

        fun setBehaviorSignatureContainer(container: ViewGroup?): Builder {
            behaviorSignatureContainer = WeakReference(container)
            return this
        }

        fun getBehaviorSignatureContainer(): ViewGroup? {
            return behaviorSignatureContainer.get()
        }

        fun setBehaviorAuthMode(mode: BehaviorAuthMode): Builder {
            behaviorAuthMode = mode
            return this
        }

        fun getBehaviorAuthMode(): BehaviorAuthMode {
            return behaviorAuthMode
        }

        fun setVoicePhrase(phrase: CharSequence?): Builder {
            voicePhrase = phrase
            val normalizedPhrase = phrase?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            val updatedExtras = Bundle(extras ?: Bundle())
            if (normalizedPhrase == null) {
                updatedExtras.remove(EXTRA_VOICE_PHRASE)
            } else {
                updatedExtras.putString(EXTRA_VOICE_PHRASE, normalizedPhrase)
            }
            extras = updatedExtras
            return this
        }

        fun getVoicePhrase(): CharSequence? {
            return voicePhrase
        }

        fun setVoicePcmSample(sampleRateHz: Int, pcmFloat: FloatArray?): Builder {
            return setVoicePcmSamples(
                sampleRateHz = sampleRateHz,
                pcmSamples = pcmFloat?.let { listOf(it) }.orEmpty()
            )
        }

        fun setVoicePcmSamples(sampleRateHz: Int, vararg pcmSamples: FloatArray): Builder {
            return setVoicePcmSamples(sampleRateHz, pcmSamples.asList())
        }

        fun setVoicePcmSamples(sampleRateHz: Int, pcmSamples: Collection<FloatArray>): Builder {
            val updatedExtras = voiceExtrasWithoutSamples()
            val maxSamples = if (sampleRateHz in 8_000..96_000) sampleRateHz * 12 else 0
            val samples = pcmSamples
                .asSequence()
                .filter { it.isNotEmpty() }
                .take(MAX_VOICE_SAMPLE_COUNT)
                .filter { it.size <= maxSamples }
                .map { it.copyOf() }
                .toList()
            if (samples.isNotEmpty()) {
                updatedExtras.putInt(EXTRA_VOICE_SAMPLE_RATE, sampleRateHz)
                if (samples.size == 1) {
                    updatedExtras.putFloatArray(EXTRA_VOICE_PCM_FLOAT, samples.first())
                } else {
                    updatedExtras.putInt(EXTRA_VOICE_SAMPLE_COUNT, samples.size)
                    samples.forEachIndexed { index, sample ->
                        updatedExtras.putFloatArray("$EXTRA_VOICE_PCM_FLOAT.$index", sample)
                    }
                }
            }
            extras = updatedExtras
            return this
        }

        /**
         * @deprecated Precomputed embeddings are rejected by the Voice provider because
         * they bypass trusted PCM capture. Use [setVoicePcmSample], [setVoicePcmSamples],
         * or the provider's automatic capture flow instead.
         */
        @Deprecated(
            message = "Precomputed voice embeddings are not accepted; provide PCM capture instead",
            level = DeprecationLevel.WARNING
        )
        fun setVoiceEmbedding(embedding: FloatArray?): Builder {
            val updatedExtras = voiceExtrasWithoutSamples()
            embedding
                ?.copyOf()
                ?.takeIf { it.isNotEmpty() }
                ?.let { updatedExtras.putFloatArray(EXTRA_VOICE_EMBEDDING, it) }
            extras = updatedExtras
            return this
        }

        fun clearVoiceSample(): Builder {
            extras = voiceExtrasWithoutSamples()
            return this
        }

        private fun voiceExtrasWithoutSamples(): Bundle {
            val updatedExtras = Bundle(extras ?: Bundle())
            updatedExtras.remove(EXTRA_VOICE_SAMPLE_RATE)
            updatedExtras.remove(EXTRA_VOICE_PCM_FLOAT)
            updatedExtras.remove(EXTRA_VOICE_EMBEDDING)
            updatedExtras.remove(EXTRA_VOICE_SAMPLE_COUNT)
            repeat(MAX_VOICE_SAMPLE_COUNT) { index ->
                updatedExtras.remove("$EXTRA_VOICE_PCM_FLOAT.$index")
            }
            return updatedExtras
        }

        fun getNegativeButtonText(): CharSequence? {
            return negativeButtonText
        }

        @Deprecated("BiometricPromptCompat.setNegativeButtonText may not work properly on some devices!")
        fun setNegativeButtonText(text: CharSequence?): Builder {
            negativeButtonText = text
            return this
        }

        @Deprecated("BiometricPromptCompat.setNegativeButtonText may not work properly on some devices!")
        fun setNegativeButtonText(@StringRes res: Int): Builder {
            negativeButtonText = (getActivity() ?: getContext()).getString(res)
            return this
        }

        fun build(): BiometricPromptCompat {
            return BiometricPromptCompat(this)
        }
    }

}
