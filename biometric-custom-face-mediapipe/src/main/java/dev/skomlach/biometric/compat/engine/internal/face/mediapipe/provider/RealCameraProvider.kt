package dev.skomlach.biometric.compat.engine.internal.face.mediapipe.provider

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.media.ImageReader
import android.os.Handler
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.framework.image.MediaImageBuilder
import dev.skomlach.biometric.compat.custom.AbstractCustomBiometricManager
import dev.skomlach.common.permissions.PermissionUtils
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean

class RealCameraProvider(private val context: Context) : IFrameProvider,
    ImageReader.OnImageAvailableListener {

    private var onFrame: ((MPImage) -> Unit)? = null
    private var onError: ((Int, String) -> Unit)? = null
    private var backgroundHandler: Handler? = null

    private val cameraManager: CameraManager =
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var cameraDevice: CameraDevice? = null
    private var imageReader: ImageReader? = null
    private var captureSession: CameraCaptureSession? = null
    private var sensorOrientation: Int = 0

    // Блокування
    private val isConverting = AtomicBoolean(false)

    override fun start(
        handler: Handler,
        frameListener: (image: MPImage) -> Unit,
        errorListener: (code: Int, message: String) -> Unit
    ) {
        this.backgroundHandler = handler
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
                "No Camera Permission"
            )
            return
        }

        val cameraId = getFrontFacingCameraId(cameraManager) ?: run {
            onError?.invoke(
                AbstractCustomBiometricManager.CUSTOM_BIOMETRIC_ERROR_HW_UNAVAILABLE,
                "No front camera"
            )
            return
        }

        try {
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
            sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0

            // Вибираємо 640x480 або близько того. MediaPipe любить 4:3 або 16:9, але 640x480 - ідеал для швидкодії.
            val validSizes = map.getOutputSizes(ImageFormat.YUV_420_888).filter {
                it.width >= 640 && it.height >= 480
            }

            if (validSizes.isEmpty()) {
                onError?.invoke(
                    AbstractCustomBiometricManager.CUSTOM_BIOMETRIC_ERROR_HW_UNAVAILABLE,
                    "Camera resolution low"
                )
                return
            }

            val previewSize =
                Collections.min(validSizes) { l, r -> (l.width * l.height).compareTo(r.width * r.height) }

            // MaxImages = 2, щоб уникнути затримок, але мати буфер
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
        // Ми використовуємо onFrame як сигнал "обробляй".
        // Якщо система зайнята, ми просто пропускаємо кадр (reader автоматично скине старі, якщо maxImages=2 заповнено)

        // Але тут важливий момент: MediaPipe потребує, щоб ми закривали image.
        // Тому ми зчитуємо image, створюємо MPImage і передаємо далі.
        // Відповідальність за close() лежить на споживачеві (Manager).

        try {
            val image = reader?.acquireLatestImage() ?: return

            // Якщо конвертація/обробка вже йде, краще дропнути кадр, щоб не накопичувати лаг
            if (isConverting.get()) {
                image.close()
                return
            }

            // Створюємо MPImage. Це НЕ копіює дані, а лише створює обгортку.
            // setRotation каже MediaPipe, як треба крутити картинку віртуально.
            val mpImage = MediaImageBuilder(image)
                .setOrientation(sensorOrientation)
                .build()

            // Передаємо в Manager
            onFrame?.invoke(mpImage)

            // Важливо: ми НЕ викликаємо image.close() тут.
            // image.close() буде викликано автоматично, коли ми викличемо mpImage.close() в Manager.

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getFrontFacingCameraId(manager: CameraManager): String? {
        return manager.cameraIdList.firstOrNull {
            manager.getCameraCharacteristics(it)
                .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
        }
    }
}