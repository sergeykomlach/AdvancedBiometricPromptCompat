/*
 *  Copyright (c) 2021 Sergey Komlach aka Salat-Cx65; Original project https://github.com/Salat-Cx65/AdvancedBiometricPromptCompat
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


//
// Image acquisition messages.
//

enum class AuthenticationHelpReason(val id: Int) {
    /**
     * The image acquired was good.
     */
    BIOMETRIC_ACQUIRED_GOOD(0),

    /**
     * Only a partial biometric image was detected. During enrollment, the user should be informed
     * on what needs to happen to resolve this problem, e.g. "press firmly on sensor." (for
     * fingerprint)
     */
    BIOMETRIC_ACQUIRED_PARTIAL(1),

    /**
     * The biometric image was too noisy to process due to a detected condition or a possibly dirty
     * sensor (See [.BIOMETRIC_ACQUIRED_IMAGER_DIRTY]).
     */
    BIOMETRIC_ACQUIRED_INSUFFICIENT(2),

    /**
     * The biometric image was too noisy due to suspected or detected dirt on the sensor.  For
     * example, it's reasonable return this after multiple [.BIOMETRIC_ACQUIRED_INSUFFICIENT]
     * or actual detection of dirt on the sensor (stuck pixels, swaths, etc.). The user is expected
     * to take action to clean the sensor when this is returned.
     */
    BIOMETRIC_ACQUIRED_IMAGER_DIRTY(3),

    /**
     * The biometric image was unreadable due to lack of motion.
     */
    BIOMETRIC_ACQUIRED_TOO_SLOW(4),

    /**
     * The biometric image was incomplete due to quick motion. For example, this could also happen
     * if the user moved during acquisition. The user should be asked to repeat the operation more
     * slowly.
     */
    BIOMETRIC_ACQUIRED_TOO_FAST(5),

    /**
     * Hardware vendors may extend this list if there are conditions that do not fall under one of
     * the above categories. Vendors are responsible for providing error strings for these errors.
     */
    BIOMETRIC_ACQUIRED_VENDOR(6),

    BIOMETRICT_ACQUIRED_VENDOR_BASE(1000);

    companion object {

        fun getByCode(id: Int): AuthenticationHelpReason? {
            for (helpReason in values()) {
                if (helpReason.id == id) return helpReason
            }
            return null
        }
    }
}