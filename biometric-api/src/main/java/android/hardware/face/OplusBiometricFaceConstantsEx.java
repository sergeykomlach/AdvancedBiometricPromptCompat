package android.hardware.face;

import android.os.Build;

import androidx.annotation.RequiresApi;

@RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
public interface OplusBiometricFaceConstantsEx {
    int FACE_ACQUIRED_CAMERA_KILL_PROCESS = 2001;
    int FACE_ACQUIRED_VENDOR_FAIL_REASON = 1001;
    int FACE_ACQUIRED_VENDOR_IMAGA_BUFFER_SEQ = 1003;
    int FACE_ACQUIRED_VENDOR_RETRY_TIME = 1002;
}
