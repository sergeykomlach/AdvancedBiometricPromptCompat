package dev.skomlach.biometric.compat.engine.internal.face.tensorflow

import android.content.res.AssetManager
import android.graphics.Bitmap
import androidx.core.graphics.scale
import dev.skomlach.common.logging.LogCat
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.abs

/**
 * Wrapper for AntiSpooginf detection
https://github.com/syaringan357/Android-MobileFaceNet-MTCNN-FaceAntiSpoofing/tree/master
 */

class FaceAntiSpoofing(assetManager: AssetManager) {

    companion object {
        private const val TAG = "FaceAntiSpoofing"
        private const val MODEL_FILE = "tf_bio/FaceAntiSpoofing.tflite"

        // Input image size for the model placeholder
        const val INPUT_IMAGE_SIZE: Int = 256

        // Threshold: values above this are considered attacks/spoofing
        const val THRESHOLD: Float = 0.2f

        // Route index observed during training
        const val ROUTE_INDEX: Int = 6

        // Laplace sampling threshold
        const val LAPLACE_THRESHOLD: Int = 50

        // Image clarity judgment threshold
        const val LAPLACIAN_THRESHOLD: Int = 1000
    }

    private val interpreter: Interpreter

    init {
        val options = Interpreter.Options()
        options.setNumThreads(4) // Use 4 threads for inference
        interpreter = Interpreter(loadModelFile(assetManager, MODEL_FILE), options)
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

    /**
     * Liveness Detection
     * @param bitmap Input face image
     * @return Score (Low score = Real face, High score = Spoof)
     */
    fun antiSpoofing(bitmap: Bitmap): Float {
        // 1. Resize image to 256x256 as required by the model
        val bitmapScale = bitmap.scale(INPUT_IMAGE_SIZE, INPUT_IMAGE_SIZE)

        // 2. Prepare ByteBuffer (Faster than multidimensional arrays)
        // Format: 1 (batch) * 256 (height) * 256 (width) * 3 (channels) * 4 (float bytes)
        val imgData = ByteBuffer.allocateDirect(1 * INPUT_IMAGE_SIZE * INPUT_IMAGE_SIZE * 3 * 4)
        imgData.order(ByteOrder.nativeOrder())

        // Normalize and fill buffer
        convertBitmapToByteBuffer(bitmapScale, imgData)

        // 3. Prepare Outputs
        // Identity: Classification Prediction (1x8)
        val clssPred = Array(1) { FloatArray(8) }
        // Identity_1: Leaf Node Mask (1x8)
        val leafNodeMask = Array(1) { FloatArray(8) }

        val outputs = HashMap<Int, Any>()
        // Note: Ensure strictly that output indices match your model.
        // Usually, 0 is classification, 1 is mask, but checking getOutputIndex is safer.
        val outputIndex0 = try {
            interpreter.getOutputIndex("Identity")
        } catch (e: Exception) {
            0
        }
        val outputIndex1 = try {
            interpreter.getOutputIndex("Identity_1")
        } catch (e: Exception) {
            1
        }

        outputs[outputIndex0] = clssPred
        outputs[outputIndex1] = leafNodeMask

        // 4. Run Inference
        val input = arrayOf<Any>(imgData)
        interpreter.runForMultipleInputsOutputs(input, outputs)

        // Log results for debugging
        LogCat.log(TAG, "ClssPred: ${clssPred[0].contentToString()}")
        LogCat.log(TAG, "LeafNodeMask: ${leafNodeMask[0].contentToString()}")

        return calculateLeafScore(clssPred, leafNodeMask)
    }

    private fun convertBitmapToByteBuffer(bitmap: Bitmap, imgData: ByteBuffer) {
        imgData.rewind()
        val intValues = IntArray(INPUT_IMAGE_SIZE * INPUT_IMAGE_SIZE)
        bitmap.getPixels(
            intValues, 0, bitmap.width, 0, 0,
            bitmap.width, bitmap.height
        )

        // Normalize to [0, 1]
        val imageStd = 255.0f

        for (pixelValue in intValues) {
            // Extract RGB and Normalize
            val r = ((pixelValue shr 16) and 0xFF) / imageStd
            val g = ((pixelValue shr 8) and 0xFF) / imageStd
            val b = (pixelValue and 0xFF) / imageStd

            imgData.putFloat(r)
            imgData.putFloat(g)
            imgData.putFloat(b)
        }
    }

    private fun calculateLeafScore(
        clssPred: Array<FloatArray>,
        leafNodeMask: Array<FloatArray>
    ): Float {
        var score = 0f
        for (i in 0..7) {
            score += abs(clssPred[0][i]) * leafNodeMask[0][i]
        }
        return score
    }

    /**
     * Laplacian algorithm to calculate image clarity (blur detection)
     * @param bitmap Input bitmap
     * @return Score (Higher means clearer/more edges)
     */
    fun laplacian(bitmap: Bitmap): Int {
        val bitmapScale = bitmap.scale(INPUT_IMAGE_SIZE, INPUT_IMAGE_SIZE)

        // Standard 3x3 Laplacian kernel
        //  0  1  0
        //  1 -4  1
        //  0  1  0

        val width = bitmapScale.width
        val height = bitmapScale.height
        val pixels = IntArray(width * height)
        bitmapScale.getPixels(pixels, 0, width, 0, 0, width, height)

        var score = 0

        // Loop through pixels excluding borders (1 pixel margin)
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                // Get grayscale values for the cross pattern (center, up, down, left, right)
                // Using manual grayscale conversion: 0.299R + 0.587G + 0.114B
                // Or simplified (R+G+B)/3 for speed, but let's stick to standard luminance for accuracy

                val center = getGreyValue(pixels[y * width + x])
                val up = getGreyValue(pixels[(y - 1) * width + x])
                val down = getGreyValue(pixels[(y + 1) * width + x])
                val left = getGreyValue(pixels[y * width + (x - 1)])
                val right = getGreyValue(pixels[y * width + (x + 1)])

                // Laplacian Kernel: 0*corners + 1*up + 1*down + 1*left + 1*right - 4*center
                val laplaceValue = up + down + left + right - (4 * center)

                // Check absolute value against threshold
                if (abs(laplaceValue) > LAPLACE_THRESHOLD) {
                    score++
                }
            }
        }
        return score
    }

    // Helper to extract luminance (grayscale) from ARGB int
    private fun getGreyValue(pixel: Int): Int {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        return (0.299 * r + 0.587 * g + 0.114 * b).toInt()
    }
}