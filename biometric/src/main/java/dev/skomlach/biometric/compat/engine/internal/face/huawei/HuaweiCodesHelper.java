package dev.skomlach.biometric.compat.engine.internal.face.huawei;

import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl;

public class HuaweiCodesHelper {
    private final HuaweiFaceUnlockLegacyModule huaweiFaceUnlockLegacyModule;

    HuaweiCodesHelper(HuaweiFaceUnlockLegacyModule huaweiFaceUnlockLegacyModule) {
        this.huaweiFaceUnlockLegacyModule = huaweiFaceUnlockLegacyModule;
    }

    String getTypeString(int type) {
        switch (type) {
            case 1:
                return "ENROLL";
            case 2:
                return "AUTH";
            case 3:
                return "REMOVE";
            default:
                return "" + type;
        }
    }

    String getCodeString(int code) {
        switch (code) {
            case 1:
                return "result";
            case 2:
                return "cancel";
            case 3:
                return "acquire";
            case 4:
                return "request busy";
            default:
                return "" + code;
        }
    }

    String getErrorCodeString(int code, int errorCode) {
        if (code != 1) {
            if (code == 3) {
                switch (errorCode) {
                    case 4:
                        return "bad quality";
                    case 5:
                        return "no face detected";
                    case 6:
                        return "face too small";
                    case 7:
                        return "face too large";
                    case 8:
                        return "offset left";
                    case 9:
                        return "offset top";
                    case 10:
                        return "offset right";
                    case 11:
                        return "offset bottom";
                    case 13:
                        return "aliveness warning";
                    case 14:
                        return "aliveness failure";
                    case 15:
                        return "rotate left";
                    case 16:
                        return "face rise to high";
                    case 17:
                        return "rotate right";
                    case 18:
                        return "face too low";
                    case 19:
                        return "keep still";
                    case 21:
                        return "eyes occlusion";
                    case 22:
                        return "eyes closed";
                    case 23:
                        return "mouth occlusion";
                    case 27:
                        return "multi faces";
                    case 28:
                        return "face blur";
                    case 29:
                        return "face not complete";
                    case 30:
                        return "too dark";
                    case 31:
                        return "too light";
                    case 32:
                        return "half shadow";
                    default:
                        break;
                }
            }
        }
        switch (errorCode) {
            case 0:
                return "success";
            case 1:
                return "failed";
            case 2:
                return "cancelled";
            case 3:
                return "compare fail";
            case 4:
                return "time out";
            case 5:
                return "invoke init first";
            case 6:
                return "hal invalid";
            case 7:
                return "over max faces";
            case 8:
                return "in lockout mode";
            case 9:
                return "invalid parameters";
            case 10:
                return "no face data";
            case 11:
                return "low temp & cap";
        }
        return "" + errorCode;
    }

    String getErrorString(int errMsg, int vendorCode) {
        switch (errMsg) {
            case 1:
                return "face_error_hw_not_available";
            case 2:
                return "face_error_unable_to_process";
            case 3:
                return "face_error_timeout";
            case 4:
                return "face_error_no_space";
            case 5:
                return "face_error_canceled";
            case 7:
                return "face_error_lockout";
            case 8:
                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append("face_error_vendor: code ");
                stringBuilder.append(vendorCode);
                return stringBuilder.toString();
            case 9:
                return "face_error_lockout_permanent";
            case 11:
                return "face_error_not_enrolled";
            case 12:
                return "face_error_hw_not_present";
            default:

                StringBuilder stringBuilder2 = new StringBuilder();
                stringBuilder2.append("Invalid error message: ");
                stringBuilder2.append(errMsg);
                stringBuilder2.append(", ");
                stringBuilder2.append(vendorCode);
                BiometricLoggerImpl.e(huaweiFaceUnlockLegacyModule.getName(), stringBuilder2.toString());
                return null;
        }
    }

    String getAcquiredString(int acquireInfo, int vendorCode) {
        switch (acquireInfo) {
            case 0:
                return null;
            case 1:
                return "face_acquired_insufficient";
            case 2:
                return "face_acquired_too_bright";
            case 3:
                return "face_acquired_too_dark";
            case 4:
                return "face_acquired_too_close";
            case 5:
                return "face_acquired_too_far";
            case 6:
                return "face_acquired_too_high";
            case 7:
                return "face_acquired_too_low";
            case 8:
                return "face_acquired_too_right";
            case 9:
                return "face_acquired_too_left";
            case 10:
                return "face_acquired_too_much_motion";
            case 11:
                return "face_acquired_poor_gaze";
            case 12:
                return "face_acquired_not_detected";
            case 13:
                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append("face_acquired_vendor: code ");
                stringBuilder.append(vendorCode);
                return stringBuilder.toString();
            default:
                StringBuilder stringBuilder2 = new StringBuilder();
                stringBuilder2.append("Invalid acquired message: ");
                stringBuilder2.append(acquireInfo);
                stringBuilder2.append(", ");
                stringBuilder2.append(vendorCode);
                BiometricLoggerImpl.e(huaweiFaceUnlockLegacyModule.getName(), stringBuilder2.toString());
                return null;
        }
    }
}
