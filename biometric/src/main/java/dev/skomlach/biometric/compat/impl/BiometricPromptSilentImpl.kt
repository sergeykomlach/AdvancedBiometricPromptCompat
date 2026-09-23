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
import dev.skomlach.biometric.compat.AuthenticationResult
import dev.skomlach.biometric.compat.BiometricAuthRequest
import dev.skomlach.biometric.compat.BiometricConfirmation
import dev.skomlach.biometric.compat.BiometricManagerCompat
import dev.skomlach.biometric.compat.BiometricPromptCompat
import dev.skomlach.biometric.compat.BiometricProviderType
import dev.skomlach.biometric.compat.BiometricType
import dev.skomlach.biometric.compat.EnrollTerminalStatus
import dev.skomlach.biometric.compat.BundleBuilder
import dev.skomlach.biometric.compat.CryptoSecurityLevel
import dev.skomlach.biometric.compat.biometricRequiredCryptoMissingDescription
import dev.skomlach.biometric.compat.resolveEnrollSessionOutcome
import dev.skomlach.biometric.compat.engine.LegacyBiometric
import dev.skomlach.biometric.compat.engine.LegacyBiometricAuthenticationListener
import dev.skomlach.biometric.compat.utils.DevicesWithKnownBugs
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.d
import dev.skomlach.biometric.compat.utils.notification.BiometricNotificationManager
import dev.skomlach.common.misc.ExecutorHelper
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class BiometricPromptSilentImpl(override val builder: BiometricPromptCompat.Builder) :
    IBiometricPromptImpl, AuthCallback {

    private val pendingAuthStart = PendingAuthStart(ExecutorHelper::postDelayed, ExecutorHelper::removeCallbacks)
    private val pendingAuthFailure = PendingAuthStart(ExecutorHelper::postDelayed, ExecutorHelper::removeCallbacks)
    private val pendingAutoCancel = PendingAuthStart(ExecutorHelper::postDelayed, ExecutorHelper::removeCallbacks)
    private val authSessionState = AuthSessionState<AuthenticationResult>()
    @Volatile
    private var authSessionToken = -1L
    private var fmAuthCallback: LegacyBiometricAuthenticationListener? = null
    private var callback: BiometricPromptCompat.AuthenticationCallback? = null
    private val isFingerprint = AtomicBoolean(false)
    private val authFinished: MutableMap<BiometricType?, AuthResult> =
        HashMap<BiometricType?, AuthResult>()
    private val failureCounter = AtomicInteger(0)
    private val isOpened = AtomicBoolean(false)

    init {
        val allTypes = builder.getAllAvailableTypes()
        if (builder.enroll) {
            val softwareTypes = allTypes.filterNot {
                BiometricManagerCompat.getAuthSnapshot(
                    BiometricAuthRequest.default().withType(it).withProvider(
                        BiometricProviderType.HARDWARE
                    )
                ).state.hardwareDetected
            }
            isFingerprint.set(softwareTypes.contains(BiometricType.BIOMETRIC_FINGERPRINT))
        } else
            isFingerprint.set(
                allTypes.contains(BiometricType.BIOMETRIC_FINGERPRINT)
            )
    }

    override fun authenticate(callback: BiometricPromptCompat.AuthenticationCallback?) {
        pendingAuthStart.cancel()
        pendingAuthFailure.cancel()
        pendingAutoCancel.cancel()
        authSessionToken = authSessionState.begin()
        fmAuthCallback = LegacyBiometricAuthenticationCallbackImpl(authSessionToken)
        failureCounter.set(0)
        d("BiometricPromptSilentImpl.authenticate():")
        this.authFinished.clear()
        seedPreSatisfiedEnrollResults()
        this.callback = callback
        onUiOpened()
        startAuth()
    }

    override fun cancelAuthentication() {
        pendingAuthStart.cancel()
        pendingAuthFailure.cancel()
        pendingAutoCancel.cancel()
        authSessionState.invalidate()
        authSessionToken = -1L
        fmAuthCallback = null
        d("BiometricPromptSilentImpl.cancelAuthentication():")
        onUiClosed()
        stopAuth()
    }

    override fun startAuth() {
        pendingAuthFailure.cancel()
        d("BiometricPromptSilentImpl.startAuth():")
        val sessionToken = authSessionToken
        val legacyCallback = fmAuthCallback ?: return
        val types: List<BiometricType?> = ArrayList(
            executionTypes()
        )
        pendingAuthStart.schedule(500) {
            if (!authSessionState.owns(sessionToken)) return@schedule
            types.filterNotNull().forEach(builder::trackSoftwareEnrollment)
            LegacyBiometric.authenticate(
                builder.getCryptographyPurpose(),
                null,
                types,
                legacyCallback,
                BundleBuilder.create(builder),
                builder.getBiometricAuthRequest().provider,
                builder.getDisabledModuleTags(),
                builder.isCryptoFallbackAllowed()
            )
        }
    }

    override fun stopAuth() {
        pendingAuthStart.cancel()
        d("BiometricPromptSilentImpl.stopAuth():")
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
            callback.dispatchCanceledOrFailed(if (canceled.isEmpty()) builder.getAllAvailableTypes().map {
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
        val sessionToken = authSessionToken
        pendingAutoCancel.schedule(TimeUnit.SECONDS.toMillis(builder.getAuthWindow().toLong())) {
            if (!authSessionState.owns(sessionToken)) return@schedule
            builder.getAllAvailableTypes().forEach {
                authSessionState.add(
                    sessionToken,
                    AuthenticationResult(it, reason = AuthenticationFailureReason.CANCELED)
                )
            }
            cancelAuth()
        }
    }

    override fun onUiClosed() {
        pendingAuthStart.cancel()
        pendingAuthFailure.cancel()
        pendingAutoCancel.cancel()
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
        if (builder.enroll &&
            normalizedAuthResult == AuthResult.AuthResultState.SUCCESS &&
            normalizedModule != null
        ) {
            builder.markEnrollConfirmedResults(setOf(normalizedModule))
        }
        if (normalizedAuthResult == AuthResult.AuthResultState.FATAL_ERROR) {
            failureCounter.incrementAndGet()
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
                EnrollTerminalStatus.CONTINUE -> return
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
        if (completion != AuthenticationCompletion.PENDING) {
            if (completion == AuthenticationCompletion.SUCCEEDED) {
                callback?.onSucceeded(buildSuccessCallbackResults(successfulResults()))
                cancelAuthentication()
            } else if (error != null) {
                if (failureCounter.get() == 1 || error.result?.reason !== AuthenticationFailureReason.LOCKED_OUT || DevicesWithKnownBugs.isHideDialogInstantly) {
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
            builder.getAllAvailableTypes()
        }
    }

    private fun completionTypes(): Set<BiometricType> {
        return if (builder.enroll) {
            builder.getCurrentEnrollCompletionTypes()
        } else {
            builder.getAllAvailableTypes()
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
        private val expectedSessionToken: Long
    ) :
        LegacyBiometricAuthenticationListener {

        override fun onSuccess(result: AuthenticationResult) {
            if (!authSessionState.owns(expectedSessionToken)) return
            checkAuthResult(result, AuthResult.AuthResultState.SUCCESS)
        }

        override fun onHelp(msg: CharSequence?) {
            if (!authSessionState.owns(expectedSessionToken)) return
        }

        override fun onFailure(result: AuthenticationResult) {
            if (!authSessionState.owns(expectedSessionToken)) return
            if (builder.disableBiometricForPermissionFailure(result)) {
                BiometricNotificationManager.dismiss(result.type)
                if (executionTypes().isEmpty()) {
                    checkAuthResult(result, AuthResult.AuthResultState.FATAL_ERROR)
                } else {
                    stopAuth()
                    startAuth()
                }
                return
            }
            checkAuthResult(
                result,
                AuthResult.AuthResultState.FATAL_ERROR,
            )
        }

        override fun onCanceled(result: AuthenticationResult) {
            if (!authSessionState.add(expectedSessionToken, result)) return
            cancelAuth()
        }
    }
}
