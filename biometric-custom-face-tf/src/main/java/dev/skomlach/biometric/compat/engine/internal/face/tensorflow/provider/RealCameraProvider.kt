package dev.skomlach.biometric.compat.engine.internal.face.tensorflow.provider

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
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
import dev.skomlach.common.permissions.PermissionUtils
import dev.skomlach.common.translate.LocalizationHelper
import java.util.concurrent.atomic.AtomicBoolean

class RealCameraProvider(private val context: Context) : IFrameProvider,
    ImageReader.OnImageAvailableListener {

    private var onFrame: ((Bitmap, List<Face>) -> Unit)? = null
    private var onError: ((Int, String) -> Unit)? = null
    private var backgroundHandler: Handler? = null
    private var mlKitDetector: FaceDetector? = null

    private val cameraManager: CameraManager =
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var cameraDevice: CameraDevice? = null
    private var imageReader: ImageReader? = null
    private var captureSession: CameraCaptureSession? = null
    private var sensorOrientation: Int = 0
    private var backgroundThread: HandlerThread? = null

    private val isConverting = AtomicBoolean(false)

    override fun start(
        faceDetector: FaceDetector,
        frameListener: (bitmap: Bitmap, faces: List<Face>) -> Unit,
        errorListener: (code: Int, message: String) -> Unit
    ) {
        if (backgroundThread == null) {
            backgroundThread = HandlerThread("FakeAssetProvider").apply {
                start()
                backgroundHandler = Handler(looper)
            }
        }
        this.mlKitDetector = faceDetector
        this.onFrame = frameListener
        this.onError = errorListener
        startCamera()
    }

    override fun stop() {
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
            imageReader?.close()
            imageReader = null
            backgroundHandler?.removeCallbacksAndMessages(null)
            backgroundHandler = null
            backgroundThread?.quitSafely()
            backgroundThread = null
        } catch (e: Exception) {
            LogCat.logException(e)
        } finally {
            isConverting.set(false)
            SensorPrivacyCheck.notifySelfCameraClosed()
        }
    }

    override fun isHardwareSupported(): Boolean {
        return try {
            !getFrontFacingCameraId(cameraManager).isNullOrEmpty()
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

        val cameraId = getFrontFacingCameraId(cameraManager) ?: run {
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

            val validSizes = map.getOutputSizes(ImageFormat.YUV_420_888).filter {
                it.width >= 1280 && it.height >= 720
            }

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
            imageReader?.setOnImageAvailableListener(this, backgroundHandler)

            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    SensorPrivacyCheck.notifySelfCameraOpened()
                    cameraDevice = camera
                    createCaptureSession()
                }

                override fun onClosed(camera: CameraDevice) {
                    SensorPrivacyCheck.notifySelfCameraClosed()
                    super.onClosed(camera)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    cameraDevice = null
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    cameraDevice = null
                    onError?.invoke(
                        AbstractSoftwareBiometricManager.CUSTOM_BIOMETRIC_ERROR_UNABLE_TO_PROCESS,
                        "Camera Error: $error"
                    )
                }
            }, backgroundHandler)

        } catch (e: Exception) {
            LogCat.logException(e)
            onError?.invoke(
                AbstractSoftwareBiometricManager.CUSTOM_BIOMETRIC_ERROR_HW_UNAVAILABLE,
                e.message ?: "Error"
            )
        }
    }

    private fun createCaptureSession() {
        try {
            val surface = imageReader?.surface ?: return
            val requestBuilder =
                cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)?.apply {
                    addTarget(surface)
                } ?: return

            cameraDevice?.createCaptureSession(
                listOf(surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
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

                    override fun onConfigureFailed(session: CameraCaptureSession) {}
                },
                backgroundHandler
            )
        } catch (e: Exception) {
            LogCat.logException(e)
        }
    }

    override fun onImageAvailable(reader: ImageReader?) {

        if (isConverting.get() || backgroundHandler == null) {
            try {
                reader?.acquireLatestImage()?.close()
            } catch (_: Exception) {
            }
            return
        }

        val image = reader?.acquireLatestImage() ?: return
        isConverting.set(true)

        try {
            val inputImage = InputImage.fromMediaImage(image, sensorOrientation)


            mlKitDetector?.process(inputImage)
                ?.addOnSuccessListener { faces ->
                    if (faces.isNotEmpty()) {
                        processImageToBitmap(image, faces)
                    } else {
                        image.close()
                        isConverting.set(false)
                    }
                }
                ?.addOnFailureListener {
                    LogCat.logException(it)
                    image.close()
                    isConverting.set(false)
                }
        } catch (e: Exception) {
            LogCat.logException(e)
            image.close()
            isConverting.set(false)
        }
    }

    private fun processImageToBitmap(image: android.media.Image, faces: List<Face>) {
        backgroundHandler?.post {
            try {
                val width = image.width
                val height = image.height
                val planes = image.planes
                val yBuffer = planes[0].buffer
                val uBuffer = planes[1].buffer
                val vBuffer = planes[2].buffer


                val yData = ByteArray(yBuffer.remaining()).apply { yBuffer.get(this) }
                val uData = ByteArray(uBuffer.remaining()).apply { uBuffer.get(this) }
                val vData = ByteArray(vBuffer.remaining()).apply { vBuffer.get(this) }

                val yRowStride = planes[0].rowStride
                val uvRowStride = planes[1].rowStride
                val uvPixelStride = planes[1].pixelStride
                image.close()
                val pixels = IntArray(width * height)
                ImageUtils.convertYUV420ToARGB8888(
                    yData, uData, vData,
                    width, height,
                    yRowStride, uvRowStride, uvPixelStride,
                    pixels
                )

                val unrotatedBitmap =
                    Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
                val matrix = Matrix().apply { postRotate(sensorOrientation.toFloat()) }
                val finalBitmap =
                    Bitmap.createBitmap(unrotatedBitmap, 0, 0, width, height, matrix, true)
                unrotatedBitmap.recycle()

                onFrame?.invoke(finalBitmap, faces)

            } catch (e: Exception) {
                LogCat.logException(e)
            } finally {
                try {
                    image.close()
                } catch (_: Exception) {
                }
                isConverting.set(false)
            }
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

    private fun getFrontFacingCameraId(manager: CameraManager): String? {
        return manager.cameraIdList.firstOrNull {
            if(manager.getCameraCharacteristics(it)
                .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT){
                val characteristics = manager.getCameraCharacteristics(it)
                val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
                val validSizes = map.getOutputSizes(ImageFormat.YUV_420_888).filter { s->
                    s.width >= 1280 && s.height >= 720
                }

                validSizes.isNotEmpty()
            } else false
        }
    }
}