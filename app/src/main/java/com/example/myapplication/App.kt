package com.example.myapplication

import androidx.multidex.MultiDexApplication
import dev.skomlach.biometric.compat.*
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
            authRequestList.addAll(BiometricPromptCompat.availableAuthRequests)
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