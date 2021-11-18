package dev.skomlach.biometric.compat.engine.internal.face.oneplus

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.d
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.e


class OnePlusFaceSettingsHelper constructor(
    context: Context
) {
    companion object {
        private val TAG = OnePlusFaceSettingsHelper::class.java.simpleName
    }

    private val hasHardware: Boolean
    private var mOnePlusFaceSettings: OnePlusFaceSettings? = null
    private var mOnePlusFaceSettingsServiceRunning = false
    private var mBoundToOnePlusFaceSettingsService = false
    private var mCallback: IOPFacelockCallback? = null
    private var mServiceConnection: ServiceConnection? = null

    init {
        mOnePlusFaceSettings = try {
            OnePlusFaceSettings(context)
        } catch (e: Throwable) {
            null
        }
        hasHardware = mOnePlusFaceSettings != null
    }

    @Synchronized
    fun destroy() {
        try {
            mOnePlusFaceSettings?.unbind()
        } catch (e: Exception) {
            e(e, TAG + "Caught exception creating OnePlusFaceSettings: $e")

        }
    }

    @Synchronized
    fun init() {
        d(TAG + ".initFacelock")
        try {
            mCallback = object : IOPFacelockCallback {
                @Throws(RemoteException::class)
                override fun onBeginRecognize(faceId: Int) {
                    d(TAG + ".IOnePlusFaceSettingsCallback.onBeginRecognize - " + faceId)
                }

                @Throws(RemoteException::class)
                override fun onCompared(
                    faceId: Int,
                    userId: Int,
                    result: Int,
                    compareTimeMillis: Int,
                    score: Int
                ) {
                    d(TAG + ".IOnePlusFaceSettingsCallback.onCompared - " + faceId + "/" + userId + "/" + result)

                }

                @Throws(RemoteException::class)
                override fun onEndRecognize(faceId: Int, userId: Int, result: Int) {
                    d(TAG + ".IOnePlusFaceSettingsCallback.onEndRecognize - " + faceId + "/" + userId + "/" + result)
                }
            }
            mServiceConnection = object : ServiceConnection {
                override fun onServiceDisconnected(name: ComponentName) {
                    d(TAG + ".ServiceConnection.onServiceDisconnected")
                    mOnePlusFaceSettingsServiceRunning = false
                    mBoundToOnePlusFaceSettingsService = false
                }

                override fun onServiceConnected(name: ComponentName, service: IBinder) {
                    d(TAG + ".ServiceConnection.onServiceConnected")
                    mBoundToOnePlusFaceSettingsService = true

                }
            }
            if (!mBoundToOnePlusFaceSettingsService) {
                mServiceConnection?.let {
                    if (mOnePlusFaceSettings?.bind(it) == false) {
                    } else {
                        d(TAG + ".Binded, waiting for connection")
                        return
                    }
                }
            } else {
                d(TAG + ".Already mBoundToOnePlusFaceSettingsService")
            }
        } catch (e: Exception) {
            e(e, TAG + "Caught exception creating OnePlusFaceSettings: $e")

        }
        d(TAG + ".init failed")
    }

    @Synchronized
    fun hasBiometric(): Boolean {
        try {
            return mOnePlusFaceSettings?.checkState() == 0
        } catch (e: Throwable) {
        }
        return false
    }
}