package android.hardware.face

import android.os.Build
import androidx.annotation.RequiresApi

@RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
interface OplusBiometricFaceConstantsEx {
    companion object {
        const val FACE_ACQUIRED_CAMERA_KILL_PROCESS = 2001
        const val FACE_ACQUIRED_VENDOR_FAIL_REASON = 1001
        const val FACE_ACQUIRED_VENDOR_IMAGA_BUFFER_SEQ = 1003
        const val FACE_ACQUIRED_VENDOR_RETRY_TIME = 1002
    }
}