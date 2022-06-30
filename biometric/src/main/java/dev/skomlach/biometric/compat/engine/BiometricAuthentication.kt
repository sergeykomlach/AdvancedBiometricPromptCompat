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

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.view.View
import dev.skomlach.biometric.compat.AuthenticationFailureReason
import dev.skomlach.biometric.compat.AuthenticationResult
import dev.skomlach.biometric.compat.BiometricCryptoObject
import dev.skomlach.biometric.compat.BiometricType
import dev.skomlach.biometric.compat.crypto.CryptographyPurpose
import dev.skomlach.biometric.compat.engine.core.Core
import dev.skomlach.biometric.compat.engine.core.interfaces.AuthenticationListener
import dev.skomlach.biometric.compat.engine.core.interfaces.BiometricModule
import dev.skomlach.biometric.compat.engine.internal.DummyBiometricModule
import dev.skomlach.biometric.compat.engine.internal.face.android.AndroidFaceUnlockModule
import dev.skomlach.biometric.compat.engine.internal.face.facelock.FacelockOldModule
import dev.skomlach.biometric.compat.engine.internal.face.huawei.HuaweiFaceUnlockModule
import dev.skomlach.biometric.compat.engine.internal.face.miui.MiuiFaceUnlockModule
import dev.skomlach.biometric.compat.engine.internal.face.oppo.OppoFaceUnlockModule
import dev.skomlach.biometric.compat.engine.internal.face.samsung.SamsungFaceUnlockModule
import dev.skomlach.biometric.compat.engine.internal.face.soter.SoterFaceUnlockModule
import dev.skomlach.biometric.compat.engine.internal.face.vivo.VivoFaceUnlockModule
import dev.skomlach.biometric.compat.engine.internal.fingerprint.*
import dev.skomlach.biometric.compat.engine.internal.iris.android.AndroidIrisUnlockModule
import dev.skomlach.biometric.compat.engine.internal.iris.samsung.SamsungIrisUnlockModule
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.d
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.e
import dev.skomlach.common.misc.ExecutorHelper
import dev.skomlach.common.misc.Utils.startActivity
import java.lang.ref.SoftReference
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

object BiometricAuthentication {
    private val moduleHashMap = Collections
        .synchronizedMap(HashMap<BiometricMethod, BiometricModule>())

    @JvmOverloads
    fun init(
        globalInitListener: BiometricInitListener? = null,
        mlist: Collection<BiometricType>? = null
    ) {
        e("BiometricAuthentication.init()")
        //main thread required
        val allMethods = ArrayList<BiometricMethod>()

        //any API
        allMethods.add(BiometricMethod.DUMMY_BIOMETRIC)
        allMethods.add(BiometricMethod.FACELOCK)

        //Samsung Pass on Kitkat-Marshmallow (4.4/5.x/6.x), then deprecated
        if (Build.VERSION.SDK_INT in 19..23) {
            allMethods.add(BiometricMethod.FINGERPRINT_SAMSUNG)
        }
        //Meizu - Lollipop (5.0-5.1),then deprecated
        if (Build.VERSION.SDK_INT in 21..22) {
            allMethods.add(BiometricMethod.FINGERPRINT_FLYME)
        }
        //Fingerprint API - Marshmallow (6.0)
        if (Build.VERSION.SDK_INT >= 23) {
            allMethods.add(BiometricMethod.FINGERPRINT_SOTERAPI)
            allMethods.add(BiometricMethod.FINGERPRINT_API23)
            allMethods.add(BiometricMethod.FINGERPRINT_SUPPORT)
        }
        //Miui and Samsung Face/Iris - Nougat (7.0)
        if (Build.VERSION.SDK_INT >= 24) {
            allMethods.add(BiometricMethod.FACE_SOTERAPI)
            allMethods.add(BiometricMethod.FACE_SAMSUNG)
            allMethods.add(BiometricMethod.IRIS_SAMSUNG)
            allMethods.add(BiometricMethod.FACE_MIUI)
        }
        //Vivo and Huawei Face - Oreo (8.0)
        if (Build.VERSION.SDK_INT >= 26) {
            allMethods.add(BiometricMethod.FACE_VIVO)
            allMethods.add(BiometricMethod.FACE_HUAWEI)
        }
        //Android biometric - Pie (9.0)
        if (Build.VERSION.SDK_INT >= 28) {
            allMethods.add(BiometricMethod.FACE_ANDROIDAPI)
            allMethods.add(BiometricMethod.IRIS_ANDROIDAPI)
        }
        moduleHashMap.clear()
        //launch in BG because for init needed about 2-3 seconds
        try {
            val list: MutableList<BiometricMethod>
            if (mlist == null || mlist.isEmpty()) list = allMethods else {
                list = ArrayList()
                for (method in allMethods) {
                    for (type in mlist) {
                        if (method.biometricType == type) {
                            list.add(method)
                        }
                    }
                }
            }
            val counter = AtomicInteger(list.size)
            val initListener: BiometricInitListener = object : BiometricInitListener {
                override fun initFinished(method: BiometricMethod, module: BiometricModule?) {
                    val moduleReady =
                        module != null && module.isManagerAccessible && module.isHardwarePresent
                    val remains = counter.decrementAndGet()
                    d(
                        "BiometricAuthentication" + ("BiometricInitListener.initListener: '" + method
                                + "' hasManager: " + (module != null && module.isManagerAccessible) +
                                " hasHardware: " + (module != null && module.isHardwarePresent) + " remains: " + remains)
                    )
                    if (moduleReady) {
                        moduleHashMap[method] = module
                    }
                    globalInitListener?.initFinished(method, module)
                    if (remains == 0) {
                        globalInitListener?.onBiometricReady()
                        d("BiometricAuthentication" + "BiometricAuthentication ready")
                    }
                }

                override fun onBiometricReady() {}
            }
            for (method in list) {
                ExecutorHelper.startOnBackground {
                    e("BiometricAuthentication.check started for $method")
                    var biometricModule: BiometricModule? = null
                    try {
                        biometricModule = when (method) {
                            BiometricMethod.DUMMY_BIOMETRIC -> DummyBiometricModule(initListener)
                            BiometricMethod.FACELOCK -> FacelockOldModule(initListener)
                            BiometricMethod.FINGERPRINT_API23 -> API23FingerprintModule(initListener)
                            BiometricMethod.FINGERPRINT_SUPPORT -> SupportFingerprintModule(
                                initListener
                            )
                            BiometricMethod.FINGERPRINT_SAMSUNG -> SamsungFingerprintModule(
                                initListener
                            )
                            BiometricMethod.FINGERPRINT_FLYME -> FlymeFingerprintModule(initListener)
                            BiometricMethod.FINGERPRINT_SOTERAPI -> SoterFingerprintUnlockModule(
                                initListener
                            )
                            BiometricMethod.FACE_HUAWEI -> HuaweiFaceUnlockModule(initListener)
                            BiometricMethod.FACE_MIUI -> MiuiFaceUnlockModule(initListener)
                            BiometricMethod.FACE_SOTERAPI -> SoterFaceUnlockModule(initListener)
                            BiometricMethod.FACE_OPPO -> OppoFaceUnlockModule(initListener)
                            BiometricMethod.FACE_SAMSUNG -> SamsungFaceUnlockModule(initListener)
                            BiometricMethod.FACE_ANDROIDAPI -> AndroidFaceUnlockModule(initListener)
                            BiometricMethod.FACE_VIVO -> VivoFaceUnlockModule(initListener)
                            BiometricMethod.IRIS_SAMSUNG -> SamsungIrisUnlockModule(initListener)
                            BiometricMethod.IRIS_ANDROIDAPI -> AndroidIrisUnlockModule(initListener)
                            else -> throw IllegalStateException("Uknowon biometric type - $method")
                        }
                    } catch (e: Throwable) {
                        e(e, "BiometricAuthentication")
                        initListener.initFinished(method, biometricModule)
                    }
                }
            }
        } catch (e: Throwable) {
            e(e, "BiometricAuthentication")
        }
    }

    val availableBiometrics: List<BiometricType?>
        get() {
            val biometricMethodListInternal = HashSet<BiometricType?>()
            val moduleHashMap = HashMap<BiometricMethod, BiometricModule>(this.moduleHashMap)
            for (method in moduleHashMap.keys) {
                e("Module:$method")
                biometricMethodListInternal.add(method.biometricType)
            }
            return ArrayList(biometricMethodListInternal)
        }
    val availableBiometricMethods: List<BiometricMethod>
        get() {
            val biometricMethodListInternal = HashSet<BiometricMethod>()
            val moduleHashMap = HashMap<BiometricMethod, BiometricModule>(this.moduleHashMap)
            for (method in moduleHashMap.keys) {
                e("Module:$method")
                biometricMethodListInternal.add(method)
            }
            return ArrayList(biometricMethodListInternal)
        }
    val isLockOut: Boolean
        get() {
            var isLocked = availableBiometrics.isNotEmpty()
            for (method in availableBiometrics) {
                val module = getAvailableBiometricModule(method)
                if (module != null && !module.isLockOut) {
                    isLocked = false
                }
            }
            return isLocked
        }
    val isHardwareDetected: Boolean
        get() {
            for (method in availableBiometrics) {
                if (getAvailableBiometricModule(method)?.isHardwarePresent == true) return true
            }
            return false
        }

    fun hasEnrolled(): Boolean {
        for (method in availableBiometrics) {
            if (getAvailableBiometricModule(method)?.hasEnrolled() == true) return true
        }
        return false
    }

    fun isEnrollChanged(): Boolean {
        for (method in availableBiometrics) {
            if (getAvailableBiometricModule(method)?.isBiometricEnrollChanged == true) return true
        }
        return false
    }

    fun authenticate(
        cryptographyPurpose: CryptographyPurpose?,
        targetView: View?,
        method: BiometricType,
        listener: BiometricAuthenticationListener
    ) {
        authenticate(cryptographyPurpose, targetView, listOf(method), listener)
    }

    fun authenticate(
        cryptographyPurpose: CryptographyPurpose?,
        targetView: View?,
        requestedMethods: List<BiometricType?>,
        listener: BiometricAuthenticationListener
    ) {
        if (requestedMethods.isEmpty()) return
        d("BiometricAuthentication.authenticate")
        var isAtLeastOneFired = false
        val hashMap = HashMap<Int, BiometricType?>()
        Core.cleanModules()
        for (type in requestedMethods) {
            val biometricModule = getAvailableBiometricModule(type)
            if (biometricModule == null || !biometricModule.hasEnrolled()) continue
            Core.registerModule(biometricModule)
            when (biometricModule) {
                is FacelockOldModule -> {
                    biometricModule.setCallerView(targetView)
                }
            }
            hashMap[biometricModule.tag()] = type
            isAtLeastOneFired = true
        }
        if (!isAtLeastOneFired) {
            listener.onFailure(
                AuthenticationFailureReason.NO_BIOMETRICS_REGISTERED,
                requestedMethods[0]
            )
            return
        } else {
            val ref = SoftReference(listener)
            Core.authenticate(cryptographyPurpose, object : AuthenticationListener {
                override fun onHelp(msg: CharSequence?) {
                    ref.get()?.onHelp(msg)
                }

                override fun onSuccess(
                    moduleTag: Int,
                    biometricCryptoObject: BiometricCryptoObject?
                ) {
                    ref.get()
                        ?.onSuccess(AuthenticationResult(hashMap[moduleTag], biometricCryptoObject))
                }

                override fun onFailure(
                    reason: AuthenticationFailureReason?,
                    moduleTag: Int
                ) {
                    ref.get()?.onFailure(reason, hashMap[moduleTag])
                }

                override fun onCanceled(moduleTag: Int) {
                    ref.get()?.onCanceled(hashMap[moduleTag])
                }
            })
        }
    }

    fun cancelAuthentication() {
        d("BiometricAuthentication.cancelAuthentication")
        for (method in availableBiometrics) {
            val biometricModule = getAvailableBiometricModule(method)
            if (biometricModule is FacelockOldModule) {
                biometricModule.stopAuth()
            }
        }
        Core.cancelAuthentication()
    }

    fun openSettings(context: Activity, type: BiometricType): Boolean {
        return if (availableBiometricMethods.isEmpty()) {
            false
        } else openSettings(
            context,
            type,
            getAvailableBiometricModule(type)
        )
    }

    private fun openSettings(
        context: Activity,
        method: BiometricType,
        biometricModule: BiometricModule?
    ): Boolean {
        if (biometricModule is SamsungFingerprintModule && method == BiometricType.BIOMETRIC_FINGERPRINT) {
            if (biometricModule.openSettings(context)) return true
        }
        if (biometricModule is FacelockOldModule && method == BiometricType.BIOMETRIC_FACE &&
            startActivity(Intent(DevicePolicyManager.ACTION_SET_NEW_PASSWORD), context)
        ) {
            return true
        }
        if (biometricModule is MiuiFaceUnlockModule && method == BiometricType.BIOMETRIC_FACE && startActivity(
                Intent().setClassName("com.android.settings", "com.android.settings.Settings")
                    .putExtra(
                        ":android:show_fragment",
                        "com.android.settings.security.MiuiSecurityAndPrivacySettings"
                    ),
                context
            )
        ) {
            return true
        }
        if (biometricModule is HuaweiFaceUnlockModule && method == BiometricType.BIOMETRIC_FACE && startActivity(
                Intent().setClassName(
                    "com.android.settings",
                    "com.android.settings.facechecker.unlock.FaceUnLockSettingsActivity"
                ), context
            )
        ) {
            return true
        }

        if (BiometricType.BIOMETRIC_FINGERPRINT == method) {
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
//                val enrollIntent = Intent(Settings.ACTION_BIOMETRIC_ENROLL)
//                enrollIntent.putExtra(
//                    Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
//                    BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.BIOMETRIC_STRONG
//                )
//                if (startActivity(enrollIntent, context))
//                    return true
//            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val enrollIntent = Intent(Settings.ACTION_FINGERPRINT_ENROLL)
                if (startActivity(enrollIntent, context))
                    return true
            }
        }

        if (BiometricType.BIOMETRIC_FACE == method) {
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
//                val enrollIntent = Intent(Settings.ACTION_BIOMETRIC_ENROLL)
//                enrollIntent.putExtra(
//                    Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
//                    BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.BIOMETRIC_STRONG
//                )
//                if (startActivity(enrollIntent, context))
//                    return true
//            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                startActivity(Intent("android.settings.FACE_ENROLL"), context)
            ) {
                return true
            }
        }


        if (BiometricType.BIOMETRIC_IRIS == method) {
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
//                val enrollIntent = Intent(Settings.ACTION_BIOMETRIC_ENROLL)
//                enrollIntent.putExtra(
//                    Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
//                    BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.BIOMETRIC_STRONG
//                )
//                if (startActivity(enrollIntent, context))
//                    return true
//            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                startActivity(Intent("android.settings.IRIS_ENROLL"), context)
            ) {
                return true
            }
        }


        return false
    }

    fun getAvailableBiometricModule(biometricMethod: BiometricType?): BiometricModule? {
        var module: BiometricMethod? = null
        //lowest  ID == highest priority
        val moduleHashMap = HashMap<BiometricMethod, BiometricModule>(this.moduleHashMap)
        for (m in moduleHashMap.keys) {
            if (m.biometricType == biometricMethod) {
                if (module == null) module = m else if (module.id > m.id) {
                    module = m
                }
            }
        }
        return if (module == null) null else moduleHashMap[module]
    }
}