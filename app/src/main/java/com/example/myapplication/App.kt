package com.example.myapplication

import android.app.Application
import dev.skomlach.biometric.compat.BiometricPromptCompat

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        BiometricPromptCompat.init(null)
    }
}