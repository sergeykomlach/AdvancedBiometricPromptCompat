package dev.skomlach.biometric.compat.utils;

import android.os.Build;

import java.util.Arrays;
import dev.skomlach.common.contextprovider.AndroidContext;

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
    private static final String[] onePlusNord = {
            "BE2028", "BE2029",//OnePlus Nord N10
            "BBE2011", "BE2012", "BE2013",//OnePlus Nord N100
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
    public static boolean isHideDialogInstantly() {
        final String[] modelPrefixes = AndroidContext.getAppContext().getResources().getStringArray(androidx.biometric.R.array.hide_fingerprint_instantly_prefixes);
        for (final String modelPrefix : modelPrefixes) {
            if (Build.MODEL.startsWith(modelPrefix)) {
                return true;
            }
        }
        return false;
    }
    public static boolean isShouldShowInScreenDialogInstantly() {
        return true;
//        if(Build.BRAND.equalsIgnoreCase("OnePlus") ){
//            //OnePlus One - 6 OR Nord
//           if(Arrays.asList(onePlusModelsWithoutBiometricBug).contains(Build.MODEL)
//                   || Arrays.asList(onePlusNord).contains(Build.MODEL))
//               return false;
//
//           return true;
//        }
//        return false;
    }
    public static boolean isLGWithBiometricBug() {
        return Build.BRAND.equalsIgnoreCase("LG") &&
                Arrays.asList(lgWithMissedBiometricUI).contains(Build.MODEL);
    }
}
