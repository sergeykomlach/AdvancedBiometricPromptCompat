/*
 *  Copyright (c) 2023 Sergey Komlach aka Salat-Cx65; Original project https://github.com/Salat-Cx65/AdvancedBiometricPromptCompat
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
package android.hardware.face

import android.os.Parcel

class FaceAuthenticateOptions {
    companion object {
        const val AUTHENTICATE_REASON_ALTERNATE_BIOMETRIC_BOUNCER_SHOWN = 4
        const val AUTHENTICATE_REASON_ASSISTANT_VISIBLE = 3
        const val AUTHENTICATE_REASON_NOTIFICATION_PANEL_CLICKED = 5
        const val AUTHENTICATE_REASON_OCCLUDING_APP_REQUESTED = 6
        const val AUTHENTICATE_REASON_PICK_UP_GESTURE_TRIGGERED = 7
        const val AUTHENTICATE_REASON_PRIMARY_BOUNCER_SHOWN = 2
        const val AUTHENTICATE_REASON_QS_EXPANDED = 8
        const val AUTHENTICATE_REASON_STARTED_WAKING_UP = 1
        const val AUTHENTICATE_REASON_SWIPE_UP_ON_BOUNCER = 9
        const val AUTHENTICATE_REASON_UDFPS_POINTER_DOWN = 10
        const val AUTHENTICATE_REASON_UNKNOWN = 0
    }
    constructor(a: Int, b: Int, c: Int, d: Int, e: Int, g: String?, i: String?) {}
    private constructor(parcel: Parcel?) {}

    val attributionTag: String?
        get() = null
    val authenticateReason: Int
        get() = 0
    val displayState: Int
        get() = 0
    val opPackageName: String?
        get() = null
    val sensorId: Int
        get() = 0
    val userId: Int
        get() = 0
    val wakeReason: Int
        get() = 0

    fun setAttributionTag(a: String?): FaceAuthenticateOptions {
        return this
    }

    fun setOpPackageName(a: String?): FaceAuthenticateOptions {
        return this
    }

    fun setSensorId(a: Int): FaceAuthenticateOptions {
        return this
    }

    class Builder {
        private val mAttributionTag: String? = null
        private val mAuthenticateReason = 0
        private val mBuilderFieldsSet: Long = 0
        private val mDisplayState = 0
        private val mOpPackageName: String? = null
        private val mSensorId = 0
        private val mUserId = 0
        private val mWakeReason = 0
        private fun checkNotUsed() {}
        fun build(): FaceAuthenticateOptions? {
            return null
        }

        fun setAttributionTag(a: String?): Builder {
            return this
        }

        fun setAuthenticateReason(a: Int): Builder {
            return this
        }

        fun setDisplayState(a: Int): Builder {
            return this
        }

        fun setOpPackageName(a: String?): Builder {
            return this
        }

        fun setSensorId(a: Int): Builder {
            return this
        }

        fun setUserId(a: Int): Builder {
            return this
        }

        fun setWakeReason(a: Int): Builder {
            return this
        }
    }

    interface AuthenticateReason


}