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
 * Wrapper for AntiSpoofing detection
 * https://github.com/syaringan357/Android-MobileFaceNet-MTCNN-FaceAntiSpoofing/tree/master
 */
class FaceAntiSpoofing private constructor(
    private val interpreter: Interpreter
) {

    companion object {
        private const val TAG = "FaceAntiSpoofing"
        const val MODEL_FILE = "tf_bio/FaceAntiSpoofing.tflite"

        const val INPUT_IMAGE_SIZE: Int = 256
        const val THRESHOLD: Float = 0.2f
        const val ROUTE_INDEX: Int = 6
        const val LAPLACE_THRESHOLD: Int = 50
        const val LAPLACIAN_THRESHOLD: Int = 1000

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

        fun create(
            assetManager: AssetManager,
            selection: TfBackendSelection
        ): FaceAntiSpoofing {
            val interpreter = Interpreter(
                loadModelFile(assetManager, MODEL_FILE),
                TfLiteBackendHelper.createOptions(selection)
            )
            return FaceAntiSpoofing(interpreter)
        }
    }

    private val imgData: ByteBuffer = ByteBuffer.allocateDirect(
        1 * INPUT_IMAGE_SIZE * INPUT_IMAGE_SIZE * 3 * 4
    ).apply {
        order(ByteOrder.nativeOrder())
    }
    private val intValues = IntArray(INPUT_IMAGE_SIZE * INPUT_IMAGE_SIZE)
    private val clssPred = Array(1) { FloatArray(8) }
    private val leafNodeMask = Array(1) { FloatArray(8) }
    private val outputs = HashMap<Int, Any>(2)
    private val outputIndex0: Int by lazy {
        try {
            interpreter.getOutputIndex("Identity")
        } catch (_: Throwable) {
            0
        }
    }
    private val outputIndex1: Int by lazy {
        try {
            interpreter.getOutputIndex("Identity_1")
        } catch (_: Throwable) {
            1
        }
    }

    init {
        outputs[outputIndex0] = clssPred
        outputs[outputIndex1] = leafNodeMask
    }

    /**
     * Liveness Detection
     * @param bitmap Input face image
     * @return Score (Low score = Real face, High score = Spoof)
     */
    fun antiSpoofing(bitmap: Bitmap): Float {
        val bitmapScale = bitmap.scale(INPUT_IMAGE_SIZE, INPUT_IMAGE_SIZE)
        try {
            convertBitmapToByteBuffer(bitmapScale)
            clearOutputs()
            interpreter.runForMultipleInputsOutputs(arrayOf<Any>(imgData), outputs)
            LogCat.log(TAG, "ClssPred: ${clssPred[0].contentToString()}")
            LogCat.log(TAG, "LeafNodeMask: ${leafNodeMask[0].contentToString()}")
            return calculateLeafScore(clssPred, leafNodeMask)
        } finally {
            if (bitmapScale !== bitmap && !bitmapScale.isRecycled) {
                bitmapScale.recycle()
            }
        }
    }

    private fun clearOutputs() {
        java.util.Arrays.fill(clssPred[0], 0f)
        java.util.Arrays.fill(leafNodeMask[0], 0f)
    }

    private fun convertBitmapToByteBuffer(bitmap: Bitmap) {
        imgData.rewind()
        bitmap.getPixels(
            intValues, 0, bitmap.width, 0, 0,
            bitmap.width, bitmap.height
        )

        val imageStd = 255.0f
        for (pixelValue in intValues) {
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
        try {
            val width = bitmapScale.width
            val height = bitmapScale.height
            val pixels = IntArray(width * height)
            bitmapScale.getPixels(pixels, 0, width, 0, 0, width, height)

            var score = 0
            for (y in 1 until height - 1) {
                for (x in 1 until width - 1) {
                    val center = getGreyValue(pixels[y * width + x])
                    val up = getGreyValue(pixels[(y - 1) * width + x])
                    val down = getGreyValue(pixels[(y + 1) * width + x])
                    val left = getGreyValue(pixels[y * width + (x - 1)])
                    val right = getGreyValue(pixels[y * width + (x + 1)])
                    val laplaceValue = up + down + left + right - (4 * center)
                    if (abs(laplaceValue) > LAPLACE_THRESHOLD) {
                        score++
                    }
                }
            }
            return score
        } finally {
            if (bitmapScale !== bitmap && !bitmapScale.isRecycled) {
                bitmapScale.recycle()
            }
        }
    }

    private fun getGreyValue(pixel: Int): Int {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        return (0.299 * r + 0.587 * g + 0.114 * b).toInt()
    }

    fun close() {
        try {
            interpreter.close()
        } catch (t: Throwable) {
            LogCat.logException(t)
        }
    }
}
