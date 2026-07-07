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

    private val fmAuthCallback: LegacyBiometricAuthenticationListener =
        LegacyBiometricAuthenticationCallbackImpl()
    private var callback: BiometricPromptCompat.AuthenticationCallback? = null
    private val isFingerprint = AtomicBoolean(false)
    private val authFinished: MutableMap<BiometricType?, AuthResult> =
        HashMap<BiometricType?, AuthResult>()
    private val canceled = HashSet<AuthenticationResult>()
    private val failureCounter = AtomicInteger(0)
    private val isOpened = AtomicBoolean(false)
    private val autoCancel = Runnable {
        canceled.addAll(builder.getAllAvailableTypes().map {
            AuthenticationResult(
                it,
                reason = AuthenticationFailureReason.CANCELED
            )
        })
        cancelAuth()

    }

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
        d("BiometricPromptSilentImpl.authenticate():")
        this.authFinished.clear()
        seedPreSatisfiedEnrollResults()
        this.callback = callback
        onUiOpened()
        startAuth()
    }

    override fun cancelAuthentication() {
        d("BiometricPromptSilentImpl.cancelAuthentication():")
        onUiClosed()
        stopAuth()
    }

    override fun startAuth() {

        d("BiometricPromptSilentImpl.startAuth():")
        val types: List<BiometricType?> = ArrayList(
            executionTypes()
        )
        ExecutorHelper.postDelayed({
            LegacyBiometric.authenticate(
                builder.getCryptographyPurpose(),
                null,
                types,
                fmAuthCallback,
                BundleBuilder.create(builder),
                builder.getBiometricAuthRequest().provider,
                builder.getDisabledModuleTags(),
                builder.isCryptoFallbackAllowed()
            )
        }, 500)
    }

    override fun stopAuth() {
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
                    canceledResults = canceled,
                    rollbackEligibleTypes = builder.getRollbackEligibleEnrollTypes(),
                    terminal = true
                )
                when (outcome.status) {
                    EnrollTerminalStatus.SUCCEEDED -> callback?.onSucceeded(
                        buildSuccessCallbackResults(outcome.results)
                    )

                    EnrollTerminalStatus.FAILED -> callback?.onFailed(outcome.results)
                    EnrollTerminalStatus.CONTINUE -> callback?.onFailed(canceled)
                }
                return
            }

            val success = authFinished.values.firstOrNull {
                it.authResultState == AuthResult.AuthResultState.SUCCESS
            }
            if (success != null) {
                return
            }
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
        ExecutorHelper.postDelayed(
            autoCancel,
            TimeUnit.SECONDS.toMillis(builder.getAuthWindow().toLong())
        )
    }

    override fun onUiClosed() {
        if (!isOpened.get())
            return
        ExecutorHelper.removeCallbacks(autoCancel)
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
                canceledResults = canceled,
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
            }
            return
        }
        if (((success != null || error != null || allList.isEmpty()) && builder.getBiometricAuthRequest().confirmation == BiometricConfirmation.ANY) ||
            (builder.getBiometricAuthRequest().confirmation == BiometricConfirmation.ALL && allList.isEmpty())
        ) {

            if (success != null) {
                callback?.onSucceeded(buildSuccessCallbackResults(successfulResults()))
                cancelAuthentication()
            } else if (error != null && allList.isEmpty()) {
                if (failureCounter.get() == 1 || error.result?.reason !== AuthenticationFailureReason.LOCKED_OUT || DevicesWithKnownBugs.isHideDialogInstantly) {
                    callback?.onFailed(fatalErrorResults())
                    cancelAuthentication()
                } else {
                    ExecutorHelper.postDelayed({
                        callback?.onFailed(fatalErrorResults())
                        cancelAuthentication()
                    }, 2000)
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

    private inner class LegacyBiometricAuthenticationCallbackImpl :
        LegacyBiometricAuthenticationListener {

        override fun onSuccess(module: AuthenticationResult) {
            checkAuthResult(module, AuthResult.AuthResultState.SUCCESS)
        }

        override fun onHelp(msg: CharSequence?) {
        }

        override fun onFailure(result: AuthenticationResult) {
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
            canceled.add(result)
            cancelAuth()
        }
    }
}
