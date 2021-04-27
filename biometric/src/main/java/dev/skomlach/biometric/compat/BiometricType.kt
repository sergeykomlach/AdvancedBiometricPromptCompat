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

package dev.skomlach.biometric.compat

import androidx.annotation.DrawableRes

enum class BiometricType(@DrawableRes val iconId: Int) {
    BIOMETRIC_FINGERPRINT(R.drawable.bio_ic_fingerprint),
    BIOMETRIC_FACE(R.drawable.bio_ic_face),
    BIOMETRIC_IRIS(R.drawable.bio_ic_iris),
    BIOMETRIC_VOICE(R.drawable.bio_ic_voice),
    BIOMETRIC_PALMPRINT(R.drawable.bio_ic_palm),
    BIOMETRIC_HEARTRATE(R.drawable.bio_ic_heartrate),
    BIOMETRIC_BEHAVIOR(R.drawable.bio_ic_behavior),
    BIOMETRIC_ANY(R.drawable.bio_ic_fingerprint)
}