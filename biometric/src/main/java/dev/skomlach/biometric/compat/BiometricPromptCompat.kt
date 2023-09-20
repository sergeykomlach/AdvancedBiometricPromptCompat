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

package dev.skomlach.biometric.compat

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Looper
import androidx.annotation.CallSuper
import androidx.annotation.ColorInt
import androidx.annotation.MainThread
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import dev.skomlach.biometric.compat.BiometricManagerCompat.hasEnrolled
import dev.skomlach.biometric.compat.BiometricManagerCompat.isBiometricSensorPermanentlyLocked
import dev.skomlach.biometric.compat.BiometricManagerCompat.isHardwareDetected
import dev.skomlach.biometric.compat.BiometricManagerCompat.isLockOut
import dev.skomlach.biometric.compat.crypto.CryptographyManager
import dev.skomlach.biometric.compat.engine.BiometricAuthentication
import dev.skomlach.biometric.compat.engine.BiometricInitListener
import dev.skomlach.biometric.compat.engine.BiometricMethod
import dev.skomlach.biometric.compat.engine.core.interfaces.BiometricModule
import dev.skomlach.biometric.compat.impl.BiometricPromptApi28Impl
import dev.skomlach.biometric.compat.impl.BiometricPromptGenericImpl
import dev.skomlach.biometric.compat.impl.BiometricPromptSilentImpl
import dev.skomlach.biometric.compat.impl.IBiometricPromptImpl
import dev.skomlach.biometric.compat.utils.*
import dev.skomlach.biometric.compat.utils.activityView.ActivityViewWatcher
import dev.skomlach.biometric.compat.utils.appstate.AppBackgroundDetector
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl
import dev.skomlach.biometric.compat.utils.notification.BiometricNotificationManager
import dev.skomlach.biometric.compat.utils.themes.DarkLightThemes
import dev.skomlach.common.contextprovider.AndroidContext
import dev.skomlach.common.device.DeviceInfo
import dev.skomlach.common.device.DeviceInfoManager
import dev.skomlach.common.logging.LogCat
import dev.skomlach.common.misc.ExecutorHelper
import dev.skomlach.common.misc.isActivityFinished
import dev.skomlach.common.multiwindow.MultiWindowSupport
import dev.skomlach.common.permissions.PermissionUtils
import dev.skomlach.common.permissionui.PermissionsFragment
import dev.skomlach.common.permissionui.notification.NotificationPermissionsFragment
import dev.skomlach.common.permissionui.notification.NotificationPermissionsHelper
import dev.skomlach.common.statusbar.StatusBarTools
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.nio.charset.Charset
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

class BiometricPromptCompat private constructor(private val builder: Builder) {
    companion object {
        var API_ENABLED = true
            private set

        val SHOW_DETAILS_IN_UI = false

        init {
            if (API_ENABLED) {
                AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        HiddenApiBypass.addHiddenApiExemptions("")
                    }
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
            }
            ExecutorHelper.post {
                try {
                    init()
                } catch (e: Throwable) {
                }
            }

        }

        @JvmStatic
        fun apiEnabled(enabled: Boolean) {
            API_ENABLED = enabled
        }

        private val availableAuthRequests = HashSet<BiometricAuthRequest>()
            get() {
                if (field.isEmpty()) {
                    //Add default first
                    var biometricAuthRequest = BiometricAuthRequest()
                    if (BiometricManagerCompat.isHardwareDetected(biometricAuthRequest)) {
                        field.add(biometricAuthRequest)
                    }
                    for (api in BiometricApi.values()) {
                        if (api == BiometricApi.AUTO)
                            continue
                        for (type in BiometricType.values()) {
                            if (type == BiometricType.BIOMETRIC_ANY)
                                continue
                            biometricAuthRequest = BiometricAuthRequest(api, type)
                            if (BiometricManagerCompat.isHardwareDetected(biometricAuthRequest)) {
                                field.add(BiometricAuthRequest(BiometricApi.AUTO, type))
                                field.add(biometricAuthRequest)
                            }
                        }
                    }
                }
                return field
            }

        @JvmStatic
        fun getAvailableAuthRequests(): List<BiometricAuthRequest> {
            return availableAuthRequests.toList().sortedBy {
                it.toString()
            }
        }

        @JvmStatic
        fun logging(
            enabled: Boolean,
            externalLogger1: BiometricLoggerImpl.ExternalLogger? = null,
            externalLogger2: LogCat.ExternalLogger? = null
        ) {
            if (!API_ENABLED)
                return
//            AbstractBiometricModule.DEBUG_MANAGERS = enabled
            LogCat.DEBUG = enabled
            BiometricLoggerImpl.DEBUG = enabled
            LogCat.externalLogger = externalLogger2
            BiometricLoggerImpl.externalLogger = externalLogger1
        }

        private val pendingTasks: MutableList<Runnable?> =
            Collections.synchronizedList(ArrayList<Runnable?>())
        private var isBiometricInit = AtomicBoolean(false)
        var isInitialized = false
            get() = isBiometricInit.get()
            private set
        private var initInProgress = AtomicBoolean(false)
        var deviceInfo: DeviceInfo? = null
            private set
        private var authFlowInProgress = AtomicBoolean(false)

        @MainThread
        @JvmStatic
        fun init(execute: Runnable? = null) {
            if (!API_ENABLED)
                return
            if (Looper.getMainLooper().thread !== Thread.currentThread())
                throw IllegalThreadStateException("Main Thread required")

            if (isInitialized) {
                BiometricLoggerImpl.d("BiometricPromptCompat.init() - ready")
                execute?.let { ExecutorHelper.post(it) }
            } else {
                if (initInProgress.get()) {
                    BiometricLoggerImpl.d("BiometricPromptCompat.init() - pending")
                    pendingTasks.add(execute)
                } else {
                    BiometricLoggerImpl.d("BiometricPromptCompat.init()")
                    isBiometricInit.set(false)
                    initInProgress.set(true)
                    pendingTasks.add(execute)
                    NotificationPermissionsFragment.preloadTranslations()
                    ExecutorHelper.startOnBackground {
                        DeviceInfoManager.getDeviceInfo(object :
                            DeviceInfoManager.OnDeviceInfoListener {
                            override fun onReady(info: DeviceInfo?) {
                                deviceInfo = info
                                ExecutorHelper.post{startBiometricInit()}
                            }
                        })
                    }
                    DeviceUnlockedReceiver.registerDeviceUnlockListener()
                }
            }
        }

        @MainThread
        @JvmStatic
        private fun startBiometricInit() {
            BiometricAuthentication.init(object : BiometricInitListener {
                override fun initFinished(
                    method: BiometricMethod,
                    module: BiometricModule?
                ) {
                }

                override fun onBiometricReady() {
                    BiometricLoggerImpl.d("BiometricPromptCompat.init() - finished")
                    isBiometricInit.set(true)
                    initInProgress.set(false)

                    for (task in pendingTasks) {
                        task?.let { ExecutorHelper.post(it) }
                    }
                    pendingTasks.clear()
                }
            })
        }
    }

    init {
        NotificationPermissionsFragment.preloadTranslations()
    }

    private val appContext = AndroidContext.appContext
    private val impl: IBiometricPromptImpl by lazy {
        val isBiometricPrompt =
            builder.getBiometricAuthRequest().api == BiometricApi.BIOMETRIC_API ||
                    if (builder.getBiometricAuthRequest().api == BiometricApi.AUTO && HardwareAccessImpl.getInstance(
                            builder.getBiometricAuthRequest()
                        ).isNewBiometricApi
                    ) {
                        var found = false
                        for (v in builder.getPrimaryAvailableTypes()) {
                            val request = BiometricAuthRequest(BiometricApi.BIOMETRIC_API, v)
                            if (BiometricManagerCompat.isBiometricReadyForUsage(request)) {
                                found = true
                                break
                            }
                        }
                        found
                    } else {
                        false
                    }
        BiometricLoggerImpl.d(
            "BiometricPromptCompat.IBiometricPromptImpl - " +
                    "$isBiometricPrompt"
        )
        val iBiometricPromptImpl = if (builder.isSilentAuthEnabled())
            BiometricPromptSilentImpl(builder)
        else if (isBiometricPrompt) {
            BiometricPromptApi28Impl(builder)
        } else {
            BiometricPromptGenericImpl(builder)
        }
        iBiometricPromptImpl
    }
    private val appBackgroundDetector: AppBackgroundDetector by lazy {
        AppBackgroundDetector(impl) {
            cancelAuthentication()
        }
    }
    private var startTs = 0L
    fun authenticate(callbackOuter: AuthenticationCallback) {
        startTs = System.currentTimeMillis()
        if (authFlowInProgress.get()) {
            callbackOuter.onCanceled()
            return
        }
        authFlowInProgress.set(true)
        if (isActivityFinished(builder.getContext())) {
            BiometricLoggerImpl.e("Unable to start BiometricPromptCompat.authenticate() cause of Activity destroyed")
            callbackOuter.onCanceled()
            authFlowInProgress.set(false)
            return
        }
        if (!API_ENABLED) {
            callbackOuter.onFailed(
                AuthenticationFailureReason.NO_HARDWARE,
                null
            )
            authFlowInProgress.set(false)
            return
        }
        BiometricLoggerImpl.d("BiometricPromptCompat.authenticate()")
        if (WideGamutBug.unsupportedColorMode(builder.getActivity())) {
            BiometricLoggerImpl.e("BiometricPromptCompat.startAuth - WideGamutBug")
            callbackOuter.onFailed(
                AuthenticationFailureReason.HARDWARE_UNAVAILABLE,
                null
            )
            authFlowInProgress.set(false)
            return
        }
        val startTime = System.currentTimeMillis()
        var timeout = false
        ExecutorHelper.startOnBackground {
            while (!builder.isTruncateChecked() || !isInitialized) {
                timeout = System.currentTimeMillis() - startTime >= TimeUnit.SECONDS.toMillis(5)
                if (timeout) {
                    break
                }
                try {
                    Thread.sleep(20)
                } catch (ignore: InterruptedException) {
                }
            }
            ExecutorHelper.post {
                if (timeout) {
                    callbackOuter.onFailed(AuthenticationFailureReason.NOT_INITIALIZED_ERROR, null)
                    authFlowInProgress.set(false)
                } else
                    startAuth(callbackOuter)
            }
        }
    }

    private fun checkHardwareAsync(callback: (result: AuthenticationFailureReason) -> Unit) {
        ExecutorHelper.startOnBackground(Runnable {
            if (!isHardwareDetected(impl.builder.getBiometricAuthRequest())) {
                BiometricLoggerImpl.e("BiometricPromptCompat.startAuth - isHardwareDetected")
                callback.invoke(AuthenticationFailureReason.NO_HARDWARE)
                return@Runnable
            } else if (!hasEnrolled(impl.builder.getBiometricAuthRequest())) {
                BiometricLoggerImpl.e("BiometricPromptCompat.startAuth - hasEnrolled")
                callback.invoke(AuthenticationFailureReason.NO_BIOMETRICS_REGISTERED)
                return@Runnable
            } else if (isLockOut(impl.builder.getBiometricAuthRequest(), false)) {
                BiometricLoggerImpl.e("BiometricPromptCompat.startAuth - isLockOut")
                callback.invoke(AuthenticationFailureReason.LOCKED_OUT)
                return@Runnable
            } else if (isBiometricSensorPermanentlyLocked(
                    impl.builder.getBiometricAuthRequest(),
                    false
                )
            ) {
                BiometricLoggerImpl.e("BiometricPromptCompat.startAuth - isBiometricSensorPermanentlyLocked")
                callback.invoke(AuthenticationFailureReason.HARDWARE_UNAVAILABLE)
                return@Runnable
            } else callback.invoke(AuthenticationFailureReason.UNKNOWN)
        })
    }

    private fun startAuth(callbackOuter: AuthenticationCallback) {
        if (isActivityFinished(builder.getContext())) {
            BiometricLoggerImpl.e("Unable to start BiometricPromptCompat.authenticate() cause of Activity destroyed")
            callbackOuter.onCanceled()
            authFlowInProgress.set(false)
            return
        }
        BiometricLoggerImpl.d("BiometricPromptCompat. start PermissionsFragment.askForPermissions")
        val result = AtomicReference<AuthenticationFailureReason?>(null)
        checkHardwareAsync {
            result.set(it)
        }
        val checkPermissions = {
            PermissionsFragment.askForPermissions(
                impl.builder.getActivity(),
                usedPermissions
            ) {
                if (usedPermissions.isNotEmpty() && !PermissionUtils.hasSelfPermissions(
                        usedPermissions
                    )
                ) {
                    callbackOuter.onFailed(
                        AuthenticationFailureReason.MISSING_PERMISSIONS_ERROR,
                        null
                    )
                    authFlowInProgress.set(false)
                } else {
                    BiometricLoggerImpl.d("BiometricPromptCompat.startAuth")
                    val activityViewWatcher = try {
                        if (!builder.isSilentAuthEnabled()) ActivityViewWatcher(
                            impl.builder,
                            object : ActivityViewWatcher.ForceToCloseCallback {
                                override fun onCloseBiometric() {
                                    cancelAuthentication()
                                }
                            }) else null
                    } catch (e: Throwable) {
                        BiometricLoggerImpl.e(e)
                        null
                    }

                    val callback = object : AuthenticationCallback() {

                        private var isOpened = AtomicBoolean(false)
                        override fun onSucceeded(result: Set<AuthenticationResult>) {
                            if (isOpened.get()) {
                                super.onSucceeded(result)
                                val confirmed = result.toMutableSet()
                                try {
                                    BiometricLoggerImpl.d("BiometricPromptCompat.AuthenticationCallback.onSucceeded1 = $confirmed")
                                    //Fix for devices that call onAuthenticationSucceeded() without enrolled biometric
                                    if (builder.shouldAutoVerifyCryptoAfterSuccess()) {
                                        if (CryptographyManager.encryptData(
                                                appContext.packageName.toByteArray(
                                                    Charset.forName("UTF-8")
                                                ), confirmed
                                            ) == null
                                        ) {
                                            onCanceled()
                                            return
                                        } else {
                                            val filtered = confirmed.map {
                                                AuthenticationResult(it.confirmed)
                                            }
                                            confirmed.apply {
                                                clear()
                                                confirmed.addAll(filtered)
                                            }
                                        }
                                    }

                                    BiometricLoggerImpl.d("BiometricPromptCompat.AuthenticationCallback.onSucceeded2 = $confirmed")
                                    if (builder.getBiometricAuthRequest().api != BiometricApi.AUTO) {
                                        HardwareAccessImpl.getInstance(builder.getBiometricAuthRequest())
                                            .updateBiometricEnrollChanged()
                                    } else {
                                        HardwareAccessImpl.getInstance(
                                            BiometricAuthRequest(
                                                BiometricApi.BIOMETRIC_API,
                                                builder.getBiometricAuthRequest().type
                                            )
                                        )
                                            .updateBiometricEnrollChanged()
                                        HardwareAccessImpl.getInstance(
                                            BiometricAuthRequest(
                                                BiometricApi.LEGACY_API,
                                                builder.getBiometricAuthRequest().type
                                            )
                                        )
                                            .updateBiometricEnrollChanged()
                                    }
                                    callbackOuter.onSucceeded(confirmed.toSet())
                                } finally {
                                    onUIClosed()
                                }
                            }
                        }

                        override fun onCanceled() {
                            if (isOpened.get()) {
                                BiometricLoggerImpl.d("BiometricPromptCompat.AuthenticationCallback.onCanceled")
                                try {
                                    callbackOuter.onCanceled()
                                } finally {
                                    onUIClosed()
                                }
                            }
                        }

                        override fun onFailed(
                            reason: AuthenticationFailureReason?,
                            dialogDescription: CharSequence?
                        ) {
                            if (isOpened.get()) {
                                BiometricLoggerImpl.d("BiometricPromptCompat.AuthenticationCallback.onFailed=$reason")
                                try {
                                    callbackOuter.onFailed(reason, dialogDescription)
                                } finally {
                                    onUIClosed()
                                }
                            }
                        }

                        override fun onUIOpened() {
                            if (!isOpened.get()) {
                                isOpened.set(true)
                                BiometricLoggerImpl.d("BiometricPromptCompat.AuthenticationCallback.onUIOpened")
                                val s =
                                    "BiometricOpeningTime: ${System.currentTimeMillis() - startTs} ms"
                                BiometricLoggerImpl.d("BiometricPromptCompat $s")
                                if (!builder.isSilentAuthEnabled()) {
                                    if (DevicesWithKnownBugs.hasUnderDisplayFingerprint && builder.isNotificationEnabled()) {
                                        BiometricNotificationManager.showNotification(builder)
                                    }
                                    StatusBarTools.setNavBarAndStatusBarColors(
                                        builder.getActivity().window,
                                        DialogMainColor.getColor(
                                            builder.getContext(),
                                            DarkLightThemes.isNightModeCompatWithInscreen(builder.getContext())
                                        ),
                                        DialogMainColor.getColor(
                                            builder.getContext(),
                                            !DarkLightThemes.isNightModeCompatWithInscreen(builder.getContext())
                                        ),
                                        builder.getStatusBarColor()
                                    )

                                    activityViewWatcher?.setupListeners()
                                }
                                callbackOuter.onUIOpened()
                            }
                        }

                        override fun onUIClosed() {
                            BiometricLoggerImpl.d("BiometricPromptCompat.AuthenticationCallback.onUIClosed")
                            if (isOpened.get()) {
                                isOpened.set(false)
                                if (!builder.isSilentAuthEnabled()) {
                                    activityViewWatcher?.resetListeners()
                                    val closeAll = Runnable {
                                        if (DevicesWithKnownBugs.hasUnderDisplayFingerprint && builder.isNotificationEnabled()) {
                                            BiometricNotificationManager.dismissAll()
                                        }
                                        StatusBarTools.setNavBarAndStatusBarColors(
                                            builder.getActivity().window,
                                            builder.getNavBarColor(),
                                            builder.getDividerColor(),
                                            builder.getStatusBarColor()
                                        )
                                    }
                                    ExecutorHelper.post(closeAll)
                                    val delay =
                                        appContext.resources.getInteger(android.R.integer.config_longAnimTime)
                                            .toLong()
                                    ExecutorHelper.postDelayed(closeAll, delay)
                                }
                                appBackgroundDetector.detachListeners()
                                authFlowInProgress.set(false)
                                callbackOuter.onUIClosed()
                            }
                        }
                    }
                    ExecutorHelper.startOnBackground {
                        while (result.get() == null) {
                            try {
                                Thread.sleep(20)
                            } catch (e: Throwable) {
                            }
                        }
                        if (result.get() != AuthenticationFailureReason.UNKNOWN) {
                            ExecutorHelper.post {
                                callbackOuter.onFailed(
                                    result.get(),
                                    null
                                )
                                authFlowInProgress.set(false)
                            }
                            return@startOnBackground
                        } else
                            ExecutorHelper.post {
                                authenticateInternal(callback)
                            }
                    }

                }
            }
        }
        if (!builder.isSilentAuthEnabled()) {
            if (DevicesWithKnownBugs.hasUnderDisplayFingerprint && builder.isNotificationEnabled()) {
                BiometricNotificationManager.initNotificationsPreferences()
                NotificationPermissionsHelper.checkNotificationPermissions(
                    builder.getContext(),
                    BiometricNotificationManager.CHANNEL_ID,
                    {
                        checkPermissions.invoke()
                    },
                    {
                        //continue anyway
                        checkPermissions.invoke()
                    })
                return
            }
        }
        checkPermissions.invoke()
    }

    private val usedPermissions: List<String>
        get() {

            val permission: MutableSet<String> = HashSet()

            if (Build.VERSION.SDK_INT >= 28) {
                permission.add("android.permission.USE_BIOMETRIC")
            }

            val biometricMethodList: MutableList<BiometricMethod> = ArrayList()
            for (m in BiometricAuthentication.availableBiometricMethods) {
                if (builder.getAllAvailableTypes().contains(m.biometricType)) {
                    biometricMethodList.add(m)
                }
            }
            for (method in biometricMethodList) {
                when (method) {
                    BiometricMethod.DUMMY_BIOMETRIC -> permission.add("android.permission.CAMERA")
                    BiometricMethod.IRIS_ANDROIDAPI -> permission.add("android.permission.USE_IRIS")
                    BiometricMethod.IRIS_SAMSUNG -> permission.add("com.samsung.android.camera.iris.permission.USE_IRIS")
                    BiometricMethod.FACELOCK -> permission.add("android.permission.WAKE_LOCK")
                    BiometricMethod.FACE_HUAWEI, BiometricMethod.FACE_HUAWEI3D, BiometricMethod.FACE_SOTERAPI -> permission.add(
                        "android.permission.USE_FACERECOGNITION"
                    )
                    BiometricMethod.FACE_HIHONOR, BiometricMethod.FACE_HIHONOR3D -> permission.add("android.permission.CAMERA")
                    BiometricMethod.FACE_ANDROIDAPI -> permission.add("android.permission.USE_FACE_AUTHENTICATION")
                    BiometricMethod.FACE_SAMSUNG -> permission.add("com.samsung.android.bio.face.permission.USE_FACE")
                    BiometricMethod.FACE_OPPO -> permission.add("oppo.permission.USE_FACE")
                    BiometricMethod.FINGERPRINT_API23, BiometricMethod.FINGERPRINT_SUPPORT -> permission.add(
                        "android.permission.USE_FINGERPRINT"
                    )
                    BiometricMethod.FINGERPRINT_FLYME -> permission.add("com.fingerprints.service.ACCESS_FINGERPRINT_MANAGER")
                    BiometricMethod.FINGERPRINT_SAMSUNG -> permission.add("com.samsung.android.providers.context.permission.WRITE_USE_APP_FEATURE_SURVEY")
                    else -> {
                        //no-op
                    }
                }
            }

            return ArrayList(permission)
        }

    private fun authenticateInternal(callback: AuthenticationCallback) {
        BiometricLoggerImpl.d("BiometricPromptCompat.authenticateInternal()")
        if (isActivityFinished(builder.getContext())) {
            BiometricLoggerImpl.e("Unable to start BiometricPromptCompat.authenticate() cause of Activity destroyed")
            callback.onCanceled()
            authFlowInProgress.set(false)
            return
        }
        try {
            BiometricLoggerImpl.d("BiometricPromptCompat.authenticateInternal() - impl.authenticate")
            appBackgroundDetector.attachListeners()
            callback.updateTimestamp()
            val s = "BiometricOpeningTime: ${System.currentTimeMillis() - startTs} ms"
            BiometricLoggerImpl.e("BiometricPromptCompat $s")
            impl.authenticate(callback)
        } catch (ignore: IllegalStateException) {
            appBackgroundDetector.detachListeners()
            callback.onFailed(AuthenticationFailureReason.INTERNAL_ERROR, null)
            authFlowInProgress.set(false)
        }
    }

    fun cancelAuthentication() {
        if (!API_ENABLED || !authFlowInProgress.get()) {
            return
        }
        ExecutorHelper.startOnBackground {
            while (!isInitialized) {
                try {
                    Thread.sleep(20)
                } catch (ignore: InterruptedException) {
                }
            }
            ExecutorHelper.post {
                impl.cancelAuthentication()
            }
        }
    }

    @ColorInt
    fun getDialogMainColor(): Int {
        if (!API_ENABLED)
            return ContextCompat.getColor(builder.getContext(), R.color.material_grey_50)
        return DialogMainColor.getColor(
            builder.getContext(),
            DarkLightThemes.isNightModeCompatWithInscreen(builder.getContext())
        )
    }

    abstract class AuthenticationCallback {
        //Solution to prevent Frida hooking
        //See https://fi5t.xyz/posts/biometric-underauthentication/
        private val skipTimeout =
            AndroidContext.appContext.resources.getInteger(android.R.integer.config_shortAnimTime)
        private val authCallTimeStamp = AtomicLong(System.currentTimeMillis())
        internal fun updateTimestamp(){
            authCallTimeStamp.set(System.currentTimeMillis())
        }
        @MainThread
        @CallSuper
        @Throws(BiometricAuthException::class)
        open fun onSucceeded(confirmed: Set<AuthenticationResult>) {
            val tmp = System.currentTimeMillis()
            if (tmp - authCallTimeStamp.get() <= skipTimeout) throw BiometricAuthException("Biometric flow hooking detected")
        }

        @MainThread
        open fun onCanceled() {
        }

        @MainThread
        open fun onFailed(
            reason: AuthenticationFailureReason?,
            dialogDescription: CharSequence? = null
        ) {
        }

        @MainThread
        open fun onUIOpened() {
        }

        @MainThread
        open fun onUIClosed() {
        }
    }

    class Builder(
        private val biometricAuthRequest: BiometricAuthRequest,
        dummy_reference: FragmentActivity? = null
    ) {
        companion object {
            init {
                //lazy init
                val initialized = isInitialized
                BiometricLoggerImpl.d("isInitialized - $initialized")
            }
        }

        private val allAvailableTypes: HashSet<BiometricType> by lazy {
            val types = HashSet<BiometricType>()
            types.addAll(primaryAvailableTypes)
            types.addAll(secondaryAvailableTypes)
            types
        }
        private val primaryAvailableTypes: HashSet<BiometricType> by lazy {
            val types = HashSet<BiometricType>()
            val api =
                if (HardwareAccessImpl.getInstance(biometricAuthRequest).isNewBiometricApi) BiometricApi.BIOMETRIC_API else BiometricApi.LEGACY_API
            if (biometricAuthRequest.type == BiometricType.BIOMETRIC_ANY) {
                for (type in BiometricType.values()) {
                    if (type == BiometricType.BIOMETRIC_ANY)
                        continue
                    val request = BiometricAuthRequest(
                        api,
                        type
                    )
                    if (BiometricManagerCompat.isBiometricReadyForUsage(request)) {
                        types.add(type)
                    }
                }
            } else {
                if (BiometricManagerCompat.isBiometricReadyForUsage(biometricAuthRequest))
                    types.add(biometricAuthRequest.type)
            }
            types
        }
        private val secondaryAvailableTypes: HashSet<BiometricType> by lazy {
            val types = HashSet<BiometricType>()
            if (HardwareAccessImpl.getInstance(biometricAuthRequest).isNewBiometricApi) {
                if (biometricAuthRequest.type == BiometricType.BIOMETRIC_ANY) {
                    for (type in BiometricType.values()) {
                        if (type == BiometricType.BIOMETRIC_ANY)
                            continue
                        val request = BiometricAuthRequest(
                            BiometricApi.LEGACY_API,
                            type
                        )
                        if (BiometricManagerCompat.isBiometricReadyForUsage(request)) {
                            types.add(type)
                        }
                    }
                } else {
                    if (BiometricManagerCompat.isBiometricReadyForUsage(biometricAuthRequest))
                        types.add(biometricAuthRequest.type)
                }
                types.removeAll(primaryAvailableTypes)
            }
            types
        }

        private var silentAuth = false
        private var authWindowSec = 30

        private var dialogTitle: CharSequence? = null
            get() {
                if (field.isNullOrEmpty())
                    field = BiometricTitle.getRelevantTitle(
                        appContext,
                        getAllAvailableTypes()
                    )
                return field
            }

        private var dialogSubtitle: CharSequence? = null

        private var dialogDescription: CharSequence? = null

        private var negativeButtonText: CharSequence? = null

        private lateinit var multiWindowSupport: MultiWindowSupport

        private var notificationEnabled = true

        private var backgroundBiometricIconsEnabled = true

        private var biometricCryptographyPurpose: BiometricCryptographyPurpose? = null

        @ColorInt
        private var colorNavBar: Int = Color.TRANSPARENT

        @ColorInt
        private var dividerColor: Int = Color.TRANSPARENT

        @ColorInt
        private var colorStatusBar: Int = Color.TRANSPARENT

        private var isTruncateChecked: Boolean? = null

        private val appContext = AndroidContext.appContext

        private var autoVerifyCryptoAfterSuccess = false

        init {
            getActivity().let { context ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    this.colorNavBar = context.window.navigationBarColor
                    this.colorStatusBar = context.window.statusBarColor
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    dividerColor = context.window.navigationBarDividerColor
                }
            }
            if (API_ENABLED) {
                multiWindowSupport = MultiWindowSupport.get()
            }

            //Known issue: at least "OnePlus 9" call onSuccess when "Cancel" button clicked,
            //so checking the crypto is only the way to check real reason - it's Canceled or Success

            //Due to limitations, applicable only for Fingerprint
            if (deviceInfo?.model?.startsWith("OnePlus 9", ignoreCase = true) == true &&
                BiometricManagerCompat.isBiometricAvailable(BiometricAuthRequest(type = BiometricType.BIOMETRIC_FINGERPRINT))
            ) {
                autoVerifyCryptoAfterSuccess = true
                biometricCryptographyPurpose =
                    BiometricCryptographyPurpose(BiometricCryptographyPurpose.ENCRYPT)
            }
        }

        constructor(dummy_reference: FragmentActivity) : this(
            BiometricAuthRequest(
                BiometricApi.AUTO,
                BiometricType.BIOMETRIC_ANY
            ), dummy_reference
        )

        fun isSilentAuthEnabled(): Boolean {
            return silentAuth
        }

        fun getAuthWindow(): Int {
            return authWindowSec
        }

        fun enableSilentAuth(authWindowSec: Int = 30) {
            if (authWindowSec <= 0) throw IllegalArgumentException("AuthWindow cann't be less then 0")
            BiometricLoggerImpl.e(
                "WARNING!!!\n" +
                        "Keep in mind - some devices use the own built-in animations " +
                        "(camera animation for Face/Iris) or other type of UI " +
                        "(Fingerprint dialog and/or under-screen recognition animation)" +
                        " and this leads to the uselessness of this function. " +
                        "Use BiometricManagerCompat.isSilentAuthAvailable() to check"
            )
            this.authWindowSec = authWindowSec
            this.silentAuth = true
        }

        fun shouldAutoVerifyCryptoAfterSuccess(): Boolean {
            return autoVerifyCryptoAfterSuccess
        }

        fun getTitle(): CharSequence? {
            return dialogTitle
        }

        fun getSubtitle(): CharSequence? {
            return dialogSubtitle
        }

        fun getDescription(): CharSequence? {
            return dialogDescription
        }

        fun getNavBarColor(): Int {
            return colorNavBar
        }

        fun getStatusBarColor(): Int {
            return colorStatusBar
        }

        fun getDividerColor(): Int {
            return dividerColor
        }


        fun isBackgroundBiometricIconsEnabled(): Boolean {
            return backgroundBiometricIconsEnabled
        }

        fun isNotificationEnabled(): Boolean {
            return notificationEnabled
        }

        fun isTruncateChecked(): Boolean {
            if (isTruncateChecked == null) {
                isTruncateChecked = false
                ExecutorHelper.post {
                    TruncatedTextFix.recalculateTexts(
                        this,
                        object : TruncatedTextFix.OnTruncateChecked {
                            override fun onDone() {
                                isTruncateChecked = true
                            }
                        })
                }
            }
            return isTruncateChecked ?: true
        }

        fun getPrimaryAvailableTypes(): Set<BiometricType> {
            return HashSet<BiometricType>(primaryAvailableTypes)
        }

        fun getSecondaryAvailableTypes(): Set<BiometricType> {
            return HashSet<BiometricType>(secondaryAvailableTypes)
        }

        fun getAllAvailableTypes(): Set<BiometricType> {
            return HashSet<BiometricType>(allAvailableTypes)
        }

        fun getActivity(): FragmentActivity {
            return (AndroidContext.activity as? FragmentActivity?)
                ?: throw java.lang.IllegalStateException("No activity on screen")
        }
        fun getContext(): Context {
            return AndroidContext.appContext
        }
        fun getCryptographyPurpose(): BiometricCryptographyPurpose? {
            return biometricCryptographyPurpose
        }

        fun getBiometricAuthRequest(): BiometricAuthRequest {
            return biometricAuthRequest
        }

        fun getMultiWindowSupport(): MultiWindowSupport {
            return multiWindowSupport
        }

        fun setCryptographyPurpose(
            biometricCryptographyPurpose: BiometricCryptographyPurpose
        ): Builder {
            autoVerifyCryptoAfterSuccess = false
            this.biometricCryptographyPurpose = biometricCryptographyPurpose
            return this
        }

        fun setEnabledBackgroundBiometricIcons(enabled: Boolean): Builder {
            this.backgroundBiometricIconsEnabled = enabled
            return this
        }

        fun setEnabledNotification(enabled: Boolean): Builder {
            this.notificationEnabled = enabled
            return this
        }

        fun setTitle(dialogTitle: CharSequence?): Builder {
            this.dialogTitle = dialogTitle
            return this
        }

        fun setTitle(@StringRes dialogTitleRes: Int): Builder {
            dialogTitle = appContext.getString(dialogTitleRes)
            return this
        }

        fun setSubtitle(dialogSubtitle: CharSequence?): Builder {
            this.dialogSubtitle = dialogSubtitle
            return this
        }

        fun setSubtitle(@StringRes dialogSubtitleRes: Int): Builder {
            dialogSubtitle = appContext.getString(dialogSubtitleRes)
            return this
        }

        fun setDescription(dialogDescription: CharSequence?): Builder {
            this.dialogDescription = dialogDescription
            return this
        }

        fun setDescription(@StringRes dialogDescriptionRes: Int): Builder {
            dialogDescription = appContext.getString(dialogDescriptionRes)
            return this
        }

        fun getNegativeButtonText(): CharSequence? {
            return negativeButtonText
        }

        @Deprecated("BiometricPromptCompat.setNegativeButtonText may not work properly on some devices!! Not actual deprecated")
        fun setNegativeButtonText(text: CharSequence?): Builder {
            negativeButtonText = text
            return this
        }
        @Deprecated("BiometricPromptCompat.setNegativeButtonText may not work properly on some devices!! Not actual deprecated")
        fun setNegativeButtonText(@StringRes res: Int): Builder {
            negativeButtonText = appContext.getString(res)
            return this
        }

        fun build(): BiometricPromptCompat {
            return BiometricPromptCompat(this)
        }
    }
}