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

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.*
import dev.skomlach.biometric.compat.engine.internal.OemCallbackSession
import dev.skomlach.biometric.compat.engine.internal.ServiceBindingState
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl
import dev.skomlach.common.misc.SettingsHelper

internal class FaceVerifyManager(private val mContext: Context) {
    companion object { private const val TAG = "PrizeFaceVerifyManager" }

    private var mIFaceVerifyService: IFaceVerifyService? = null
    private var isBinded = false
    private val serviceBindingState = ServiceBindingState()
    private val callbacks = OemCallbackSession<FaceUnlockCallback>()
    private var connection: ServiceConnection? = null
    private var registeredCallback: IFaceVerifyServiceCallback? = null
    private val mMainThread = HandlerThread(TAG).apply { start() }
    private val mHandler = Handler(mMainThread.looper)

    @Throws(Throwable::class)
    fun finalize() {
        stopFaceVerify()
        mMainThread.quit()
    }

    fun bindFaceVerifyService() {
        val capture = callbacks.current() ?: return
        if (isBinded) {
            startFaceVerify()
            return
        }
        if (serviceBindingState.isBindingActive()) return
        val vendorCallback = object : IFaceVerifyServiceCallback.Stub() {
            override fun sendRecognizeResult(resultId: Int, commandStr: String?) {
                mHandler.post {
                    callbacks.deliver(capture) { it.onFaceVerifyChanged(resultId, commandStr) }
                }
            }
        }
        val requestConnection = object : ServiceConnection {
            override fun onServiceDisconnected(name: ComponentName) {
                if (connection !== this || !callbacks.owns(capture)) return
                isBinded = false
                mIFaceVerifyService = null
                registeredCallback = null
            }

            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                if (connection !== this || !callbacks.owns(capture)) return
                val remote = IFaceVerifyService.Stub.asInterface(service) ?: return
                mIFaceVerifyService = remote
                registeredCallback = vendorCallback
                isBinded = true
                try {
                    remote.registerCallback(vendorCallback)
                    if (connection === this && callbacks.owns(capture)) remote.startVerify()
                } catch (e: RemoteException) {
                    BiometricLoggerImpl.e(e)
                }
            }
        }
        connection = requestConnection
        val intent = Intent().setComponent(ComponentName(
            "com.prize.faceunlock", "com.sensetime.faceunlock.service.PrizeFaceDetectService"
        ))
        val accepted = try {
            mContext.bindService(intent, requestConnection, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            BiometricLoggerImpl.e(e)
            false
        }
        if (connection !== requestConnection) {
            if (accepted) unbind(requestConnection)
            return
        }
        serviceBindingState.recordBindResult(accepted)
        if (!accepted) connection = null
    }

    fun unbindFaceVerifyService() = retireConnection(stopCapture = false)

    private fun unbind(oldConnection: ServiceConnection) {
        try {
            mContext.unbindService(oldConnection)
        } catch (e: IllegalArgumentException) {
            BiometricLoggerImpl.e(e)
        } catch (e: SecurityException) {
            BiometricLoggerImpl.e(e)
        }
    }

    private fun retireConnection(stopCapture: Boolean) {
        val remote = mIFaceVerifyService
        val callback = registeredCallback
        val oldConnection = connection
        val shouldUnbind = serviceBindingState.consumeUnbindRequest()
        // Retire before any remote call can synchronously enqueue a callback or reenter start.
        callbacks.clear()
        connection = null
        registeredCallback = null
        mIFaceVerifyService = null
        isBinded = false
        if (stopCapture) {
            try { remote?.stopVerify() } catch (e: RemoteException) { BiometricLoggerImpl.e(e) }
        }
        if (callback != null) {
            try { remote?.unregisterCallback(callback) } catch (e: RemoteException) { BiometricLoggerImpl.e(e) }
        }
        if (shouldUnbind && oldConnection != null) unbind(oldConnection)
    }

    fun startFaceVerify() {
        if (callbacks.current() == null) return
        try { mIFaceVerifyService?.startVerify() } catch (e: RemoteException) { BiometricLoggerImpl.e(e) }
    }

    fun stopFaceVerify() = retireConnection(stopCapture = true)

    fun setFaceUnlockCallback(callback: FaceUnlockCallback?) {
        if (callback == null) callbacks.clear() else callbacks.begin(callback)
    }

    val isFaceUnlockOn: Boolean
        get() = SettingsHelper.getInt(mContext, "prize_faceid_switch", 0) == 1
}
