package dev.skomlach.biometric.compat.engine.internal.face.miui.impl;

import android.content.Context;
import android.os.Build;

public class MiuiFaceFactory {
    public static final String TAG = "MiuiFaceFactory";
    public static final int TYPE_2D = 1;
    public static final int TYPE_3D = 2;
    public static final int TYPE_DEFAULT = 0;
    public static int sCurrentAuthType = 0;

    public static IMiuiFaceManager getFaceManager(Context context, int authType) {
        if (authType != 0) {
            sCurrentAuthType = authType;
        } else {
            sCurrentAuthType = getCurrentAuthType(context);
        }
        if (sCurrentAuthType == 2) {
            return Miui3DFaceManagerImpl.getInstance(context);
        }
        return MiuiFaceManagerImpl.getInstance(context);
    }

    public static int getCurrentAuthType(Context context) {
        if (!"ursa".equals(Build.DEVICE)) {
            sCurrentAuthType = 1;
        } else if (MiuiFaceManagerImpl.getInstance(context).hasEnrolledFaces() != 0) {
            sCurrentAuthType = 1;
        } else {
            sCurrentAuthType = 2;
        }
        return sCurrentAuthType;
    }
}
