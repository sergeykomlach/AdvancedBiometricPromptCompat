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

/*
See original thread
https://forum.xda-developers.com/showthread.php?p=25572510#post25572510
*/
package dev.skomlach.biometric.compat.engine.internal.face.facelock

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.os.RemoteException
import dev.skomlach.biometric.compat.utils.LockType.isBiometricWeakLivelinessEnabled
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.d
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.e
import dev.skomlach.common.contextprovider.AndroidContext
import dev.skomlach.common.misc.ExecutorHelper
import dev.skomlach.common.misc.ReflectionTools.getClassFromPkg
import java.lang.reflect.InvocationTargetException

@SuppressLint("PrivateApi")

class FaceLock {
    protected var mFaceLockService: Any? = null
    private var mServiceConnection: ServiceConnectionWrapper? = null
    protected var mMap = HashMap<IFaceLockCallback, Any>()
    private var flInterface: Class<*>? = null
    private var flInterfaceStub: Class<*>? = null
    private var flCallbackInterface: Class<*>? = null
    private var flCallbackInterfaceStub: Class<*>? = null
    private val pkg = "com.android.facelock"

    private val context = AndroidContext.appContext

    companion object {
        private val TAG = FaceLock::class.java.simpleName
    }

    init {
        val FACELOCK_INTERFACE = "com.android.internal.policy.IFaceLockInterface"
        val FACELOCK_CALLBACK = "com.android.internal.policy.IFaceLockCallback"
        val TRUSTEDFACE_INTERFACE = "com.android.facelock.ITrustedFaceInterface"
        val TRUSTEDFACE_CALLBACK = "com.android.facelock.ITrustedFaceCallback"
        try {
            flInterface = getClassFromPkg(pkg, FACELOCK_INTERFACE)
            flInterfaceStub = getClassFromPkg(pkg, "$FACELOCK_INTERFACE\$Stub")
            flCallbackInterface = getClassFromPkg(pkg, FACELOCK_CALLBACK)
            flCallbackInterfaceStub = getClassFromPkg(pkg, "$FACELOCK_CALLBACK\$Stub")
        } catch (ignored: Throwable) {
            try {
                flInterface = getClassFromPkg(pkg, TRUSTEDFACE_INTERFACE)
                flInterfaceStub = getClassFromPkg(pkg, "$TRUSTEDFACE_INTERFACE\$Stub")
                flCallbackInterface = getClassFromPkg(pkg, TRUSTEDFACE_CALLBACK)
                flCallbackInterfaceStub = getClassFromPkg(pkg, "$TRUSTEDFACE_CALLBACK\$Stub")
            } catch (ignored2: Throwable) {
            }
        }
        if (flCallbackInterfaceStub == null) {
            throw RuntimeException(TAG + " not supported")
        }
    }

    fun bind(connection: ServiceConnection): Boolean {
        d(TAG + " bind to service")
        if (mServiceConnection != null) {
            return false
        }
        mServiceConnection = ServiceConnectionWrapper(connection)
        val intent = Intent()
        intent.setPackage(pkg)
        return context
            .bindService(intent, mServiceConnection ?: return false, Context.BIND_AUTO_CREATE)
    }

    fun unbind() {
        d(TAG + " unbind from service")
        mServiceConnection?.let {
            context.unbindService(it)
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
    fun startUi(token: IBinder?, x: Int, y: Int, width: Int, height: Int) {
        d(TAG + " startUi")
        try {
            val method = flInterface?.getMethod("start")
            method?.invoke(mFaceLockService)
            return
        } catch (ignore: Throwable) {
        }
        //newer API
        try {
            val method = flInterface?.getMethod(
                "startUi",
                IBinder::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType
            )
            method?.invoke(
                mFaceLockService,
                token,
                x,
                y,
                width,
                height,
                isBiometricWeakLivelinessEnabled(context)
            )
            return
        } catch (ignore: Throwable) {
        }
        try {
            //older API's
            val method = flInterface?.getMethod(
                "startUi",
                IBinder::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            method?.invoke(mFaceLockService, token, x, y, width, height)
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
    fun stopUi() {
        d(TAG + " stopUi")
        try {
            flInterface?.getMethod("stop")?.invoke(mFaceLockService)
            return
        } catch (ignore: Throwable) {
        }
        try {
            flInterface?.getMethod("stopUi")?.invoke(mFaceLockService)
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
    fun registerCallback(cb: IFaceLockCallback) {
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
    fun unregisterCallback(cb: IFaceLockCallback) {
        d(TAG + " unregisterCallback")
        flInterface?.getMethod("unregisterCallback", flCallbackInterface)
            ?.invoke(mFaceLockService, mMap[cb])
        mMap.remove(cb)
    }

    private inner class ServiceConnectionWrapper constructor(private val mServiceConnection: ServiceConnection) :
        ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            d(TAG + " service connected to $name")
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
            d(TAG + " service disconnected from $name")
            mServiceConnection.onServiceDisconnected(name)
            mFaceLockService = null
        }
    }

    private inner class CallBackBinder(private val mCallback: IFaceLockCallback) :
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
                ExecutorHelper.post {
                    try {
                        IFaceLockCallback::class.java.getMethod(mMap[code] ?: return@post)
                            .invoke(mCallback)
                    } catch (e: Throwable) {
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
            val methods = IFaceLockCallback::class.java.methods
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