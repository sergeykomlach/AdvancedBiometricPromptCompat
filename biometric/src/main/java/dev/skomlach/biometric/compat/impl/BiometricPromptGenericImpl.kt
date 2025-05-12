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
import dev.skomlach.biometric.compat.impl.dialogs.BiometricPromptCompatDialogImpl
import dev.skomlach.biometric.compat.utils.DevicesWithKnownBugs
import dev.skomlach.biometric.compat.utils.DevicesWithKnownBugs.isHideDialogInstantly
import dev.skomlach.biometric.compat.utils.Vibro
import dev.skomlach.biometric.compat.utils.activityView.IconStateHelper
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.d
import dev.skomlach.biometric.compat.utils.notification.BiometricNotificationManager
import dev.skomlach.common.misc.ExecutorHelper
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class BiometricPromptGenericImpl(override val builder: BiometricPromptCompat.Builder) :
    IBiometricPromptImpl, AuthCallback {
    private var dialog: BiometricPromptCompatDialogImpl? = null
    private val fmAuthCallback: BiometricAuthenticationListener =
        BiometricAuthenticationCallbackImpl()
    private var callback: BiometricPromptCompat.AuthenticationCallback? = null
    private val isFingerprint = AtomicBoolean(false)
    private val authFinished: MutableMap<BiometricType?, AuthResult> =
        HashMap<BiometricType?, AuthResult>()
    private val isOpened = AtomicBoolean(false)
    private val failureCounter = AtomicInteger(0)
    private val canceled = HashSet<AuthenticationResult>()

    init {
        isFingerprint.set(
            builder.getAllAvailableTypes().contains(BiometricType.BIOMETRIC_FINGERPRINT)
        )
    }

    override fun authenticate(callback: BiometricPromptCompat.AuthenticationCallback?) {
        this.authFinished.clear()
        this.callback = callback
        val doNotShowDialog = isFingerprint.get() && isHideDialogInstantly
        d("BiometricPromptGenericImpl.authenticate(): doNotShowDialog=$doNotShowDialog")
        onUiOpened()
        if (!doNotShowDialog) {
            dialog = BiometricPromptCompatDialogImpl(
                builder,
                this@BiometricPromptGenericImpl,
                isFingerprint.get() && DevicesWithKnownBugs.hasUnderDisplayFingerprint
            )
            dialog?.showDialog()
        } else {
            startAuth()
        }
    }

    override fun cancelAuthentication() {
        d("BiometricPromptGenericImpl.cancelAuthentication():")
        onUiClosed()
        if (dialog != null) dialog?.dismissDialog() else {
            stopAuth()
        }
    }

    override fun startAuth() {
        d("BiometricPromptGenericImpl.startAuth():")
        val types: List<BiometricType?> = ArrayList(
            builder.getAllAvailableTypes()
        )
        BiometricAuthentication.authenticate(
            builder.getCryptographyPurpose(),
            dialog?.authPreview,
            types,
            fmAuthCallback,
            BundleBuilder.create(builder)
        )
    }

    override fun stopAuth() {
        d("BiometricPromptGenericImpl.stopAuth():")
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
    }

    override fun onUiClosed() {
        if (!isOpened.get())
            return
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
        if (authResult == AuthResult.AuthResultState.SUCCESS) {
            if (builder.getBiometricAuthRequest().confirmation == BiometricConfirmation.ALL) {
                Vibro.start()
            }
            IconStateHelper.successType(module?.type)
        } else if (authResult == AuthResult.AuthResultState.FATAL_ERROR) {
            failureCounter.incrementAndGet()
            dialog?.onFailure(failureReason == AuthenticationFailureReason.LOCKED_OUT)
            IconStateHelper.errorType(module?.type)
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
        dialog?.authFinishedCopy = authFinished
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
                if (failureCounter.get() == 1 || error.result?.reason !== AuthenticationFailureReason.LOCKED_OUT || isHideDialogInstantly) {
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
            if (!msg.isNullOrEmpty()) {
                if (dialog != null) dialog?.onHelp(msg)
            }
        }

        override fun onFailure(
            result: AuthenticationResult
        ) {
            checkAuthResult(
                result,
                AuthResult.AuthResultState.FATAL_ERROR
            )
        }

        override fun onCanceled(result: AuthenticationResult) {
            canceled.add(result)
            cancelAuth()
        }
    }
}