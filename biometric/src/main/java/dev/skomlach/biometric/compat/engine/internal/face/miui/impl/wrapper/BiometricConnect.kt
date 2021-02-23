package dev.skomlach.biometric.compat.engine.internal.face.miui.impl.wrapper;

import android.os.Parcelable;

import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl;

public class BiometricConnect {
    public static boolean DEBUG_LOG = false;
    public static String MSG_VER_SER_MAJ = null;
    public static String MSG_VER_SER_MIN = null;
    public static String MSG_VER_MODULE_MAJ = null;
    public static String MSG_VER_MODULE_MIN = null;
    public static String MSG_REPLY_MODULE_ID = null;
    public static String MSG_REPLY_ARG1 = null;
    public static String MSG_REPLY_ARG2 = null;
    public static String MSG_REPLY_NO_SEND_WAIT = null;
    public static String SERVICE_PACKAGE_NAME = null;

    public static String MSG_CB_BUNDLE_DB_TEMPLATE_ID_MAX = null;
    public static String MSG_CB_BUNDLE_DB_GROUP_ID_MAX = null;
    public static String MSG_CB_BUNDLE_DB_TEMPLATE = null;
    public static String MSG_CB_BUNDLE_ENROLL_PARAM_DETECT_ZONE = null;
    public static String MSG_CB_BUNDLE_ENROLL_PARAM_DETECT_FACE = null;
    public static String MSG_CB_BUNDLE_ENROLL_PARAM_DETECT_DISTANCE = null;
    public static String MSG_CB_BUNDLE_ENROLL_PARAM_WAITING_UI = null;
    public static String MSG_CB_BUNDLE_ENROLL_PARAM_DETECT_DEPTHMAP = null;
    public static String MSG_CB_BUNDLE_FACE_IS_IR = null;
    public static String MSG_CB_BUNDLE_FACE_HAS_FACE = null;
    public static String MSG_CB_BUNDLE_FACE_RECT_BOUND = null;
    public static String MSG_CB_BUNDLE_FACE_FLOAT_YAW = null;
    public static String MSG_CB_BUNDLE_FACE_FLOAT_ROLL = null;
    public static String MSG_CB_BUNDLE_FACE_FLOAT_EYE_DIST = null;
    public static String MSG_CB_BUNDLE_FACE_POINTS_ARRAY = null;
    private static Class<?> clazz;
    private static Class<?> dbtemplateClass = null;
    private static Class<?> dbgroupClass = null;

    static {
        try {
            clazz = Class.forName("android.miui.BiometricConnect");
            dbtemplateClass = Class.forName("android.miui.BiometricConnect$DBTemplate");
            dbgroupClass = Class.forName("android.miui.BiometricConnect$DBGroup");
        } catch (Throwable e) {
            BiometricLoggerImpl.e(e);
        }
        try {
            DEBUG_LOG = clazz.getField("DEBUG_LOG").getBoolean(null);

            MSG_VER_SER_MAJ = (String) clazz.getField("MSG_VER_SER_MAJ").get(null);
            MSG_VER_SER_MIN = (String) clazz.getField("MSG_VER_SER_MIN").get(null);

            MSG_VER_MODULE_MAJ = (String) clazz.getField("MSG_VER_MODULE_MAJ").get(null);
            MSG_VER_MODULE_MIN = (String) clazz.getField("MSG_VER_MODULE_MIN").get(null);

            MSG_REPLY_MODULE_ID = (String) clazz.getField("MSG_REPLY_MODULE_ID").get(null);

            MSG_REPLY_NO_SEND_WAIT = (String) clazz.getField("MSG_REPLY_NO_SEND_WAIT").get(null);

            MSG_REPLY_ARG1 = (String) clazz.getField("MSG_REPLY_ARG1").get(null);
            MSG_REPLY_ARG2 = (String) clazz.getField("MSG_REPLY_ARG2").get(null);

            SERVICE_PACKAGE_NAME = (String) clazz.getField("SERVICE_PACKAGE_NAME").get(null);

            MSG_CB_BUNDLE_DB_TEMPLATE_ID_MAX = (String) clazz.getField("MSG_CB_BUNDLE_DB_TEMPLATE_ID_MAX").get(null);
            MSG_CB_BUNDLE_DB_GROUP_ID_MAX = (String) clazz.getField("MSG_CB_BUNDLE_DB_GROUP_ID_MAX").get(null);
            MSG_CB_BUNDLE_DB_TEMPLATE = (String) clazz.getField("MSG_CB_BUNDLE_DB_TEMPLATE").get(null);

            MSG_CB_BUNDLE_ENROLL_PARAM_DETECT_ZONE = (String) clazz.getField("MSG_CB_BUNDLE_ENROLL_PARAM_DETECT_ZONE").get(null);

            MSG_CB_BUNDLE_ENROLL_PARAM_DETECT_FACE = (String) clazz.getField("MSG_CB_BUNDLE_ENROLL_PARAM_DETECT_FACE").get(null);
            MSG_CB_BUNDLE_ENROLL_PARAM_DETECT_DISTANCE = (String) clazz.getField("MSG_CB_BUNDLE_ENROLL_PARAM_DETECT_DISTANCE").get(null);
            MSG_CB_BUNDLE_ENROLL_PARAM_WAITING_UI = (String) clazz.getField("MSG_CB_BUNDLE_ENROLL_PARAM_WAITING_UI").get(null);
            MSG_CB_BUNDLE_ENROLL_PARAM_DETECT_DEPTHMAP = (String) clazz.getField("MSG_CB_BUNDLE_ENROLL_PARAM_DETECT_DEPTHMAP").get(null);

            MSG_CB_BUNDLE_FACE_IS_IR = (String) clazz.getField("MSG_CB_BUNDLE_FACE_IS_IR").get(null);
            MSG_CB_BUNDLE_FACE_HAS_FACE = (String) clazz.getField("MSG_CB_BUNDLE_FACE_HAS_FACE").get(null);
            MSG_CB_BUNDLE_FACE_RECT_BOUND = (String) clazz.getField("MSG_CB_BUNDLE_FACE_RECT_BOUND").get(null);
            MSG_CB_BUNDLE_FACE_FLOAT_YAW = (String) clazz.getField("MSG_CB_BUNDLE_FACE_FLOAT_YAW").get(null);
            MSG_CB_BUNDLE_FACE_FLOAT_ROLL = (String) clazz.getField("MSG_CB_BUNDLE_FACE_FLOAT_ROLL").get(null);
            MSG_CB_BUNDLE_FACE_FLOAT_EYE_DIST = (String) clazz.getField("MSG_CB_BUNDLE_FACE_FLOAT_EYE_DIST").get(null);
            MSG_CB_BUNDLE_FACE_POINTS_ARRAY = (String) clazz.getField("MSG_CB_BUNDLE_FACE_POINTS_ARRAY").get(null);
        } catch (Throwable e) {
            BiometricLoggerImpl.e(e);
        }
    }

    public static void syncDebugLog() {
        try {
            clazz.getMethod("syncDebugLog").invoke(null);
        } catch (Throwable e) {
            BiometricLoggerImpl.e(e);
        }
    }

    public static Parcelable getDBTemplate(int id, String name, String Data, int group_id) {
        try {
            return (Parcelable) dbtemplateClass.getConstructor(int.class, String.class, String.class, int.class).newInstance(id, name, Data, group_id);
        } catch (Throwable e) {
            BiometricLoggerImpl.e(e);
            return null;
        }
    }

    public static Parcelable getDBGroup(int id, String name) {
        try {
            return (Parcelable) dbgroupClass.getConstructor(int.class, String.class).newInstance(id, name);
        } catch (Throwable e) {
            BiometricLoggerImpl.e(e);
            return null;
        }
    }
}
