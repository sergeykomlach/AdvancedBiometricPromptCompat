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

import dev.skomlach.biometric.compat.BiometricConfirmation
import dev.skomlach.biometric.compat.BiometricPromptCompat
import dev.skomlach.biometric.compat.BiometricType
import dev.skomlach.biometric.compat.AuthenticationFailureReason
import dev.skomlach.biometric.compat.AuthenticationHelpReason
import dev.skomlach.biometric.compat.engine.BiometricAuthentication
import dev.skomlach.biometric.compat.engine.BiometricAuthentication.authenticate
import dev.skomlach.biometric.compat.engine.BiometricAuthenticationListener
import dev.skomlach.biometric.compat.impl.dialogs.BiometricPromptCompatDialogImpl
import dev.skomlach.biometric.compat.utils.DevicesWithKnownBugs
import dev.skomlach.biometric.compat.utils.DevicesWithKnownBugs.isHideDialogInstantly
import dev.skomlach.biometric.compat.utils.HardwareAccessImpl
import dev.skomlach.biometric.compat.utils.Vibro
import dev.skomlach.biometric.compat.utils.activityView.IconStateHelper
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.d
import dev.skomlach.biometric.compat.utils.notification.BiometricNotificationManager
import dev.skomlach.common.misc.ExecutorHelper
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean


class BiometricPromptGenericImpl(override val builder: BiometricPromptCompat.Builder) :
    IBiometricPromptImpl, AuthCallback {
    private var dialog: BiometricPromptCompatDialogImpl? = null
    private val fmAuthCallback: BiometricAuthenticationListener =
        BiometricAuthenticationCallbackImpl()
    private var callback: BiometricPromptCompat.AuthenticationCallback? = null
    private val isFingerprint = AtomicBoolean(false)
    private val authFinished: MutableMap<BiometricType?, AuthResult> =
        HashMap<BiometricType?, AuthResult>()

    init {
        isFingerprint.set(
            builder.getAllAvailableTypes().contains(BiometricType.BIOMETRIC_FINGERPRINT)
        )
    }

    override fun authenticate(callback: BiometricPromptCompat.AuthenticationCallback?) {
        d("BiometricPromptGenericImpl.authenticate():")
        this.callback = callback
        val doNotShowDialog = isFingerprint.get() && isHideDialogInstantly
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
        onUiOpened()
    }

    override fun cancelAuthentication() {
        d("BiometricPromptGenericImpl.cancelAuthentication():")
        if (dialog != null) dialog?.dismissDialog() else {
            stopAuth()
        }
        onUiClosed()
    }


    override fun startAuth() {

        d("BiometricPromptGenericImpl.startAuth():")
        val types: List<BiometricType?> = ArrayList(
            builder.getAllAvailableTypes()
        )
        authenticate(if (dialog != null) dialog?.authPreview else null, types, fmAuthCallback)
    }

    override fun stopAuth() {
        d("BiometricPromptGenericImpl.stopAuth():")
        BiometricAuthentication.cancelAuthentication()
    }

    override fun cancelAuth() {
        callback?.onCanceled()
    }

    override fun onUiOpened() {
        callback?.onUIOpened()
    }

    override fun onUiClosed() {
        callback?.onUIClosed()
    }

    private fun checkAuthResult(
        module: BiometricType?,
        authResult: AuthResult.AuthResultState,
        failureReason: AuthenticationFailureReason? = null
    ) {
        if (authResult == AuthResult.AuthResultState.SUCCESS) {
            IconStateHelper.successType(module)
            if (builder.getBiometricAuthRequest().confirmation == BiometricConfirmation.ALL) {
                Vibro.start()
            }
        } else if (authResult == AuthResult.AuthResultState.FATAL_ERROR) {
            IconStateHelper.errorType(module)
            dialog?.onFailure(failureReason == AuthenticationFailureReason.LOCKED_OUT)
        }
        //non fatal
        when (failureReason) {
            AuthenticationFailureReason.SENSOR_FAILED, AuthenticationFailureReason.AUTHENTICATION_FAILED -> return
        }
        authFinished[module] = AuthResult(authResult, failureReason)
        dialog?.authFinishedCopy = authFinished
        BiometricNotificationManager.dismiss(module)

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
                    callback?.onSucceeded(onlySuccess.keys.toList().filterNotNull().toSet())
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

        override fun onSuccess(module: BiometricType?) {
            checkAuthResult(module, AuthResult.AuthResultState.SUCCESS)
        }

        override fun onHelp(helpReason: AuthenticationHelpReason?, msg: CharSequence?) {
            if (helpReason !== AuthenticationHelpReason.BIOMETRIC_ACQUIRED_GOOD && !msg.isNullOrEmpty()) {
                if (dialog != null) dialog?.onHelp(msg)
            }
        }

        override fun onFailure(
            failureReason: AuthenticationFailureReason?,
            module: BiometricType?
        ) {
            checkAuthResult(module, AuthResult.AuthResultState.FATAL_ERROR, failureReason)
        }
    }
}