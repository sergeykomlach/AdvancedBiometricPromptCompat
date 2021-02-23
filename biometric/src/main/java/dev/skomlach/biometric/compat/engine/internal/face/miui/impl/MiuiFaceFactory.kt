package dev.skomlach.biometric.compat.engine.internal.face.miui.impl

import android.content.Context
import android.os.Build

object MiuiFaceFactory {
    const val TAG = "MiuiFaceFactory"
    const val TYPE_2D = 1
    const val TYPE_3D = 2
    const val TYPE_DEFAULT = 0
    var sCurrentAuthType = 0
    fun getFaceManager(context: Context, authType: Int): IMiuiFaceManager? {
        sCurrentAuthType = if (authType != TYPE_DEFAULT) {
            authType
        } else {
            getCurrentAuthType(context)
        }
        return if (sCurrentAuthType == TYPE_3D) {
            Miui3DFaceManagerImpl.getInstance(context)
        } else MiuiFaceManagerImpl.getInstance(context)
    }

    fun getCurrentAuthType(context: Context): Int {
        sCurrentAuthType = when {
            "ursa" != Build.DEVICE -> {
                TYPE_2D
            }
            MiuiFaceManagerImpl.getInstance(context)?.hasEnrolledFaces() != 0 -> {
                TYPE_2D
            }
            else -> {
                TYPE_3D
            }
        }
        return sCurrentAuthType
    }
}