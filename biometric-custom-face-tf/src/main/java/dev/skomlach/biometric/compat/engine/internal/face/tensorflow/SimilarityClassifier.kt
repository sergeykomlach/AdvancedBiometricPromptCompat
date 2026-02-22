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

import android.graphics.Bitmap
import android.graphics.RectF

/** Generic interface for interacting with different recognition engines.  */
interface SimilarityClassifier {
    fun registeredCount(): Int
    fun hasRegistered(): Boolean
    fun delete(name: String?)

    /**
     * Registers a new recognition.
     *
     * @param name The name to associate with the recognition.
     * @param recognition The recognition result to register.
     */
    fun register(name: String, recognition: Recognition)

    /**
     * Runs recognition on a new image.
     *
     * @param bitmap The image to process.
     * @param getExtra Whether to store the embedding info in the Recognition object.
     * @return A list of recognitions (usually 1 for this use case).
     */
    fun recognizeImage(bitmap: Bitmap, getExtra: Boolean): List<Recognition>


    /** An immutable result returned by a Classifier describing what was recognized.  */
    class Recognition(
        /**
         * A unique identifier for what has been recognized. Specific to the class, not the instance of
         * the object.
         */
        val id: String?,
        /** Display name for the recognition.  */
        val title: String?,
        /**
         * A sortable score for how good the recognition is relative to others. Lower should be better.
         */
        val distance: Float?,
        /** Optional location within the source image for the location of the recognized object.  */
        private var location: RectF?
    ) {
        var extra: Any? = null

        var crop: Bitmap? = null

        fun getLocation(): RectF {
            return RectF(location)
        }

        fun setLocation(location: RectF?) {
            this.location = location
        }

        override fun toString(): String {
            var resultString = ""
            if (id != null) {
                resultString += "[$id] "
            }

            if (title != null) {
                resultString += "$title "
            }

            if (distance != null) {
                resultString += String.format("(Dist: %.4f) ", distance)
            }

            if (location != null) {
                resultString += location.toString()
            }

            return resultString.trim { it <= ' ' }
        }
    }
}