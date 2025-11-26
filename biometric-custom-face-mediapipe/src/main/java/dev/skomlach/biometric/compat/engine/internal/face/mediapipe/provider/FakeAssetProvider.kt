package dev.skomlach.biometric.compat.engine.internal.face.mediapipe.provider

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Handler
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import java.io.IOException

class FakeAssetProvider(
    private val context: Context,
    private val assetPath: String
) : IFrameProvider {

    private var onFrame: ((MPImage) -> Unit)? = null
    private var onError: ((Int, String) -> Unit)? = null
    private var backgroundHandler: Handler? = null
    private var isRunning = false
    private val assetFiles = mutableListOf<String>()
    private var currentIndex = 0

    override fun start(
        handler: Handler,
        frameListener: (image: MPImage) -> Unit,
        errorListener: (code: Int, message: String) -> Unit
    ) {
        this.backgroundHandler = handler
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
                        // Bitmap -> MPImage
                        val mpImage = BitmapImageBuilder(bitmap).build()
                        onFrame?.invoke(mpImage)
                        // В даному випадку FakeProvider створює копію, тому mpImage.close() в менеджері просто звільнить пам'ять MediaPipe

                        postNextFrame()
                    } else {
                        postNextFrame()
                    }
                }
            } catch (e: Exception) {
                postNextFrame()
            }
        }, 500)
    }

    override fun stop() {
        isRunning = false
        backgroundHandler?.removeCallbacksAndMessages(null)
    }
}