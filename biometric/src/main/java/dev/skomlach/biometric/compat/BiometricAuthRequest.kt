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


/**
 * Immutable selector for the biometric route used by manager and prompt APIs.
 *
 * Use [default] and the `with...` copy helpers for source and binary stability;
 * the primary constructor is kept only for existing callers.
 */
@Parcelize
data class BiometricAuthRequest
@Deprecated("Instead of a direct constructor call, preferred to use BiometricAuthRequest.default().withXXX()")
constructor(
    /** Android API family that should be queried or used for authentication. */
    val api: BiometricApi,
    /** Biometric modality requested by the caller. */
    val type: BiometricType,
    /** Whether one successful provider is enough or all selected providers must confirm. */
    val confirmation: BiometricConfirmation,
    /** Hardware/software provider group allowed for this request. */
    val provider: BiometricProviderType
) : Parcelable {
    /** Returns a copy that targets a different Android biometric API route. */
    fun withApi(api: BiometricApi) = this.copy(api = api)

    /** Returns a copy that targets a different biometric modality. */
    fun withType(type: BiometricType) = this.copy(type = type)

    /** Returns a copy that changes the confirmation policy. */
    fun withConfirmation(confirmation: BiometricConfirmation) =
        this.copy(confirmation = confirmation)

    /** Returns a copy that changes the hardware/software provider selection. */
    fun withProvider(provider: BiometricProviderType) = this.copy(provider = provider)

    companion object {
        /** Default request: automatic API, any biometric type, any confirmation, combined providers. */
        @Suppress("DEPRECATION")
        @JvmStatic
        fun default() = BiometricAuthRequest(
            api = BiometricApi.AUTO,
            type = BiometricType.BIOMETRIC_ANY,
            confirmation = BiometricConfirmation.ANY,
            provider = BiometricProviderType.COMBINED
        )
    }
}
