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

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.graphics.Rect
import android.os.IBinder
import android.os.PowerManager
import android.os.RemoteException
import android.view.View
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.d
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.e
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.locks.ReentrantLock


class FaceLockHelper(context: Context, faceLockInterface: FaceLockInterface) {
    private val faceLockInterface: FaceLockInterface
    private val context: Context? = null
    private var targetView: View? = null
    private var mFaceLock: FaceLock? = null
    private var mFaceLockServiceRunning = false
    private var mBoundToFaceLockService = false
    private var mCallback: IFaceLockCallback? = null
    private var mServiceConnection: ServiceConnection? = null
    private val hasHardware: Boolean
    private val lock = ReentrantLock()

    companion object {
        const val FACELOCK_UNABLE_TO_BIND = 1
        const val FACELOCK_API_NOT_FOUND = 2
        const val FACELOCK_CANNT_START = 3
        const val FACELOCK_NOT_SETUP = 4
        const val FACELOCK_CANCELED = 5
        const val FACELOCK_NO_FACE_FOUND = 6
        const val FACELOCK_FAILED_ATTEMPT = 7
        const val FACELOCK_TIMEOUT = 8
        protected val TAG = FaceLockHelper::class.java.simpleName
        fun getMessage(code: Int): String {
            return when (code) {
                FACELOCK_UNABLE_TO_BIND -> "Unable to bind to FaceId"
                FACELOCK_API_NOT_FOUND -> TAG + ". not found"
                FACELOCK_CANNT_START -> "Can not start FaceId"
                FACELOCK_NOT_SETUP -> TAG + ". not set up"
                FACELOCK_CANCELED -> TAG + ". canceled"
                FACELOCK_NO_FACE_FOUND -> "No face found"
                FACELOCK_FAILED_ATTEMPT -> "Failed attempt"
                FACELOCK_TIMEOUT -> "Timeout"
                else -> "Unknown error ($code)"
            }
        }
    }

    init {
        this.faceLockInterface = faceLockInterface
        mFaceLock = try {
            FaceLock(context)
        } catch (e: Throwable) {
            null
        }
        hasHardware = mFaceLock != null
    }

    fun faceUnlockAvailable(): Boolean {
        return hasHardware
    }


    fun destroy() {
        try {
            lock.runCatching { this.lock() }
            targetView = null
            mCallback = null
            mServiceConnection = null
        } finally {
            lock.runCatching {
                this.unlock()
            }
        }
    }


    fun initFacelock() {
        try {
            lock.runCatching { this.lock() }
            d(TAG + ".initFacelock")
            try {
                mCallback = object : IFaceLockCallback {
                    private var mStarted = false

                    @Throws(RemoteException::class)
                    override fun unlock() {
                        d(TAG + ".IFaceIdCallback.unlock")
                        stopFaceLock()
                        d(TAG + ".IFaceIdCallback.exec onAuthorized")
                        faceLockInterface.onAuthorized()
                        mStarted = false
                    }

                    @Throws(RemoteException::class)
                    override fun cancel() {
                        d(TAG + ".IFaceIdCallback.cancel")
                        if (mBoundToFaceLockService) {
                            mStarted = if (mStarted) {
                                d(TAG + ".timeout")
                                faceLockInterface.onError(
                                    FACELOCK_TIMEOUT,
                                    getMessage(FACELOCK_TIMEOUT)
                                )
                                stopFaceLock()
                                false
                            } else {
                                d(TAG + ".canceled")
                                faceLockInterface.onError(
                                    FACELOCK_CANCELED,
                                    getMessage(FACELOCK_CANCELED)
                                )
                                stopFaceLock()
                                false
                            }
                        }
                    }

                    @Throws(RemoteException::class)
                    override fun reportFailedAttempt() {
                        d(TAG + ".IFaceIdCallback.reportFailedAttempt")
                        faceLockInterface.onError(
                            FACELOCK_FAILED_ATTEMPT, getMessage(
                                FACELOCK_FAILED_ATTEMPT
                            )
                        )
                    }

                    @Throws(RemoteException::class)
                    override fun exposeFallback() {
                        d(TAG + ".IFaceIdCallback.exposeFallback")
                        mStarted = true
                    }

                    @Throws(RemoteException::class)
                    override fun pokeWakelock() {
                        d(TAG + ".IFaceIdCallback.pokeWakelock")
                        mStarted = true
                        try {
                            val pm =
                                context?.getSystemService(Context.POWER_SERVICE) as PowerManager?
                            val screenLock = pm
                                ?.newWakeLock(
                                    PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.SCREEN_DIM_WAKE_LOCK,
                                    javaClass.name
                                )
                            screenLock?.acquire(25000L)
                            if (screenLock?.isHeld == true) {
                                screenLock.release()
                            }
                        } catch (e: Throwable) {
                            e(e, TAG)
                        }
                    }
                }
                mServiceConnection = object : ServiceConnection {
                    override fun onServiceDisconnected(name: ComponentName) {
                        d(TAG + ".ServiceConnection.onServiceDisconnected")
                        try {
                            mCallback?.let {
                                mFaceLock?.unregisterCallback(it)
                            }
                        } catch (e: Exception) {
                            if (e is InvocationTargetException) {
                                e(
                                    e, TAG + ("Caught invocation exception registering callback: "
                                            + e
                                        .targetException)
                                )
                            } else {
                                e(e, TAG + "Caught exception registering callback: $e")
                            }
                        }
                        mFaceLockServiceRunning = false
                        mBoundToFaceLockService = false
                        faceLockInterface.onDisconnected()
                    }

                    override fun onServiceConnected(name: ComponentName, service: IBinder) {
                        d(TAG + ".ServiceConnection.onServiceConnected")
                        mBoundToFaceLockService = true
                        try {
                            mCallback?.let {
                                mFaceLock?.registerCallback(it)
                            }
                        } catch (e: Exception) {
                            if (e is InvocationTargetException) {
                                e(
                                    e, TAG + ("Caught invocation exception registering callback: "
                                            + e
                                        .targetException)
                                )
                            } else {
                                e(e, TAG + "Caught exception registering callback: $e")
                            }
                            mBoundToFaceLockService = false
                        }
                        faceLockInterface.onConnected()
                    }
                }
                if (!mBoundToFaceLockService) {
                    mServiceConnection?.let {
                        if (mFaceLock?.bind(it) == false) {
                            faceLockInterface
                                .onError(
                                    FACELOCK_UNABLE_TO_BIND,
                                    getMessage(FACELOCK_UNABLE_TO_BIND)
                                )
                        } else {
                            d(TAG + ".Binded, waiting for connection")
                            return
                        }
                    }
                } else {
                    d(TAG + ".Already mBoundToFaceLockService")
                }
            } catch (e: Exception) {
                e(e, TAG + "Caught exception creating FaceId: $e")
                faceLockInterface.onError(
                    FACELOCK_API_NOT_FOUND,
                    getMessage(FACELOCK_API_NOT_FOUND)
                )
            }
            d(TAG + ".init failed")
        } finally {
            lock.runCatching {
                this.unlock()
            }
        }
    }

    // Tells the FaceId service to stop displaying its UI and stop recognition

    fun stopFaceLock() {
        try {
            lock.runCatching { this.lock() }
            d(TAG + ".stopFaceLock")
            if (mFaceLockServiceRunning) {
                try {
                    d(TAG + ".Stopping FaceId")
                    mFaceLock?.stopUi()
                } catch (e: Exception) {
                    e(e, TAG + "Caught exception stopping FaceId: $e")
                }
                mFaceLockServiceRunning = false
            }
            if (mBoundToFaceLockService) {
                mFaceLock?.unbind()
                d(TAG + ".FaceId.unbind()")
                mBoundToFaceLockService = false
            }
        } finally {
            lock.runCatching {
                this.unlock()
            }
        }
    }

    // Tells the FaceId service to start displaying its UI and perform recognition
    private fun startFaceAuth(targetView: View) {
        d(TAG + ".startFaceLock")
        if (!mFaceLockServiceRunning) {
            try {
                d(TAG + ".Starting FaceId")
                val rect = Rect()
                targetView.getGlobalVisibleRect(rect)
                d(TAG + " rect: $rect")
                mFaceLock?.startUi(
                    targetView.windowToken,
                    rect.left, rect.top,
                    rect.width(), rect.height()
                )
            } catch (e: Exception) {
                e(e, TAG + ("Caught exception starting FaceId: " + e.message))
                faceLockInterface.onError(FACELOCK_CANNT_START, getMessage(FACELOCK_CANNT_START))
                return
            }
            mFaceLockServiceRunning = true
        } else {
            e(TAG + ".startFaceLock() attempted while running")
        }
    }


    fun startFaceLockWithUi(view: View?) {
        try {
            lock.runCatching { this.lock() }
            d(TAG + ".startFaceLockWithUi")
            targetView = view
            targetView?.let {
                startFaceAuth(it)
            }
        } finally {
            lock.runCatching {
                this.unlock()
            }
        }
    }
}