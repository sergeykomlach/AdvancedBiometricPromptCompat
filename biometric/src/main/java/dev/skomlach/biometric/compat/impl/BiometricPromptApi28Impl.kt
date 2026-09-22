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
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricPrompt.PromptInfo
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import dev.skomlach.biometric.compat.AuthenticationFailureReason
import dev.skomlach.biometric.compat.AuthenticationResult
import dev.skomlach.biometric.compat.BiometricConfirmation
import dev.skomlach.biometric.compat.BiometricCryptoObject
import dev.skomlach.biometric.compat.BiometricCryptographyPurpose
import dev.skomlach.biometric.compat.BiometricPromptCompat
import dev.skomlach.biometric.compat.BiometricProviderType
import dev.skomlach.biometric.compat.custom.AbstractSoftwareBiometricManager
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
import dev.skomlach.biometric.compat.engine.StatusLegacyBiometricAuthenticationListener
import dev.skomlach.biometric.compat.engine.onStatus
import dev.skomlach.biometric.compat.engine.internal.SoftwareBiometricModule
import dev.skomlach.biometric.compat.impl.dialogs.BiometricPromptCompatDialogImpl
import dev.skomlach.biometric.compat.utils.BiometricErrorLockoutPermanentFix
import dev.skomlach.biometric.compat.utils.DevicesWithKnownBugs
import dev.skomlach.biometric.compat.utils.HardwareAccessImpl
import dev.skomlach.biometric.compat.utils.SensorPrivacyCheck
import dev.skomlach.biometric.compat.utils.Vibro
import dev.skomlach.biometric.compat.utils.activityView.IconStateHelper
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.d
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.e
import dev.skomlach.biometric.compat.utils.notification.BiometricNotificationManager
import dev.skomlach.biometric.compat.utils.themes.DarkLightThemes
import dev.skomlach.common.misc.ExecutorHelper
import dev.skomlach.common.misc.Utils
import dev.skomlach.common.permissions.PermissionUtils
import dev.skomlach.common.misc.Utils.isAtLeastR
import dev.skomlach.common.themes.monet.SystemColorScheme
import dev.skomlach.common.themes.monet.toArgb
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
    }

    @Volatile private var legacySessionOwner = Any()
    private val authSessionState = AuthSessionState<AuthenticationResult>()
    @Volatile
    private var authSessionToken = -1L
    private val callbackDispatchSessionToken = AtomicLong(-1L)
    private val isOpened = AtomicBoolean(false)
    private val systemPromptStarted = AtomicBoolean(false)
    private var hardwareConfirmation: AuthResult.AuthResultState? = null
    private var availableTypesAtStart: Set<BiometricType> = emptySet()
    private var nativePrimaryTypes: Set<BiometricType> = emptySet()
    private var deferredPreparation: DeferredSoftwarePreparation? = null
    private var preparationTimeout: Runnable? = null
    private var admittedSoftwareTypes: Set<BiometricType> = emptySet()
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
    private var biometricPromptHost: FragmentActivity? = null
    private var dialog: BiometricPromptCompatDialogImpl? = null
    private var callback: BiometricPromptCompat.AuthenticationCallback? = null
    @Volatile private var parallelCapture: ParallelSoftwareCapture? = null
    private val parallelDelegates = java.util.concurrent.CopyOnWriteArrayList<SoftwareBiometricPromptDelegate>()
    private var foregroundFeedback: dev.skomlach.biometric.compat.utils.activityView.ForegroundFeedbackSession? = null
    private val authFinished: MutableMap<BiometricType?, AuthResult> =
        HashMap<BiometricType?, AuthResult>()

    private var systemPromptCancellation: AndroidXPromptCancellation? = null
    private val fmAuthCallback: LegacyBiometricAuthenticationListener =
        LegacyBiometricAuthenticationCallbackImpl()
    private var sessionLegacyAuthCallback: LegacyBiometricAuthenticationListener? = null

    private val authCallback: BiometricPrompt.AuthenticationCallback =
        object : BiometricPrompt.AuthenticationCallback() {

            override fun onAuthenticationFailed() {
                d("BiometricPromptApi28Impl.onAuthenticationFailed")
                if (callback != null) {
                    dialog?.onFailure(false)
                    for (module in nativePrimaryTypes) {
                        IconStateHelper.errorType(module)
                    }
                }
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                val eventSessionToken = callbackDispatchSessionToken.get()
                if (!authSessionState.owns(eventSessionToken)) return
                d("BiometricPromptApi28Impl.onAuthenticationError: $errorCode $errString")
                //...present normal failed screen...

                ExecutorHelper.post(Runnable {
                    if (!authSessionState.owns(eventSessionToken)) return@Runnable
                    val failureReason = nativePromptFailureReason(errorCode)
                    when (if (errorCode < 1000) errorCode else errorCode % 1000) {
                        BiometricPrompt.ERROR_LOCKOUT_PERMANENT ->
                            nativePrimaryTypes.forEach {
                                BiometricErrorLockoutPermanentFix.setBiometricSensorPermanentlyLocked(it)
                            }
                        BiometricPrompt.ERROR_LOCKOUT ->
                            HardwareAccessImpl.getInstance(builder.getBiometricAuthRequest()).lockout()
                    }
                    if (failureReason == AuthenticationFailureReason.CANCELED ||
                        failureReason == AuthenticationFailureReason.CANCELED_BY_USER) {
                        nativePrimaryTypes.forEach {
                            authSessionState.add(eventSessionToken, AuthenticationResult(
                                it, reason = failureReason,
                                description = normalizeBiometricErrorDescription(errString)))
                        }
                        cancelAuth()
                        return@Runnable
                    }
                    // Native errors are terminal. Retrying only UI leaves a dead prompt pending;
                    // vendor errors must still allow the independent legacy ANY branch to finish.
                    checkAuthResult(
                        AuthResult.AuthResultState.FATAL_ERROR,
                        AuthenticationResult(
                            BiometricType.BIOMETRIC_ANY,
                            reason = failureReason,
                            description = errString.ifEmpty { biometricErrorWithCodeDescription(errorCode) }
                        )
                    )
                })
            }

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val eventSessionToken = callbackDispatchSessionToken.get()
                if (!authSessionState.owns(eventSessionToken)) return
                d("BiometricPromptApi28Impl.onAuthenticationSucceeded: ${result.authenticationType}; Crypto=${result.cryptoObject}")
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
        return object : StatusLegacyBiometricAuthenticationListener {
            override fun onSuccess(result: AuthenticationResult) {
                if (authSessionState.owns(expectedSessionToken)) fmAuthCallback.onSuccess(result)
            }

            override fun onHelp(msg: CharSequence?) {
                ExecutorHelper.post {
                    if (authSessionState.owns(expectedSessionToken)) fmAuthCallback.onHelp(msg)
                }
            }

            override fun onStatus(source: BiometricType?, status: SoftwarePromptStatus) {
                ExecutorHelper.post {
                    if (authSessionState.owns(expectedSessionToken)) fmAuthCallback.onStatus(source, status)
                }
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
        val previousCancellation = systemPromptCancellation
        systemPromptCancellation = null
        legacySessionOwner = Any()
        authSessionToken = authSessionState.begin()
        previousCancellation?.cancel()
        foregroundFeedback = builder.foregroundFeedback
        callbackDispatchSessionToken.set(-1L)
        pendingPromptCryptoObject.set(null)
        sessionLegacyAuthCallback = guardedLegacyAuthCallback(authSessionToken)
        this.authFinished.clear()
        updateSystemPromptStarted(false)
        this.hardwareConfirmation = null
        nativePrimaryTypes = builder.getPrimaryAvailableTypes().toSet()
        admittedSoftwareTypes = emptySet()
        deferredPreparation?.cancel()
        val sessionToken = authSessionToken
        deferredPreparation = if (builder.deferSoftwarePreparation) DeferredSoftwarePreparation(
            { authSessionState.owns(sessionToken) },
            { preparationTimeout?.let { ExecutorHelper.removeCallbacks(it) }; preparationTimeout = null }
        ) else null
        // Withheld software is not a requirement until its one preparation batch is admitted.
        availableTypesAtStart = builder.getAllAvailableTypes().filterTo(LinkedHashSet()) {
            deferredPreparation == null || builder.selectedRoute(it)?.provider != BiometricProviderType.SOFTWARE
        }
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
        val activity = builder.getActivity()
        biometricPromptHost = activity
        biometricPrompt = activity?.let {
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
        deferredPreparation?.cancel()
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
                biometricPrompt = null
                biometricPromptHost = null
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
                dialog == null && SoftwareBiometricPromptRegistry.resolve(
                    type,
                    builder.getContext()
                )
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
        val deferred = deferredPreparation
        if (deferred?.isPending == true) {
            deferred.start(
                startNative = { prompt?.let(::showSystemUi) },
                scheduleTimeout = { expire ->
                    Runnable { expire() }.also {
                        preparationTimeout = it
                        ExecutorHelper.postDelayed(it, 5_000L)
                    }
                },
                prepare = { ready -> prepareDeferredSoftware(deferred, ready) },
                onFinished = { ready ->
                    if (!ready) admittedSoftwareTypes = emptySet()
                    applyDeferredSoftwareAdmission(
                        builder.getAllAvailableTypes(), nativePrimaryTypes, admittedSoftwareTypes,
                        { builder.selectedRoute(it)?.provider == BiometricProviderType.SOFTWARE },
                        builder::disableBiometricType
                    )
                    availableTypesAtStart = availableTypesAtStart + admittedSoftwareTypes
                    // No new native authenticate: join the secondary branch only.
                    if (systemPromptStarted.get()) startPreparedSoftware()
                    checkAuthResult(AuthResult.AuthResultState.FATAL_ERROR, null, fromSystemPrompt = false)
                }
            )
        } else if (!systemPromptStarted.get()) {
            prompt?.let(::showSystemUi)
        }
        if (stagePlan.backgroundPreparationTypes.isNotEmpty()) {
            startParallelCapture(stagePlan.backgroundPreparationTypes)
        }
        startLegacyAuth(stagePlan.legacyAuthTypes)
    }

    private fun prepareDeferredSoftware(gate: DeferredSoftwarePreparation, ready: () -> Unit) {
        ExecutorHelper.startOnBackground {
            if (!gate.canContinue) return@startOnBackground
            try {
                SoftwareBiometricPromptRegistry.prepareInitial(
                    builder.getBiometricAuthRequest().type, builder.getContext(), { gate.canContinue }
                ) {
                    ExecutorHelper.post {
                        if (!gate.canContinue) return@post
                        try {
                            builder.invalidateAvailableTypes()
                            val types = eligibleDeferredSoftwareTypes()
                            LegacyBiometric.prepareSoftwareModulesForAuthentication(
                                builder.getBiometricAuthRequest(), types, false,
                                builder.getDisabledModuleTags(),
                                onModuleSkipped = { if (gate.canContinue) builder.disableBiometricModule(it) },
                                callback = object : AbstractSoftwareBiometricManager.PreparationCallback() {
                                    override fun onPrepared() {
                                        ExecutorHelper.post {
                                            if (!gate.canContinue) return@post
                                            admittedSoftwareTypes = eligibleDeferredSoftwareTypes().intersect(types)
                                            ready()
                                        }
                                    }
                                    override fun onPreparationError(errMsgId: Int, errString: CharSequence?) {
                                        ExecutorHelper.post { if (gate.canContinue) ready() }
                                    }
                                    override fun onPreparationCanceled() {
                                        ExecutorHelper.post { if (gate.canContinue) ready() }
                                    }
                                },
                                isActive = { gate.canContinue }
                            )
                        } catch (error: Exception) { e(error); ready() }
                        catch (error: LinkageError) { e(error); ready() }
                    }
                }
            } catch (error: Exception) {
                e(error)
                ExecutorHelper.post { if (gate.canContinue) ready() }
            } catch (error: LinkageError) {
                e(error)
                ExecutorHelper.post { if (gate.canContinue) ready() }
            }
        }
    }

    private fun eligibleDeferredSoftwareTypes(): Set<BiometricType> =
        builder.getAllAvailableTypes().filterTo(LinkedHashSet()) { type ->
            if (type in nativePrimaryTypes) return@filterTo false
            val route = builder.selectedRoute(type) ?: return@filterTo false
            if (route.provider != BiometricProviderType.SOFTWARE) return@filterTo false
            val permissions = builder.getSelectedTypePermissions(type)
            PermissionUtils.INSTANCE.hasSelfPermissions(permissions) &&
                !(android.Manifest.permission.CAMERA in permissions && SensorPrivacyCheck.isCameraBlocked()) &&
                route.module?.getModuleState()?.let {
                    it.managerAccessible && it.hardwarePresent && it.enrolled && !it.lockedOut && !it.permanentlyLocked
                } == true
        }

    private fun startPreparedSoftware() {
        val types = remainingSecondaryTypes().filter { it in admittedSoftwareTypes }
        val plan = planApi28StartAuthStage(
            remainingPrimaryTypes(), types, builder::selectedRoute,
            ::requiresReadyExtrasBeforeAuthentication,
            { SoftwareBiometricPromptRegistry.resolve(it, builder.getContext())?.supportsBackgroundPreparation(false) == true }
        )
        if (plan.backgroundPreparationTypes.isNotEmpty()) startParallelCapture(plan.backgroundPreparationTypes)
        startLegacyAuth(plan.legacyAuthTypes)
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
                types.forEach(builder::trackSoftwareEnrollment)
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
            val delegate = SoftwareBiometricPromptRegistry.resolve(
                type,
                builder.getContext()
            )
                ?.createPrompt(
                SoftwareBiometricPromptHost(
                    context = builder.getContext().applicationContext,
                    builder = builder,
                    enroll = builder.enroll,
                    rootView = null,
                    callbacks = object : SoftwareBiometricPromptHost.Callbacks {
                        override fun isPromptActive() = active()
                        override fun onHelp(message: CharSequence) {
                            onStatus(SoftwarePromptStatus(message))
                        }
                        override fun onStatus(status: SoftwarePromptStatus) {
                            ExecutorHelper.post(Runnable {
                                if (!active()) return@Runnable
                                showSoftwareFeedback(status, type)
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

    private fun showSoftwareFeedback(status: SoftwarePromptStatus, source: BiometricType? = null) {
        if (builder.getBiometricFeedbackOptions().enabled) foregroundFeedback?.show(source, status)
        else dialog?.onSoftwareStatus(status)
    }

    private fun updateSystemPromptStarted(started: Boolean) {
        systemPromptStarted.set(started)
        foregroundFeedback?.setSystemPromptActive(started)
    }

    private fun remainingPrimaryTypes(): Set<BiometricType> {
        return nativePrimaryTypes
            .filterNotTo(LinkedHashSet()) { type -> authFinished.containsKey(type) }
    }

    private fun remainingSecondaryTypes(): Set<BiometricType> {
        return builder.getSecondaryAvailableTypes()
            .filterTo(LinkedHashSet()) { type ->
                type !in nativePrimaryTypes && !authFinished.containsKey(type) &&
                        (deferredPreparation == null ||
                            builder.selectedRoute(type)?.provider != BiometricProviderType.SOFTWARE ||
                            type in admittedSoftwareTypes) &&
                        builder.selectedRoute(type)?.usesBiometricPromptHardware == false
            }
    }

    private fun pendingLegacyTypes(): Set<BiometricType> {
        return remainingSecondaryTypes()
    }

    private fun requiresReadyExtrasBeforeAuthentication(type: BiometricType): Boolean {
        return SoftwareBiometricPromptRegistry.resolve(
            type,
            builder.getContext()
        )
            ?.requiresReadyExtrasBeforeAuthentication == true
    }

    @SuppressLint("RestrictedApi")
    private fun showSystemUi(biometricPrompt: BiometricPrompt) {
        try {
            d("BiometricPromptApi28Impl.showSystemUi() $biometricPrompt")
            // Use the host that owns this AndroidX prompt, never a newly resumed Activity.
            val activity = biometricPromptHost
            if (activity == null || activity.isDestroyed || activity.isFinishing ||
                activity.supportFragmentManager.isStateSaved
            ) {
                callback?.onFailed(builder.getAllAvailableTypes().map {
                    AuthenticationResult(
                        it,
                        reason = AuthenticationFailureReason.INTERNAL_ERROR,
                        description = if (activity?.supportFragmentManager?.isStateSaved == true) {
                            biometricStartAuthenticationDescription()
                        } else {
                            biometricActivityDestroyedDescription()
                        }
                    )
                }.toSet())
                return
            }
            val sessionToken = authSessionToken
            if (systemPromptCancellation == null) {
                systemPromptCancellation = AndroidXPromptCancellation(
                    activity.supportFragmentManager, biometricPrompt
                ) { authSessionState.owns(sessionToken) }
            }
            systemPromptCancellation?.observe()
            var biometricCryptoObject: BiometricCryptoObject? = null
            var isAppFlowCrypto = false
            builder.getCryptographyPurpose()?.let {
                biometricCryptoObject = BiometricCryptoObjectHelper.getBiometricCryptoObject(
                    PROMPT_CRYPTO_KEY,
                    builder.getCryptographyPurpose(),
                    true
                )
                isAppFlowCrypto =
                    AppFlowCryptoRegistry.getAccessType(PROMPT_CRYPTO_KEY) == CryptoAccessType.APP_FLOW
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
                updateSystemPromptStarted(true)
            } else if (crpObject != null) {
                try {
                    pendingPromptCryptoObject.set(null)
                    authCallTimestamp.set(System.currentTimeMillis())
                    biometricPrompt.authenticate(biometricPromptInfo, crpObject)
                    updateSystemPromptStarted(true)
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
                            updateSystemPromptStarted(true)
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
                    updateSystemPromptStarted(true)
                }
            } else {
                pendingPromptCryptoObject.set(null)
                authCallTimestamp.set(System.currentTimeMillis())
                biometricPrompt.authenticate(biometricPromptInfo)
                updateSystemPromptStarted(true)
            }
        } catch (e: BiometricCryptoException) {
            e(e)
            callback?.onFailed(builder.getAllAvailableTypes().map {
                AuthenticationResult(
                    it,
                    reason = AuthenticationFailureReason.CRYPTO_ERROR,
                    description = biometricRequiredCryptoRejectedDescription()
                )
            }.toSet())
        }
    }


    override fun stopAuth() {
        deferredPreparation?.cancel()
        updateSystemPromptStarted(false)
        parallelCapture = null
        parallelDelegates.forEach { it.dispose() }
        parallelDelegates.clear()
        foregroundFeedback?.clear()
        legacySessionOwner = Any()
        e("BiometricPromptApi28Impl.stopAuth():")
        LegacyBiometric.cancelAuthentication()
        val cancellation = systemPromptCancellation
        systemPromptCancellation = null
        if (cancellation != null) {
            cancellation.cancel()
        } else {
            biometricPrompt?.cancelAuthentication()
        }
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
        if (fromSystemPrompt) updateSystemPromptStarted(false)
        val normalizedModule = normalizeCryptoResult(module, authResult)
        val normalizedAuthResult = if (normalizedModule?.reason == AuthenticationFailureReason.CRYPTO_ERROR) {
            AuthResult.AuthResultState.FATAL_ERROR
        } else {
            authResult
        }
        if (!fromSystemPrompt && normalizedModule != null) {
            val terminalStatus = if (normalizedAuthResult == AuthResult.AuthResultState.FATAL_ERROR) {
                normalizedModule.description?.takeIf { it.isNotBlank() }
                    ?.let { SoftwarePromptStatus(it, terminal = true) }
            } else null
            foregroundFeedback?.finishSource(normalizedModule.type,
                terminalStatus.takeIf { builder.getBiometricFeedbackOptions().enabled })
            if (!builder.getBiometricFeedbackOptions().enabled) {
                terminalStatus?.let { dialog?.onSoftwareStatus(it) }
            }
        }
        if (fromSystemPrompt) hardwareConfirmation = normalizedAuthResult
        var failureReason = normalizedModule?.reason
        if (shouldApplyExhaustedLegacyLockout(fromSystemPrompt, failureReason)) {
            HardwareAccessImpl.getInstance(builder.getBiometricAuthRequest()).lockout()
            failureReason = AuthenticationFailureReason.LOCKED_OUT
        }
        var added = false
        val completedTypes = if (fromSystemPrompt) nativePrimaryTypes
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
            authFinished,
            softwarePreparationPending = deferredPreparation?.isPending == true
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


        } else if (deferredPreparation?.isPending != true && shouldShowPostSystemCompatDialog(
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
        StatusLegacyBiometricAuthenticationListener {

        override fun onSuccess(result: AuthenticationResult) {
            IconStateHelper.successType(result.type)
            checkAuthResult(AuthResult.AuthResultState.SUCCESS, result, fromSystemPrompt = false)
        }

        override fun onHelp(msg: CharSequence?) {
            if (!msg.isNullOrEmpty()) {
                showSoftwareFeedback(SoftwarePromptStatus(msg))
            }
        }

        override fun onStatus(source: BiometricType?, status: SoftwarePromptStatus) {
            showSoftwareFeedback(status, source)
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
                    showSoftwareFeedback(SoftwarePromptStatus(it), result.type)
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
