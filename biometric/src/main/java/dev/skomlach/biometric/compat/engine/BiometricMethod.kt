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

package dev.skomlach.biometric.compat.engine

import androidx.annotation.RestrictTo
import dev.skomlach.biometric.compat.BiometricType

@RestrictTo(RestrictTo.Scope.LIBRARY)
enum class BiometricMethod(val id: Int, val biometricType: BiometricType) {
    FINGERPRINT_API23(100, BiometricType.BIOMETRIC_FINGERPRINT), FINGERPRINT_SUPPORT(
        101,
        BiometricType.BIOMETRIC_FINGERPRINT
    ),
    FINGERPRINT_SAMSUNG(
        102,
        BiometricType.BIOMETRIC_FINGERPRINT
    ),  //http://open-wiki.flyme.cn/index.php?title=%E6%8C%87%E7%BA%B9%E8%AF%86%E5%88%ABAPI
    FINGERPRINT_FLYME(103, BiometricType.BIOMETRIC_FINGERPRINT), FINGERPRINT_SOTERAPI(
        104,
        BiometricType.BIOMETRIC_FINGERPRINT
    ),  //https://android-review.googlesource.com/c/platform/frameworks/base/+/640360/2/core/java/android/hardware/face/FaceAuthenticationManager.java#498
    FACE_ANDROIDAPI(
        201,
        BiometricType.BIOMETRIC_FACE
    ),  //https://github.com/fonix232/SCoverRE/blob/2374565740e4c7bfc653b3f05bd9be519e722e32/Reversed/framework/com/samsung/android/bio/face/SemBioFaceManager.java
    FACE_SAMSUNG(202, BiometricType.BIOMETRIC_FACE), FACE_OPPO(
        203,
        BiometricType.BIOMETRIC_FACE
    ),  //https://developer.huawei.com/consumer/en/doc/development/system-Guides/32002
    FACE_HUAWEI(205, BiometricType.BIOMETRIC_FACE),  //https://github.com/Tencent/soter
    FACE_SOTERAPI(206, BiometricType.BIOMETRIC_FACE), FACE_MIUI(
        207,
        BiometricType.BIOMETRIC_FACE
    ),
    FACE_VIVO(
        209,
        BiometricType.BIOMETRIC_FACE
    ),  //old FaceLock impl

    //https://forum.xda-developers.com/showthread.php?p=25572510#post25572510
    FACELOCK(
        299,
        BiometricType.BIOMETRIC_FACE
    ),  //https://android-review.googlesource.com/c/platform/frameworks/base/+/608396/2/core/java/android/hardware/iris/IrisManager.java#46
    IRIS_ANDROIDAPI(
        300,
        BiometricType.BIOMETRIC_IRIS
    ),  //https://github.com/fonix232/SCoverRE/blob/2374565740e4c7bfc653b3f05bd9be519e722e32/Reversed/framework/com/samsung/android/camera/iris/SemIrisManager.java
    IRIS_SAMSUNG(301, BiometricType.BIOMETRIC_IRIS), DUMMY_BIOMETRIC(
        9999,
        BiometricType.BIOMETRIC_ANY
    );
}