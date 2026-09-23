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

package dev.skomlach.biometric.compat.impl

import dev.skomlach.biometric.compat.AuthenticationFailureReason
import dev.skomlach.biometric.compat.AuthenticationUiOwner
import dev.skomlach.biometric.compat.AuthenticationResult
import dev.skomlach.biometric.compat.BiometricConfirmation
import dev.skomlach.biometric.compat.BiometricPromptCompat
import dev.skomlach.biometric.compat.BiometricType
import dev.skomlach.biometric.compat.EnrollTerminalStatus
import dev.skomlach.biometric.compat.BundleBuilder
import dev.skomlach.biometric.compat.CryptoSecurityLevel
import dev.skomlach.biometric.compat.custom.SoftwarePromptStatus
import dev.skomlach.biometric.compat.utils.FingerprintSensorPlacement
import dev.skomlach.biometric.compat.biometricRequiredCryptoMissingDescription
import dev.skomlach.biometric.compat.resolveEnrollSessionOutcome
import dev.skomlach.biometric.compat.engine.LegacyBiometric
import dev.skomlach.biometric.compat.engine.LegacyBiometricAuthenticationListener
import dev.skomlach.biometric.compat.engine.StatusLegacyBiometricAuthenticationListener
import dev.skomlach.biometric.compat.engine.internal.SoftwareBiometricModule
import dev.skomlach.biometric.compat.engine.internal.fingerprint.API23FingerprintModule
import dev.skomlach.biometric.compat.impl.dialogs.BiometricPromptCompatDialogImpl
import dev.skomlach.biometric.compat.utils.DevicesWithKnownBugs
import dev.skomlach.biometric.compat.utils.Vibro
import dev.skomlach.biometric.compat.utils.activityView.IconStateHelper
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.d
import dev.skomlach.biometric.compat.utils.notification.BiometricNotificationManager
import dev.skomlach.common.misc.ExecutorHelper
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

internal fun legacyAuthStartDelayMillis(hideCompatDialog: Boolean): Long {
    return if (hideCompatDialog) 0L else 500L
}

class BiometricPromptGenericImpl(override val builder: BiometricPromptCompat.Builder) :
    IBiometricPromptImpl, AuthCallback {
    private var dialog: BiometricPromptCompatDialogImpl? = null
    private val pendingAuthStart = PendingAuthStart(ExecutorHelper::postDelayed, ExecutorHelper::removeCallbacks)
    private val pendingAuthFailure = PendingAuthStart(ExecutorHelper::postDelayed, ExecutorHelper::removeCallbacks)
    private val authSessionState = AuthSessionState<AuthenticationResult>()
    @Volatile
    private var authSessionToken = -1L
    private var foregroundFeedback: dev.skomlach.biometric.compat.utils.activityView.ForegroundFeedbackSession? = null
    private var fmAuthCallback: LegacyBiometricAuthenticationListener? = null
    private var callback: BiometricPromptCompat.AuthenticationCallback? = null
    private val useUnderDisplayFingerprintLayout = AtomicBoolean(false)
    private val authFinished: MutableMap<BiometricType?, AuthResult> =
        HashMap<BiometricType?, AuthResult>()
    private val isOpened = AtomicBoolean(false)
    private val failureCounter = AtomicInteger(0)
    private var uiDecision = AuthenticationUiDecision(AuthenticationUiOwner.UNKNOWN, "not-prepared")
    private var systemUiModuleTag: Int? = null
    private var stageTypes: Set<BiometricType> = emptySet()
    private var remainingStageTypes: Set<BiometricType> = emptySet()
    @Volatile
    private var stageGeneration = 0L
    internal val systemPromptOwnsUi: Boolean
        get() = uiDecision.owner == AuthenticationUiOwner.SYSTEM

    init {
        prepareUiSession()
    }

    /** Snapshot once before the host installs UI observers, never from layout/focus callbacks. */
    internal fun prepareUiSession() {
        val requested = executionTypes()
        prepareStage(requested)
        if (requested.size > 1 && BiometricType.BIOMETRIC_FINGERPRINT in requested) {
            prepareStage(setOf(BiometricType.BIOMETRIC_FINGERPRINT))
            if (systemPromptOwnsUi) {
                remainingStageTypes = requested - BiometricType.BIOMETRIC_FINGERPRINT
            } else {
                prepareStage(requested)
                remainingStageTypes = emptySet()
            }
        } else remainingStageTypes = emptySet()
    }

    private fun prepareStage(types: Set<BiometricType>) {
        stageTypes = types
        val selected = LegacyBiometric.getSelectedBiometricModule(
            stageTypes,
            builder.getBiometricAuthRequest().provider,
            builder.enroll,
            builder.getDisabledModuleTags()
        )
        val hardwareFingerprint = selected?.first == BiometricType.BIOMETRIC_FINGERPRINT &&
                selected.second !is SoftwareBiometricModule
        val sensor = if (hardwareFingerprint) DevicesWithKnownBugs.fingerprintSensor else null
        val placement = when (sensor?.placement) {
            FingerprintSensorPlacement.UNDER_DISPLAY -> FingerprintPlacement.UNDER_DISPLAY
            FingerprintSensorPlacement.SIDE -> FingerprintPlacement.SIDE
            else -> FingerprintPlacement.UNKNOWN
        }
        useUnderDisplayFingerprintLayout.set(placement == FingerprintPlacement.UNDER_DISPLAY)
        uiDecision = resolveAuthenticationUiOwner(
            singleFingerprint = stageTypes == setOf(BiometricType.BIOMETRIC_FINGERPRINT),
            software = selected?.second is SoftwareBiometricModule,
            frameworkFingerprint = selected?.second is API23FingerprintModule,
            placement = placement,
            missingSystemUi = hardwareFingerprint && DevicesWithKnownBugs.isMissedBiometricUI,
            frameworkOverride = builder.frameworkFingerprintUiOwner,
            verifiedFrameworkOwner = if (selected?.second is API23FingerprintModule)
                dev.skomlach.biometric.compat.FrameworkFingerprintUiProfile.getVerifiedOwner()
            else AuthenticationUiOwner.UNKNOWN
        )
        systemUiModuleTag = selected?.second?.tag().takeIf { systemPromptOwnsUi }
        d("Biometric UI: backend=${selected?.second?.tag()}, placement=$placement, sensorPlacement=${sensor?.placement}, sensorEvidence=${sensor?.source}, owner=${uiDecision.owner}, evidence=${uiDecision.evidence}")
    }

    override fun authenticate(callback: BiometricPromptCompat.AuthenticationCallback?) {
        foregroundFeedback = builder.foregroundFeedback
        pendingAuthStart.cancel()
        pendingAuthFailure.cancel()
        authSessionToken = authSessionState.begin()
        stageGeneration++
        fmAuthCallback = LegacyBiometricAuthenticationCallbackImpl(authSessionToken, stageGeneration)
        failureCounter.set(0)
        this.authFinished.clear()
        seedPreSatisfiedEnrollResults()
        this.callback = callback
        showStageUi()
    }

    private fun showStageUi() {
        builder.systemPromptOwnsUi = systemPromptOwnsUi
        val doNotShowDialog = systemPromptOwnsUi
        d("BiometricPromptGenericImpl.authenticate(): doNotShowDialog=$doNotShowDialog")
        onUiOpened()
        if (!doNotShowDialog) {
            dialog = BiometricPromptCompatDialogImpl(
                builder,
                this@BiometricPromptGenericImpl,

                useUnderDisplayFingerprintLayout.get()
            )
            dialog?.authFinishedCopy = authFinished
            dialog?.showDialog()
        } else {
            startAuth()
        }
    }

    override fun cancelAuthentication() {
        pendingAuthStart.cancel()
        pendingAuthFailure.cancel()
        stageGeneration++
        remainingStageTypes = emptySet()
        authSessionState.invalidate()
        authSessionToken = -1L
        fmAuthCallback = null
        d("BiometricPromptGenericImpl.cancelAuthentication():")
        try {
            onUiClosed()
        } finally {
            // Engine callbacks can outlive cancellation. Do not retain the completed UI session.
            callback = null
            try {
                stopAuth()
            } finally {
                val closingDialog = dialog
                dialog = null
                closingDialog?.dismissDialog()
            }
        }
    }

    override fun startAuth() {
        pendingAuthFailure.cancel()
        d("BiometricPromptGenericImpl.startAuth():")
        val sessionToken = authSessionToken
        val legacyCallback = fmAuthCallback ?: return
        val types: List<BiometricType?> = ArrayList(
            stageTypes.intersect(executionTypes())
        )
        // A system-owned session must not silently switch to a backend requiring our preview/UI.
        val excludedTags = builder.getDisabledModuleTags() + if (systemPromptOwnsUi) {
            stageTypes.flatMap { type ->
                LegacyBiometric.getAvailableBiometricModules(type, builder.getBiometricAuthRequest().provider)
            }.map { it.tag() }.filter { it != systemUiModuleTag }
        } else emptyList()
        pendingAuthStart.schedule(legacyAuthStartDelayMillis(systemPromptOwnsUi)) {
            if (!authSessionState.owns(sessionToken)) return@schedule
            foregroundFeedback?.setSystemPromptActive(systemPromptOwnsUi)
            types.filterNotNull().forEach(builder::trackSoftwareEnrollment)
            LegacyBiometric.authenticate(
                builder.getCryptographyPurpose(),
                dialog?.authPreview,
                types,
                legacyCallback,
                BundleBuilder.create(builder),
                builder.getBiometricAuthRequest().provider,
                excludedTags,
                builder.isCryptoFallbackAllowed()
            )
        }

    }

    override fun stopAuth() {
        foregroundFeedback?.clear()
        pendingAuthStart.cancel()
        d("BiometricPromptGenericImpl.stopAuth():")
        LegacyBiometric.cancelAuthentication()
    }

    override fun cancelAuth() {
        try {
            if (builder.enroll) {
                val outcome = resolveEnrollSessionOutcome(
                    confirmation = builder.getBiometricAuthRequest().confirmation,
                    scopeTypes = completionTypes(),
                    successResults = successfulResults(),
                    confirmedTypes = builder.getConfirmedEnrollTypes(),
                    failureResults = fatalErrorResults(),
                    canceledResults = canceledResults().ifEmpty {
                        dev.skomlach.biometric.compat.emptyEffectiveBiometricCancellationResults(completionTypes())
                    },
                    rollbackEligibleTypes = builder.getRollbackEligibleEnrollTypes(),
                    terminal = true
                )
                when (outcome.status) {
                    EnrollTerminalStatus.SUCCEEDED -> callback?.onSucceeded(
                        buildSuccessCallbackResults(outcome.results)
                    )

                    EnrollTerminalStatus.FAILED -> callback?.onFailed(outcome.results)
                    EnrollTerminalStatus.CANCELED -> callback?.onCanceled(outcome.results)
                    EnrollTerminalStatus.CONTINUE -> callback?.onFailed(canceledResults())
                }
                return
            }

            val success = authFinished.values.firstOrNull {
                it.authResultState == AuthResult.AuthResultState.SUCCESS
            }
            if (success != null && builder.getBiometricAuthRequest().confirmation == BiometricConfirmation.ANY) {
                return
            }

            val canceled = canceledResults()
            callback.dispatchCanceledOrFailed(if (canceled.isEmpty()) builder.getEffectiveAvailableTypes().map {
                AuthenticationResult(
                    it,
                    reason = AuthenticationFailureReason.CANCELED_BY_USER
                )
            }.toSet() else canceled)
        } finally {
            cancelAuthentication()
        }
    }

    override fun onUiOpened() {
        if (isOpened.get())
            return
        isOpened.set(true)
        callback?.onUIOpened()
    }

    override fun onUiClosed() {
        pendingAuthStart.cancel()
        pendingAuthFailure.cancel()
        if (!isOpened.get())
            return
        callback?.onUIClosed()
        isOpened.set(false)
    }

    override fun onPreAuthFailure(result: AuthenticationResult) {
        callback?.onFailed(setOf(result))
        cancelAuthentication()
    }

    private fun checkAuthResult(
        module: AuthenticationResult?,
        authResult: AuthResult.AuthResultState
    ) {
        if (!isOpened.get())
            return
        val normalizedModule = normalizeCryptoResult(module, authResult)
        val normalizedAuthResult = if (normalizedModule?.reason == AuthenticationFailureReason.CRYPTO_ERROR) {
            AuthResult.AuthResultState.FATAL_ERROR
        } else {
            authResult
        }
        val failureReason = normalizedModule?.reason
        if (normalizedAuthResult == AuthResult.AuthResultState.SUCCESS) {
            foregroundFeedback?.finishSource(normalizedModule?.type)
            if (builder.enroll && normalizedModule != null) {
                builder.markEnrollConfirmedResults(setOf(normalizedModule))
            }
            if (builder.getBiometricAuthRequest().confirmation == BiometricConfirmation.ALL) {
                Vibro.start()
            }
            IconStateHelper.successType(normalizedModule?.type)
        } else if (normalizedAuthResult == AuthResult.AuthResultState.FATAL_ERROR) {
            val terminal = failureReason != AuthenticationFailureReason.SENSOR_FAILED &&
                failureReason != AuthenticationFailureReason.AUTHENTICATION_FAILED
            val status = normalizedModule?.description?.takeIf { it.isNotBlank() }
                ?.let { SoftwarePromptStatus(it, terminal = terminal) }
            if (terminal) {
                foregroundFeedback?.finishSource(normalizedModule?.type, status)
            } else if (status != null) {
                foregroundFeedback?.show(normalizedModule.type, status)
            }
            failureCounter.incrementAndGet()
            dialog?.onFailure(failureReason == AuthenticationFailureReason.LOCKED_OUT)
            IconStateHelper.errorType(normalizedModule?.type)
            if (failureReason != AuthenticationFailureReason.SENSOR_FAILED &&
                failureReason != AuthenticationFailureReason.AUTHENTICATION_FAILED) {
                IconStateHelper.refreshAvailability()
            }
        }

        //non fatal
        if (mutableListOf(
                AuthenticationFailureReason.SENSOR_FAILED,
                AuthenticationFailureReason.AUTHENTICATION_FAILED
            ).contains(failureReason)
        ) {
            return
        }
        authFinished[normalizedModule?.type] =
            AuthResult(normalizedAuthResult, result = normalizedModule)
        dialog?.authFinishedCopy = authFinished
        BiometricNotificationManager.dismiss(normalizedModule?.type)

        val authFinishedList: List<BiometricType?> = ArrayList(authFinished.keys)
        val allList: MutableList<BiometricType?> = ArrayList(
            completionTypes()
        )
        allList.removeAll(authFinishedList)
        d("checkAuthResult.authFinished - ${builder.getBiometricAuthRequest()}: $allList; ($authFinished / ${completionTypes()})")
        val error =
            authFinished.values.firstOrNull { it.authResultState == AuthResult.AuthResultState.FATAL_ERROR }
        val success =
            authFinished.values.firstOrNull { it.authResultState == AuthResult.AuthResultState.SUCCESS }
        d("checkAuthResult.authFinished - ${builder.getBiometricAuthRequest()}: $error/$success")
        if (builder.enroll) {
            val outcome = resolveEnrollSessionOutcome(
                confirmation = builder.getBiometricAuthRequest().confirmation,
                scopeTypes = completionTypes(),
                successResults = successfulResults(),
                confirmedTypes = builder.getConfirmedEnrollTypes(),
                failureResults = fatalErrorResults(),
                    canceledResults = canceledResults(),
                rollbackEligibleTypes = builder.getRollbackEligibleEnrollTypes(),
                terminal = error != null || allList.isEmpty()
            )
            when (outcome.status) {
                EnrollTerminalStatus.CONTINUE -> {
                    advanceStageIfPending()
                    return
                }
                EnrollTerminalStatus.SUCCEEDED -> {
                    callback?.onSucceeded(buildSuccessCallbackResults(outcome.results))
                    cancelAuthentication()
                }

                EnrollTerminalStatus.FAILED -> {
                    callback?.onFailed(outcome.results)
                    cancelAuthentication()
                }
                EnrollTerminalStatus.CANCELED -> {
                    callback?.onCanceled(outcome.results)
                    cancelAuthentication()
                }
            }
            return
        }
        val completion = resolveAuthenticationCompletion(
            builder.getBiometricAuthRequest().confirmation,
            completionTypes(),
            authFinished
        )
        if (completion == AuthenticationCompletion.PENDING) {
            advanceStageIfPending()
        } else {
            if (completion == AuthenticationCompletion.SUCCEEDED) {
                callback?.onSucceeded(buildSuccessCallbackResults(successfulResults()))
                cancelAuthentication()
            } else if (error != null) {
                if (failureCounter.get() == 1 || error.result?.reason !== AuthenticationFailureReason.LOCKED_OUT || systemPromptOwnsUi) {
                    callback?.onFailed(fatalErrorResults())
                    cancelAuthentication()
                } else {
                    val failedCallback = callback
                    val failedResults = fatalErrorResults()
                    pendingAuthFailure.schedule(2000) {
                        try {
                            failedCallback?.onFailed(failedResults)
                        } finally {
                            cancelAuthentication()
                        }
                    }
                }
            }


        }
    }

    private fun advanceStageIfPending() {
        if (!systemPromptOwnsUi || remainingStageTypes.isEmpty()) return
        // Reject old backend callbacks before cancellation can synchronously report CANCELED.
        stageGeneration++
        fmAuthCallback = null
        stopAuth()
        val next = remainingCompatStage(remainingStageTypes, executionTypes(), authFinished.keys)
        remainingStageTypes = emptySet()
        if (next.isEmpty()) {
            // Eligibility can change between the completion decision and this handoff.
            // Do not leave an open session with no backend capable of completing it.
            onPreAuthFailure(AuthenticationResult(
                completionTypes().firstOrNull { !authFinished.containsKey(it) }
                    ?: BiometricType.BIOMETRIC_ANY,
                reason = AuthenticationFailureReason.NO_BIOMETRICS_REGISTERED
            ))
            return
        }
        val token = authSessionToken
        val generation = stageGeneration
        // Leave the platform callback stack before creating the next UI. This is not a UI probe.
        pendingAuthStart.schedule(0L) {
            if (!authSessionState.owns(token) || stageGeneration != generation) return@schedule
            prepareStage(next)
            fmAuthCallback = LegacyBiometricAuthenticationCallbackImpl(token, generation)
            showStageUi()
        }
    }

    private fun normalizeCryptoResult(
        module: AuthenticationResult?,
        authResult: AuthResult.AuthResultState
    ): AuthenticationResult? {
        if (authResult != AuthResult.AuthResultState.SUCCESS ||
            builder.getCryptographyPurpose() == null ||
            isAcceptedCryptoResult(module)
        ) {
            return module
        }
        return AuthenticationResult(
            module?.type ?: BiometricType.BIOMETRIC_ANY,
            reason = AuthenticationFailureReason.CRYPTO_ERROR,
            description = biometricRequiredCryptoMissingDescription()
        )
    }

    private fun isAcceptedCryptoResult(module: AuthenticationResult?): Boolean {
        return module?.cryptoSecurityLevel == CryptoSecurityLevel.HARDWARE_BACKED ||
                (
                        builder.isCryptoFallbackAllowed() &&
                                module?.cryptoSecurityLevel == CryptoSecurityLevel.APP_FLOW_NOT_BIOMETRIC_BOUND
                        )
    }

    private fun executionTypes(): Set<BiometricType> {
        return if (builder.enroll) {
            builder.getPendingEnrollTypes()
        } else {
            builder.getEffectiveAvailableTypes()
        }
    }

    private fun completionTypes(): Set<BiometricType> {
        return if (builder.enroll) {
            builder.getCurrentEnrollCompletionTypes()
        } else {
            builder.getEffectiveAvailableTypes()
        }
    }

    private fun seedPreSatisfiedEnrollResults() {
        if (!builder.enroll) {
            return
        }
        builder.getPreSatisfiedEnrollResults().forEach { result ->
            authFinished[result.type] = AuthResult(
                AuthResult.AuthResultState.SUCCESS,
                result
            )
        }
    }

    private fun successfulResults(): Set<AuthenticationResult> {
        return authFinished.values
            .filter { it.authResultState == AuthResult.AuthResultState.SUCCESS }
            .mapNotNull { it.result }
            .toSet()
    }

    private fun fatalErrorResults(): Set<AuthenticationResult> {
        return authFinished.values
            .filter { it.authResultState == AuthResult.AuthResultState.FATAL_ERROR }
            .mapNotNull { it.result }
            .toSet()
    }

    private fun buildSuccessCallbackResults(
        results: Collection<AuthenticationResult>
    ): Set<AuthenticationResult> {
        val fixCryptoObjects = builder.getCryptographyPurpose()?.purpose == null
        return results.mapTo(LinkedHashSet()) { result ->
            AuthenticationResult(
                result.type,
                if (fixCryptoObjects) null else result.cryptoObject,
                result.reason,
                result.description,
                if (fixCryptoObjects) CryptoSecurityLevel.NONE else result.cryptoSecurityLevel
            )
        }
    }

    private fun canceledResults(): Set<AuthenticationResult> =
        authSessionState.snapshot(authSessionToken)

    private inner class LegacyBiometricAuthenticationCallbackImpl(
        private val expectedSessionToken: Long,
        private val expectedStageGeneration: Long
    ) :
        StatusLegacyBiometricAuthenticationListener {

        override fun onSuccess(result: AuthenticationResult) {
            if (!authSessionState.owns(expectedSessionToken) || stageGeneration != expectedStageGeneration) return
            checkAuthResult(result, AuthResult.AuthResultState.SUCCESS)
        }

        override fun onHelp(msg: CharSequence?) {
            if (!authSessionState.owns(expectedSessionToken) || stageGeneration != expectedStageGeneration) return
            if (!msg.isNullOrEmpty()) {
                onStatus(null, SoftwarePromptStatus(primaryText = msg))
            }
        }

        override fun onStatus(source: BiometricType?, status: SoftwarePromptStatus) {
            ExecutorHelper.post {
                if (!authSessionState.owns(expectedSessionToken) || stageGeneration != expectedStageGeneration) return@post
                if (builder.getBiometricFeedbackOptions().enabled) foregroundFeedback?.show(source, status)
                else dialog?.onSoftwareStatus(status)
            }
        }

        override fun onFailure(
            result: AuthenticationResult
        ) {
            if (!authSessionState.owns(expectedSessionToken) || stageGeneration != expectedStageGeneration) return
            if (builder.disableBiometricForPermissionFailure(result)) {
                BiometricNotificationManager.dismiss(result.type)
                if (systemPromptOwnsUi || executionTypes().isEmpty()) {
                    checkAuthResult(result, AuthResult.AuthResultState.FATAL_ERROR)
                } else {
                    stopAuth()
                    startAuth()
                }
                return
            }
            checkAuthResult(
                result,
                AuthResult.AuthResultState.FATAL_ERROR
            )
        }

        override fun onCanceled(result: AuthenticationResult) {
            if (stageGeneration != expectedStageGeneration) return
            if (!authSessionState.add(expectedSessionToken, result)) return
            cancelAuth()
        }
    }
}
