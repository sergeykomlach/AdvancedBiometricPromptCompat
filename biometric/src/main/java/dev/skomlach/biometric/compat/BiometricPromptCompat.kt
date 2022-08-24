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

import android.graphics.Color
import android.os.Build
import android.os.Looper
import androidx.annotation.ColorInt
import androidx.annotation.MainThread
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import dev.skomlach.biometric.compat.BiometricManagerCompat.hasEnrolled
import dev.skomlach.biometric.compat.BiometricManagerCompat.isBiometricEnrollChanged
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
import dev.skomlach.biometric.compat.impl.IBiometricPromptImpl
import dev.skomlach.biometric.compat.impl.PermissionsFragment
import dev.skomlach.biometric.compat.impl.dialogs.HomeWatcher
import dev.skomlach.biometric.compat.utils.*
import dev.skomlach.biometric.compat.utils.activityView.ActivityViewWatcher
import dev.skomlach.biometric.compat.utils.device.DeviceInfo
import dev.skomlach.biometric.compat.utils.device.DeviceInfoManager
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl
import dev.skomlach.biometric.compat.utils.notification.BiometricNotificationManager
import dev.skomlach.biometric.compat.utils.statusbar.StatusBarTools
import dev.skomlach.biometric.compat.utils.themes.DarkLightThemes
import dev.skomlach.common.contextprovider.AndroidContext
import dev.skomlach.common.logging.LogCat
import dev.skomlach.common.misc.ExecutorHelper
import dev.skomlach.common.misc.Utils
import dev.skomlach.common.misc.isActivityFinished
import dev.skomlach.common.misc.multiwindow.MultiWindowSupport
import dev.skomlach.common.permissions.PermissionUtils
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.lang.ref.WeakReference
import java.nio.charset.Charset
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class BiometricPromptCompat private constructor(private val builder: Builder) {
    companion object {
        var API_ENABLED = true
            private set

        init {
            if (API_ENABLED) {
                AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        HiddenApiBypass.setHiddenApiExemptions("L");
                    }
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
            }
        }

        @JvmStatic
        fun apiEnabled(enabled: Boolean) {
            API_ENABLED = enabled
        }

        private val availableAuthRequests = ArrayList<BiometricAuthRequest>()

        @JvmStatic
        fun getAvailableAuthRequests(): List<BiometricAuthRequest> {
            return availableAuthRequests
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
        var isInit = false
            get() = isBiometricInit.get()
            private set
        private var initInProgress = AtomicBoolean(false)
        var deviceInfo: DeviceInfo? = null
            private set
            get() {
                if (field == null && !isDeviceInfoCheckInProgress.get()) {
                    ExecutorHelper.startOnBackground {
                        isDeviceInfoCheckInProgress.set(true)
                        DeviceInfoManager.getDeviceInfo(object :
                            DeviceInfoManager.OnDeviceInfoListener {
                            override fun onReady(info: DeviceInfo?) {
                                isDeviceInfoCheckInProgress.set(false)
                                field = info
                            }
                        })
                    }
                }
                return field
            }

        private var isDeviceInfoCheckInProgress = AtomicBoolean(false)
        private var authFlowInProgress = AtomicBoolean(false)

        @MainThread
        @JvmStatic
        fun init(execute: Runnable? = null) {
            if (!API_ENABLED)
                return
            if (Looper.getMainLooper().thread !== Thread.currentThread())
                throw IllegalThreadStateException("Main Thread required")

            if (isBiometricInit.get()) {
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
                    AndroidContext.appContext
                    startBiometricInit()
                    ExecutorHelper.startOnBackground {
                        isDeviceInfoCheckInProgress.set(true)
                        DeviceInfoManager.getDeviceInfo(object :
                            DeviceInfoManager.OnDeviceInfoListener {
                            override fun onReady(info: DeviceInfo?) {
                                isDeviceInfoCheckInProgress.set(false)
                                deviceInfo = info
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
                    //Add default first
                    var biometricAuthRequest = BiometricAuthRequest()
                    if (isHardwareDetected(biometricAuthRequest)) {
                        availableAuthRequests.add(biometricAuthRequest)
                    }

                    for (api in BiometricApi.values()) {
                        for (type in BiometricType.values()) {
                            if (type == BiometricType.BIOMETRIC_ANY)
                                continue
                            biometricAuthRequest = BiometricAuthRequest(api, type)
                            if (isHardwareDetected(biometricAuthRequest)) {
                                availableAuthRequests.add(biometricAuthRequest)
                                //just cache value
                                hasEnrolled(biometricAuthRequest)
                                isLockOut(biometricAuthRequest)
                                isBiometricEnrollChanged(biometricAuthRequest)
                            }
                        }
                    }

                    for (task in pendingTasks) {
                        task?.let { ExecutorHelper.post(it) }
                    }
                    pendingTasks.clear()
                }
            })
        }
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
                            if (BiometricManagerCompat.isBiometricReady(request)) {
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
        val iBiometricPromptImpl = if (isBiometricPrompt) {
            BiometricPromptApi28Impl(builder)
        } else {
            BiometricPromptGenericImpl(builder)
        }
        iBiometricPromptImpl
    }
    private var stopWatcher: Runnable? = null
    private val homeWatcher = HomeWatcher(object : HomeWatcher.OnHomePressedListener {
        override fun onHomePressed() {
            cancelAuthentication()
        }

        override fun onRecentAppPressed() {
            cancelAuthentication()
        }

        override fun onPowerPressed() {
            cancelAuthentication()
        }
    })
    private val fragmentLifecycleCallbacks = object :
        FragmentManager.FragmentLifecycleCallbacks() {
        private val atomicBoolean = AtomicInteger(0)
        private val dismissTask = Runnable {
            if (atomicBoolean.get() <= 0) {
                BiometricLoggerImpl.e("BiometricPromptCompat.dismissTask")
                cancelAuthentication()
            }
        }

        override fun onFragmentResumed(fm: FragmentManager, f: Fragment) {
            if (f is androidx.biometric.BiometricFragment ||
                f is androidx.biometric.FingerprintDialogFragment ||
                f is dev.skomlach.biometric.compat.impl.dialogs.BiometricPromptCompatDialog
            ) {
                BiometricLoggerImpl.d(
                    "BiometricPromptCompat.FragmentLifecycleCallbacks.onFragmentResumed - " +
                            "$f"
                )
                atomicBoolean.incrementAndGet()
            }
        }

        override fun onFragmentPaused(fm: FragmentManager, f: Fragment) {
            if (f is androidx.biometric.BiometricFragment ||
                f is androidx.biometric.FingerprintDialogFragment ||
                f is dev.skomlach.biometric.compat.impl.dialogs.BiometricPromptCompatDialog
            ) {
                BiometricLoggerImpl.d(
                    "BiometricPromptCompat.FragmentLifecycleCallbacks.onFragmentPaused - " +
                            "$f"
                )
                atomicBoolean.decrementAndGet()
                ExecutorHelper.removeCallbacks(dismissTask)
                val delay =
                    appContext.resources.getInteger(android.R.integer.config_longAnimTime)
                        .toLong()
                ExecutorHelper.postDelayed(
                    dismissTask,
                    delay
                )//delay for case when system fragment closed and fallback shown
            }
        }
    }

    fun authenticate(callbackOuter: AuthenticationCallback) {
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
            callbackOuter.onFailed(AuthenticationFailureReason.NO_HARDWARE, appContext.getString(androidx.biometric.R.string.fingerprint_error_hw_not_present))
            authFlowInProgress.set(false)
            return
        }
        BiometricLoggerImpl.d("BiometricPromptCompat.authenticate()")
        if (WideGamutBug.unsupportedColorMode(builder.getContext())) {
            BiometricLoggerImpl.e("BiometricPromptCompat.startAuth - WideGamutBug")
            callbackOuter.onFailed(AuthenticationFailureReason.HARDWARE_UNAVAILABLE, appContext.getString(androidx.biometric.R.string.fingerprint_error_hw_not_available))
            authFlowInProgress.set(false)
            return
        }
        val startTime = System.currentTimeMillis()
        var timeout = false
        ExecutorHelper.startOnBackground {
            while (!builder.isTruncateChecked() || isDeviceInfoCheckInProgress.get() || !isInit) {
                timeout = System.currentTimeMillis() - startTime >= TimeUnit.SECONDS.toMillis(5)
                if (timeout) {
                    break
                }
                try {
                    Thread.sleep(250)
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

    private fun startAuth(callbackOuter: AuthenticationCallback) {
        if (isActivityFinished(builder.getContext())) {
            BiometricLoggerImpl.e("Unable to start BiometricPromptCompat.authenticate() cause of Activity destroyed")
            callbackOuter.onCanceled()
            authFlowInProgress.set(false)
            return
        }
        BiometricLoggerImpl.d("BiometricPromptCompat.startAuth")

        if (!isHardwareDetected(impl.builder.getBiometricAuthRequest())) {
            BiometricLoggerImpl.e("BiometricPromptCompat.startAuth - isHardwareDetected")
            callbackOuter.onFailed(AuthenticationFailureReason.NO_HARDWARE, appContext.getString(androidx.biometric.R.string.fingerprint_error_hw_not_present))
            authFlowInProgress.set(false)
            return
        }
        if (!hasEnrolled(impl.builder.getBiometricAuthRequest())) {
            BiometricLoggerImpl.e("BiometricPromptCompat.startAuth - hasEnrolled")
            callbackOuter.onFailed(AuthenticationFailureReason.NO_BIOMETRICS_REGISTERED, appContext.getString(androidx.biometric.R.string.fingerprint_error_no_fingerprints))
            authFlowInProgress.set(false)
            return
        }
        if (isLockOut(impl.builder.getBiometricAuthRequest())) {
            BiometricLoggerImpl.e("BiometricPromptCompat.startAuth - isLockOut")
            callbackOuter.onFailed(AuthenticationFailureReason.LOCKED_OUT, appContext.getString(androidx.biometric.R.string.fingerprint_error_lockout))
            authFlowInProgress.set(false)
            return
        }
        if (isBiometricSensorPermanentlyLocked(impl.builder.getBiometricAuthRequest())) {
            BiometricLoggerImpl.e("BiometricPromptCompat.startAuth - isBiometricSensorPermanentlyLocked")
            callbackOuter.onFailed(AuthenticationFailureReason.HARDWARE_UNAVAILABLE, appContext.getString(androidx.biometric.R.string.fingerprint_error_hw_not_available))
            authFlowInProgress.set(false)
            return
        }
        BiometricLoggerImpl.d("BiometricPromptCompat. start PermissionsFragment.askForPermissions")
        PermissionsFragment.askForPermissions(
            impl.builder.getContext(),
            usedPermissions
        ) {
            if (usedPermissions.isNotEmpty() && !PermissionUtils.hasSelfPermissions(
                    usedPermissions
                )
            ) {
                callbackOuter.onFailed(AuthenticationFailureReason.MISSING_PERMISSIONS_ERROR, null)
                authFlowInProgress.set(false)
            } else {
                val activityViewWatcher = try {
                    ActivityViewWatcher(
                        impl.builder,
                        object : ActivityViewWatcher.ForceToCloseCallback {
                            override fun onCloseBiometric() {
                                cancelAuthentication()
                            }
                        })
                } catch (e: Throwable) {
                    BiometricLoggerImpl.e(e)
                    null
                }

                val callback = object : AuthenticationCallback() {

                    private var isOpened = AtomicBoolean(false)
                    override fun onSucceeded(result: Set<AuthenticationResult>) {
                        val confirmed = result.toMutableSet()
                        try {
                            //Fix for OnePlus devices that call onAuthenticationSucceeded() without enrolled biometric
                            if (builder.shouldVerifyCryptoAfterSuccess()) {
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

                            BiometricLoggerImpl.d("BiometricPromptCompat.AuthenticationCallback.onSucceeded = $confirmed")
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

                    override fun onCanceled() {
                        BiometricLoggerImpl.d("BiometricPromptCompat.AuthenticationCallback.onCanceled")
                        try {
                            callbackOuter.onCanceled()
                        } finally {
                            onUIClosed()
                        }
                    }

                    override fun onFailed(reason: AuthenticationFailureReason?, description: CharSequence?) {
                        BiometricLoggerImpl.d("BiometricPromptCompat.AuthenticationCallback.onFailed=$reason")
                        try {
                            if (description == null) {
                                val msg = when (reason) {
                                    AuthenticationFailureReason.LOCKED_OUT -> appContext.getString(
                                        androidx.biometric.R.string.fingerprint_error_lockout
                                    )
                                    AuthenticationFailureReason.NO_HARDWARE -> appContext.getString(
                                        androidx.biometric.R.string.fingerprint_error_hw_not_present
                                    )
                                    AuthenticationFailureReason.NO_BIOMETRICS_REGISTERED -> appContext.getString(
                                        androidx.biometric.R.string.fingerprint_error_no_fingerprints
                                    )
                                    AuthenticationFailureReason.HARDWARE_UNAVAILABLE -> appContext.getString(
                                        androidx.biometric.R.string.fingerprint_error_hw_not_available
                                    )
                                    else -> null
                                }
                                callbackOuter.onFailed(reason, msg)
                            } else {
                                callbackOuter.onFailed(reason, description)
                            }
                        } finally {
                            onUIClosed()
                        }
                    }

                    override fun onUIOpened() {
                        BiometricLoggerImpl.d("BiometricPromptCompat.AuthenticationCallback.onUIOpened")
                        if (!isOpened.get()) {
                            isOpened.set(true)
                            callbackOuter.onUIOpened()
                            if (DeviceInfoManager.hasUnderDisplayFingerprint(deviceInfo) && builder.isNotificationEnabled()) {
                                BiometricNotificationManager.showNotification(builder)
                            }

                            StatusBarTools.setNavBarAndStatusBarColors(
                                builder.getContext().window,
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
                    }

                    override fun onUIClosed() {
                        BiometricLoggerImpl.d("BiometricPromptCompat.AuthenticationCallback.onUIClosed")
                        if (isOpened.get()) {
                            isOpened.set(false)
                            val closeAll = Runnable {
                                if (DeviceInfoManager.hasUnderDisplayFingerprint(deviceInfo) && builder.isNotificationEnabled()) {
                                    BiometricNotificationManager.dismissAll()
                                }
                                activityViewWatcher?.resetListeners()
                                StatusBarTools.setNavBarAndStatusBarColors(
                                    builder.getContext().window,
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
                            callbackOuter.onUIClosed()
//                    ExecutorHelper.removeCallbacks(fragmentLifecycleCallbacks.dismissTask)
                            stopWatcher?.run()
                            stopWatcher = null
                            try {
                                impl.builder.getContext().supportFragmentManager.unregisterFragmentLifecycleCallbacks(
                                    fragmentLifecycleCallbacks
                                )
                            } catch (ignore: Throwable) {
                            }
                            authFlowInProgress.set(false)
                        }
                    }
                }
                authenticateInternal(callback)
            }
        }
    }

    private val usedPermissions: List<String>
        get() {

            val permission: MutableSet<String> = HashSet()

            if (Utils.isAtLeastT && DeviceInfoManager.hasUnderDisplayFingerprint(deviceInfo) && builder.isNotificationEnabled()) {
                permission.add("android.permission.POST_NOTIFICATIONS")
            }
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
                    BiometricMethod.FACE_HUAWEI, BiometricMethod.FACE_SOTERAPI -> permission.add("android.permission.USE_FACERECOGNITION")
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
            try {
                impl.builder.getContext().supportFragmentManager.unregisterFragmentLifecycleCallbacks(
                    fragmentLifecycleCallbacks
                )
            } catch (ignore: Throwable) {
            }
            impl.builder.getContext().supportFragmentManager.registerFragmentLifecycleCallbacks(
                fragmentLifecycleCallbacks,
                false
            )
            stopWatcher = homeWatcher.startWatch()
//            val delay =
//                appContext.resources.getInteger(android.R.integer.config_longAnimTime)
//                    .toLong()
//            ExecutorHelper.postDelayed(fragmentLifecycleCallbacks.dismissTask, delay)
            impl.authenticate(callback)
        } catch (ignore: IllegalStateException) {
            try {
                impl.builder.getContext().supportFragmentManager.unregisterFragmentLifecycleCallbacks(
                    fragmentLifecycleCallbacks
                )
            } catch (ignore: Throwable) {
            }
            callback.onFailed(AuthenticationFailureReason.INTERNAL_ERROR, null)
            authFlowInProgress.set(false)
        }
    }

    fun cancelAuthentication() {
        if (!API_ENABLED) {
            return
        }
        ExecutorHelper.startOnBackground {
            while (isDeviceInfoCheckInProgress.get() || !isInit) {
                try {
                    Thread.sleep(250)
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
        @MainThread
        open fun onSucceeded(confirmed: Set<AuthenticationResult>) {
        }

        @MainThread
        open fun onCanceled() {
        }

        @MainThread
        open fun onFailed(reason: AuthenticationFailureReason?, description: CharSequence? = null) {
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
        dummy_reference: FragmentActivity
    ) {
        private val reference = WeakReference<FragmentActivity>(dummy_reference)
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
                    BiometricLoggerImpl.d(
                        "primaryAvailableTypes - $request -> ${
                            isHardwareDetected(
                                request
                            )
                        }"
                    )
                    if (BiometricManagerCompat.isBiometricReady(request)) {
                        types.add(type)
                    }
                }
            } else {
                if (BiometricManagerCompat.isBiometricReady(biometricAuthRequest))
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
                        BiometricLoggerImpl.d(
                            "secondaryAvailableTypes - $request -> ${
                                isHardwareDetected(
                                    request
                                )
                            }"
                        )
                        if (BiometricManagerCompat.isBiometricReady(request)) {
                            types.add(type)
                        }
                    }
                } else {
                    if (BiometricManagerCompat.isBiometricReady(biometricAuthRequest))
                        types.add(biometricAuthRequest.type)
                }
                types.removeAll(primaryAvailableTypes)
            }
            types
        }

        private var title: CharSequence? = null

        private var subtitle: CharSequence? = null

        private var description: CharSequence? = null

        private lateinit var multiWindowSupport: MultiWindowSupport

        private var notificationEnabled = true

        private var backgroundBiometricIconsEnabled = true

        private var experimentalFeaturesEnabled = BuildConfig.DEBUG

        private var biometricCryptographyPurpose: BiometricCryptographyPurpose? = null


        @ColorInt
        private var colorNavBar: Int = Color.TRANSPARENT

        @ColorInt
        private var dividerColor: Int = Color.TRANSPARENT

        @ColorInt
        private var colorStatusBar: Int = Color.TRANSPARENT

        private var isTruncateChecked = false
        private val appContext = AndroidContext.appContext
        private var verifyCryptoAfterSuccess = false
        init {
            getContext().let { context ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    this.colorNavBar = context.window.navigationBarColor
                    this.colorStatusBar = context.window.statusBarColor
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    dividerColor = context.window.navigationBarDividerColor
                }
            }
            if (API_ENABLED) {
                multiWindowSupport = MultiWindowSupport()
            }
            if(DevicesWithKnownBugs.isOnePlus){
                biometricCryptographyPurpose = BiometricCryptographyPurpose(BiometricCryptographyPurpose.ENCRYPT)
                verifyCryptoAfterSuccess = true
            }
        }

        constructor(dummy_reference: FragmentActivity) : this(
            BiometricAuthRequest(
                BiometricApi.AUTO,
                BiometricType.BIOMETRIC_ANY
            ), dummy_reference
        )

        fun shouldVerifyCryptoAfterSuccess(): Boolean {
            return verifyCryptoAfterSuccess
        }

        fun getTitle(): CharSequence? {
            return title
        }

        fun getSubtitle(): CharSequence? {
            return subtitle
        }

        fun getDescription(): CharSequence? {
            return description
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

        fun isExperimentalFeaturesEnabled(): Boolean {
            return experimentalFeaturesEnabled
        }

        fun isBackgroundBiometricIconsEnabled(): Boolean {
            return backgroundBiometricIconsEnabled
        }

        fun isNotificationEnabled(): Boolean {
            return notificationEnabled
        }

        fun isTruncateChecked(): Boolean {
            return isTruncateChecked
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

        fun getContext(): FragmentActivity {
            return reference.get() ?: (AndroidContext.activity as? FragmentActivity?)
            ?: throw java.lang.IllegalStateException("No activity on screen")
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
            verifyCryptoAfterSuccess = false
            this.biometricCryptographyPurpose = biometricCryptographyPurpose
            return this
        }

        fun setExperimentalFeaturesEnabled(enabled: Boolean): Builder {
            this.experimentalFeaturesEnabled = enabled
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

        fun setTitle(title: CharSequence?): Builder {
            this.title = title
            return this
        }

        fun setTitle(@StringRes titleRes: Int): Builder {
            title = appContext.getString(titleRes)
            return this
        }

        fun setSubtitle(subtitle: CharSequence?): Builder {
            this.subtitle = subtitle
            return this
        }

        fun setSubtitle(@StringRes subtitleRes: Int): Builder {
            subtitle = appContext.getString(subtitleRes)
            return this
        }

        fun setDescription(description: CharSequence?): Builder {
            this.description = description
            return this
        }

        fun setDescription(@StringRes descriptionRes: Int): Builder {
            description = appContext.getString(descriptionRes)
            return this
        }


        fun build(): BiometricPromptCompat {
            if (title == null)
                title = BiometricTitle.getRelevantTitle(
                    appContext,
                    getAllAvailableTypes()
                )
            TruncatedTextFix.recalculateTexts(this, object : TruncatedTextFix.OnTruncateChecked {
                override fun onDone() {
                    isTruncateChecked = true
                }
            })
            return BiometricPromptCompat(this)
        }
    }
}