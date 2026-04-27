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

package dev.skomlach.biometric.compat.engine

import dev.skomlach.biometric.compat.BiometricType

enum class BiometricMethod(id: Int, biometricType: BiometricType) {
    CUSTOM_UNDEFINED(0, BiometricType.BIOMETRIC_ANY),
    CUSTOM_FINGERPRINT(1, BiometricType.BIOMETRIC_FINGERPRINT),
    CUSTOM_FACE(2, BiometricType.BIOMETRIC_FACE),
    CUSTOM_IRIS(3, BiometricType.BIOMETRIC_IRIS),
    CUSTOM_VOICE(4, BiometricType.BIOMETRIC_VOICE),
    CUSTOM_PALMPRINT(5, BiometricType.BIOMETRIC_PALMPRINT),
    CUSTOM_HEARTRATE(6, BiometricType.BIOMETRIC_HEARTRATE),
    CUSTOM_BEHAVIOR(7, BiometricType.BIOMETRIC_BEHAVIOR),
    FINGERPRINT_API23(100, BiometricType.BIOMETRIC_FINGERPRINT),
    FINGERPRINT_SUPPORT(101, BiometricType.BIOMETRIC_FINGERPRINT),
    FINGERPRINT_SOTERAPI(102, BiometricType.BIOMETRIC_FINGERPRINT),

    FACE_ANDROIDAPI(201, BiometricType.BIOMETRIC_FACE),
    FACE_SAMSUNG(
        202,
        BiometricType.BIOMETRIC_FACE
    ), //https://github.com/fonix232/SCoverRE/blob/2374565740e4c7bfc653b3f05bd9be519e722e32/Reversed/framework/com/samsung/android/bio/face/SemBioFaceManager.java
    FACE_OPPO(203, BiometricType.BIOMETRIC_FACE),
    FACE_HUAWEI3D(
        204,
        BiometricType.BIOMETRIC_FACE
    ),//https://developer.huawei.com/consumer/en/doc/development/Security-References/overview-0000001051748079
    FACE_HUAWEI(205, BiometricType.BIOMETRIC_FACE),
    FACE_SOTERAPI(206, BiometricType.BIOMETRIC_FACE), //https://github.com/Tencent/soter
    FACE_MIUI(207, BiometricType.BIOMETRIC_FACE),

    FACEUNLOCK_LAVA(209, BiometricType.BIOMETRIC_FACE),

    //same that Huawei
    FACE_HIHONOR3D(
        210,
        BiometricType.BIOMETRIC_FACE
    ),
    FACE_HIHONOR(211, BiometricType.BIOMETRIC_FACE),

    FACELOCK(
        299,
        BiometricType.BIOMETRIC_FACE
    ),  //old FaceLock impl https://forum.xda-developers.com/showthread.php?p=25572510#post25572510

    IRIS_ANDROIDAPI(
        300,
        BiometricType.BIOMETRIC_IRIS
    ), //https://android-review.googlesource.com/c/platform/frameworks/base/+/608396/2/core/java/android/hardware/iris/IrisManager.java#46
    IRIS_SAMSUNG(
        301,
        BiometricType.BIOMETRIC_IRIS
    ),  //https://github.com/fonix232/SCoverRE/blob/2374565740e4c7bfc653b3f05bd9be519e722e32/Reversed/framework/com/samsung/android/camera/iris/SemIrisManager.java
    DUMMY_BIOMETRIC(9999, BiometricType.BIOMETRIC_ANY);

    var id: Int = id
        private set
    var biometricType: BiometricType = biometricType
        private set

    companion object {
        @Suppress("UNUSED_PARAMETER")
        fun createCustomModule(id: Int, biometricType: BiometricType): BiometricMethod =
            when (biometricType) {
                BiometricType.BIOMETRIC_FINGERPRINT -> CUSTOM_FINGERPRINT
                BiometricType.BIOMETRIC_FACE -> CUSTOM_FACE
                BiometricType.BIOMETRIC_IRIS -> CUSTOM_IRIS
                BiometricType.BIOMETRIC_VOICE -> CUSTOM_VOICE
                BiometricType.BIOMETRIC_PALMPRINT -> CUSTOM_PALMPRINT
                BiometricType.BIOMETRIC_HEARTRATE -> CUSTOM_HEARTRATE
                BiometricType.BIOMETRIC_BEHAVIOR -> CUSTOM_BEHAVIOR
                BiometricType.BIOMETRIC_ANY -> CUSTOM_UNDEFINED
            }.apply {
                if (entries.any {
                        it.id == id
                    }) throw IllegalArgumentException("This ID already used")
                this.id = id
                this.biometricType = biometricType
            }
    }
}
