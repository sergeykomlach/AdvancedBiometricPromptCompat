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

package dev.skomlach.biometric.compat

import android.os.Bundle

object BundleBuilder {
    const val REGISTRATION = "registration"
    const val TITLE = "prompt_title"
    const val SUBTITLE = "prompt_subtitle"
    const val DESCRIPTION = "prompt_description"
    fun create(
        biometricBuilderCompat: BiometricPromptCompat.Builder
    ): Bundle {
        val title = biometricBuilderCompat.getTitle()?.toString()
        val subtitle = biometricBuilderCompat.getSubtitle()?.toString()
        val description = biometricBuilderCompat.getDescription()?.toString()
        return Bundle().apply {
            putBoolean(REGISTRATION, biometricBuilderCompat.registration)
            title?.let { this.putString(TITLE, it) }
            subtitle?.let { this.putString(SUBTITLE, it) }
            description?.let { this.putString(DESCRIPTION, it) }
        }
    }
}