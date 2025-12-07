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
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetector
import dev.skomlach.biometric.compat.custom.AbstractCustomBiometricManager
import dev.skomlach.biometric.compat.engine.internal.face.tensorflow.ImageUtils
import dev.skomlach.biometric.custom.face.tf.R
import dev.skomlach.common.permissions.PermissionUtils
import dev.skomlach.common.translate.LocalizationHelper
import java.util.Collections
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


    private val isConverting = AtomicBoolean(false)

    override fun start(
        handler: Handler,
        faceDetector: FaceDetector,
        frameListener: (bitmap: Bitmap, faces: List<Face>) -> Unit,
        errorListener: (code: Int, message: String) -> Unit
    ) {
        this.backgroundHandler = handler
        this.mlKitDetector = faceDetector
        this.onFrame = frameListener
        this.onError = errorListener
        startCamera()
    }

    override fun stop() {
        try {
            captureSession?.stopRepeating()
            captureSession?.close()
            cameraDevice?.close()
            imageReader?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            captureSession = null
            cameraDevice = null
            imageReader = null
            isConverting.set(false)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startCamera() {
        if (!PermissionUtils.INSTANCE.hasSelfPermissions(Manifest.permission.CAMERA)) {
            onError?.invoke(
                AbstractCustomBiometricManager.CUSTOM_BIOMETRIC_ERROR_HW_UNAVAILABLE,
                LocalizationHelper.getLocalizedString(context, R.string.tf_face_help_model_no_camera_permissions)
               // "No Camera Permission"
            )
            return
        }

        val cameraId = getFrontFacingCameraId(cameraManager) ?: run {
            onError?.invoke(
                AbstractCustomBiometricManager.CUSTOM_BIOMETRIC_ERROR_HW_UNAVAILABLE,
                LocalizationHelper.getLocalizedString(context, R.string.tf_face_help_model_no_front_camera)
//                "No front camera"
            )
            return
        }

        try {
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
            sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0

            val validSizes = map.getOutputSizes(ImageFormat.YUV_420_888).filter {
                it.width >= 640 && it.height >= 480
            }

            if (validSizes.isEmpty()) {
                onError?.invoke(
                    AbstractCustomBiometricManager.CUSTOM_BIOMETRIC_ERROR_HW_UNAVAILABLE,
                    LocalizationHelper.getLocalizedString(context, R.string.tf_face_help_model_camera_low_res)
                )
                return
            }

            val previewSize =
                Collections.min(validSizes) { l, r -> (l.width * l.height).compareTo(r.width * r.height) }

            imageReader = ImageReader.newInstance(
                previewSize.width,
                previewSize.height,
                ImageFormat.YUV_420_888,
                2
            )
            imageReader?.setOnImageAvailableListener(this, backgroundHandler)

            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    createCaptureSession()
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    cameraDevice = null
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    cameraDevice = null
                    onError?.invoke(
                        AbstractCustomBiometricManager.CUSTOM_BIOMETRIC_ERROR_UNABLE_TO_PROCESS,
                        "Camera Error: $error"
                    )
                }
            }, backgroundHandler)

        } catch (e: Exception) {
            onError?.invoke(
                AbstractCustomBiometricManager.CUSTOM_BIOMETRIC_ERROR_HW_UNAVAILABLE,
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
                            e.printStackTrace()
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {}
                },
                backgroundHandler
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onImageAvailable(reader: ImageReader?) {

        if (isConverting.get() || backgroundHandler == null) {
            reader?.acquireLatestImage()?.close()
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
                    image.close()
                    isConverting.set(false)
                }
        } catch (e: Exception) {
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

                val pixels = IntArray(width * height)
                ImageUtils.convertYUV420ToARGB8888(
                    yData, uData, vData,
                    width, height,
                    planes[0].rowStride, planes[1].rowStride, planes[1].pixelStride,
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
                e.printStackTrace()
            } finally {
                image.close()
                isConverting.set(false)
            }
        }
    }

    private fun getFrontFacingCameraId(manager: CameraManager): String? {
        return manager.cameraIdList.firstOrNull {
            manager.getCameraCharacteristics(it)
                .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
        }
    }
}