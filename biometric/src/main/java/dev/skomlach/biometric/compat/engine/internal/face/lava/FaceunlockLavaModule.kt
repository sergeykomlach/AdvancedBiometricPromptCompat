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

package dev.skomlach.biometric.compat.engine.internal.face.lava

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.os.CancellationSignal
import dev.skomlach.biometric.compat.AuthenticationFailureReason
import dev.skomlach.biometric.compat.BiometricCryptoObject
import dev.skomlach.biometric.compat.engine.BiometricInitListener
import dev.skomlach.biometric.compat.engine.BiometricMethod
import dev.skomlach.biometric.compat.engine.core.Core
import dev.skomlach.biometric.compat.engine.core.interfaces.AuthenticationListener
import dev.skomlach.biometric.compat.engine.core.interfaces.RestartPredicate
import dev.skomlach.biometric.compat.engine.internal.AbstractBiometricModule
import dev.skomlach.biometric.compat.utils.LockType
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.d
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.e
import dev.skomlach.common.misc.ExecutorHelper


class FaceunlockLavaModule(private var listener: BiometricInitListener?) :
    AbstractBiometricModule(BiometricMethod.FACEUNLOCK_LAVA) {
    private var faceLockHelper: FaceVerifyManager? = null
    private var facelockProxyListener: ProxyListener? = null
    override var isManagerAccessible = false

    init {
        val faceLockInterface: FaceUnlockCallback = object : FaceUnlockCallback {
            override fun onFaceVerifyChanged(resultCode: Int, msg: String?) {
                if (resultCode == 1) {
                    facelockProxyListener?.onAuthenticationSucceeded(null)
                } else {
                    facelockProxyListener?.onAuthenticationError()
                }
            }
        }
        faceLockHelper = FaceVerifyManager(context)
        faceLockHelper?.setFaceUnlockCallback(faceLockInterface)
        if (!isHardwarePresent) {
            if (listener != null) {
                listener?.initFinished(biometricMethod, this@FaceunlockLavaModule)
                listener = null
            }
        } else {
            faceLockHelper?.bindFaceVerifyService()
        }
    }

    override val isUserAuthCanByUsedWithCrypto: Boolean
        get() = false

    fun stopAuth() {
        faceLockHelper?.stopFaceVerify()
        faceLockHelper?.setFaceUnlockCallback(null)
    }

    override fun getManagers(): Set<Any> {
        //No way to detect enrollments
        return emptySet()
    }

    // Retrieve all services that can match the given intent
    override val isHardwarePresent: Boolean
        get() {
            val pm = context.packageManager
            if (!pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)) {
                return false
            }
            val dpm =
                context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager?
            if (dpm?.getCameraDisabled(null) == true)
                return false

            // Retrieve all services that can match the given intent
            return faceLockHelper?.isFaceUnlockOn == true
        }

    override val hasEnrolled: Boolean
        get() {
            return LockType.isBiometricWeakEnabled("com.prize.faceunlock", context)
        }

    @Throws(SecurityException::class)
    override fun authenticate(
        biometricCryptoObject: BiometricCryptoObject?,
        cancellationSignal: CancellationSignal?,
        listener: AuthenticationListener?,
        restartPredicate: RestartPredicate?
    ) {
        d("$name.authenticate - $biometricMethod; Crypto=$biometricCryptoObject")
        try {
            d("$name: Facelock call authorize")
            cancellationSignal?.setOnCancelListener {
                stopAuth()
            }
            authorize(
                ProxyListener(
                    biometricCryptoObject,
                    restartPredicate,
                    cancellationSignal,
                    listener
                )
            )
            return
        } catch (e: Throwable) {
            e(e, "$name: authenticate failed unexpectedly")
        }
        listener?.onFailure(
            AuthenticationFailureReason.UNKNOWN,
            tag()
        )
    }

    private fun authorize(proxyListener: ProxyListener) {
        facelockProxyListener = proxyListener
        authCallTimestamp.set(System.currentTimeMillis())
        faceLockHelper?.stopFaceVerify()
        faceLockHelper?.startFaceVerify()
    }


    inner class ProxyListener(
        private val biometricCryptoObject: BiometricCryptoObject?,
        private val restartPredicate: RestartPredicate?,
        private val cancellationSignal: CancellationSignal?,
        private val listener: AuthenticationListener?
    ) {
        private var errorTs = System.currentTimeMillis()
        private val skipTimeout =
            context.resources.getInteger(android.R.integer.config_shortAnimTime)
        private var selfCanceled = false
        fun onAuthenticationError(): Void? {
            d("$name.onAuthenticationError")
            val tmp = System.currentTimeMillis()
            if (tmp - errorTs <= skipTimeout || tmp - authCallTimestamp.get() <= skipTimeout)
                return null
            errorTs = tmp
            var failureReason = AuthenticationFailureReason.AUTHENTICATION_FAILED

            if (restartCauseTimeout(failureReason)) {
                selfCanceled = true
                stopAuth()
                ExecutorHelper.postDelayed({
                    authenticate(
                        biometricCryptoObject,
                        cancellationSignal,
                        listener,
                        restartPredicate
                    )
                }, skipTimeout.toLong())
            } else
                if (failureReason == AuthenticationFailureReason.TIMEOUT || restartPredicate?.invoke(
                        failureReason
                    ) == true
                ) {
                    listener?.onFailure(failureReason, tag())
                    selfCanceled = true
                    stopAuth()
                    ExecutorHelper.postDelayed({
                        authenticate(
                            biometricCryptoObject,
                            cancellationSignal,
                            listener,
                            restartPredicate
                        )
                    }, 1000)
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
                    postCancelTask {
                        if (cancellationSignal?.isCanceled == false) {
                            selfCanceled = true
                            listener?.onCanceled(tag())
                        }
                        Core.cancelAuthentication(this@FaceunlockLavaModule)
                    }
                }
            return null
        }

        fun onAuthenticationHelp(helpMsgId: Int, helpString: CharSequence?): Void? {
            d("$name.onAuthenticationError: $helpMsgId-$helpString")
            return null
        }

        fun onAuthenticationSucceeded(result: Any?): Void? {
            d("$name.onAuthenticationSucceeded $result")
            val tmp = System.currentTimeMillis()
            if (tmp - errorTs <= skipTimeout || tmp - authCallTimestamp.get() <= skipTimeout)
                return null
            errorTs = tmp
            listener?.onSuccess(
                tag(),
                BiometricCryptoObject(
                    biometricCryptoObject?.signature,
                    biometricCryptoObject?.cipher,
                    biometricCryptoObject?.mac
                )
            )
            return null
        }

        fun onAuthenticationAcquired(acquireInfo: Int): Void? {
            d("$name.onAuthenticationAcquired $acquireInfo")
            return null
        }

        fun onAuthenticationFailed(): Void? {
            d("$name.onAuthenticationFailed")
            val tmp = System.currentTimeMillis()
            if (tmp - errorTs <= skipTimeout || tmp - authCallTimestamp.get() <= skipTimeout)
                return null
            errorTs = tmp
            listener?.onFailure(
                AuthenticationFailureReason.AUTHENTICATION_FAILED,
                tag()
            )
            return null
        }
    }
}