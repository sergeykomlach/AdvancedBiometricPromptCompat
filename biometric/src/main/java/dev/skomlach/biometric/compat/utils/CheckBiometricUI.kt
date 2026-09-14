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

package dev.skomlach.biometric.compat.utils

import android.content.Context
import dev.skomlach.biometric.compat.BiometricPromptCompat
import dev.skomlach.biometric.compat.impl.dialogs.SystemBiometricDialogResources
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl
import dev.skomlach.common.misc.SystemStringsHelper

object CheckBiometricUI {
    internal data class Provider(val name: String, val enabled: Boolean?, val revision: String?)

    /** Retained for callers of the old API; front-facing assets do not identify a sensor. */
    @Deprecated("Use DevicesWithKnownBugs.hasUnderDisplayFingerprint")
    fun hasSomethingFrontSensor(context: Context): Boolean {
        val deviceInfo = BiometricPromptCompat.deviceInfo
        return FingerprintSensorDetector.detect(context, deviceInfo?.sensors.orEmpty(),
            deviceInfo?.emulatorKind != null).placement == FingerprintSensorPlacement.UNDER_DISPLAY
    }

    internal fun provider(context: Context): Provider {
        val name = getBiometricUiPackage(context)
        return try {
            val info = context.packageManager.getPackageInfo(name, 0)
            Provider(name, info.applicationInfo?.enabled, "${info.lastUpdateTime}:${info.applicationInfo?.sourceDir}")
        } catch (_: Exception) {
            // Missing package visibility and an unavailable provider are indistinguishable here.
            Provider(name, null, null)
        } catch (_: LinkageError) { Provider(name, null, null) }
    }

    internal fun availability(context: Context, provider: Provider = provider(context)): BiometricUiAvailability {
        if (provider.enabled == false) return BiometricUiAvailability.UNAVAILABLE
        val readable = SystemBiometricDialogResources.cached(context)?.source?.startsWith("${provider.name}/") == true
        if (!readable) SystemBiometricDialogResources.warmUp(context)
        return resolveBiometricUiAvailability(provider.enabled, readable)
    }

    /** An unreadable or unrecognized layout is not evidence that the system has no prompt UI. */
    fun hasExists(context: Context): Boolean = availability(context) != BiometricUiAvailability.UNAVAILABLE

    fun getBiometricUiPackage(context: Context): String {
        return (SystemStringsHelper.getFromSystem(context, "config_biometric_prompt_ui_package")?.takeIf { it.isNotBlank() }
            ?: "com.android.systemui").also {
            BiometricLoggerImpl.d("CheckBiometricUI", it)
        }
    }
}
