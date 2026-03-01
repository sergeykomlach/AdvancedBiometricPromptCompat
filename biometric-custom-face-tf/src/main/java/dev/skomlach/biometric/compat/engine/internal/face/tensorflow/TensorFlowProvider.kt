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

import android.content.Context
import dev.skomlach.biometric.compat.custom.AbstractCustomBiometricManager
import dev.skomlach.biometric.compat.custom.CustomBiometricProvider
import dev.skomlach.biometric.custom.face.tf.R
import dev.skomlach.common.contextprovider.AndroidContext
import dev.skomlach.common.translate.LocalizationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class TensorFlowProvider : CustomBiometricProvider() {
    init {
        GlobalScope.launch(Dispatchers.IO) {
            LocalizationHelper.prefetch(
                AndroidContext.appContext,
                R.string.tf_face_help_timeout, //Authentication timed out
                R.string.tf_face_help_canceled_by_new_operation, //Cancelled by new operation
                R.string.tf_face_help_model_not_available, //Face detection models not available
                R.string.tf_face_help_model_enrollment_tag_not_provided, //Enrollment tag not provided
                R.string.tf_face_help_model_not_registered, //Biometric not registered
                R.string.tf_face_help_model_already_registered, //This face is already registered
                R.string.tf_face_help_model_image_is_blurry, //Image is blurry, hold still
                R.string.tf_face_help_model_fake_face_detected, //Fake face detected
                R.string.tf_face_help_model_retry, //Retry
                R.string.tf_face_help_model_look_straight_ahead, //Look straight ahead
                R.string.tf_face_help_model_too_dark, //Too dark
                R.string.tf_face_help_model_no_camera_permissions, //No Camera Permission
                R.string.tf_face_help_model_no_front_camera, //No front camera
                R.string.tf_face_help_model_camera_low_res, //Camera resolution low
                R.string.tf_face_help_model_camera_locked_out, //Camera locked out
                R.string.tf_face_help_model_camera_disabled, //Camera disabled
                R.string.tf_face_help_too_many_attempts_try_later, //Face Unlock is locked out. Try again later
                R.string.tf_face_help_too_many_attempts_permanent //Face Unlock is locked out permanently
            )
        }
    }

    override fun getCustomManager(context: Context): AbstractCustomBiometricManager {
        return TensorFlowFaceUnlockManager(context)
    }
}