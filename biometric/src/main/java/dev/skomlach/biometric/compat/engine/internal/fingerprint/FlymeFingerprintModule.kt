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

import android.os.IBinder
import androidx.core.os.CancellationSignal
import com.fingerprints.service.FingerprintManager
import com.fingerprints.service.FingerprintManager.IdentifyCallback
import dev.skomlach.biometric.compat.AuthenticationFailureReason
import dev.skomlach.biometric.compat.BiometricCryptoObject
import dev.skomlach.biometric.compat.engine.BiometricInitListener
import dev.skomlach.biometric.compat.engine.BiometricMethod
import dev.skomlach.biometric.compat.engine.core.interfaces.AuthenticationListener
import dev.skomlach.biometric.compat.engine.core.interfaces.RestartPredicate
import dev.skomlach.biometric.compat.engine.internal.AbstractBiometricModule
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.d
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.e


class FlymeFingerprintModule(listener: BiometricInitListener?) :
    AbstractBiometricModule(BiometricMethod.FINGERPRINT_FLYME) {
    private var mFingerprintServiceFingerprintManager: FingerprintManager? = null

    init {
        try {
            val servicemanager = Class.forName("android.os.ServiceManager")
            val getService = servicemanager.getMethod("getService", String::class.java)
            val binder = getService.invoke(null, "fingerprints_service") as IBinder?
            binder?.let {
                mFingerprintServiceFingerprintManager = FingerprintManager.open()
                isManagerAccessible =
                    mFingerprintServiceFingerprintManager != null && mFingerprintServiceFingerprintManager?.isSurpport == true
            }
        } catch (e: Throwable) {
            if (DEBUG_MANAGERS)
                e(e, name)
        } finally {
            cancelFingerprintServiceFingerprintRequest()
        }
        listener?.initFinished(biometricMethod, this@FlymeFingerprintModule)
    }

    override fun getManagers(): Set<Any> {
        val managers = HashSet<Any>()
        mFingerprintServiceFingerprintManager?.let {
            managers.add(it)
        }
        return managers
    }

    override fun getIds(manager: Any): List<String> {
        val ids = ArrayList<String>()
        mFingerprintServiceFingerprintManager?.let {
            it.ids?.let { array ->
                for (a in array)
                    ids.add("$a")
            }
        }
        return ids
    }

    override var isManagerAccessible = false
    override val isHardwarePresent: Boolean
        get() {
            if (isManagerAccessible) {
                try {
                    mFingerprintServiceFingerprintManager = FingerprintManager.open()
                    return mFingerprintServiceFingerprintManager
                        ?.isFingerEnable == true
                } catch (e: Throwable) {
                    e(e, name)
                } finally {
                    cancelFingerprintServiceFingerprintRequest()
                }
            }
            return false
        }

    override fun hasEnrolled(): Boolean {
        if (isManagerAccessible) {
            try {
                mFingerprintServiceFingerprintManager = FingerprintManager.open()
                val fingerprintIds = mFingerprintServiceFingerprintManager?.ids
                return fingerprintIds?.isNotEmpty() == true
            } catch (e: Throwable) {
                e(e, name)
            } finally {
                cancelFingerprintServiceFingerprintRequest()
            }
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
        d("$name.authenticate - $biometricMethod")
        if (isManagerAccessible) {
            try {
                cancelFingerprintServiceFingerprintRequest()
                mFingerprintServiceFingerprintManager = FingerprintManager.open()
                mFingerprintServiceFingerprintManager
                    ?.startIdentify(object : IdentifyCallback {
                        override fun onIdentified(i: Int, b: Boolean) {
                            listener?.onSuccess(
                                tag(),
                                BiometricCryptoObject(
                                    biometricCryptoObject?.signature,
                                    biometricCryptoObject?.cipher,
                                    biometricCryptoObject?.mac
                                )
                            )
                            cancelFingerprintServiceFingerprintRequest()
                        }

                        override fun onNoMatch() {
                            fail(AuthenticationFailureReason.AUTHENTICATION_FAILED)
                        }

                        private fun fail(reason: AuthenticationFailureReason) {
                            var failureReason: AuthenticationFailureReason? = reason
                            if (restartCauseTimeout(failureReason)) {
                                authenticate(
                                    biometricCryptoObject,
                                    cancellationSignal,
                                    listener,
                                    restartPredicate
                                )
                            } else
                                if (restartPredicate?.invoke(failureReason) == true) {
                                    listener?.onFailure(failureReason, tag())
                                    authenticate(
                                        biometricCryptoObject,
                                        cancellationSignal,
                                        listener,
                                        restartPredicate
                                    )
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
                                    cancelFingerprintServiceFingerprintRequest()
                                }
                        }
                    }, mFingerprintServiceFingerprintManager?.ids)
                cancellationSignal?.setOnCancelListener { cancelFingerprintServiceFingerprintRequest() }
                return
            } catch (e: Throwable) {
                e(e, "$name: authenticate failed unexpectedly")
            }
        }
        listener?.onFailure(AuthenticationFailureReason.UNKNOWN, tag())
        cancelFingerprintServiceFingerprintRequest()
        return
    }

    private fun cancelFingerprintServiceFingerprintRequest() {
        try {

            mFingerprintServiceFingerprintManager?.abort()
            mFingerprintServiceFingerprintManager?.release()
            mFingerprintServiceFingerprintManager = null

        } catch (e: Throwable) {
            e(e, name)
            // There's no way to query if there's an active identify request,
            // so just try to cancel and ignore any exceptions.
        }
    }

}