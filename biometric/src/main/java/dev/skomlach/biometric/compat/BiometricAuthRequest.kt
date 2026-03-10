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

import android.os.Parcelable
import kotlinx.parcelize.Parcelize


@Parcelize
data class BiometricAuthRequest
@Deprecated("Instead of a direct constructor call, preferred to use BiometricAuthRequest.default().withXXX()")
constructor(
    val api: BiometricApi,
    val type: BiometricType,
    val confirmation: BiometricConfirmation,
    val provider: BiometricProviderType
) : Parcelable {
    fun withApi(api: BiometricApi) = this.copy(api = api)

    fun withType(type: BiometricType) = this.copy(type = type)

    fun withConfirmation(confirmation: BiometricConfirmation) =
        this.copy(confirmation = confirmation)

    fun withProvider(provider: BiometricProviderType) = this.copy(provider = provider)

    companion object {
        @JvmStatic
        fun default() = BiometricAuthRequest(
            api = BiometricApi.AUTO,
            type = BiometricType.BIOMETRIC_ANY,
            confirmation = BiometricConfirmation.ANY,
            provider = BiometricProviderType.COMBINED
        )
    }
}