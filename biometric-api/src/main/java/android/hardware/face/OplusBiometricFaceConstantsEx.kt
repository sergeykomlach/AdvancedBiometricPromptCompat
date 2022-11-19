package android.hardware.face

import android.hardware.face.FaceManager.authenticate
import androidx.annotation.RequiresApi
import android.os.Build
import android.hardware.face.OplusFaceManager.OplusAuthenticationCallback
import android.hardware.face.OplusFaceManager
import android.hardware.face.OplusFaceManager.FaceCommandCallback
import android.hardware.face.IFaceCommandCallback
import android.hardware.face.IOplusFaceManager
import android.os.IInterface
import kotlin.Throws
import android.os.IBinder
import android.os.Parcel

@RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
interface OplusBiometricFaceConstantsEx {
    companion object {
        const val FACE_ACQUIRED_CAMERA_KILL_PROCESS = 2001
        const val FACE_ACQUIRED_VENDOR_FAIL_REASON = 1001
        const val FACE_ACQUIRED_VENDOR_IMAGA_BUFFER_SEQ = 1003
        const val FACE_ACQUIRED_VENDOR_RETRY_TIME = 1002
    }
}