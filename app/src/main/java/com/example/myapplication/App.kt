package com.example.myapplication

import androidx.multidex.MultiDexApplication
import dev.skomlach.biometric.compat.BiometricAuthRequest
import dev.skomlach.biometric.compat.BiometricPromptCompat
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
        LogCat.instance.setLog2ViewCallback(object : LogCat.Log2ViewCallback{
            override fun log(string: String?) {
                LogCat.instance.setLog2ViewCallback(null)
                BiometricPromptCompat.logging(true)
                BiometricPromptCompat.init {
                    authRequestList.addAll(BiometricPromptCompat.availableAuthRequests)
                    for (listener in onInitListeners) {
                        listener.onFinished()
                    }
                    onInitListeners.clear()
                    isReady = true
                }
            }
        })

        LogCat.instance.start()
    }

    interface OnInitFinished {
        fun onFinished()
    }
}