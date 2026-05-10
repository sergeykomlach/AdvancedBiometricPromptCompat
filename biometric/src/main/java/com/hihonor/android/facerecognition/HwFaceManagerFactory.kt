/*
 *  Copyright (c) 2023 Sergey Komlach aka Salat-Cx65; Original project https://github.com/Salat-Cx65/AdvancedBiometricPromptCompat
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

package com.hihonor.android.facerecognition

import android.content.Context
import android.os.Build.VERSION
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl
import java.lang.reflect.InvocationTargetException

object HwFaceManagerFactory {
    @Synchronized
    @JvmStatic
    fun getFaceManager(context: Context?): FaceManager? {
        try {
            if (VERSION.SDK_INT < 29) {
                BiometricLoggerImpl.d(
                    "FaceRecognize",
                    "The current version does not support face recognition"
                )
                return null
            }
            val t = Class.forName("com.hihonor.android.facerecognition.FaceManagerFactory")
            val method = t.getDeclaredMethod("getFaceManager", Context::class.java)
            return method.invoke(null as Any?, context) as FaceManager?
        } catch (var3: ClassNotFoundException) {
            BiometricLoggerImpl.d("FaceRecognize", "Throw exception: ClassNotFoundException")
        } catch (var4: NoSuchMethodException) {
            BiometricLoggerImpl.d("FaceRecognize", "Throw exception: NoSuchMethodException")
        } catch (var5: IllegalAccessException) {
            BiometricLoggerImpl.d("FaceRecognize", "Throw exception: IllegalAccessException")
        } catch (var6: InvocationTargetException) {
            BiometricLoggerImpl.d("FaceRecognize", "Throw exception: InvocationTargetException")
        }
        return null
    }
}