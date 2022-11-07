/*
 *  Copyright (c) 2021 Sergey Komlach aka Salat-Cx65; Original project: https://github.com/Salat-Cx65/AdvancedBiometricPromptCompat
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

import dev.skomlach.biometric.compat.*
import dev.skomlach.biometric.compat.engine.BiometricAuthentication
import dev.skomlach.biometric.compat.engine.BiometricAuthenticationListener
import dev.skomlach.biometric.compat.utils.HardwareAccessImpl
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.d
import dev.skomlach.biometric.compat.utils.notification.BiometricNotificationManager
import dev.skomlach.common.misc.ExecutorHelper
import java.util.concurrent.atomic.AtomicBoolean

class BiometricPromptSilentImpl(override val builder: BiometricPromptCompat.Builder) :
    IBiometricPromptImpl, AuthCallback {

    private val fmAuthCallback: BiometricAuthenticationListener =
        BiometricAuthenticationCallbackImpl()
    private var callback: BiometricPromptCompat.AuthenticationCallback? = null
    private val isFingerprint = AtomicBoolean(false)
    private val authFinished: MutableMap<BiometricType?, AuthResult> =
        HashMap<BiometricType?, AuthResult>()

    private val isOpened = AtomicBoolean(false)
    private val autoCancel = Runnable {
        cancelAuthentication()
    }

    init {
        isFingerprint.set(
            builder.getAllAvailableTypes().contains(BiometricType.BIOMETRIC_FINGERPRINT)
        )
    }

    override fun authenticate(callback: BiometricPromptCompat.AuthenticationCallback?) {
        d("BiometricPromptSilentImpl.authenticate():")
        this.callback = callback
        startAuth()
        onUiOpened()
    }

    override fun cancelAuthentication() {
        d("BiometricPromptSilentImpl.cancelAuthentication():")
        stopAuth()
        onUiClosed()
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
        callback?.onCanceled()
    }

    override fun onUiOpened() {
        if (isOpened.get())
            return
        isOpened.set(true)
        callback?.onUIOpened()
        ExecutorHelper.postDelayed(autoCancel, 15_000L)
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
        authResult: AuthResult.AuthResultState,
        failureReason: AuthenticationFailureReason? = null
    ) {
        //non fatal
        if (mutableListOf(
                AuthenticationFailureReason.SENSOR_FAILED,
                AuthenticationFailureReason.AUTHENTICATION_FAILED
            ).contains(failureReason)
        ) {
            return
        }
        authFinished[module?.confirmed] =
            AuthResult(authResult, successData = module, failureReason)
        BiometricNotificationManager.dismiss(module?.confirmed)

        val authFinishedList: List<BiometricType?> = ArrayList(authFinished.keys)
        val allList: MutableList<BiometricType?> = ArrayList(
            builder.getAllAvailableTypes()
        )
        allList.removeAll(authFinishedList)
        d("checkAuthResult.authFinished - ${builder.getBiometricAuthRequest()}: $allList; ($authFinished / ${builder.getAllAvailableTypes()})")
        val error =
            authFinished.values.lastOrNull { it.authResultState == AuthResult.AuthResultState.FATAL_ERROR }
        val success =
            authFinished.values.lastOrNull { it.authResultState == AuthResult.AuthResultState.SUCCESS }
        d("checkAuthResult.authFinished - ${builder.getBiometricAuthRequest()}: $error/$success")
        if (((success != null || allList.isEmpty()) && builder.getBiometricAuthRequest().confirmation == BiometricConfirmation.ANY) ||
            (builder.getBiometricAuthRequest().confirmation == BiometricConfirmation.ALL && allList.isEmpty())
        ) {
            ExecutorHelper.post {
                cancelAuthentication()
                if (success != null) {
                    val onlySuccess = authFinished.filter {
                        it.value.authResultState == AuthResult.AuthResultState.SUCCESS
                    }
                    val fixCryptoObjects = builder.getCryptographyPurpose()?.purpose == null

                    callback?.onSucceeded(onlySuccess.keys.toList().mapNotNull {
                        var result: AuthenticationResult? = null
                        onlySuccess[it]?.successData?.let { r ->
                            result = AuthenticationResult(
                                r.confirmed,
                                if (fixCryptoObjects) null else r.cryptoObject
                            )
                        }
                        result
                    }.toSet())
                } else if (error != null) {
                    if (error.failureReason !== AuthenticationFailureReason.LOCKED_OUT) {
                        callback?.onFailed(error.failureReason)
                    } else {
                        HardwareAccessImpl.getInstance(builder.getBiometricAuthRequest()).lockout()
                        ExecutorHelper.postDelayed({
                            callback?.onFailed(error.failureReason)
                        }, 2000)
                    }
                }
            }
        }
    }

    private inner class BiometricAuthenticationCallbackImpl : BiometricAuthenticationListener {

        override fun onSuccess(module: AuthenticationResult?) {
            checkAuthResult(module, AuthResult.AuthResultState.SUCCESS)
        }

        override fun onHelp(msg: CharSequence?) {
        }

        override fun onFailure(
            failureReason: AuthenticationFailureReason?,
            module: BiometricType?
        ) {
            checkAuthResult(
                AuthenticationResult(module),
                AuthResult.AuthResultState.FATAL_ERROR,
                failureReason
            )
        }

        override fun onCanceled(module: BiometricType?) {
            cancelAuth()
            cancelAuthentication()
        }
    }
}