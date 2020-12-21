package android.hardware.miuiface;

import android.content.Context;
        import android.os.Build;

public class MiuiFaceFactory {
    public static final String TAG = "MiuiFaceFactory";
    public static final int TYPE_2D = 1;
    public static final int TYPE_3D = 2;
    public static final int TYPE_DEFAULT = 0;
    public static int sCurrentAuthType = 0;

    public static IMiuiFaceManager getFaceManager(Context context, int authType) {
       return null;
    }

    public static int getCurrentAuthType(Context context) {
        return sCurrentAuthType;
    }
}

