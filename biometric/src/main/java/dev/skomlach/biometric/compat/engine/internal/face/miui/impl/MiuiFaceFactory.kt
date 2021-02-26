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