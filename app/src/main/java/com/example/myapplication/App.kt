package com.example.myapplication

import android.app.Application
import androidx.multidex.MultiDexApplication
import dev.skomlach.biometric.compat.BiometricApi
import dev.skomlach.biometric.compat.BiometricAuthRequest
import dev.skomlach.biometric.compat.BiometricPromptCompat
import dev.skomlach.biometric.compat.BiometricType
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl

class App : MultiDexApplication() {
    override fun onCreate() {
        super.onCreate()
        BiometricPromptCompat.init {
            for (api in BiometricApi.values()) {
                for (type in BiometricType.values()) {
                    BiometricLoggerImpl.d(
                        "BiometricPromptCompat: Check ${api.name}/${type.name} - " + BiometricPromptCompat.isHardwareDetected(
                            BiometricAuthRequest(api, type)
                        )
                    );
                }
            }
        }
    }
}