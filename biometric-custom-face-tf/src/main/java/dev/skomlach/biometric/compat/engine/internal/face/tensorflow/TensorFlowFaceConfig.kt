/*
 *  Copyright (c) 2025 Sergey Komlach aka Salat-Cx65; Original project https://github.com/Salat-Cx65/AdvancedBiometricPromptCompat
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

package dev.skomlach.biometric.compat.engine.internal.face.tensorflow

import androidx.annotation.FloatRange
import androidx.annotation.IntRange

data class TensorFlowFaceConfig(
    /*
    "How closely two faces resemble each other"
    Suggested to use a range of 0.7-0.8 as a compromise between usability and security
    <=0.7 - more accurate, but slower
    >= 0.8 - less accurate, but faster
    */
    @FloatRange(from = 0.5, to = 1.0)
    val maxDistanceThresholds: Float = 0.75f,
    /*
    The number of success Consecutive Matches
    */
    @IntRange(from = 1)
    val requiredConsecutiveMatches: Int = 3
) {
    init {
        require(maxDistanceThresholds in 0.5f..1.0f) {
            "maxDistanceThresholds must be between 0.5 and 1.0, but was $maxDistanceThresholds"
        }

        require(requiredConsecutiveMatches > 0) {
            "requiredConsecutiveMatches must be positive, but was $requiredConsecutiveMatches"
        }
    }
}