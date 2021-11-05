/*
 *  Copyright (c) 2021 Sergey Komlach aka Salat-Cx65; Original project: https://github.com/Salat-Cx65/AdvancedBiometricPromptCompat
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

package dev.skomlach.biometric.compat.engine.internal.face.miui.impl.wrapper

import android.os.Parcelable
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.e

object BiometricConnect {
    var DEBUG_LOG = false
    var MSG_VER_SER_MAJ: String? = null
    var MSG_VER_SER_MIN: String? = null
    var MSG_VER_MODULE_MAJ: String? = null
    var MSG_VER_MODULE_MIN: String? = null
    var MSG_REPLY_MODULE_ID: String? = null
    var MSG_REPLY_ARG1: String? = null
    var MSG_REPLY_ARG2: String? = null
    var MSG_REPLY_NO_SEND_WAIT: String? = null
    var SERVICE_PACKAGE_NAME: String? = null
    var MSG_CB_BUNDLE_DB_TEMPLATE_ID_MAX: String? = null
    var MSG_CB_BUNDLE_DB_GROUP_ID_MAX: String? = null
    var MSG_CB_BUNDLE_DB_TEMPLATE: String? = null
    var MSG_CB_BUNDLE_ENROLL_PARAM_DETECT_ZONE: String? = null
    var MSG_CB_BUNDLE_ENROLL_PARAM_DETECT_FACE: String? = null
    var MSG_CB_BUNDLE_ENROLL_PARAM_DETECT_DISTANCE: String? = null
    var MSG_CB_BUNDLE_ENROLL_PARAM_WAITING_UI: String? = null
    var MSG_CB_BUNDLE_ENROLL_PARAM_DETECT_DEPTHMAP: String? = null
    var MSG_CB_BUNDLE_FACE_IS_IR: String? = null
    var MSG_CB_BUNDLE_FACE_HAS_FACE: String? = null
    var MSG_CB_BUNDLE_FACE_RECT_BOUND: String? = null
    var MSG_CB_BUNDLE_FACE_FLOAT_YAW: String? = null
    var MSG_CB_BUNDLE_FACE_FLOAT_ROLL: String? = null
    var MSG_CB_BUNDLE_FACE_FLOAT_EYE_DIST: String? = null
    var MSG_CB_BUNDLE_FACE_POINTS_ARRAY: String? = null
    private var clazz: Class<*>? = null
    private var dbtemplateClass: Class<*>? = null
    private var dbgroupClass: Class<*>? = null

    init {
        try {
            clazz = Class.forName("android.miui.BiometricConnect")
            dbtemplateClass = Class.forName("android.miui.BiometricConnect\$DBTemplate")
            dbgroupClass = Class.forName("android.miui.BiometricConnect\$DBGroup")
            DEBUG_LOG = clazz?.getField("DEBUG_LOG")?.getBoolean(null) ?: false
            MSG_VER_SER_MAJ = clazz?.getField("MSG_VER_SER_MAJ")?.get(null) as String?
            MSG_VER_SER_MIN = clazz?.getField("MSG_VER_SER_MIN")?.get(null) as String?
            MSG_VER_MODULE_MAJ = clazz?.getField("MSG_VER_MODULE_MAJ")?.get(null) as String?
            MSG_VER_MODULE_MIN = clazz?.getField("MSG_VER_MODULE_MIN")?.get(null) as String?
            MSG_REPLY_MODULE_ID = clazz?.getField("MSG_REPLY_MODULE_ID")?.get(null) as String?
            MSG_REPLY_NO_SEND_WAIT = clazz?.getField("MSG_REPLY_NO_SEND_WAIT")?.get(null) as String?
            MSG_REPLY_ARG1 = clazz?.getField("MSG_REPLY_ARG1")?.get(null) as String?
            MSG_REPLY_ARG2 = clazz?.getField("MSG_REPLY_ARG2")?.get(null) as String?
            SERVICE_PACKAGE_NAME = clazz?.getField("SERVICE_PACKAGE_NAME")?.get(null) as String?
            MSG_CB_BUNDLE_DB_TEMPLATE_ID_MAX =
                clazz?.getField("MSG_CB_BUNDLE_DB_TEMPLATE_ID_MAX")?.get(null) as String?
            MSG_CB_BUNDLE_DB_GROUP_ID_MAX =
                clazz?.getField("MSG_CB_BUNDLE_DB_GROUP_ID_MAX")?.get(null) as String?
            MSG_CB_BUNDLE_DB_TEMPLATE =
                clazz?.getField("MSG_CB_BUNDLE_DB_TEMPLATE")?.get(null) as String?
            MSG_CB_BUNDLE_ENROLL_PARAM_DETECT_ZONE =
                clazz?.getField("MSG_CB_BUNDLE_ENROLL_PARAM_DETECT_ZONE")?.get(null) as String?
            MSG_CB_BUNDLE_ENROLL_PARAM_DETECT_FACE =
                clazz?.getField("MSG_CB_BUNDLE_ENROLL_PARAM_DETECT_FACE")?.get(null) as String?
            MSG_CB_BUNDLE_ENROLL_PARAM_DETECT_DISTANCE =
                clazz?.getField("MSG_CB_BUNDLE_ENROLL_PARAM_DETECT_DISTANCE")?.get(null) as String?
            MSG_CB_BUNDLE_ENROLL_PARAM_WAITING_UI =
                clazz?.getField("MSG_CB_BUNDLE_ENROLL_PARAM_WAITING_UI")?.get(null) as String?
            MSG_CB_BUNDLE_ENROLL_PARAM_DETECT_DEPTHMAP =
                clazz?.getField("MSG_CB_BUNDLE_ENROLL_PARAM_DETECT_DEPTHMAP")?.get(null) as String?
            MSG_CB_BUNDLE_FACE_IS_IR =
                clazz?.getField("MSG_CB_BUNDLE_FACE_IS_IR")?.get(null) as String?
            MSG_CB_BUNDLE_FACE_HAS_FACE =
                clazz?.getField("MSG_CB_BUNDLE_FACE_HAS_FACE")?.get(null) as String?
            MSG_CB_BUNDLE_FACE_RECT_BOUND =
                clazz?.getField("MSG_CB_BUNDLE_FACE_RECT_BOUND")?.get(null) as String?
            MSG_CB_BUNDLE_FACE_FLOAT_YAW =
                clazz?.getField("MSG_CB_BUNDLE_FACE_FLOAT_YAW")?.get(null) as String?
            MSG_CB_BUNDLE_FACE_FLOAT_ROLL =
                clazz?.getField("MSG_CB_BUNDLE_FACE_FLOAT_ROLL")?.get(null) as String?
            MSG_CB_BUNDLE_FACE_FLOAT_EYE_DIST =
                clazz?.getField("MSG_CB_BUNDLE_FACE_FLOAT_EYE_DIST")?.get(null) as String?
            MSG_CB_BUNDLE_FACE_POINTS_ARRAY =
                clazz?.getField("MSG_CB_BUNDLE_FACE_POINTS_ARRAY")?.get(null) as String?
        } catch (ignored: ClassNotFoundException) {
        } catch (e: Throwable) {
            e(e)
        }
    }

    fun syncDebugLog() {
        try {
            clazz?.getMethod("syncDebugLog")?.invoke(null)
        } catch (e: Throwable) {
            e(e)
        }
    }

    fun getDBTemplate(id: Int, name: String?, Data: String?, group_id: Int): Parcelable? {
        return try {
            dbtemplateClass?.getConstructor(
                Int::class.javaPrimitiveType,
                String::class.java,
                String::class.java,
                Int::class.javaPrimitiveType
            )?.newInstance(id, name, Data, group_id) as Parcelable
        } catch (e: Throwable) {
            e(e)
            null
        }
    }

    fun getDBGroup(id: Int, name: String?): Parcelable? {
        return try {
            dbgroupClass?.getConstructor(Int::class.javaPrimitiveType, String::class.java)
                ?.newInstance(id, name) as Parcelable
        } catch (e: Throwable) {
            e(e)
            null
        }
    }
}