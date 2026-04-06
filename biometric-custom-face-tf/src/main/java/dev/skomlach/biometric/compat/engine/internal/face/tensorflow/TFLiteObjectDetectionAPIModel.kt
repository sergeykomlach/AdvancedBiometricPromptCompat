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
package dev.skomlach.biometric.compat.engine.internal.face.tensorflow

import android.content.res.AssetFileDescriptor
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.os.AsyncTask
import android.util.Base64
import android.util.Log
import android.util.Pair
import androidx.core.content.edit
import dev.skomlach.biometric.custom.face.tf.BuildConfig
import dev.skomlach.common.contextprovider.AndroidContext
import dev.skomlach.common.logging.LogCat
import dev.skomlach.common.storage.SharedPreferenceProvider.getProtectedPreferences
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.sqrt

class TFLiteObjectDetectionAPIModel private constructor() : SimilarityClassifier {
    companion object {
        const val storageName = "tf_storage"
        private const val PREF_NAME = "registered"
        private const val OUTPUT_SIZE = 192
        private const val IMAGE_MEAN = 128.0f
        private const val IMAGE_STD = 128.0f

        @Throws(IOException::class)
        private fun loadModelFile(assets: AssetManager, modelFilename: String): MappedByteBuffer {
            var inputStream: FileInputStream? = null
            var fileChannel: FileChannel? = null
            var fileDescriptor: AssetFileDescriptor? = null
            try {
                fileDescriptor = assets.openFd(modelFilename)
                inputStream = FileInputStream(fileDescriptor.fileDescriptor)
                fileChannel = inputStream.channel
                val startOffset = fileDescriptor.startOffset
                val declaredLength = fileDescriptor.declaredLength
                return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
            } finally {
                fileChannel?.close()
                inputStream?.close()
                fileDescriptor?.close()
            }
        }

        @Throws(IOException::class)
        fun create(
            assetManager: AssetManager,
            modelFilename: String,
            inputSize: Int,
            isQuantized: Boolean,
            options: Interpreter.Options?
        ): SimilarityClassifier {
            val model = TFLiteObjectDetectionAPIModel()
            model.inputSize = inputSize
            model.isModelQuantized = isQuantized
            model.tfLite = Interpreter(loadModelFile(assetManager, modelFilename), options)

            val numBytesPerChannel = if (isQuantized) 1 else 4
            model.imgData = ByteBuffer.allocateDirect(inputSize * inputSize * 3 * numBytesPerChannel)
            model.imgData.order(ByteOrder.nativeOrder())
            model.intValues = IntArray(inputSize * inputSize)
            model.embeddings = Array(1) { FloatArray(OUTPUT_SIZE) }
            model.outputMap[0] = model.embeddings
            return model
        }
    }

    private val registered: HashMap<String?, SimilarityClassifier.Recognition?> by lazy {
        val map = HashMap<String?, SimilarityClassifier.Recognition?>()
        try {
            val sharedPreferences = getProtectedPreferences(storageName)
            val jsonString = sharedPreferences.getString(PREF_NAME, null)
            val jsonObjectRoot = if (jsonString == null) JSONObject() else JSONObject(jsonString)
            val keys = jsonObjectRoot.keys()
            while (keys.hasNext()) {
                val name = keys.next()
                try {
                    val jsonObject = jsonObjectRoot.getJSONObject(name)
                    val recognition = json2recognition(jsonObject)
                    map[name] = recognition
                } catch (e: Throwable) {
                    LogCat.logException(e)
                }
            }
        } catch (e: Throwable) {
            LogCat.logException(e)
        }
        Log.e(javaClass.simpleName, "registered: size ${map.size}")
        map
    }

    private var isModelQuantized = false
    private var inputSize = 0
    private var intValues: IntArray = IntArray(0)
    private var embeddings: Array<FloatArray> = emptyArray()
    private var imgData: ByteBuffer = ByteBuffer.allocate(0)
    private var tfLite: Interpreter? = null
    private val outputMap: MutableMap<Int, Any> = HashMap(1)

    @Throws(JSONException::class)
    private fun json2recognition(jsonObject: JSONObject): SimilarityClassifier.Recognition {
        val id = jsonObject.getString("id")
        val title = jsonObject.getString("title")
        val distance = jsonObject.getDouble("distance").toFloat()
        val rect = jsonObject.getJSONObject("location")

        val location = RectF(
            rect.getDouble("left").toFloat(),
            rect.getDouble("top").toFloat(),
            rect.getDouble("right").toFloat(),
            rect.getDouble("bottom").toFloat()
        )

        val recognition = SimilarityClassifier.Recognition(id, title, distance, location)
        if (jsonObject.has("extra")) {
            val top = jsonObject.getJSONArray("extra")
            val array = arrayOfNulls<FloatArray>(top.length())
            for (i in 0 until top.length()) {
                val inner = top.getJSONArray(i)
                val innerArray = FloatArray(inner.length())
                for (j in 0 until inner.length()) {
                    innerArray[j] = inner.getDouble(j).toFloat()
                }
                array[i] = innerArray
            }
            recognition.extra = array
        }
        if (jsonObject.has("crop")) {
            val base64Bitmap = jsonObject.getString("crop")
            val bytes = Base64.decode(base64Bitmap, Base64.DEFAULT)
            recognition.crop = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
        return recognition
    }

    @Throws(JSONException::class)
    private fun recognition2json(rec: SimilarityClassifier.Recognition): JSONObject {
        val jsonObject = JSONObject()
        jsonObject.put("id", rec.id)
        val rect = JSONObject()
        rect.put("top", rec.getLocation().top.toDouble())
        rect.put("left", rec.getLocation().left.toDouble())
        rect.put("bottom", rec.getLocation().bottom.toDouble())
        rect.put("right", rec.getLocation().right.toDouble())
        jsonObject.put("location", rect)
        jsonObject.put("distance", rec.distance?.toDouble())
        val extra = rec.extra as? Array<FloatArray>
        if (extra != null) {
            val topArray = JSONArray()
            for (i in extra.indices) {
                val innerArray = JSONArray()
                for (j in extra[i].indices) {
                    innerArray.put(extra[i][j].toDouble())
                }
                topArray.put(innerArray)
            }
            jsonObject.put("extra", topArray)
        }
        jsonObject.put("title", rec.title)

        rec.crop?.let { crop ->
            val byteArrayOutputStream = ByteArrayOutputStream()
            crop.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream)
            jsonObject.put(
                "crop",
                Base64.encodeToString(byteArrayOutputStream.toByteArray(), Base64.DEFAULT)
            )
        }
        return jsonObject
    }

    override fun registeredCount(): Int = registered.size
    override fun hasRegistered(): Boolean = registered.isNotEmpty()
    override fun getEnrolls(): Set<String> = registered.keys.filterNotNull().toSet()

    override fun delete(name: String?) {
        if (name == null) {
            if (BuildConfig.DEBUG) {
                registered.values.toMutableList().forEach { rec ->
                    if (rec != null) {
                        ImageUtils.deleteBitmap(AndroidContext.appContext, "${rec.title}-${rec.id}.png")
                    }
                }
            }
            registered.clear()
            try {
                getProtectedPreferences(storageName).edit { clear() }
            } catch (e: Throwable) {
                LogCat.logException(e)
            }
            return
        }

        if (BuildConfig.DEBUG) {
            registered[name]?.let { rec ->
                ImageUtils.deleteBitmap(AndroidContext.appContext, "${rec.title}-${rec.id}.png")
            }
        }
        registered.remove(name)
        try {
            val sharedPreferences = getProtectedPreferences(storageName)
            val jsonString = sharedPreferences.getString(PREF_NAME, null)
            val jsonObjectRoot = if (jsonString == null) JSONObject() else JSONObject(jsonString)
            jsonObjectRoot.remove(name)
            sharedPreferences.edit { putString(PREF_NAME, jsonObjectRoot.toString()) }
        } catch (e: Throwable) {
            LogCat.logException(e)
        }
    }

    override fun register(name: String, rec: SimilarityClassifier.Recognition) {
        registered[name] = rec
        val sharedPreferences = getProtectedPreferences(storageName)
        val jsonString = sharedPreferences.getString(PREF_NAME, null)
        val jsonObjectRoot = if (jsonString == null) JSONObject() else JSONObject(jsonString)
        jsonObjectRoot.put(name, recognition2json(rec))
        sharedPreferences.edit { putString(PREF_NAME, jsonObjectRoot.toString()) }

        if (BuildConfig.DEBUG) {
            AsyncTask.THREAD_POOL_EXECUTOR.execute {
                try {
                    ImageUtils.saveBitmap(
                        AndroidContext.appContext,
                        rec.crop ?: return@execute,
                        "${rec.title}-${rec.id}.png"
                    )
                } catch (e: Throwable) {
                    LogCat.logException(e)
                }
            }
        }
    }

    private fun findNearest(emb: FloatArray): Pair<String, Float>? {
        var ret: Pair<String, Float>? = null
        for ((name, recognition) in registered.entries) {
            val knownEmb = (recognition?.extra as? Array<FloatArray>)?.firstOrNull()
            if (knownEmb != null && name != null) {
                var distance = 0f
                for (i in emb.indices) {
                    val diff = emb[i] - knownEmb[i]
                    distance += diff * diff
                }
                distance = sqrt(distance.toDouble()).toFloat()
                if (ret == null || distance < ret.second) {
                    ret = Pair(name, distance)
                }
            }
        }
        return ret
    }

    override fun recognizeImage(
        bitmap: Bitmap,
        storeExtra: Boolean
    ): MutableList<SimilarityClassifier.Recognition> {
        bitmap.getPixels(
            intValues,
            0,
            bitmap.width,
            0,
            0,
            bitmap.width,
            bitmap.height
        )

        imgData.rewind()
        for (i in 0 until inputSize) {
            for (j in 0 until inputSize) {
                val pixelValue = intValues[i * inputSize + j]
                if (isModelQuantized) {
                    imgData.put(((pixelValue shr 16) and 0xFF).toByte())
                    imgData.put(((pixelValue shr 8) and 0xFF).toByte())
                    imgData.put((pixelValue and 0xFF).toByte())
                } else {
                    imgData.putFloat((((pixelValue shr 16) and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
                    imgData.putFloat((((pixelValue shr 8) and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
                    imgData.putFloat(((pixelValue and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
                }
            }
        }

        embeddings[0].fill(0f)
        tfLite?.runForMultipleInputsOutputs(arrayOf<Any>(imgData), outputMap)

        var distance = Float.MAX_VALUE
        var recognitionId = "unknown"
        var label: String? = "face"

        if (registered.isNotEmpty()) {
            val nearest = findNearest(embeddings[0])
            if (nearest != null) {
                recognitionId = nearest.first
                label = nearest.first
                distance = nearest.second
                Log.e(javaClass.simpleName, "nearest: $label - distance: $distance")
            }
        }

        val recognitions = ArrayList<SimilarityClassifier.Recognition>(1)
        val rec = SimilarityClassifier.Recognition(recognitionId, label, distance, RectF())
        if (storeExtra) {
            rec.extra = embeddings.map { it.copyOf() }.toTypedArray()
        }
        recognitions.add(rec)
        return recognitions
    }

    fun close() {
        try {
            tfLite?.close()
        } catch (t: Throwable) {
            LogCat.logException(t)
        } finally {
            tfLite = null
        }
    }
}
