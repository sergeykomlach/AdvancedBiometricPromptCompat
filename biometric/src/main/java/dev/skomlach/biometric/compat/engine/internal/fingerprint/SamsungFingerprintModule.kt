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

package dev.skomlach.biometric.compat.engine.internal.fingerprint

import android.app.Activity
import androidx.core.os.CancellationSignal
import com.samsung.android.sdk.pass.Spass
import com.samsung.android.sdk.pass.SpassFingerprint
import dev.skomlach.biometric.compat.AuthenticationFailureReason
import dev.skomlach.biometric.compat.BiometricCryptoObject
import dev.skomlach.biometric.compat.engine.BiometricInitListener
import dev.skomlach.biometric.compat.engine.BiometricMethod
import dev.skomlach.biometric.compat.engine.core.interfaces.AuthenticationListener
import dev.skomlach.biometric.compat.engine.core.interfaces.RestartPredicate
import dev.skomlach.biometric.compat.engine.internal.AbstractBiometricModule
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.d
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.e
import dev.skomlach.common.misc.ExecutorHelper


class SamsungFingerprintModule(listener: BiometricInitListener?) :
    AbstractBiometricModule(BiometricMethod.FINGERPRINT_SAMSUNG) {
    private var mSpass: Spass? = null
    private var mSpassFingerprint: SpassFingerprint? = null

    init {
        try {
            mSpass = Spass()
            mSpass?.initialize(context)
            if (mSpass?.isFeatureEnabled(Spass.DEVICE_FINGERPRINT) == false) {
                throw RuntimeException("No hardware")
            }
            mSpassFingerprint = SpassFingerprint(context)
        } catch (e: Throwable) {
            if (DEBUG_MANAGERS)
                e(e, name)
            mSpass = null
            mSpassFingerprint = null
        }
        listener?.initFinished(biometricMethod, this@SamsungFingerprintModule)
    }

    override val isUserAuthCanByUsedWithCrypto: Boolean
        get() = false

    override fun getManagers(): Set<Any> {
        val managers = HashSet<Any>()
        mSpassFingerprint?.let {
            managers.add(it)
        }
        return managers
    }

    override fun getIds(manager: Any): List<String> {
        val ids = ArrayList<String>()
        try {
            mSpassFingerprint?.let {
                it.registeredFingerprintUniqueID?.let { array ->
                    for (i in 0 until array.size()) {
                        //Sparsearray contains String
                        (array.get(i) as? String)?.let { s ->
                            ids.add(s)
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            mSpassFingerprint?.let {
                it.registeredFingerprintName?.let { array ->
                    for (i in 0 until array.size()) {
                        //Sparsearray contains String
                        (array.get(i) as? String)?.let { s ->
                            ids.add(s)
                        }
                    }
                }
            }
        }
        return ids
    }

    override val isManagerAccessible: Boolean
        get() = mSpass != null && mSpassFingerprint != null
    override val isHardwarePresent: Boolean
        get() {

            try {
                return mSpass?.isFeatureEnabled(Spass.DEVICE_FINGERPRINT) == true
            } catch (e: Throwable) {

            }

            return false
        }

    override val hasEnrolled: Boolean
        get() {

            try {
                return mSpassFingerprint?.hasRegisteredFinger() == true
            } catch (e: Throwable) {

            }

            return false
        }

    @Throws(SecurityException::class)
    override fun authenticate(
        biometricCryptoObject: BiometricCryptoObject?,
        cancellationSignal: CancellationSignal?,
        listener: AuthenticationListener?,
        restartPredicate: RestartPredicate?
    ) {
        d("$name.authenticate - $biometricMethod; Crypto=$biometricCryptoObject")
        mSpassFingerprint?.let {
            try {
                cancelFingerprintRequest()
                val callback = object : SpassFingerprint.IdentifyListener {
                    private var errorTs = System.currentTimeMillis()
                    private val skipTimeout =
                        context.resources.getInteger(android.R.integer.config_shortAnimTime)

                    override fun onFinished(status: Int) {
                        val tmp = System.currentTimeMillis()
                        if (tmp - errorTs <= skipTimeout || tmp - authCallTimestamp.get() <= skipTimeout)
                            return
                        errorTs = tmp
                        when (status) {
                            SpassFingerprint.STATUS_AUTHENTIFICATION_SUCCESS, SpassFingerprint.STATUS_AUTHENTIFICATION_PASSWORD_SUCCESS -> {
                                listener?.onSuccess(
                                    tag(),
                                    BiometricCryptoObject(
                                        biometricCryptoObject?.signature,
                                        biometricCryptoObject?.cipher,
                                        biometricCryptoObject?.mac
                                    )
                                )
                                return
                            }

                            SpassFingerprint.STATUS_QUALITY_FAILED, SpassFingerprint.STATUS_SENSOR_FAILED -> fail(
                                AuthenticationFailureReason.SENSOR_FAILED
                            )

                            SpassFingerprint.STATUS_AUTHENTIFICATION_FAILED -> fail(
                                AuthenticationFailureReason.AUTHENTICATION_FAILED
                            )

                            SpassFingerprint.STATUS_TIMEOUT_FAILED -> fail(
                                AuthenticationFailureReason.TIMEOUT
                            )

                            SpassFingerprint.STATUS_USER_CANCELLED, SpassFingerprint.STATUS_BUTTON_PRESSED, SpassFingerprint.STATUS_USER_CANCELLED_BY_TOUCH_OUTSIDE -> {
                                return
                            }

                            else -> fail(AuthenticationFailureReason.UNKNOWN)
                        }
                    }

                    private fun fail(reason: AuthenticationFailureReason) {
                        var failureReason: AuthenticationFailureReason? = reason

                        if (restartCauseTimeout(failureReason)) {
                            cancelFingerprintRequest()
                            ExecutorHelper.postDelayed({
                                authenticate(
                                    biometricCryptoObject,
                                    cancellationSignal,
                                    listener,
                                    restartPredicate
                                )
                            }, context.resources.getInteger(android.R.integer.config_longAnimTime).toLong())
                        } else
                            if (failureReason == AuthenticationFailureReason.TIMEOUT || restartPredicate?.invoke(
                                    failureReason
                                ) == true
                            ) {
                                listener?.onFailure(failureReason, tag())
                                cancelFingerprintRequest()
                            } else {
                                if (mutableListOf(
                                        AuthenticationFailureReason.SENSOR_FAILED,
                                        AuthenticationFailureReason.AUTHENTICATION_FAILED
                                    ).contains(failureReason)
                                ) {
                                    lockout()
                                    failureReason = AuthenticationFailureReason.LOCKED_OUT
                                }
                                listener?.onFailure(failureReason, tag())
                                if (mutableListOf(
                                        AuthenticationFailureReason.SENSOR_FAILED,
                                        AuthenticationFailureReason.AUTHENTICATION_FAILED
                                    ).contains(failureReason)
                                ) {
                                    cancelFingerprintRequest()
                                    ExecutorHelper.postDelayed({
                                        authenticate(
                                            biometricCryptoObject,
                                            cancellationSignal,
                                            listener,
                                            restartPredicate
                                        )
                                    }, context.resources.getInteger(android.R.integer.config_longAnimTime).toLong())
                                }
                            }
                    }

                    override fun onReady() {}
                    override fun onStarted() {}
                    override fun onCompleted() {}
                }
                cancellationSignal?.setOnCancelListener {
                    cancelFingerprintRequest()
                }
                authCallTimestamp.set(System.currentTimeMillis())
                it.startIdentify(callback)

                return
            } catch (e: Throwable) {
                e(e, "$name: authenticate failed unexpectedly")
            }
        }
        listener?.onFailure(
            AuthenticationFailureReason.UNKNOWN,
            tag()
        )
        return
    }

    private fun cancelFingerprintRequest() {
        try {

            mSpassFingerprint?.cancelIdentify()

        } catch (t: Throwable) {
            // There's no way to query if there's an active identify request,
            // so just try to cancel and ignore any exceptions.
        }
    }

    fun openSettings(context: Activity): Boolean {
        return try {
            mSpassFingerprint?.registerFinger(context) { }
            true
        } catch (e: Exception) {
            e(e, name)
            false
        }
    }


}