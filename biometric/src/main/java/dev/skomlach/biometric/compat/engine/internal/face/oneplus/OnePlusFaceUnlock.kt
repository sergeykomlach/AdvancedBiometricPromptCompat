/*
 *  Copyright (c) 2021 Sergey Komlach aka Salat-Cx65; Original project https://github.com/Salat-Cx65/AdvancedBiometricPromptCompat
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
package dev.skomlach.biometric.compat.engine.internal.face.oneplus

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.os.RemoteException
import androidx.annotation.RestrictTo
import dev.skomlach.biometric.compat.utils.ReflectionTools.getClassFromPkg
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.d
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.e
import dev.skomlach.common.misc.ExecutorHelper
import java.lang.reflect.InvocationTargetException
import java.util.*

@SuppressLint("PrivateApi")
@RestrictTo(RestrictTo.Scope.LIBRARY)
class OnePlusFaceUnlock(  //https://github.com/xayron/OPSystemUI/tree/d805abc13d081bd3579355a1075d4ae5e8be9270/sources/com/oneplus/faceunlock/internal
    private val mContext: Context
) {

    companion object {
        private val TAG = OnePlusFaceUnlock::class.java.simpleName
    }

    private val pkg = "com.oneplus.faceunlock"
    private var flInterface: Class<*>? = null
    private var flInterfaceStub: Class<*>? = null
    private var flCallbackInterface: Class<*>? = null
    private var flCallbackInterfaceStub: Class<*>? = null
    private var mFaceLockService: Any? = null
    private var mServiceConnection: ServiceConnectionWrapper? = null
    private var mMap = HashMap<IOPFacelockCallback?, Any>()
    private var onePlusFaceSettings: OnePlusFaceSettings? = null

    init {
        val FACELOCK_INTERFACE = "com.oneplus.faceunlock.internal.IOPFacelockService"
        val FACELOCK_CALLBACK = "com.oneplus.faceunlock.internal.IOPFacelockCallback"
        try {
            flInterface = getClassFromPkg(pkg, FACELOCK_INTERFACE)
            flInterfaceStub = getClassFromPkg(pkg, "$FACELOCK_INTERFACE\$Stub")
            flCallbackInterface = getClassFromPkg(pkg, FACELOCK_CALLBACK)
            flCallbackInterfaceStub = getClassFromPkg(pkg, "$FACELOCK_CALLBACK\$Stub")
            onePlusFaceSettings = OnePlusFaceSettings(mContext)
        } catch (ignored: Throwable) {
        }
        if (flCallbackInterfaceStub == null)
            throw RuntimeException(TAG + " not supported")
    }

    fun checkState(): Int {
        try {
            return onePlusFaceSettings?.checkState() ?: -1
        } catch (ignored: Throwable) {
        }
        return -1
    }

    fun bind(connection: ServiceConnection): Boolean {
        d(TAG + " bind to service")
        if (mServiceConnection != null) {
            return false
        }
        mServiceConnection = ServiceConnectionWrapper(connection)
        val intent = Intent()
        intent.component = ComponentName(pkg, "com.oneplus.faceunlock.FaceUnlockService")
        return mContext
            .bindService(intent, mServiceConnection ?: return false, Context.BIND_AUTO_CREATE)
    }

    fun unbind() {
        d(TAG + " unbind from service")
        mServiceConnection?.let {
            mContext.unbindService(it)
        }
        mServiceConnection = null
    }

    @Throws(
        RemoteException::class,
        IllegalArgumentException::class,
        IllegalAccessException::class,
        InvocationTargetException::class,
        NoSuchMethodException::class
    )
    fun startFaceUnlock() {
        d(TAG + " startUi")
        try {
            flInterface?.getMethod("prepare")?.invoke(mFaceLockService)
            val method = flInterface?.getMethod("startFaceUnlock", Int::class.javaPrimitiveType)
            method?.invoke(mFaceLockService, 0)
        } catch (ignore: Throwable) {
        }
    }

    @Throws(
        RemoteException::class,
        IllegalArgumentException::class,
        IllegalAccessException::class,
        InvocationTargetException::class,
        NoSuchMethodException::class
    )
    fun stopFaceUnlock() {
        d(TAG + " stopUi")
        try {
            flInterface?.getMethod(
                "stopFaceUnlock",
                Int::class.javaPrimitiveType
            )?.invoke(mFaceLockService, 0)
            flInterface?.getMethod("release")?.invoke(mFaceLockService)
        } catch (ignore: Throwable) {
        }
    }

    @Throws(
        NoSuchMethodException::class,
        ClassNotFoundException::class,
        IllegalArgumentException::class,
        IllegalAccessException::class,
        InvocationTargetException::class,
        InstantiationException::class
    )
    fun registerCallback(cb: IOPFacelockCallback?) {
        d(TAG + " registerCallback")
        val c = getClassFromPkg(pkg, flCallbackInterface?.name + "\$Stub\$Proxy")
            .getDeclaredConstructor(IBinder::class.java)
        val isAccessible = c.isAccessible
        try {
            if (!isAccessible) c.isAccessible = true
            val callbacks = c.newInstance(CallBackBinder(cb))
            flInterface?.getMethod("registerCallback", flCallbackInterface)
                ?.invoke(mFaceLockService, callbacks)
            mMap[cb] = callbacks
        } finally {
            if (!isAccessible) c.isAccessible = false
        }
    }

    @Throws(
        RemoteException::class,
        IllegalArgumentException::class,
        IllegalAccessException::class,
        InvocationTargetException::class,
        NoSuchMethodException::class
    )
    fun unregisterCallback(cb: IOPFacelockCallback?) {
        d(TAG + " unregisterCallback")
        flInterface?.getMethod("unregisterCallback", flCallbackInterface)
            ?.invoke(mFaceLockService, mMap[cb])
        mMap.remove(cb)
    }

    private inner class ServiceConnectionWrapper(private val mServiceConnection: ServiceConnection) :
        ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            d(TAG + " service connected")
            try {
                mFaceLockService = flInterfaceStub?.getMethod("asInterface", IBinder::class.java)
                    ?.invoke(null, service)
                mServiceConnection.onServiceConnected(name, service)
            } catch (e: IllegalArgumentException) {
                mFaceLockService = null
                e(e, TAG + e.message)
            } catch (e: IllegalAccessException) {
                mFaceLockService = null
                e(e, TAG + e.message)
            } catch (e: InvocationTargetException) {
                mFaceLockService = null
                e(e, TAG + e.message)
            } catch (e: NoSuchMethodException) {
                mFaceLockService = null
                e(e, TAG + e.message)
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            d(TAG + " service disconnected")
            mServiceConnection.onServiceDisconnected(name)
            mFaceLockService = null
        }
    }

    private inner class CallBackBinder(private val mCallback: IOPFacelockCallback?) :
        Binder() {
        private val mMap = HashMap<Int, String>()

        @Throws(RemoteException::class)
        override fun onTransact(
            code: Int, data: Parcel, reply: Parcel?,
            flags: Int
        ): Boolean {
            if (mMap.containsKey(code)) {
                d(TAG + (" onTransact " + mMap[code]))

                // Callback may be called outside the UI thread
                ExecutorHelper.INSTANCE.handler.post {
                    try {
                        IOPFacelockCallback::class.java.getMethod(mMap[code] ?: return@post)
                            .invoke(mCallback)
                    } catch (e: IllegalArgumentException) {
                        e(e, TAG + e.message)
                    } catch (e: IllegalAccessException) {
                        e(e, TAG + e.message)
                    } catch (e: InvocationTargetException) {
                        e(e, TAG + e.message)
                    } catch (e: NoSuchMethodException) {
                        e(e, TAG + e.message)
                    }
                }
                return true
            } else {
                d(TAG + " unknown transact : $code")
            }
            return super.onTransact(code, data, reply, flags)
        }

        init {

            // Find matching TRANSACTION_**** values
            val methods = IOPFacelockCallback::class.java.methods
            for (m in methods) {
                try {
                    val f = flCallbackInterfaceStub?.getDeclaredField("TRANSACTION_" + m.name)
                    val isAccessible = f?.isAccessible
                    try {
                        if (isAccessible == false) f.isAccessible = true
                        f?.isAccessible = true
                        mMap[f?.get(null) as Int] = m.name
                    } finally {
                        if (isAccessible == false) f.isAccessible = false
                    }
                } catch (ignore: NoSuchFieldException) {
                } catch (e: IllegalArgumentException) {
                    e(e, TAG)
                } catch (e: IllegalAccessException) {
                    e(e, TAG)
                }
            }
        }
    }
}