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

package dev.skomlach.biometric.compat.engine.internal.face.facelock

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.pm.PackageManager
import android.view.View
import androidx.annotation.RestrictTo
import androidx.core.os.CancellationSignal
import dev.skomlach.biometric.compat.engine.AuthenticationFailureReason
import dev.skomlach.biometric.compat.engine.BiometricCodes
import dev.skomlach.biometric.compat.engine.BiometricInitListener
import dev.skomlach.biometric.compat.engine.BiometricMethod
import dev.skomlach.biometric.compat.engine.core.Core.cancelAuthentication
import dev.skomlach.biometric.compat.engine.core.interfaces.AuthenticationListener
import dev.skomlach.biometric.compat.engine.core.interfaces.RestartPredicate
import dev.skomlach.biometric.compat.engine.internal.AbstractBiometricModule
import dev.skomlach.biometric.compat.utils.BiometricErrorLockoutPermanentFix
import dev.skomlach.biometric.compat.utils.LockType.isBiometricWeakEnabled
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.d
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.e
import java.lang.ref.WeakReference

@RestrictTo(RestrictTo.Scope.LIBRARY)
class FacelockOldModule(private var listener: BiometricInitListener?) :
    AbstractBiometricModule(BiometricMethod.FACELOCK) {
    private var faceLockHelper: FaceLockHelper? = null
    private var facelockProxyListener: ProxyListener? = null
    private var viewWeakReference = WeakReference<View?>(null)
    override var isManagerAccessible = false

    companion object {
        const val BIOMETRIC_AUTHENTICATION_FAILED = 1001
    }

    init {
        val faceLockInterface: FaceLockInterface = object : FaceLockInterface {
            override fun onError(code: Int, msg: String) {
                d("$name:FaceIdInterface.onError $code $msg")
                if (facelockProxyListener != null) {
                    var failureReason = BiometricCodes.BIOMETRIC_ERROR_CANCELED
                    when (code) {
                        FaceLockHelper.FACELOCK_FAILED_ATTEMPT -> failureReason =
                            BIOMETRIC_AUTHENTICATION_FAILED
                        FaceLockHelper.FACELOCK_TIMEOUT -> failureReason =
                            BiometricCodes.BIOMETRIC_ERROR_TIMEOUT
                        FaceLockHelper.FACELOCK_NO_FACE_FOUND -> failureReason =
                            BiometricCodes.BIOMETRIC_ERROR_UNABLE_TO_PROCESS
                        FaceLockHelper.FACELOCK_NOT_SETUP -> failureReason =
                            BiometricCodes.BIOMETRIC_ERROR_NO_BIOMETRICS
                        FaceLockHelper.FACELOCK_CANCELED -> failureReason =
                            BiometricCodes.BIOMETRIC_ERROR_CANCELED
                        FaceLockHelper.FACELOCK_CANNT_START, FaceLockHelper.FACELOCK_UNABLE_TO_BIND, FaceLockHelper.FACELOCK_API_NOT_FOUND -> failureReason =
                            BiometricCodes.BIOMETRIC_ERROR_HW_UNAVAILABLE
                    }
                    facelockProxyListener?.onAuthenticationError(failureReason, msg)
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
                    d(name + ".authorize: " + viewWeakReference.get())
                    faceLockHelper?.startFaceLockWithUi(viewWeakReference.get())
                }
            }

            override fun onDisconnected() {
                d("$name.FaceIdInterface.onDisconnected")
                if (facelockProxyListener != null) {
                    facelockProxyListener?.onAuthenticationError(
                        BiometricCodes.BIOMETRIC_ERROR_CANCELED,
                        FaceLockHelper.getMessage(BiometricCodes.BIOMETRIC_ERROR_CANCELED)
                    )
                }
                if (listener != null) {
                    listener?.initFinished(biometricMethod, this@FacelockOldModule)
                    listener = null
                    faceLockHelper?.stopFaceLock()
                }
            }
        }
        faceLockHelper = FaceLockHelper(context, faceLockInterface)
        if (!isHardwarePresent) {
            if (listener != null) {
                listener?.initFinished(biometricMethod, this@FacelockOldModule)
                listener = null
            }
        } else {
            faceLockHelper?.initFacelock()
        }
    }

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
            // Retrieve all services that can match the given intent
            if (faceLockHelper?.faceUnlockAvailable() == false) return false
            val pm = context.packageManager
            if (!pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)) {
                return false
            }
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            return if (dpm.getCameraDisabled(null)) {
                false
            } else hasEnrolled()
        }

    @Throws(SecurityException::class)
    override fun hasEnrolled(): Boolean {
        return isBiometricWeakEnabled(context)
    }

    @Throws(SecurityException::class)
    override fun authenticate(
        cancellationSignal: CancellationSignal?,
        listener: AuthenticationListener?,
        restartPredicate: RestartPredicate?
    ) {
        try {
            d("$name: Facelock call authorize")
            authorize(ProxyListener(restartPredicate, cancellationSignal, listener))
            return
        } catch (e: Throwable) {
            e(e, "$name: authenticate failed unexpectedly")
        }
        listener?.onFailure(
            AuthenticationFailureReason.UNKNOWN,
            tag()
        )
    }

    fun setCallerView(targetView: View?) {
        d("$name.setCallerView: $targetView")
        viewWeakReference = WeakReference(targetView)
    }

    private fun authorize(proxyListener: ProxyListener) {
        facelockProxyListener = proxyListener
        faceLockHelper?.stopFaceLock()
        faceLockHelper?.initFacelock()
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    inner class ProxyListener(
        private val restartPredicate: RestartPredicate?,
        private val cancellationSignal: CancellationSignal?,
        private val listener: AuthenticationListener?
    ) {
        fun onAuthenticationError(errMsgId: Int, errString: CharSequence?): Void? {
            var failureReason = AuthenticationFailureReason.UNKNOWN
            when (errMsgId) {
                BiometricCodes.BIOMETRIC_ERROR_NO_BIOMETRICS -> failureReason =
                    AuthenticationFailureReason.NO_BIOMETRICS_REGISTERED
                BIOMETRIC_AUTHENTICATION_FAILED -> failureReason =
                    AuthenticationFailureReason.AUTHENTICATION_FAILED
                BiometricCodes.BIOMETRIC_ERROR_HW_NOT_PRESENT -> failureReason =
                    AuthenticationFailureReason.NO_HARDWARE
                BiometricCodes.BIOMETRIC_ERROR_HW_UNAVAILABLE -> failureReason =
                    AuthenticationFailureReason.HARDWARE_UNAVAILABLE
                BiometricCodes.BIOMETRIC_ERROR_LOCKOUT_PERMANENT -> {
                    BiometricErrorLockoutPermanentFix.setBiometricSensorPermanentlyLocked(
                        biometricMethod.biometricType
                    )
                    failureReason = AuthenticationFailureReason.HARDWARE_UNAVAILABLE
                }
                BiometricCodes.BIOMETRIC_ERROR_UNABLE_TO_PROCESS -> failureReason =
                    AuthenticationFailureReason.HARDWARE_UNAVAILABLE
                BiometricCodes.BIOMETRIC_ERROR_NO_SPACE -> failureReason =
                    AuthenticationFailureReason.SENSOR_FAILED
                BiometricCodes.BIOMETRIC_ERROR_TIMEOUT -> failureReason =
                    AuthenticationFailureReason.TIMEOUT
                BiometricCodes.BIOMETRIC_ERROR_LOCKOUT -> {
                    lockout()
                    failureReason = AuthenticationFailureReason.LOCKED_OUT
                }
                BiometricCodes.BIOMETRIC_ERROR_USER_CANCELED -> {
                    cancelAuthentication(this@FacelockOldModule)
                    return null
                }
                BiometricCodes.BIOMETRIC_ERROR_CANCELED ->                     // Don't send a cancelled message.
                    return null
            }
            if (restartCauseTimeout(failureReason)) {
                authenticate(cancellationSignal, listener, restartPredicate)
            } else
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
            return null
        }

        fun onAuthenticationHelp(helpMsgId: Int, helpString: CharSequence?): Void? {
            return null
        }

        fun onAuthenticationSucceeded(result: Any?): Void? {
            listener?.onSuccess(tag())
            return null
        }

        fun onAuthenticationAcquired(acquireInfo: Int): Void? {
            d("$name.FaceIdInterface.ProxyListener $acquireInfo")
            return null
        }

        fun onAuthenticationFailed(): Void? {
            listener?.onFailure(
                AuthenticationFailureReason.AUTHENTICATION_FAILED,
                tag()
            )
            return null
        }
    }
}