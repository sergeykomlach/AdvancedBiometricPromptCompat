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

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
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
import dev.skomlach.biometric.compat.engine.BiometricAuthentication
import dev.skomlach.biometric.compat.engine.BiometricAuthenticationListener
import dev.skomlach.biometric.compat.engine.BiometricCodes
import dev.skomlach.biometric.compat.engine.core.RestartPredicatesImpl.defaultPredicate
import dev.skomlach.biometric.compat.impl.dialogs.BiometricPromptCompatDialogImpl
import dev.skomlach.biometric.compat.utils.BiometricErrorLockoutPermanentFix
import dev.skomlach.biometric.compat.utils.CodeToString.getErrorCode
import dev.skomlach.biometric.compat.utils.DevicesWithKnownBugs
import dev.skomlach.biometric.compat.utils.DevicesWithKnownBugs.isOnePlusWithBiometricBug
import dev.skomlach.biometric.compat.utils.HardwareAccessImpl
import dev.skomlach.biometric.compat.utils.Vibro
import dev.skomlach.biometric.compat.utils.activityView.IconStateHelper
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.d
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.e
import dev.skomlach.biometric.compat.utils.monet.SystemColorScheme
import dev.skomlach.biometric.compat.utils.monet.toArgb
import dev.skomlach.biometric.compat.utils.notification.BiometricNotificationManager
import dev.skomlach.biometric.compat.utils.themes.DarkLightThemes
import dev.skomlach.common.misc.ExecutorHelper
import dev.skomlach.common.misc.Utils
import dev.skomlach.common.misc.Utils.isAtLeastR
import java.security.KeyStore
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

@TargetApi(Build.VERSION_CODES.P)

class BiometricPromptApi28Impl(override val builder: BiometricPromptCompat.Builder) :
    IBiometricPromptImpl, BiometricCodes, AuthCallback {
    private val biometricPromptInfo: PromptInfo
    private val biometricPrompt: BiometricPrompt
    private val restartPredicate = defaultPredicate()
    private var dialog: BiometricPromptCompatDialogImpl? = null
    private var callback: BiometricPromptCompat.AuthenticationCallback? = null
    private val authFinished: MutableMap<BiometricType?, AuthResult> =
        HashMap<BiometricType?, AuthResult>()
    private var biometricFragment: BiometricFragment? = null
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
                    cancelAuthentication()
                } else {
                    //...normal failed processing...//
                    for (module in builder.getPrimaryAvailableTypes()) {
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

                ExecutorHelper.post(Runnable {
                    var failureReason = AuthenticationFailureReason.UNKNOWN
                    when (errorCode) {
                        BiometricPrompt.ERROR_NO_BIOMETRICS -> failureReason =
                            AuthenticationFailureReason.NO_BIOMETRICS_REGISTERED
                        BiometricPrompt.ERROR_HW_NOT_PRESENT -> failureReason =
                            AuthenticationFailureReason.NO_HARDWARE
                        BiometricPrompt.ERROR_HW_UNAVAILABLE -> failureReason =
                            AuthenticationFailureReason.HARDWARE_UNAVAILABLE
                        BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> {
                            for (t in builder.getPrimaryAvailableTypes()) {
                                BiometricErrorLockoutPermanentFix.setBiometricSensorPermanentlyLocked(
                                    t
                                )
                            }
                            failureReason = AuthenticationFailureReason.HARDWARE_UNAVAILABLE
                        }
                        BiometricPrompt.ERROR_UNABLE_TO_PROCESS -> failureReason =
                            AuthenticationFailureReason.HARDWARE_UNAVAILABLE
                        BiometricPrompt.ERROR_NO_SPACE -> failureReason =
                            AuthenticationFailureReason.SENSOR_FAILED
                        BiometricPrompt.ERROR_TIMEOUT -> failureReason =
                            AuthenticationFailureReason.TIMEOUT
                        BiometricPrompt.ERROR_LOCKOUT -> {
                            HardwareAccessImpl.getInstance(builder.getBiometricAuthRequest())
                                .lockout()
                            failureReason = AuthenticationFailureReason.LOCKED_OUT
                        }
                        else -> {
                            callback?.onCanceled()
                            cancelAuthentication()
                            return@Runnable
                        }
                    }
                    if (restartPredicate.invoke(failureReason)) {
                        if (callback != null) {
                            for (module in builder.getPrimaryAvailableTypes()) {
                                IconStateHelper.errorType(module)
                            }
                            dialog?.onFailure(
                                failureReason == AuthenticationFailureReason.LOCKED_OUT
                            )
                            authenticate(callback)
                        }
                    } else {
                        checkAuthResultForPrimary(
                            AuthResult.AuthResultState.FATAL_ERROR,
                            failureReason
                        )
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

    private var forceToFingerprint = false

    init {
        val promptInfoBuilder = PromptInfo.Builder()
        builder.getTitle()?.let {
            promptInfoBuilder.setTitle(it)
        }

        builder.getSubtitle()?.let {
            promptInfoBuilder.setSubtitle(it)
        }

        builder.getDescription()?.let {
            promptInfoBuilder.setDescription(it)
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

        builder.getNegativeButtonText()?.let {
            if (isAtLeastR) promptInfoBuilder.setNegativeButtonText(it) else promptInfoBuilder.setNegativeButtonText(
                getFixedString(
                    it, color = buttonTextColor
                )
            )
        }
        //Should force to Fingerprint-only
        if (builder.isExperimentalFeaturesEnabled() && builder.getPrimaryAvailableTypes().size == 1 && builder.getPrimaryAvailableTypes()
                .toMutableList()[0] == BiometricType.BIOMETRIC_FINGERPRINT
        ) {
            forceToFingerprint = true
        }
        if (isAtLeastR) {
            promptInfoBuilder.setAllowedAuthenticators(if (forceToFingerprint) BiometricManager.Authenticators.BIOMETRIC_STRONG else BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.BIOMETRIC_STRONG)
        } else {
            promptInfoBuilder.setDeviceCredentialAllowed(false)
        }
        promptInfoBuilder.setConfirmationRequired(true)
        biometricPromptInfo = promptInfoBuilder.build()
        biometricPrompt = BiometricPrompt(
            builder.getContext(),
            ExecutorHelper.executor, authCallback
        )
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


    override fun cancelAuthentication() {
        d("BiometricPromptApi28Impl.cancelAuthentication():")
        if (dialog != null) dialog?.dismissDialog() else {
            stopAuth()
        }
        onUiClosed()
    }

    private fun hasPrimaryFinished(): Boolean {

        val authFinishedList: List<BiometricType?> = ArrayList(authFinished.keys)
        val allList: MutableList<BiometricType?> = ArrayList(
            builder.getPrimaryAvailableTypes()
        )
        allList.removeAll(authFinishedList)
        return allList.isEmpty()
    }

    private fun hasSecondaryFinished(): Boolean {

        val authFinishedList: List<BiometricType?> = ArrayList(authFinished.keys)
        val allList: MutableList<BiometricType?> = ArrayList(
            builder.getSecondaryAvailableTypes()
        )
        allList.removeAll(authFinishedList)
        return allList.isEmpty()
    }

    override fun startAuth() {
        d("BiometricPromptApi28Impl.startAuth():")

        val candidates = builder.getAllAvailableTypes().filter {
            it != BiometricType.BIOMETRIC_ANY
        }
        val withoutFingerprint = candidates.filter {
            it != BiometricType.BIOMETRIC_FINGERPRINT
        }
        val isSamsungWorkaroundRequired =
            DevicesWithKnownBugs.isSamsung && candidates.size > 1 //If only one - let the system to deal with this

        if (!isSamsungWorkaroundRequired) {
            if (!hasSecondaryFinished()) {
                val secondary = HashSet<BiometricType>(builder.getSecondaryAvailableTypes())
                if (secondary.isNotEmpty()) {
                    BiometricAuthentication.authenticate(
                        null,
                        ArrayList<BiometricType>(secondary),
                        fmAuthCallback
                    )
                }
            }

            if (!hasPrimaryFinished()) {
                showSystemUi(biometricPrompt)
            }

        } else {
            val delayMillis = 1500L
            val shortDelayMillis =
                builder.getContext().resources.getInteger(android.R.integer.config_shortAnimTime)
                    .toLong()
            val successList = mutableSetOf<BiometricType>()
            BiometricAuthentication.authenticate(
                null,
                ArrayList<BiometricType>(withoutFingerprint),
                object : BiometricAuthenticationListener {
                    override fun onSuccess(module: BiometricType?) {
                        module?.let {
                            successList.add(it)
                        }
                    }

                    override fun onHelp(
                        helpReason: AuthenticationHelpReason?,
                        msg: CharSequence?
                    ) {
                    }

                    override fun onFailure(
                        failureReason: AuthenticationFailureReason?,
                        module: BiometricType?
                    ) {
                    }

                    override fun onCanceled(module: BiometricType?) {}
                }
            )
            val finalTaskExecuted = AtomicBoolean(false)
            val finalTask = Runnable {
                finalTaskExecuted.set(true)
                //Flow for Samsung devices
                BiometricAuthentication.cancelAuthentication()

                if (successList.isNotEmpty()) {
                    val dialogClosed = AtomicBoolean(false)
                    showSystemUi(BiometricPrompt(
                        builder.getContext(),
                        ExecutorHelper.executor,
                        object : BiometricPrompt.AuthenticationCallback() {
                            var onePlusWithBiometricBugFailure = false
                            override fun onAuthenticationFailed() {
                                if (isOnePlusWithBiometricBug) {
                                    onePlusWithBiometricBugFailure = true
                                    if (!dialogClosed.get()) {
                                        dialogClosed.set(true)
                                        stopAuth()
                                        callback?.onSucceeded(successList)
                                    }
                                }
                            }

                            override fun onAuthenticationError(
                                errorCode: Int,
                                errString: CharSequence
                            ) {
                                if (onePlusWithBiometricBugFailure) {
                                    onePlusWithBiometricBugFailure = false
                                    return
                                }
                                if (!dialogClosed.get()) {
                                    dialogClosed.set(true)
                                    stopAuth()
                                    callback?.onSucceeded(successList)
                                }
                            }

                            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                if (!dialogClosed.get()) {
                                    dialogClosed.set(true)
                                    stopAuth()
                                    callback?.onSucceeded(successList)
                                }
                            }

                        }
                    )
                    )
                    ExecutorHelper.postDelayed({
                        if (!dialogClosed.get()) {
                            dialogClosed.set(true)
                            stopAuth()
                            callback?.onSucceeded(successList)
                        }
                    }, delayMillis)
                } else
                //general case
                {
                    if (!hasPrimaryFinished()) {
                        showSystemUi(biometricPrompt)
                    }
                }

            }
            val resultCheckTask = object : Runnable {
                override fun run() {
                    if (!finalTaskExecuted.get()) {
                        if (successList.isNotEmpty()) {
                            ExecutorHelper.removeCallbacks(finalTask)
                            finalTask.run()
                        } else {
                            ExecutorHelper.postDelayed(this, shortDelayMillis)
                        }
                    }
                }
            }
            ExecutorHelper.postDelayed(finalTask, delayMillis)
            ExecutorHelper.postDelayed(resultCheckTask, shortDelayMillis)
        }
    }

    private fun showSystemUi(biometricPrompt: BiometricPrompt) {

        //Should force to Fingerprint-only
        val crpObject: BiometricPrompt.CryptoObject? = try {
            if (forceToFingerprint && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) cryptoObject else null
        } catch (e: Throwable) {
            null
        }
        if (crpObject != null) {
            try {
                biometricPrompt.authenticate(biometricPromptInfo, crpObject)
            } catch (e: Throwable) {
                biometricPrompt.authenticate(biometricPromptInfo)
            }
        } else {
            biometricPrompt.authenticate(biometricPromptInfo)
        }

        //fallback - sometimes we not able to cancel BiometricPrompt properly
        try {
            val m = BiometricPrompt::class.java.declaredMethods.first {
                it.parameterTypes.size == 1 && it.parameterTypes[0] == FragmentManager::class.java && it.returnType == BiometricFragment::class.java
            }
            val isAccessible = m.isAccessible
            try {
                if (!isAccessible)
                    m.isAccessible = true
                biometricFragment = m.invoke(
                    null,
                    builder.getContext().supportFragmentManager
                ) as BiometricFragment?
            } finally {
                if (!isAccessible)
                    m.isAccessible = false
            }
        } catch (e: Throwable) {
            e(e)
        }
    }

    override fun stopAuth() {
        d("BiometricPromptApi28Impl.stopAuth():")
        BiometricAuthentication.cancelAuthentication()
        biometricFragment?.let {
            CancellationHelper.forceCancel(biometricFragment)
        } ?: run {
            biometricPrompt.cancelAuthentication()
        }
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
        dropKeystore()
    }

    private fun checkAuthResultForPrimary(
        authResult: AuthResult.AuthResultState,
        reason: AuthenticationFailureReason? = null
    ) {
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
        for (module in builder.getPrimaryAvailableTypes()) {
            authFinished[module] = AuthResult(authResult, failureReason)
            dialog?.authFinishedCopy = authFinished
            if (AuthResult.AuthResultState.SUCCESS == authResult) {
                IconStateHelper.successType(module)
            } else
                IconStateHelper.errorType(module)
            added = true
            BiometricNotificationManager.dismiss(module)
        }
        if (added && builder.getBiometricAuthRequest().confirmation == BiometricConfirmation.ALL && AuthResult.AuthResultState.SUCCESS == authResult) {
            Vibro.start()
        }

        val authFinishedList: List<BiometricType?> = java.util.ArrayList(authFinished.keys)
        val allList: MutableList<BiometricType?> = java.util.ArrayList(
            builder.getAllAvailableTypes()
        )
        allList.removeAll(authFinishedList)
        d("checkAuthResultForPrimary.authFinished - ${builder.getBiometricAuthRequest()}: $allList; ($authFinished / ${builder.getAllAvailableTypes()})")
        val error =
            authFinished.values.lastOrNull { it.authResultState == AuthResult.AuthResultState.FATAL_ERROR }
        val success =
            authFinished.values.lastOrNull { it.authResultState == AuthResult.AuthResultState.SUCCESS }
        d("checkAuthResultForPrimary.authFinished - ${builder.getBiometricAuthRequest()}: $error/$success")
        if (((success != null || allList.isEmpty()) && builder.getBiometricAuthRequest().confirmation == BiometricConfirmation.ANY) ||
            (builder.getBiometricAuthRequest().confirmation == BiometricConfirmation.ALL && (DevicesWithKnownBugs.isSamsung || allList.isEmpty()))
        ) {
            ExecutorHelper.post {
                cancelAuthentication()
                if (success != null) {
                    val onlySuccess = authFinished.filter {
                        it.value.authResultState == AuthResult.AuthResultState.SUCCESS
                    }
                    callback?.onSucceeded(onlySuccess.keys.toList().filterNotNull().toSet())
                } else if (error != null) {
                    if (failureReason == AuthenticationFailureReason.LOCKED_OUT) {
                        ExecutorHelper.postDelayed({
                            callback?.onFailed(error.failureReason)
                        }, 2000)
                    } else {
                        callback?.onFailed(error.failureReason)
                    }
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
        if (mutableListOf(
                AuthenticationFailureReason.SENSOR_FAILED,
                AuthenticationFailureReason.AUTHENTICATION_FAILED
            ).contains(failureReason)
        ) {
            return
        }
        authFinished[module] = AuthResult(authResult, failureReason)
        dialog?.authFinishedCopy = authFinished
        BiometricNotificationManager.dismiss(module)

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

        d("checkAuthResultForSecondary.authFinished - ${builder.getBiometricAuthRequest()}: $error/$success")
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
            checkAuthResultForSecondary(module, AuthResult.AuthResultState.SUCCESS)
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
            checkAuthResultForSecondary(
                module,
                AuthResult.AuthResultState.FATAL_ERROR,
                failureReason
            )
        }

        override fun onCanceled(module: BiometricType?) {
            cancelAuth()
            cancelAuthentication()
        }
    }

    //====================================================================================
    //region Dummy crypto object that is used just to block Face, Iris scan
    //====================================================================================
    private val ENCRYPTION_BLOCK_MODE = KeyProperties.BLOCK_MODE_GCM
    private val ENCRYPTION_PADDING = KeyProperties.ENCRYPTION_PADDING_NONE
    private val ENCRYPTION_ALGORITHM = KeyProperties.KEY_ALGORITHM_AES
    private val KEY_SIZE = 128

    /**
     * Crypto object requires STRONG biometric methods, and currently Android considers only
     * FingerPrint auth is STRONG enough. Therefore, providing a crypto object while calling
     * [androidx.biometric.BiometricPrompt.authenticate] will block Face and Iris Scan methods
     */
    private val cryptoObject by lazy {
        getDummyCryptoObject()
    }

    @SuppressLint("NewApi")
    private fun getDummyCryptoObject(): BiometricPrompt.CryptoObject {
        dropKeystore()
        val transformation = "$ENCRYPTION_ALGORITHM/$ENCRYPTION_BLOCK_MODE/$ENCRYPTION_PADDING"
        val cipher = Cipher.getInstance(transformation)
        var secKey = getOrCreateSecretKey(false)
        try {
            cipher.init(Cipher.ENCRYPT_MODE, secKey)
        } catch (e: KeyPermanentlyInvalidatedException) {
            e.printStackTrace()
            secKey = getOrCreateSecretKey(true)
            cipher.init(Cipher.ENCRYPT_MODE, secKey)
        } catch (e: Exception) {
            e(e, "BiometricPromptApi28Impl")
        }
        return BiometricPrompt.CryptoObject(cipher)
    }

    private fun getOrCreateSecretKey(mustCreateNew: Boolean): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        if (!mustCreateNew) {
            keyStore.getKey("dummyKey", null)?.let { return it as SecretKey }
        }

        val paramsBuilder = KeyGenParameterSpec.Builder(
            "dummyKey",
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
        paramsBuilder.apply {
            setBlockModes(ENCRYPTION_BLOCK_MODE)
            setEncryptionPaddings(ENCRYPTION_PADDING)
            setKeySize(KEY_SIZE)
            setUserAuthenticationRequired(true)
        }

        val keyGenParams = paramsBuilder.build()
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )
        keyGenerator.init(keyGenParams)
        return keyGenerator.generateKey()
    }

    private fun dropKeystore() {
        try {
            val name = "dummyKey"
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            if (keyStore.containsAlias(name))
                keyStore.deleteEntry(name)
        } catch (throwable: Throwable) {
        }
    }
    //endregion
}