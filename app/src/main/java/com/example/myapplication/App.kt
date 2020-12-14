package com.example.myapplication

import androidx.multidex.MultiDexApplication
import dev.skomlach.biometric.compat.BiometricApi
import dev.skomlach.biometric.compat.BiometricAuthRequest
import dev.skomlach.biometric.compat.BiometricPromptCompat
import dev.skomlach.biometric.compat.BiometricType
import java.util.*

class App : MultiDexApplication() {
    companion object {
        @JvmStatic
        val authRequestList = ArrayList<BiometricAuthRequest>()

        @JvmStatic
        val onInitListeners = ArrayList<OnInitFinished>()

        @JvmStatic
        var isReady = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        BiometricPromptCompat.init {
            for (api in BiometricApi.values()) {
                for (type in BiometricType.values()) {
                    val biometricAuthRequest = BiometricAuthRequest(api, type)
                    if (BiometricPromptCompat.isHardwareDetected(biometricAuthRequest)) {
                        authRequestList.add(biometricAuthRequest)
                    }
                }
            }
            for (listener in onInitListeners) {
                listener.onFinished()
            }
            onInitListeners.clear()
            isReady = true
        }
    }

    interface OnInitFinished {
        fun onFinished()
    }
}