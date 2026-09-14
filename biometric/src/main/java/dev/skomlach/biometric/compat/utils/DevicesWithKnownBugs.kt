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

package dev.skomlach.biometric.compat.utils

import android.content.pm.PackageManager
import android.os.Build
import androidx.biometric.R
import dev.skomlach.biometric.compat.BiometricPromptCompat
import dev.skomlach.common.contextprovider.AndroidContext.appContext
import dev.skomlach.common.device.DeviceModelManager
import dev.skomlach.common.misc.Utils
import dev.skomlach.common.storage.SharedPreferenceProvider
import java.lang.reflect.Modifier

object DevicesWithKnownBugs {

    private val preferences by lazy {
        SharedPreferenceProvider.getPreferences("BiometricCompat_ManagerCompat")
    }
    private val capabilityCache by lazy {
        BiometricCapabilityCache(
            read = { preferences.getString(it, null) },
            write = { key, value -> preferences.edit().putString(key, value).apply() }
        )
    }

    private fun configurationInputs() = listOf(Build.FINGERPRINT, appContext.resources.configuration.toString())

    private val buildStringFields by lazy {
        Build::class.java.fields.filter {
            !Modifier.isPrivate(it.modifiers) && it.type == String::class.java
        }
    }


    //Users reports that on LG devices have a bug with wrong/missing BiometricUI
    //After digging I found that it seems like system BiometricPrompt simply missing on this device
    //https://lg-firmwares.com/models-list/
    private val lgWithMissedBiometricUI = arrayOf(
        //G8 ThinQ
        "G820",
        //G8S ThinQ
        "G810",
        //G8X ThinQ
        "G850",
        //Velvet/Velvet 5G
        "G900",
        //Velvet 4G Dual Sim
        "G910",
    )

    private val isOnePlusWithBiometricBug: Boolean
        get() = (isOnePlus && !Utils.isAtLeastS) && hasUnderDisplayFingerprint

    val isHideDialogInstantly: Boolean
        get() {
            val modelPrefixes =
                appContext.resources.getStringArray(R.array.hide_fingerprint_instantly_prefixes)
            for (modelPrefix in modelPrefixes) {
                if (Build.MODEL.startsWith(modelPrefix)) {
                    return true
                }
            }
            return isOnePlus && Utils.isAtLeastS && hasUnderDisplayFingerprint
        }


    private val isSamsungVendorDevice: Boolean
        get() {
            return checkForVendor("Samsung", ignoreCase = true)
        }
    val isOnePlus: Boolean
        get() {
            return checkForVendor("OnePlus", ignoreCase = true)
        }

    val hasExplicitMissingBiometricUiBug: Boolean
        get() {
            return (checkForVendor("LG", ignoreCase = false) &&
                    lgWithMissedBiometricUI.any { knownModel ->
                        Build.MODEL.contains(knownModel, ignoreCase = true)
                    }) || isOnePlusWithBiometricBug
        }

    val isMissedBiometricUI: Boolean
        get() {
            // Recheck provider revision/enabled state even on a cache hit (package updates/settings).
            val provider = CheckBiometricUI.provider(appContext)
            val explicitBug = hasExplicitMissingBiometricUiBug
            val inputs = configurationInputs() + listOf(provider.name, provider.revision.orEmpty(),
                provider.enabled.toString(), explicitBug.toString())
            val cacheableProvider = provider.revision != null && provider.enabled != null
            if (cacheableProvider) capabilityCache.get("isMissedBiometricUI", inputs)?.toBooleanStrictOrNull()?.let { return it }
            val availability = CheckBiometricUI.availability(appContext, provider)
            val missing = explicitBug || availability == BiometricUiAvailability.UNAVAILABLE
            // Do not turn a cold resource reader or missing package visibility into a permanent false.
            if (cacheableProvider && (explicitBug || availability != BiometricUiAvailability.UNKNOWN)) {
                capabilityCache.put("isMissedBiometricUI", inputs, missing.toString())
            }
            return missing
        }

    internal val fingerprintSensor: FingerprintSensorEvidence
        get() {
            val deviceInfo = BiometricPromptCompat.deviceInfo
            val sensors = deviceInfo?.sensors.orEmpty().toSet()
            val isEmulator = deviceInfo?.emulatorKind != null
            val inputs = configurationInputs() + listOf("emulator=$isEmulator") + sensors.sorted()
            capabilityCache.get("hasUnderDisplayFingerprint", inputs)?.let { stored ->
                val fields = stored.split('|')
                val placement = FingerprintSensorPlacement.entries.firstOrNull { it.name == fields.firstOrNull() }
                val source = fields.getOrNull(1)
                if (fields.size == 2 && placement != null && placement != FingerprintSensorPlacement.UNKNOWN &&
                    source in setOf("framework-udfps-config", "framework-side-config", "device-database-placement")) {
                    return FingerprintSensorEvidence(placement, source!!)
                }
            }
            return FingerprintSensorDetector.detect(appContext, sensors, isEmulator).also { evidence ->
                if (evidence.placement != FingerprintSensorPlacement.UNKNOWN && !evidence.conflicting) {
                    capabilityCache.put("hasUnderDisplayFingerprint", inputs, "${evidence.placement}|${evidence.source}")
                }
            }
        }

    // Versioned evidence replaces old persisted guesses; missing metadata is never persisted.
    val hasUnderDisplayFingerprint: Boolean
        get() = fingerprintSensor.placement == FingerprintSensorPlacement.UNDER_DISPLAY

    private fun checkForVendor(vendor: String, ignoreCase: Boolean): Boolean {
        val capability = "checkForVendor-$vendor-$ignoreCase"
        val inputs = listOf(Build.FINGERPRINT)
        return capabilityCache.get(capability, inputs)?.toBooleanStrictOrNull()
            ?: checkVendor(vendor, ignoreCase).also { capabilityCache.put(capability, inputs, it.toString()) }
    }

    private fun checkVendor(vendor: String, ignoreCase: Boolean): Boolean {
        for (f in buildStringFields) try {
            val value = f[null] as String
            if (value.contains(vendor, ignoreCase = ignoreCase)) return true
        } catch (ignore: Throwable) {

        }
        return false
    }

    val isChromeBook: Boolean
        get() {
            //https://developer.chrome.com/apps/getstarted_arc
            //https://github.com/google/talkback/blob/master/src/main/java/com/google/android/marvin/talkback/TalkBackService.java#L1779-L1781
            //https://stackoverflow.com/a/39843396
            return (checkForVendor(
                "Chromium",
                ignoreCase = true
            ) || Build.DEVICE != null && Build.DEVICE.matches(Regex(".+_cheets"))
                    || appContext.packageManager.hasSystemFeature("org.chromium.arc.device_management"))
                    || appContext.packageManager.hasSystemFeature("org.chromium.arc")
        }

    val isFoldable: Boolean
        get() {
            if (appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_HINGE_ANGLE))
                return true
            else
                if (isChromeBook) return true
                else if (isSamsungVendorDevice) {
                    val model = BiometricPromptCompat.deviceInfo?.model
                        ?: DeviceModelManager.getDeviceModel().deviceName
                    return model.contains("Flip") || model.contains("Fold")
                }

            return false
        }
}
