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
import android.text.Spanned
import android.text.style.ForegroundColorSpan
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
import dev.skomlach.biometric.compat.BiometricConfirmation
import dev.skomlach.biometric.compat.BiometricCryptoObject
import dev.skomlach.biometric.compat.BiometricCryptographyPurpose
import dev.skomlach.biometric.compat.BiometricPromptCompat
import dev.skomlach.biometric.compat.BiometricType
import dev.skomlach.biometric.compat.BundleBuilder
import dev.skomlach.biometric.compat.CryptoSecurityLevel
import dev.skomlach.biometric.compat.custom.SoftwareBiometricPromptRegistry
import dev.skomlach.biometric.compat.custom.supportsBackgroundPreparation
import dev.skomlach.biometric.compat.custom.SoftwareBiometricPromptDelegate
import dev.skomlach.biometric.compat.custom.SoftwareBiometricPromptHost
import dev.skomlach.biometric.compat.custom.SoftwarePromptStatus
import dev.skomlach.biometric.compat.planApi28StartAuthStage
import dev.skomlach.biometric.compat.R
import dev.skomlach.biometric.compat.biometricActivityDestroyedDescription
import dev.skomlach.biometric.compat.biometricErrorWithCodeDescription
import dev.skomlach.biometric.compat.biometricInternalErrorDescription
import dev.skomlach.biometric.compat.normalizeBiometricErrorDescription
import dev.skomlach.biometric.compat.shouldShowInitialCompatDialog
import dev.skomlach.biometric.compat.shouldShowPostSystemCompatDialog
import dev.skomlach.biometric.compat.biometricRequiredCryptoMissingDescription
import dev.skomlach.biometric.compat.biometricRequiredCryptoRejectedDescription
import dev.skomlach.biometric.compat.biometricStartAuthenticationDescription
import dev.skomlach.biometric.compat.crypto.AppFlowCryptoRegistry
import dev.skomlach.biometric.compat.crypto.BiometricCryptoException
import dev.skomlach.biometric.compat.crypto.BiometricCryptoObjectHelper
import dev.skomlach.biometric.compat.crypto.CryptoAccessType
import dev.skomlach.biometric.compat.engine.LegacyBiometric
import dev.skomlach.biometric.compat.engine.LegacyBiometricAuthenticationListener
import dev.skomlach.biometric.compat.engine.core.RestartPredicatesImpl.defaultPredicate
import dev.skomlach.biometric.compat.engine.internal.SoftwareBiometricModule
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
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference


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
    companion object {
        private const val PROMPT_CRYPTO_KEY = "BiometricPromptCompat"
        private val biometricFragmentMethod: Method? by lazy {
            runCatching {
                BiometricPrompt::class.java.declaredMethods.first {
                    it.parameterTypes.size == 1 &&
                            it.parameterTypes[0] == FragmentManager::class.java &&
                            it.returnType == BiometricFragment::class.java
                }.apply {
                    isAccessible = true
                }
            }.getOrNull()
        }
    }

    @Volatile private var legacySessionOwner = Any()
    private val authSessionState = AuthSessionState<AuthenticationResult>()
    @Volatile
    private var authSessionToken = -1L
    private val callbackDispatchSessionToken = AtomicLong(-1L)
    private val authErrorTimestamp = AtomicLong(0L)
    private val isOpened = AtomicBoolean(false)
    private val systemPromptStarted = AtomicBoolean(false)
    private var hardwareConfirmation: AuthResult.AuthResultState? = null
    private var availableTypesAtStart: Set<BiometricType> = emptySet()
    private var softwareEnrollTargetsAtStart: Set<BiometricType> = emptySet()
    private val authCallTimestamp = AtomicLong(0)
    private val pendingPromptCryptoObject = AtomicReference<BiometricCryptoObject?>(null)
    private val isDeviceCredentialAllowed: Boolean
        get() {
            return if (isAtLeastR) {
                if (builder.forceDeviceCredential()) {
                    true
                } else {
                    builder.getCryptographyPurpose() == null
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
    private var biometricPrompt: BiometricPrompt? = null
    private var restartPredicate = defaultPredicate()
    private var dialog: BiometricPromptCompatDialogImpl? = null
    private var callback: BiometricPromptCompat.AuthenticationCallback? = null
    @Volatile private var parallelCapture: ParallelSoftwareCapture? = null
    private val parallelDelegates = java.util.concurrent.CopyOnWriteArrayList<SoftwareBiometricPromptDelegate>()
    private var feedbackToast: android.widget.Toast? = null
    private val feedbackThrottle = SoftwareFeedbackThrottle()
    private var feedbackMessage: String? = null
    private var pendingFeedback: Runnable? = null
    private val authFinished: MutableMap<BiometricType?, AuthResult> =
        HashMap<BiometricType?, AuthResult>()

    @SuppressLint("RestrictedApi")
    private var biometricFragment: AtomicReference<BiometricFragment?> =
        AtomicReference<BiometricFragment?>(null)
    private val fmAuthCallback: LegacyBiometricAuthenticationListener =
        LegacyBiometricAuthenticationCallbackImpl()
    private var sessionLegacyAuthCallback: LegacyBiometricAuthenticationListener? = null

    private val authCallback: BiometricPrompt.AuthenticationCallback =
        object : BiometricPrompt.AuthenticationCallback() {
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
                val eventSessionToken = callbackDispatchSessionToken.get()
                if (!authSessionState.owns(eventSessionToken)) return
                d("BiometricPromptApi28Impl.onAuthenticationError: $errorCode $errString")
                val tmp = System.currentTimeMillis()
                if (tmp - authErrorTimestamp.get() <= skipTimeout)
                    return
                authErrorTimestamp.set(tmp)
                //...present normal failed screen...

                ExecutorHelper.post(Runnable {
                    if (!authSessionState.owns(eventSessionToken)) return@Runnable
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
                                AuthenticationFailureReason.AUTHENTICATION_FAILED
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
                                authSessionState.add(
                                    eventSessionToken,
                                    AuthenticationResult(
                                        it,
                                        reason = AuthenticationFailureReason.CANCELED,
                                        description = normalizeBiometricErrorDescription(errString)
                                    )
                                )
                            }
                            cancelAuth()
                            return@Runnable
                        }

                        BiometricPrompt.ERROR_USER_CANCELED, BiometricPrompt.ERROR_NEGATIVE_BUTTON -> {
                            builder.getPrimaryAvailableTypes().forEach {
                                authSessionState.add(
                                    eventSessionToken,
                                    AuthenticationResult(
                                        it,
                                        reason = AuthenticationFailureReason.CANCELED_BY_USER,
                                        description = normalizeBiometricErrorDescription(errString)
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
                                            biometricErrorWithCodeDescription(errorCode)
                                        }
                                    )
                                )
                            )
                            cancelAuthentication()
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
                                    biometricErrorWithCodeDescription(errorCode)
                                }
                            )
                        )
                    }
                })
            }

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val eventSessionToken = callbackDispatchSessionToken.get()
                if (!authSessionState.owns(eventSessionToken)) return
                d("BiometricPromptApi28Impl.onAuthenticationSucceeded: ${result.authenticationType}; Crypto=${result.cryptoObject}")
                val tmp = System.currentTimeMillis()
                authErrorTimestamp.set(tmp)
                val resultCryptoObject = BiometricCryptoObject(
                    result.cryptoObject?.signature,
                    result.cryptoObject?.cipher,
                    result.cryptoObject?.mac
                )
                checkAuthResult(
                    AuthResult.AuthResultState.SUCCESS,
                    AuthenticationResult(
                        BiometricType.BIOMETRIC_ANY,
                        cryptoObject = if (
                            resultCryptoObject.signature != null ||
                            resultCryptoObject.cipher != null ||
                            resultCryptoObject.mac != null
                        ) {
                            resultCryptoObject
                        } else {
                            pendingPromptCryptoObject.getAndSet(null)
                        }
                    )
                )
            }
        }

    private fun guardedSystemAuthCallback(
        expectedSessionToken: Long
    ): BiometricPrompt.AuthenticationCallback {
        return object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationFailed() {
                if (!authSessionState.owns(expectedSessionToken)) return
                callbackDispatchSessionToken.set(expectedSessionToken)
                authCallback.onAuthenticationFailed()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (!authSessionState.owns(expectedSessionToken)) return
                callbackDispatchSessionToken.set(expectedSessionToken)
                authCallback.onAuthenticationError(errorCode, errString)
            }

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                if (!authSessionState.owns(expectedSessionToken)) return
                callbackDispatchSessionToken.set(expectedSessionToken)
                authCallback.onAuthenticationSucceeded(result)
            }
        }
    }

    private fun guardedLegacyAuthCallback(
        expectedSessionToken: Long
    ): LegacyBiometricAuthenticationListener {
        return object : LegacyBiometricAuthenticationListener {
            override fun onSuccess(result: AuthenticationResult) {
                if (authSessionState.owns(expectedSessionToken)) fmAuthCallback.onSuccess(result)
            }

            override fun onHelp(msg: CharSequence?) {
                if (authSessionState.owns(expectedSessionToken)) fmAuthCallback.onHelp(msg)
            }

            override fun onFailure(result: AuthenticationResult) {
                if (authSessionState.owns(expectedSessionToken)) fmAuthCallback.onFailure(result)
            }

            override fun onCanceled(result: AuthenticationResult) {
                if (authSessionState.owns(expectedSessionToken)) fmAuthCallback.onCanceled(result)
            }
        }
    }

    override fun onPreAuthFailure(result: AuthenticationResult) {
        callback?.onFailed(setOf(result))
        cancelAuthentication()
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
        legacySessionOwner = Any()
        authSessionToken = authSessionState.begin()
        feedbackThrottle.reset()
        callbackDispatchSessionToken.set(-1L)
        authErrorTimestamp.set(0L)
        pendingPromptCryptoObject.set(null)
        sessionLegacyAuthCallback = guardedLegacyAuthCallback(authSessionToken)
        this.restartPredicate = defaultPredicate()
        this.authFinished.clear()
        this.biometricFragment.set(null)
        this.systemPromptStarted.set(false)
        this.hardwareConfirmation = null
        availableTypesAtStart = builder.getAllAvailableTypes().toSet()
        softwareEnrollTargetsAtStart = if (builder.hasSoftwareEnrollTargets()) builder.getPendingEnrollTypes().toSet() else emptySet()
        callback = cbk
        if (requiresSensorSpecificRoute(
            builder.getBiometricAuthRequest().type,
            builder.getPrimaryAvailableTypes().any { builder.selectedRoute(it)?.usesBiometricPromptHardware == true },
            builder.forceDeviceCredential()
        )) {
            callback?.onFailed(setOf(AuthenticationResult(
                builder.getBiometricAuthRequest().type,
                reason = AuthenticationFailureReason.UNSUPPORTED_AUTHENTICATION_TYPE,
                description = builder.getContext().getString(R.string.biometriccompat_modality_unverifiable)
            )))
            return
        }
        if (softwareEnrollTargetsAtStart.isEmpty() && !canConfirmSystemModalities(builder.getBiometricAuthRequest().confirmation, builder.getPrimaryAvailableTypes())) {
            callback?.onFailed(builder.getPrimaryAvailableTypes().map {
                AuthenticationResult(it, reason = AuthenticationFailureReason.INTERNAL_ERROR,
                    description = biometricStartAuthenticationDescription())
            }.toSet())
            return
        }
        biometricPrompt = builder.getActivity()?.let { activity ->
            BiometricPrompt(
                activity,
                ExecutorHelper.executor,
                guardedSystemAuthCallback(authSessionToken)
            )
        }
        // Only a known OEM bug or a disabled provider proves missing UI. Unreadable XML does not.
        val confirmedMissingSystemUi = DevicesWithKnownBugs.isMissedBiometricUI
        val hasSelectedSystemPromptRoute = builder.getPrimaryAvailableTypes().any { type ->
            builder.selectedRoute(type)?.usesBiometricPromptHardware == true
        }
        if (shouldShowInitialCompatDialog(
                explicitSystemUiBug = confirmedMissingSystemUi,
                heuristicReportsMissingUi = false,
                hasSelectedSystemPromptRoute = hasSelectedSystemPromptRoute
            )
        ) {
            //1) LG G8 do not have BiometricPrompt UI
            //2) One Plus 6T with InScreen fingerprint sensor
            dialog = BiometricPromptCompatDialogImpl(
                builder,
                this@BiometricPromptApi28Impl,
                shouldUseUnderDisplayFingerprintLayout(builder.getPrimaryAvailableTypes())
            )
            dialog?.showDialog()
        } else {
            startAuth()
        }

    }

    override fun cancelAuthentication() {
        legacySessionOwner = Any()
        authSessionState.invalidate()
        authSessionToken = -1L
        callbackDispatchSessionToken.set(-1L)
        sessionLegacyAuthCallback = null
        try {
            onUiClosed()
        } finally {
            callback = null
            val closingDialog = dialog
            dialog = null
            e("BiometricPromptApi28Impl.cancelAuthentication():")
            try {
                stopAuth()
            } finally {
                closingDialog?.dismissDialog()
            }
        }
    }

    override fun startAuth() {
        val remainingPrimaryTypes = remainingPrimaryTypes()
        val remainingSecondaryTypes = remainingSecondaryTypes()
        val stagePlan = planApi28StartAuthStage(
            remainingPrimaryTypes = remainingPrimaryTypes,
            remainingSecondaryTypes = remainingSecondaryTypes,
            routeForType = builder::selectedRoute,
            requiresReadyExtrasBeforeAuthentication = { type ->
                softwareEnrollTargetsAtStart.isNotEmpty() || requiresReadyExtrasBeforeAuthentication(type)
            },
            canPrepareInBackground = { type ->
                dialog == null && SoftwareBiometricPromptRegistry.resolve(type)
                    ?.supportsBackgroundPreparation(builder.enroll) == true
            }
        )
        d(
            "BiometricPromptApi28Impl.startAuth(): primary=$remainingPrimaryTypes " +
                    "secondary=$remainingSecondaryTypes plan=$stagePlan"
        )
        val prompt = if (stagePlan.shouldShowSystemPrompt) {
            biometricPrompt ?: run {
                callback?.onFailed(builder.getAllAvailableTypes().map {
                    AuthenticationResult(
                        it,
                        reason = AuthenticationFailureReason.INTERNAL_ERROR,
                        description = biometricStartAuthenticationDescription()
                    )
                }.toSet())
                return
            }
        } else {
            null
        }
        onUiOpened()
        prompt?.let(::showSystemUi)
        if (stagePlan.backgroundPreparationTypes.isNotEmpty()) {
            startParallelCapture(stagePlan.backgroundPreparationTypes)
        }
        startLegacyAuth(stagePlan.legacyAuthTypes)
    }

    private fun startLegacyAuth(types: List<BiometricType>, extras: android.os.Bundle? = null) {
        if (types.isNotEmpty()) {
            val sessionToken = authSessionToken
            val legacyCallback = sessionLegacyAuthCallback ?: return
            val owner = legacySessionOwner
            val bundle = BundleBuilder.create(builder).apply {
                extras?.let { putAll(it) }
                putBoolean(BundleBuilder.ENROLL, builder.enroll)
            }
            ExecutorHelper.postDelayed({
                if (!authSessionState.owns(sessionToken) || legacySessionOwner !== owner) return@postDelayed
                if (builder.enroll) {
                    types.filter { it in parallelCapture?.types.orEmpty() }
                        .forEach(builder::trackParallelEnrollment)
                }
                LegacyBiometric.authenticateInSession(
                    owner,
                    builder.getCryptographyPurpose(),
                    dialog?.authPreview,
                    types,
                    legacyCallback,
                    bundle,
                    builder.getBiometricAuthRequest().provider,
                    builder.getDisabledModuleTags(),
                    builder.isCryptoFallbackAllowed()
                )
            }, 0)
        }

    }

    private fun startParallelCapture(types: List<BiometricType>) {
        if (parallelCapture != null || !authSessionState.owns(authSessionToken)) return
        d("BiometricPromptApi28Impl.startParallelCapture(): $types")
        val sessionToken = authSessionToken
        val capture = ParallelSoftwareCapture(types.toSet())
        parallelCapture = capture
        fun active() = authSessionState.owns(sessionToken) && parallelCapture === capture
        for (type in types) {
            if (!active()) break
            val delegate = SoftwareBiometricPromptRegistry.resolve(type)?.create(
                SoftwareBiometricPromptHost(
                    context = builder.getContext().applicationContext,
                    builder = builder,
                    enroll = builder.enroll,
                    rootView = null,
                    callbacks = object : SoftwareBiometricPromptHost.Callbacks {
                        override fun isPromptActive() = active()
                        override fun onHelp(message: CharSequence) {
                            ExecutorHelper.post(Runnable {
                                if (!active()) return@Runnable
                                showSoftwareFeedback(message)
                            })
                        }
                        override fun onReady(extras: android.os.Bundle?) {
                            ExecutorHelper.post(Runnable {
                                if (!active()) return@Runnable
                                if (capture.complete(type) && type in remainingSecondaryTypes()) {
                                    startLegacyAuth(listOf(type), extras)
                                }
                            })
                        }
                        override fun onFailure(result: AuthenticationResult) {
                            ExecutorHelper.post(Runnable {
                                if (!active() || !capture.complete(type)) return@Runnable
                                checkAuthResult(AuthResult.AuthResultState.FATAL_ERROR, result, fromSystemPrompt = false)
                            })
                        }
                    }
                )
            )
            if (delegate == null) {
                checkAuthResult(AuthResult.AuthResultState.FATAL_ERROR,
                    AuthenticationResult(type, reason = AuthenticationFailureReason.INTERNAL_ERROR), false)
                capture.complete(type)
            } else {
                parallelDelegates.add(delegate)
                delegate.start()
            }
        }
    }

    private fun showSoftwareFeedback(message: CharSequence) {
        if (message.isBlank()) return
        dialog?.let {
            it.onSoftwareStatus(SoftwarePromptStatus(message))
            return
        }
        val now = android.os.SystemClock.elapsedRealtime()
        if (!feedbackThrottle.tryAcquire(now)) {
            val changed = feedbackMessage != message.toString()
            feedbackMessage = message.toString()
            if (changed) feedbackToast?.setText(message)
            if (pendingFeedback == null) {
                val session = authSessionToken
                pendingFeedback = Runnable {
                    pendingFeedback = null
                    if (authSessionState.owns(session)) feedbackMessage?.let(::showSoftwareFeedback)
                }.also { ExecutorHelper.postDelayed(it, feedbackThrottle.remainingDelay(now)) }
            }
            return
        }
        pendingFeedback?.let(ExecutorHelper::removeCallbacks)
        pendingFeedback = null
        feedbackMessage = message.toString()
        feedbackToast?.cancel()
        feedbackToast = android.widget.Toast.makeText(
            builder.getContext().applicationContext, message, android.widget.Toast.LENGTH_LONG
        ).also { it.show() }
    }

    private fun remainingPrimaryTypes(): Set<BiometricType> {
        return builder.getPrimaryAvailableTypes()
            .filterNotTo(LinkedHashSet()) { type -> authFinished.containsKey(type) }
    }

    private fun remainingSecondaryTypes(): Set<BiometricType> {
        return builder.getSecondaryAvailableTypes()
            .filterTo(LinkedHashSet()) { type ->
                !authFinished.containsKey(type) &&
                        builder.selectedRoute(type)?.usesBiometricPromptHardware == false
            }
    }

    private fun pendingLegacyTypes(): Set<BiometricType> {
        return remainingSecondaryTypes()
    }

    private fun requiresReadyExtrasBeforeAuthentication(type: BiometricType): Boolean {
        return SoftwareBiometricPromptRegistry.resolve(type)
            ?.requiresReadyExtrasBeforeAuthentication == true
    }

    @SuppressLint("RestrictedApi")
    private fun showSystemUi(biometricPrompt: BiometricPrompt) {
        try {
            d("BiometricPromptApi28Impl.showSystemUi() $biometricPrompt")
            var biometricCryptoObject: BiometricCryptoObject? = null
            var isAppFlowCrypto = false
            builder.getCryptographyPurpose()?.let {
                try {
                    biometricCryptoObject = BiometricCryptoObjectHelper.getBiometricCryptoObject(
                        PROMPT_CRYPTO_KEY,
                        builder.getCryptographyPurpose(),
                        true
                    )
                    isAppFlowCrypto =
                        AppFlowCryptoRegistry.getAccessType(PROMPT_CRYPTO_KEY) == CryptoAccessType.APP_FLOW
                } catch (e: BiometricCryptoException) {
                    if (builder.getCryptographyPurpose()?.purpose == BiometricCryptographyPurpose.ENCRYPT &&
                        !e.isNoKeystoreBiometricEnrollment()
                    ) {
                        BiometricCryptoObjectHelper.deleteCrypto(PROMPT_CRYPTO_KEY)
                        biometricCryptoObject =
                            BiometricCryptoObjectHelper.getBiometricCryptoObject(
                                PROMPT_CRYPTO_KEY,
                                builder.getCryptographyPurpose(),
                                true
                            )
                        isAppFlowCrypto =
                            AppFlowCryptoRegistry.getAccessType(PROMPT_CRYPTO_KEY) == CryptoAccessType.APP_FLOW
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
            if (isAppFlowCrypto) {
                d("BiometricPromptApi28Impl.authenticate: app-flow crypto is prepared; authenticate without AndroidX CryptoObject")
                pendingPromptCryptoObject.set(biometricCryptoObject)
                authCallTimestamp.set(System.currentTimeMillis())
                biometricPrompt.authenticate(biometricPromptInfo)
                systemPromptStarted.set(true)
            } else if (crpObject != null) {
                try {
                    pendingPromptCryptoObject.set(null)
                    authCallTimestamp.set(System.currentTimeMillis())
                    biometricPrompt.authenticate(biometricPromptInfo, crpObject)
                    systemPromptStarted.set(true)
                } catch (e: Throwable) {
                    e(
                        e,
                        "BiometricPromptApi28Impl.authenticate with CryptoObject failed"
                    )
                    pendingPromptCryptoObject.set(null)
                    if (builder.getCryptographyPurpose() != null) {
                        if (builder.isCryptoFallbackAllowed()) {
                            val fallbackCryptoObject = BiometricCryptoObjectHelper.getBiometricCryptoObject(
                                PROMPT_CRYPTO_KEY,
                                builder.getCryptographyPurpose(),
                                false
                            )
                            pendingPromptCryptoObject.set(fallbackCryptoObject)
                            authCallTimestamp.set(System.currentTimeMillis())
                            biometricPrompt.authenticate(biometricPromptInfo)
                            systemPromptStarted.set(true)
                            return
                        }
                        checkAuthResult(
                            AuthResult.AuthResultState.FATAL_ERROR,
                            AuthenticationResult(
                                BiometricType.BIOMETRIC_ANY,
                                reason = AuthenticationFailureReason.CRYPTO_ERROR,
                                description = biometricRequiredCryptoRejectedDescription()
                            )
                        )
                        return
                    }
                    authCallTimestamp.set(System.currentTimeMillis())
                    biometricPrompt.authenticate(biometricPromptInfo)
                    systemPromptStarted.set(true)
                }
            } else {
                pendingPromptCryptoObject.set(null)
                authCallTimestamp.set(System.currentTimeMillis())
                biometricPrompt.authenticate(biometricPromptInfo)
                systemPromptStarted.set(true)
            }
            ExecutorHelper.startOnBackground {
                //fallback - sometimes we are not able to cancel BiometricPrompt properly
                try {
                    try {
                        biometricFragment.set(
                            biometricFragmentMethod?.invoke(
                                null,
                                builder.getActivity()?.supportFragmentManager
                            ) as BiometricFragment?
                        )
                    } finally {
                        if (biometricFragment.get() == null) {
                            callback?.onFailed(builder.getAllAvailableTypes().map {
                                AuthenticationResult(
                                    it,
                                    reason = AuthenticationFailureReason.INTERNAL_ERROR,
                                    description = biometricActivityDestroyedDescription()
                                )
                            }.toSet())
                        }
                    }
                } catch (e: Throwable) {
                    e(e)
                }
            }
        } catch (e: BiometricCryptoException) {
            e(e)
            callback?.onFailed(builder.getAllAvailableTypes().map {
                AuthenticationResult(
                    it,
                    reason = AuthenticationFailureReason.INTERNAL_ERROR,
                    description = biometricInternalErrorDescription()
                )
            }.toSet())
        }
    }


    override fun stopAuth() {
        parallelCapture = null
        parallelDelegates.forEach { it.dispose() }
        parallelDelegates.clear()
        feedbackToast?.cancel()
        feedbackToast = null
        feedbackMessage = null
        pendingFeedback?.let(ExecutorHelper::removeCallbacks)
        pendingFeedback = null
        legacySessionOwner = Any()
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
            val canceled = authSessionState.snapshot(authSessionToken)
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
        module: AuthenticationResult?,
        fromSystemPrompt: Boolean = true
    ) {
        if (!isOpened.get())
            return
        d("BiometricPromptApi28Impl.checkAuthResult(): stage 1")
        if (fromSystemPrompt) systemPromptStarted.set(false)
        val normalizedModule = normalizeCryptoResult(module, authResult)
        val normalizedAuthResult = if (normalizedModule?.reason == AuthenticationFailureReason.CRYPTO_ERROR) {
            AuthResult.AuthResultState.FATAL_ERROR
        } else {
            authResult
        }
        if (fromSystemPrompt) hardwareConfirmation = normalizedAuthResult
        var failureReason = normalizedModule?.reason
        if (mutableListOf(
                AuthenticationFailureReason.SENSOR_FAILED,
                AuthenticationFailureReason.AUTHENTICATION_FAILED
            ).contains(failureReason)
        ) {
            HardwareAccessImpl.getInstance(builder.getBiometricAuthRequest()).lockout()
            failureReason = AuthenticationFailureReason.LOCKED_OUT
        }
        var added = false
        val completedTypes = if (fromSystemPrompt) builder.getPrimaryAvailableTypes()
            else setOfNotNull(normalizedModule?.type)
        if (fromSystemPrompt && isDeviceCredentialAllowed) {
            for (m in completedTypes) {
                authFinished[m] =
                    AuthResult(
                        normalizedAuthResult,
                        AuthenticationResult(
                            BiometricType.BIOMETRIC_ANY,
                            normalizedModule?.cryptoObject,
                            normalizedModule?.reason,
                            normalizedModule?.description,
                            normalizedModule?.cryptoSecurityLevel ?: CryptoSecurityLevel.NONE
                        )
                    )
            }
            added = true
        } else
            for (m in completedTypes) {
                authFinished[m] =
                    AuthResult(
                        normalizedAuthResult,
                        AuthenticationResult(
                            if (fromSystemPrompt) BiometricType.BIOMETRIC_ANY else m,
                            normalizedModule?.cryptoObject,
                            normalizedModule?.reason,
                            normalizedModule?.description,
                            normalizedModule?.cryptoSecurityLevel ?: CryptoSecurityLevel.NONE
                        )
                    )
                added = true

                BiometricNotificationManager.dismiss(m)
                if (AuthResult.AuthResultState.SUCCESS == normalizedAuthResult) {
                    IconStateHelper.successType(m)
                } else
                    IconStateHelper.errorType(m)


            }
        dialog?.authFinishedCopy = authFinished
        d("BiometricPromptApi28Impl.checkAuthResult(): stage 2")
        if (added && builder.getBiometricAuthRequest().confirmation == BiometricConfirmation.ALL && AuthResult.AuthResultState.SUCCESS == normalizedAuthResult) {
            Vibro.start()
        }

        val authFinishedList: List<BiometricType?> = java.util.ArrayList(authFinished.keys)
        val allList: MutableList<BiometricType?> = java.util.ArrayList(
            builder.getAllAvailableTypes()
        )
        allList.removeAll(authFinishedList)
        d {
            "BiometricPromptApi28Impl.checkAuthResult.authFinished >> " +
                    "${builder.getBiometricAuthRequest()}: $allList; " +
                    "($authFinished / ${builder.getAllAvailableTypes()})"
        }
        val error =
            authFinished.values.firstOrNull { it.authResultState == AuthResult.AuthResultState.FATAL_ERROR }
        val success =
            authFinished.values.firstOrNull { it.authResultState == AuthResult.AuthResultState.SUCCESS }
        d("BiometricPromptApi28Impl.checkAuthResult.authFinished << ${builder.getBiometricAuthRequest()}: $error/$success")
        // A failed/disabled route remains a requirement of this operation. Recomputing the
        // scope here could turn a failed voice enrollment into hardware-only success.
        val completionTypes = softwareEnrollTargetsAtStart.ifEmpty { availableTypesAtStart }
        if (builder.enroll && !fromSystemPrompt && normalizedAuthResult == AuthResult.AuthResultState.SUCCESS) {
            normalizedModule?.let { builder.markEnrollConfirmedResults(setOf(it)) }
        }
        val completion = resolveApi28Completion(
            builder.getBiometricAuthRequest().confirmation,
            availableTypesAtStart,
            softwareEnrollTargetsAtStart,
            hardwareConfirmation,
            authFinished
        )
        if (completion != AuthenticationCompletion.PENDING) {
            if (completion == AuthenticationCompletion.SUCCEEDED) {
                val onlySuccess = authFinished.filter {
                    completionTypes.contains(it.key) && it.value.authResultState == AuthResult.AuthResultState.SUCCESS
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
                            if (fixCryptoObjects) null else r.cryptoObject,
                            r.reason,
                            r.description,
                            if (fixCryptoObjects) CryptoSecurityLevel.NONE else r.cryptoSecurityLevel
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


        } else if (shouldShowPostSystemCompatDialog(
                systemPromptStarted = systemPromptStarted.get(),
                hasPendingLegacyRoute = pendingLegacyTypes().let { pending ->
                    pending.isNotEmpty() && parallelCapture?.types.orEmpty().none { it in pending }
                }
            )
        ) {
            if (dialog == null) {
                dialog =
                    BiometricPromptCompatDialogImpl(
                        builder, object : AuthCallback {
                            override fun startAuth() {
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

                            override fun onPreAuthFailure(result: AuthenticationResult) {
                                this@BiometricPromptApi28Impl.onPreAuthFailure(result)
                            }
                        },
                        shouldUseUnderDisplayFingerprintLayout(builder.getSecondaryAvailableTypes())
                    )
                dialog?.authFinishedCopy = authFinished
            }
            dialog?.showDialog()
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

    private inner class LegacyBiometricAuthenticationCallbackImpl :
        LegacyBiometricAuthenticationListener {

        override fun onSuccess(result: AuthenticationResult) {
            IconStateHelper.successType(result.type)
            checkAuthResult(AuthResult.AuthResultState.SUCCESS, result, fromSystemPrompt = false)
        }

        override fun onHelp(msg: CharSequence?) {
            if (!msg.isNullOrEmpty()) {
                showSoftwareFeedback(msg)
            }
        }

        override fun onFailure(
            result: AuthenticationResult
        ) {
            if (builder.disableBiometricForPermissionFailure(result)) {
                BiometricNotificationManager.dismiss(result.type)
                checkAuthResult(AuthResult.AuthResultState.FATAL_ERROR, result, fromSystemPrompt = false)
                return
            }
            val isLockedOut = result.reason == AuthenticationFailureReason.LOCKED_OUT
            if (isLockedOut || result.reason !in setOf(AuthenticationFailureReason.SENSOR_FAILED, AuthenticationFailureReason.AUTHENTICATION_FAILED)) {
                checkAuthResult(AuthResult.AuthResultState.FATAL_ERROR, result, fromSystemPrompt = false)
            } else {
                IconStateHelper.errorType(result.type)
                dialog?.onFailure(false)
                // Keep software feedback inside its dialog; the system owns its own error UI.
                result.description?.takeIf { it.isNotEmpty() }?.let {
                    showSoftwareFeedback(it)
                }
                if (dialog == null) Vibro.start()
            }
        }

        override fun onCanceled(result: AuthenticationResult) {
            if (!authSessionState.add(authSessionToken, result)) return
            if (!isOpened.get()) return
            if (builder.getBiometricAuthRequest().confirmation == BiometricConfirmation.ALL) cancelAuth()
            else checkAuthResult(AuthResult.AuthResultState.FATAL_ERROR, result, fromSystemPrompt = false)
        }
    }

    private fun shouldUseUnderDisplayFingerprintLayout(types: Collection<BiometricType>): Boolean {
        if (!types.contains(BiometricType.BIOMETRIC_FINGERPRINT)) return false
        val fingerprintModule = LegacyBiometric.getSelectedBiometricModule(
            BiometricType.BIOMETRIC_FINGERPRINT,
            builder.getBiometricAuthRequest().provider,
            builder.enroll,
            builder.getDisabledModuleTags()
        )
        return fingerprintModule != null &&
                fingerprintModule !is SoftwareBiometricModule &&
                DevicesWithKnownBugs.hasUnderDisplayFingerprint
    }
}
