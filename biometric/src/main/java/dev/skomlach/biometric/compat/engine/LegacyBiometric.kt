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

import android.app.admin.DevicePolicyManager
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
import dev.skomlach.biometric.compat.BundleBuilder
import dev.skomlach.biometric.compat.custom.AbstractSoftwareBiometricManager
import dev.skomlach.biometric.compat.custom.SoftwareBiometricProvider
import dev.skomlach.biometric.compat.engine.core.Core
import dev.skomlach.biometric.compat.engine.core.interfaces.AuthenticationListener
import dev.skomlach.biometric.compat.engine.core.interfaces.BiometricModule
import dev.skomlach.biometric.compat.engine.internal.AbstractBiometricModule
import dev.skomlach.biometric.compat.engine.internal.DummyBiometricModule
import dev.skomlach.biometric.compat.engine.internal.SoftwareBiometricModule
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
import dev.skomlach.biometric.compat.engine.internal.fingerprint.SoterFingerprintUnlockModule
import dev.skomlach.biometric.compat.engine.internal.fingerprint.SupportFingerprintModule
import dev.skomlach.biometric.compat.engine.internal.iris.android.AndroidIrisUnlockModule
import dev.skomlach.biometric.compat.engine.internal.iris.samsung.SamsungIrisUnlockModule
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.d
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.e
import dev.skomlach.common.contextprovider.AndroidContext
import dev.skomlach.common.misc.ExecutorHelper
import java.lang.ref.SoftReference
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.ServiceLoader
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

object LegacyBiometric {
    private val moduleHashMap =
        Collections.synchronizedMap(HashMap<BiometricMethod, BiometricModule>())
    private val customModuleHashMap =
        Collections.synchronizedMap(HashMap<BiometricMethod, AbstractSoftwareBiometricManager>())

    private val initInProgress = AtomicBoolean(false)
    private val authInProgress = AtomicBoolean(false)

    @Volatile
    private var customLoading = false

    private val allMethods = Collections.synchronizedList(ArrayList<BiometricMethod>()).apply {
        add(BiometricMethod.DUMMY_BIOMETRIC)
        add(BiometricMethod.FACELOCK)
        add(BiometricMethod.FACEUNLOCK_LAVA)

        add(BiometricMethod.FINGERPRINT_SOTERAPI)
        add(BiometricMethod.FINGERPRINT_API23)
        add(BiometricMethod.FINGERPRINT_SUPPORT)

        if (Build.VERSION.SDK_INT >= 24) {
            add(BiometricMethod.FACE_SOTERAPI)
            add(BiometricMethod.FACE_SAMSUNG)
            add(BiometricMethod.IRIS_SAMSUNG)
        }
        if (Build.VERSION.SDK_INT in 24..33) {
            add(BiometricMethod.FACE_MIUI)
        }
        if (Build.VERSION.SDK_INT in 21..28) {
            add(BiometricMethod.FACE_HUAWEI)
            add(BiometricMethod.FACE_HIHONOR)
        }
        if (Build.VERSION.SDK_INT >= 29) {
            add(BiometricMethod.FACE_HUAWEI3D)
            add(BiometricMethod.FACE_HIHONOR3D)
        }
        if (Build.VERSION.SDK_INT >= 28) {
            add(BiometricMethod.FACE_ANDROIDAPI)
            add(BiometricMethod.IRIS_ANDROIDAPI)
        }
    }

    fun resetSoftwarePermanentLockOut() {
        synchronized(customModuleHashMap) {
            customModuleHashMap.values.forEach { module ->
                module.resetPermanentLockOut()

            }
        }
    }

    fun resetSoftwareLockOut() {
        synchronized(customModuleHashMap) {
            customModuleHashMap.values.forEach { module ->
                module.resetLockOut()

            }
        }
    }

    fun getSoftwareModulePermissions(): List<String> {
        val permission = mutableListOf<String>()
        synchronized(customModuleHashMap) {
            customModuleHashMap.values.forEach { module ->
                permission.addAll(module.getPermissions())

            }
        }
        return permission
    }
    fun rollbackLastEnrollInSoftwareModules() {
        d("BiometricAuthentication", "rollbackLastEnroll")
        synchronized(moduleHashMap) {
            moduleHashMap.values.forEach { module ->
                if (module is SoftwareBiometricModule) module.rollbackLastEnroll()
            }
        }
    }

    fun unregisterAllNonHardwareBiometrics() {
        synchronized(customModuleHashMap) {
            customModuleHashMap.values.forEach {
                it.remove(null)
            }
        }
    }

    fun unloadSoftwareModules() {
        if (customLoading) return
        d("BiometricAuthentication", "resetSoftwareModules called")
        try {
            customLoading = true
            val keysToRemove = synchronized(customModuleHashMap) {
                val keys = customModuleHashMap.toMutableMap()
                customModuleHashMap.clear()
                keys
            }
            synchronized(moduleHashMap) {
                keysToRemove.forEach {
                    moduleHashMap.remove(it.key)
                }
            }
        } catch (e: Throwable) {
            e("BiometricAuthentication", "resetSoftwareModules failure", e)
        } finally {
            customLoading = false
        }
    }

    fun loadSoftwareModules() {
        if (customLoading || customModuleHashMap.isNotEmpty()) return
        d("BiometricAuthentication", "loadSoftwareModules called")
        try {
            customLoading = true
            val loader = ServiceLoader.load(SoftwareBiometricProvider::class.java)
            val newSoftwareModules = HashMap<BiometricMethod, BiometricModule>()

            for (provider in loader) {
                try {
                    val customManager = provider.getCustomManager(AndroidContext.appContext)
                    val targetType = customManager.biometricType

                    val isAlreadyRegistered = synchronized(customModuleHashMap) {
                        customModuleHashMap.values.any { it.biometricType == targetType }
                    }

                    if (!isAlreadyRegistered && !BiometricManagerCompat.isHardwareDetected(
                            BiometricAuthRequest.default().withApi(BiometricApi.AUTO).withType(targetType)
                        )
                    ) {
                        val biometricMethod = BiometricMethod.createCustomModule(
                            customManager::class.java.name.hashCode(),
                            targetType
                        )

                        customModuleHashMap[biometricMethod] = customManager
                        newSoftwareModules[biometricMethod] =
                            SoftwareBiometricModule(biometricMethod, customManager, null)
                        d(
                            "BiometricAuthentication",
                            "Registered custom module: ${customManager.javaClass.simpleName}"
                        )
                    }
                } catch (e: Throwable) {
                    e("BiometricAuthentication", "Error loading custom module", e)
                }
            }
            moduleHashMap.putAll(newSoftwareModules)
        } catch (e: Throwable) {
            e("BiometricAuthentication", "ServiceLoader failure", e)
        } finally {
            customLoading = false
        }
    }

    @JvmOverloads
    fun init(
        globalInitListener: LegacyBiometricInitListener? = null,
        mlist: Collection<BiometricType>? = null
    ) {
        if (!initInProgress.compareAndSet(false, true)) return

        val ts = System.currentTimeMillis()
        e("BiometricAuthentication.init() - started")

        val allMethodsCopy = synchronized(allMethods) { allMethods.toList() }
        val modulesMap = Collections.synchronizedMap(HashMap<BiometricMethod, BiometricModule>())

        try {
            val list = if (mlist.isNullOrEmpty()) {
                allMethodsCopy
            } else {
                allMethodsCopy.filter { method -> mlist.any { it == method.biometricType } }
            }

            if (list.isEmpty()) {
                finalizeInit(ts, modulesMap, globalInitListener)
                return
            }

            val counter = AtomicInteger(list.size)
            val initListener = object : LegacyBiometricInitListener {
                override fun initFinished(method: BiometricMethod, module: BiometricModule?) {
                    if (module?.isManagerAccessible == true && module.isHardwarePresent) {
                        modulesMap[method] = module
                    }
                    globalInitListener?.initFinished(method, module)
                    if (counter.decrementAndGet() == 0) {
                        finalizeInit(ts, modulesMap, globalInitListener)
                    }
                }

                override fun onBiometricReady() {}
            }

            for (method in list) {
                initModule(method, initListener)
            }
        } catch (e: Throwable) {
            e(e, "BiometricAuthentication")
            initInProgress.set(false)
        }
    }

    private fun finalizeInit(
        ts: Long,
        modules: Map<BiometricMethod, BiometricModule>,
        listener: LegacyBiometricInitListener?
    ) {
        synchronized(moduleHashMap) {
            moduleHashMap.clear()
            moduleHashMap.putAll(modules)
        }
        initInProgress.set(false)
        e("BiometricAuthentication.init() - done; ts=${System.currentTimeMillis() - ts} ms")
        listener?.onBiometricReady()
    }

    private fun initModule(method: BiometricMethod, initListener: LegacyBiometricInitListener) {
        ExecutorHelper.startOnBackground {
            try {
                when (method) {
                    BiometricMethod.DUMMY_BIOMETRIC -> DummyBiometricModule(initListener)
                    BiometricMethod.FACELOCK -> FacelockOldModule(initListener)
                    BiometricMethod.FACEUNLOCK_LAVA -> FaceunlockLavaModule(initListener)
                    BiometricMethod.FINGERPRINT_API23 -> API23FingerprintModule(initListener)
                    BiometricMethod.FINGERPRINT_SUPPORT -> SupportFingerprintModule(initListener)
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
                    else -> initListener.initFinished(method, null)
                }
            } catch (e: Throwable) {
                e(e, "BiometricAuthentication")
                initListener.initFinished(method, null)
            }
        }
    }

    fun updateBiometricEnrollChanged() {
        availableBiometrics.forEach { type ->
            (getAvailableBiometricModule(type) as? AbstractBiometricModule)?.updateBiometricEnrollChanged()
        }
    }

    fun isEnrollChanged(): Boolean =
        availableBiometrics.any { getAvailableBiometricModule(it)?.isBiometricEnrollChanged == true }

    val availableBiometrics: List<BiometricType>
        get() = synchronized(moduleHashMap) {
            moduleHashMap.keys.map { it.biometricType }.distinct()
        }

    val availableBiometricMethods: List<BiometricMethod>
        get() = synchronized(moduleHashMap) { moduleHashMap.keys.toList() }

    val isLockOut: Boolean
        get() {
            val biometrics = availableBiometrics
            if (biometrics.isEmpty()) return false
            return biometrics.all { getAvailableBiometricModule(it)?.isLockOut == true }
        }

    val isHardwareDetected: Boolean
        get() = availableBiometrics.any { getAvailableBiometricModule(it)?.isHardwarePresent == true }

    val hasEnrolled: Boolean
        get() = availableBiometrics.any { getAvailableBiometricModule(it)?.hasEnrolled == true }

    fun authenticate(
        biometricCryptographyPurpose: BiometricCryptographyPurpose?,
        targetView: SurfaceView?,
        requestedMethods: List<BiometricType?>,
        listener: LegacyBiometricAuthenticationListener,
        bundle: Bundle?
    ) {
        if (authInProgress.get() || requestedMethods.isEmpty()) return

        if (initInProgress.get()) {
            val viewRef = WeakReference(targetView)
            val methodsRef = requestedMethods.filterNotNull()
            ExecutorHelper.startOnBackground {
                val start = System.currentTimeMillis()
                while (initInProgress.get() && System.currentTimeMillis() - start < 5000) {
                    Thread.sleep(50)
                }
                ExecutorHelper.post {
                    authenticate(
                        biometricCryptographyPurpose,
                        viewRef.get(),
                        methodsRef,
                        listener,
                        bundle
                    )
                }
            }
            return
        }


        val activeModules = HashMap<Int, BiometricType>()
        Core.cleanModules()

        requestedMethods.filterNotNull().forEach { type ->
            getAvailableBiometricModule(type)?.takeIf {
                (bundle?.getBoolean(
                    BundleBuilder.ENROLL,
                    false
                ) == true && it is SoftwareBiometricModule) || it.hasEnrolled
            }?.let { module ->
                Core.registerModule(module)
                if (module is FacelockOldModule) module.setCallerView(targetView)
                if (module is SoterFaceUnlockModule) module.bundle = bundle
                if (module is SoterFingerprintUnlockModule) module.bundle = bundle
                if (module is SoftwareBiometricModule) module.bundle = bundle
                activeModules[module.tag()] = type
            }
        }
        d("BiometricAuthentication.authenticate $activeModules")
        if (activeModules.isEmpty()) {
            listener.onFailure(
                AuthenticationResult(
                    requestedMethods.firstOrNull(),
                    reason = AuthenticationFailureReason.NO_BIOMETRICS_REGISTERED
                )
            )
            return
        }

        authInProgress.set(true)
        val listenerRef = SoftReference(listener)

        Core.authenticate(biometricCryptographyPurpose, object : AuthenticationListener {
            override fun onHelp(msg: CharSequence?) {
                listenerRef.get()?.onHelp(msg)
            }

            override fun onSuccess(tag: Int, crypto: BiometricCryptoObject?) {
                authInProgress.set(false)
                listenerRef.get()?.onSuccess(AuthenticationResult(activeModules[tag], crypto))
            }

            override fun onFailure(
                tag: Int,
                reason: AuthenticationFailureReason?,
                desc: CharSequence?
            ) {
                authInProgress.set(false)
                listenerRef.get()?.onFailure(
                    AuthenticationResult(
                        activeModules[tag],
                        reason = reason,
                        description = desc
                    )
                )
            }

            override fun onCanceled(
                tag: Int,
                reason: AuthenticationFailureReason?,
                desc: CharSequence?
            ) {
                authInProgress.set(false)
                listenerRef.get()?.onCanceled(
                    AuthenticationResult(
                        activeModules[tag],
                        reason = reason,
                        description = desc
                    )
                )
            }
        })
    }

    fun cancelAuthentication() {
        if (authInProgress.compareAndSet(true, false)) {
            d("BiometricAuthentication.cancelAuthentication")
            ExecutorHelper.startOnBackground {
                availableBiometricMethods.forEach { method ->
                    val module = moduleHashMap[method]
                    if (module is FacelockOldModule) module.stopAuth()
                    if (module is FaceunlockLavaModule) module.stopAuth()
                }
                Core.cancelAuthentication()
            }
        }
    }

    fun getSettingsIntent(type: BiometricType): Intent? {
        val module = getAvailableBiometricModule(type) ?: return null
        return try {
            when (module) {
                is FacelockOldModule if type == BiometricType.BIOMETRIC_FACE ->
                    return Intent(DevicePolicyManager.ACTION_SET_NEW_PASSWORD)

                is MiuiFaceUnlockModule if type == BiometricType.BIOMETRIC_FACE ->
                    return Intent().setClassName(
                        "com.android.settings",
                        "com.android.settings.Settings"
                    )
                        .putExtra(
                            ":android:show_fragment",
                            "com.android.settings.security.MiuiSecurityAndPrivacySettings"
                        )

                is HuaweiFaceUnlockModule if type == BiometricType.BIOMETRIC_FACE ->
                    return Intent().setClassName(
                        "com.android.settings",
                        "com.android.settings.facechecker.unlock.FaceUnLockSettingsActivity"
                    )

                else -> null
            }
        } catch (e: Throwable) {
            e(e, "BiometricAuthentication.openSettings")
            null
        }
    }

    fun getAvailableBiometricModule(biometricType: BiometricType?): BiometricModule? {
        if (biometricType == null) return null
        return synchronized(moduleHashMap) {
            moduleHashMap.entries
                .filter { it.key.biometricType == biometricType }
                .minByOrNull { it.key.id }
                ?.value
        }
    }
}