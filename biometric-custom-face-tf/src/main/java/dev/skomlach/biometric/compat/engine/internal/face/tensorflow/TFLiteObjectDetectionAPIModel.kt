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

/**
 * Wrapper for frozen detection models trained using the Tensorflow Object Detection API:
 * - https://github.com/tensorflow/models/tree/master/research/object_detection
 * where you can find the training code.
 *
 *
 * To use pretrained models in the API or convert to TF Lite models, please see docs for details:
 * - https://github.com/tensorflow/models/blob/master/research/object_detection/g3doc/detection_model_zoo.md
 * - https://github.com/tensorflow/models/blob/master/research/object_detection/g3doc/running_on_mobile_tensorflowlite.md#running-our-model-on-android
 */
class TFLiteObjectDetectionAPIModel
private constructor() : SimilarityClassifier {
    companion object {
        const val storageName = "tf_storage"
        private const val PREF_NAME = "registered"

        //private static final int OUTPUT_SIZE = 512;
        private const val OUTPUT_SIZE = 192

        // Only return this many results.
        private const val NUM_DETECTIONS = 1

        // Float model
        private const val IMAGE_MEAN = 128.0f
        private const val IMAGE_STD = 128.0f


        /** Memory-map the model file in Assets.  */
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

        /**
         * Initializes a native TensorFlow session for classifying images.
         *
         * @param assetManager  The asset manager to be used to load assets.
         * @param modelFilename The filepath of the model GraphDef protocol buffer.
         * @param inputSize     The size of image input
         * @param isQuantized   Boolean representing model is quantized or not
         */
        @Throws(IOException::class)
        fun create(
            assetManager: AssetManager,
            modelFilename: String,
            inputSize: Int,
            isQuantized: Boolean,
            options: Interpreter.Options?
        ): SimilarityClassifier {
            val d = TFLiteObjectDetectionAPIModel()


            d.inputSize = inputSize

            try {
                d.tfLite = Interpreter(loadModelFile(assetManager, modelFilename), options)
            } catch (e: Exception) {
                throw RuntimeException(e)
            }

            d.isModelQuantized = isQuantized
            // Pre-allocate buffers.
            val numBytesPerChannel = if (isQuantized) {
                1 // Quantized
            } else {
                4 // Floating point
            }
            d.imgData =
                ByteBuffer.allocateDirect(d.inputSize * d.inputSize * 3 * numBytesPerChannel)
            d.imgData.order(ByteOrder.nativeOrder())
            d.intValues = IntArray(d.inputSize * d.inputSize)
            return d
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

    // Config values.
    private var inputSize = 0
    private var intValues: IntArray = IntArray(0)

    private var embeedings: Array<FloatArray> = emptyArray<FloatArray>()
    private var imgData: ByteBuffer = ByteBuffer.allocate(0)
    private var tfLite: Interpreter? = null

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
            for (i in 0..<top.length()) {
                val inner = top.getJSONArray(i)
                val innerArray = FloatArray(inner.length())
                for (j in 0..<inner.length()) {
                    innerArray[j] = inner.getDouble(j).toFloat()
                }
                array[i] = innerArray
            }
            recognition.extra = (array)
        }
        if (jsonObject.has("crop")) {
            val base64Bitmap = jsonObject.getString("crop")
            val bytes = Base64.decode(base64Bitmap, Base64.DEFAULT)
            val bm = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            recognition.crop = (bm)
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
        val extra = rec.extra as Array<FloatArray>
        val topArray = JSONArray()
        for (i in extra.indices) {
            val top = extra[i]
            val innerArray = JSONArray()
            for (j in top.indices) {
                val inner = extra[i][j]
                innerArray.put(inner.toDouble())
            }
            topArray.put(innerArray)
        }
        jsonObject.put("extra", topArray)
        jsonObject.put("title", rec.title)

        val crop = rec.crop
        if (crop != null) {
            val byteArrayOutputStream = ByteArrayOutputStream()
            crop.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream)
            jsonObject.put(
                "crop",
                Base64.encodeToString(byteArrayOutputStream.toByteArray(), Base64.DEFAULT)
            )
        }
        return jsonObject
    }
    override fun registeredCount(): Int {
        Log.i(javaClass.simpleName, "registeredCount: size ${registered.size}")
        return registered.size
    }
    override fun hasRegistered(): Boolean {
        Log.i(javaClass.simpleName, "hasRegistered:  ${registered.isNotEmpty()}")
        return registered.isNotEmpty()
    }

    override fun delete(name: String?) {
        if (name == null) {
            if (BuildConfig.DEBUG) {
                registered.values.toMutableList().forEach { rec ->
                    if (rec != null) ImageUtils.deleteBitmap(
                        AndroidContext.appContext,
                        "${rec.title}-${rec.id}.png"
                    )
                }
            }
            registered.clear()
            try {
                getProtectedPreferences(storageName).edit { clear() }
            } catch (e: Throwable) {
                LogCat.logException(e)
            }
        } else {
            if (BuildConfig.DEBUG) {
                registered[name]?.let { rec ->
                    ImageUtils.deleteBitmap(AndroidContext.appContext, "${rec.title}-${rec.id}.png")
                }
            }
            registered.remove(name)
            try {
                val sharedPreferences = getProtectedPreferences(storageName)
                val jsonString = sharedPreferences.getString(PREF_NAME, null)
                val jsonObjectRoot =
                    if (jsonString == null) JSONObject() else JSONObject(jsonString)
                jsonObjectRoot.remove(name)
                sharedPreferences.edit { putString(PREF_NAME, jsonObjectRoot.toString()) }
            } catch (e: Throwable) {
                LogCat.logException(e)
            }
        }
        Log.e(javaClass.simpleName, "registered: size ${registered.size}")
    }


    override fun register(name: String, rec: SimilarityClassifier.Recognition) {
        registered[name] = rec
        val sharedPreferences = getProtectedPreferences(storageName)
        val jsonString = sharedPreferences.getString(PREF_NAME, null)
        val jsonObjectRoot =
            if (jsonString == null) JSONObject() else JSONObject(jsonString)

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
        Log.e(javaClass.simpleName, "registered: size ${registered.size}")
    }

    // looks for the nearest embeeding in the dataset (using L2 norm)
    // and returns the pair <id, distance>
    private fun findNearest(emb: FloatArray): Pair<String, Float>? {
        var ret: Pair<String, Float>? = null
        for (entry in registered.entries) {
            val name = entry.key
            val knownEmb = (entry.value?.extra as? Array<FloatArray>)?.get(0)


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
        // Log this method so that it can be analyzed with systrace.

        // Preprocess the image data from 0-255 int to normalized float based
        // on the provided parameters.

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
        for (i in 0..<inputSize) {
            for (j in 0..<inputSize) {
                val pixelValue = intValues[i * inputSize + j]
                if (isModelQuantized) {
                    // Quantized model
                    imgData.put(((pixelValue shr 16) and 0xFF).toByte())
                    imgData.put(((pixelValue shr 8) and 0xFF).toByte())
                    imgData.put((pixelValue and 0xFF).toByte())
                } else { // Float model
                    imgData.putFloat((((pixelValue shr 16) and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
                    imgData.putFloat((((pixelValue shr 8) and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
                    imgData.putFloat(((pixelValue and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
                }
            }
        }

        val inputArray = arrayOf<Any?>(imgData)

        // Here outputMap is changed to fit the Face Mask detector
        val outputMap: MutableMap<Int?, Any?> = HashMap<Int?, Any?>()

        embeedings = Array<FloatArray>(1) { FloatArray(OUTPUT_SIZE) }
        outputMap[0] = embeedings

        // Run the inference call.
        tfLite?.runForMultipleInputsOutputs(inputArray, outputMap)

        var distance = Float.MAX_VALUE
        val id = "0"
        var label: String? = "face"

        if (!registered.isEmpty()) {
            //LOGGER.i("dataset SIZE: " + registered.size());
            val nearest = findNearest(embeedings[0])
            if (nearest != null) {
                val name = nearest.first
                label = name
                distance = nearest.second

                Log.e(javaClass.simpleName, "nearest: $name - distance: $distance")
            }
        }

        val numDetectionsOutput = 1

        val recognitions = ArrayList<SimilarityClassifier.Recognition>(numDetectionsOutput)
        val rec = SimilarityClassifier.Recognition(
            id,
            label,
            distance,
            RectF()
        )

        recognitions.add(rec)

        if (storeExtra) {
            rec.extra = (embeedings)
        }
        return recognitions
    }


}