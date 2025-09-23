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

import android.content.Context
import android.content.pm.PackageManager
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
import dev.skomlach.biometric.compat.crypto.CryptographyManager
import dev.skomlach.biometric.compat.engine.BiometricAuthentication
import dev.skomlach.biometric.compat.engine.BiometricInitListener
import dev.skomlach.biometric.compat.engine.BiometricMethod
import dev.skomlach.biometric.compat.engine.core.interfaces.BiometricModule
import dev.skomlach.biometric.compat.impl.BiometricPromptApi28Impl
import dev.skomlach.biometric.compat.impl.BiometricPromptGenericImpl
import dev.skomlach.biometric.compat.impl.BiometricPromptSilentImpl
import dev.skomlach.biometric.compat.impl.IBiometricPromptImpl
import dev.skomlach.biometric.compat.impl.credentials.CredentialsRequestFragment
import dev.skomlach.biometric.compat.impl.dialogs.UntrustedAccessibilityFragment
import dev.skomlach.biometric.compat.utils.BiometricErrorLockoutPermanentFix
import dev.skomlach.biometric.compat.utils.BiometricTitle
import dev.skomlach.biometric.compat.utils.DeviceUnlockedReceiver
import dev.skomlach.biometric.compat.utils.DevicesWithKnownBugs
import dev.skomlach.biometric.compat.utils.DialogMainColor
import dev.skomlach.biometric.compat.utils.HardwareAccessImpl
import dev.skomlach.biometric.compat.utils.TruncatedTextFix
import dev.skomlach.biometric.compat.utils.WideGamutBug
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
import dev.skomlach.common.misc.Utils
import dev.skomlach.common.misc.isActivityFinished
import dev.skomlach.common.multiwindow.MultiWindowSupport
import dev.skomlach.common.permissionui.notification.NotificationPermissionsFragment
import dev.skomlach.common.permissionui.notification.NotificationPermissionsHelper
import dev.skomlach.common.protection.A11yDetection
import dev.skomlach.common.protection.HookDetection
import dev.skomlach.common.statusbar.StatusBarTools
import dev.skomlach.common.translate.LocalizationHelper
import java.nio.charset.Charset
import java.util.Collections
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class BiometricPromptCompat private constructor(private val builder: Builder) {
    companion object {
        private var reference = AtomicBoolean(false)
        var API_ENABLED = true
            private set

        val SHOW_DETAILS_IN_UI = BuildConfig.DEBUG

        init {
            if (API_ENABLED) {

                if (!AppCompatDelegate.isCompatVectorFromResourcesEnabled())
                    AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)
                ExecutorHelper.post {
                    try {
                        init()
                    } catch (e: Throwable) {
                    }
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
                    for (api in BiometricApi.entries) {
                        if (api == BiometricApi.AUTO)
                            continue
                        for (type in BiometricType.entries) {
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
            enabled: Boolean
        ) {
            if (!API_ENABLED)
                return
//            AbstractBiometricModule.DEBUG_MANAGERS = enabled
            LogCat.DEBUG = enabled
            BiometricLoggerImpl.DEBUG = enabled
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
        var initStart = System.currentTimeMillis()

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
                    initStart = System.currentTimeMillis()
                    isBiometricInit.set(false)
                    initInProgress.set(true)
                    pendingTasks.add(execute)
                    BiometricLoggerImpl.d("BiometricPromptCompat.init() for ${AndroidContext.appContext.packageName}")
                    reference.set(false)
                    HookDetection.detect(object : HookDetection.HookDetectionListener {
                        override fun onDetected(flag: Boolean) {
                            reference.set(flag)
                        }
                    })
                    ExecutorHelper.startOnBackground {
                        if (BiometricErrorLockoutPermanentFix.isRebootDetected())
                            BiometricErrorLockoutPermanentFix.resetBiometricSensorPermanentlyLocked()
                    }
                    startBiometricInit()
                    ExecutorHelper.startOnBackground {
                        DeviceInfoManager.getDeviceInfo(object :
                            DeviceInfoManager.OnDeviceInfoListener {
                            override fun onReady(info: DeviceInfo?) {
                                deviceInfo = info
                            }
                        })
                    }
                    ExecutorHelper.startOnBackground {
                        DeviceUnlockedReceiver.registerDeviceUnlockListener()
                    }
                    AndroidContext.configurationLiveData.observeForever {
                        NotificationPermissionsFragment.preloadTranslations()
                        UntrustedAccessibilityFragment.preloadTranslations()
                    }
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
                    BiometricLoggerImpl.e("BiometricPromptCompat initialized in ${System.currentTimeMillis() - initStart} ms")
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


    private lateinit var oldDescription: CharSequence
    private lateinit var oldTitle: CharSequence
    private val oldIsBiometricReadyForUsage =
        BiometricManagerCompat.isBiometricSensorPermanentlyLocked(builder.getBiometricAuthRequest())
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
                            if (BiometricManagerCompat.isBiometricAvailable(request)) {
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
            if (!builder.forceDeviceCredential()) {
                BiometricLoggerImpl.e("BiometricPromptCompat.AppBackgroundDetector()")
                cancelAuthentication()
            }
        }
    }
    private var startTs = 0L
    private var startTsImpl = 0L
    fun authenticate(callbackOuter: AuthenticationCallback) {
        BiometricLoggerImpl.d("BiometricPromptCompat.authenticate() stage1")
        startTs = System.currentTimeMillis()
        if (authFlowInProgress.get()) {
            callbackOuter.onCanceled(builder.getAllAvailableTypes().map {
                AuthenticationResult(
                    it,
                    reason = AuthenticationFailureReason.BIOMETRIC_ALREADY_STARTED
                )
            }.toSet())
            return
        }
        authFlowInProgress.set(true)

        if (!API_ENABLED) {
            callbackOuter.onFailed(
                builder.getAllAvailableTypes().map {
                    AuthenticationResult(
                        it,
                        reason = AuthenticationFailureReason.NO_HARDWARE,
                        description = "API disabled"
                    )
                }.toSet()
            )
            authFlowInProgress.set(false)
            return
        }
        if (builder.getActivity() == null) {
            BiometricLoggerImpl.e(
                IllegalStateException(),
                "Unable to start BiometricPromptCompat.authenticate() cause of Activity destroyed"

            )
            callbackOuter.onCanceled(builder.getAllAvailableTypes().map {
                AuthenticationResult(
                    it,
                    reason = AuthenticationFailureReason.INTERNAL_ERROR,
                    description = "Unable to start BiometricPromptCompat.authenticate() cause of Activity destroyed"
                )
            }.toSet())
            authFlowInProgress.set(false)
            return
        }
        BiometricLoggerImpl.d("BiometricPromptCompat.authenticate() stage2")
        if (builder.getAllAvailableTypes().any {
                it == BiometricType.BIOMETRIC_FINGERPRINT
            } && WideGamutBug.unsupportedColorMode(builder.getActivity())) {
            BiometricLoggerImpl.e("BiometricPromptCompat.startAuth - WideGamutBug")
            callbackOuter.onFailed(
                builder.getAllAvailableTypes().map {
                    AuthenticationResult(
                        it,
                        reason = AuthenticationFailureReason.HARDWARE_UNAVAILABLE,
                        description = "WideGamut prevent from proper optical sensor work"
                    )
                }.toSet()
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
                    Thread.sleep(10)
                } catch (ignore: InterruptedException) {
                }
            }
            if (builder.getAllAvailableTypes().isEmpty()) {
                val checkHardware = checkHardware()
                val interruptAuth = when (checkHardware) {
                    //Temporary blocked, we can try to bypass
                    AuthenticationFailureReason.HARDWARE_UNAVAILABLE -> !builder.forceDeviceCredential()
                    //All good
                    AuthenticationFailureReason.UNKNOWN -> false
                    //Not able to continue
                    else -> true
                }
                if (interruptAuth) {
                    ExecutorHelper.post {
                        callbackOuter.onFailed(
                            setOf(
                                AuthenticationResult(
                                    BiometricType.BIOMETRIC_ANY,
                                    reason = checkHardware
                                )
                            )
                        )
                        authFlowInProgress.set(false)
                    }
                    return@startOnBackground
                }
            }
            ExecutorHelper.post {
                if (timeout) {
                    callbackOuter.onFailed(builder.getAllAvailableTypes().map {
                        AuthenticationResult(
                            it,
                            reason = AuthenticationFailureReason.NOT_INITIALIZED_ERROR,
                            description = "Error initialization takes too long"
                        )
                    }.toSet())
                    authFlowInProgress.set(false)
                } else if (BiometricManagerCompat.isLockOut(impl.builder.getBiometricAuthRequest())) {
                    callbackOuter.onFailed(builder.getAllAvailableTypes().map {
                        AuthenticationResult(
                            it,
                            reason = AuthenticationFailureReason.LOCKED_OUT
                        )
                    }.toSet())
                    authFlowInProgress.set(false)
                } else
                    startAuth(callbackOuter)
            }
        }
    }

    private fun checkHardware(): AuthenticationFailureReason {
//        if (!BiometricManagerCompat.hasPermissionsGranted(impl.builder.getBiometricAuthRequest())) {
//            BiometricLoggerImpl.e("BiometricPromptCompat.checkHardware - missed permissions")
//            return AuthenticationFailureReason.MISSING_PERMISSIONS_ERROR
//        } else
        if (!BiometricManagerCompat.isHardwareDetected(impl.builder.getBiometricAuthRequest())) {
            BiometricLoggerImpl.e("BiometricPromptCompat.checkHardware - isHardwareDetected")
            return AuthenticationFailureReason.NO_HARDWARE
        } else if (!BiometricManagerCompat.hasEnrolled(impl.builder.getBiometricAuthRequest())) {
            BiometricLoggerImpl.e("BiometricPromptCompat.checkHardware - hasEnrolled")
            return AuthenticationFailureReason.NO_BIOMETRICS_REGISTERED
        } else if (BiometricManagerCompat.isLockOut(
                impl.builder.getBiometricAuthRequest(),
                false
            )
        ) {
            BiometricLoggerImpl.e("BiometricPromptCompat.checkHardware - isLockOut")
            return AuthenticationFailureReason.LOCKED_OUT
        } else if (BiometricManagerCompat.isBiometricSensorPermanentlyLocked(
                impl.builder.getBiometricAuthRequest(),
                false
            )
        ) {
            BiometricLoggerImpl.e("BiometricPromptCompat.checkHardware - isBiometricSensorPermanentlyLocked")
            return AuthenticationFailureReason.HARDWARE_UNAVAILABLE
        } else return AuthenticationFailureReason.UNKNOWN
    }

    private fun startAuth(
        callbackOuter: AuthenticationCallback
    ) {
        if (builder.getActivity() == null) {
            BiometricLoggerImpl.e(
                IllegalStateException(),
                "Unable to start BiometricPromptCompat.authenticate() cause of Activity destroyed"

            )
            callbackOuter.onCanceled(builder.getAllAvailableTypes().map {
                AuthenticationResult(
                    it,
                    reason = AuthenticationFailureReason.INTERNAL_ERROR,
                    description = "Unable to start BiometricPromptCompat.authenticate() cause of Activity destroyed"
                )
            }.toSet())
            authFlowInProgress.set(false)
            return
        }
        val authTask = {
            if (builder.getActivity() == null) {
                BiometricLoggerImpl.e(
                    IllegalStateException(),
                    "Unable to start BiometricPromptCompat.authenticate() cause of Activity destroyed"
                )
                callbackOuter.onCanceled(builder.getAllAvailableTypes().map {
                    AuthenticationResult(
                        it,
                        reason = AuthenticationFailureReason.INTERNAL_ERROR,
                        description = "Unable to start BiometricPromptCompat.authenticate() cause of Activity destroyed"
                    )
                }.toSet())
                authFlowInProgress.set(false)
            } else {
                BiometricLoggerImpl.d("BiometricPromptCompat.startAuth")
                val activityViewWatcher = try {
                    if (!builder.isSilentAuthEnabled()) ActivityViewWatcher(
                        impl.builder,
                        object : ActivityViewWatcher.ForceToCloseCallback {
                            override fun onCloseBiometric() {
                                BiometricLoggerImpl.e("BiometricPromptCompat.onCloseBiometric")
                                cancelAuthentication()
                            }
                        }) else null
                } catch (e: Throwable) {
                    BiometricLoggerImpl.e(e)
                    null
                }

                val callback = object : AuthenticationCallback() {

                    private var isOpened = AtomicBoolean(false)
                    private var lastKnownOrientation = AtomicInteger(0)
                    override fun onSucceeded(result: Set<AuthenticationResult>) {
                        if (isOpened.get()) {
                            super.onSucceeded(result)
                            if (builder.isDeviceCredentialFallbackAllowed() && builder.forceDeviceCredential()) {
                                val checkHardware = checkHardware()
                                val interruptAuth = when (checkHardware) {
                                    //All good
                                    AuthenticationFailureReason.UNKNOWN -> false
                                    //Not able to continue
                                    else -> true
                                }
                                if (!interruptAuth) {
                                    BiometricLoggerImpl.e("BiometricPromptCompat.AuthenticationCallback.onSucceeded restart auth with biometric")
                                    builder.setForceDeviceCredentials(false)
                                    if (::oldTitle.isInitialized)
                                        builder.setTitle(oldTitle)
                                    if (::oldDescription.isInitialized)
                                        builder.setDescription(oldDescription)
                                    ExecutorHelper.postDelayed(
                                        {
                                            authenticateInternal(this)
                                        },
                                        AndroidContext.appContext.resources.getInteger(android.R.integer.config_shortAnimTime)
                                            .toLong()
                                    )
                                    return
                                }
                            }
                            val confirmed = result.toMutableSet()

                            BiometricLoggerImpl.d("BiometricPromptCompat.AuthenticationCallback.onSucceeded1 = $confirmed")
                            //Fix for devices that call onAuthenticationSucceeded() without enrolled biometric
                            if (builder.shouldAutoVerifyCryptoAfterSuccess()) {
                                if (CryptographyManager.encryptData(
                                        AndroidContext.appContext.packageName.toByteArray(
                                            Charset.forName("UTF-8")
                                        ), confirmed
                                    ) == null
                                ) {
                                    callbackOuter.onCanceled(builder.getAllAvailableTypes().map {
                                        AuthenticationResult(
                                            it,
                                            reason = AuthenticationFailureReason.CRYPTO_ERROR,
                                            description = "Error occurred during Cryptography verification"
                                        )
                                    }.toSet())
                                    return
                                } else {
                                    val filtered = confirmed.map {
                                        AuthenticationResult(it.type)
                                    }
                                    confirmed.apply {
                                        clear()
                                        confirmed.addAll(filtered)
                                    }
                                }
                            }
                            BiometricLoggerImpl.d("BiometricPromptCompat.AuthenticationCallback.onSucceeded2 = $confirmed")
                            ExecutorHelper.post {
                                callbackOuter.onSucceeded(confirmed.toSet())
                            }
                            ExecutorHelper.post {
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
                            }

                            onUIClosed()
                        }
                    }

                    override fun onCanceled(canceled: Set<AuthenticationResult>) {
                        if (isOpened.get()) {
                            BiometricLoggerImpl.d("BiometricPromptCompat.AuthenticationCallback.onCanceled")
                            ExecutorHelper.post { callbackOuter.onCanceled(canceled) }
                            onUIClosed()
                        }
                    }

                    override fun onFailed(
                        canceled: Set<AuthenticationResult>
                    ) {
                        if (isOpened.get()) {
                            //Lock/Permanent Lock
                            if (System.currentTimeMillis() - startTsImpl <= AndroidContext.appContext.resources.getInteger(
                                    android.R.integer.config_longAnimTime
                                )
                                && (oldIsBiometricReadyForUsage != BiometricManagerCompat.isBiometricSensorPermanentlyLocked(
                                    builder.getBiometricAuthRequest()
                                ))
                                && builder.isDeviceCredentialFallbackAllowed() && !builder.forceDeviceCredential()
                            ) {
                                BiometricLoggerImpl.e("BiometricPromptCompat.AuthenticationCallback.onFailed restart auth with credentials")
                                builder.setForceDeviceCredentials(true)
                                ExecutorHelper.postDelayed(
                                    {
                                        authenticateInternal(this)
                                    },
                                    AndroidContext.appContext.resources.getInteger(android.R.integer.config_shortAnimTime)
                                        .toLong()
                                )
                                return
                            }
                            BiometricLoggerImpl.d("BiometricPromptCompat.AuthenticationCallback.onFailed=$canceled")

                            ExecutorHelper.post {
                                callbackOuter.onFailed(
                                    canceled
                                )
                            }
                            onUIClosed()
                        }
                    }

                    override fun onUIOpened() {
                        if (!isOpened.get()) {
                            isOpened.set(true)
                            if (DevicesWithKnownBugs.hasUnderDisplayFingerprint) {
                                lastKnownOrientation.set(
                                    builder.getActivity()?.requestedOrientation
                                        ?: builder.getMultiWindowSupport().screenOrientation
                                )
                                builder.getActivity()?.requestedOrientation =
                                    builder.getMultiWindowSupport().screenOrientation
                            }
                            BiometricLoggerImpl.d("BiometricPromptCompat.AuthenticationCallback.onUIOpened")
                            val s =
                                "BiometricOpeningTime: onUIOpened << ${System.currentTimeMillis() - startTs} ms"
                            BiometricLoggerImpl.d("BiometricPromptCompat $s")
                            ExecutorHelper.post {
                                appBackgroundDetector.attachListeners()
                            }
                            ExecutorHelper.post { callbackOuter.onUIOpened() }
                            if (!builder.isSilentAuthEnabled()) {
                                ExecutorHelper.post { activityViewWatcher?.setupListeners() }
                                ExecutorHelper.post {
                                    builder.getActivity()?.let {
                                        StatusBarTools.setNavBarAndStatusBarColors(
                                            it.window,
                                            DialogMainColor.getColor(
                                                builder.getContext(),
                                                DarkLightThemes.isNightModeCompatWithInscreen(
                                                    builder.getContext()
                                                )
                                            ),
                                            DialogMainColor.getColor(
                                                builder.getContext(),
                                                !DarkLightThemes.isNightModeCompatWithInscreen(
                                                    builder.getContext()
                                                )
                                            ),
                                            builder.getStatusBarColor()
                                        )
                                    }
                                    if (DevicesWithKnownBugs.hasUnderDisplayFingerprint && builder.isNotificationEnabled()) {
                                        BiometricNotificationManager.showNotification(
                                            builder
                                        )
                                    }

                                }
                            }
                        }
                    }

                    override fun onUIClosed() {
                        if (isOpened.get()) {
                            isOpened.set(false)
                            if (DevicesWithKnownBugs.hasUnderDisplayFingerprint) {
                                builder.getActivity()?.requestedOrientation =
                                    lastKnownOrientation.get()
                            }
                            BiometricLoggerImpl.e("BiometricPromptCompat.AuthenticationCallback.onUIClosed")
                            ExecutorHelper.post { appBackgroundDetector.detachListeners() }
                            ExecutorHelper.post {
                                callbackOuter.onUIClosed()
                                authFlowInProgress.set(false)
                            }
                            ExecutorHelper.post {
                                BiometricAuthentication.cancelAuthentication()//cancel previews and reinit for next usage
                            }
                            if (!builder.isSilentAuthEnabled()) {
                                ExecutorHelper.post { activityViewWatcher?.resetListeners() }
                                ExecutorHelper.post {
                                    builder.getActivity()?.let {
                                        StatusBarTools.setNavBarAndStatusBarColors(
                                            it.window,
                                            builder.getNavBarColor(),
                                            builder.getDividerColor(),
                                            builder.getStatusBarColor()
                                        )
                                    }
                                    if (DevicesWithKnownBugs.hasUnderDisplayFingerprint && builder.isNotificationEnabled()) {
                                        BiometricNotificationManager.dismissAll()
                                    }
                                }
                            }

                        }
                    }
                }
                authenticateInternal(callback)
            }
        }
        if (!builder.isSilentAuthEnabled()) {
            if (DevicesWithKnownBugs.hasUnderDisplayFingerprint && builder.isNotificationEnabled()) {
                BiometricNotificationManager.initNotificationsPreferences()
                NotificationPermissionsHelper.checkNotificationPermissions(
                    builder.getActivity(),
                    BiometricNotificationManager.CHANNEL_ID,
                    {
                        authTask.invoke()
                    },
                    {
                        //continue anyway
                        authTask.invoke()
                    })
                return
            }
        }
        authTask.invoke()
    }


    private fun authenticateInternal(
        callback: AuthenticationCallback
    ) {
        BiometricLoggerImpl.e("BiometricPromptCompat.authenticateInternal();  isDeviceCredentialFallbackAllowed=${builder.isDeviceCredentialFallbackAllowed()}; forceDeviceCredential=${builder.forceDeviceCredential()}")
        if (builder.getActivity() == null) {
            BiometricLoggerImpl.e(
                IllegalStateException(),
                "Unable to start BiometricPromptCompat.authenticate() cause of Activity destroyed"

            )
            callback.onCanceled(builder.getAllAvailableTypes().map {
                AuthenticationResult(
                    it,
                    reason = AuthenticationFailureReason.INTERNAL_ERROR,
                    description = "Unable to start BiometricPromptCompat.authenticate() cause of Activity destroyed"
                )
            }.toSet())
            authFlowInProgress.set(false)
            return
        }
        try {
            BiometricLoggerImpl.d("BiometricPromptCompat.authenticateInternal() - impl.authenticate $impl")

            callback.updateTimestamp()

            if (!builder.forceDeviceCredential()) {
                BiometricLoggerImpl.d("BiometricPromptCompat BiometricOpeningTime: authenticateInternal >> regular ${System.currentTimeMillis() - startTs} ms")
                startTsImpl = System.currentTimeMillis()
                impl.authenticate(callback)
            } else {
                BiometricLoggerImpl.d("BiometricPromptCompat BiometricOpeningTime: authenticateInternal >> credentials ${System.currentTimeMillis() - startTs} ms")
                if (builder.getCryptographyPurpose() != null) {
                    callback.onFailed(builder.getAllAvailableTypes().map {
                        AuthenticationResult(
                            it,
                            reason = AuthenticationFailureReason.CRYPTO_ERROR,
                            description = "Cryptography not supported with DeviceCredential fallback"
                        )
                    }.toSet())
                    return
                }
                if (!::oldTitle.isInitialized) {
                    oldTitle = builder.getTitle() ?: ""
                }
                if (!::oldDescription.isInitialized) {
                    oldDescription = builder.getDescription() ?: ""
                }
                val secureScreenDialog = {
                    val title = try {
                        val appInfo =
                            (if (Utils.isAtLeastT) AndroidContext.appContext.packageManager.getApplicationInfo(
                                AndroidContext.appContext.packageName ?: "",
                                PackageManager.ApplicationInfoFlags.of(0L)
                            ) else AndroidContext.appContext.packageManager.getApplicationInfo(
                                AndroidContext.appContext.packageName ?: "",
                                0
                            ))
                        AndroidContext.appContext.packageManager.getApplicationLabel(appInfo)
                            .ifEmpty {
                                (builder.getActivity()
                                    ?: builder.getContext()).getString(appInfo.labelRes)
                            }
                    } catch (e: Throwable) {
                        oldTitle
                    }
                    builder.setTitle(title)
                    builder.setDescription(
                        LocalizationHelper.getLocalizedString(
                            builder.getActivity() ?: builder.getContext(),
                            R.string.biometriccompat_use_devicecredentials
                        )
                    )
                    if (impl is BiometricPromptApi28Impl) {//BiometricPrompt deal with credentials natively
                        impl.authenticate(callback)
                    } else {
                        val activity = builder.getActivity()
                        if (activity == null) {
                            BiometricLoggerImpl.e(
                                "Unable to start BiometricPromptCompat.authenticate() cause of Activity destroyed",
                                IllegalStateException()
                            )
                            callback.onCanceled(builder.getAllAvailableTypes().map {
                                AuthenticationResult(
                                    it,
                                    reason = AuthenticationFailureReason.INTERNAL_ERROR,
                                    description = "Unable to start BiometricPromptCompat.authenticate() cause of Activity destroyed"
                                )
                            }.toSet())
                        } else {
                            CredentialsRequestFragment.showFragment(
                                activity,
                                null,
                                builder.getDescription()
                            ) {
                                if (it) {
                                    callback.onSucceeded(
                                        mutableSetOf(
                                            AuthenticationResult(
                                                BiometricType.BIOMETRIC_ANY
                                            )
                                        )
                                    )
                                } else {
                                    callback.onCanceled(builder.getAllAvailableTypes().map {
                                        AuthenticationResult(
                                            it,
                                            reason = AuthenticationFailureReason.CANCELED_BY_USER,
                                            description = "Credentials requested, but user cancel verification"
                                        )
                                    }.toSet())
                                }
                            }
                            callback.onUIOpened()
                        }
                    }
                }
                if (!A11yDetection.shouldWeTrustA11y(builder.getContext())) {
                    UntrustedAccessibilityFragment.askForTrust(builder.getActivity() ?: return) {
                        if (it) {
                            secureScreenDialog.invoke()
                        } else {
                            callback.onCanceled(builder.getAllAvailableTypes().map {
                                AuthenticationResult(
                                    it,
                                    reason = AuthenticationFailureReason.CANCELED_BY_USER,
                                    description = "Insecure accessibility service(s) detected, user decide to interrupt biometric"
                                )
                            }.toSet())
                        }
                    }
                    callback.onUIOpened()
                } else {
                    secureScreenDialog.invoke()
                }
            }
        } catch (e: IllegalStateException) {
            callback.onFailed(builder.getAllAvailableTypes().map {
                AuthenticationResult(
                    it,
                    reason = AuthenticationFailureReason.INTERNAL_ERROR,
                    description = e.message
                )
            }.toSet())
        }
    }

    fun cancelAuthentication() {
        if (!API_ENABLED || !authFlowInProgress.get() || !isInitialized) {
            return
        }
        authFlowInProgress.set(false)
        ExecutorHelper.post {
            impl.cancelAuthentication()
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
        internal fun updateTimestamp() {
            authCallTimeStamp.set(System.currentTimeMillis())
        }

        @MainThread
        @CallSuper
        @Throws(BiometricAuthException::class)
        open fun onSucceeded(confirmed: Set<AuthenticationResult>) {
            val tmp = System.currentTimeMillis()
            if (reference.get() && tmp - authCallTimeStamp.get() <= skipTimeout) throw BiometricAuthException(
                "Biometric flow hooking detected"
            )
        }

        @MainThread
        open fun onCanceled(canceled: Set<AuthenticationResult>) {
        }

        @MainThread
        open fun onFailed(
            canceled: Set<AuthenticationResult>
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
        activity: FragmentActivity? = null
    ) {
        companion object {
            init {
                //lazy init
                init()
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
                for (type in BiometricType.entries) {
                    if (type == BiometricType.BIOMETRIC_ANY)
                        continue
                    val request = BiometricAuthRequest(
                        api,
                        type
                    )
                    if (BiometricManagerCompat.isBiometricAvailable(request)) {
                        types.add(type)
                    }
                }
            } else {
                if (BiometricManagerCompat.isBiometricAvailable(biometricAuthRequest))
                    types.add(biometricAuthRequest.type)
            }
            types
        }
        private val secondaryAvailableTypes: HashSet<BiometricType> by lazy {
            val types = HashSet<BiometricType>()
            if (HardwareAccessImpl.getInstance(biometricAuthRequest).isNewBiometricApi) {
                if (biometricAuthRequest.type == BiometricType.BIOMETRIC_ANY) {
                    for (type in BiometricType.entries) {
                        if (type == BiometricType.BIOMETRIC_ANY)
                            continue
                        val request = BiometricAuthRequest(
                            BiometricApi.LEGACY_API,
                            type
                        )
                        if (BiometricManagerCompat.isBiometricAvailable(request)) {
                            types.add(type)
                        }
                    }
                } else {
                    if (BiometricManagerCompat.isBiometricAvailable(biometricAuthRequest))
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
                        getActivity() ?: getContext(),
                        getAllAvailableTypes()
                    )
                return field
            }

        private var dialogSubtitle: CharSequence? = null

        private var dialogDescription: CharSequence? = null

        private var negativeButtonText: CharSequence? = null

        private lateinit var multiWindowSupport: MultiWindowSupport

        private var notificationEnabled = false

        private var backgroundBiometricIconsEnabled = true

        private var biometricCryptographyPurpose: BiometricCryptographyPurpose? = null

        @ColorInt
        private var colorNavBar: Int = Color.TRANSPARENT

        @ColorInt
        private var dividerColor: Int = Color.TRANSPARENT

        @ColorInt
        private var colorStatusBar: Int = Color.TRANSPARENT

        private var isTruncateChecked: Boolean? = null


        private val currentActivity = activity ?: (AndroidContext.activity as? FragmentActivity
            ?: throw java.lang.IllegalStateException("No activity on screen"))

        private var isDeviceCredentialFallbackAllowed: Boolean = false
        private var forceDeviceCredential: Boolean = false

        init {

            if (BiometricErrorLockoutPermanentFix.isRebootDetected())
                BiometricErrorLockoutPermanentFix.resetBiometricSensorPermanentlyLocked()

            reference.set(false)
            HookDetection.detect(object : HookDetection.HookDetectionListener {
                override fun onDetected(flag: Boolean) {
                    reference.set(flag)
                }
            })
            setDeviceCredentialFallbackAllowed(true)
            getActivity()?.let { context ->
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
            //Known issue: at least "OnePlus 9" call onSuccess when "Cancel" button clicked,
            //so checking the crypto is only the way to check real reason - it's Canceled or Success

            //Due to limitations, applicable only for Fingerprint
            //https://github.com/sergeykomlach/AdvancedBiometricPromptCompat/issues/305
            return if (biometricCryptographyPurpose?.purpose == null && deviceInfo?.model?.startsWith(
                    "OnePlus 9",
                    ignoreCase = true
                ) == true &&
                Build.VERSION.SDK_INT in Build.VERSION_CODES.S..Build.VERSION_CODES.S_V2 &&
                getAllAvailableTypes().contains(BiometricType.BIOMETRIC_FINGERPRINT)
            ) {
                biometricCryptographyPurpose =
                    BiometricCryptographyPurpose(BiometricCryptographyPurpose.ENCRYPT)
                true
            } else if (biometricCryptographyPurpose?.purpose == BiometricCryptographyPurpose.ENCRYPT && deviceInfo?.model?.startsWith(
                    "OnePlus 9",
                    ignoreCase = true
                ) == true &&
                Build.VERSION.SDK_INT in Build.VERSION_CODES.S..Build.VERSION_CODES.S_V2 &&
                getAllAvailableTypes().contains(BiometricType.BIOMETRIC_FINGERPRINT)
            ) {
                true
            } else false
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

        fun forceDeviceCredential(): Boolean {
            return isDeviceCredentialFallbackAllowed() && forceDeviceCredential
        }

        fun isDeviceCredentialFallbackAllowed(): Boolean {
            return isDeviceCredentialFallbackAllowed && BiometricManagerCompat.isDeviceSecureAvailable()
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
            return isTruncateChecked == true
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

        fun getActivity(): FragmentActivity? {
            if (isActivityFinished(currentActivity)) {
                BiometricLoggerImpl.e(
                    "BiometricPromptCompat.getActivity",
                    IllegalStateException("No activity on screen")
                )
                return null
            }
            return currentActivity
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
            this.biometricCryptographyPurpose = biometricCryptographyPurpose
            return this
        }

        fun setForceDeviceCredentials(enabled: Boolean): Builder {
            if (this.isDeviceCredentialFallbackAllowed) {
                this.forceDeviceCredential = enabled
            }
            return this
        }

        fun setDeviceCredentialFallbackAllowed(enabled: Boolean): Builder {
            this.isDeviceCredentialFallbackAllowed = enabled
            this.forceDeviceCredential =
                enabled && BiometricManagerCompat.isBiometricSensorPermanentlyLocked(
                    biometricAuthRequest
                )
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
            dialogTitle = (getActivity() ?: getContext()).getString(dialogTitleRes)
            return this
        }

        fun setSubtitle(dialogSubtitle: CharSequence?): Builder {
            this.dialogSubtitle = dialogSubtitle
            return this
        }

        fun setSubtitle(@StringRes dialogSubtitleRes: Int): Builder {
            dialogSubtitle = (getActivity() ?: getContext()).getString(dialogSubtitleRes)
            return this
        }

        fun setDescription(dialogDescription: CharSequence?): Builder {
            this.dialogDescription = dialogDescription
            return this
        }

        fun setDescription(@StringRes dialogDescriptionRes: Int): Builder {
            dialogDescription = (getActivity() ?: getContext()).getString(dialogDescriptionRes)
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
            negativeButtonText = (getActivity() ?: getContext()).getString(res)
            return this
        }

        fun build(): BiometricPromptCompat {
            isTruncateChecked()
            return BiometricPromptCompat(this)
        }
    }
}