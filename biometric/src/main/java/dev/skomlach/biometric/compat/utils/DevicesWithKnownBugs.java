package dev.skomlach.biometric.compat.utils;

import android.os.Build;

import java.util.Arrays;

public class DevicesWithKnownBugs {

    //https://forums.oneplus.com/threads/oneplus-7-pro-fingerprint-biometricprompt-does-not-show.1035821/
    private static final String[] onePlusModelsWithoutBiometricBug = {
            "A0001", // OnePlus One
            "ONE A2001", "ONE A2003", "ONE A2005", // OnePlus 2
            "ONE E1001", "ONE E1003", "ONE E1005", // OnePlus X
            "ONEPLUS A3000", "ONEPLUS SM-A3000", "ONEPLUS A3003", // OnePlus 3
            "ONEPLUS A3010", // OnePlus 3T
            "ONEPLUS A5000", // OnePlus 5
            "ONEPLUS A5010", // OnePlus 5T
            "ONEPLUS A6000", "ONEPLUS A6003" // OnePlus 6
    };

    //Users reports that on LG G8 displayed "Biometric dialog without fingerprint icon";
    //After digging I found that it seems like BiometricPrompt simply missing on this device,
    //so the fallback screen for In-Screen scanners are displayed, where we expecte that
    //Fingerpint icon will be shown by the System

    //https://lg-firmwares.com/models-list/
    private static final String[] lgWithMissedBiometricUI = {
            //G8 ThinQ
            "G820N", "G820QM", "G820QM5", "G820TM", "G820UM",

            //G8S ThinQ
            "G810EA", "G810EAW", "G810RA",

            //G8X ThinQ
            "G850EM", "G850EMW", "G850QM", "G850UM",
    };

    public static boolean isOnePlusWithBiometricBug() {
        return Build.BRAND.equalsIgnoreCase("OnePlus") &&
                !Arrays.asList(onePlusModelsWithoutBiometricBug).contains(Build.MODEL);
    }

    public static boolean isLGWithBiometricBug() {
        return Build.BRAND.equalsIgnoreCase("LG") &&
                Arrays.asList(lgWithMissedBiometricUI).contains(Build.MODEL);
    }
}
