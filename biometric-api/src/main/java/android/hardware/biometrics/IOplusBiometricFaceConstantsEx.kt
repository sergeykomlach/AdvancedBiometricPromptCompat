package android.hardware.biometrics

import android.os.Build
import androidx.annotation.RequiresApi

@RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
interface IOplusBiometricFaceConstantsEx {
    companion object {
        const val FACE_ACQUIRED_CAMERA_PREVIEW = 1001
        const val FACE_ACQUIRED_DEPTH_TOO_NEARLY = 303
        const val FACE_ACQUIRED_DOE_CHECK = 307
        const val FACE_ACQUIRED_DOE_PRECHECK = 306
        const val FACE_ACQUIRED_FACEDOE_IMAGE_READY = 308
        const val FACE_ACQUIRED_IR_HKER = 305
        const val FACE_ACQUIRED_IR_PATTERN = 304
        const val FACE_ACQUIRED_MOUTH_OCCLUSION = 113
        const val FACE_ACQUIRED_MULTI_FACE = 116
        const val FACE_ACQUIRED_NOSE_OCCLUSION = 115
        const val FACE_ACQUIRED_NOT_FRONTAL_FACE = 114
        const val FACE_ACQUIRED_NO_FACE = 101
        const val FACE_ACQUIRED_NO_FOCUS = 112
        const val FACE_ACQUIRED_SWITCH_DEPTH = 302
        const val FACE_ACQUIRED_SWITCH_IR = 301
        const val FACE_AUTHENTICATE_AUTO = 0
        const val FACE_AUTHENTICATE_BY_FINGERPRINT = 3
        const val FACE_AUTHENTICATE_BY_USER = 1
        const val FACE_AUTHENTICATE_BY_USER_WITH_ANIM = 2
        const val FACE_AUTHENTICATE_PAY = 4
        const val FACE_ERROR_CAMERA_UNAVAILABLE = 0
        const val FACE_KEYGUARD_CANCELED_BY_SCREEN_OFF = "cancelRecognitionByScreenOff"
        const val FACE_WITH_EYES_CLOSED = 111
    }
}