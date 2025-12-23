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

package dev.skomlach.biometric.compat.engine

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.SurfaceView
import dev.skomlach.biometric.compat.AuthenticationFailureReason
import dev.skomlach.biometric.compat.AuthenticationResult
import dev.skomlach.biometric.compat.BiometricApi
import dev.skomlach.biometric.compat.BiometricAuthRequest
import dev.skomlach.biometric.compat.BiometricCryptoObject
import dev.skomlach.biometric.compat.BiometricCryptographyPurpose
import dev.skomlach.biometric.compat.BiometricManagerCompat
import dev.skomlach.biometric.compat.BiometricType
import dev.skomlach.biometric.compat.custom.AbstractCustomBiometricManager
import dev.skomlach.biometric.compat.custom.CustomBiometricProvider
import dev.skomlach.biometric.compat.engine.core.Core
import dev.skomlach.biometric.compat.engine.core.interfaces.AuthenticationListener
import dev.skomlach.biometric.compat.engine.core.interfaces.BiometricModule
import dev.skomlach.biometric.compat.engine.internal.AbstractBiometricModule
import dev.skomlach.biometric.compat.engine.internal.CustomBiometricModule
import dev.skomlach.biometric.compat.engine.internal.DummyBiometricModule
import dev.skomlach.biometric.compat.engine.internal.face.android.AndroidFaceUnlockModule
import dev.skomlach.biometric.compat.engine.internal.face.facelock.FacelockOldModule
import dev.skomlach.biometric.compat.engine.internal.face.hihonor.Hihonor3DFaceUnlockModule
import dev.skomlach.biometric.compat.engine.internal.face.hihonor.HihonorFaceUnlockModule
import dev.skomlach.biometric.compat.engine.internal.face.huawei.Huawei3DFaceUnlockModule
import dev.skomlach.biometric.compat.engine.internal.face.huawei.HuaweiFaceUnlockModule
import dev.skomlach.biometric.compat.engine.internal.face.lava.FaceunlockLavaModule
import dev.skomlach.biometric.compat.engine.internal.face.miui.MiuiFaceUnlockModule
import dev.skomlach.biometric.compat.engine.internal.face.oppo.OppoFaceUnlockModule
import dev.skomlach.biometric.compat.engine.internal.face.samsung.SamsungFaceUnlockModule
import dev.skomlach.biometric.compat.engine.internal.face.soter.SoterFaceUnlockModule
import dev.skomlach.biometric.compat.engine.internal.fingerprint.API23FingerprintModule
import dev.skomlach.biometric.compat.engine.internal.fingerprint.FlymeFingerprintModule
import dev.skomlach.biometric.compat.engine.internal.fingerprint.SamsungFingerprintModule
import dev.skomlach.biometric.compat.engine.internal.fingerprint.SoterFingerprintUnlockModule
import dev.skomlach.biometric.compat.engine.internal.fingerprint.SupportFingerprintModule
import dev.skomlach.biometric.compat.engine.internal.iris.android.AndroidIrisUnlockModule
import dev.skomlach.biometric.compat.engine.internal.iris.samsung.SamsungIrisUnlockModule
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.d
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.e
import dev.skomlach.common.misc.ExecutorHelper
import dev.skomlach.common.misc.Utils.startActivity
import java.lang.ref.SoftReference
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.ServiceLoader
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

object BiometricAuthentication {
    private val moduleHashMap = Collections
        .synchronizedMap(HashMap<BiometricMethod, BiometricModule>())

    private var initInProgress = AtomicBoolean(false)
    private var authInProgress = AtomicBoolean(false)
    private val customModuleHashMap = Collections
        .synchronizedMap(HashMap<BiometricMethod, AbstractCustomBiometricManager>())

    private val allMethods = ArrayList<BiometricMethod>().apply {

        //any API
        this.add(BiometricMethod.DUMMY_BIOMETRIC)
        this.add(BiometricMethod.FACELOCK)
        this.add(BiometricMethod.FACEUNLOCK_LAVA)

        //Samsung Pass on Kitkat-Marshmallow (4.4/5.x/6.x), then deprecated
        if (Build.VERSION.SDK_INT in 19..23) {
            this.add(BiometricMethod.FINGERPRINT_SAMSUNG)
        }
        //Meizu - Lollipop (5.0-5.1),then deprecated
        if (Build.VERSION.SDK_INT in 21..22) {
            this.add(BiometricMethod.FINGERPRINT_FLYME)
        }
        //Fingerprint API - Marshmallow (6.0)
        if (Build.VERSION.SDK_INT >= 23) {
            this.add(BiometricMethod.FINGERPRINT_SOTERAPI)
            this.add(BiometricMethod.FINGERPRINT_API23)
            this.add(BiometricMethod.FINGERPRINT_SUPPORT)
        }
        //Samsung Face/Iris - Nougat (7.0)+
        if (Build.VERSION.SDK_INT >= 24) {
            this.add(BiometricMethod.FACE_SOTERAPI)
            this.add(BiometricMethod.FACE_SAMSUNG)
            this.add(BiometricMethod.IRIS_SAMSUNG)
        }
        //Miui FaceUnlock - AOS7 - AOS13
        if (Build.VERSION.SDK_INT in 24..33) {
            this.add(BiometricMethod.FACE_MIUI)
        }
        //Honor and Huawei FaceUnlock - AOS5-AOS9
        if (Build.VERSION.SDK_INT in 21..28) {
            this.add(BiometricMethod.FACE_HUAWEI)
            this.add(BiometricMethod.FACE_HIHONOR)
        }
        //Honor and Huawei official FaceUnlock - AOS10+
        if (Build.VERSION.SDK_INT >= 29) {
            this.add(BiometricMethod.FACE_HUAWEI3D)
            this.add(BiometricMethod.FACE_HIHONOR3D)
        }
        //Android biometric - Pie (9.0)
        if (Build.VERSION.SDK_INT >= 28) {
            this.add(BiometricMethod.FACE_ANDROIDAPI)
            this.add(BiometricMethod.IRIS_ANDROIDAPI)
        }

    }

    @Volatile
    private var customLoaded = false
    fun loadCustomModules(context: Context) {
        d("BiometricAuthentication", "loadCustomModules called")
        try {
            if (customLoaded) return
            customLoaded = true
            val loader = ServiceLoader.load(CustomBiometricProvider::class.java)

            for (provider in loader) {
                try {
                    d(
                        "BiometricAuthentication",
                        "loadCustomModules provider ${provider.javaClass.simpleName}"
                    )
                    val customManager = provider.getCustomManager(context)
                    val targetType = customManager.biometricType

                    val isAlreadyRegistered = customModuleHashMap.keys.any { method ->
                        method.biometricType == targetType
                    }

                    if (!isAlreadyRegistered && !BiometricManagerCompat.isBiometricAvailable(
                            BiometricAuthRequest(BiometricApi.AUTO, targetType)
                        )
                    ) {
                        val newMethod =
                            BiometricMethod.createCustomModule(customManager.hashCode(), targetType)
                        registerCustomModule(newMethod, customManager)

                        d(
                            "BiometricAuthentication",
                            "Registered custom module: ${customManager.javaClass.simpleName}"
                        )
                    } else {
                        d(
                            "BiometricAuthentication",
                            "Ignored custom module ${customManager.javaClass.simpleName} because $targetType is already handled"
                        )
                    }
                } catch (e: Throwable) {
                    e("BiometricAuthentication", "Error loading custom module", e)
                }
            }
        } catch (e: Throwable) {
            e("BiometricAuthentication", "ServiceLoader failure", e)
        } finally {
            customLoaded = false
        }
    }

    private fun registerCustomModule(
        biometricMethod: BiometricMethod,
        provider: AbstractCustomBiometricManager
    ): Boolean {
        if (customModuleHashMap.any {
                it.key.id == biometricMethod.id
            }) return false

        customModuleHashMap[biometricMethod] = provider
        return true
    }


    @JvmOverloads
    fun init(
        globalInitListener: BiometricInitListener? = null,
        mlist: Collection<BiometricType>? = null
    ) {
        if (initInProgress.get())
            return
        initInProgress.set(true)
        val ts = System.currentTimeMillis()
        e("BiometricAuthentication.init() - started")
        //main thread required
        val allMethodsCopy = allMethods.toMutableList()
        customModuleHashMap.toMutableMap().forEach {
            allMethodsCopy.add(it.key)
        }
        val modulesMap = HashMap<BiometricMethod, BiometricModule?>()
        //launch in BG because for init needed about 2-3 seconds
        try {
            val list: MutableList<BiometricMethod>
            if (mlist.isNullOrEmpty()) list = allMethodsCopy else {
                list = ArrayList()
                for (method in allMethodsCopy) {
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

                    if (moduleReady) {
                        modulesMap[method] = module
                    }
                    globalInitListener?.initFinished(method, module)
                    if (remains == 0) {
                        moduleHashMap.apply {
                            clear()
                            putAll(modulesMap)
                        }
                        initInProgress.set(false)
                        e("BiometricAuthentication.init() - done; ts=${System.currentTimeMillis() - ts} ms")
                        globalInitListener?.onBiometricReady()

                    }
                }

                override fun onBiometricReady() {}
            }
            if (list.isEmpty()) {
                initInProgress.set(false)
                e("BiometricAuthentication.init() - done; ts=${System.currentTimeMillis() - ts} ms")
                globalInitListener?.onBiometricReady()
            } else
                for (method in list) {
                    initModule(method, initListener)
                }
        } catch (e: Throwable) {
            e(e, "BiometricAuthentication")
        }
    }

    private fun initModule(method: BiometricMethod, initListener: BiometricInitListener) {
        ExecutorHelper.startOnBackground {
            e("BiometricAuthentication.check started for $method")
            var biometricModule: BiometricModule? = null
            try {
                biometricModule = when (method) {
                    BiometricMethod.DUMMY_BIOMETRIC -> DummyBiometricModule(initListener)
                    BiometricMethod.FACELOCK -> FacelockOldModule(initListener)
                    BiometricMethod.FACEUNLOCK_LAVA -> FaceunlockLavaModule(initListener)
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
                    BiometricMethod.FACE_HUAWEI3D -> Huawei3DFaceUnlockModule(initListener)
                    BiometricMethod.FACE_HIHONOR -> HihonorFaceUnlockModule(initListener)
                    BiometricMethod.FACE_HIHONOR3D -> Hihonor3DFaceUnlockModule(initListener)
                    BiometricMethod.FACE_MIUI -> MiuiFaceUnlockModule(initListener)
                    BiometricMethod.FACE_SOTERAPI -> SoterFaceUnlockModule(initListener)
                    BiometricMethod.FACE_OPPO -> OppoFaceUnlockModule(initListener)
                    BiometricMethod.FACE_SAMSUNG -> SamsungFaceUnlockModule(initListener)
                    BiometricMethod.FACE_ANDROIDAPI -> AndroidFaceUnlockModule(initListener)
                    BiometricMethod.IRIS_SAMSUNG -> SamsungIrisUnlockModule(initListener)
                    BiometricMethod.IRIS_ANDROIDAPI -> AndroidIrisUnlockModule(initListener)
                    BiometricMethod.CUSTOM ->
                        CustomBiometricModule(
                            method,
                            customModuleHashMap[method]
                                ?: throw IllegalStateException("Unknown biometric type - $method"),
                            initListener
                        )

                    else -> throw IllegalStateException("Unknown biometric type - $method")
                }
            } catch (e: Throwable) {
                e(e, "BiometricAuthentication")
                initListener.initFinished(method, biometricModule)
            }
        }
    }

    fun updateBiometricEnrollChanged() {
        for (method in availableBiometrics) {
            val biometricModule = getAvailableBiometricModule(method)
            if (biometricModule?.isBiometricEnrollChanged == true)
                (biometricModule as? AbstractBiometricModule)?.updateBiometricEnrollChanged()
        }
    }

    fun isEnrollChanged(): Boolean {
        for (method in availableBiometrics) {
            if (getAvailableBiometricModule(method)?.isBiometricEnrollChanged == true) return true
        }
        return false
    }

    val availableBiometrics: List<BiometricType?>
        get() {
            val biometricMethodListInternal = HashSet<BiometricType?>()
            val moduleHashMap = HashMap<BiometricMethod, BiometricModule>(this.moduleHashMap)
            for (method in moduleHashMap.keys) {
                biometricMethodListInternal.add(method.biometricType)
            }
            return ArrayList(biometricMethodListInternal)
        }
    val availableBiometricMethods: List<BiometricMethod>
        get() {
            val biometricMethodListInternal = HashSet<BiometricMethod>()
            val moduleHashMap = HashMap<BiometricMethod, BiometricModule>(this.moduleHashMap)
            for (method in moduleHashMap.keys) {
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

    val hasEnrolled: Boolean
        get() {
            for (method in availableBiometrics) {
                if (getAvailableBiometricModule(method)?.hasEnrolled == true) return true
            }
            return false
        }


    fun authenticate(
        biometricCryptographyPurpose: BiometricCryptographyPurpose?,
        targetView: SurfaceView?,
        method: BiometricType,
        listener: BiometricAuthenticationListener,
        bundle: Bundle?
    ) {
        authenticate(biometricCryptographyPurpose, targetView, listOf(method), listener, bundle)
    }

    fun authenticate(
        biometricCryptographyPurpose: BiometricCryptographyPurpose?,
        targetView: SurfaceView?,
        requestedMethods: List<BiometricType?>,
        listener: BiometricAuthenticationListener,
        bundle: Bundle?
    ) {
        if (authInProgress.get() || requestedMethods.isEmpty()) return
        if (initInProgress.get()) {
            val reference = WeakReference(targetView)
            val startTime = System.currentTimeMillis()
            var timeout = false
            ExecutorHelper.startOnBackground {
                while (initInProgress.get()) {
                    timeout = System.currentTimeMillis() - startTime >= TimeUnit.SECONDS.toMillis(5)
                    if (timeout) {
                        break
                    }
                    try {
                        Thread.sleep(10)
                    } catch (ignore: InterruptedException) {
                    }
                }
                if (!initInProgress.get())
                    ExecutorHelper.post {
                        authenticate(
                            biometricCryptographyPurpose,
                            reference.get(),
                            requestedMethods,
                            listener,
                            bundle
                        )
                    } else listener.onFailure(
                    AuthenticationResult(
                        requestedMethods.first(),
                        null,
                        AuthenticationFailureReason.INTERNAL_ERROR,
                        "Can't start authenticate"
                    )
                )
            }
            return
        }
        d("BiometricAuthentication.authenticate")
        var isAtLeastOneFired = false
        val hashMap = HashMap<Int, BiometricType?>()
        Core.cleanModules()
        for (type in requestedMethods) {
            val biometricModule = getAvailableBiometricModule(type)
            if (biometricModule == null || !biometricModule.hasEnrolled) continue
            Core.registerModule(biometricModule)
            when (biometricModule) {
                is SoterFaceUnlockModule -> {
                    biometricModule.bundle = bundle
                }

                is SoterFingerprintUnlockModule -> {
                    biometricModule.bundle = bundle
                }
//                is HuaweiFaceUnlockModule -> {
//                    biometricModule.setCallerView(targetView)
//                }
//                is HihonorFaceUnlockModule -> {
//                    biometricModule.setCallerView(targetView)
//                }
//                is SamsungFaceUnlockModule -> {
//                    biometricModule.setCallerView(targetView)
//                }
//                is SamsungIrisUnlockModule -> {
//                    biometricModule.setCallerView(targetView)
//                }
                is FacelockOldModule -> {
                    biometricModule.setCallerView(targetView)
                }
            }
            hashMap[biometricModule.tag()] = type
            isAtLeastOneFired = true
        }
        if (!isAtLeastOneFired) {
            listener.onFailure(
                AuthenticationResult(
                    requestedMethods[0],
                    reason = AuthenticationFailureReason.NO_BIOMETRICS_REGISTERED,
                )
            )
            return
        } else {
            authInProgress.set(true)
            val ref = SoftReference(listener)
            Core.authenticate(biometricCryptographyPurpose, object : AuthenticationListener {
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
                    moduleTag: Int,
                    reason: AuthenticationFailureReason?,
                    description: CharSequence?
                ) {
                    ref.get()?.onFailure(
                        AuthenticationResult(
                            hashMap[moduleTag],
                            reason = reason,
                            description = description
                        )
                    )
                }

                override fun onCanceled(
                    moduleTag: Int,
                    reason: AuthenticationFailureReason?,
                    description: CharSequence?
                ) {
                    ref.get()?.onCanceled(
                        AuthenticationResult(
                            hashMap[moduleTag],
                            reason = reason,
                            description = description
                        )
                    )
                }
            })
        }
    }

    fun cancelAuthentication() {
        if (authInProgress.get()) {
            authInProgress.set(false)
            d("BiometricAuthentication.cancelAuthentication")
            ExecutorHelper.startOnBackground {
                for (method in availableBiometrics) {
                    val biometricModule = getAvailableBiometricModule(method)
                    if (biometricModule is FacelockOldModule) {
                        biometricModule.stopAuth()
                    }
                    if (biometricModule is FaceunlockLavaModule) {
                        biometricModule.stopAuth()
                    }
                }
                Core.cancelAuthentication()

                init(null, availableBiometrics.filterNotNull())
            }

        }
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