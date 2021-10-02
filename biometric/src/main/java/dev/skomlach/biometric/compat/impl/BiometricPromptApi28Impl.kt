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

import android.annotation.TargetApi
import android.os.Build
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import androidx.annotation.ColorInt
import androidx.annotation.RestrictTo
import androidx.biometric.BiometricFragment
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricPrompt.PromptInfo
import androidx.biometric.CancelationHelper
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentManager
import dev.skomlach.biometric.compat.BiometricConfirmation
import dev.skomlach.biometric.compat.BiometricPromptCompat
import dev.skomlach.biometric.compat.BiometricType
import dev.skomlach.biometric.compat.R
import dev.skomlach.biometric.compat.engine.*
import dev.skomlach.biometric.compat.engine.core.RestartPredicatesImpl.defaultPredicate
import dev.skomlach.biometric.compat.impl.dialogs.BiometricPromptCompatDialogImpl
import dev.skomlach.biometric.compat.utils.*
import dev.skomlach.biometric.compat.utils.CodeToString.getErrorCode
import dev.skomlach.biometric.compat.utils.DevicesWithKnownBugs.isOnePlusWithBiometricBug
import dev.skomlach.biometric.compat.utils.HardwareAccessImpl
import dev.skomlach.biometric.compat.utils.activityView.IconStateHelper
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.d
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.e
import dev.skomlach.biometric.compat.utils.notification.BiometricNotificationManager
import dev.skomlach.biometric.compat.utils.themes.DarkLightThemes.isNightMode
import dev.skomlach.common.misc.ExecutorHelper
import dev.skomlach.common.misc.Utils.isAtLeastR
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.collections.ArrayList
import kotlin.collections.HashSet

@TargetApi(Build.VERSION_CODES.P)
@RestrictTo(RestrictTo.Scope.LIBRARY)
class BiometricPromptApi28Impl(override val builder: BiometricPromptCompat.Builder) :
    IBiometricPromptImpl, BiometricCodes, AuthCallback {
    private val biometricPromptInfo: PromptInfo
    private val biometricPrompt: BiometricPrompt
    private val restartPredicate = defaultPredicate()
    private var dialog: BiometricPromptCompatDialogImpl? = null
    private var callback: BiometricPromptCompat.Result? = null
    private val authFinished: MutableMap<BiometricType?, AuthResult> = HashMap<BiometricType?, AuthResult>()
    private var biometricFragment : BiometricFragment? = null
    private val fmAuthCallback: BiometricAuthenticationListener =
        BiometricAuthenticationCallbackImpl()
    val authCallback: BiometricPrompt.AuthenticationCallback =
        object : BiometricPrompt.AuthenticationCallback() {
            //https://forums.oneplus.com/threads/oneplus-7-pro-fingerprint-biometricprompt-does-not-show.1035821/
            private var onePlusWithBiometricBugFailure = false
            override fun onAuthenticationFailed() {
                d("BiometricPromptApi28Impl.onAuthenticationFailed")
                if (isOnePlusWithBiometricBug) {
                    onePlusWithBiometricBugFailure = true
                    cancelAuthenticate()
                } else {
                    //...normal failed processing...//
                    for(module in builder.primaryAvailableTypes) {
                        IconStateHelper.errorType(module)
                    }
                    dialog?.onFailure(false)
                }
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                d("BiometricPromptApi28Impl.onAuthenticationError: " + getErrorCode(errorCode) + " " + errString)
                // Authentication failed on OnePlus device with broken BiometricPrompt implementation
                // Present the same screen with additional buttons to allow retry/fail
                if (onePlusWithBiometricBugFailure) {
                    onePlusWithBiometricBugFailure = false
                    //...present retryable error screen...
                    return
                }
                //...present normal failed screen...

                ExecutorHelper.INSTANCE.handler.post(Runnable {
                    var failureReason = AuthenticationFailureReason.UNKNOWN
                    when (errorCode) {
                        BiometricCodes.BIOMETRIC_ERROR_NO_BIOMETRICS -> failureReason =
                            AuthenticationFailureReason.NO_BIOMETRICS_REGISTERED
                        BiometricCodes.BIOMETRIC_ERROR_HW_NOT_PRESENT -> failureReason =
                            AuthenticationFailureReason.NO_HARDWARE
                        BiometricCodes.BIOMETRIC_ERROR_HW_UNAVAILABLE -> failureReason =
                            AuthenticationFailureReason.HARDWARE_UNAVAILABLE
                        BiometricCodes.BIOMETRIC_ERROR_LOCKOUT_PERMANENT -> {
                            BiometricErrorLockoutPermanentFix.INSTANCE.setBiometricSensorPermanentlyLocked(
                                builder.biometricAuthRequest.type
                            )
                            failureReason = AuthenticationFailureReason.HARDWARE_UNAVAILABLE
                        }
                        BiometricCodes.BIOMETRIC_ERROR_UNABLE_TO_PROCESS -> failureReason =
                            AuthenticationFailureReason.HARDWARE_UNAVAILABLE
                        BiometricCodes.BIOMETRIC_ERROR_NO_SPACE -> failureReason =
                            AuthenticationFailureReason.SENSOR_FAILED
                        BiometricCodes.BIOMETRIC_ERROR_TIMEOUT -> failureReason =
                            AuthenticationFailureReason.TIMEOUT
                        BiometricCodes.BIOMETRIC_ERROR_LOCKOUT -> {
                            HardwareAccessImpl.getInstance(builder.biometricAuthRequest).lockout()
                            failureReason = AuthenticationFailureReason.LOCKED_OUT
                        }
                        BiometricCodes.BIOMETRIC_ERROR_USER_CANCELED, BiometricCodes.BIOMETRIC_ERROR_NEGATIVE_BUTTON, BiometricCodes.BIOMETRIC_ERROR_CANCELED -> {
                            callback?.onCanceled()
                            cancelAuthenticate()
                            return@Runnable
                        }
                    }
                    if (restartPredicate.invoke(failureReason)) {
                        if (callback != null) {
                            for(module in builder.primaryAvailableTypes) {
                                IconStateHelper.errorType(module)
                            }
                           dialog?.onFailure(
                                failureReason == AuthenticationFailureReason.LOCKED_OUT)
                            authenticate(callback)
                        }
                    } else {
                        when (failureReason) {
                            AuthenticationFailureReason.SENSOR_FAILED, AuthenticationFailureReason.AUTHENTICATION_FAILED -> {
                                HardwareAccessImpl.getInstance(builder.biometricAuthRequest).lockout()
                                failureReason = AuthenticationFailureReason.LOCKED_OUT
                            }
                        }

                        for(module in builder.primaryAvailableTypes) {
                            IconStateHelper.errorType(module)
                        }
                        dialog?.onFailure(
                            failureReason == AuthenticationFailureReason.LOCKED_OUT
                        )
                        checkAuthResultForPrimary(AuthResult.AuthResultState.FATAL_ERROR, failureReason)
                        BiometricAuthWasCanceledByError.INSTANCE.setCanceledByError()
                        if (failureReason == AuthenticationFailureReason.LOCKED_OUT) {
                            ExecutorHelper.INSTANCE.handler.postDelayed({
                                callback?.onCanceled()
                                cancelAuthenticate()
                            }, 2000)
                        } else {
                            callback?.onCanceled()
                            cancelAuthenticate()
                        }
                    }
                })
            }

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                d("BiometricPromptApi28Impl.onAuthenticationSucceeded:")
                onePlusWithBiometricBugFailure = false
                checkAuthResultForPrimary(AuthResult.AuthResultState.SUCCESS)
            }
        }
    private val isFingerprint = AtomicBoolean(false)

    init {
        val promptInfoBuilder = PromptInfo.Builder()
        builder.title?.let {
            promptInfoBuilder.setTitle(it)
        }

        builder.subtitle?.let {
            promptInfoBuilder.setSubtitle(it)
        }

        builder.description?.let {
            promptInfoBuilder.setDescription(it)
        }
        builder.negativeButtonText?.let {
            if (isAtLeastR) promptInfoBuilder.setNegativeButtonText(it) else promptInfoBuilder.setNegativeButtonText(
                getFixedString(
                    it, ContextCompat.getColor(
                        builder.context, R.color.material_deep_teal_500
                    )
                )
            )
        }
        if (isAtLeastR) {
            promptInfoBuilder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
        } else {
            promptInfoBuilder.setDeviceCredentialAllowed(false)
        }
        promptInfoBuilder.setConfirmationRequired(true)
        biometricPromptInfo = promptInfoBuilder.build()
        biometricPrompt = BiometricPrompt(
            builder.context,
            ExecutorHelper.INSTANCE.executor, authCallback
        )
        isFingerprint.set(builder.primaryAvailableTypes.contains(BiometricType.BIOMETRIC_FINGERPRINT))
    }
    private fun getFixedString(str: CharSequence?, @ColorInt color: Int): CharSequence {
        val wordtoSpan: Spannable = SpannableString(str)
        wordtoSpan.setSpan(
            ForegroundColorSpan(color),
            0,
            wordtoSpan.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        return wordtoSpan
    }

    override fun authenticate(cbk: BiometricPromptCompat.Result?) {
            d("BiometricPromptApi28Impl.authenticate():")
            callback = cbk
            if (DevicesWithKnownBugs.isMissedBiometricUI) {
                //1) LG G8 do not have BiometricPrompt UI
                //2) One Plus 6T with InScreen fingerprint sensor
                    dialog = BiometricPromptCompatDialogImpl(
                    builder,
                    this@BiometricPromptApi28Impl,
                     isFingerprint.get() && DevicesWithKnownBugs.hasUnderDisplayFingerprint
                )
                dialog?.showDialog()
            } else {
                startAuth()
            }
            onUiOpened()
    }

    override fun cancelAuthenticateBecauseOnPause(): Boolean {
        d("BiometricPromptApi28Impl.cancelAuthenticateBecauseOnPause():")
        return if (dialog != null) {
            dialog?.cancelAuthenticateBecauseOnPause() == true
        } else {
            cancelAuthenticate()
            true
        }
    }

    override val isNightMode: Boolean
        get() = if (dialog != null) dialog?.isNightMode == true else {
            isNightMode(builder.context)
        }
    override val usedPermissions: List<String>
        get() {
            val permission: MutableSet<String> = HashSet()
            permission.add("android.permission.USE_FINGERPRINT")
            if (Build.VERSION.SDK_INT >= 28) {
                permission.add("android.permission.USE_BIOMETRIC")
            }
            return ArrayList(permission)
        }

    override fun cancelAuthenticate() {
        d("BiometricPromptApi28Impl.cancelAuthenticate():")
        if (dialog != null) dialog?.dismissDialog() else {
            stopAuth()
        }
        onUiClosed()
    }

    private fun hasPrimaryFinished(): Boolean {

        val authFinishedList: List<BiometricType?> = ArrayList(authFinished.keys)
        val allList: MutableList<BiometricType?> = ArrayList(
            builder.primaryAvailableTypes
        )
        allList.removeAll(authFinishedList)
        return allList.isEmpty()
    }

    private fun hasSecondaryFinished(): Boolean {

        val authFinishedList: List<BiometricType?> = ArrayList(authFinished.keys)
        val allList: MutableList<BiometricType?> = ArrayList(
            builder.secondaryAvailableTypes
        )
        allList.removeAll(authFinishedList)
        return allList.isEmpty()
    }

    override fun startAuth() {
        d("BiometricPromptApi28Impl.startAuth():")

        if (!hasSecondaryFinished()) {
            val secondary = HashSet<BiometricType>(builder.secondaryAvailableTypes)
            secondary.removeAll(builder.primaryAvailableTypes)
            if (secondary.isNotEmpty()) {
                d("BiometricPromptApi28Impl.startAuth(): - secondaryAvailableTypes - secondary $secondary; primary - ${builder.primaryAvailableTypes}")
                BiometricAuthentication.authenticate(
                    null,
                    ArrayList<BiometricType>(secondary),
                    fmAuthCallback
                )
            }
        }

        if (!hasPrimaryFinished()) {

            biometricPrompt.authenticate(biometricPromptInfo)
            //fallback - sometimes we not able to cancel BiometricPrompt properly
            try {
                val m = BiometricPrompt::class.java.declaredMethods.first {
                    it.parameterTypes.size == 1 && it.parameterTypes[0] == FragmentManager::class.java && it.returnType == BiometricFragment::class.java
                }
                val isAccessible = m.isAccessible
                try {
                    if (!isAccessible)
                        m.isAccessible = true
                    biometricFragment = m.invoke(null, builder.context.supportFragmentManager) as BiometricFragment?
                } finally {
                    if (!isAccessible)
                        m.isAccessible = false
                }
            } catch (e: Throwable) {
                e(e)
            }
        }
    }

    override fun stopAuth() {
        d("BiometricPromptApi28Impl.stopAuth():")

        BiometricAuthentication.cancelAuthentication()
        biometricPrompt.cancelAuthentication()

        CancelationHelper.forceCancel(biometricFragment)
        biometricFragment = null
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
    private fun checkAuthResultForPrimary(authResult: AuthResult.AuthResultState, failureReason: AuthenticationFailureReason? = null){

        var added = false
        for(module in builder.primaryAvailableTypes) {
            authFinished[module] = AuthResult(authResult, failureReason)
            dialog?.authFinishedCopy = authFinished
            if(AuthResult.AuthResultState.SUCCESS == authResult) {
                IconStateHelper.successType(module)
            }
            added = true
            BiometricNotificationManager.INSTANCE.dismiss(module)
        }
        if(added && builder.biometricAuthRequest.confirmation == BiometricConfirmation.ALL && AuthResult.AuthResultState.SUCCESS == authResult) {
            Vibro.start()
        }

        val authFinishedList: List<BiometricType?> = java.util.ArrayList(authFinished.keys)
        val allList: MutableList<BiometricType?> = java.util.ArrayList(
            builder.allAvailableTypes
        )
        allList.removeAll(authFinishedList)
        d("authFinished - $allList; ($authFinished / ${builder.allAvailableTypes})")
        if (builder.biometricAuthRequest.confirmation == BiometricConfirmation.ANY ||
            (builder.biometricAuthRequest.confirmation == BiometricConfirmation.ALL && (DevicesWithKnownBugs.isHideDialogInstantly || allList.isEmpty()))
        ) {
            ExecutorHelper.INSTANCE.handler.post {
                cancelAuthenticate()
                val error = authFinished.values.lastOrNull{ it.authResultState == AuthResult.AuthResultState.FATAL_ERROR }
                val success = authFinished.values.lastOrNull{ it.authResultState == AuthResult.AuthResultState.SUCCESS }
                if(success!=null) {
                    val onlyFailures = authFinished.filter {
                        it.value.authResultState == AuthResult.AuthResultState.FATAL_ERROR
                    }
                    val set = HashSet<BiometricType>(builder.allAvailableTypes)
                    set.removeAll(onlyFailures.keys)
                    callback?.onSucceeded(set.toList().toSet())
                }
                else if(error!=null){
                    callback?.onFailed(error.failureReason)
                }
            }
        }else {
            if(dialog == null) {
                dialog =
                    BiometricPromptCompatDialogImpl(
                        builder, this@BiometricPromptApi28Impl,
                        builder.secondaryAvailableTypes.contains(BiometricType.BIOMETRIC_FINGERPRINT)
                                && DevicesWithKnownBugs.hasUnderDisplayFingerprint
                    )
                dialog?.authFinishedCopy = authFinished
            }
            dialog?.showDialog()
        }
    }
    private fun checkAuthResultForSecondary(module: BiometricType?, authResult: AuthResult.AuthResultState, failureReason: AuthenticationFailureReason? = null){
        authFinished[module] = AuthResult(authResult, failureReason)
        dialog?.authFinishedCopy = authFinished
        if(authResult == AuthResult.AuthResultState.SUCCESS) {
            IconStateHelper.successType(module)
            if (builder.biometricAuthRequest.confirmation == BiometricConfirmation.ALL) {
                Vibro.start()
            }
        }
        BiometricNotificationManager.INSTANCE.dismiss(module)

        val authFinishedList: List<BiometricType?> = java.util.ArrayList(authFinished.keys)
        val allList: MutableList<BiometricType?> = java.util.ArrayList(
            builder.allAvailableTypes
        )
        allList.removeAll(authFinishedList)
        d("authFinished - $allList; ($authFinished / ${builder.allAvailableTypes})")
        if (builder.biometricAuthRequest.confirmation == BiometricConfirmation.ANY ||
            builder.biometricAuthRequest.confirmation == BiometricConfirmation.ALL && allList.isEmpty()
        ) {
            ExecutorHelper.INSTANCE.handler.post {
                cancelAuthenticate()
                val error = authFinished.values.lastOrNull{ it.authResultState == AuthResult.AuthResultState.FATAL_ERROR }
                val success = authFinished.values.lastOrNull{ it.authResultState == AuthResult.AuthResultState.SUCCESS }
                if(success!=null) {
                    val onlySuccess = authFinished.filter {
                        it.value.authResultState == AuthResult.AuthResultState.SUCCESS
                    }
                    callback?.onSucceeded(onlySuccess.keys.toList().filterNotNull().toSet())
                }
                else if(error!=null){
                    callback?.onFailed(error.failureReason)
                }
            }
        }
    }
    private inner class BiometricAuthenticationCallbackImpl : BiometricAuthenticationListener {

        override fun onSuccess(module: BiometricType?) {
            checkAuthResultForSecondary(module, AuthResult.AuthResultState.SUCCESS)
        }

        override fun onHelp(helpReason: AuthenticationHelpReason?, msg: CharSequence?) {
            if (helpReason !== AuthenticationHelpReason.BIOMETRIC_ACQUIRED_GOOD && !msg.isNullOrEmpty() ) {
                if (dialog != null) dialog?.onHelp(msg)
            }
        }

        override fun onFailure(
            failureReason: AuthenticationFailureReason?,
            module: BiometricType?
        ) {

                IconStateHelper.errorType(module)
                dialog?.onFailure(failureReason == AuthenticationFailureReason.LOCKED_OUT)

            if (failureReason !== AuthenticationFailureReason.LOCKED_OUT) {
                //non fatal
                when (failureReason) {
                    AuthenticationFailureReason.SENSOR_FAILED, AuthenticationFailureReason.AUTHENTICATION_FAILED -> return
                }
                ExecutorHelper.INSTANCE.handler.post {
                    cancelAuthenticate()
                    checkAuthResultForSecondary(module, AuthResult.AuthResultState.FATAL_ERROR)
                }
            } else {
                HardwareAccessImpl.getInstance(builder.biometricAuthRequest).lockout()
                ExecutorHelper.INSTANCE.handler.postDelayed({
                    cancelAuthenticate()
                    checkAuthResultForSecondary(module, AuthResult.AuthResultState.FATAL_ERROR)
                }, 2000)
            }
        }
    }
}