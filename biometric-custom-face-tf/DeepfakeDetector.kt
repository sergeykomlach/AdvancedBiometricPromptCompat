package dev.skomlach.biometric.compat.engine.internal.face.tensorflow

import android.content.res.AssetManager
import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.imageclassifier.ImageClassifier
import dev.skomlach.common.contextprovider.AndroidContext
import org.tensorflow.lite.support.image.TensorImage
import java.io.FileInputStream
import java.io.IOException
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel


class DeepfakeDetector(
    val context: Context
) {
    private var imageClassifier: ImageClassifier? = null

    init {
        setupClassifier()
    }
    @Throws(IOException::class)
    private fun loadModelFile(assets: AssetManager, modelFilename: String): MappedByteBuffer {
        assets.openFd(modelFilename).use { fileDescriptor ->
            FileInputStream(fileDescriptor.fileDescriptor).use { inputStream ->
                val fileChannel = inputStream.channel
                val startOffset = fileDescriptor.startOffset
                val declaredLength = fileDescriptor.declaredLength
                return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
            }
        }
    }
    private fun setupClassifier() {
        try {
            imageClassifier = ImageClassifier.createFromBuffer(
                context,
                loadModelFile(context.assets, "tf_bio/deepfake_detection_model.tflite")
            )
        } catch (e: IllegalStateException) {
            e.printStackTrace()
        }
    }

    fun classify(bitmap: Bitmap): DetectionResult {
        if (imageClassifier == null) {
            setupClassifier()
        }

        // Конвертуємо Bitmap у формат, який розуміє TFLite
        val tensorImage = TensorImage.fromBitmap(bitmap)

        // Виконуємо розпізнавання
        val results = imageClassifier?.classify(tensorImage)

        // Логіка інтерпретації результатів
        val topResult = results?.firstOrNull()?.categories?.firstOrNull()

        return if (topResult != null) {
            DetectionResult(
                label = topResult.label, // Наприклад: "Real" або "Deepfake"
                confidence = topResult.score
            )
        } else {
            DetectionResult("Unknown", 0f)
        }
    }

    data class DetectionResult(val label: String, val confidence: Float)
}