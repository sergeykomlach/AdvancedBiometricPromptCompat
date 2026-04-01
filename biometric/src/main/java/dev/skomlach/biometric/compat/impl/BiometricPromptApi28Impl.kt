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
import android.content.Context
import android.os.Build
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.widget.Toast
import androidx.annotation.ColorInt
import androidx.biometric.BiometricFragment
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricPrompt.PromptInfo
import androidx.biometric.CancellationHelper
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentManager
import dev.skomlach.biometric.compat.AuthenticationFailureReason
import dev.skomlach.biometric.compat.AuthenticationResult
import dev.skomlach.biometric.compat.BiometricAuthRequest
import dev.skomlach.biometric.compat.BiometricConfirmation
import dev.skomlach.biometric.compat.BiometricCryptoObject
import dev.skomlach.biometric.compat.BiometricCryptographyPurpose
import dev.skomlach.biometric.compat.BiometricManagerCompat
import dev.skomlach.biometric.compat.BiometricPromptCompat
import dev.skomlach.biometric.compat.BiometricProviderType
import dev.skomlach.biometric.compat.BiometricType
import dev.skomlach.biometric.compat.BundleBuilder
import dev.skomlach.biometric.compat.R
import dev.skomlach.biometric.compat.crypto.BiometricCryptoException
import dev.skomlach.biometric.compat.crypto.BiometricCryptoObjectHelper
import dev.skomlach.biometric.compat.engine.LegacyBiometric
import dev.skomlach.biometric.compat.engine.LegacyBiometricAuthenticationListener
import dev.skomlach.biometric.compat.engine.core.RestartPredicatesImpl.defaultPredicate
import dev.skomlach.biometric.compat.impl.dialogs.BiometricPromptCompatDialogImpl
import dev.skomlach.biometric.compat.utils.BiometricErrorLockoutPermanentFix
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
import dev.skomlach.common.translate.LocalizationHelper
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import android.text.Spanned


fun CharSequence.toColoredText(context: Context, colorRes: Int): CharSequence {
    return SpannableString(this).apply {
        setSpan(
            ForegroundColorSpan(ContextCompat.getColor(context, colorRes)),
            0,
            length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }
}
@TargetApi(Build.VERSION_CODES.P)

class BiometricPromptApi28Impl(override val builder: BiometricPromptCompat.Builder) :
    IBiometricPromptImpl, AuthCallback {
    private val isOpened = AtomicBoolean(false)
    private val authCallTimestamp = AtomicLong(0)
    private val canceled = HashSet<AuthenticationResult>()
    private val isDeviceCredentialAllowed: Boolean
        get() {
            return if (isAtLeastR) {
                if (builder.forceDeviceCredential()) {
                    true
                } else {
                    return builder.getCryptographyPurpose() == null
                }
            } else {
                builder.forceDeviceCredential()
            }
        }
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
                (builder.getNegativeButtonText() ?: (builder.getActivity() ?: builder.getContext())
                    .getString(android.R.string.cancel)).let {
                    if (isAtLeastR) promptInfoBuilder.setNegativeButtonText(it) else promptInfoBuilder.setNegativeButtonText(
                        getFixedString(
                            it, color = buttonTextColor
                        )
                    )
                }
            }


            if (isAtLeastR)
                promptInfoBuilder.setAllowedAuthenticators(
                    if (builder.forceDeviceCredential())
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
                    else
                        if (builder.getCryptographyPurpose() != null)
                            BiometricManager.Authenticators.BIOMETRIC_STRONG
                        else
                            (BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.BIOMETRIC_STRONG)
                )
            else
                promptInfoBuilder.setDeviceCredentialAllowed(builder.forceDeviceCredential())

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

    @SuppressLint("RestrictedApi")
    private var biometricFragment: AtomicReference<BiometricFragment?> =
        AtomicReference<BiometricFragment?>(null)
    private val fmAuthCallback: LegacyBiometricAuthenticationListener =
        LegacyBiometricAuthenticationCallbackImpl()

    private val authCallback: BiometricPrompt.AuthenticationCallback =
        object : BiometricPrompt.AuthenticationCallback() {
            private var errorTs = 0L
            private val skipTimeout =
                builder.getContext().resources.getInteger(android.R.integer.config_shortAnimTime)

            override fun onAuthenticationFailed() {
                d("BiometricPromptApi28Impl.onAuthenticationFailed")
                if (callback != null) {
                    dialog?.onFailure(false)
                    for (module in builder.getPrimaryAvailableTypes()) {
                        IconStateHelper.errorType(module)
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

                        BiometricPrompt.ERROR_HW_UNAVAILABLE, BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL -> {
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

                        BiometricPrompt.ERROR_CANCELED -> {
                            builder.getPrimaryAvailableTypes().forEach {
                                canceled.add(
                                    AuthenticationResult(
                                        it,
                                        reason = AuthenticationFailureReason.CANCELED,
                                        description = errString
                                    )
                                )
                            }
                            cancelAuth()
                            return@Runnable
                        }

                        BiometricPrompt.ERROR_USER_CANCELED, BiometricPrompt.ERROR_NEGATIVE_BUTTON -> {
                            builder.getPrimaryAvailableTypes().forEach {
                                canceled.add(
                                    AuthenticationResult(
                                        it,
                                        reason = AuthenticationFailureReason.CANCELED_BY_USER,
                                        description = errString
                                    )
                                )
                            }
                            cancelAuth()

                            return@Runnable
                        }

                        else -> {
                            callback?.onFailed(
                                setOf(
                                    AuthenticationResult(
                                        BiometricType.BIOMETRIC_ANY,
                                        reason = failureReason,
                                        description = errString.ifEmpty {
                                            "$errorCode-$errString"
                                        }
                                    )
                                )
                            )
                            postCancelTask {
                                builder.getPrimaryAvailableTypes().forEach {
                                    canceled.add(
                                        AuthenticationResult(
                                            it,
                                            reason = AuthenticationFailureReason.CANCELED,
                                            description = null
                                        )
                                    )
                                }
                                cancelAuth()

                            }
                            return@Runnable
                        }
                    }
                    if (restartPredicate.invoke(failureReason)) {
                        if (callback != null) {
                            dialog?.onFailure(
                                failureReason == AuthenticationFailureReason.LOCKED_OUT
                            )
                            for (module in builder.getPrimaryAvailableTypes()) {
                                IconStateHelper.errorType(module)
                            }
                        }
                    } else {
                        checkAuthResult(
                            AuthResult.AuthResultState.FATAL_ERROR,
                            AuthenticationResult(
                                BiometricType.BIOMETRIC_ANY,
                                reason = failureReason,
                                description = errString.ifEmpty {
                                    "$errorCode-$errString"
                                }
                            )
                        )
                    }
                })
            }

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                d("BiometricPromptApi28Impl.onAuthenticationSucceeded: ${result.authenticationType}; Crypto=${result.cryptoObject}")
                val tmp = System.currentTimeMillis()
                if (tmp - errorTs <= skipTimeout || tmp - authCallTimestamp.get() <= skipTimeout)
                    return
                errorTs = tmp
                checkAuthResult(
                    AuthResult.AuthResultState.SUCCESS,
                    AuthenticationResult(
                        BiometricType.BIOMETRIC_ANY,
                        cryptoObject = BiometricCryptoObject(
                            result.cryptoObject?.signature,
                            result.cryptoObject?.cipher,
                            result.cryptoObject?.mac
                        )
                    )
                )
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
        if (builder.enroll) {
            val skipHardwareList = builder.getPrimaryAvailableTypes().filter {
                BiometricManagerCompat.isHardwareDetected(
                    BiometricAuthRequest.default().withType(it).withProvider(
                        BiometricProviderType.HARDWARE
                    )
                )
            }
            val filtered = builder.getPrimaryAvailableTypes().toMutableList()
            filtered.removeAll(skipHardwareList)
            isFingerprint.set(filtered.contains(BiometricType.BIOMETRIC_FINGERPRINT))
        } else isFingerprint.set(
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

    override fun startAuth() {
        d("BiometricPromptApi28Impl.startAuth():")
        val prompt = biometricPrompt
        if (prompt == null) {
            callback?.onCanceled(builder.getAllAvailableTypes().map {
                AuthenticationResult(
                    it,
                    reason = AuthenticationFailureReason.INTERNAL_ERROR,
                    description = LocalizationHelper.getLocalizedString(
                        builder.getContext(),
                        R.string.biometriccompat_camera_blocked
                    )
                )
            }.toSet())
            return
        }
        val secondary = ArrayList<BiometricType>(builder.getSecondaryAvailableTypes())
        onUiOpened()
        showSystemUi(prompt)
        if (secondary.isNotEmpty()) {
            ExecutorHelper.post {
                LegacyBiometric.authenticate(
                    builder.getCryptographyPurpose(),
                    null,
                    secondary,
                    fmAuthCallback,
                    BundleBuilder.create(builder)
                )
            }
        }

    }

    @SuppressLint("RestrictedApi")
    private fun showSystemUi(biometricPrompt: BiometricPrompt) {
        try {
            d("BiometricPromptApi28Impl.showSystemUi()")
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
                    biometricCryptoObject.cipher.let { BiometricPrompt.CryptoObject(it) }
                else if (biometricCryptoObject?.mac != null)
                    biometricCryptoObject.mac.let { BiometricPrompt.CryptoObject(it) }
                else biometricCryptoObject?.signature?.let { BiometricPrompt.CryptoObject(it) }

            d("BiometricPromptApi28Impl.authenticate:  Crypto=$crpObject")
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
            checkAuthResult(
                AuthResult.AuthResultState.FATAL_ERROR,
                AuthenticationResult(
                    BiometricType.BIOMETRIC_ANY,
                    reason = AuthenticationFailureReason.CRYPTO_ERROR,
                    description = e.message
                )
            )
        }
    }


    override fun stopAuth() {
        e("BiometricPromptApi28Impl.stopAuth():")
        LegacyBiometric.cancelAuthentication()
        biometricFragment.get()?.let {
            CancellationHelper.forceCancel(it)
        } ?: run {
            biometricPrompt?.cancelAuthentication()
        }
        biometricFragment.set(null)
    }

    override fun cancelAuth() {
        try {
            e("BiometricPromptApi28Impl.cancelAuth()")
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
        d("BiometricPromptApi28Impl.onUIClosed():")
        callback?.onUIClosed()
        isOpened.set(false)
    }

    private fun checkAuthResult(
        authResult: AuthResult.AuthResultState,
        module: AuthenticationResult?
    ) {
        if (!isOpened.get())
            return
        d("BiometricPromptApi28Impl.checkAuthResult(): stage 1")
        var failureReason = module?.reason
        if (mutableListOf(
                AuthenticationFailureReason.SENSOR_FAILED,
                AuthenticationFailureReason.AUTHENTICATION_FAILED
            ).contains(failureReason)
        ) {
            HardwareAccessImpl.getInstance(builder.getBiometricAuthRequest()).lockout()
            failureReason = AuthenticationFailureReason.LOCKED_OUT
        }
        var added = false
        if (isDeviceCredentialAllowed) {
            for (m in builder.getPrimaryAvailableTypes()) {
                authFinished[m] =
                    AuthResult(
                        authResult,
                        AuthenticationResult(
                            BiometricType.BIOMETRIC_ANY,
                            module?.cryptoObject,
                            module?.reason,
                            module?.description
                        )
                    )
            }
            added = true
        } else
            for (m in builder.getPrimaryAvailableTypes()) {
                authFinished[m] =
                    AuthResult(
                        authResult,
                        AuthenticationResult(
                            m,
                            module?.cryptoObject,
                            module?.reason,
                            module?.description
                        )
                    )
                added = true

                BiometricNotificationManager.dismiss(m)
                if (AuthResult.AuthResultState.SUCCESS == authResult) {
                    IconStateHelper.successType(m)
                } else
                    IconStateHelper.errorType(m)


            }
        dialog?.authFinishedCopy = authFinished
        d("BiometricPromptApi28Impl.checkAuthResult(): stage 2")
        if (added && builder.getBiometricAuthRequest().confirmation == BiometricConfirmation.ALL && AuthResult.AuthResultState.SUCCESS == authResult) {
            Vibro.start()
        }

        val authFinishedList: List<BiometricType?> = java.util.ArrayList(authFinished.keys)
        val allList: MutableList<BiometricType?> = java.util.ArrayList(
            builder.getAllAvailableTypes()
        )
        allList.removeAll(authFinishedList)
        d("BiometricPromptApi28Impl.checkAuthResult.authFinished >> ${builder.getBiometricAuthRequest()}: $allList; ($authFinished / ${builder.getAllAvailableTypes()})")
        val error =
            authFinished.values.firstOrNull { it.authResultState == AuthResult.AuthResultState.FATAL_ERROR }
        val success =
            authFinished.values.firstOrNull { it.authResultState == AuthResult.AuthResultState.SUCCESS }
        d("BiometricPromptApi28Impl.checkAuthResult.authFinished << ${builder.getBiometricAuthRequest()}: $error/$success")
        if (((success != null || error != null || allList.isEmpty()) && builder.getBiometricAuthRequest().confirmation == BiometricConfirmation.ANY) ||
            (builder.getBiometricAuthRequest().confirmation == BiometricConfirmation.ALL && allList.isEmpty())
        ) {
            if (success != null) {
                val onlySuccess = authFinished.filter {
                    it.value.authResultState == AuthResult.AuthResultState.SUCCESS
                }
                val fixCryptoObjects = builder.getCryptographyPurpose()?.purpose == null
                d("BiometricPromptApi28Impl.checkAuthResult() -> onSucceeded")

                if (isDeviceCredentialAllowed) {
                    BiometricErrorLockoutPermanentFix.resetBiometricSensorPermanentlyLocked()
                }
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
            } else if (error != null) {
                e("BiometricPromptApi28Impl.checkAuthResult() -> onFailed ${authFinished.values.filter { it.authResultState == AuthResult.AuthResultState.FATAL_ERROR }}")
                callback?.onFailed(authFinished.values.filter { it.authResultState == AuthResult.AuthResultState.FATAL_ERROR }
                    .mapNotNull {
                        it.result
                    }.toSet())
                cancelAuthentication()
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

    private inner class LegacyBiometricAuthenticationCallbackImpl :
        LegacyBiometricAuthenticationListener {

        override fun onSuccess(result: AuthenticationResult) {
            IconStateHelper.successType(result.type)
            checkAuthResult(AuthResult.AuthResultState.SUCCESS, result)
        }

        override fun onHelp(msg: CharSequence?) {
            if (!msg.isNullOrEmpty()) {
                dialog?.onHelp(msg)
            }
        }

        override fun onFailure(
            result: AuthenticationResult
        ) {
            val isLockedOut = result.reason == AuthenticationFailureReason.LOCKED_OUT
            if (isLockedOut) {
                checkAuthResult(AuthResult.AuthResultState.FATAL_ERROR, result)
            } else {
                IconStateHelper.errorType(result.type)
                dialog?.onFailure(false) ?: run {
                    val msg = result.description
                    if (!msg.isNullOrEmpty()) {
                        builder.getActivity()?.let {
                            Toast.makeText(it, msg.toColoredText(it, R.color.material_red_500), Toast.LENGTH_LONG).show()
                        }
                    }
                    Vibro.start()
                }
            }
        }

        override fun onCanceled(result: AuthenticationResult) {
            canceled.add(result)
        }
    }
}