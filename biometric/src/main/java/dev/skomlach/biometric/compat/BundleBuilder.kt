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
    fun create(
        biometricBuilderCompat: BiometricPromptCompat.Builder
    ): Bundle {
        val title = biometricBuilderCompat.getTitle()?.toString()
        val subtitle = biometricBuilderCompat.getSubtitle()?.toString()
        val description = biometricBuilderCompat.getDescription()?.toString()
        return Bundle().apply {
            putBoolean("registration", biometricBuilderCompat.registration)
            title?.let { this.putString("prompt_title", it) }
            subtitle?.let { this.putString("prompt_subtitle", it) }
            description?.let { this.putString("prompt_description", it) }
        }
    }
}