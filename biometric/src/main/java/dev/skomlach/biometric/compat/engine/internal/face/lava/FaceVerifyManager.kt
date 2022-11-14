/*
 *  Copyright (c) 2022 Sergey Komlach aka Salat-Cx65; Original project https://github.com/Salat-Cx65/AdvancedBiometricPromptCompat
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

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Message
import android.os.RemoteException
import android.provider.Settings
import android.util.Log
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl


class FaceVerifyManager(private val mContext: Context) {

    companion object {
        private const val TAG = "PrizeFaceVerifyManager"
        private const val ACTION = "com.prize.faceunlock.prizeFaceUnlockDetectService"
        private const val VERIFY_MSG = 0
        const val LAVA_FACE_UNLOCK_KEY = "lava_face_unlock_key"
    }

    private var mIFaceVerifyService: IFaceVerifyService? = null
    private var isBinded = false
    private var mFaceUnlockCallback: FaceUnlockCallback? = null

    @SuppressLint("HandlerLeak")
    private val mHandler: Handler = object : Handler() {
        override fun handleMessage(msg: Message) {
            if (msg.what == VERIFY_MSG) {
                val result = msg.arg1
                val resultStr = msg.obj as String
                mFaceUnlockCallback?.onFaceVerifyChanged(result, resultStr)

            }
        }
    }
    private val mIFaceVerifyServiceCallback: IFaceVerifyServiceCallback =
        object : IFaceVerifyServiceCallback.Stub() {
            @Throws(RemoteException::class)
            override fun sendRecognizeResult(resultId: Int, commandStr: String?) {
                val msg = mHandler.obtainMessage()
                msg.what = VERIFY_MSG
                msg.arg1 = resultId
                msg.obj = commandStr
                mHandler.sendMessage(msg)
            }
        }
    private val conn: ServiceConnection = object : ServiceConnection {
        override fun onServiceDisconnected(name: ComponentName) {
            isBinded = false
            mIFaceVerifyService = null
        }

        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            Log.i(TAG, "onServiceConnected start  ")
            mIFaceVerifyService = IFaceVerifyService.Stub.asInterface(service)
            if (null != mIFaceVerifyService) {
                try {
                    Log.i(
                        TAG,
                        "onServiceConnected mIFaceVerifyServiceCallback  = $mIFaceVerifyServiceCallback"
                    )
                    mIFaceVerifyService?.registerCallback(mIFaceVerifyServiceCallback)
                    mIFaceVerifyService?.startVerify()
                } catch (e: RemoteException) {
                    // TODO Auto-generated catch block
                    e.printStackTrace()
                }
            }
            isBinded = true
            Log.i(TAG, "onServiceConnected end  ")
        }
    }

    fun bindFaceVerifyService() {
        if (isBinded) {
            startFaceVerify()
        } else {
            Log.i(TAG, "bindFaceVerifyService start 11111  ")
            val intent = Intent()
            val cn = ComponentName(
                "com.prize.faceunlock",
                "com.sensetime.faceunlock.service.PrizeFaceDetectService"
            )
            intent.component = cn
            mContext.bindService(intent, conn, Context.BIND_AUTO_CREATE)
            Log.i(TAG, "bindLavaVoiceService end  ")
        }
    }

    fun unbindFaceVerifyService() {
        Log.i(
            TAG,
            "unbindLavaVoiceService start  isBinded = $isBinded"
        )
        if (isBinded) {
            mContext.unbindService(conn)
            mIFaceVerifyService = null
            isBinded = false
        }
        Log.i(TAG, "unbindLavaVoiceService start  ")
    }

    fun startFaceVerify() {
        if (null != mIFaceVerifyService) {
            try {
                mIFaceVerifyService?.startVerify()
            } catch (e: RemoteException) {
                BiometricLoggerImpl.e(e)
            }
        }
    }

    fun stopFaceVerify() {
        if (null != mIFaceVerifyService) {
            try {
                mIFaceVerifyService?.stopVerify()
            } catch (e: RemoteException) {
                BiometricLoggerImpl.e(e)
            }
            if (isBinded && !isFaceUnlockOn) {
                unbindFaceVerifyService()
            }
        }
    }

    fun setFaceUnlockCallback(callback: FaceUnlockCallback?) {
        mFaceUnlockCallback = callback
    }

    val isFaceUnlockOn: Boolean
        get() {
            var on = 0
            on = Settings.System.getInt(
                mContext.contentResolver,
                "prize_faceid_switch",//Settings.System.PRIZE_FACEID_SWITCH,
                0
            )
            return on == 1
        }

}