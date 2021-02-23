package dev.skomlach.biometric.compat.utils

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import dev.skomlach.common.contextprovider.AndroidContext

object Vibro {
    private var v: Vibrator? = null
    private var audioManager: AudioManager? = null

    init {
        v = AndroidContext.appContext.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator?
        audioManager =
            AndroidContext.appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager?
    }

    private fun canVibrate(): Boolean {
        var canVibrate = false
        if (v?.hasVibrator() == true) canVibrate =
            audioManager?.ringerMode != AudioManager.RINGER_MODE_SILENT
        return canVibrate
    }

    @JvmStatic
    fun start() {
        if (canVibrate()) {
            if (Build.VERSION.SDK_INT < 26)
                v?.vibrate(75)
            else try {
                v?.vibrate(VibrationEffect.createOneShot(75, VibrationEffect.DEFAULT_AMPLITUDE))
            } catch (ignore: Throwable) {
                v?.vibrate(75)
            }
        }
    }
}