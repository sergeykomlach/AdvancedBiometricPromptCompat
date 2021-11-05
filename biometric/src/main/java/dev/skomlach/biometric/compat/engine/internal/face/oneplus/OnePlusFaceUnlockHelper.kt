package dev.skomlach.biometric.compat.engine.internal.face.oneplus

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.d
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.e


class OnePlusFaceUnlockHelper constructor(
    context: Context,
    onePlusFaceUnlockInterface: FaceLockInterface
) {
    companion object {
        const val FACEUNLOCK_UNABLE_TO_BIND = -1
        const val FACEUNLOCK_API_NOT_FOUND = -2
        const val FACEUNLOCK_CANNT_START = -3
        const val FACEUNLOCK_TIMEOUT = 2
        const val FACEUNLOCK_CAMERA_ERROR = 3
        const val FACEUNLOCK_NO_PERMISSION = 4
        const val FACEUNLOCK_FAILED_ATTEMPT = 5
        private val TAG = OnePlusFaceUnlockHelper::class.java.simpleName
        fun getMessage(code: Int): String {
            return when (code) {
                FACEUNLOCK_UNABLE_TO_BIND -> "Unable to bind to OnePlusFaceUnlock"
                FACEUNLOCK_API_NOT_FOUND -> TAG + ". not found"
                FACEUNLOCK_CANNT_START -> "Can not start OnePlusFaceUnlock"
                FACEUNLOCK_CAMERA_ERROR -> TAG + " camera error"
                FACEUNLOCK_NO_PERMISSION -> TAG + " no permission"
                FACEUNLOCK_FAILED_ATTEMPT -> "Failed attempt"
                FACEUNLOCK_TIMEOUT -> "Timeout"
                else -> "Unknown error ($code)"
            }
        }
    }

    private val onePlusFaceUnlockInterface: FaceLockInterface = onePlusFaceUnlockInterface
    private val hasHardware: Boolean
    private var mOnePlusFaceUnlock: OnePlusFaceUnlock? = null
    private var mOnePlusFaceUnlockServiceRunning = false
    private var mBoundToOnePlusFaceUnlockService = false
    private var mCallback: IOPFacelockCallback? = null
    private var mServiceConnection: ServiceConnection? = null

    init {
        mOnePlusFaceUnlock = try {
            OnePlusFaceUnlock(context)
        } catch (e: Throwable) {
            null
        }
        hasHardware = mOnePlusFaceUnlock != null
    }

    fun faceUnlockAvailable(): Boolean {
        return hasHardware
    }

    @Synchronized
    fun destroy() {
        mCallback = null
        mServiceConnection = null
    }

    @Synchronized
    fun initFacelock() {
        d(TAG + ".initFacelock")
        try {
            mCallback = object : IOPFacelockCallback {
                @Throws(RemoteException::class)
                override fun onBeginRecognize(faceId: Int) {
                    d(TAG + ".IOnePlusFaceUnlockCallback.onBeginRecognize - " + faceId)
                }

                @Throws(RemoteException::class)
                override fun onCompared(
                    faceId: Int,
                    userId: Int,
                    result: Int,
                    compareTimeMillis: Int,
                    score: Int
                ) {
                    d(TAG + ".IOnePlusFaceUnlockCallback.onCompared - " + faceId + "/" + userId + "/" + result)
                    if (result != 0) {
                        onePlusFaceUnlockInterface.onError(result, getMessage(result))
                    }
                }

                @Throws(RemoteException::class)
                override fun onEndRecognize(faceId: Int, userId: Int, result: Int) {
                    d(TAG + ".IOnePlusFaceUnlockCallback.onEndRecognize - " + faceId + "/" + userId + "/" + result)
                    if (result == 0) {
                        stopFaceLock()
                        d(TAG + ".IOnePlusFaceUnlockCallback.exec onAuthorized")
                        onePlusFaceUnlockInterface.onAuthorized()
                    } else {
                        d(TAG + ".IOnePlusFaceUnlockCallback.unlock")
                        stopFaceLock()
                        d(TAG + ".IOnePlusFaceUnlockCallback.exec onError")
                        onePlusFaceUnlockInterface.onError(result, getMessage(result))
                    }
                }
            }
            mServiceConnection = object : ServiceConnection {
                override fun onServiceDisconnected(name: ComponentName) {
                    d(TAG + ".ServiceConnection.onServiceDisconnected")
                    mOnePlusFaceUnlockServiceRunning = false
                    mBoundToOnePlusFaceUnlockService = false
                    onePlusFaceUnlockInterface.onDisconnected()
                }

                override fun onServiceConnected(name: ComponentName, service: IBinder) {
                    d(TAG + ".ServiceConnection.onServiceConnected")
                    mBoundToOnePlusFaceUnlockService = true
                    onePlusFaceUnlockInterface.onConnected()
                }
            }
            if (!mBoundToOnePlusFaceUnlockService) {
                mServiceConnection?.let {
                    if (mOnePlusFaceUnlock?.bind(it) == false) {
                        onePlusFaceUnlockInterface
                            .onError(
                                FACEUNLOCK_UNABLE_TO_BIND,
                                getMessage(FACEUNLOCK_UNABLE_TO_BIND)
                            )
                    } else {
                        d(TAG + ".Binded, waiting for connection")
                        return
                    }
                }
            } else {
                d(TAG + ".Already mBoundToOnePlusFaceUnlockService")
            }
        } catch (e: Exception) {
            e(e, TAG + "Caught exception creating OnePlusFaceUnlock: $e")
            onePlusFaceUnlockInterface.onError(
                FACEUNLOCK_API_NOT_FOUND, getMessage(
                    FACEUNLOCK_API_NOT_FOUND
                )
            )
        }
        d(TAG + ".init failed")
    }

    // Tells the OnePlusFaceUnlock service to stop displaying its UI and stop recognition
    @Synchronized
    fun stopFaceLock() {
        d(TAG + ".stopFaceLock")
        if (mOnePlusFaceUnlockServiceRunning) {
            try {
                d(TAG + ".Stopping OnePlusFaceUnlock")
                mOnePlusFaceUnlock?.unregisterCallback(mCallback)
                mOnePlusFaceUnlock?.stopFaceUnlock()
            } catch (e: Exception) {
                e(e, TAG + "Caught exception stopping OnePlusFaceUnlock: $e")
            }
            mOnePlusFaceUnlockServiceRunning = false
        }
        if (mBoundToOnePlusFaceUnlockService) {
            mOnePlusFaceUnlock?.unbind()
            d(TAG + ".OnePlusFaceUnlock.unbind()")
            mBoundToOnePlusFaceUnlockService = false
        }
    }

    @Synchronized
    fun startFaceLock() {
        d(TAG + ".startOnePlusFaceUnlockWithUi")
        if (!mOnePlusFaceUnlockServiceRunning) {
            try {
                d(TAG + ".Starting OnePlusFaceUnlock")
                mOnePlusFaceUnlock?.registerCallback(mCallback)
                mOnePlusFaceUnlock?.startFaceUnlock()
            } catch (e: Exception) {
                e(e, TAG + ("Caught exception starting OnePlusFaceUnlock: " + e.message))
                onePlusFaceUnlockInterface.onError(
                    FACEUNLOCK_CANNT_START, getMessage(
                        FACEUNLOCK_CANNT_START
                    )
                )
                return
            }
            mOnePlusFaceUnlockServiceRunning = true
        } else {
            e(TAG + ".startOnePlusFaceUnlock() attempted while running")
        }
    }

    @Synchronized
    fun hasBiometric(): Boolean {
        try {
            return mOnePlusFaceUnlock?.checkState() == 0
        } catch (e: Throwable) {
        }
        return false
    }
}