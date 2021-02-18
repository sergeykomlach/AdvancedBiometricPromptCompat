package dev.skomlach.biometric.compat

import android.annotation.TargetApi
import android.content.DialogInterface
import android.os.Build
import android.os.Looper
import android.view.View
import android.view.ViewTreeObserver.OnWindowFocusChangeListener
import androidx.annotation.*
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.fragment.app.FragmentActivity
import dev.skomlach.biometric.compat.BiometricManagerCompat.hasEnrolled
import dev.skomlach.biometric.compat.BiometricManagerCompat.isBiometricSensorPermanentlyLocked
import dev.skomlach.biometric.compat.BiometricManagerCompat.isHardwareDetected
import dev.skomlach.biometric.compat.BiometricManagerCompat.isLockOut
import dev.skomlach.biometric.compat.BiometricManagerCompat.isNewBiometricApi
import dev.skomlach.biometric.compat.engine.AuthenticationFailureReason
import dev.skomlach.biometric.compat.engine.BiometricAuthentication
import dev.skomlach.biometric.compat.engine.BiometricInitListener
import dev.skomlach.biometric.compat.engine.BiometricMethod
import dev.skomlach.biometric.compat.engine.internal.core.interfaces.BiometricModule
import dev.skomlach.biometric.compat.impl.BiometricPromptApi28Impl
import dev.skomlach.biometric.compat.impl.BiometricPromptGenericImpl
import dev.skomlach.biometric.compat.impl.IBiometricPromptImpl
import dev.skomlach.biometric.compat.impl.PermissionsFragment
import dev.skomlach.biometric.compat.utils.ActiveWindow
import dev.skomlach.biometric.compat.utils.DeviceUnlockedReceiver
import dev.skomlach.biometric.compat.utils.device.DeviceInfo
import dev.skomlach.biometric.compat.utils.device.DeviceInfoManager
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl
import dev.skomlach.biometric.compat.utils.notification.BiometricNotificationManager
import dev.skomlach.biometric.compat.utils.statusbar.StatusBarTools
import dev.skomlach.common.contextprovider.AndroidContext
import dev.skomlach.common.logging.LogCat
import dev.skomlach.common.misc.ExecutorHelper
import dev.skomlach.common.misc.multiwindow.MultiWindowSupport
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.collections.HashSet

class BiometricPromptCompat private constructor(private val builder: Builder) {
    companion object {
        init {
            AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)
        }
        @JvmStatic
        val availableAuthRequests = ArrayList<BiometricAuthRequest>()
        @JvmStatic
        fun logging(enabled: Boolean){
            LogCat.DEBUG = enabled
            BiometricLoggerImpl.DEBUG = enabled
        }
        private val pendingTasks: MutableList<Runnable?> = Collections.synchronizedList(ArrayList<Runnable?>())
        private var isBiometricInit = AtomicBoolean(false)
        var isInit = false
            get() = isBiometricInit.get()
            private set
        private var initInProgress = AtomicBoolean(false)
        var deviceInfo: DeviceInfo?=null
        get() {
            if(field == null){
                ExecutorHelper.INSTANCE.startOnBackground{
                        DeviceInfoManager.INSTANCE.getDeviceInfo(object  : DeviceInfoManager.OnDeviceInfoListener{
                            override fun onReady(info: DeviceInfo?) {
                                field = info
                            }
                        })
                    }
            }
            return field
        }
        @MainThread
        @JvmStatic
        fun init(execute: Runnable? = null) {
            if (Looper.getMainLooper().thread !== Thread.currentThread())
                throw IllegalThreadStateException("Main Thread required")

            if (isBiometricInit.get()) {
                BiometricLoggerImpl.e("BiometricPromptCompat.init() - ready")
                execute?.let { ExecutorHelper.INSTANCE.handler.post(it) }
            } else {
                if (initInProgress.get()) {
                    BiometricLoggerImpl.e("BiometricPromptCompat.init() - pending")
                    pendingTasks.add(execute)
                } else {
                    BiometricLoggerImpl.e("BiometricPromptCompat.init()")
                    isBiometricInit.set(false)
                    initInProgress.set(true)
                    pendingTasks.add(execute)
                    AndroidContext.appContext
                    startBiometricInit()
                    ExecutorHelper.INSTANCE.startOnBackground{
                        DeviceInfoManager.INSTANCE.getDeviceInfo(object  : DeviceInfoManager.OnDeviceInfoListener{
                            override fun onReady(info: DeviceInfo?) {
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
        private fun startBiometricInit(){
            BiometricAuthentication.init(object : BiometricInitListener {
                override fun initFinished(
                    method: BiometricMethod,
                    module: BiometricModule?
                ) {
                }

                override fun onBiometricReady() {
                    BiometricLoggerImpl.e("BiometricPromptCompat.init() - finished")
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
                            }
                        }
                    }

                    for (task in pendingTasks) {
                        task?.let { ExecutorHelper.INSTANCE.handler.post(it) }
                    }
                    pendingTasks.clear()
                }
            })

        }
    }

    private val impl: IBiometricPromptImpl by lazy {
        val iBiometricPromptImpl = if (builder.biometricAuthRequest.api === BiometricApi.BIOMETRIC_API
            || (builder.biometricAuthRequest.api === BiometricApi.AUTO
                    && isNewBiometricApi(builder.biometricAuthRequest))
        ) {
            BiometricPromptApi28Impl(builder)
        } else {
            BiometricPromptGenericImpl(builder)
        }
        iBiometricPromptImpl
    }

    fun authenticate(callbackOuter: Result) {
        BiometricLoggerImpl.e("BiometricPromptCompat.authenticate()")
        ExecutorHelper.INSTANCE.startOnBackground {
            while (deviceInfo == null || !isInit) {
                try {
                    Thread.sleep(300)
                } catch (ignore: InterruptedException) {
                }
            }

            ExecutorHelper.INSTANCE.handler.post { startAuth(callbackOuter) }
        }
    }

    private fun startAuth(callbackOuter: Result){
        val callback = object : Result {
            override fun onSucceeded() {
                callbackOuter.onSucceeded()
            }

            override fun onCanceled() {
                callbackOuter.onCanceled()
            }

            override fun onFailed(reason: AuthenticationFailureReason?) {
                callbackOuter.onFailed(reason)
            }

            override fun onUIOpened() {
                callbackOuter.onUIOpened()
                if(impl.builder.colorNavBar != 0 && impl.builder.colorStatusBar != 0){
                    StatusBarTools.setNavBarAndStatusBarColors(impl.builder.context,
                        ContextCompat.getColor(impl.builder.context, getDialogMainColor()), impl.builder.colorStatusBar)
                }
                if (impl.builder.notificationEnabled) {
                    BiometricNotificationManager.INSTANCE.showNotification(
                        impl.builder.title,
                        impl.builder.description,
                        impl.builder.allTypes
                    )
                }
            }

            override fun onUIClosed() {
                if (impl.builder.notificationEnabled) {
                    BiometricNotificationManager.INSTANCE.dismissAll()
                }
                if(impl.builder.colorNavBar != 0 && impl.builder.colorStatusBar != 0){
                    StatusBarTools.setNavBarAndStatusBarColors(impl.builder.context, impl.builder.colorNavBar, impl.builder.colorStatusBar)
                }
                callbackOuter.onUIClosed()
            }
        }

        if (!isHardwareDetected(impl.builder.biometricAuthRequest)) {
            callback.onFailed(AuthenticationFailureReason.NO_HARDWARE)
            return
        }
        if (!hasEnrolled(impl.builder.biometricAuthRequest)) {
            callback.onFailed(AuthenticationFailureReason.NO_BIOMETRICS_REGISTERED)
            return
        }
        if (isLockOut(impl.builder.biometricAuthRequest)) {
            callback.onFailed(AuthenticationFailureReason.LOCKED_OUT)
            return
        }
        if (isBiometricSensorPermanentlyLocked(impl.builder.biometricAuthRequest)) {
            callback.onFailed(AuthenticationFailureReason.HARDWARE_UNAVAILABLE)
            return
        }
        BiometricLoggerImpl.e("BiometricPromptCompat. start PermissionsFragment.askForPermissions")
        PermissionsFragment.askForPermissions(
            impl.builder.context,
            impl.usedPermissions
        ) { authenticateInternal(callback) }
    }

    private fun authenticateInternal(callback: Result) {
        BiometricLoggerImpl.e("BiometricPromptCompat.authenticateInternal()")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                val d = impl.builder.activeWindow
                if (!ViewCompat.isAttachedToWindow(d)) {
                    checkForAttachAndStart(d, callback)
                } else {
                    checkForFocusAndStart(callback)
                }
            } else {
                BiometricLoggerImpl.e("BiometricPromptCompat.authenticateInternal() - impl.authenticate")
                impl.authenticate(callback)
            }
        } catch (ignore: IllegalStateException) {
            ExecutorHelper.INSTANCE.handler.post { callback.onCanceled() }
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    private fun checkForAttachAndStart(d: View, callback: Result) {
        BiometricLoggerImpl.e("BiometricPromptCompat.checkForAttachAndStart() - started")
        d.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                d.removeOnAttachStateChangeListener(this)
                checkForFocusAndStart(callback)
                d.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: View) {}
                    override fun onViewDetachedFromWindow(v: View) {
                        d.removeOnAttachStateChangeListener(this)
                        impl.cancelAuthenticate()
                    }
                })
            }

            override fun onViewDetachedFromWindow(v: View) {
                d.removeOnAttachStateChangeListener(this)
            }
        })
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    private fun checkForFocusAndStart(callback: Result) {
        BiometricLoggerImpl.e("BiometricPromptCompat.checkForFocusAndStart() - started")
        val activity = ActiveWindow.getActiveView(impl.builder.context)
        if (!activity.hasWindowFocus()) {
            val windowFocusChangeListener: OnWindowFocusChangeListener =
                object : OnWindowFocusChangeListener {
                    override fun onWindowFocusChanged(focus: Boolean) {
                        if (activity.hasWindowFocus()) {
                            activity.viewTreeObserver.removeOnWindowFocusChangeListener(this)
                            impl.authenticate(callback)
                        }
                    }
                }
            activity.viewTreeObserver.addOnWindowFocusChangeListener(windowFocusChangeListener)
        } else {
            impl.authenticate(callback)
        }
    }

    fun cancelAuthenticate() {
        ExecutorHelper.INSTANCE.startOnBackground {
            while (deviceInfo == null || !isInit) {
                try {
                    Thread.sleep(250)
                } catch (ignore: InterruptedException) {
                }
            }
            ExecutorHelper.INSTANCE.handler.post {
                impl.cancelAuthenticate() }
        }

    }

    fun cancelAuthenticateBecauseOnPause(): Boolean {
        return if(!isInit){
            ExecutorHelper.INSTANCE.startOnBackground {
                while (deviceInfo == null || !isInit) {
                    try {
                        Thread.sleep(250)
                    } catch (ignore: InterruptedException) {
                    }
                }
                ExecutorHelper.INSTANCE.handler.post {
                    impl.cancelAuthenticate() }
            }
            true
        } else
            impl.cancelAuthenticateBecauseOnPause()
    }

    @ColorRes
    fun getDialogMainColor(): Int {
        return if (impl.isNightMode) {
            android.R.color.black
        } else {
            R.color.material_grey_50
        }
    }

    interface Result {
        @MainThread
        fun onSucceeded()

        @MainThread
        fun onCanceled()

        @MainThread
        fun onFailed(reason: AuthenticationFailureReason?)

        @MainThread
        fun onUIOpened()
        @MainThread
        fun onUIClosed()
    }

    class Builder(
        @field:RestrictTo(RestrictTo.Scope.LIBRARY) val biometricAuthRequest: BiometricAuthRequest,
        @field:RestrictTo(
            RestrictTo.Scope.LIBRARY
        ) val context: FragmentActivity
    ) {
        val allTypes: HashSet<BiometricType> by lazy {
            val types = HashSet<BiometricType>()
            if (biometricAuthRequest.type == BiometricType.BIOMETRIC_ANY) {
                for (type in BiometricType.values()) {
                    if (type == BiometricType.BIOMETRIC_ANY)
                        continue
                    val request = BiometricAuthRequest(
                        biometricAuthRequest.api,
                        type
                    )
                    if (isHardwareDetected(request) && hasEnrolled(request)) {
                        types.add(type)
                    }
                }
            } else {
                types.add(biometricAuthRequest.type)
            }
            types
        }

        val activeWindow: View by lazy {
            ActiveWindow.getActiveView(context)
        }

        @JvmField @RestrictTo(RestrictTo.Scope.LIBRARY)
        var title: CharSequence? = null

        @JvmField @RestrictTo(RestrictTo.Scope.LIBRARY)
        var subtitle: CharSequence? = null

        @JvmField @RestrictTo(RestrictTo.Scope.LIBRARY)
        var description: CharSequence? = null

        @JvmField @RestrictTo(RestrictTo.Scope.LIBRARY)
        var negativeButtonText: CharSequence? = null

        @RestrictTo(RestrictTo.Scope.LIBRARY)
        var negativeButtonListener: DialogInterface.OnClickListener? = null

        @JvmField @RestrictTo(RestrictTo.Scope.LIBRARY)
        var multiWindowSupport: MultiWindowSupport = MultiWindowSupport(context)

        @JvmField @RestrictTo(RestrictTo.Scope.LIBRARY)
        var notificationEnabled = true

        @JvmField @RestrictTo(RestrictTo.Scope.LIBRARY)
        @ColorInt
        var colorNavBar: Int = 0
        @JvmField @RestrictTo(RestrictTo.Scope.LIBRARY)
        @ColorInt
        var colorStatusBar: Int = 0
        constructor(context: FragmentActivity) : this(
            BiometricAuthRequest(
                BiometricApi.AUTO,
                BiometricType.BIOMETRIC_ANY
            ), context
        ) {
        }
        fun allowToManageSystemBars( @ColorInt  activityNavBarColor: Int, @ColorInt  activityStatusBarColor: Int): Builder {
            this.colorNavBar = activityNavBarColor
            this.colorStatusBar = activityStatusBarColor
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
            title = context.getString(titleRes)
            return this
        }

        fun setSubtitle(subtitle: CharSequence?): Builder {
            this.subtitle = subtitle
            return this
        }

        fun setSubtitle(@StringRes subtitleRes: Int): Builder {
            subtitle = context.getString(subtitleRes)
            return this
        }

        fun setDescription(description: CharSequence?): Builder {
            this.description = description
            return this
        }

        fun setDescription(@StringRes descriptionRes: Int): Builder {
            description = context.getString(descriptionRes)
            return this
        }

        fun setNegativeButton(
            text: CharSequence,
            listener: DialogInterface.OnClickListener?
        ): Builder {
            negativeButtonText = text
            negativeButtonListener = listener
            return this
        }

        fun setNegativeButton(
            @StringRes textResId: Int,
            listener: DialogInterface.OnClickListener?
        ): Builder {
            negativeButtonText = context.getString(textResId)
            negativeButtonListener = listener
            return this
        }

        fun build(): BiometricPromptCompat {
            requireNotNull(title) { "You should set a title for BiometricPrompt." }
            requireNotNull(negativeButtonText) { "You should set a negativeButtonText for BiometricPrompt." }
            return BiometricPromptCompat(this)
        }
    }
}