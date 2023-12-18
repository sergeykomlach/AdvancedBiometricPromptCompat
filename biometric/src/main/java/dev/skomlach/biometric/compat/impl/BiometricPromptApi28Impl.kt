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

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.os.Build
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import androidx.annotation.ColorInt
import androidx.biometric.BiometricFragment
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricPrompt.PromptInfo
import androidx.biometric.CancellationHelper
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentManager
import dev.skomlach.biometric.compat.*
import dev.skomlach.biometric.compat.crypto.BiometricCryptoException
import dev.skomlach.biometric.compat.crypto.BiometricCryptoObjectHelper
import dev.skomlach.biometric.compat.engine.BiometricAuthentication
import dev.skomlach.biometric.compat.engine.BiometricAuthenticationListener
import dev.skomlach.biometric.compat.engine.core.RestartPredicatesImpl.defaultPredicate
import dev.skomlach.biometric.compat.impl.dialogs.BiometricPromptCompatDialogImpl
import dev.skomlach.biometric.compat.utils.BiometricErrorLockoutPermanentFix
import dev.skomlach.biometric.compat.utils.BiometricLockoutFix
import dev.skomlach.biometric.compat.utils.DevicesWithKnownBugs
import dev.skomlach.biometric.compat.utils.HardwareAccessImpl
import dev.skomlach.biometric.compat.utils.Vibro
import dev.skomlach.biometric.compat.utils.activityView.IconStateHelper
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.d
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.e
import dev.skomlach.biometric.compat.utils.notification.BiometricNotificationManager
import dev.skomlach.biometric.compat.utils.themes.DarkLightThemes
import dev.skomlach.common.misc.ExecutorHelper
import dev.skomlach.common.misc.Utils
import dev.skomlach.common.misc.Utils.isAtLeastR
import dev.skomlach.common.themes.monet.SystemColorScheme
import dev.skomlach.common.themes.monet.toArgb
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

@TargetApi(Build.VERSION_CODES.P)

class BiometricPromptApi28Impl(override val builder: BiometricPromptCompat.Builder) :
    IBiometricPromptImpl, AuthCallback {
    private val isOpened = AtomicBoolean(false)
    private val authCallTimestamp = AtomicLong(0)
    private val biometricPromptInfo: PromptInfo
        get() {
            val promptInfoBuilder = PromptInfo.Builder()
            builder.getTitle()?.let {
                promptInfoBuilder.setTitle(it)
            }

            builder.getDescription()?.let {
                promptInfoBuilder.setDescription(it)
            }

            if (!builder.forceDeviceCredential()) {
                builder.getSubtitle()?.let {
                    promptInfoBuilder.setSubtitle(it)
                }
                var buttonTextColor: Int =
                    ContextCompat.getColor(
                        builder.getContext(),
                        if (Utils.isAtLeastS) R.color.material_blue_500 else R.color.material_deep_teal_500
                    )

                if (Utils.isAtLeastS) {
                    val monetColors = SystemColorScheme()
                    if (DarkLightThemes.isNightModeCompatWithInscreen(builder.getContext()))
                        monetColors.accent2[100]?.toArgb()?.let {
                            buttonTextColor = it
                        }
                    else
                        monetColors.neutral2[500]?.toArgb()?.let {
                            buttonTextColor = it
                        }
                }
                (builder.getNegativeButtonText() ?: builder.getContext()
                    .getString(android.R.string.cancel)).let {
                    if (isAtLeastR) promptInfoBuilder.setNegativeButtonText(it) else promptInfoBuilder.setNegativeButtonText(
                        getFixedString(
                            it, color = buttonTextColor
                        )
                    )
                }
            }

            promptInfoBuilder.setDeviceCredentialAllowed(builder.forceDeviceCredential())

            promptInfoBuilder.setAllowedAuthenticators(
                if (builder.forceDeviceCredential())
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
                else
                    if (builder.getCryptographyPurpose() != null)
                        BiometricManager.Authenticators.BIOMETRIC_STRONG
                    else
                        (BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.BIOMETRIC_STRONG)
            )

            promptInfoBuilder.setConfirmationRequired(false)
            return promptInfoBuilder.build()
        }
    private val biometricPrompt: BiometricPrompt? by lazy {
        val activity = builder.getActivity()
        if (activity == null) null
        else
            BiometricPrompt(
                activity,
                ExecutorHelper.executor, authCallback
            )
    }
    private var restartPredicate = defaultPredicate()
    private var dialog: BiometricPromptCompatDialogImpl? = null
    private var callback: BiometricPromptCompat.AuthenticationCallback? = null
    private val authFinished: MutableMap<BiometricType?, AuthResult> =
        HashMap<BiometricType?, AuthResult>()
    private var biometricFragment: AtomicReference<BiometricFragment?> =
        AtomicReference<BiometricFragment?>(null)
    private val fmAuthCallback: BiometricAuthenticationListener =
        BiometricAuthenticationCallbackImpl()

    private val authCallback: BiometricPrompt.AuthenticationCallback =
        object : BiometricPrompt.AuthenticationCallback() {
            private var errorTs = 0L
            private val skipTimeout =
                builder.getContext().resources.getInteger(android.R.integer.config_shortAnimTime)

            override fun onAuthenticationFailed() {
                d("BiometricPromptApi28Impl.onAuthenticationFailed")
                if (callback != null) {
                    ExecutorHelper.post {
                        dialog?.onFailure(false)
                        for (module in (if (isNativeBiometricWorkaroundRequired) builder.getAllAvailableTypes() else builder.getPrimaryAvailableTypes())) {
                            IconStateHelper.errorType(module)
                        }
                    }
                }
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                d("BiometricPromptApi28Impl.onAuthenticationError: $errorCode $errString")
                val tmp = System.currentTimeMillis()
                if (tmp - errorTs <= skipTimeout)
                    return
                errorTs = tmp
                //...present normal failed screen...

                ExecutorHelper.post(Runnable {
                    var failureReason = AuthenticationFailureReason.UNKNOWN
                    when (if (errorCode < 1000) errorCode else errorCode % 1000) {
                        BiometricPrompt.ERROR_NO_BIOMETRICS -> {
                            failureReason =
                                AuthenticationFailureReason.NO_BIOMETRICS_REGISTERED
                        }

                        BiometricPrompt.ERROR_HW_NOT_PRESENT -> {
                            failureReason =
                                AuthenticationFailureReason.NO_HARDWARE
                        }

                        BiometricPrompt.ERROR_HW_UNAVAILABLE, BiometricPrompt.ERROR_SECURITY_UPDATE_REQUIRED, BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL -> {
                            failureReason =
                                AuthenticationFailureReason.HARDWARE_UNAVAILABLE
                        }

                        BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> {
                            for (t in builder.getPrimaryAvailableTypes()) {
                                BiometricErrorLockoutPermanentFix.setBiometricSensorPermanentlyLocked(
                                    t
                                )
                            }
                            failureReason = AuthenticationFailureReason.HARDWARE_UNAVAILABLE
                        }

                        BiometricPrompt.ERROR_UNABLE_TO_PROCESS -> {
                            failureReason =
                                AuthenticationFailureReason.SENSOR_FAILED
                        }

                        BiometricPrompt.ERROR_NO_SPACE -> {
                            failureReason =
                                AuthenticationFailureReason.SENSOR_FAILED
                        }

                        BiometricPrompt.ERROR_TIMEOUT -> {
                            failureReason =
                                AuthenticationFailureReason.TIMEOUT
                        }

                        BiometricPrompt.ERROR_LOCKOUT -> {
                            HardwareAccessImpl.getInstance(builder.getBiometricAuthRequest())
                                .lockout()
                            failureReason = AuthenticationFailureReason.LOCKED_OUT
                        }

                        BiometricPrompt.ERROR_CANCELED, BiometricPrompt.ERROR_USER_CANCELED, BiometricPrompt.ERROR_NEGATIVE_BUTTON -> {
                            cancelAuth()

                            return@Runnable
                        }

                        else -> {
                            callback?.onFailed(failureReason)
                            postCancelTask {
                                cancelAuth()

                            }
                            return@Runnable
                        }
                    }
                    if (restartPredicate.invoke(failureReason)) {
                        if (callback != null) {
                            ExecutorHelper.post {
                                dialog?.onFailure(
                                    failureReason == AuthenticationFailureReason.LOCKED_OUT
                                )
                                for (module in (if (isNativeBiometricWorkaroundRequired) builder.getAllAvailableTypes() else builder.getPrimaryAvailableTypes())) {
                                    IconStateHelper.errorType(module)
                                }
                            }
                        }
                    } else {
                        checkAuthResultForPrimary(
                            AuthResult.AuthResultState.FATAL_ERROR,
                            null,
                            failureReason
                        )
                    }
                })
            }

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                d("BiometricPromptApi28Impl.onAuthenticationSucceeded: _skomlach ${result.authenticationType}; Crypto=${result.cryptoObject}")
                val tmp = System.currentTimeMillis()
                if (tmp - errorTs <= skipTimeout || tmp - authCallTimestamp.get() <= skipTimeout)
                    return
                errorTs = tmp

                if (biometricPromptInfo.isDeviceCredentialAllowed) {
                    BiometricErrorLockoutPermanentFix.resetBiometricSensorPermanentlyLocked()
                    BiometricLockoutFix.reset()
                }
                checkAuthResultForPrimary(AuthResult.AuthResultState.SUCCESS, result.cryptoObject)
            }
        }
    private val isFingerprint = AtomicBoolean(false)
    private var cancelTask: Runnable? = null

    @Throws(Throwable::class)
    fun finalize() {
        cancelTask?.let {
            ExecutorHelper.removeCallbacks(it)
        }
    }

    fun postCancelTask(runnable: Runnable?) {
        cancelTask?.let {
            ExecutorHelper.removeCallbacks(it)
        }
        cancelTask = runnable
        ExecutorHelper.postDelayed(runnable ?: return, 2000)
    }

    init {
        isFingerprint.set(
            builder.getPrimaryAvailableTypes().contains(BiometricType.BIOMETRIC_FINGERPRINT)
        )
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

    override fun authenticate(cbk: BiometricPromptCompat.AuthenticationCallback?) {
        d("BiometricPromptApi28Impl.authenticate():")
        this.restartPredicate = defaultPredicate()
        this.authFinished.clear()
        this.biometricFragment.set(null)
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

    }

    override fun cancelAuthentication() {
        onUiClosed()
        e("BiometricPromptApi28Impl.cancelAuthentication():")
        if (dialog != null) dialog?.dismissDialog() else {
            stopAuth()
        }
    }

    private val finalTask = Runnable {
        val secondary = ArrayList<BiometricType>(builder.getSecondaryAvailableTypes())
        BiometricAuthentication.cancelAuthentication()
        val finished = secondary.filter { type ->
            authFinished.keys.contains(type)
        }
        secondary.removeAll(finished.toSet())
        secondary.forEach {
            checkAuthResultForSecondary(
                AuthenticationResult(confirmed = it),
                AuthResult.AuthResultState.FATAL_ERROR,
                AuthenticationFailureReason.TIMEOUT
            )
        }

    }

    override fun startAuth() {
        d("BiometricPromptApi28Impl.startAuth():")
        val prompt = biometricPrompt
        if (prompt == null) {
            callback?.onCanceled()
            return
        }
        val shortDelayMillis =
            builder.getContext().resources.getInteger(android.R.integer.config_shortAnimTime)
                .toLong()
        val secondary = ArrayList<BiometricType>(builder.getSecondaryAvailableTypes())
        onUiOpened()
        showSystemUi(prompt)
        if (secondary.isNotEmpty()) {
            ExecutorHelper.postDelayed({
                if (!isNativeBiometricWorkaroundRequired) {
                    BiometricAuthentication.authenticate(
                        builder.getCryptographyPurpose(),
                        null,
                        secondary,
                        fmAuthCallback,
                        BundleBuilder.create(builder)
                    )
                } else {
                    BiometricAuthentication.authenticate(
                        builder.getCryptographyPurpose(),
                        null,
                        secondary,
                        object : BiometricAuthenticationListener {
                            override fun onSuccess(module: AuthenticationResult?) {
                                checkAuthResultForSecondary(
                                    module,
                                    AuthResult.AuthResultState.SUCCESS
                                )
                            }

                            override fun onHelp(msg: CharSequence?) {

                            }

                            override fun onFailure(
                                failureReason: AuthenticationFailureReason?,
                                module: BiometricType?
                            ) {
                                checkAuthResultForSecondary(
                                    AuthenticationResult(confirmed = module),
                                    AuthResult.AuthResultState.FATAL_ERROR,
                                    failureReason
                                )
                            }


                            override fun onCanceled(module: BiometricType?) {}
                        },
                        BundleBuilder.create(builder)
                    )

                    ExecutorHelper.postDelayed(finalTask, 1500)
                }
            }, shortDelayMillis)
        }

    }

    @SuppressLint("RestrictedApi")
    private fun showSystemUi(biometricPrompt: BiometricPrompt) {
        try {

            var biometricCryptoObject: BiometricCryptoObject? = null
            builder.getCryptographyPurpose()?.let {
                try {
                    biometricCryptoObject = BiometricCryptoObjectHelper.getBiometricCryptoObject(
                        "BiometricPromptCompat",
                        builder.getCryptographyPurpose(),
                        true
                    )
                } catch (e: BiometricCryptoException) {
                    if (builder.getCryptographyPurpose()?.purpose == BiometricCryptographyPurpose.ENCRYPT) {
                        BiometricCryptoObjectHelper.deleteCrypto("BiometricPromptCompat")
                        biometricCryptoObject =
                            BiometricCryptoObjectHelper.getBiometricCryptoObject(
                                "BiometricPromptCompat",
                                builder.getCryptographyPurpose(),
                                true
                            )
                    } else throw e
                }
            }

            val crpObject =
                if (biometricCryptoObject?.cipher != null)
                    biometricCryptoObject?.cipher?.let { BiometricPrompt.CryptoObject(it) }
                else if (biometricCryptoObject?.mac != null)
                    biometricCryptoObject?.mac?.let { BiometricPrompt.CryptoObject(it) }
                else if (biometricCryptoObject?.signature != null)
                    biometricCryptoObject?.signature?.let { BiometricPrompt.CryptoObject(it) }
                else
                    null

            d("BiometricPromptCompat.authenticate:  Crypto=$crpObject")
            if (crpObject != null) {
                try {
                    authCallTimestamp.set(System.currentTimeMillis())
                    biometricPrompt.authenticate(biometricPromptInfo, crpObject)
                } catch (e: Throwable) {
                    authCallTimestamp.set(System.currentTimeMillis())
                    biometricPrompt.authenticate(biometricPromptInfo)
                }
            } else {
                authCallTimestamp.set(System.currentTimeMillis())
                biometricPrompt.authenticate(biometricPromptInfo)
            }
            ExecutorHelper.startOnBackground {
                //fallback - sometimes we not able to cancel BiometricPrompt properly
                try {
                    val m = BiometricPrompt::class.java.declaredMethods.first {
                        it.parameterTypes.size == 1 && it.parameterTypes[0] == FragmentManager::class.java && it.returnType == BiometricFragment::class.java
                    }
                    val isAccessible = m.isAccessible
                    try {
                        if (!isAccessible)
                            m.isAccessible = true
                        biometricFragment.set(
                            m.invoke(
                                null,
                                builder.getActivity()?.supportFragmentManager
                            ) as BiometricFragment?
                        )
                    } finally {
                        if (!isAccessible)
                            m.isAccessible = false
                    }
                } catch (e: Throwable) {
                    e(e)
                }
            }
        } catch (e: BiometricCryptoException) {
            e(e)
            checkAuthResultForPrimary(
                AuthResult.AuthResultState.FATAL_ERROR,
                null,
                AuthenticationFailureReason.CRYPTO_ERROR
            )
        }
    }


    override fun stopAuth() {
        e("BiometricPromptApi28Impl.stopAuth():")
        ExecutorHelper.removeCallbacks(finalTask)
        BiometricAuthentication.cancelAuthentication()
        biometricFragment.get()?.let {
            CancellationHelper.forceCancel(it)
        } ?: run {
            biometricPrompt?.cancelAuthentication()
        }
        biometricFragment.set(null)
    }

    override fun cancelAuth() {
        try {
            val success =
                authFinished.values.lastOrNull { it.authResultState == AuthResult.AuthResultState.SUCCESS }
            e("BiometricPromptApi28Impl.cancelAuth(): $success")
            if (success != null)
                return
            callback?.onCanceled()
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
        d("BiometricPromptApi28Impl.onUIClosed():")
        callback?.onUIClosed()
        isOpened.set(false)
    }

    private val isNativeBiometricWorkaroundRequired: Boolean
        get() {
            val candidatesAll = builder.getAllAvailableTypes().filter {
                it != BiometricType.BIOMETRIC_ANY
            }

            if (DevicesWithKnownBugs.systemDealWithBiometricPrompt) //Samsung OR Android 13+
            {
                return candidatesAll.size > 1 && builder.getPrimaryAvailableTypes()
                    .contains(BiometricType.BIOMETRIC_FINGERPRINT)
            }
            return false
        }

    private fun checkAuthResultForPrimary(
        authResult: AuthResult.AuthResultState,
        cryptoObject: BiometricPrompt.CryptoObject?,
        reason: AuthenticationFailureReason? = null
    ) {
        if (!isOpened.get())
            return
        d("BiometricPromptApi28Impl.checkAuthResultForPrimary(): stage 1")
        var failureReason = reason
        if (mutableListOf(
                AuthenticationFailureReason.SENSOR_FAILED,
                AuthenticationFailureReason.AUTHENTICATION_FAILED
            ).contains(failureReason)
        ) {
            HardwareAccessImpl.getInstance(builder.getBiometricAuthRequest()).lockout()
            failureReason = AuthenticationFailureReason.LOCKED_OUT
        }
        var added = false
        val crypto = if (cryptoObject == null) null else {
            BiometricCryptoObject(cryptoObject.signature, cryptoObject.cipher, cryptoObject.mac)
        }
        if (biometricPromptInfo.isDeviceCredentialAllowed) {
            authFinished[BiometricType.BIOMETRIC_ANY] =
                AuthResult(
                    authResult,
                    AuthenticationResult(BiometricType.BIOMETRIC_ANY, crypto),
                    failureReason
                )
            added = true
        } else
            for (module in (if (isNativeBiometricWorkaroundRequired) builder.getAllAvailableTypes() else builder.getPrimaryAvailableTypes())) {
                authFinished[module] =
                    AuthResult(authResult, AuthenticationResult(module, crypto), failureReason)
                added = true

                BiometricNotificationManager.dismiss(module)
                if (AuthResult.AuthResultState.SUCCESS == authResult) {
                    IconStateHelper.successType(module)
                } else
                    IconStateHelper.errorType(module)


            }
        dialog?.authFinishedCopy = authFinished
        d("BiometricPromptApi28Impl.checkAuthResultForPrimary(): stage 2")
        if (added && builder.getBiometricAuthRequest().confirmation == BiometricConfirmation.ALL && AuthResult.AuthResultState.SUCCESS == authResult) {
            Vibro.start()
        }

        val authFinishedList: List<BiometricType?> = java.util.ArrayList(authFinished.keys)
        val allList: MutableList<BiometricType?> = java.util.ArrayList(
            builder.getAllAvailableTypes()
        )
        allList.removeAll(authFinishedList)
        d("BiometricPromptApi28Impl.checkAuthResultForPrimary.authFinished - ${builder.getBiometricAuthRequest()}: $allList; ($authFinished / ${builder.getAllAvailableTypes()})")
        val error =
            authFinished.values.lastOrNull { it.authResultState == AuthResult.AuthResultState.FATAL_ERROR }
        val success =
            authFinished.values.lastOrNull { it.authResultState == AuthResult.AuthResultState.SUCCESS }
        d("BiometricPromptApi28Impl.checkAuthResultForPrimary.authFinished - ${builder.getBiometricAuthRequest()}: $error/$success")
        if (((success != null || allList.isEmpty()) && builder.getBiometricAuthRequest().confirmation == BiometricConfirmation.ANY) ||
            (builder.getBiometricAuthRequest().confirmation == BiometricConfirmation.ALL && (DevicesWithKnownBugs.systemDealWithBiometricPrompt || allList.isEmpty()))
        ) {
            if (success != null) {
                val onlySuccess = authFinished.filter {
                    it.value.authResultState == AuthResult.AuthResultState.SUCCESS
                }
                val fixCryptoObjects = builder.getCryptographyPurpose()?.purpose == null
                d("BiometricPromptApi28Impl.checkAuthResultForPrimary() -> onSucceeded")
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
                cancelAuthentication()
            } else if (error != null) {
                if (error.failureReason !== AuthenticationFailureReason.LOCKED_OUT || DevicesWithKnownBugs.isHideDialogInstantly) {
                    e("BiometricPromptApi28Impl.checkAuthResultForPrimary() -> onFailed")
                    callback?.onFailed(error.failureReason)
                    cancelAuthentication()
                } else {
                    ExecutorHelper.postDelayed({
                        e("BiometricPromptApi28Impl.checkAuthResultForPrimary() -> onFailed")
                        callback?.onFailed(error.failureReason)
                        cancelAuthentication()
                    }, 2000)
                }
            }


        } else if (allList.isNotEmpty()) {
            if (dialog == null) {
                dialog =
                    BiometricPromptCompatDialogImpl(
                        builder, object : AuthCallback {
                            private val ignoreFirstOpen = AtomicBoolean(true)
                            override fun startAuth() {
                                if (ignoreFirstOpen.getAndSet(false))
                                    return
                                this@BiometricPromptApi28Impl.startAuth()
                            }

                            override fun stopAuth() {
                                this@BiometricPromptApi28Impl.stopAuth()
                            }

                            override fun cancelAuth() {
                                this@BiometricPromptApi28Impl.cancelAuth()
                            }

                            override fun onUiOpened() {
                                this@BiometricPromptApi28Impl.onUiOpened()
                            }

                            override fun onUiClosed() {
                                this@BiometricPromptApi28Impl.onUiClosed()
                            }
                        },
                        builder.getSecondaryAvailableTypes()
                            .contains(BiometricType.BIOMETRIC_FINGERPRINT)
                                && DevicesWithKnownBugs.hasUnderDisplayFingerprint
                    )
                dialog?.authFinishedCopy = authFinished
            }
            dialog?.showDialog()
        }
    }

    private fun checkAuthResultForSecondary(
        module: AuthenticationResult?,
        authResult: AuthResult.AuthResultState,
        failureReason: AuthenticationFailureReason? = null
    ) {
        if (!isOpened.get())
            return
        d("BiometricPromptApi28Impl.checkAuthResultForSecondary():")

        if (authResult == AuthResult.AuthResultState.SUCCESS) {
            if (builder.getBiometricAuthRequest().confirmation == BiometricConfirmation.ALL) {
                Vibro.start()
            }
            IconStateHelper.successType(module?.confirmed)
        } else if (authResult == AuthResult.AuthResultState.FATAL_ERROR) {
            dialog?.onFailure(failureReason == AuthenticationFailureReason.LOCKED_OUT)
            IconStateHelper.errorType(module?.confirmed)
        }

        //non fatal
        if (mutableListOf(
                AuthenticationFailureReason.SENSOR_FAILED,
                AuthenticationFailureReason.AUTHENTICATION_FAILED
            ).contains(failureReason)
        ) {
            return
        }
        authFinished[module?.confirmed] = AuthResult(authResult, module, failureReason)
        dialog?.authFinishedCopy = authFinished
        BiometricNotificationManager.dismiss(module?.confirmed)

        val authFinishedList: List<BiometricType?> = java.util.ArrayList(authFinished.keys)
        val allList: MutableList<BiometricType?> = java.util.ArrayList(
            builder.getAllAvailableTypes()
        )
        allList.removeAll(authFinishedList)
        d("checkAuthResultForSecondary.authFinished - ${builder.getBiometricAuthRequest()}: $allList; ($authFinished / ${builder.getAllAvailableTypes()})")
        val error =
            authFinished.values.lastOrNull { it.authResultState == AuthResult.AuthResultState.FATAL_ERROR }
        val success =
            authFinished.values.lastOrNull { it.authResultState == AuthResult.AuthResultState.SUCCESS }

        d("BiometricPromptApi28Impl.checkAuthResultForSecondary.authFinished - ${builder.getBiometricAuthRequest()}: $error/$success")
        if (((success != null || allList.isEmpty()) && builder.getBiometricAuthRequest().confirmation == BiometricConfirmation.ANY) ||
            (builder.getBiometricAuthRequest().confirmation == BiometricConfirmation.ALL && allList.isEmpty())
        ) {


            if (success != null) {
                val onlySuccess = authFinished.filter {
                    it.value.authResultState == AuthResult.AuthResultState.SUCCESS
                }
                val fixCryptoObjects = builder.getCryptographyPurpose()?.purpose == null
                d("BiometricPromptApi28Impl.checkAuthResultForSecondary() -> onSucceeded")
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
                cancelAuth()

            } else if (error != null) {
                if (error.failureReason !== AuthenticationFailureReason.LOCKED_OUT || DevicesWithKnownBugs.isHideDialogInstantly) {
                    e("BiometricPromptApi28Impl.checkAuthResultForSecondary() -> onFailed")
                    callback?.onFailed(error.failureReason)
                    cancelAuth()

                } else {
                    ExecutorHelper.postDelayed({
                        e("BiometricPromptApi28Impl.checkAuthResultForSecondary() -> onFailed")
                        callback?.onFailed(error.failureReason)
                        cancelAuth()

                    }, 2000)
                }
            }


        }
    }

    private inner class BiometricAuthenticationCallbackImpl : BiometricAuthenticationListener {

        override fun onSuccess(module: AuthenticationResult?) {
            checkAuthResultForSecondary(module, AuthResult.AuthResultState.SUCCESS)
        }

        override fun onHelp(msg: CharSequence?) {
            if (!msg.isNullOrEmpty()) {
                if (dialog != null) dialog?.onHelp(msg)
            }
        }

        override fun onFailure(
            failureReason: AuthenticationFailureReason?,
            module: BiometricType?
        ) {
            checkAuthResultForSecondary(
                AuthenticationResult(confirmed = module),
                AuthResult.AuthResultState.FATAL_ERROR,
                failureReason
            )
        }

        override fun onCanceled(module: BiometricType?) {
            cancelAuth()
        }
    }
}