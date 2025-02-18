/* Copyright 2019 The TensorFlow Authors. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
==============================================================================*/

package dev.skomlach.biometric.custom.face.tf.tflite;

import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.RectF;
import android.os.AsyncTask;
import android.util.Base64;
import android.util.Log;
import android.util.Pair;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import dev.skomlach.common.storage.SharedPreferenceProvider;

/**
 * Wrapper for frozen detection models trained using the Tensorflow Object Detection API:
 * - https://github.com/tensorflow/models/tree/master/research/object_detection
 * where you can find the training code.
 * <p>
 * To use pretrained models in the API or convert to TF Lite models, please see docs for details:
 * - https://github.com/tensorflow/models/blob/master/research/object_detection/g3doc/detection_model_zoo.md
 * - https://github.com/tensorflow/models/blob/master/research/object_detection/g3doc/running_on_mobile_tensorflowlite.md#running-our-model-on-android
 */
public class TFLiteObjectDetectionAPIModel
        implements SimilarityClassifier {
    private static final String PREF_NAME = "registered";

    //private static final int OUTPUT_SIZE = 512;
    private static final int OUTPUT_SIZE = 192;

    // Only return this many results.
    private static final int NUM_DETECTIONS = 1;

    // Float model
    private static final float IMAGE_MEAN = 128.0f;
    private static final float IMAGE_STD = 128.0f;

    // Number of threads in the java app
    private static final int NUM_THREADS = 4;
    // Pre-allocated buffers.
    private final Vector<String> labels = new Vector<String>();
    private final HashMap<String, Recognition> registered = new HashMap<>();
    private boolean isModelQuantized;
    // Config values.
    private int inputSize;
    private int[] intValues;
    // outputLocations: array of shape [Batchsize, NUM_DETECTIONS,4]
    // contains the location of detected boxes
    private float[][][] outputLocations;
    // outputClasses: array of shape [Batchsize, NUM_DETECTIONS]
    // contains the classes of detected boxes
    private float[][] outputClasses;
    // outputScores: array of shape [Batchsize, NUM_DETECTIONS]
    // contains the scores of detected boxes
    private float[][] outputScores;
    // numDetections: array of shape [Batchsize]
    // contains the number of detected boxes
    private float[] numDetections;
    private float[][] embeedings;
    private ByteBuffer imgData;
    private Interpreter tfLite;
    // Face Mask Detector Output
    private float[][] output;

    private TFLiteObjectDetectionAPIModel() {
        try {
            SharedPreferences sharedPreferences = SharedPreferenceProvider.INSTANCE.getPreferences("biometric_tf");
            String jsonString = sharedPreferences.getString(PREF_NAME, null);
            JSONObject jsonObjectRoot = jsonString == null ? new JSONObject() : new JSONObject(jsonString);
            Iterator<String> keys = jsonObjectRoot.keys();
            while (keys.hasNext()) {
                String name = keys.next();
                try {
                    JSONObject jsonObject = jsonObjectRoot.getJSONObject(name);
                    Recognition recognition = json2recognition(jsonObject);
                    registered.put(name, recognition);
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    /** Memory-map the model file in Assets. */
    private static MappedByteBuffer loadModelFile(AssetManager assets, String modelFilename)
            throws IOException {
        FileInputStream inputStream = null;
        FileChannel fileChannel = null;
        AssetFileDescriptor fileDescriptor = null;
        try {
            fileDescriptor = assets.openFd(modelFilename);
            inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
            fileChannel = inputStream.getChannel();
            long startOffset = fileDescriptor.getStartOffset();
            long declaredLength = fileDescriptor.getDeclaredLength();

            return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
        } finally {
            if (fileChannel != null) {
                fileChannel.close();
            }
            if (inputStream != null) {
                inputStream.close();
            }
            if (fileDescriptor != null) {
                fileDescriptor.close();
            }
        }
    }

    /**
     * Initializes a native TensorFlow session for classifying images.
     *
     * @param assetManager  The asset manager to be used to load assets.
     * @param modelFilename The filepath of the model GraphDef protocol buffer.
     * @param labelFilename The filepath of label file for classes.
     * @param inputSize     The size of image input
     * @param isQuantized   Boolean representing model is quantized or not
     */
    public static SimilarityClassifier create(
            final AssetManager assetManager,
            final String modelFilename,
            final String labelFilename,
            final int inputSize,
            final boolean isQuantized)
            throws IOException {

        final TFLiteObjectDetectionAPIModel d = new TFLiteObjectDetectionAPIModel();

        String actualFilename = labelFilename.split("file:///android_asset/")[1];
        InputStream labelsInput = assetManager.open(actualFilename);
        BufferedReader br = new BufferedReader(new InputStreamReader(labelsInput));
        String line;
        while ((line = br.readLine()) != null) {
            Log.w("SimilarityClassifier", line);
            d.labels.add(line);
        }
        br.close();

        d.inputSize = inputSize;

        try {
            d.tfLite = new Interpreter(loadModelFile(assetManager, modelFilename));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        d.isModelQuantized = isQuantized;
        // Pre-allocate buffers.
        int numBytesPerChannel;
        if (isQuantized) {
            numBytesPerChannel = 1; // Quantized
        } else {
            numBytesPerChannel = 4; // Floating point
        }
        d.imgData = ByteBuffer.allocateDirect(d.inputSize * d.inputSize * 3 * numBytesPerChannel);
        d.imgData.order(ByteOrder.nativeOrder());
        d.intValues = new int[d.inputSize * d.inputSize];

//        d.tfLite.setNumThreads(NUM_THREADS);
        d.outputLocations = new float[1][NUM_DETECTIONS][4];
        d.outputClasses = new float[1][NUM_DETECTIONS];
        d.outputScores = new float[1][NUM_DETECTIONS];
        d.numDetections = new float[1];
        return d;
    }

    private Recognition json2recognition(JSONObject jsonObject) throws JSONException {
        final String id = jsonObject.getString("id");
        final String title = jsonObject.getString("title");
        final float distance = (float) jsonObject.getDouble("distance");
        JSONObject rect = jsonObject.getJSONObject("location");

        final RectF location = new RectF(
                (float) rect.getDouble("left"),
                (float) rect.getDouble("top"),
                (float) rect.getDouble("right"),
                (float) rect.getDouble("bottom"));

        Recognition recognition = new Recognition(id, title, distance, location);
        recognition.setColor(jsonObject.getInt("color"));

        if (jsonObject.has("extra")) {
            JSONArray top = jsonObject.getJSONArray("extra");
            float[][] array = new float[top.length()][];
            for (int i = 0; i < top.length(); i++) {
                JSONArray inner = top.getJSONArray(i);
                float[] innerArray = new float[inner.length()];
                for (int j = 0; j < inner.length(); j++) {
                    innerArray[j] = (float) inner.getDouble(j);
                }
                array[i] = innerArray;
            }
            recognition.setExtra(array);
        }
        if (jsonObject.has("crop")) {
            String base64Bitmap = jsonObject.getString("crop");
            byte[] bytes = Base64.decode(base64Bitmap, Base64.DEFAULT);
            Bitmap bm = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            recognition.setCrop(bm);
        }
        return recognition;
    }

    private JSONObject recognition2json(Recognition rec) throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("id", rec.getId());
        jsonObject.put("color", rec.getColor());

        if (rec.getLocation() != null) {
            JSONObject rect = new JSONObject();
            rect.put("top", rec.getLocation().top);
            rect.put("left", rec.getLocation().left);
            rect.put("bottom", rec.getLocation().bottom);
            rect.put("right", rec.getLocation().right);
            jsonObject.put("location", rect);
        }
        jsonObject.put("distance", (double) rec.getDistance());
        float[][] extra = (float[][]) rec.getExtra();
        JSONArray topArray = new JSONArray();
        for (int i = 0; i < extra.length; i++) {
            float[] top = extra[i];
            JSONArray innerArray = new JSONArray();
            for (int j = 0; j < top.length; j++) {
                float inner = extra[i][j];
                innerArray.put(inner);
            }
            topArray.put(innerArray);
        }
        jsonObject.put("extra", topArray);
        jsonObject.put("title", rec.getTitle());

        Bitmap crop = rec.getCrop();
        if (crop != null) {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            crop.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream);
            jsonObject.put("crop", Base64.encodeToString(byteArrayOutputStream.toByteArray(), Base64.DEFAULT));
        }
        return jsonObject;
    }

    public void register(String name, Recognition rec) {
        registered.put(name, rec);
        AsyncTask.THREAD_POOL_EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    SharedPreferences sharedPreferences = SharedPreferenceProvider.INSTANCE.getPreferences("biometric_tf");
                    String jsonString = sharedPreferences.getString(PREF_NAME, null);
                    JSONObject jsonObjectRoot = jsonString == null ? new JSONObject() : new JSONObject(jsonString);

                    jsonObjectRoot.put(name, recognition2json(rec));
                    sharedPreferences.edit().putString(PREF_NAME, jsonObjectRoot.toString()).apply();
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        });
    }

    // looks for the nearest embeeding in the dataset (using L2 norm)
    // and retrurns the pair <id, distance>
    private Pair<String, Float> findNearest(float[] emb) {

        Pair<String, Float> ret = null;
        for (Map.Entry<String, Recognition> entry : registered.entrySet()) {
            final String name = entry.getKey();
            final float[] knownEmb = ((float[][]) entry.getValue().getExtra())[0];

            float distance = 0;
            for (int i = 0; i < emb.length; i++) {
                float diff = emb[i] - knownEmb[i];
                distance += diff * diff;
            }
            distance = (float) Math.sqrt(distance);
            if (ret == null || distance < ret.second) {
                ret = new Pair<>(name, distance);
            }
        }

        return ret;
    }

    @Override
    public List<Recognition> recognizeImage(final Bitmap bitmap, boolean storeExtra) {
        // Log this method so that it can be analyzed with systrace.

        // Preprocess the image data from 0-255 int to normalized float based
        // on the provided parameters.
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());

        imgData.rewind();
        for (int i = 0; i < inputSize; ++i) {
            for (int j = 0; j < inputSize; ++j) {
                int pixelValue = intValues[i * inputSize + j];
                if (isModelQuantized) {
                    // Quantized model
                    imgData.put((byte) ((pixelValue >> 16) & 0xFF));
                    imgData.put((byte) ((pixelValue >> 8) & 0xFF));
                    imgData.put((byte) (pixelValue & 0xFF));
                } else { // Float model
                    imgData.putFloat((((pixelValue >> 16) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                    imgData.putFloat((((pixelValue >> 8) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                    imgData.putFloat(((pixelValue & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                }
            }
        }

        Object[] inputArray = {imgData};

// Here outputMap is changed to fit the Face Mask detector
        Map<Integer, Object> outputMap = new HashMap<>();

        embeedings = new float[1][OUTPUT_SIZE];
        outputMap.put(0, embeedings);

        // Run the inference call.

        //tfLite.runForMultipleInputsOutputs(inputArray, outputMapBack);
        tfLite.runForMultipleInputsOutputs(inputArray, outputMap);

//    String res = "[";
//    for (int i = 0; i < embeedings[0].length; i++) {
//      res += embeedings[0][i];
//      if (i < embeedings[0].length - 1) res += ", ";
//    }
//    res += "]";

        float distance = Float.MAX_VALUE;
        String id = "0";
        String label = "?";

        if (!registered.isEmpty()) {
            //LOGGER.i("dataset SIZE: " + registered.size());
            final Pair<String, Float> nearest = findNearest(embeedings[0]);
            if (nearest != null) {

                final String name = nearest.first;
                label = name;
                distance = nearest.second;

                Log.e(getClass().getSimpleName(), "nearest: " + name + " - distance: " + distance);
            }
        }

        final int numDetectionsOutput = 1;
        final ArrayList<Recognition> recognitions = new ArrayList<>(numDetectionsOutput);
        Recognition rec = new Recognition(
                id,
                label,
                distance,
                new RectF());

        recognitions.add(rec);

        if (storeExtra) {
            rec.setExtra(embeedings);
        }
        return recognitions;
    }

}
