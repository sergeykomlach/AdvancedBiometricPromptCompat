package dev.skomlach.biometric.compat.engine.internal.face.tensorflow.provider

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.HandlerThread
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetector
import java.io.IOException

class FakeAssetProvider(
    private val context: Context,
    private val assetPath: String
) : IFrameProvider {

    private var onFrame: ((Bitmap, List<Face>) -> Unit)? = null
    private var onError: ((Int, String) -> Unit)? = null
    private var backgroundHandler: Handler? = null
    private var mlKitDetector: FaceDetector? = null
    private var isRunning = false
    private val assetFiles = mutableListOf<String>()
    private var currentIndex = 0
    private var backgroundThread: HandlerThread? = null

    override fun start(
        faceDetector: FaceDetector,
        frameListener: (bitmap: Bitmap, faces: List<Face>) -> Unit,
        errorListener: (code: Int, message: String) -> Unit
    ) {
        if (backgroundThread == null) {
            backgroundThread = HandlerThread("FakeAssetProvider").apply {
                start()
                backgroundHandler = Handler(looper)
            }
        }
        this.mlKitDetector = faceDetector
        this.onFrame = frameListener
        this.onError = errorListener
        this.isRunning = true

        try {
            context.assets.list(assetPath)?.let { assetFiles.addAll(it) }
            if (assetFiles.isNotEmpty()) {
                postNextFrame()
            } else {
                errorListener(0, "No assets found")
            }
        } catch (e: IOException) {
            errorListener(0, e.message ?: "Asset Error")
        }
    }

    private fun postNextFrame() {
        if (!isRunning) return

        backgroundHandler?.postDelayed({
            if (!isRunning) return@postDelayed

            if (currentIndex >= assetFiles.size) currentIndex = 0
            val fileName = assetFiles[currentIndex++]

            try {
                context.assets.open("$assetPath/$fileName").use { stream ->
                    val bitmap = BitmapFactory.decodeStream(stream)
                    if (bitmap != null) {
                        val inputImage = InputImage.fromBitmap(bitmap, 0)
                        mlKitDetector?.process(inputImage)
                            ?.addOnSuccessListener { faces ->
                                onFrame?.invoke(bitmap, faces)
                                postNextFrame()
                            }
                            ?.addOnFailureListener { postNextFrame() }
                    } else {
                        postNextFrame()
                    }
                }
            } catch (e: Exception) {
                postNextFrame()
            }
        }, 500) // 500ms delay to simulate real time and not spam the log
    }

    override fun stop() {
        isRunning = false
        backgroundHandler?.removeCallbacksAndMessages(null)
        backgroundHandler = null
        backgroundThread?.quitSafely()
        backgroundThread = null
    }

    override fun isHardwareSupported(): Boolean {
        try {
            return !context.assets.list(assetPath).isNullOrEmpty()
        } catch (e: IOException) {

        }
        return false
    }
}