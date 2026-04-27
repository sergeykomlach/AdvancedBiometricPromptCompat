package dev.skomlach.biometric.compat.engine.internal.fingerprint.zk

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Base64
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.zkteco.android.biometric.FingerprintExceptionListener
import com.zkteco.android.biometric.core.device.ParameterHelper
import com.zkteco.android.biometric.core.device.TransportType
import com.zkteco.android.biometric.module.fingerprintreader.FingerprintCaptureListener
import com.zkteco.android.biometric.module.fingerprintreader.FingerprintSensor
import com.zkteco.android.biometric.module.fingerprintreader.FingprintFactory
import com.zkteco.android.biometric.module.fingerprintreader.ZKFingerService
import com.zkteco.android.biometric.module.fingerprintreader.exception.FingerprintException
import dev.skomlach.biometric.compat.BiometricType
import dev.skomlach.biometric.compat.custom.AbstractSoftwareBiometricManager
import dev.skomlach.biometric.zkfinger.R
import dev.skomlach.common.logging.LogCat
import dev.skomlach.common.storage.SharedPreferenceProvider.getProtectedPreferences
import dev.skomlach.common.translate.LocalizationHelper
import java.nio.charset.StandardCharsets.UTF_8
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class ZkFingerUnlockManager(
    private val context: Context
) : AbstractSoftwareBiometricManager() {

    companion object {
        const val IS_ENROLLMENT_KEY = "is_enrollment"
        const val ENROLLMENT_TAG_KEY = "enrollment_tag"

        private const val TAG = "ZkFingerUnlockManager"
        private const val STORAGE_NAME = "zkfinger_templates"
        private const val TEMPLATE_PREFIX = "template_"
        private const val TEMPLATE_SIZE = 2048
        private const val IDENTIFY_BUFFER_SIZE = 256
        private const val KEY_FAILED_ATTEMPTS = "failed_attempts"
        private const val KEY_LOCKOUT_END_TIMESTAMP = "lockout_end_timestamp"
        private const val KEY_PERMANENT_LOCKOUT_COUNT = "permanent_lockout_count"

        @Volatile
        private var config: ZkFingerConfig = ZkFingerConfig()

        private val activeSessionLock = Any()

        @Volatile
        private var currentActiveManager: ZkFingerUnlockManager? = null

        fun setZkFingerConfig(zkFingerConfig: ZkFingerConfig) {
            require(zkFingerConfig.enrollmentScanCount >= 1) {
                "enrollmentScanCount should be at least 1"
            }
            require(zkFingerConfig.productIds.isNotEmpty()) {
                "productIds should not be empty"
            }
            config = zkFingerConfig
        }

        private fun requestActiveSession(newManager: ZkFingerUnlockManager) {
            synchronized(activeSessionLock) {
                val previous = currentActiveManager
                if (previous != null && previous != newManager) {
                    previous.cancelInternal()
                }
                currentActiveManager = newManager
            }
        }

        private fun releaseSession(manager: ZkFingerUnlockManager) {
            synchronized(activeSessionLock) {
                if (currentActiveManager == manager) {
                    currentActiveManager = null
                }
            }
        }
    }

    private val effectiveConfig: ZkFingerConfig
        get() = config

    private val prefs by lazy {
        getProtectedPreferences(STORAGE_NAME)
    }

    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null
    private var callbackHandler: Handler = Handler(Looper.getMainLooper())
    private var authCallback: AuthenticationCallback? = null
    private var cancellationSignal: CancellationSignal? = null
    private var fingerprintSensor: FingerprintSensor? = null
    private var usbReceiver: BroadcastReceiver? = null
    private var isEnrolling = false
    private var enrollmentTag = ""
    private var enrollmentSamples = mutableListOf<ByteArray>()
    private val isSessionActive = AtomicBoolean(false)
    private val isOpening = AtomicBoolean(false)

    override fun getTimeoutMessage(): CharSequence {
        return localized(R.string.biometriccompat_zkfinger_help_timeout)
    }

    override fun resetLockOut() {
        prefs.edit {
            remove(KEY_LOCKOUT_END_TIMESTAMP)
            remove(KEY_FAILED_ATTEMPTS)
        }
    }

    override fun resetPermanentLockOut() {
        prefs.edit {
            remove(KEY_FAILED_ATTEMPTS)
            remove(KEY_LOCKOUT_END_TIMESTAMP)
            remove(KEY_PERMANENT_LOCKOUT_COUNT)
        }
    }

    override fun getPermissions(): List<String> = emptyList()

    override val biometricType: BiometricType = BiometricType.BIOMETRIC_FINGERPRINT

    override fun isHardwareDetected(): Boolean = findSupportedDevice() != null

    override fun hasEnrolledBiometric(): Boolean = getEnrolls().isNotEmpty()

    override fun getManagers(): Set<Any> = setOfNotNull(fingerprintSensor)

    override fun remove(extra: Bundle?) {
        val tag = extra?.getString(ENROLLMENT_TAG_KEY)
        if (tag.isNullOrBlank()) {
            getEnrolls().forEach { removeTemplate(it) }
            runCatching { ZKFingerService.clear() }
        } else {
            removeTemplate(tag)
            runCatching { ZKFingerService.del(tag) }
        }
    }

    override fun getEnrollBundle(name: String?): Bundle {
        return Bundle().apply {
            putBoolean(IS_ENROLLMENT_KEY, true)
            putString(ENROLLMENT_TAG_KEY, sanitizeTag(name) ?: nextEnrollmentTag())
        }
    }

    override fun getEnrolls(): Collection<String> {
        return prefs.all.keys
            .filterIsInstance<String>()
            .filter { it.startsWith(TEMPLATE_PREFIX) }
            .map { it.removePrefix(TEMPLATE_PREFIX) }
            .sorted()
    }

    override fun authenticate(
        crypto: CryptoObject?,
        flags: Int,
        cancel: CancellationSignal?,
        callback: AuthenticationCallback?,
        handler: Handler?,
        extra: Bundle?
    ) {
        requestActiveSession(this)
        val lockoutError = checkLockoutState()
        if (lockoutError != null) {
            onAuthenticationError(lockoutError, lockoutMessage(lockoutError))
            releaseSession(this)
            return
        }

        isEnrolling = extra?.getBoolean(IS_ENROLLMENT_KEY, false) ?: false
        enrollmentTag = sanitizeTag(extra?.getString(ENROLLMENT_TAG_KEY)) ?: nextEnrollmentTag()
        enrollmentSamples.clear()

        if (!isEnrolling && !hasEnrolledBiometric()) {
            onAuthenticationError(
                CUSTOM_BIOMETRIC_ERROR_NO_BIOMETRIC,
                localized(R.string.biometriccompat_zkfinger_help_not_registered)
            )
            releaseSession(this)
            return
        }

        if (!isHardwareDetected()) {
            onAuthenticationError(
                CUSTOM_BIOMETRIC_ERROR_HW_NOT_PRESENT,
                localized(R.string.biometriccompat_zkfinger_help_sensor_not_found)
            )
            releaseSession(this)
            return
        }

        callbackHandler = handler ?: Handler(Looper.getMainLooper())
        authCallback = callback
        cancellationSignal = cancel
        isSessionActive.set(true)
        cancellationSignal?.setOnCancelListener {
            if (isSessionActive.get()) {
                val callback = authCallback
                callbackHandler.post {
                    callback?.onAuthenticationCancelled()
                }
                stopAuthentication()
            }
        }

        startBackgroundThread()
        backgroundHandler?.post {
            openWhenUsbPermissionReady()
        }
    }

    private fun openWhenUsbPermissionReady() {
        if (!isSessionActive.get() || !isOpening.compareAndSet(false, true)) return
        try {
            val device = findSupportedDevice()
            if (device == null) {
                onAuthenticationError(
                    CUSTOM_BIOMETRIC_ERROR_HW_NOT_PRESENT,
                    localized(R.string.biometriccompat_zkfinger_help_sensor_not_found)
                )
                stopAuthentication()
                return
            }

            val usbManager = usbManager()
            if (usbManager?.hasPermission(device) == true) {
                openDevice(device)
                return
            }

            registerUsbReceiver(device)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        PendingIntent.FLAG_MUTABLE
                    } else {
                        0
                    }
            val permissionIntent = PendingIntent.getBroadcast(
                context,
                0,
                Intent(permissionAction()).setPackage(context.packageName),
                flags
            )
            usbManager?.requestPermission(device, permissionIntent)
        } catch (e: Throwable) {
            LogCat.logException(e)
            onAuthenticationError(
                CUSTOM_BIOMETRIC_ERROR_HW_UNAVAILABLE,
                localized(R.string.biometriccompat_zkfinger_help_sensor_unavailable)
            )
            stopAuthentication()
        } finally {
            isOpening.set(false)
        }
    }

    private fun registerUsbReceiver(targetDevice: UsbDevice) {
        unregisterUsbReceiver()
        val filter = IntentFilter().apply {
            addAction(permissionAction())
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        usbReceiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) {
                val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                }
                if (device?.vendorId != targetDevice.vendorId ||
                    device.productId != targetDevice.productId
                ) {
                    return
                }
                when (intent.action) {
                    permissionAction() -> {
                        if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                            backgroundHandler?.post { openDevice(device) }
                        } else {
                            onAuthenticationError(
                                CUSTOM_BIOMETRIC_ERROR_NO_PERMISSIONS,
                                localized(R.string.biometriccompat_zkfinger_help_usb_permission_denied)
                            )
                            stopAuthentication()
                        }
                    }

                    UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                        onAuthenticationError(
                            CUSTOM_BIOMETRIC_ERROR_HW_UNAVAILABLE,
                            localized(R.string.biometriccompat_zkfinger_help_sensor_unavailable)
                        )
                        stopAuthentication()
                    }
                }
            }
        }
        ContextCompat.registerReceiver(
            context,
            usbReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun openDevice(device: UsbDevice) {
        if (!isSessionActive.get()) return
        try {
            ensureTemplateEngine()
            loadTemplatesIntoEngine()
            val params = HashMap<String, Any>().apply {
                put(ParameterHelper.PARAM_KEY_VID, device.vendorId)
                put(ParameterHelper.PARAM_KEY_PID, device.productId)
            }
            val sensor = FingprintFactory.createFingerprintSensor(
                context,
                TransportType.USB,
                params
            )
            fingerprintSensor = sensor
            sensor.setFingerprintCaptureListener(effectiveConfig.deviceIndex, captureListener)
            sensor.SetFingerprintExceptionListener(exceptionListener)
            sensor.open(effectiveConfig.deviceIndex)
            sensor.startCapture(effectiveConfig.deviceIndex)
            postHelp(localized(R.string.biometriccompat_zkfinger_help_scan_again))
        } catch (e: Throwable) {
            LogCat.logException(e)
            onAuthenticationError(
                CUSTOM_BIOMETRIC_ERROR_HW_UNAVAILABLE,
                localized(R.string.biometriccompat_zkfinger_help_sensor_unavailable)
            )
            stopAuthentication()
        }
    }

    private val captureListener = object : FingerprintCaptureListener {
        override fun captureOK(image: ByteArray?) = Unit

        override fun captureError(e: FingerprintException?) {
            LogCat.logException(e ?: return)
            postHelp(localized(R.string.biometriccompat_zkfinger_help_scan_again))
        }

        override fun extractOK(template: ByteArray?) {
            if (!isSessionActive.get() || template == null) return
            backgroundHandler?.post {
                processTemplate(template.copyOf())
            }
        }

        override fun extractError(errorCode: Int) {
            LogCat.logError(TAG, "extractError=$errorCode")
            postHelp(localized(R.string.biometriccompat_zkfinger_help_template_error))
        }
    }

    private val exceptionListener = FingerprintExceptionListener {
        if (isSessionActive.get()) {
            onAuthenticationError(
                CUSTOM_BIOMETRIC_ERROR_HW_UNAVAILABLE,
                localized(R.string.biometriccompat_zkfinger_help_sensor_unavailable)
            )
            stopAuthentication()
        }
    }

    private fun processTemplate(template: ByteArray) {
        if (!isSessionActive.get()) return
        try {
            if (isEnrolling) {
                processEnrollmentTemplate(template)
            } else {
                processAuthenticationTemplate(template)
            }
        } catch (e: Throwable) {
            LogCat.logException(e)
            onAuthenticationError(
                CUSTOM_BIOMETRIC_ERROR_UNABLE_TO_PROCESS,
                localized(R.string.biometriccompat_zkfinger_help_template_error)
            )
            stopAuthentication()
        }
    }

    private fun processEnrollmentTemplate(template: ByteArray) {
        val duplicate = identify(template)
        if (duplicate != null && duplicate.first != enrollmentTag) {
            onAuthenticationError(
                CUSTOM_BIOMETRIC_ERROR_UNABLE_TO_PROCESS,
                localized(R.string.biometriccompat_zkfinger_help_already_registered)
            )
            stopAuthentication()
            return
        }

        val previous = enrollmentSamples.lastOrNull()
        if (previous != null && ZKFingerService.verify(previous, template) <= 0) {
            enrollmentSamples.clear()
            onAuthenticationError(
                CUSTOM_BIOMETRIC_ERROR_UNABLE_TO_PROCESS,
                localized(R.string.biometriccompat_zkfinger_help_same_finger_required)
            )
            stopAuthentication()
            return
        }

        enrollmentSamples += template.copyOf(TEMPLATE_SIZE)
        if (enrollmentSamples.size < effectiveConfig.enrollmentScanCount) {
            postHelp(localized(R.string.biometriccompat_zkfinger_help_enroll_progress))
            return
        }

        val merged = ByteArray(TEMPLATE_SIZE)
        val ret = if (enrollmentSamples.size >= 3) {
            ZKFingerService.merge(enrollmentSamples[0], enrollmentSamples[1], enrollmentSamples[2], merged)
        } else {
            System.arraycopy(enrollmentSamples.first(), 0, merged, 0, TEMPLATE_SIZE)
            TEMPLATE_SIZE
        }
        if (ret <= 0) {
            onAuthenticationError(
                CUSTOM_BIOMETRIC_ERROR_UNABLE_TO_PROCESS,
                localized(R.string.biometriccompat_zkfinger_help_template_error)
            )
            stopAuthentication()
            return
        }

        val saveRet = ZKFingerService.save(merged, enrollmentTag)
        if (saveRet != 0) {
            onAuthenticationError(
                CUSTOM_BIOMETRIC_ERROR_UNABLE_TO_PROCESS,
                localized(R.string.biometriccompat_zkfinger_help_template_error)
            )
            stopAuthentication()
            return
        }

        saveTemplate(enrollmentTag, merged)
        resetPermanentLockOut()
        onAuthenticationSucceeded()
        stopAuthentication()
    }

    private fun processAuthenticationTemplate(template: ByteArray) {
        val match = identify(template)
        if (match != null) {
            resetPermanentLockOut()
            onAuthenticationSucceeded()
            stopAuthentication()
            return
        }

        handleFailedAttempt()
        val lockoutError = checkLockoutState()
        if (lockoutError != null) {
            onAuthenticationError(lockoutError, lockoutMessage(lockoutError))
            stopAuthentication()
        } else {
            onAuthenticationError(
                CUSTOM_BIOMETRIC_ERROR_UNABLE_TO_PROCESS,
                localized(R.string.biometriccompat_zkfinger_help_scan_again)
            )
        }
    }

    private fun identify(template: ByteArray): Pair<String, Int>? {
        val buffer = ByteArray(IDENTIFY_BUFFER_SIZE)
        val score = ZKFingerService.identify(template, buffer, effectiveConfig.matchThreshold, 1)
        if (score <= 0) return null
        val payload = String(buffer, UTF_8).substringBefore('\u0000').trim()
        val parts = payload.split('\t')
        val id = parts.firstOrNull()?.trim().orEmpty()
        val parsedScore = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: score
        return if (id.isNotEmpty()) id to parsedScore else null
    }

    private fun ensureTemplateEngine() {
        runCatching { ZKFingerService.init() }
            .onFailure { LogCat.logException(it) }
    }

    private fun loadTemplatesIntoEngine() {
        runCatching { ZKFingerService.clear() }
        prefs.all.forEach { (key, value) ->
            val id = key.removePrefix(TEMPLATE_PREFIX)
            if (id == key) return@forEach
            val encoded = value as? String ?: return@forEach
            val template = runCatching {
                Base64.decode(encoded, Base64.NO_WRAP)
            }.getOrNull() ?: return@forEach
            if (template.isNotEmpty()) {
                val ret = ZKFingerService.save(template, id)
                if (ret != 0) {
                    LogCat.log(TAG, "Failed to load ZK template for $id: $ret")
                }
            }
        }
    }

    private fun saveTemplate(id: String, template: ByteArray) {
        prefs.edit {
            putString(
                TEMPLATE_PREFIX + id,
                Base64.encodeToString(template, Base64.NO_WRAP)
            )
        }
    }

    private fun removeTemplate(id: String) {
        prefs.edit {
            remove(TEMPLATE_PREFIX + id)
        }
    }

    private fun checkLockoutState(): Int? {
        val permanentLockoutCount = prefs.getInt(KEY_PERMANENT_LOCKOUT_COUNT, 0)
        if (permanentLockoutCount >= effectiveConfig.maxTemporaryLockoutsBeforePermanent) {
            return CUSTOM_BIOMETRIC_ERROR_LOCKOUT_PERMANENT
        }

        val lockoutEndTime = prefs.getLong(KEY_LOCKOUT_END_TIMESTAMP, 0)
        val currentTime = System.currentTimeMillis()
        if (lockoutEndTime > currentTime) {
            return CUSTOM_BIOMETRIC_ERROR_LOCKOUT
        } else if (lockoutEndTime > 0) {
            prefs.edit {
                remove(KEY_LOCKOUT_END_TIMESTAMP)
                remove(KEY_FAILED_ATTEMPTS)
            }
        }
        return null
    }

    private fun handleFailedAttempt() {
        var failedAttempts = prefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
        var permanentLockoutCount = prefs.getInt(KEY_PERMANENT_LOCKOUT_COUNT, 0)

        prefs.edit {
            if (failedAttempts >= effectiveConfig.maxFailedAttemptsBeforeLockout) {
                permanentLockoutCount++
                failedAttempts = 0
                if (permanentLockoutCount < effectiveConfig.maxTemporaryLockoutsBeforePermanent) {
                    putLong(
                        KEY_LOCKOUT_END_TIMESTAMP,
                        System.currentTimeMillis() + effectiveConfig.lockoutDurationMs
                    )
                }
                putInt(KEY_PERMANENT_LOCKOUT_COUNT, permanentLockoutCount)
            }
            putInt(KEY_FAILED_ATTEMPTS, failedAttempts)
        }
    }

    private fun lockoutMessage(error: Int): String {
        return if (error == CUSTOM_BIOMETRIC_ERROR_LOCKOUT_PERMANENT) {
            localized(R.string.biometriccompat_zkfinger_help_too_many_attempts_permanent)
        } else {
            localized(R.string.biometriccompat_zkfinger_help_too_many_attempts_try_later)
        }
    }

    private fun startBackgroundThread() {
        if (backgroundThread == null) {
            backgroundThread = HandlerThread("ZkFingerBackground").apply {
                start()
                backgroundHandler = Handler(looper)
            }
        }
    }

    private fun stopBackgroundThread() {
        backgroundHandler = null
        backgroundThread?.quitSafely()
        backgroundThread = null
    }

    private fun cancelInternal() {
        if (isSessionActive.get()) {
            onAuthenticationError(
                CUSTOM_BIOMETRIC_ERROR_CANCELED,
                localized(R.string.biometriccompat_zkfinger_help_canceled_by_new_operation)
            )
        }
        stopAuthentication()
    }

    private fun stopAuthentication() {
        if (!isSessionActive.compareAndSet(true, false)) return
        unregisterUsbReceiver()
        try {
            fingerprintSensor?.stopCapture(effectiveConfig.deviceIndex)
        } catch (e: Throwable) {
            LogCat.logException(e)
        }
        try {
            fingerprintSensor?.close(effectiveConfig.deviceIndex)
        } catch (e: Throwable) {
            LogCat.logException(e)
        }
        try {
            fingerprintSensor?.destroy()
        } catch (e: Throwable) {
            LogCat.logException(e)
        }
        runCatching { ZKFingerService.free() }
        fingerprintSensor = null
        authCallback = null
        cancellationSignal = null
        enrollmentSamples.clear()
        isEnrolling = false
        releaseSession(this)
        stopBackgroundThread()
    }

    private fun findSupportedDevice(): UsbDevice? {
        val usbManager = usbManager() ?: return null
        val currentConfig = effectiveConfig
        return usbManager.deviceList.values.firstOrNull { device ->
            device.vendorId == currentConfig.vendorId &&
                    currentConfig.productIds.contains(device.productId)
        }
    }

    private fun usbManager(): UsbManager? {
        return context.getSystemService(Context.USB_SERVICE) as? UsbManager
    }

    private fun permissionAction(): String {
        return "${context.packageName}.dev.skomlach.biometric.zkfinger.USB_PERMISSION"
    }

    private fun unregisterUsbReceiver() {
        val receiver = usbReceiver ?: return
        runCatching { context.unregisterReceiver(receiver) }
        usbReceiver = null
    }

    private fun sanitizeTag(tag: String?): String? {
        val sanitized = tag
            ?.trim()
            ?.replace(Regex("[^A-Za-z0-9_.-]"), "_")
            ?.take(64)
        return sanitized?.takeIf { it.isNotBlank() }
    }

    private fun nextEnrollmentTag(): String {
        val existing = getEnrolls().toSet()
        for (i in 1..999) {
            val candidate = "zkfinger$i"
            if (!existing.contains(candidate)) return candidate
        }
        return "zkfinger_${UUID.randomUUID()}"
    }

    private fun onAuthenticationError(code: Int, msg: CharSequence?) {
        val callback = authCallback
        callbackHandler.post {
            callback?.onAuthenticationError(code, msg)
        }
    }

    private fun postHelp(msg: CharSequence?) {
        val callback = authCallback
        callbackHandler.post {
            callback?.onAuthenticationHelp(CUSTOM_BIOMETRIC_ACQUIRED_PARTIAL, msg)
        }
    }

    private fun onAuthenticationSucceeded() {
        val callback = authCallback
        callbackHandler.post {
            callback?.onAuthenticationSucceeded(AuthenticationResult(null))
        }
    }

    private fun localized(id: Int): String {
        return LocalizationHelper.getLocalizedString(context, id)
    }
}
