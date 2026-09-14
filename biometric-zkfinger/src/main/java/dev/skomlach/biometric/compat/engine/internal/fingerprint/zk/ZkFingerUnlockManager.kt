package dev.skomlach.biometric.compat.engine.internal.fingerprint.zk

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
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
import dev.skomlach.common.storage.editProtected
import dev.skomlach.common.storage.ProtectedStorageUnavailableException
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
import dev.skomlach.biometric.compat.custom.SoftwareBiometricAssuranceLevel
import dev.skomlach.biometric.compat.custom.SoftwareBiometricSecurityProfile
import dev.skomlach.biometric.zkfinger.R
import dev.skomlach.common.logging.LogCat
import dev.skomlach.common.storage.SharedPreferenceProvider.getProtectedPreferences
import dev.skomlach.common.translate.LocalizationHelper
import java.lang.ref.WeakReference
import java.nio.charset.StandardCharsets.UTF_8
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class ZkFingerUnlockManager(
    private val context: Context
) : AbstractSoftwareBiometricManager() {

    override val priority: Int = PRIORITY_ABOVE_SYSTEM_HARDWARE
    override val securityProfile: SoftwareBiometricSecurityProfile =
        SoftwareBiometricSecurityProfile(
            biometricType = BiometricType.BIOMETRIC_FINGERPRINT,
            assurance = SoftwareBiometricAssuranceLevel.VENDOR_BACKED,
            requiresTrustedCapture = true,
            allowsCompatibilityCapture = false,
            supportsCryptoObject = false,
            maxCaptureDurationMs = 30_000L
        )
    override val trustedCaptureForAuthentication: Boolean = true

    companion object {
        const val IS_ENROLLMENT_KEY = "is_enrollment"
        const val ENROLLMENT_TAG_KEY = "enrollment_tag"

        private const val TAG = "ZkFingerUnlockManager"
        private const val STORAGE_NAME = "zkfinger_templates"
        private const val TEMPLATE_PREFIX = "template_"
        private const val TEMPLATE_SIZE = 2048
        private const val IDENTIFY_BUFFER_SIZE = 256
        private const val USB_PERMISSION_POLL_INTERVAL_MS = 250L
        private const val USB_PERMISSION_TIMEOUT_MS = 30_000L

        @Volatile
        private var config: ZkFingerConfig = ZkFingerConfig()

        // ZKFingerService owns process-wide native state. All managers must open,
        // process and free it on the same worker, including cancellation cleanup.
        private val nativeHandler by lazy {
            Handler(HandlerThread("ZkFingerBackground").apply { start() }.looper)
        }

        private val activeSessionLock = Any()

        @Volatile
        private var currentActiveManager: WeakReference<ZkFingerUnlockManager>? = null

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
                val previous = currentActiveManager?.get()
                if (previous != null) {
                    previous.cancelInternal()
                }
                currentActiveManager = WeakReference(newManager)
            }
        }

        private fun releaseSession(manager: ZkFingerUnlockManager) {
            synchronized(activeSessionLock) {
                if (currentActiveManager?.get() == manager) {
                    currentActiveManager = null
                }
            }
        }
    }

    private val effectiveConfig: ZkFingerConfig
        get() = sessionConfig ?: config

    private val prefs by lazy {
        getProtectedPreferences(STORAGE_NAME)
    }

    @Volatile
    private var sessionConfig: ZkFingerConfig? = null
    private var captureSession = newCaptureSession()
    private var pendingUsbPermission: AtomicBoolean? = null

    private fun newCaptureSession() = ZkFingerCaptureSession { action ->
        nativeHandler.post { action() }
    }
    private var callbackHandler: Handler = Handler(Looper.getMainLooper())
    private var authCallback: AuthenticationCallback? = null
    private var cancellationSignal: CancellationSignal? = null
    @Volatile
    private var fingerprintSensor: FingerprintSensor? = null
    private var usbReceiver: BroadcastReceiver? = null
    private var isEnrolling = false
    private var enrollmentTag = ""
    private var enrollmentSamples = mutableListOf<ByteArray>()
    private val isSessionActive = AtomicBoolean(false)
    private val isOpening = AtomicBoolean(false)

    private val lockoutPolicy: LockoutPolicy
        get() = LockoutPolicy(
            maxFailedAttemptsBeforeLockout = effectiveConfig.maxFailedAttemptsBeforeLockout,
            maxTemporaryLockoutsBeforePermanent = effectiveConfig.maxTemporaryLockoutsBeforePermanent,
            lockoutDurationMs = effectiveConfig.lockoutDurationMs
        )

    override fun getTimeoutMessage(): CharSequence {
        return localized(R.string.biometriccompat_zkfinger_help_timeout)
    }

    override fun resetLockOut() {
        resetTemporaryLockoutState(prefs)
    }

    override fun resetPermanentLockOut() {
        resetPermanentLockoutState(prefs)
    }

    override fun getPermissions(): List<String> = emptyList()

    override fun prepareForAuthentication(
        callback: PreparationCallback
    ) {
        val mainHandler = Handler(Looper.getMainLooper())
        val delivery = object : PreparationCallback() {
            override fun onPrepared() {
                mainHandler.post { callback.onPrepared() }
            }

            override fun onPreparationError(errMsgId: Int, errString: CharSequence?) {
                mainHandler.post { callback.onPreparationError(errMsgId, errString) }
            }
        }
        nativeHandler.post { prepareOnWorker(delivery) }
    }

    private fun prepareOnWorker(callback: PreparationCallback) {
        clearPendingUsbPermission()
        try {
            val device = findSupportedDevice()
            if (device == null) {
                callback.onPreparationError(
                    CUSTOM_BIOMETRIC_ERROR_HW_NOT_PRESENT,
                    localized(R.string.biometriccompat_zkfinger_help_sensor_not_found)
                )
                return
            }

            if (hasUsbPermission(device)) {
                callback.onPrepared()
                return
            }

            requestUsbPermission(
                device,
                onGranted = {
                    callback.onPrepared()
                },
                onDenied = {
                    callback.onPreparationError(
                        CUSTOM_BIOMETRIC_ERROR_NO_PERMISSIONS,
                        localized(R.string.biometriccompat_zkfinger_help_usb_permission_denied)
                    )
                },
                onDetached = {
                    callback.onPreparationError(
                        CUSTOM_BIOMETRIC_ERROR_HW_UNAVAILABLE,
                        localized(R.string.biometriccompat_zkfinger_help_sensor_unavailable)
                    )
                }
            )
        } catch (e: Throwable) {
            LogCat.logException(e)
            callback.onPreparationError(
                CUSTOM_BIOMETRIC_ERROR_HW_UNAVAILABLE,
                localized(R.string.biometriccompat_zkfinger_help_sensor_unavailable)
            )
        }
    }

    override val biometricType: BiometricType = BiometricType.BIOMETRIC_FINGERPRINT

    override fun isHardwareDetected(): Boolean {
        return resolveZkHardwareDetected(
            usbHostAvailable = isUsbHostAvailable(),
            supportedDeviceConnected = findSupportedDevice() != null
        )
    }

    override fun hasEnrolledBiometric(): Boolean = getEnrolls().isNotEmpty()

    override fun getManagers(): Set<Any> = setOfNotNull(fingerprintSensor)

    override fun remove(extra: Bundle?) {
        val tag = extra?.getString(ENROLLMENT_TAG_KEY)
        if (tag.isNullOrBlank()) {
            getEnrolls().forEach { removeTemplate(it) }
            nativeHandler.post { runCatching { ZKFingerService.clear() } }
        } else {
            removeTemplate(tag)
            nativeHandler.post { runCatching { ZKFingerService.del(tag) } }
        }
    }

    override fun getEnrollBundle(name: String?): Bundle {
        return Bundle().apply {
            putBoolean(IS_ENROLLMENT_KEY, true)
            putString(ENROLLMENT_TAG_KEY, sanitizeZkEnrollmentTag(name) ?: nextEnrollmentTag())
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
        val extras = extra?.let(::Bundle)
        nativeHandler.post {
            try {
                authenticateOnWorker(cancel, callback, handler, extras)
            } catch (error: ProtectedStorageUnavailableException) {
                LogCat.logException(error)
                onAuthenticationError(CUSTOM_BIOMETRIC_ERROR_HW_UNAVAILABLE,
                    localized(R.string.biometriccompat_zkfinger_help_sensor_unavailable))
                stopAuthentication()
                authCallback = null
                cancellationSignal = null
                sessionConfig = null
                releaseSession(this)
            }
        }
    }

    private fun authenticateOnWorker(
        cancel: CancellationSignal?,
        callback: AuthenticationCallback?,
        handler: Handler?,
        extra: Bundle?
    ) {
        requestActiveSession(this)
        clearPendingUsbPermission()
        captureSession.invalidate()
        captureSession = newCaptureSession()
        sessionConfig = config
        callbackHandler = handler ?: Handler(Looper.getMainLooper())
        authCallback = callback
        cancellationSignal = cancel
        val lockoutError = checkLockoutState()
        if (lockoutError != null) {
            onAuthenticationError(lockoutError, lockoutMessage(lockoutError))
            authCallback = null
            cancellationSignal = null
            sessionConfig = null
            releaseSession(this)
            return
        }

        isEnrolling = extra?.getBoolean(IS_ENROLLMENT_KEY, false) ?: false
        enrollmentTag = sanitizeZkEnrollmentTag(extra?.getString(ENROLLMENT_TAG_KEY)) ?: nextEnrollmentTag()
        enrollmentSamples.clear()

        if (!isEnrolling && !hasEnrolledBiometric()) {
            onAuthenticationError(
                CUSTOM_BIOMETRIC_ERROR_NO_BIOMETRIC,
                localized(R.string.biometriccompat_zkfinger_help_not_registered)
            )
            authCallback = null
            cancellationSignal = null
            sessionConfig = null
            releaseSession(this)
            return
        }

        if (!isHardwareDetected()) {
            onAuthenticationError(
                CUSTOM_BIOMETRIC_ERROR_HW_NOT_PRESENT,
                localized(R.string.biometriccompat_zkfinger_help_sensor_not_found)
            )
            authCallback = null
            cancellationSignal = null
            sessionConfig = null
            releaseSession(this)
            return
        }

        isSessionActive.set(true)
        val session = captureSession
        val resultHandler = callbackHandler
        cancellationSignal?.setOnCancelListener {
            if (session.invalidate()) {
                resultHandler.post { callback?.onAuthenticationCancelled() }
            }
            nativeHandler.post {
                if (captureSession === session && isSessionActive.get()) {
                    stopAuthentication()
                }
            }
        }

        session.post { openWhenUsbPermissionReady() }
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

            if (hasUsbPermission(device)) {
                openDevice(device)
                return
            }

            requestUsbPermission(
                device,
                onGranted = { grantedDevice ->
                    captureSession.post { openDevice(grantedDevice) }
                },
                onDenied = {
                    onAuthenticationError(
                        CUSTOM_BIOMETRIC_ERROR_NO_PERMISSIONS,
                        localized(R.string.biometriccompat_zkfinger_help_usb_permission_denied)
                    )
                    stopAuthentication()
                },
                onDetached = {
                    onAuthenticationError(
                        CUSTOM_BIOMETRIC_ERROR_HW_UNAVAILABLE,
                        localized(R.string.biometriccompat_zkfinger_help_sensor_unavailable)
                    )
                    stopAuthentication()
                }
            )
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

    private fun requestUsbPermission(
        targetDevice: UsbDevice,
        onGranted: (UsbDevice) -> Unit,
        onDenied: () -> Unit,
        onDetached: () -> Unit
    ) {
        val manager = usbManager() ?: run {
            onDetached()
            return
        }
        pendingUsbPermission?.set(true)
        val completed = AtomicBoolean(false).also { pendingUsbPermission = it }
        val completeGranted = { device: UsbDevice ->
            if (completed.compareAndSet(false, true)) {
                unregisterUsbReceiver()
                onGranted(device)
            }
        }
        val completeDenied = {
            if (completed.compareAndSet(false, true)) {
                unregisterUsbReceiver()
                onDenied()
            }
        }
        val completeDetached = {
            if (completed.compareAndSet(false, true)) {
                unregisterUsbReceiver()
                onDetached()
            }
        }

        registerUsbReceiver(targetDevice, completeGranted, completeDenied, completeDetached)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // UsbManager adds EXTRA_DEVICE and EXTRA_PERMISSION_GRANTED to the
                    // permission result. This PendingIntent must stay mutable for that
                    // platform flow; the receiver still validates action and VID/PID.
                    PendingIntent.FLAG_MUTABLE
                } else {
                    0
                }
        val permissionIntent = PendingIntent.getBroadcast(
            context,
            resolveZkPermissionRequestCode(
                vendorId = targetDevice.vendorId,
                productId = targetDevice.productId,
                deviceIndex = effectiveConfig.deviceIndex
            ),
            Intent(resolveZkPermissionAction(context.packageName)).setPackage(context.packageName),
            flags
        )
        manager.requestPermission(targetDevice, permissionIntent)
        pollUsbPermissionResult(
            targetDevice,
            System.currentTimeMillis() + USB_PERMISSION_TIMEOUT_MS,
            completed,
            completeGranted,
            completeDenied,
            completeDetached
        )
    }

    private fun registerUsbReceiver(
        targetDevice: UsbDevice,
        onGranted: (UsbDevice) -> Unit,
        onDenied: () -> Unit,
        onDetached: () -> Unit
    ) {
        unregisterUsbReceiver()
        val filter = IntentFilter().apply {
            addAction(resolveZkPermissionAction(context.packageName))
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
                val action = intent.action
                val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                nativeHandler.post {
                    if (usbReceiver !== this) return@post
                    when (action) {
                        resolveZkPermissionAction(context.packageName) -> {
                            if (granted) {
                                onGranted(device)
                            } else {
                                onDenied()
                            }
                        }

                        UsbManager.ACTION_USB_DEVICE_DETACHED -> onDetached()
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

    private fun pollUsbPermissionResult(
        targetDevice: UsbDevice,
        deadline: Long,
        completed: AtomicBoolean,
        onGranted: (UsbDevice) -> Unit,
        onDenied: () -> Unit,
        onDetached: () -> Unit
    ) {
        nativeHandler.postDelayed(
            {
                if (completed.get()) return@postDelayed
                val device = findSupportedDevice()
                if (device == null ||
                    device.vendorId != targetDevice.vendorId ||
                    device.productId != targetDevice.productId
                ) {
                    onDetached()
                    return@postDelayed
                }
                if (hasUsbPermission(device)) {
                    onGranted(device)
                    return@postDelayed
                }
                if (System.currentTimeMillis() >= deadline) {
                    onDenied()
                    return@postDelayed
                }
                pollUsbPermissionResult(
                    targetDevice,
                    deadline,
                    completed,
                    onGranted,
                    onDenied,
                    onDetached
                )
            },
            USB_PERMISSION_POLL_INTERVAL_MS
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
            sensor.setFingerprintCaptureListener(effectiveConfig.deviceIndex, captureListener(captureSession))
            sensor.SetFingerprintExceptionListener(exceptionListener(captureSession))
            sensor.open(effectiveConfig.deviceIndex)
            sensor.startCapture(effectiveConfig.deviceIndex)
            postHelp(initialScanMessage())
        } catch (e: Throwable) {
            LogCat.logException(e)
            onAuthenticationError(
                CUSTOM_BIOMETRIC_ERROR_HW_UNAVAILABLE,
                localized(R.string.biometriccompat_zkfinger_help_sensor_unavailable)
            )
            stopAuthentication()
        }
    }

    private fun captureListener(session: ZkFingerCaptureSession) = object : FingerprintCaptureListener {

        override fun captureOK(image: ByteArray?) = Unit

        override fun captureError(e: FingerprintException?) = Unit

        override fun extractOK(template: ByteArray?) {
            session.postTemplate(template, ::processTemplate)
        }

        override fun extractError(errorCode: Int) {
            session.post {
                LogCat.logError(TAG, "extractError=$errorCode")
                onAuthenticationFailed()
            }
        }
    }

    private fun exceptionListener(session: ZkFingerCaptureSession) = FingerprintExceptionListener {
        session.post {
            onAuthenticationError(
                CUSTOM_BIOMETRIC_ERROR_HW_UNAVAILABLE,
                localized(R.string.biometriccompat_zkfinger_help_sensor_unavailable)
            )
            stopAuthentication()
        }
    }

    private fun processTemplate(template: ByteArray) {
        if (!isSessionActive.get() || !captureSession.isActive) return
        try {
            if (isEnrolling) {
                processEnrollmentTemplate(template)
            } else {
                processAuthenticationTemplate(template)
            }
        } catch (error: ProtectedStorageUnavailableException) {
            LogCat.logException(error)
            onAuthenticationError(CUSTOM_BIOMETRIC_ERROR_HW_UNAVAILABLE,
                localized(R.string.biometriccompat_zkfinger_help_sensor_unavailable))
            stopAuthentication()
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
            postHelp(
                localized(
                    R.string.biometriccompat_zkfinger_help_enroll_progress,
                    enrollmentSamples.size,
                    effectiveConfig.enrollmentScanCount
                )
            )
            return
        }

        postHelp(localized(R.string.biometriccompat_zkfinger_help_enroll_finalizing))
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

        if (!captureSession.isActive) return
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
            onAuthenticationFailed()
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
        prefs.editProtected {
            putString(
                TEMPLATE_PREFIX + id,
                Base64.encodeToString(template, Base64.NO_WRAP)
            )
        }
    }

    private fun removeTemplate(id: String) {
        prefs.editProtected {
            remove(TEMPLATE_PREFIX + id)
        }
    }

    private fun checkLockoutState(): Int? {
        if (findSupportedDevice() == null) {
            return CUSTOM_BIOMETRIC_ERROR_LOCKOUT_PERMANENT
        }

        return getStoredLockoutError(prefs, lockoutPolicy)
    }

    override fun getLockoutError(): Int? = checkLockoutState()

    private fun handleFailedAttempt() {
        recordFailedAttempt(prefs, lockoutPolicy)
    }

    private fun lockoutMessage(error: Int): String {
        if (error == CUSTOM_BIOMETRIC_ERROR_HW_UNAVAILABLE) {
            return localized(R.string.biometriccompat_zkfinger_help_sensor_unavailable)
        }
        return localized(zkFingerLockoutOutcomeForError(error).messageResId)
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
        captureSession.invalidate()
        clearPendingUsbPermission()
        if (!isSessionActive.compareAndSet(true, false)) return
        val sensor = fingerprintSensor
        fingerprintSensor = null
        try {
            sensor?.stopCapture(effectiveConfig.deviceIndex)
        } catch (e: Throwable) {
            LogCat.logException(e)
        }
        try {
            sensor?.close(effectiveConfig.deviceIndex)
        } catch (e: Throwable) {
            LogCat.logException(e)
        }
        try {
            sensor?.let { FingprintFactory.destroy(it) }
        } catch (e: Throwable) {
            LogCat.logException(e)
        }
        runCatching { ZKFingerService.free() }
        authCallback = null
        cancellationSignal = null
        enrollmentSamples.clear()
        isEnrolling = false
        releaseSession(this)
        sessionConfig = null
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

    private fun isUsbHostAvailable(): Boolean {
        val currentConfig = effectiveConfig
        return currentConfig.vendorId > 0 &&
                currentConfig.productIds.isNotEmpty() &&
                context.packageManager.hasSystemFeature(PackageManager.FEATURE_USB_HOST) &&
                usbManager() != null
    }

    private fun hasUsbPermission(device: UsbDevice): Boolean {
        return usbManager()?.hasPermission(device) == true
    }

    private fun unregisterUsbReceiver() {
        val receiver = usbReceiver ?: return
        runCatching { context.unregisterReceiver(receiver) }
        usbReceiver = null
    }

    private fun clearPendingUsbPermission() {
        pendingUsbPermission?.set(true)
        pendingUsbPermission = null
        unregisterUsbReceiver()
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
        if (!captureSession.isActive) return
        val callback = authCallback
        callbackHandler.post {
            callback?.onAuthenticationError(code, msg)
        }
    }

    private fun postHelp(msg: CharSequence?) {
        if (!captureSession.isActive || msg.isNullOrBlank()) return
        val callback = authCallback
        callbackHandler.post {
            callback?.onAuthenticationHelp(CUSTOM_BIOMETRIC_ACQUIRED_PARTIAL, msg)
        }
    }

    private fun onAuthenticationSucceeded() {
        if (!captureSession.isActive) return
        val callback = authCallback
        val signal = cancellationSignal
        callbackHandler.post {
            if (signal?.isCanceled != true) {
                callback?.onAuthenticationSucceeded(AuthenticationResult(null))
            }
        }
    }

    private fun onAuthenticationFailed() {
        if (!captureSession.isActive) return
        val callback = authCallback
        callbackHandler.post {
            callback?.onAuthenticationFailed()
        }
    }

    private fun initialScanMessage(): CharSequence {
        return if (isEnrolling) {
            localized(
                R.string.biometriccompat_zkfinger_help_enroll_start,
                effectiveConfig.enrollmentScanCount
            )
        } else {
            localized(R.string.biometriccompat_zkfinger_help_scan_again)
        }
    }

    private fun localized(id: Int): String {
        return LocalizationHelper.getLocalizedString(context, id)
    }

    private fun localized(id: Int, vararg formatArgs: Any?): String {
        return LocalizationHelper.getLocalizedString(context, id, *formatArgs)
    }
}
