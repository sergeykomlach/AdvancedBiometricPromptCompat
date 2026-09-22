package dev.skomlach.biometric.compat.engine.internal.face.tensorflow.provider

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetector
import dev.skomlach.biometric.compat.custom.AbstractSoftwareBiometricManager
import dev.skomlach.biometric.compat.engine.internal.face.tensorflow.ImageUtils
import dev.skomlach.biometric.compat.utils.SensorPrivacyCheck
import dev.skomlach.biometric.custom.face.tf.R
import dev.skomlach.common.logging.LogCat
import dev.skomlach.common.misc.ExecutorHelper
import dev.skomlach.common.permissions.PermissionUtils
import dev.skomlach.common.translate.LocalizationHelper

class RealCameraProvider(private val context: Context) : IFrameProvider, CaptureContinuityProvider,
    ImageReader.OnImageAvailableListener {

    companion object {
        private const val MIN_PREVIEW_LONG_EDGE = 640
        private const val MIN_PREVIEW_SHORT_EDGE = 480
    }

    private var onFrame: ((Bitmap, List<Face>) -> Unit)? = null
    private var onError: ((Int, String) -> Unit)? = null
    private var onDiscontinuity: (() -> Unit)? = null

    @Synchronized
    override fun setCaptureDiscontinuityListener(listener: (() -> Unit)?) {
        onDiscontinuity = listener
    }
    private var backgroundHandler: Handler? = null
    private var mlKitDetector: FaceDetector? = null

    private val cameraManager: CameraManager =
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var cameraDevice: CameraDevice? = null
    private var imageReader: ImageReader? = null
    private var imageLifetime: FrameResourceLifetime? = null
    private var captureSession: CameraCaptureSession? = null
    private var sensorOrientation: Int = 0
    private var backgroundThread: HandlerThread? = null

    private var yData = ByteArray(0)
    private var uData = ByteArray(0)
    private var vData = ByteArray(0)
    private var argbPixels = IntArray(0)

    @Synchronized
    override fun start(
        faceDetector: FaceDetector,
        frameListener: (bitmap: Bitmap, faces: List<Face>) -> Unit,
        errorListener: (code: Int, message: String) -> Unit
    ) {
        if (backgroundThread != null) stop()
        if (backgroundThread == null) {
            backgroundThread = HandlerThread("TensorFlowFaceCameraProvider").apply {
                start()
                backgroundHandler = Handler(looper)
            }
        }
        this.mlKitDetector = faceDetector
        this.onFrame = frameListener
        this.onError = errorListener
        startCamera()
    }

    @Synchronized
    override fun stop() {
        val lifetime = imageLifetime
        imageLifetime = null
        try {
            imageReader?.setOnImageAvailableListener(null, null)
            try {
                captureSession?.stopRepeating()
                captureSession?.abortCaptures()
            } catch (e: Exception) {
                LogCat.logException(e)
            }
            captureSession?.close()
            captureSession = null
            cameraDevice?.close()
            cameraDevice = null
            backgroundHandler?.removeCallbacksAndMessages(null)
            backgroundHandler = null
            backgroundThread?.quitSafely()
            backgroundThread = null
        } catch (e: Exception) {
            LogCat.logException(e)
        } finally {
            imageReader = null
            onFrame = null
            onError = null
            mlKitDetector = null
            // A detector task may still be reading the native image. Its completion
            // closes the image first, then releases this retired reader.
            lifetime?.close()
            SensorPrivacyCheck.notifySelfCameraClosed()
        }
    }

    override fun isHardwareSupported(): Boolean {
        return try {
            !getCaptureCameraId(cameraManager).isNullOrEmpty()
        } catch (_: Exception) {
            false
        }
    }

    override fun isHardwareCapabilityAvailable(): Boolean {
        val packageManager = context.packageManager
        return try {
                    packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT) ||
                    packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_EXTERNAL) ||
                    packageManager.hasSystemFeature(PackageManager.FEATURE_USB_HOST) ||
                    cameraManager.cameraIdList.isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }

    @SuppressLint("MissingPermission")
    private fun startCamera() {
        if (!PermissionUtils.INSTANCE.hasSelfPermissions(Manifest.permission.CAMERA)) {
            onError?.invoke(
                AbstractSoftwareBiometricManager.CUSTOM_BIOMETRIC_ERROR_NO_PERMISSIONS,
                LocalizationHelper.getLocalizedString(
                    context,
                    R.string.biometriccompat_tf_face_help_model_no_camera_permissions
                )
                // "No Camera Permission"
            )
            return
        }

        val cameraId = getCaptureCameraId(cameraManager) ?: run {
            onError?.invoke(
                AbstractSoftwareBiometricManager.CUSTOM_BIOMETRIC_ERROR_HW_UNAVAILABLE,
                LocalizationHelper.getLocalizedString(
                    context,
                    R.string.biometriccompat_tf_face_help_model_no_front_camera
                )
//                "No front camera"
            )
            return
        }

        try {
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
            sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0

            val validSizes = map.getOutputSizes(ImageFormat.YUV_420_888)
                .filter { isUsablePreviewSize(it) }

            if (validSizes.isEmpty()) {
                onError?.invoke(
                    AbstractSoftwareBiometricManager.CUSTOM_BIOMETRIC_ERROR_HW_UNAVAILABLE,
                    LocalizationHelper.getLocalizedString(
                        context,
                        R.string.biometriccompat_tf_face_help_model_camera_low_res
                    )
                )
                return
            }

            val previewSize = choosePreviewSize(validSizes)
            LogCat.log(
                javaClass.simpleName,
                "Using camera preview size: ${previewSize.width}x${previewSize.height}, sensorOrientation=$sensorOrientation"
            )

            imageReader = ImageReader.newInstance(
                previewSize.width,
                previewSize.height,
                ImageFormat.YUV_420_888,
                2
            )
            val sessionReader = imageReader ?: return
            imageLifetime = FrameResourceLifetime { sessionReader.close() }
            imageReader?.setOnImageAvailableListener(this, backgroundHandler)

            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    synchronized(this@RealCameraProvider) {
                        if (imageReader !== sessionReader) {
                            camera.close()
                            return
                        }
                        SensorPrivacyCheck.notifySelfCameraOpened()
                        cameraDevice = camera
                        createCaptureSession()
                    }
                }

                override fun onClosed(camera: CameraDevice) {
                    synchronized(this@RealCameraProvider) {
                        if (imageReader === sessionReader) {
                            SensorPrivacyCheck.notifySelfCameraClosed()
                        }
                    }
                    super.onClosed(camera)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    synchronized(this@RealCameraProvider) {
                        camera.close()
                        if (cameraDevice === camera) cameraDevice = null
                    }
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    synchronized(this@RealCameraProvider) {
                        camera.close()
                        if (cameraDevice === camera) cameraDevice = null
                        if (imageReader !== sessionReader) return
                        onError?.invoke(
                            AbstractSoftwareBiometricManager.CUSTOM_BIOMETRIC_ERROR_UNABLE_TO_PROCESS,
                            LocalizationHelper.getLocalizedString(
                                context,
                                R.string.biometriccompat_tf_face_help_camera_error,
                                error
                            )
                        )
                    }
                }
            }, backgroundHandler)

        } catch (e: Exception) {
            LogCat.logException(e)
            onError?.invoke(
                AbstractSoftwareBiometricManager.CUSTOM_BIOMETRIC_ERROR_HW_UNAVAILABLE,
                LocalizationHelper.getLocalizedString(
                    context,
                    R.string.biometriccompat_tf_face_help_model_error_generic
                )
            )
        }
    }

    private fun createCaptureSession() {
        try {
            val sessionReader = imageReader ?: return
            val surface = sessionReader.surface
            val requestBuilder =
                cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)?.apply {
                    addTarget(surface)
                } ?: return

            cameraDevice?.createCaptureSession(
                listOf(surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        synchronized(this@RealCameraProvider) {
                            if (imageReader !== sessionReader) {
                                session.close()
                                return
                            }
                            captureSession = session
                            try {
                                session.setRepeatingRequest(
                                    requestBuilder.build(),
                                    null,
                                    backgroundHandler
                                )
                            } catch (e: Exception) {
                                LogCat.logException(e)
                            }
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {}
                },
                backgroundHandler
            )
        } catch (e: Exception) {
            LogCat.logException(e)
        }
    }

    @Synchronized
    override fun onImageAvailable(reader: ImageReader?) {
        if (reader == null || reader !== imageReader) return
        val lifetime = imageLifetime ?: return
        val detector = mlKitDetector ?: return
        val image = try {
            lifetime.acquireFrame { reader.acquireLatestImage() }
        } catch (e: Exception) {
            LogCat.logException(e)
            onDiscontinuity?.invoke()
            null
        } ?: return

        val task = try {
            detector.process(InputImage.fromMediaImage(image, sensorOrientation))
        } catch (e: Exception) {
            lifetime.completeFrame { image.close() }
            LogCat.logException(e)
            onDiscontinuity?.invoke()
            return
        }
        // This executor outlives the camera HandlerThread, so stop() cannot discard
        // the completion callback and leak an acquired image/reader.
        task.addOnCompleteListener(ExecutorHelper.backgroundExecutor) { result ->
            try {
                synchronized(this) {
                    if (reader !== imageReader || !lifetime.isOpen()) return@synchronized
                    if (result.isSuccessful) {
                        val faces = result.result
                        if (faces.isNotEmpty()) processImageToBitmap(image, faces)
                        else onDiscontinuity?.invoke()
                    } else {
                        result.exception?.let { LogCat.logException(it) }
                        onDiscontinuity?.invoke()
                    }
                }
            } catch (e: Exception) {
                LogCat.logException(e)
                synchronized(this) {
                    if (reader === imageReader && lifetime.isOpen()) onDiscontinuity?.invoke()
                }
            } finally {
                lifetime.completeFrame { image.close() }
            }
        }
    }

    private fun processImageToBitmap(image: android.media.Image, faces: List<Face>) {
        try {
            val width = image.width
            val height = image.height
            val planes = image.planes
            val yBuffer = planes[0].buffer
            val uBuffer = planes[1].buffer
            val vBuffer = planes[2].buffer


            if (yData.size != yBuffer.remaining()) yData = ByteArray(yBuffer.remaining())
            if (uData.size != uBuffer.remaining()) uData = ByteArray(uBuffer.remaining())
            if (vData.size != vBuffer.remaining()) vData = ByteArray(vBuffer.remaining())
            yBuffer.get(yData)
            uBuffer.get(uData)
            vBuffer.get(vData)

            val yRowStride = planes[0].rowStride
            val uvRowStride = planes[1].rowStride
            val uvPixelStride = planes[1].pixelStride
            if (argbPixels.size != width * height) argbPixels = IntArray(width * height)
            ImageUtils.convertYUV420ToARGB8888(
                yData, uData, vData,
                width, height,
                yRowStride, uvRowStride, uvPixelStride,
                argbPixels
            )

            val unrotatedBitmap =
                Bitmap.createBitmap(argbPixels, width, height, Bitmap.Config.ARGB_8888)
            val matrix = Matrix().apply { postRotate(sensorOrientation.toFloat()) }
            val finalBitmap =
                Bitmap.createBitmap(unrotatedBitmap, 0, 0, width, height, matrix, true)
            if (finalBitmap !== unrotatedBitmap) {
                unrotatedBitmap.recycle()
            }

            val frameListener = onFrame
            if (frameListener == null) {
                finalBitmap.recycle()
            } else {
                try {
                    frameListener.invoke(finalBitmap, faces)
                } catch (error: Throwable) {
                    if (!finalBitmap.isRecycled) finalBitmap.recycle()
                    throw error
                }
            }

        } catch (e: Exception) {
            LogCat.logException(e)
            onDiscontinuity?.invoke()
        }
    }


    private fun choosePreviewSize(validSizes: List<Size>): Size {
        val filtered = validSizes
            .filter { it.width <= 1920 && it.height <= 1080 }
            .ifEmpty { validSizes }

        filtered.firstOrNull { it.width == 1280 && it.height == 720 }?.let { return it }

        return filtered.maxWithOrNull(
            compareBy<Size> { minOf(it.width, 1280) * minOf(it.height, 720) }
                .thenBy { -(kotlin.math.abs(it.width - 1280) + kotlin.math.abs(it.height - 720)) }
                .thenBy { it.width * it.height }
        ) ?: filtered.maxByOrNull { it.width * it.height } ?: validSizes.first()
    }

    private fun getCaptureCameraId(manager: CameraManager): String? {
        val candidates = manager.cameraIdList.mapNotNull { id ->
            val characteristics = manager.getCameraCharacteristics(id)
            val lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING)
            if (!isFaceCaptureLens(lensFacing) || !hasUsablePreviewOutput(characteristics)) {
                null
            } else {
                id to lensFacing
            }
        }
        return candidates.firstOrNull {
            it.second == CameraCharacteristics.LENS_FACING_FRONT
        }?.first ?: candidates.firstOrNull {
            it.second == CameraCharacteristics.LENS_FACING_EXTERNAL
        }?.first
    }

    private fun isFaceCaptureLens(lensFacing: Int?): Boolean {
        return lensFacing == CameraCharacteristics.LENS_FACING_FRONT ||
                lensFacing == CameraCharacteristics.LENS_FACING_EXTERNAL
    }

    private fun hasUsablePreviewOutput(characteristics: CameraCharacteristics): Boolean {
        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ?: return false
        return map.getOutputSizes(ImageFormat.YUV_420_888)
            ?.any { isUsablePreviewSize(it) } == true
    }

    private fun isUsablePreviewSize(size: Size): Boolean {
        return maxOf(size.width, size.height) >= MIN_PREVIEW_LONG_EDGE &&
                minOf(size.width, size.height) >= MIN_PREVIEW_SHORT_EDGE
    }
}
