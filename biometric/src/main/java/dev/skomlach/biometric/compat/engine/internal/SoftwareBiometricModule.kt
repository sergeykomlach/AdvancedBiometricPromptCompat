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

package dev.skomlach.biometric.compat.engine.internal

import android.os.Bundle
import android.os.Handler
import androidx.core.os.CancellationSignal
import dev.skomlach.biometric.compat.AuthenticationFailureReason
import dev.skomlach.biometric.compat.BiometricCryptoObject
import dev.skomlach.biometric.compat.BundleBuilder
import dev.skomlach.biometric.compat.softwareBiometricHardwareBackedCryptoUnsupportedDescription
import dev.skomlach.biometric.compat.custom.AbstractSoftwareBiometricManager
import dev.skomlach.biometric.compat.custom.SoftwareBiometricEnrollment
import dev.skomlach.biometric.compat.custom.requireReadable
import dev.skomlach.common.storage.SharedPreferenceProvider
import dev.skomlach.common.storage.editProtected
import dev.skomlach.biometric.compat.custom.AbstractSoftwareBiometricManager.Companion.CUSTOM_BIOMETRIC_ERROR_HW_NOT_PRESENT
import dev.skomlach.biometric.compat.custom.AbstractSoftwareBiometricManager.Companion.CUSTOM_BIOMETRIC_ERROR_HW_UNAVAILABLE
import dev.skomlach.biometric.compat.custom.AbstractSoftwareBiometricManager.Companion.CUSTOM_BIOMETRIC_ERROR_LOCKOUT
import dev.skomlach.biometric.compat.custom.AbstractSoftwareBiometricManager.Companion.CUSTOM_BIOMETRIC_ERROR_LOCKOUT_PERMANENT
import dev.skomlach.biometric.compat.custom.AbstractSoftwareBiometricManager.Companion.CUSTOM_BIOMETRIC_ERROR_NO_BIOMETRIC
import dev.skomlach.biometric.compat.custom.AbstractSoftwareBiometricManager.Companion.CUSTOM_BIOMETRIC_ERROR_NO_PERMISSIONS
import dev.skomlach.biometric.compat.custom.AbstractSoftwareBiometricManager.Companion.CUSTOM_BIOMETRIC_ERROR_NO_SPACE
import dev.skomlach.biometric.compat.custom.AbstractSoftwareBiometricManager.Companion.CUSTOM_BIOMETRIC_ERROR_TIMEOUT
import dev.skomlach.biometric.compat.custom.AbstractSoftwareBiometricManager.Companion.CUSTOM_BIOMETRIC_ERROR_UNABLE_TO_PROCESS
import dev.skomlach.biometric.compat.custom.AbstractSoftwareBiometricManager.Companion.CUSTOM_BIOMETRIC_ERROR_USER_CANCELED
import dev.skomlach.biometric.compat.custom.SoftwareBiometricSecurityDecision
import dev.skomlach.biometric.compat.custom.SoftwareBiometricSecurityPolicy
import dev.skomlach.biometric.compat.custom.SoftwareBiometricAssuranceLevel
import dev.skomlach.biometric.compat.custom.SoftwareBiometricSessionGuard
import dev.skomlach.biometric.compat.custom.SoftwareBiometricSessionToken
import dev.skomlach.biometric.compat.custom.SoftwareBiometricTerminalState
import dev.skomlach.biometric.compat.custom.SoftwareBiometricRuntime
import dev.skomlach.biometric.compat.engine.BiometricMethod
import dev.skomlach.biometric.compat.engine.LegacyBiometricInitListener
import dev.skomlach.biometric.compat.engine.core.Core
import dev.skomlach.biometric.compat.engine.core.interfaces.AuthenticationListener
import dev.skomlach.biometric.compat.engine.core.interfaces.BiometricModuleState
import dev.skomlach.biometric.compat.engine.core.interfaces.RestartPredicate
import dev.skomlach.biometric.compat.utils.BiometricErrorLockoutPermanentFix
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.d
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.e
import dev.skomlach.common.misc.ExecutorHelper

internal fun resolveSoftwareFailureReason(
    baseReason: AuthenticationFailureReason,
    managerLockoutError: Int?
): AuthenticationFailureReason {
    if (baseReason != AuthenticationFailureReason.AUTHENTICATION_FAILED &&
        baseReason != AuthenticationFailureReason.SENSOR_FAILED
    ) {
        return baseReason
    }

    return when (managerLockoutError) {
        CUSTOM_BIOMETRIC_ERROR_HW_UNAVAILABLE -> AuthenticationFailureReason.HARDWARE_UNAVAILABLE
        CUSTOM_BIOMETRIC_ERROR_LOCKOUT_PERMANENT,
        CUSTOM_BIOMETRIC_ERROR_LOCKOUT -> resolveSoftwareLockoutFailureReason(managerLockoutError)
        else -> baseReason
    }
}

internal fun resolveSoftwareLockoutFailureReason(
    managerLockoutError: Int
): AuthenticationFailureReason {
    return when (managerLockoutError) {
        CUSTOM_BIOMETRIC_ERROR_LOCKOUT_PERMANENT -> AuthenticationFailureReason.HARDWARE_UNAVAILABLE
        else -> AuthenticationFailureReason.LOCKED_OUT
    }
}

class SoftwareBiometricModule internal constructor(
    private val method: BiometricMethod,
    internal val runtime: SoftwareBiometricRuntime?,
    private val listener: LegacyBiometricInitListener?
) :
    AbstractBiometricModule(method) {
    internal val manager: AbstractSoftwareBiometricManager?
        get() = runtime?.manager
    private val timeoutHandler = Handler(ExecutorHelper.handler.looper)
    private val sessionGuard = SoftwareBiometricSessionGuard()
    private val enrollmentTracker by lazy {
        EnrollmentChangeTracker(
            readSnapshot = { (manager?.getEnrollmentSnapshot() ?: SoftwareBiometricEnrollment.Unavailable()).toEnrollmentSnapshot() },
            readBaseline = {
                val preferences = softwareEnrollmentPreferences()
                val key = "enrolled_v1_" + tag()
                if (preferences.contains(key)) {
                    decodeEnrollmentBaseline(preferences.getString(key, null))
                } else null
            },
            readLegacyBaseline = {
                SharedPreferenceProvider.getPreferences("BiometricCompat_AbstractModule")
                    .getStringSet("enrolled_" + tag(), null)?.toSet()
            },
            writeBaseline = { snapshot ->
                softwareEnrollmentPreferences().editProtected {
                    putString("enrolled_v1_" + tag(), encodeEnrollmentBaseline(snapshot))
                }
            },
            onError = { e(it, "Software enrollment snapshot unavailable") }
        )
    }

    private fun softwareEnrollmentPreferences() =
        SharedPreferenceProvider.getProtectedPreferences("BiometricCompat_SoftwareEnrollment")

    @Deprecated("Use provider snapshots when an unavailable state must be distinguished from unchanged")
    override val isBiometricEnrollChanged: Boolean
        get() = when (enrollmentTracker.check()) {
            EnrollmentChange.CHANGED -> true
            EnrollmentChange.UNCHANGED -> false
            EnrollmentChange.UNAVAILABLE -> enrollmentTracker.lastConfirmedChange
            EnrollmentChange.UNSUPPORTED -> super.isBiometricEnrollChanged
        }

    override fun updateBiometricEnrollChanged() {
        if (enrollmentTracker.acknowledge() == EnrollmentChange.UNSUPPORTED) {
            super.updateBiometricEnrollChanged()
        }
    }

    private fun requireReadableEnrollment() {
        (manager?.getEnrollmentSnapshot() ?: SoftwareBiometricEnrollment.Unavailable()).requireReadable()
    }
    private var activeSessionToken: SoftwareBiometricSessionToken? = null
    private var enrollBundle: Bundle? = null
    private var enrollmentRollbackScope: EnrollmentRollbackScope? = null

    internal fun trackEnrollmentRollback(): EnrollmentRollbackScope =
        EnrollmentRollbackScope().also { enrollmentRollbackScope = it }

    internal fun finishEnrollmentRollback(scope: EnrollmentRollbackScope, succeeded: Boolean) {
        if (enrollmentRollbackScope === scope) enrollmentRollbackScope = null
        scope.finish(succeeded)
    }
    private var timeoutRunnable = Runnable {}

    init {
        listener?.initFinished(biometricMethod, this@SoftwareBiometricModule)
    }

    override val priority: Int
        get() = manager?.priority ?: super.priority


    fun rollbackLastEnroll() {
        d("$name: rollbackLastEnroll $enrollBundle")
        try {
            manager?.remove(enrollBundle ?: return)
        } catch (error: Exception) {
            e(error, "$name: enrollment rollback unavailable")
        }
    }

    override fun getManagers(): Set<Any> {
        // Retained only for providers that do not implement the typed snapshot contract.
        return manager?.getManagers() ?: emptySet()
    }

    override val isManagerAccessible: Boolean
        get() = getModuleState().managerAccessible
    override val isHardwarePresent: Boolean
        get() {

            val result = try {
                manager?.isHardwareDetected() == true
            } catch (e: Throwable) {
                false
            }
            d("$name: isHardwareDetected=$result")
            return result
        }

    override val isLockOut: Boolean
        get() {
            val result = try {
                super.isLockOut || manager?.isLockedOut() == true
            } catch (e: Throwable) {
                true
            }
            d("$name: isLockOut=$result")
            return result
        }

    val isPermanentlyLockedOut: Boolean
        get() {
            val result = try {
                manager?.getLockoutError() == CUSTOM_BIOMETRIC_ERROR_LOCKOUT_PERMANENT
            } catch (e: Throwable) {
                false
            }
            d("$name: isPermanentlyLockedOut=$result")
            return result
        }

    override val hasEnrolled: Boolean
        get() {

            val result = try {
                requireReadableEnrollment()
                manager?.hasEnrolledBiometric() == true
            } catch (e: Throwable) {
                false
            }
            d("$name: hasEnrolled=$result")
            return result
        }

    override fun getModuleState(): BiometricModuleState = readSoftwareModuleState(
        managerPresent = manager != null,
        moduleLockedOut = super.isLockOut,
        hardwareDetected = { manager?.isHardwareDetected() == true },
        hasEnrollment = {
            requireReadableEnrollment()
            manager?.hasEnrolledBiometric() == true
        },
        lockoutError = { manager?.getLockoutError() },
        onError = { e(it, "$name: software state unavailable") },
        managerLockedOut = { manager?.isLockedOut() == true }
    )

    @Throws(SecurityException::class)
    override fun authenticate(
        biometricCryptoObject: BiometricCryptoObject?,
        cancellationSignal: CancellationSignal?,
        listener: AuthenticationListener?,
        restartPredicate: RestartPredicate?
    ) {
        manager?.let {
            try {
                activeSessionToken?.let { previous ->
                    sessionGuard.tryTerminate(previous, SoftwareBiometricTerminalState.CANCELLED)
                }
                val sessionToken = sessionGuard.start()
                activeSessionToken = sessionToken
                // Why getCancellationSignalObject returns an Object is unexplained
                (if (cancellationSignal == null) null else cancellationSignal.cancellationSignalObject as android.os.CancellationSignal?)
                    ?: throw IllegalArgumentException("CancellationSignal can't be null")

                this.originalCancellationSignal = cancellationSignal
                timeoutHandler.postDelayed(Runnable {
                    if (this.originalCancellationSignal?.isCanceled == false) {
                        listener?.onFailure(
                            tag(),
                            AuthenticationFailureReason.TIMEOUT,
                            manager?.getTimeoutMessage()
                        )
                        sessionGuard.tryTerminate(
                            sessionToken,
                            SoftwareBiometricTerminalState.EXPIRED
                        )
                        this.originalCancellationSignal?.cancel()
                    }
                }.also {
                    timeoutRunnable = it
                }, 30_000L)
                authenticateInternal(
                    biometricCryptoObject,
                    listener,
                    restartPredicate,
                    sessionToken
                )
                return
            } catch (error: Throwable) {
                e(error, "$name: authenticate failed unexpectedly")
                timeoutHandler.removeCallbacks(timeoutRunnable)
                activeSessionToken?.let { sessionGuard.tryTerminate(it, SoftwareBiometricTerminalState.CANCELLED) }
                originalCancellationSignal?.cancel()
                if (error is dev.skomlach.common.storage.ProtectedStorageUnavailableException) {
                    listener?.onFailure(tag(), AuthenticationFailureReason.HARDWARE_UNAVAILABLE,
                        startAuthenticationFailureDescription())
                    return
                }
            }
        }
        listener?.onFailure(
            tag(),
            AuthenticationFailureReason.INTERNAL_ERROR,
            startAuthenticationFailureDescription()
        )
        return
    }

    private fun authenticateInternal(
        biometricCryptoObject: BiometricCryptoObject?,
        listener: AuthenticationListener?,
        restartPredicate: RestartPredicate?,
        sessionToken: SoftwareBiometricSessionToken
    ) {
        d("$name.authenticate - $biometricMethod; Crypto=$biometricCryptoObject")
        manager?.let {
            try {
                // Build the provider-facing object once so the common gate and provider
                // invocation observe the same cryptographic request.
                val crypto = if (biometricCryptoObject == null) null else {
                    if (biometricCryptoObject.cipher != null)
                        AbstractSoftwareBiometricManager.CryptoObject(biometricCryptoObject.cipher)
                    else if (biometricCryptoObject.mac != null)
                        AbstractSoftwareBiometricManager.CryptoObject(biometricCryptoObject.mac)
                    else if (biometricCryptoObject.signature != null)
                        AbstractSoftwareBiometricManager.CryptoObject(biometricCryptoObject.signature)
                    else
                        null
                }
                val securityDecision = SoftwareBiometricSecurityPolicy.evaluate(
                    profile = it.securityProfile,
                    requestedType = biometricMethod.biometricType,
                    cryptoObject = crypto,
                    trustedCapture = it.trustedCaptureForAuthentication,
                    compatibilityCapture = it.securityProfile.assurance ==
                        SoftwareBiometricAssuranceLevel.LEGACY_COMPATIBILITY
                )
                if (securityDecision != SoftwareBiometricSecurityDecision.ALLOW ||
                    (biometricCryptoObject.hasUsableCrypto() && !it.supportsCryptoObject)
                ) {
                    timeoutHandler.removeCallbacks(timeoutRunnable)
                    listener?.onFailure(
                        tag(),
                        AuthenticationFailureReason.CRYPTO_ERROR,
                        softwareBiometricHardwareBackedCryptoUnsupportedDescription(name)
                    )
                    originalCancellationSignal?.cancel()
                    return
                }
                val cancellationSignal = CancellationSignal()
                originalCancellationSignal?.setOnCancelListener {
                    sessionGuard.tryTerminate(
                        sessionToken,
                        SoftwareBiometricTerminalState.CANCELLED
                    )
                    if (!cancellationSignal.isCanceled) {
                        timeoutHandler.removeCallbacks(timeoutRunnable)
                        cancellationSignal.cancel()
                    }
                }
                // Why getCancellationSignalObject returns an Object is unexplained
                val signalObject =
                    (cancellationSignal.cancellationSignalObject as android.os.CancellationSignal?)
                        ?: throw IllegalArgumentException("CancellationSignal can't be null")
                val callback: AbstractSoftwareBiometricManager.AuthenticationCallback =
                    AuthCallback(
                        biometricCryptoObject,
                        restartPredicate,
                        cancellationSignal,
                        listener,
                        sessionToken
                    )

                d("$name.authenticate:  Crypto=$crypto")
                // Recheck after preparation: unavailable templates must never reach the provider.
                requireReadableEnrollment()
                authCallTimestamp.set(System.currentTimeMillis())
                it.authenticate(
                    crypto,
                    0,
                    signalObject,
                    callback,
                    ExecutorHelper.handler,
                    convertBundleToCustom()
                        ?: throw IllegalArgumentException("Bundle should be not NULL")
                )
                return
            } catch (error: Throwable) {
                e(error, "$name: authenticate failed unexpectedly")
                timeoutHandler.removeCallbacks(timeoutRunnable)
                activeSessionToken?.let { sessionGuard.tryTerminate(it, SoftwareBiometricTerminalState.CANCELLED) }
                originalCancellationSignal?.cancel()
                if (error is dev.skomlach.common.storage.ProtectedStorageUnavailableException) {
                    listener?.onFailure(tag(), AuthenticationFailureReason.HARDWARE_UNAVAILABLE,
                        startAuthenticationFailureDescription())
                    return
                }
            }
        }
        listener?.onFailure(
            tag(),
            AuthenticationFailureReason.INTERNAL_ERROR,
            startAuthenticationFailureDescription()
        )
        return
    }

    private fun BiometricCryptoObject?.hasUsableCrypto(): Boolean {
        return this?.cipher != null || this?.mac != null || this?.signature != null
    }

    private fun convertBundleToCustom(): Bundle? {

        var extras = bundle

        if (bundle?.getBoolean(
                BundleBuilder.ENROLL,
                false
            ) == true
        ) {
            extras = Bundle(bundle ?: Bundle()).apply {
                // Provisional enrollment must never replace an existing template or use
                // a null tag whose rollback means "remove all" in a software manager.
                val provisionalName = enrollmentRollbackScope?.let { java.util.UUID.randomUUID().toString() }
                manager?.getEnrollBundle(provisionalName)?.let { putAll(it) }
            }
            enrollBundle = extras
            val enrolledExtras = extras
            enrollmentRollbackScope?.record {
                runCatching { manager?.remove(enrolledExtras) }.onFailure { e(it) }
            }
        } else enrollBundle = null

        return extras
    }

    internal inner class AuthCallback(
        private val biometricCryptoObject: BiometricCryptoObject?,
        private val restartPredicate: RestartPredicate?,
        private val cancellationSignal: CancellationSignal?,
        private val listener: AuthenticationListener?,
        private val sessionToken: SoftwareBiometricSessionToken
    ) : AbstractSoftwareBiometricManager.AuthenticationCallback() {
        private val callbackGate = SoftwareBiometricCallbackGate(
            sessionGuard, sessionToken,
            { cancellationSignal?.isCanceled != false || originalCancellationSignal?.isCanceled != false },
            android.os.SystemClock::elapsedRealtime
        )
        private var errorTs = 0L
        private val skipTimeout =
            context.resources.getInteger(android.R.integer.config_shortAnimTime)
        private var selfCanceled = false
        override fun onAuthenticationError(errMsgId: Int, errString: CharSequence?) {
            d("$name.onAuthenticationError: $errMsgId-$errString")
            val tmp = System.currentTimeMillis()
            if (tmp - errorTs <= skipTimeout)
                return
            errorTs = tmp
            var failureReason = AuthenticationFailureReason.UNKNOWN
            when (if (errMsgId < 1000) errMsgId else errMsgId % 1000) {
                CUSTOM_BIOMETRIC_ERROR_NO_BIOMETRIC -> failureReason =
                    AuthenticationFailureReason.NO_BIOMETRICS_REGISTERED

                CUSTOM_BIOMETRIC_ERROR_HW_NOT_PRESENT -> failureReason =
                    AuthenticationFailureReason.NO_HARDWARE

                CUSTOM_BIOMETRIC_ERROR_HW_UNAVAILABLE -> failureReason =
                    AuthenticationFailureReason.HARDWARE_UNAVAILABLE

                CUSTOM_BIOMETRIC_ERROR_NO_PERMISSIONS -> failureReason =
                    AuthenticationFailureReason.MISSING_PERMISSIONS_ERROR

                CUSTOM_BIOMETRIC_ERROR_LOCKOUT_PERMANENT -> {
                    BiometricErrorLockoutPermanentFix.setBiometricSensorPermanentlyLocked(
                        biometricMethod.biometricType
                    )
                    failureReason = AuthenticationFailureReason.HARDWARE_UNAVAILABLE
                }

                CUSTOM_BIOMETRIC_ERROR_UNABLE_TO_PROCESS -> failureReason =
                    AuthenticationFailureReason.AUTHENTICATION_FAILED

                CUSTOM_BIOMETRIC_ERROR_NO_SPACE -> failureReason =
                    AuthenticationFailureReason.SENSOR_FAILED

                CUSTOM_BIOMETRIC_ERROR_TIMEOUT -> failureReason =
                    AuthenticationFailureReason.TIMEOUT

                CUSTOM_BIOMETRIC_ERROR_LOCKOUT -> {
                    lockout()
                    failureReason = AuthenticationFailureReason.LOCKED_OUT
                }

                CUSTOM_BIOMETRIC_ERROR_USER_CANCELED, CUSTOM_BIOMETRIC_ERROR_USER_CANCELED -> {
                    return
                }

                else -> {
                    if (!selfCanceled) {
                        listener?.onFailure(tag(), failureReason, errString)
                        postCancelTask {
                            if (cancellationSignal?.isCanceled == false) {
                                selfCanceled = true
                                listener?.onCanceled(
                                    tag(),
                                    AuthenticationFailureReason.CANCELED,
                                    null
                                )
                                Core.cancelAuthentication(this@SoftwareBiometricModule)
                            }
                        }
                    }
                    return
                }
            }
            if (restartCauseTimeout(failureReason)) {
                selfCanceled = true
                cancellationSignal?.cancel()
                ExecutorHelper.postDelayed({
                    authenticateInternal(
                        biometricCryptoObject,
                        listener,
                        restartPredicate,
                        sessionToken
                    )
                }, skipTimeout.toLong())
            } else
                if (failureReason == AuthenticationFailureReason.TIMEOUT || restartPredicate?.invoke(
                        failureReason
                    ) == true
                ) {
                    listener?.onFailure(tag(), failureReason, errString)
                    selfCanceled = true
                    cancellationSignal?.cancel()
                    ExecutorHelper.postDelayed({
                        authenticateInternal(
                            biometricCryptoObject,
                            listener,
                            restartPredicate,
                            sessionToken
                        )
                    }, skipTimeout.toLong())
                } else {
                    failureReason = resolveSoftwareFailureReason(
                        failureReason,
                        manager?.getLockoutError()
                    )
                    if (failureReason == AuthenticationFailureReason.LOCKED_OUT) {
                        lockout()
                    }
                    listener?.onFailure(tag(), failureReason, errString)
                    postCancelTask {
                        if (cancellationSignal?.isCanceled == false) {
                            selfCanceled = true
                            listener?.onCanceled(tag(), AuthenticationFailureReason.CANCELED, null)
                            Core.cancelAuthentication(this@SoftwareBiometricModule)
                        }
                    }
                }
        }

        override fun onAuthenticationHelp(helpMsgId: Int, helpString: CharSequence?) {
            if (!callbackGate.tryHelp(helpString)) return
            d("$name.onAuthenticationHelp: $helpMsgId-$helpString")
            listener?.onHelp(helpString)
        }

        override fun onAuthenticationSucceeded(result: AbstractSoftwareBiometricManager.AuthenticationResult?) {
            d("$name.onAuthenticationSucceeded: $result; Crypto=${result?.cryptoObject}")
            if (cancellationSignal?.isCanceled != false || originalCancellationSignal?.isCanceled != false) {
                timeoutHandler.removeCallbacks(timeoutRunnable)
                return
            }
            try {
                if (!sessionGuard.tryTerminate(
                        sessionToken,
                        SoftwareBiometricTerminalState.SUCCEEDED
                    )
                ) {
                    return
                }
                if (result?.cryptoObject != null &&
                    this@SoftwareBiometricModule.manager?.securityProfile?.supportsCryptoObject != true
                ) {
                    listener?.onFailure(
                        tag(),
                        AuthenticationFailureReason.CRYPTO_ERROR,
                        softwareBiometricHardwareBackedCryptoUnsupportedDescription(name)
                    )
                    return
                }
                listener?.onSuccess(
                    tag(),
                    BiometricCryptoObject(
                        result?.cryptoObject?.signature,
                        result?.cryptoObject?.cipher,
                        result?.cryptoObject?.mac
                    )
                )
            } finally {
                timeoutHandler.removeCallbacks(timeoutRunnable)
            }
        }

        override fun onAuthenticationFailed() {
            d("$name.onAuthenticationFailed: ")
            listener?.onFailure(tag(), AuthenticationFailureReason.AUTHENTICATION_FAILED, null)
        }
    }

}

