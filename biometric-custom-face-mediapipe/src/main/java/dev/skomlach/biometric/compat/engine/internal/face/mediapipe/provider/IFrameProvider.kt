package dev.skomlach.biometric.compat.engine.internal.face.mediapipe.provider

import android.os.Handler
import com.google.mediapipe.framework.image.MPImage

interface IFrameProvider {
    /**
     * @param frameListener приймає MPImage.
     * ВАЖЛИВО: Отримувач (Manager) ЗОБОВ'ЯЗАНИЙ викликати mpImage.close() після використання!
     */
    fun start(
        handler: Handler,
        frameListener: (image: MPImage) -> Unit,
        errorListener: (code: Int, message: String) -> Unit
    )

    fun stop()
}