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

package dev.skomlach.biometric.compat.engine.internal.face.facelock

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.pm.PackageManager
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import androidx.core.os.CancellationSignal
import dev.skomlach.biometric.compat.AuthenticationFailureReason
import dev.skomlach.biometric.compat.BiometricCryptoObject
import dev.skomlach.biometric.compat.engine.BiometricInitListener
import dev.skomlach.biometric.compat.engine.BiometricMethod
import dev.skomlach.biometric.compat.engine.core.Core
import dev.skomlach.biometric.compat.engine.core.interfaces.AuthenticationListener
import dev.skomlach.biometric.compat.engine.core.interfaces.RestartPredicate
import dev.skomlach.biometric.compat.engine.internal.AbstractBiometricModule
import dev.skomlach.biometric.compat.utils.LockType.isBiometricWeakEnabled
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.d
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.e
import dev.skomlach.common.misc.ExecutorHelper
import java.lang.ref.WeakReference


class FacelockOldModule(private var listener: BiometricInitListener?) :
    AbstractBiometricModule(BiometricMethod.FACELOCK) {
    private var faceLockHelper: FaceLockHelper? = null
    private var facelockProxyListener: ProxyListener? = null
    private var viewWeakReference = WeakReference<SurfaceView?>(null)
    override var isManagerAccessible = false


    init {
        val faceLockInterface: FaceLockInterface = object : FaceLockInterface {
            override fun onError(code: Int, msg: String) {
                d("$name:FaceIdInterface.onError $code $msg")
                if (facelockProxyListener != null) {
                    facelockProxyListener?.onAuthenticationError(code, msg)
                }
            }

            override fun onAuthorized() {
                d("$name.FaceIdInterface.onAuthorized")
                if (facelockProxyListener != null) {
                    facelockProxyListener?.onAuthenticationSucceeded(null)
                }
            }

            override fun onConnected() {
                d("$name.FaceIdInterface.onConnected")
                if (facelockProxyListener != null) {
                    facelockProxyListener?.onAuthenticationAcquired(0)
                }
                if (listener != null) {
                    isManagerAccessible = true
                    listener?.initFinished(biometricMethod, this@FacelockOldModule)
                    listener = null
                    faceLockHelper?.stopFaceLock()
                } else {
                    try {
                        d(name + ".authorize: " + viewWeakReference.get())
                        viewWeakReference.get()?.let { view ->
                            if (view.visibility == View.VISIBLE || view.holder.isCreating) {
                                authCallTimestamp.set(System.currentTimeMillis())
                                faceLockHelper?.startFaceLockWithUi(view)
                                return
                            } else {
                                view.holder.addCallback(object : SurfaceHolder.Callback {
                                    override fun surfaceCreated(p0: SurfaceHolder) {
                                        authCallTimestamp.set(System.currentTimeMillis())
                                        faceLockHelper?.startFaceLockWithUi(view)
                                    }

                                    override fun surfaceChanged(
                                        p0: SurfaceHolder,
                                        p1: Int,
                                        p2: Int,
                                        p3: Int
                                    ) {

                                    }

                                    override fun surfaceDestroyed(p0: SurfaceHolder) {

                                    }
                                })
                                view.visibility = View.VISIBLE
                            }

                        } ?: kotlin.run {
                            authCallTimestamp.set(System.currentTimeMillis())
                            faceLockHelper?.startFaceLockWithUi(null)
                        }

                    } catch (e: Throwable) {
                        e("$name.FaceIdInterface.onConnected", e)
                    }
                }
            }

            override fun onDisconnected() {
                d("$name.FaceIdInterface.onDisconnected")
                if (facelockProxyListener != null) {
                    facelockProxyListener?.onAuthenticationError(
                        FaceLockHelper.FACELOCK_CANCELED,
                        FaceLockHelper.getMessage(FaceLockHelper.FACELOCK_CANCELED)
                    )
                }
                if (listener != null) {
                    listener?.initFinished(biometricMethod, this@FacelockOldModule)
                    listener = null
                    faceLockHelper?.stopFaceLock()
                }
            }
        }
        faceLockHelper = FaceLockHelper(faceLockInterface)
        if (!isHardwarePresent) {
            if (listener != null) {
                listener?.initFinished(biometricMethod, this@FacelockOldModule)
                listener = null
            }
        } else {
            faceLockHelper?.initFacelock()
        }
    }

    override val isUserAuthCanByUsedWithCrypto: Boolean
        get() = false

    fun stopAuth() {
        faceLockHelper?.stopFaceLock()
        faceLockHelper?.destroy()
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
            return faceLockHelper?.faceUnlockAvailable() == true
        }

    override val hasEnrolled: Boolean
        get() {
            return isBiometricWeakEnabled("com.android.facelock", context)
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

    fun setCallerView(targetView: SurfaceView?) {
        d("$name.setCallerView: $targetView")
        viewWeakReference = WeakReference(targetView)
    }

    private fun authorize(proxyListener: ProxyListener) {
        facelockProxyListener = proxyListener
        faceLockHelper?.stopFaceLock()
        faceLockHelper?.initFacelock()
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
        fun onAuthenticationError(errMsgId: Int, errString: CharSequence?): Void? {
            d("$name.onAuthenticationError: $errMsgId-$errString")
            val tmp = System.currentTimeMillis()
            if (tmp - errorTs <= skipTimeout || tmp - authCallTimestamp.get() <= skipTimeout)
                return null
            errorTs = tmp
            var failureReason = AuthenticationFailureReason.UNKNOWN
            when (errMsgId) {
                FaceLockHelper.FACELOCK_FAILED_ATTEMPT -> failureReason =
                    AuthenticationFailureReason.AUTHENTICATION_FAILED

                FaceLockHelper.FACELOCK_TIMEOUT -> failureReason =
                    AuthenticationFailureReason.TIMEOUT

                FaceLockHelper.FACELOCK_NO_FACE_FOUND -> failureReason =
                    AuthenticationFailureReason.HARDWARE_UNAVAILABLE

                FaceLockHelper.FACELOCK_NOT_SETUP -> failureReason =
                    AuthenticationFailureReason.NO_BIOMETRICS_REGISTERED

                FaceLockHelper.FACELOCK_CANNT_START, FaceLockHelper.FACELOCK_UNABLE_TO_BIND, FaceLockHelper.FACELOCK_API_NOT_FOUND -> failureReason =
                    AuthenticationFailureReason.HARDWARE_UNAVAILABLE

                else -> {
                    if (!selfCanceled) {
                        listener?.onFailure(failureReason, tag())
                        postCancelTask {
                            if (cancellationSignal?.isCanceled == false) {
                                selfCanceled = true
                                listener?.onCanceled(tag())
                                Core.cancelAuthentication(this@FacelockOldModule)
                            }
                        }
                    }
                    return null
                }
            }
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
                            Core.cancelAuthentication(this@FacelockOldModule)
                        }
                    }
                }
            return null
        }

        fun onAuthenticationHelp(helpMsgId: Int, helpString: CharSequence?): Void? {
            d("$name.onAuthenticationHelp: $helpMsgId-$helpString")
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