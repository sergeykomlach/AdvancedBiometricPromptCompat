/*
 *  Copyright (c) 2025 Sergey Komlach aka Salat-Cx65; Original project https://github.com/Salat-Cx65/AdvancedBiometricPromptCompat
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

// file: TensorFlowFaceUnlockManager.kt
package dev.skomlach.biometric.compat.engine.internal.face.tensorflow

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Rect
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.media.ImageReader
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.HandlerThread
import androidx.core.graphics.scale
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import dev.skomlach.biometric.compat.custom.AbstractCustomBiometricManager
import dev.skomlach.common.contextprovider.AndroidContext
import dev.skomlach.common.misc.ExecutorHelper
import dev.skomlach.common.permissions.PermissionUtils
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.RuntimeFlavor
import org.tensorflow.lite.gpu.GpuDelegateFactory
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.Rot90Op
import java.util.Collections
import java.util.UUID

class TensorFlowFaceUnlockManager(private val context: Context) : AbstractCustomBiometricManager(),
    ImageReader.OnImageAvailableListener {

    companion object {
        const val IS_ENROLLMENT_KEY = "is_enrollment"
        const val ENROLLMENT_TAG_KEY = "enrollment_tag"

        private const val TIMEOUT_MS = 30000L
        private const val CONFIDENCE_THRESHOLD = 0.90f // Поріг з DetectorActivity

        // Налаштування моделі з DetectorActivity
        private const val TF_OD_API_INPUT_SIZE = 112
        private const val TF_OD_API_IS_QUANTIZED = false
        private const val TF_OD_API_MODEL_FILE = "mobile_face_net.tflite"
        private const val TF_OD_API_LABELS_FILE = "file:///android_asset/labelmap.txt"
    }

    // --- Камера та обробка ---
    private val cameraManager: CameraManager =
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null
    private var cameraDevice: CameraDevice? = null
    private var imageReader: ImageReader? = null
    private var captureSession: CameraCaptureSession? = null
    private val timeoutHandler = Handler(ExecutorHelper.handler.looper)
    private var isProcessing = false
    private var sensorOrientation: Int = 0
    private var positiveMatchCounter = 0

    // **ДОДАНО:** Поля, які були відсутні
    private var previewWidth: Int = 0
    private var previewHeight: Int = 0


    // --- TensorFlow & MLKit ---
    // **ВИПРАВЛЕНО:** Ініціалізація зроблена 'lazy', щоб уникнути блокування в 'init'
    private val faceDetector: FaceDetector? by lazy {
        try {
            val options = FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setContourMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .build()
            FaceDetection.getClient(options)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private val detector: SimilarityClassifier? by lazy {
        try {
            val interpreterOptions = Interpreter.Options()
            try {
                interpreterOptions.addDelegate(GpuDelegateFactory().create(RuntimeFlavor.SYSTEM))
            } catch (e: Exception) {
                try {
                    interpreterOptions.addDelegate(GpuDelegateFactory().create(RuntimeFlavor.APPLICATION))
                } catch (e: Exception) {
                    // Помилка GPU, нічого страшного, працюватиме на CPU
                    e.printStackTrace()
                }
            }

            TFLiteObjectDetectionAPIModel.create(
                context.assets,
                TF_OD_API_MODEL_FILE,
                TF_OD_API_LABELS_FILE,
                TF_OD_API_INPUT_SIZE,
                TF_OD_API_IS_QUANTIZED,
                interpreterOptions
            )
        } catch (e: Exception) {
            // Помилка ініціалізації, цей менеджер не буде працездатним
            e.printStackTrace()
            null
        }
    }

    // --- Стан ---
    private var authCallback: AuthenticationCallback? = null
    private var cancellationSignal: CancellationSignal? = null
    private var isEnrolling: Boolean = false
    private var enrollmentTag: String? = null

    // **ВИДАЛЕНО:** 'init' блок. Ініціалізація тепер 'lazy'.

    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground").apply {
            start()
            backgroundHandler = Handler(looper)
        }
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            // ignore
        }
    }

    private val timeoutRunnable = Runnable {
        authCallback?.onAuthenticationError(
            CUSTOM_BIOMETRIC_ERROR_TIMEOUT,
            "Authentication timed out"
        )
        stopAuthentication()
    }

    private fun stopAuthentication() {
        timeoutHandler.removeCallbacks(timeoutRunnable)

        try {
            captureSession?.stopRepeating()
            captureSession?.close()
            cameraDevice?.close()
            imageReader?.close()
        } catch (e: Exception) {
            // ignore
        } finally {
            captureSession = null
            cameraDevice = null
            imageReader = null
            isProcessing = false // Скидаємо блокування
            positiveMatchCounter = 0
            stopBackgroundThread()

            authCallback = null
            cancellationSignal = null
            isEnrolling = false
            enrollmentTag = null
        }
    }

    override fun isHardwareDetected(): Boolean {
        // 'lazy' ініціалізація спрацює тут
        if (!PermissionUtils.INSTANCE.hasSelfPermissions(Manifest.permission.CAMERA) || detector == null || faceDetector == null) return false // Не вдалось завантажити моделі
        return try {
            getFrontFacingCameraId(cameraManager) != null
        } catch (e: Exception) {
            false
        }
    }

    override fun hasEnrolledBiometric(): Boolean {
        // Це I/O, але очікується, що воно буде швидким
        return try {
            val prefs =
                AndroidContext.appContext.getSharedPreferences("biometric_tf", Context.MODE_PRIVATE)
            val json = prefs.getString("registered", null)
            !json.isNullOrEmpty() && json != "{}"
        } catch (e: Exception) {
            false
        }
    }

    override fun remove(extra: Bundle?) {
        stopAuthentication()
        if (extra == null) {
            val prefs =
                AndroidContext.appContext.getSharedPreferences("biometric_tf", Context.MODE_PRIVATE)
            prefs.edit().clear().apply()
        } else {
            enrollmentTag = extra?.getString(ENROLLMENT_TAG_KEY)
            detector?.delete(enrollmentTag)
        }
    }

    override fun authenticate(
        crypto: CryptoObject?,
        flags: Int,
        cancel: CancellationSignal?,
        callback: AuthenticationCallback?,
        handler: Handler?,
        extra: Bundle?
    ) {
        if (!isHardwareDetected()) {
            callback?.onAuthenticationError(
                CUSTOM_BIOMETRIC_ERROR_HW_NOT_PRESENT,
                "Face detection models or camera not available"
            )
            return
        }

        if (!PermissionUtils.INSTANCE.hasSelfPermissions(Manifest.permission.CAMERA)) {
            callback?.onAuthenticationError(
                CUSTOM_BIOMETRIC_ERROR_HW_UNAVAILABLE,
                "Camera permission not granted"
            )
            return
        }

        authCallback = callback
        cancellationSignal = cancel
        isEnrolling = extra?.getBoolean(IS_ENROLLMENT_KEY, false) ?: false
        enrollmentTag = extra?.getString(ENROLLMENT_TAG_KEY) ?: UUID.randomUUID().toString()

        if (isEnrolling && enrollmentTag.isNullOrBlank()) {
            callback?.onAuthenticationError(
                CUSTOM_BIOMETRIC_ERROR_UNABLE_TO_PROCESS,
                "Enrollment tag not provided"
            )
            return
        }

        cancellationSignal?.setOnCancelListener {
            authCallback?.onAuthenticationCancelled()
            stopAuthentication()
        }

        startBackgroundThread()
        startCamera()

        // Встановлюємо таймаут
        timeoutHandler.postDelayed(timeoutRunnable, TIMEOUT_MS)
    }


    @SuppressLint("MissingPermission")
    private fun startCamera() {
        val cameraId =
            getFrontFacingCameraId(cameraManager) ?: run {
                authCallback?.onAuthenticationError(
                    CUSTOM_BIOMETRIC_ERROR_HW_UNAVAILABLE,
                    "No front-facing camera"
                )
                stopAuthentication()
                return
            }

        try {
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?: throw CameraAccessException(CameraAccessException.CAMERA_ERROR, "No map")

            // Зберігаємо орієнтацію сенсора
            sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0

            // Вибираємо найменший розмір, здатний обробляти YUV_420_888
            // Це аналогічно 'chooseOptimalSize'
            val sizes = map.getOutputSizes(ImageFormat.YUV_420_888)

            val MIN_WIDTH = 640
            val MIN_HEIGHT = 480

            val validSizes = sizes.filter {
                (it.width * it.height) >= (MIN_WIDTH * MIN_HEIGHT)
            }

            if (validSizes.isEmpty()) {
                // На пристрої немає камери, що відповідає мінімальним вимогам
                authCallback?.onAuthenticationError(
                    CUSTOM_BIOMETRIC_ERROR_HW_UNAVAILABLE,
                    "Camera resolution is too low"
                )
                stopAuthentication()
                return
            }

            val previewSize = Collections.min(validSizes) { l, r -> // <--- Використовуйте 'validSizes'
                (l.width * l.height).compareTo(r.width * r.height)
            }

            previewWidth = previewSize.width
            previewHeight = previewSize.height

            imageReader = ImageReader.newInstance(
                previewWidth, previewHeight, ImageFormat.YUV_420_888, 2
            ).apply {
                setOnImageAvailableListener(this@TensorFlowFaceUnlockManager, backgroundHandler)
            }

            cameraManager.openCamera(cameraId, cameraStateCallback, backgroundHandler)

        } catch (e: CameraAccessException) {
            authCallback?.onAuthenticationError(
                CUSTOM_BIOMETRIC_ERROR_HW_UNAVAILABLE,
                e.message
            )
            stopAuthentication()
        }
    }

    private val cameraStateCallback = object : CameraDevice.StateCallback() {
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
            authCallback?.onAuthenticationError(
                CUSTOM_BIOMETRIC_ERROR_UNABLE_TO_PROCESS,
                "Camera error: $error"
            )
            stopAuthentication()
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
                        } catch (e: CameraAccessException) {
                            // handle error
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        authCallback?.onAuthenticationError(
                            CUSTOM_BIOMETRIC_ERROR_UNABLE_TO_PROCESS,
                            "Failed to configure camera session"
                        )
                        stopAuthentication()
                    }
                },
                backgroundHandler
            )
        } catch (e: CameraAccessException) {
            authCallback?.onAuthenticationError(
                CUSTOM_BIOMETRIC_ERROR_UNABLE_TO_PROCESS,
                e.message
            )
            stopAuthentication()
        }
    }

    // **КРИТИЧНЕ ВИПРАВЛЕННЯ:** Повністю переписано onImageAvailable
    // для виправлення race condition та блокування потоку
    override fun onImageAvailable(reader: ImageReader?) {
        if (isProcessing || backgroundHandler == null) {
            reader?.acquireLatestImage()?.close() // Важливо очистити чергу
            return
        }

        val image = reader?.acquireLatestImage()
        if (image == null) {
            return
        }

        isProcessing = true // Встановлюємо блокування

        try {
            // 1. Створюємо InputImage з YUV (швидко)
            // MLKit сам впорається з YUV_420_888 та поворотом
            val inputImage = InputImage.fromMediaImage(image, sensorOrientation)

            // 2. Запускаємо MLKit. Не вказуємо 'handler',
            // щоб він не блокував потік камери.
            // Callbacks прийдуть на Main Thread (або потік MLKit).
            faceDetector?.process(inputImage)
                ?.addOnSuccessListener { faces ->
                    // ЦЕ ВЖЕ НЕ ПОТІК КАМЕРИ
                    if (faces.size == 1) {
                        // Знайшли обличчя.
                        // Тепер нам потрібен Bitmap для TFLite.
                        // Відправляємо важку роботу (конвертація+TFLite) на фоновий потік.
                        backgroundHandler?.post {
                            try {
                                // Конвертуємо YUV в Bitmap
                                val yuvImage = TensorImage().apply { load(image) }
                                val rotationOps = Rot90Op(sensorOrientation / 90)
                                val imageProcessor =
                                    ImageProcessor.Builder().add(rotationOps).build()
                                val rgbImage = imageProcessor.process(yuvImage)
                                val bitmap = rgbImage.bitmap

                                // Тепер, коли маємо Bitmap, обробляємо обличчя
                                handleFace(faces[0], bitmap)

                                // isProcessing = false буде встановлено в handleFace (через stopAuthentication)
                                // або в catch блоці
                            } catch (e: Exception) {
                                e.printStackTrace()
                                // Помилка в TFLite/Bitmap
                                isProcessing = false
                            } finally {
                                image.close() // **ВИПРАВЛЕНО:** ЗАКРИВАЄМО ТУТ (після обробки)
                            }
                        }
                    } else {
                        // Облич не знайдено
                        isProcessing = false
                        image.close() // **ВИПРАВЛЕНО:** ЗАКРИВАЄМО ТУТ
                    }
                }
                ?.addOnFailureListener { e ->
                    // Помилка MLKit
                    e.printStackTrace()
                    isProcessing = false
                    image.close() // **ВИПРАВЛЕНО:** ЗАКРИВАЄМО ТУТ
                }
        } catch (e: Exception) {
            // Синхронна помилка (напр. fromMediaImage)
            e.printStackTrace()
            isProcessing = false
            image.close() // **ВИПРАВЛЕНО:** ЗАКРИВАЄМО ТУТ
        }
    }

    private fun isBitmapBrightEnough(bitmap: Bitmap, threshold: Int): Boolean {
        // Ми можемо перевірити лише невелику кількість пікселів для швидкості
        val width = bitmap.width
        val height = bitmap.height
        var totalLuminance = 0L
        val stepSize = 10 // Перевіряємо кожен 10-й піксель
        var pixelCount = 0

        for (y in 0 until height step stepSize) {
            for (x in 0 until width step stepSize) {
                val pixel = bitmap.getPixel(x, y)
                // Швидкий розрахунок яскравості (Luminance)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                totalLuminance += (0.299 * r + 0.587 * g + 0.114 * b).toLong()
                pixelCount++
            }
        }

        if (pixelCount == 0) return true // Уникаємо ділення на нуль
        val avgLuminance = totalLuminance / pixelCount
        return avgLuminance >= threshold
    }

    private fun handleFace(face: Face, originalBitmap: Bitmap) {
        // Цей метод тепер завжди викликається на backgroundHandler
        try {

            val yaw = face.headEulerAngleY // Нахил вліво/вправо (як "ні")
            val roll = face.headEulerAngleZ // Нахил до плеча

            // Дозволяємо відхилення не більше 30 градусів
            val MAX_ANGLE_DEGREES = 30f

            if (Math.abs(yaw) > MAX_ANGLE_DEGREES || Math.abs(roll) > MAX_ANGLE_DEGREES) {
                isProcessing = false // Обличчя занадто сильно повернуте
                return
            }

            val leftEyeOpen = face.leftEyeOpenProbability
            val rightEyeOpen = face.rightEyeOpenProbability

            // Перевіряємо, чи очі взагалі розпізнані
            if (leftEyeOpen != null && rightEyeOpen != null) {
                // Встановлюємо поріг "відкритості", напр. 0.4 (40%)
                // Якщо обидва ока закриті (або це фото), ймовірність буде низькою.
                if (leftEyeOpen < 0.4f && rightEyeOpen < 0.4f) {
                    // Це, ймовірно, фото або людина з закритими очима.
                    // Не розпізнаємо, чекаємо наступного кадру.
                    isProcessing = false
                    return
                }
            }


            // Вирізаємо обличчя з bitmap (як у DetectorActivity, але простіше)
            val bb = face.boundingBox
            // Переконуємось, що ми не виходимо за межі
            val rect = Rect(
                bb.left.coerceAtLeast(0),
                bb.top.coerceAtLeast(0),
                bb.right.coerceAtMost(originalBitmap.width),
                bb.bottom.coerceAtMost(originalBitmap.height)
            )
            if (rect.width() <= 0 || rect.height() <= 0) {
                isProcessing = false // Не змогли обрізати, чекаємо на наступний кадр
                return
            }

            val faceBitmap = Bitmap.createBitmap(
                originalBitmap,
                rect.left,
                rect.top,
                rect.width(),
                rect.height()
            )
            if (!isBitmapBrightEnough(faceBitmap, 50)) { // 50 - це поріг яскравості (0-255)
                isProcessing = false // Зображення занадто темне
                return
            }
            // Масштабуємо до потрібного розміру моделі
            val scaledFace = faceBitmap.scale(TF_OD_API_INPUT_SIZE, TF_OD_API_INPUT_SIZE, false)

            // Запускаємо розпізнавання
            // 'detector' вже 'lazy' ініціалізований
            val results = detector?.recognizeImage(scaledFace, isEnrolling)

            if (results.isNullOrEmpty()) {
                positiveMatchCounter = 0 // <--- Скидаємо при невдачі TFLite
                isProcessing = false
                return
            }

            val recognition = results[0]

            if (isEnrolling) {
                // --- РЕЖIM РЕЄСТРАЦІЇ ---
                // Ми отримали 'extra' (embedding) під час 'recognizeImage',
                // тепер реєструємо його під потрібним тегом.
                recognition.crop = scaledFace
                // **ВИПРАВЛЕНО:** Використовуємо '' для enrollmentTag, оскільки ми його перевірили
                detector?.register(enrollmentTag, recognition)

                // Успішна реєстрація
                authCallback?.onAuthenticationSucceeded(AuthenticationResult(null))
                stopAuthentication() // Завершуємо сесію

            } else {
                // --- РЕЖИМ АВТЕНТИФІКАЦІЇ ---
                val distance = recognition.distance
                if (distance != null && distance < CONFIDENCE_THRESHOLD) {
                    // Знайдено співпадіння! Але не поспішаємо...

                    positiveMatchCounter++ // <--- ЗБІЛЬШУЄМО ЛІЧИЛЬНИК

                    if (positiveMatchCounter >= 3) { // <--- ВИМАГАЄМО 3 ВДАЛИХ КАДРИ
                        // Успіх!
                        authCallback?.onAuthenticationSucceeded(AuthenticationResult(null))
                        stopAuthentication() // Завершуємо сесію
                    } else {
                        // Ще недостатньо кадрів, продовжуємо...
                        isProcessing = false
                    }

                } else {
                    // Обличчя не співпало
                    positiveMatchCounter = 0 // <--- СКИДАЄМО ЛІЧИЛЬНИК
                    isProcessing = false
                    // Не викликаємо onAuthenticationFailed() одразу, даємо шанс наступним кадрам
                }
            }

        } catch (e: Exception) {
            // Помилка під час обрізки або розпізнавання
            authCallback?.onAuthenticationError(
                CUSTOM_BIOMETRIC_ERROR_UNABLE_TO_PROCESS,
                "Face processing failed"
            )
            stopAuthentication() // Завершуємо сесію при будь-якій помилці
        }
    }

    private fun getFrontFacingCameraId(manager: CameraManager): String? {
        val cameraIds = manager.cameraIdList
        for (id in cameraIds) {
            val characteristics = manager.getCameraCharacteristics(id)
            val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
            if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                return id
            }
        }
        return null
    }
}