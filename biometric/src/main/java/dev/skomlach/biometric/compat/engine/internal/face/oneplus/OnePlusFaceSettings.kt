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
import android.os.IBinder
import android.os.RemoteException
import androidx.annotation.RestrictTo
import dev.skomlach.biometric.compat.utils.ReflectionTools.getClassFromPkg
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.d
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.e
import java.lang.reflect.InvocationTargetException
import java.util.*

@SuppressLint("PrivateApi")
@RestrictTo(RestrictTo.Scope.LIBRARY)
internal class OnePlusFaceSettings(  //https://github.com/xayron/OPSystemUI/tree/d805abc13d081bd3579355a1075d4ae5e8be9270/sources/com/oneplus/faceunlock/internal
    private val mContext: Context
) {

    companion object {
        private val TAG = OnePlusFaceSettings::class.java.simpleName
    }

    private val pkg = "com.oneplus.faceunlock"
    private var flInterface: Class<*>? = null
    private var flInterfaceStub: Class<*>? = null
    private var mFaceLockService: Any? = null
    private var mServiceConnection: ServiceConnectionWrapper? = null
    private var mMap = HashMap<IOPFacelockCallback, Any>()

    init {
        val FACELOCK_INTERFACE = "com.oneplus.faceunlock.internal.IOPFacelockService"
        try {
            flInterface = getClassFromPkg(pkg, FACELOCK_INTERFACE)
            flInterfaceStub = getClassFromPkg(pkg, "$FACELOCK_INTERFACE\$Stub")
        } catch (ignored: Throwable) {
        }
        if (flInterfaceStub == null)
            throw RuntimeException(TAG + " not supported")
    }

    fun bind(connection: ServiceConnection): Boolean {
        d(TAG + " bind to service")
        if (mServiceConnection != null) {
            return false
        }
        mServiceConnection = ServiceConnectionWrapper(connection)
        val intent = Intent()
        intent.component = ComponentName(pkg, "com.oneplus.faceunlock.FaceSettingService")
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
    fun checkState(): Int {
        d(TAG + " startUi")
        try {
            val method = flInterface?.getMethod("checkState", Int::class.javaPrimitiveType)
            return method?.invoke(mFaceLockService, 0) as Int
        } catch (ignore: Throwable) {
        }
        return -1
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
}