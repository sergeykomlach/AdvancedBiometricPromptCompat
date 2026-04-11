/*
 *  Copyright (c) 2026 Sergey Komlach aka Salat-Cx65; Original project https://github.com/Salat-Cx65/AdvancedBiometricPromptCompat
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

package dev.skomlach.biometric.compat.engine.internal.face.tensorflow

import android.content.res.AssetManager
import android.graphics.Bitmap
import androidx.core.graphics.scale
import dev.skomlach.common.logging.LogCat
import dev.skomlach.common.misc.ExecutorHelper
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.ArrayDeque

class DeepfakeFrameSequenceDetector private constructor(
    assetManager: AssetManager,
    modelPath: String,
    selection: TfBackendSelection,
    private val sequenceLength: Int = DEFAULT_SEQUENCE_LENGTH,
    private val inputSize: Int = DEFAULT_INPUT_SIZE,
    private val threshold: Float = DEFAULT_THRESHOLD,
    private val normalizeToUnitRange: Boolean = true
) {

    data class DetectionResult(
        val isDeepfake: Boolean,
        val score: Float,
        val confidence: Float,
        val ready: Boolean,
        val windowSize: Int
    )

    companion object {
        private const val TAG = "DeepfakeSequenceDetector"
        const val DEFAULT_MODEL_FILE = "tf_bio/deepfake_detection_model.tflite"
        const val DEFAULT_SEQUENCE_LENGTH = 10
        const val DEFAULT_INPUT_SIZE = 224
        const val DEFAULT_THRESHOLD = 0.5f

        @Throws(IOException::class)
        private fun loadModelFile(assets: AssetManager, modelFilename: String): MappedByteBuffer {
            assets.openFd(modelFilename).use { fileDescriptor ->
                FileInputStream(fileDescriptor.fileDescriptor).use { inputStream ->
                    val fileChannel = inputStream.channel
                    val startOffset = fileDescriptor.startOffset
                    val declaredLength = fileDescriptor.declaredLength
                    return fileChannel.map(
                        FileChannel.MapMode.READ_ONLY,
                        startOffset,
                        declaredLength
                    )
                }
            }
        }

        fun create(
            assetManager: AssetManager,
            selection: TfBackendSelection,
            modelPath: String = DEFAULT_MODEL_FILE,
            sequenceLength: Int = DEFAULT_SEQUENCE_LENGTH,
            inputSize: Int = DEFAULT_INPUT_SIZE,
            threshold: Float = DEFAULT_THRESHOLD,
            normalizeToUnitRange: Boolean = true
        ): DeepfakeFrameSequenceDetector {
            return DeepfakeFrameSequenceDetector(
                assetManager = assetManager,
                modelPath = modelPath,
                selection = selection,
                sequenceLength = sequenceLength,
                inputSize = inputSize,
                threshold = threshold,
                normalizeToUnitRange = normalizeToUnitRange
            )
        }
    }

    private var interpreter: Interpreter? = null
    private val frameWindow = ArrayDeque<Bitmap>(sequenceLength)
    private val intValues = IntArray(inputSize * inputSize)
    private val inputBuffer: ByteBuffer = ByteBuffer.allocateDirect(
        1 * sequenceLength * inputSize * inputSize * 3 * 4
    ).apply {
        order(ByteOrder.nativeOrder())
    }
    private val output = Array(1) { FloatArray(1) }

    init {
        ExecutorHelper.startOnBackground {
            interpreter = Interpreter(
                loadModelFile(assetManager, modelPath),
                TfLiteBackendHelper.createOptions(selection)
            )
        }
    }

    @Synchronized
    fun clear() {
        while (frameWindow.isNotEmpty()) {
            val bmp = frameWindow.removeFirst()
            if (!bmp.isRecycled) bmp.recycle()
        }
        inputBuffer.clear()
    }

    @Synchronized
    fun addFrame(bitmap: Bitmap) {
        val prepared = if (bitmap.width == inputSize && bitmap.height == inputSize) {
            bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)
        } else {
            bitmap.scale(inputSize, inputSize)
        }
        if (frameWindow.size == sequenceLength) {
            val removed = frameWindow.removeFirst()
            if (!removed.isRecycled) removed.recycle()
        }
        frameWindow.addLast(prepared)
    }

    @Synchronized
    fun isReady(): Boolean = frameWindow.size >= sequenceLength

    @Synchronized
    fun predict(): DetectionResult {
        if (frameWindow.size < sequenceLength) {
            return DetectionResult(
                isDeepfake = false,
                score = 0f,
                confidence = 0f,
                ready = false,
                windowSize = frameWindow.size
            )
        }

        inputBuffer.rewind()

        for (frame in frameWindow) {
            frame.getPixels(intValues, 0, inputSize, 0, 0, inputSize, inputSize)
            for (pixel in intValues) {
                val r = ((pixel shr 16) and 0xFF).toFloat()
                val g = ((pixel shr 8) and 0xFF).toFloat()
                val b = (pixel and 0xFF).toFloat()
                if (normalizeToUnitRange) {
                    inputBuffer.putFloat(r / 255f)
                    inputBuffer.putFloat(g / 255f)
                    inputBuffer.putFloat(b / 255f)
                } else {
                    inputBuffer.putFloat(r)
                    inputBuffer.putFloat(g)
                    inputBuffer.putFloat(b)
                }
            }
        }

        output[0][0] = 0f
        val interpreter = interpreter ?: run {
            LogCat.logError(javaClass.simpleName, "Interpreter is not initialized")
            return DetectionResult(
                isDeepfake = false,
                score = 0f,
                confidence = 0f,
                ready = false,
                windowSize = frameWindow.size
            )
        }
        interpreter.run(inputBuffer, output)

        val score = output[0][0]
        val isDeepfake = score >= threshold
        val confidence = if (isDeepfake) score else 1f - score
        LogCat.log(TAG, "score=$score threshold=$threshold deepfake=$isDeepfake")
        return DetectionResult(
            isDeepfake = isDeepfake,
            score = score,
            confidence = confidence,
            ready = true,
            windowSize = frameWindow.size
        )
    }

    fun close() {
        try {
            clear()
        } catch (_: Throwable) {
        }
        try {
            interpreter?.close()
        } catch (t: Throwable) {
            LogCat.logException(t)
        } finally {
            interpreter = null
        }
    }
}
