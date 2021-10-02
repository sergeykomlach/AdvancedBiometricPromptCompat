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

import android.content.*
import androidx.annotation.RestrictTo
import androidx.core.os.CancellationSignal
import com.samsung.android.sdk.pass.Spass
import com.samsung.android.sdk.pass.SpassFingerprint
import dev.skomlach.biometric.compat.engine.AuthenticationFailureReason
import dev.skomlach.biometric.compat.engine.BiometricInitListener
import dev.skomlach.biometric.compat.engine.BiometricMethod
import dev.skomlach.biometric.compat.engine.core.interfaces.AuthenticationListener
import dev.skomlach.biometric.compat.engine.core.interfaces.RestartPredicate
import dev.skomlach.biometric.compat.engine.internal.AbstractBiometricModule
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.d
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.e

@RestrictTo(RestrictTo.Scope.LIBRARY)
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
            mSpass?.isFeatureEnabled(Spass.DEVICE_FINGERPRINT)
            mSpassFingerprint?.hasRegisteredFinger()
        } catch (e: Throwable) {
            if (DEBUG_MANAGERS)
                e(e, name)
            mSpass = null
            mSpassFingerprint = null
        }
        listener?.initFinished(biometricMethod, this@SamsungFingerprintModule)
    }
    override fun getManagers(): Set<Any> {
        val managers = HashSet<Any>()
        mSpassFingerprint?.let {
            managers.add(it)
        }
        return managers
    }
    override fun getIds(manager: Any): List<String> {
        val ids = ArrayList<String>()
        mSpassFingerprint?.let {
            it.registeredFingerprintUniqueID?.let {  array->
                for(i in 0 until array.size()) {
                    //Sparsearray contains String
                    (array.get(i) as? String)?.let { s->
                        ids.add(s)
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
                    e(e, name)
                }

            return false
        }

    override fun hasEnrolled(): Boolean {

            try {
                if (mSpassFingerprint?.hasRegisteredFinger() == true) {
                    return true
                }
            } catch (e: Throwable) {
                e(e, name)
            }

        return false
    }

    @Throws(SecurityException::class)
    override fun authenticate(
        cancellationSignal: CancellationSignal?,
        listener: AuthenticationListener?,
        restartPredicate: RestartPredicate?
    ) {
        d("$name.authenticate - $biometricMethod")
        mSpassFingerprint?.let {
            try {
                cancelFingerprintRequest()
                it.startIdentify(object : SpassFingerprint.IdentifyListener {
                    override fun onFinished(status: Int) {
                        when (status) {
                            SpassFingerprint.STATUS_AUTHENTIFICATION_SUCCESS, SpassFingerprint.STATUS_AUTHENTIFICATION_PASSWORD_SUCCESS -> {
                                listener?.onSuccess(tag())
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
                            SpassFingerprint.STATUS_USER_CANCELLED -> {
                            }
                            else -> fail(AuthenticationFailureReason.UNKNOWN)
                        }
                    }

                    private fun fail(reason: AuthenticationFailureReason) {
                        var failureReason: AuthenticationFailureReason? = reason
                        if(restartCauseTimeout(failureReason)){
                            authenticate(cancellationSignal, listener, restartPredicate)
                        }
                        else
                        if (restartPredicate?.invoke(failureReason) == true) {
                            listener?.onFailure(failureReason, tag())
                            authenticate(cancellationSignal, listener, restartPredicate)
                        } else {
                            when (failureReason) {
                                AuthenticationFailureReason.SENSOR_FAILED, AuthenticationFailureReason.AUTHENTICATION_FAILED -> {
                                    lockout()
                                    failureReason = AuthenticationFailureReason.LOCKED_OUT
                                }
                            }
                            listener?.onFailure(failureReason, tag())
                        }
                    }

                    override fun onReady() {}
                    override fun onStarted() {}
                    override fun onCompleted() {}
                })
                cancellationSignal?.setOnCancelListener { cancelFingerprintRequest() }
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

    fun openSettings(context: Context?): Boolean {
        return try {
            mSpassFingerprint?.registerFinger(context) { }
            true
        } catch (e: Exception) {
            e(e, name)
            false
        }
    }


}