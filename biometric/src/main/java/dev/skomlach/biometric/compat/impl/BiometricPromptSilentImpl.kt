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
import dev.skomlach.biometric.compat.BiometricConfirmation
import dev.skomlach.biometric.compat.BiometricPromptCompat
import dev.skomlach.biometric.compat.BiometricType
import dev.skomlach.biometric.compat.BundleBuilder
import dev.skomlach.biometric.compat.engine.BiometricAuthentication
import dev.skomlach.biometric.compat.engine.BiometricAuthenticationListener
import dev.skomlach.biometric.compat.utils.DevicesWithKnownBugs
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.d
import dev.skomlach.biometric.compat.utils.notification.BiometricNotificationManager
import dev.skomlach.common.misc.ExecutorHelper
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class BiometricPromptSilentImpl(override val builder: BiometricPromptCompat.Builder) :
    IBiometricPromptImpl, AuthCallback {

    private val fmAuthCallback: BiometricAuthenticationListener =
        BiometricAuthenticationCallbackImpl()
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
        isFingerprint.set(
            builder.getAllAvailableTypes().contains(BiometricType.BIOMETRIC_FINGERPRINT)
        )
    }

    override fun authenticate(callback: BiometricPromptCompat.AuthenticationCallback?) {
        d("BiometricPromptSilentImpl.authenticate():")
        this.authFinished.clear()
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
            builder.getAllAvailableTypes()
        )
        BiometricAuthentication.authenticate(
            builder.getCryptographyPurpose(),
            null,
            types,
            fmAuthCallback,
            BundleBuilder.create(builder)
        )
    }

    override fun stopAuth() {
        d("BiometricPromptSilentImpl.stopAuth():")
        BiometricAuthentication.cancelAuthentication()
    }

    override fun cancelAuth() {
        try {
            val success =
                authFinished.values.firstOrNull { it.authResultState == AuthResult.AuthResultState.SUCCESS }
            if (success != null)
                return
            callback?.onCanceled(if (canceled.isEmpty()) builder.getAllAvailableTypes().map {
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

    private fun checkAuthResult(
        module: AuthenticationResult?,
        authResult: AuthResult.AuthResultState
    ) {
        if (!isOpened.get())
            return
        val failureReason = module?.reason
        if (authResult == AuthResult.AuthResultState.FATAL_ERROR) {
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
        authFinished[module?.type] =
            AuthResult(authResult, result = module)
        BiometricNotificationManager.dismiss(module?.type)

        val authFinishedList: List<BiometricType?> = ArrayList(authFinished.keys)
        val allList: MutableList<BiometricType?> = ArrayList(
            builder.getAllAvailableTypes()
        )
        allList.removeAll(authFinishedList)
        d("checkAuthResult.authFinished - ${builder.getBiometricAuthRequest()}: $allList; ($authFinished / ${builder.getAllAvailableTypes()})")
        val error =
            authFinished.values.firstOrNull { it.authResultState == AuthResult.AuthResultState.FATAL_ERROR }
        val success =
            authFinished.values.firstOrNull { it.authResultState == AuthResult.AuthResultState.SUCCESS }
        d("checkAuthResult.authFinished - ${builder.getBiometricAuthRequest()}: $error/$success")
        if (((success != null || error != null || allList.isEmpty()) && builder.getBiometricAuthRequest().confirmation == BiometricConfirmation.ANY) ||
            (builder.getBiometricAuthRequest().confirmation == BiometricConfirmation.ALL && allList.isEmpty())
        ) {

            if (success != null) {
                val onlySuccess = authFinished.filter {
                    it.value.authResultState == AuthResult.AuthResultState.SUCCESS
                }
                val fixCryptoObjects = builder.getCryptographyPurpose()?.purpose == null

                callback?.onSucceeded(onlySuccess.keys.toList().mapNotNull {
                    var result: AuthenticationResult? = null
                    onlySuccess[it]?.result?.let { r ->
                        result = AuthenticationResult(
                            r.type,
                            if (fixCryptoObjects) null else r.cryptoObject, r.reason, r.description
                        )
                    }
                    result
                }.toSet())
                cancelAuthentication()
            } else if (error != null && allList.isEmpty()) {
                if (failureCounter.get() == 1 || error.result?.reason !== AuthenticationFailureReason.LOCKED_OUT || DevicesWithKnownBugs.isHideDialogInstantly) {
                    callback?.onFailed(authFinished.values.filter { it.authResultState == AuthResult.AuthResultState.FATAL_ERROR }
                        .mapNotNull {
                            it.result
                        }.toSet())
                    cancelAuthentication()
                } else {
                    ExecutorHelper.postDelayed({
                        callback?.onFailed(authFinished.values.filter { it.authResultState == AuthResult.AuthResultState.FATAL_ERROR }
                            .mapNotNull {
                                it.result
                            }.toSet())
                        cancelAuthentication()
                    }, 2000)
                }
            }


        }
    }

    private inner class BiometricAuthenticationCallbackImpl : BiometricAuthenticationListener {

        override fun onSuccess(module: AuthenticationResult) {
            checkAuthResult(module, AuthResult.AuthResultState.SUCCESS)
        }

        override fun onHelp(msg: CharSequence?) {
        }

        override fun onFailure(result: AuthenticationResult) {
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