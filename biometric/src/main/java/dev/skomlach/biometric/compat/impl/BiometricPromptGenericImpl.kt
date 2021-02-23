package dev.skomlach.biometric.compat.impl

import android.text.TextUtils
import androidx.annotation.RestrictTo
import dev.skomlach.biometric.compat.BiometricConfirmation
import dev.skomlach.biometric.compat.BiometricPromptCompat
import dev.skomlach.biometric.compat.BiometricType
import dev.skomlach.biometric.compat.engine.AuthenticationFailureReason
import dev.skomlach.biometric.compat.engine.AuthenticationHelpReason
import dev.skomlach.biometric.compat.engine.BiometricAuthentication.authenticate
import dev.skomlach.biometric.compat.engine.BiometricAuthentication.availableBiometricMethods
import dev.skomlach.biometric.compat.engine.BiometricAuthentication.cancelAuthentication
import dev.skomlach.biometric.compat.engine.BiometricAuthenticationListener
import dev.skomlach.biometric.compat.engine.BiometricMethod
import dev.skomlach.biometric.compat.impl.dialogs.BiometricPromptCompatDialogImpl
import dev.skomlach.biometric.compat.utils.DevicesWithKnownBugs.isHideDialogInstantly
import dev.skomlach.biometric.compat.utils.HardwareAccessImpl.Companion.getInstance
import dev.skomlach.biometric.compat.utils.Vibro
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.d
import dev.skomlach.biometric.compat.utils.notification.BiometricNotificationManager
import dev.skomlach.biometric.compat.utils.themes.DarkLightThemes.isNightMode
import dev.skomlach.common.misc.ExecutorHelper
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

@RestrictTo(RestrictTo.Scope.LIBRARY)
class BiometricPromptGenericImpl(override val builder: BiometricPromptCompat.Builder) :
    IBiometricPromptImpl, AuthCallback {
    private var dialog: BiometricPromptCompatDialogImpl? = null
    private val fmAuthCallback: BiometricAuthenticationListener =
        BiometricAuthenticationCallbackImpl()
    private var callback: BiometricPromptCompat.Result? = null
    private val isFingerprint = AtomicBoolean(false)
    private val confirmed: MutableSet<BiometricType?> = HashSet()
    init {
        isFingerprint.set(builder.allAvailableTypes.contains(BiometricType.BIOMETRIC_FINGERPRINT))
    }

    override fun authenticate(callback: BiometricPromptCompat.Result?) {
        d("BiometricPromptGenericImpl.authenticate():")
        this.callback = callback
        val doNotShowDialog = isFingerprint.get() && isHideDialogInstantly
        if (!doNotShowDialog) {
            dialog = BiometricPromptCompatDialogImpl(
                builder,
                this@BiometricPromptGenericImpl,
                isFingerprint.get()
            )
            dialog?.showDialog()
        } else {
            startAuth()
        }
        onUiOpened()
    }

    override fun cancelAuthenticate() {
        d("BiometricPromptGenericImpl.cancelAuthenticate():")
        if (dialog != null) dialog?.dismissDialog() else {
            stopAuth()
        }
        onUiClosed()
    }

    override val isNightMode: Boolean
        get() = if (dialog != null) dialog?.isNightMode == true else {
            isNightMode(builder.context)
        }
    override val usedPermissions: List<String>
        get() {
            val permission: MutableSet<String> = HashSet()
            val biometricMethodList: MutableList<BiometricMethod> = ArrayList()
            for (m in availableBiometricMethods) {
                if (builder.allAvailableTypes.contains(m.biometricType)) {
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
                }
            }
            return ArrayList(permission)
        }

    override fun cancelAuthenticateBecauseOnPause(): Boolean {
        d("BiometricPromptGenericImpl.cancelAuthenticateBecauseOnPause():")
        return if (dialog != null) {
            dialog?.cancelAuthenticateBecauseOnPause() == true
        } else {
            cancelAuthenticate()
            true
        }
    }

    override fun startAuth() {
        if (builder.notificationEnabled) {
            BiometricNotificationManager.INSTANCE.showNotification(builder)
        }
        d("BiometricPromptGenericImpl.startAuth():")
        val types: List<BiometricType?> = ArrayList(
            builder.allAvailableTypes
        )
        authenticate(if (dialog != null) dialog?.container else null, types, fmAuthCallback)
    }

    override fun stopAuth() {
        d("BiometricPromptGenericImpl.stopAuth():")
        cancelAuthentication()
        if (builder.notificationEnabled) {
            BiometricNotificationManager.INSTANCE.dismissAll()
        }
    }

    override fun cancelAuth() {
        callback?.onCanceled()
    }

    override fun onUiOpened() {
        callback?.onUIOpened()
    }

    override fun onUiClosed() {
        callback?.onUIClosed()
    }

    private inner class BiometricAuthenticationCallbackImpl : BiometricAuthenticationListener {

        override fun onSuccess(module: BiometricType?) {
            if(confirmed.add(module))
                Vibro.start()
            val confirmedList: List<BiometricType?> = ArrayList(confirmed)
            val allList: MutableList<BiometricType?> = ArrayList(
                builder.allAvailableTypes
            )
            allList.removeAll(confirmedList)
            if (builder.biometricAuthRequest.confirmation == BiometricConfirmation.ANY ||
                builder.biometricAuthRequest.confirmation == BiometricConfirmation.ALL && allList.isEmpty()
            ) {
                ExecutorHelper.INSTANCE.handler.post {
                    cancelAuthenticate()
                    callback?.onSucceeded()
                }
            }
        }

        override fun onHelp(helpReason: AuthenticationHelpReason?, msg: CharSequence?) {
            if (helpReason !== AuthenticationHelpReason.BIOMETRIC_ACQUIRED_GOOD && !TextUtils.isEmpty(
                    msg
                )
            ) {
                if (dialog != null) dialog?.onHelp(msg)
            }
        }

        override fun onFailure(
            failureReason: AuthenticationFailureReason?,
            module: BiometricType?
        ) {
            if (dialog != null) {
                dialog?.onFailure(failureReason == AuthenticationFailureReason.LOCKED_OUT, module)
            }
            if (failureReason !== AuthenticationFailureReason.LOCKED_OUT) {
                //non fatal
                when (failureReason) {
                    AuthenticationFailureReason.SENSOR_FAILED, AuthenticationFailureReason.AUTHENTICATION_FAILED -> return
                }
                ExecutorHelper.INSTANCE.handler.post {
                    cancelAuthenticate()
                    callback?.onFailed(failureReason)
                }
            } else {
                getInstance(builder.biometricAuthRequest).lockout()
                ExecutorHelper.INSTANCE.handler.postDelayed({
                    cancelAuthenticate()
                    callback?.onFailed(failureReason)
                }, 2000)
            }
        }
    }
}