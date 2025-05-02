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

package dev.skomlach.biometric.compat.engine.internal.face.miui.impl

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import dev.skomlach.biometric.compat.engine.internal.face.miui.impl.wrapper.BiometricConnect
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.d
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.e
import dev.skomlach.common.contextprovider.AndroidContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

class BiometricClient {
    companion object {
        private const val LOG_TAG = "BiometricClient"
        private const val MSG_GET_VERSION = 5002
        private const val MSG_RELEASE_SERVICE = 5001
        private const val MSG_RELEASE_TIMEOUT = 5
        private const val MSG_REPLAY_TIMEOUT = 2
        private const val MSG_SEND_SERVEICE = 5003
        private const val MSG_START_SERVICE = 5000
        private const val SERVICE_READY_TIMEOUT = 2
        private const val SERVICE_STATUS_CONNECTED = 2
        private const val SERVICE_STATUS_CONNECTING = 1
        private const val SERVICE_STATUS_CONNECTING_ERROR = 12
        private const val SERVICE_STATUS_CONNECTING_TIME_OUT = 11
        private const val SERVICE_STATUS_DISCONNECT = 5
        private const val SERVICE_STATUS_DISCONNECTING = 3
        private const val SERVICE_STATUS_NONE = 0
        private const val SERVICE_STATUS_UNBIND = 4
    }

    private val accessLock_ = ReentrantLock()
    private var mCallbackThread: HandlerThread? = null
    private var mClientLister: ClientLister? = null
    private var mConnectHandler: ConnectHandler? = null
    private var mHandler: Handler? = null
    private var mMainThread: HandlerThread? = null
    private var mReplyMessager: Messenger? = null
    private var mSendMessager: Messenger? = null
    private var mServiceCallback: ServiceCallback? = null
    private var mServiceConnectStatus = 0
    private var mServiceConnection: MyServiceConnection? = null
    private var mTagInfo: String? = null
    private var replayReadyLatch_: CountDownLatch? = null
    private var serviceReadyLatch_: CountDownLatch? = null
    private val context = AndroidContext.appContext

    init {
        var stringBuilder = StringBuilder()
        stringBuilder.append("BiometricMainThread_")
        stringBuilder.append(mTagInfo)
        mMainThread = HandlerThread(stringBuilder.toString())
        mMainThread?.let {
            it.start()
            mHandler = MyHandler(it.looper)
        }

        mTagInfo = context.toString()

        stringBuilder = StringBuilder()
        stringBuilder.append("BiometricClientCBThread_")
        stringBuilder.append(mTagInfo)
        mCallbackThread = HandlerThread(stringBuilder.toString())
        mCallbackThread?.let {
            it.start()
            mConnectHandler = ConnectHandler(it.looper)
        }

        mReplyMessager = Messenger(mConnectHandler)
        BiometricConnect.syncDebugLog()
        val stringBuilder2 = StringBuilder()
        stringBuilder2.append(":")
        stringBuilder2.append(mTagInfo)
        stringBuilder2.append(":BiometricClient MainThread id:")
        stringBuilder2.append(mMainThread?.id)
        stringBuilder2.append(" CallbackThread id:")
        stringBuilder2.append(mCallbackThread?.id)
        d(LOG_TAG, stringBuilder2.toString())
    }

    /* Access modifiers changed, original: protected */
    @Throws(Throwable::class)
    fun finalize() {
        val stringBuilder = StringBuilder()
        stringBuilder.append(":")
        stringBuilder.append(mTagInfo)
        stringBuilder.append(":finalize")
        d(LOG_TAG, stringBuilder.toString())
        release()
    }

    private fun release() {
        if (mHandler != null) {
            val stringBuilder = StringBuilder()
            stringBuilder.append(":")
            stringBuilder.append(mTagInfo)
            stringBuilder.append(":release")
            d(LOG_TAG, stringBuilder.toString())
            var handlerThread = mCallbackThread
            if (handlerThread != null) {

                mCallbackThread?.looper?.quit()

                mCallbackThread?.quit()
                mCallbackThread = null
            }
            mConnectHandler = null
            mReplyMessager = null
            handlerThread = mMainThread
            if (handlerThread != null) {
                mMainThread?.looper?.quit()

                mMainThread?.quit()
                mMainThread = null
            }
            mHandler = null
        }
    }

    fun startService(cb: ServiceCallback?) {
        mServiceCallback = cb
        mServiceConnectStatus = 0
        mSendMessager = null
        val stringBuilder = StringBuilder()
        stringBuilder.append(":")
        stringBuilder.append(mTagInfo)
        stringBuilder.append(":startService")
        d(LOG_TAG, stringBuilder.toString())
        mHandler?.sendEmptyMessage(5000)
        val bundle = Bundle()
        bundle.putString("info", mTagInfo)
        sendBundle(0, bundle)
    }

    private fun removeAllMessage() {
        if (mHandler != null) {
            val stringBuilder = StringBuilder()
            val str = ":"
            stringBuilder.append(str)
            stringBuilder.append(mTagInfo)
            stringBuilder.append(":removeAllMessage")
            val stringBuilder2 = stringBuilder.toString()
            val str2 = LOG_TAG
            d(str2, stringBuilder2)
            for (arg in 5000..5003) {
                while (mHandler?.hasMessages(arg) == true) {
                    val stringBuilder3 = StringBuilder()
                    stringBuilder3.append(str)
                    stringBuilder3.append(mTagInfo)
                    stringBuilder3.append(":removeAllMessage arg：")
                    stringBuilder3.append(arg)
                    d(str2, stringBuilder3.toString())
                    mHandler?.removeMessages(arg)
                }
            }
        }
    }

    fun releaseService(cb: ServiceCallback?) {
        val stringBuilder = StringBuilder()
        stringBuilder.append(":")
        stringBuilder.append(mTagInfo)
        stringBuilder.append(":releaseService  mServiceConnectStatus:")
        stringBuilder.append(mServiceConnectStatus)
        d(LOG_TAG, stringBuilder.toString())
        removeAllMessage()
        sendCommand(2)
        mHandler?.sendEmptyMessage(5001)
    }

    fun getServiceVersion(module_id: Int, l: ClientLister?) {
        if (BiometricConnect.DEBUG_LOG) {
            val stringBuilder = StringBuilder()
            stringBuilder.append(":")
            stringBuilder.append(mTagInfo)
            stringBuilder.append(":getServiceVersion module_id:")
            stringBuilder.append(module_id)
            d(LOG_TAG, stringBuilder.toString())
        }
        mClientLister = l
        val msg = Message.obtain(mHandler)
        msg.what = 5002
        msg.arg1 = module_id
        mHandler?.sendMessage(msg)
    }

    private fun onServiceBind(service: IBinder) {
        var stringBuilder = StringBuilder()
        val str = ":"
        stringBuilder.append(str)
        stringBuilder.append(mTagInfo)
        stringBuilder.append(":onServiceBind begin")
        val stringBuilder2 = stringBuilder.toString()
        val str2 = LOG_TAG
        d(str2, stringBuilder2)
        accessLock_.runCatching { this.lock() }
        if (mTagInfo == null) {
            stringBuilder = StringBuilder()
            stringBuilder.append(str)
            stringBuilder.append(mTagInfo)
            stringBuilder.append(":onServiceBind mTagInfo is null")
            d(str2, stringBuilder.toString())
            accessLock_.runCatching {
                this.unlock()
            }
            return
        }
        mServiceConnectStatus = 2
        mSendMessager = Messenger(service)
        serviceReadyLatch_?.countDown()
        if (mServiceCallback != null) {
            d(str2, "mServiceCallback yes")
            mServiceCallback?.onBiometricServiceConnected()
        }
        accessLock_.runCatching {
            this.unlock()
        }
        stringBuilder = StringBuilder()
        stringBuilder.append(str)
        stringBuilder.append(mTagInfo)
        stringBuilder.append(":onServiceBind end")
        d(str2, stringBuilder.toString())
    }

    private fun onServiceUnbind(lock: Boolean) {
        if (lock) {
            accessLock_.runCatching { this.lock() }
        }
        val i = mServiceConnectStatus
        val str = ":"
        val str2 = LOG_TAG
        val stringBuilder: StringBuilder
        if (4 == i || 5 == i) {
            stringBuilder = StringBuilder()
            stringBuilder.append(str)
            stringBuilder.append(mTagInfo)
            stringBuilder.append(":onServiceUnbind ignore mServiceConnectStatus:")
            stringBuilder.append(mServiceConnectStatus)
            d(str2, stringBuilder.toString())
            if (lock) {
                accessLock_.runCatching {
                    this.unlock()
                }
            }
            return
        }
        stringBuilder = StringBuilder()
        stringBuilder.append(str)
        stringBuilder.append(mTagInfo)
        stringBuilder.append(":onServiceUnbind mServiceConnectStatus:")
        stringBuilder.append(mServiceConnectStatus)
        d(str2, stringBuilder.toString())
        mServiceConnectStatus = 4
        val serviceCallback = mServiceCallback
        serviceCallback?.onBiometricServiceDisconnected()
        if (lock) {
            accessLock_.runCatching {
                this.unlock()
            }
        }
    }

    @JvmOverloads
    fun sendCommand(cmd: Int, arg: Int = 0) {
        val z = BiometricConnect.DEBUG_LOG
        val str = ":Send MSG: sendCommand cmd:"
        val str2 = ":"
        val str3 = LOG_TAG
        if (z) {
            val stringBuilder = StringBuilder()
            stringBuilder.append(str2)
            stringBuilder.append(mTagInfo)
            stringBuilder.append(str)
            stringBuilder.append(cmd)
            stringBuilder.append(",arg:")
            stringBuilder.append(arg)
            d(str3, stringBuilder.toString())
        }
        val msg = Message.obtain()
        msg.what = 1001
        msg.arg1 = cmd
        msg.arg2 = arg
        val msg_cmd = Message.obtain(mHandler, 5003)
        msg_cmd.obj = msg
        mHandler?.sendMessage(msg_cmd)
        if (BiometricConnect.DEBUG_LOG) {
            val stringBuilder2 = StringBuilder()
            stringBuilder2.append(str2)
            stringBuilder2.append(mTagInfo)
            stringBuilder2.append(str)
            stringBuilder2.append(cmd)
            stringBuilder2.append(" ok")
            d(str3, stringBuilder2.toString())
        }
    }

    fun sendBundle(key: Int, bundle: Bundle) {
        if (BiometricConnect.DEBUG_LOG) {
            val stringBuilder = StringBuilder()
            stringBuilder.append(":")
            stringBuilder.append(mTagInfo)
            stringBuilder.append(":Send MSG: sendBundle key:")
            stringBuilder.append(key)
            d(LOG_TAG, stringBuilder.toString())
        }
        val msg = Message.obtain()
        msg.what = 1002
        bundle.putInt("tag", key)
        msg.data = bundle
        val msg_cmd = Message.obtain(mHandler, 5003)
        msg_cmd.obj = msg
        mHandler?.sendMessage(msg_cmd)
    }

    @SuppressLint("WrongConstant")
    private fun handle_startService() {
        var stringBuilder: StringBuilder
        var stringBuilder2: StringBuilder
        val z = BiometricConnect.DEBUG_LOG
        val str = ":"
        val str2 = LOG_TAG
        if (z) {
            val stringBuilder3 = StringBuilder()
            stringBuilder3.append(str)
            stringBuilder3.append(mTagInfo)
            stringBuilder3.append(":handle_startService")
            d(str2, stringBuilder3.toString())
        }
        accessLock_.runCatching { this.lock() }
        mServiceConnectStatus = 1
        serviceReadyLatch_ = CountDownLatch(1)
        val intent = Intent("com.xiaomi.biometric.BiometricService")
        intent.setPackage(BiometricConnect.SERVICE_PACKAGE_NAME)
        mServiceConnection = MyServiceConnection()
        try {
            mServiceConnection?.let {
                context.bindService(intent, it, 65)
            }
        } catch (e: Exception) {
            stringBuilder = StringBuilder()
            stringBuilder.append(str)
            stringBuilder.append(mTagInfo)
            stringBuilder.append(":handle_startService - bindService Exception ERROR: ")
            stringBuilder.append(e)
            d(str2, stringBuilder.toString())
        }
        accessLock_.runCatching {
            this.unlock()
        }
        if (BiometricConnect.DEBUG_LOG) {
            stringBuilder2 = StringBuilder()
            stringBuilder2.append(str)
            stringBuilder2.append(mTagInfo)
            stringBuilder2.append(":handle_startService await...")
            d(str2, stringBuilder2.toString())
        }
        try {
            if (serviceReadyLatch_?.await(2, TimeUnit.SECONDS) == false) {
                stringBuilder2 = StringBuilder()
                stringBuilder2.append(str)
                stringBuilder2.append(mTagInfo)
                stringBuilder2.append(":handle_startService - ERROR: tmeout!")
                d(str2, stringBuilder2.toString())
                accessLock_.runCatching { this.lock() }
                mServiceConnectStatus = 11
                accessLock_.runCatching {
                    this.unlock()
                }
            }
        } catch (e2: Exception) {
            stringBuilder = StringBuilder()
            stringBuilder.append(str)
            stringBuilder.append(mTagInfo)
            stringBuilder.append(":handle_startService - ERROR: ")
            stringBuilder.append(e2)
            d(str2, stringBuilder.toString())
            e(e2)
            accessLock_.runCatching { this.lock() }
            mServiceConnectStatus = 12
            accessLock_.runCatching {
                this.unlock()
            }
        }
        if (BiometricConnect.DEBUG_LOG) {
            stringBuilder2 = StringBuilder()
            stringBuilder2.append(str)
            stringBuilder2.append(mTagInfo)
            stringBuilder2.append(":handle_startService ok")
            d(str2, stringBuilder2.toString())
        }
    }

    private fun handle_releaseService() {
        val stringBuilder: StringBuilder
        accessLock_.runCatching { this.lock() }
        val str = mTagInfo
        val str2 = ":"
        val str3 = LOG_TAG
        var stringBuilder2: StringBuilder
        if (str == null) {
            stringBuilder2 = StringBuilder()
            stringBuilder2.append(str2)
            stringBuilder2.append(mTagInfo)
            stringBuilder2.append(":handle_releaseService mClientInfoList is null")
            e(str3, stringBuilder2.toString())
            accessLock_.runCatching {
                this.unlock()
            }
            return
        }
        stringBuilder2 = StringBuilder()
        stringBuilder2.append(str2)
        stringBuilder2.append(mTagInfo)
        stringBuilder2.append(":handle_releaseService mServiceConnectStatus:")
        stringBuilder2.append(mServiceConnectStatus)
        stringBuilder2.append(" ")
        d(str3, stringBuilder2.toString())
        if (4 != mServiceConnectStatus) {
            onServiceUnbind(false)
        }
        try {
            mServiceConnection?.let {
                context.unbindService(it)
            }
        } catch (e: IllegalArgumentException) {
            stringBuilder = StringBuilder()
            stringBuilder.append(str2)
            stringBuilder.append(mTagInfo)
            stringBuilder.append(":handle_releaseService IllegalArgumentException:")
            stringBuilder.append(e.toString())
            d(str3, stringBuilder.toString())
        } catch (e2: NullPointerException) {
            stringBuilder = StringBuilder()
            stringBuilder.append(str2)
            stringBuilder.append(mTagInfo)
            stringBuilder.append(":handle_releaseService NullPointerException:")
            stringBuilder.append(e2.toString())
            d(str3, stringBuilder.toString())
        } catch (e3: Exception) {
            stringBuilder = StringBuilder()
            stringBuilder.append(str2)
            stringBuilder.append(mTagInfo)
            stringBuilder.append(":handle_releaseService Exception:")
            stringBuilder.append(e3.toString())
            d(str3, stringBuilder.toString())
        }
        mServiceConnectStatus = 5
        mServiceConnection = null
        release()
        accessLock_.runCatching {
            this.unlock()
        }
        if (BiometricConnect.DEBUG_LOG) {
            stringBuilder2 = StringBuilder()
            stringBuilder2.append(str2)
            stringBuilder2.append(mTagInfo)
            stringBuilder2.append(":handle_releaseService end")
            d(str3, stringBuilder2.toString())
        }
    }

    private fun handle_getServiceVersion(module_id: Int) {
        accessLock_.runCatching { this.lock() }
        val i = mServiceConnectStatus
        val str = ":"
        val str2 = LOG_TAG
        var stringBuilder: StringBuilder
        if (i != 2) {
            stringBuilder = StringBuilder()
            stringBuilder.append(str)
            stringBuilder.append(mTagInfo)
            stringBuilder.append(":handle_getServiceVersion error: service not Connected")
            d(str2, stringBuilder.toString())
            accessLock_.runCatching {
                this.unlock()
            }
            return
        }
        var stringBuilder2: StringBuilder
        try {
            if (BiometricConnect.DEBUG_LOG) {
                stringBuilder = StringBuilder()
                stringBuilder.append(str)
                stringBuilder.append(mTagInfo)
                stringBuilder.append(":handle_getServiceVersion")
                d(str2, stringBuilder.toString())
            }
            val msg = Message.obtain(null, 1000, module_id, 0)
            msg.replyTo = mReplyMessager
            replayReadyLatch_ = CountDownLatch(1)
            mSendMessager?.send(msg)
            accessLock_.runCatching {
                this.unlock()
            }
            try {
                if (replayReadyLatch_?.await(2, TimeUnit.SECONDS) == false) {
                    stringBuilder2 = StringBuilder()
                    stringBuilder2.append(str)
                    stringBuilder2.append(mTagInfo)
                    stringBuilder2.append(":handle_getServiceVersion - ERROR: timeout!")
                    d(str2, stringBuilder2.toString())
                }
            } catch (ex: InterruptedException) {
                val stringBuilder3 = StringBuilder()
                stringBuilder3.append(str)
                stringBuilder3.append(mTagInfo)
                stringBuilder3.append(":handle_getServiceVersion - ERROR: ")
                stringBuilder3.append(ex)
                d(str2, stringBuilder3.toString())
                e(ex)
            }
        } catch (e: RemoteException) {
            stringBuilder2 = StringBuilder()
            stringBuilder2.append(str)
            stringBuilder2.append(mTagInfo)
            stringBuilder2.append(":handle_getServiceVersion - RemoteException ERROR: ")
            stringBuilder2.append(e)
            d(str2, stringBuilder2.toString())
            e(e)
        } catch (e2: Exception) {
            stringBuilder2 = StringBuilder()
            stringBuilder2.append(str)
            stringBuilder2.append(mTagInfo)
            stringBuilder2.append(":handle_getServiceVersion - Exception ERROR: ")
            stringBuilder2.append(e2)
            d(str2, stringBuilder2.toString())
            e(e2)
        }
        if (BiometricConnect.DEBUG_LOG) {
            stringBuilder = StringBuilder()
            stringBuilder.append(str)
            stringBuilder.append(mTagInfo)
            stringBuilder.append(":handle_getServiceVersion end")
            d(str2, stringBuilder.toString())
        }
    }

    private fun handle_sendService(msg: Message) {
        var stringBuilder: StringBuilder
        val str = ":handle_sendService - ERROR: "
        accessLock_.runCatching { this.lock() }
        val i = mServiceConnectStatus
        val str2 = ":"
        val str3 = LOG_TAG
        val stringBuilder2: StringBuilder
        if (i != 2) {
            stringBuilder2 = StringBuilder()
            stringBuilder2.append(str2)
            stringBuilder2.append(mTagInfo)
            stringBuilder2.append(":handle_sendService error: service not Connected")
            d(str3, stringBuilder2.toString())
            accessLock_.runCatching {
                this.unlock()
            }
            return
        }
        var stringBuilder3: StringBuilder
        if (BiometricConnect.DEBUG_LOG) {
            stringBuilder3 = StringBuilder()
            stringBuilder3.append(str2)
            stringBuilder3.append(mTagInfo)
            stringBuilder3.append(":handle_sendService")
            d(str3, stringBuilder3.toString())
        }
        try {
            msg.replyTo = mReplyMessager
            replayReadyLatch_ = CountDownLatch(1)
            mSendMessager?.send(msg)
            accessLock_.runCatching {
                this.unlock()
            }
            try {
                if (replayReadyLatch_?.await(2, TimeUnit.SECONDS) == false) {
                    stringBuilder3 = StringBuilder()
                    stringBuilder3.append(str2)
                    stringBuilder3.append(mTagInfo)
                    stringBuilder3.append(":handle_sendService - ERROR: timeout! cmd:")
                    stringBuilder3.append(msg.arg1)
                    stringBuilder3.append(" arg:")
                    stringBuilder3.append(msg.arg2)
                    d(str3, stringBuilder3.toString())
                }
            } catch (ex: InterruptedException) {
                stringBuilder = StringBuilder()
                stringBuilder.append(str2)
                stringBuilder.append(mTagInfo)
                stringBuilder.append(str)
                stringBuilder.append(ex)
                d(str3, stringBuilder.toString())
                e(ex)
            }
        } catch (ex2: RemoteException) {
            stringBuilder = StringBuilder()
            stringBuilder.append(str2)
            stringBuilder.append(mTagInfo)
            stringBuilder.append(str)
            stringBuilder.append(ex2)
            d(str3, stringBuilder.toString())
            e(ex2)
        } catch (e: Exception) {
            stringBuilder3 = StringBuilder()
            stringBuilder3.append(str2)
            stringBuilder3.append(mTagInfo)
            stringBuilder3.append(":handle_sendService - Exception ERROR: ")
            stringBuilder3.append(e)
            d(str3, stringBuilder3.toString())
            e(e)
        }
        if (BiometricConnect.DEBUG_LOG) {
            stringBuilder2 = StringBuilder()
            stringBuilder2.append(str2)
            stringBuilder2.append(mTagInfo)
            stringBuilder2.append(":handle_sendService end")
            d(str3, stringBuilder2.toString())
        }
    }

    interface ClientLister {
        fun onVersion(f: Float, f2: Float)
    }

    interface ServiceCallback {
        fun onBiometricBundleCallback(i: Int, i2: Int, bundle: Bundle)
        fun onBiometricEventCallback(i: Int, i2: Int, i3: Int, i4: Int)
        fun onBiometricEventClassLoader(bundle: Bundle)
        fun onBiometricServiceConnected()
        fun onBiometricServiceDisconnected()
    }

    private inner class ConnectHandler(looper: Looper) : Handler(looper) {
        override fun handleMessage(msg: Message) {
            var stringBuilder: StringBuilder
            val z = BiometricConnect.DEBUG_LOG
            val str = ", arg1:"
            var str2 = ":"
            val str3 = LOG_TAG
            if (z) {
                stringBuilder = StringBuilder()
                stringBuilder.append(str2)
                stringBuilder.append(mTagInfo)
                stringBuilder.append(":handleMessage cb - what:")
                stringBuilder.append(msg.what)
                stringBuilder.append(str)
                stringBuilder.append(msg.arg1)
                d(str3, stringBuilder.toString())
            }
            if (msg.what == 9999) {
                d(str3, "MSG_CONNECT_TEST ok")
                if (replayReadyLatch_ != null) {
                    replayReadyLatch_?.countDown()
                }
                return
            }
            accessLock_.runCatching { this.lock() }
            if (mServiceCallback == null) {
                stringBuilder = StringBuilder()
                stringBuilder.append(str2)
                stringBuilder.append(mTagInfo)
                stringBuilder.append(":handleMessage cb ignore- what:")
                stringBuilder.append(msg.what)
                stringBuilder.append(str)
                stringBuilder.append(msg.arg1)
                e(str3, stringBuilder.toString())
                accessLock_.runCatching {
                    this.unlock()
                }
                return
            }
            val in_bundle = msg.data
            in_bundle.classLoader = BiometricConnect::class.java.classLoader
            val result = in_bundle.getBoolean("result")
            var i = msg.what
            if (i == 1000) {
                val serviceVerMaj_ = in_bundle.getInt(BiometricConnect.MSG_VER_SER_MAJ)
                i = in_bundle.getInt(BiometricConnect.MSG_VER_SER_MIN)
                val moduleVerMaj_ = in_bundle.getInt(BiometricConnect.MSG_VER_MODULE_MAJ)
                val moduleVerMin_ = in_bundle.getInt(BiometricConnect.MSG_VER_MODULE_MIN)
                val stringBuilder2 = StringBuilder()
                stringBuilder2.append(str2)
                stringBuilder2.append(mTagInfo)
                stringBuilder2.append(":handleMessage cb - MSG_SEVICE_VERSION:SVC: v")
                stringBuilder2.append(serviceVerMaj_)
                str2 = "."
                stringBuilder2.append(str2)
                stringBuilder2.append(i)
                stringBuilder2.append(", Module: v")
                stringBuilder2.append(moduleVerMaj_)
                stringBuilder2.append(str2)
                stringBuilder2.append(moduleVerMin_)
                d(str3, stringBuilder2.toString())
                if (mClientLister != null) {
                    mClientLister?.onVersion(
                        (serviceVerMaj_ * 100 + i).toFloat() / 100.0f,
                        (moduleVerMaj_ * 100 + moduleVerMin_).toFloat() / 100.0f
                    )
                }
                accessLock_.runCatching {
                    this.unlock()
                }
            } else if (i != 1001) {
                val str4 = BiometricConnect.MSG_REPLY_MODULE_ID
                val stringBuilder3: StringBuilder
                if (i == 1004) {
                    if (!result || mServiceCallback == null) {
                        stringBuilder3 = StringBuilder()
                        stringBuilder3.append(str2)
                        stringBuilder3.append(mTagInfo)
                        stringBuilder3.append(":handleMessage cb MSG_CALLBACK_EVENT ignore- what:")
                        stringBuilder3.append(msg.what)
                        stringBuilder3.append(str)
                        stringBuilder3.append(msg.arg1)
                        e(str3, stringBuilder3.toString())
                    } else {
                        mServiceCallback?.onBiometricEventCallback(
                            in_bundle.getInt(str4),
                            in_bundle.getInt("event"),
                            in_bundle.getInt(BiometricConnect.MSG_REPLY_ARG1),
                            in_bundle.getInt(BiometricConnect.MSG_REPLY_ARG2)
                        )
                    }
                    accessLock_.runCatching {
                        this.unlock()
                    }
                } else if (i != 1005) {
                    accessLock_.runCatching {
                        this.unlock()
                    }
                } else {
                    if (!result || mServiceCallback == null) {
                        stringBuilder3 = StringBuilder()
                        stringBuilder3.append(str2)
                        stringBuilder3.append(mTagInfo)
                        stringBuilder3.append(":handleMessage cb MSG_CALLBACK_BUNDLE ignore- what:")
                        stringBuilder3.append(msg.what)
                        stringBuilder3.append(str)
                        stringBuilder3.append(msg.arg1)
                        e(str3, stringBuilder3.toString())
                    } else {
                        mServiceCallback?.onBiometricBundleCallback(
                            in_bundle.getInt(str4),
                            in_bundle.getInt("key"),
                            in_bundle
                        )
                    }
                    accessLock_.runCatching {
                        this.unlock()
                    }
                }
            } else {
                val stringBuilder4: StringBuilder
                if (2 == msg.arg1) {
                    if (BiometricConnect.DEBUG_LOG) {
                        stringBuilder4 = StringBuilder()
                        stringBuilder4.append(str2)
                        stringBuilder4.append(mTagInfo)
                        stringBuilder4.append(":handleMessage cb - MSG_COMMAND_DEINIT_CALLBACK")
                        d(str3, stringBuilder4.toString())
                    }
                    onServiceUnbind(false)
                } else if (1 == msg.arg1 && BiometricConnect.DEBUG_LOG) {
                    stringBuilder4 = StringBuilder()
                    stringBuilder4.append(str2)
                    stringBuilder4.append(mTagInfo)
                    stringBuilder4.append(":handleMessage cb - MSG_COMMAND_INIT_CALLBACK")
                    d(str3, stringBuilder4.toString())
                }
                accessLock_.runCatching {
                    this.unlock()
                }
            }
            if (!in_bundle.getBoolean(BiometricConnect.MSG_REPLY_NO_SEND_WAIT)) {
                replayReadyLatch_?.countDown()
            }
        }

        init {
            val stringBuilder = StringBuilder()
            stringBuilder.append(":")
            stringBuilder.append(mTagInfo)
            stringBuilder.append(":ConnectHandler")
            d(LOG_TAG, stringBuilder.toString())
        }
    }

    private inner class MyHandler(looper: Looper) : Handler(looper) {
        override fun handleMessage(msg: Message) {
            if (BiometricConnect.DEBUG_LOG) {
                val stringBuilder = StringBuilder()
                stringBuilder.append(":")
                stringBuilder.append(mTagInfo)
                stringBuilder.append(":handleMessage main what:")
                stringBuilder.append(msg.what)
                d(LOG_TAG, stringBuilder.toString())
            }
            when (msg.what) {
                5000 -> {
                    handle_startService()
                    return
                }

                5001 -> {
                    handle_releaseService()
                    return
                }

                5002 -> {
                    handle_getServiceVersion(msg.arg1)
                    return
                }

                5003 -> {
                    handle_sendService(msg.obj as Message)
                    return
                }

                else -> return
            }
        }
    }

    private inner class MyServiceConnection : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val stringBuilder = StringBuilder()
            stringBuilder.append(":")
            stringBuilder.append(mTagInfo)
            stringBuilder.append(":onServiceConnected mServiceConnectStatus:")
            stringBuilder.append(mServiceConnectStatus)
            d(LOG_TAG, stringBuilder.toString())
            onServiceBind(service)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            val stringBuilder = StringBuilder()
            stringBuilder.append(":")
            stringBuilder.append(mTagInfo)
            stringBuilder.append(":onServiceDisconnected mServiceConnectStatus:")
            stringBuilder.append(mServiceConnectStatus)
            d(LOG_TAG, stringBuilder.toString())
            onServiceUnbind(true)
            handle_releaseService()
        }
    }
}