package dev.skomlach.biometric.compat.engine;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.Intent;
import android.os.Build;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import dev.skomlach.biometric.compat.BiometricType;
import dev.skomlach.biometric.compat.engine.internal.DummyBiometricModule;
import dev.skomlach.biometric.compat.engine.internal.core.Core;
import dev.skomlach.biometric.compat.engine.internal.core.interfaces.AuthenticationListener;
import dev.skomlach.biometric.compat.engine.internal.core.interfaces.BiometricModule;
import dev.skomlach.biometric.compat.engine.internal.face.android.AndroidFaceUnlockModule;
import dev.skomlach.biometric.compat.engine.internal.face.facelock.FacelockOldModule;
import dev.skomlach.biometric.compat.engine.internal.face.huawei.HuaweiFaceUnlockModule;
import dev.skomlach.biometric.compat.engine.internal.face.miui.MiuiFaceUnlockModule;
import dev.skomlach.biometric.compat.engine.internal.face.oneplus.OnePlusFaceUnlockModule;
import dev.skomlach.biometric.compat.engine.internal.face.oppo.OppoFaceUnlockModule;
import dev.skomlach.biometric.compat.engine.internal.face.samsung.SamsungFaceUnlockModule;
import dev.skomlach.biometric.compat.engine.internal.face.soter.SoterFaceUnlockModule;
import dev.skomlach.biometric.compat.engine.internal.face.vivo.VivoFaceUnlockModule;
import dev.skomlach.biometric.compat.engine.internal.fingerprint.API23FingerprintModule;
import dev.skomlach.biometric.compat.engine.internal.fingerprint.FlymeFingerprintModule;
import dev.skomlach.biometric.compat.engine.internal.fingerprint.SamsungFingerprintModule;
import dev.skomlach.biometric.compat.engine.internal.fingerprint.SoterFingerprintUnlockModule;
import dev.skomlach.biometric.compat.engine.internal.fingerprint.SupportFingerprintModule;
import dev.skomlach.biometric.compat.engine.internal.iris.android.AndroidIrisUnlockModule;
import dev.skomlach.biometric.compat.engine.internal.iris.samsung.SamsungIrisUnlockModule;
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl;
import dev.skomlach.common.misc.ExecutorHelper;

import static dev.skomlach.common.misc.Utils.startActivity;

@RestrictTo(RestrictTo.Scope.LIBRARY)
public class BiometricAuthentication {

    private static final Map<BiometricMethod, BiometricModule> moduleHashMap = Collections
            .synchronizedMap(new HashMap<>());

    public static void init() {
        init(null);
    }

    public static void init(BiometricInitListener globalInitListener) {
        init(globalInitListener, null);
    }

    public static void init(BiometricInitListener globalInitListener, Collection<BiometricType> mlist) {
        BiometricLoggerImpl.e("BiometricAuthentication.init()");
        //main thread required
        final ArrayList<BiometricMethod> allMethods = new ArrayList<>();


        //any API
        allMethods.add(BiometricMethod.DUMMY_BIOMETRIC);
        allMethods.add(BiometricMethod.FACELOCK);


        //Samsung Pass appears on Kitkat
        if(Build.VERSION.SDK_INT >= 19) {
            allMethods.add(BiometricMethod.FINGERPRINT_SAMSUNG);
        }
        //Meizu fingerprint - seems like starts Lollipop
        if(Build.VERSION.SDK_INT >= 21) {
            allMethods.add(BiometricMethod.FINGERPRINT_FLYME);

            allMethods.add(BiometricMethod.FACE_SOTERAPI);
            allMethods.add(BiometricMethod.FINGERPRINT_SOTERAPI);
        }
        //Fingerprint API - Marshmallow
        if(Build.VERSION.SDK_INT >= 23) {
            allMethods.add(BiometricMethod.FINGERPRINT_API23);
            allMethods.add(BiometricMethod.FINGERPRINT_SUPPORT);
        }
        //Samsung and others - seems like starts from Oreo
        if(Build.VERSION.SDK_INT >= 26) {
            allMethods.add(BiometricMethod.FACE_SAMSUNG);
            allMethods.add(BiometricMethod.IRIS_SAMSUNG);
            allMethods.add(BiometricMethod.FACE_OPPO);
            allMethods.add(BiometricMethod.FACE_ONEPLUS);
            allMethods.add(BiometricMethod.FACE_VIVO);
            allMethods.add(BiometricMethod.FACE_MIUI);
            allMethods.add(BiometricMethod.FACE_HUAWEI);
        }
        //Android biometric - Pie
        if(Build.VERSION.SDK_INT >= 28) {
            allMethods.add(BiometricMethod.FACE_ANDROIDAPI);
            allMethods.add(BiometricMethod.IRIS_ANDROIDAPI);
        }

        moduleHashMap.clear();
        //launch in BG because for init needed about 2-3 seconds

        try {
            List<BiometricMethod> list;
            if (mlist == null || mlist.size() == 0)
                list = allMethods;
            else {
                list = new ArrayList<>();

                for (BiometricMethod method : allMethods) {
                    for (BiometricType type : mlist) {
                        if (method.getBiometricType() == type) {
                            list.add(method);
                        }
                    }
                }
            }

            final AtomicInteger counter = new AtomicInteger(list.size());
            final BiometricInitListener initListener = new BiometricInitListener() {
                @Override
                public void initFinished(BiometricMethod method, BiometricModule module) {
                    boolean moduleReady = (module != null && module.isManagerAccessible() && module.isHardwarePresent());
                    int remains = counter.decrementAndGet();
                    BiometricLoggerImpl.d("BiometricAuthentication" + ("BiometricInitListener.initListener: '" + method
                            + "' hasManager: " + (module != null && module.isManagerAccessible())+
                            " hasHardware: " + (module != null && module.isHardwarePresent()) + " remains: " + remains));

                    if (moduleReady) {
                        moduleHashMap.put(method, module);
                    }
                    if (globalInitListener != null)
                        globalInitListener.initFinished(method, module);

                    if (remains == 0) {
                        if (globalInitListener != null)
                            globalInitListener.onBiometricReady();

                        BiometricLoggerImpl.d("BiometricAuthentication" + "BiometricAuthentication ready");
                    }
                }

                @Override
                public void onBiometricReady() {

                }
            };

            for (BiometricMethod method : list) {
                ExecutorHelper.INSTANCE.getHandler().post(() -> {
                    BiometricLoggerImpl.e("BiometricAuthentication.check started for "+method);
                    BiometricModule biometricModule = null;
                    try {
                        switch (method) {

                            case DUMMY_BIOMETRIC:
                                biometricModule = new DummyBiometricModule(initListener);
                                break;

                            case FACELOCK:
                                biometricModule = new FacelockOldModule(initListener);
                                break;

                            ///****///
                            case FINGERPRINT_API23:
                                biometricModule = new API23FingerprintModule(initListener);
                                break;
                            case FINGERPRINT_SUPPORT:
                                biometricModule = new SupportFingerprintModule(initListener);
                                break;
                            case FINGERPRINT_SAMSUNG:
                                biometricModule = new SamsungFingerprintModule(initListener);
                                break;
                            case FINGERPRINT_FLYME:
                                biometricModule = new FlymeFingerprintModule(initListener);
                                break;
                            case FINGERPRINT_SOTERAPI:
                                biometricModule = new SoterFingerprintUnlockModule(initListener);
                                break;
                            ///****//
                            case FACE_HUAWEI:
                                biometricModule = new HuaweiFaceUnlockModule(initListener);
                                break;
                            case FACE_MIUI:
                                biometricModule = new MiuiFaceUnlockModule(initListener);
                                break;
                            case FACE_ONEPLUS:
                                biometricModule = new OnePlusFaceUnlockModule(initListener);
                                break;
                            case FACE_SOTERAPI:
                                biometricModule = new SoterFaceUnlockModule(initListener);
                                break;
                            case FACE_OPPO:
                                biometricModule = new OppoFaceUnlockModule(initListener);
                                break;
                            case FACE_SAMSUNG:
                                biometricModule = new SamsungFaceUnlockModule(initListener);
                                break;
                            case FACE_ANDROIDAPI:
                                biometricModule = new AndroidFaceUnlockModule(initListener);
                                break;
                            case FACE_VIVO:
                                biometricModule = new VivoFaceUnlockModule(initListener);
                                break;
                            ///****//
                            case IRIS_SAMSUNG:
                                biometricModule = new SamsungIrisUnlockModule(initListener);
                                break;
                            case IRIS_ANDROIDAPI:
                                biometricModule = new AndroidIrisUnlockModule(initListener);
                                break;
                            ///****//
                            default:
                                throw new IllegalStateException("Uknowon biometric type - " + method);
                        }
                    } catch (Throwable e) {
                        BiometricLoggerImpl.e(e, "BiometricAuthentication" );
                        initListener.initFinished(method, biometricModule);
                    }
                });

            }
        } catch (Throwable e) {
            BiometricLoggerImpl.e(e, "BiometricAuthentication" );
        }
    }

    public static List<BiometricType> getAvailableBiometrics() {
        HashSet<BiometricType> biometricMethodListInternal = new HashSet<>();
        for (BiometricMethod method : moduleHashMap.keySet()) {
            BiometricLoggerImpl.e("Module:" + method);
            biometricMethodListInternal.add(method.getBiometricType());
        }
        return new ArrayList<>(biometricMethodListInternal);
    }

    public static List<BiometricMethod> getAvailableBiometricMethods() {
        HashSet<BiometricMethod> biometricMethodListInternal = new HashSet<>();
        for (BiometricMethod method : moduleHashMap.keySet()) {
            BiometricLoggerImpl.e("Module:" + method);
            biometricMethodListInternal.add(method);
        }
        return new ArrayList<>(biometricMethodListInternal);
    }

    public static boolean isLockOut() {
        for (BiometricType method : BiometricAuthentication.getAvailableBiometrics()) {
            BiometricModule module = getAvailableBiometricModule(method);
            if (module.isLockOut()) {
                return true;
            }
        }
        return false;
    }

    public static boolean isHardwareDetected() {
        for (BiometricType method : BiometricAuthentication.getAvailableBiometrics()) {
            if (getAvailableBiometricModule(method).isHardwarePresent())
                return true;
        }
        return false;
    }

    public static boolean hasEnrolled() {
        for (BiometricType method : BiometricAuthentication.getAvailableBiometrics()) {
            if (getAvailableBiometricModule(method).hasEnrolled())
                return true;
        }

        return false;
    }

    public static void authenticate(@Nullable View targetView, @NonNull final BiometricType method,
                                    @NonNull final BiometricAuthenticationListener listener) {
        authenticate(targetView, Collections.singletonList(method), listener);
    }

    public static void authenticate(@Nullable View targetView, @NonNull final List<BiometricType> requestedMethods,
                                    @NonNull final BiometricAuthenticationListener listener) {
        if (requestedMethods.isEmpty())
            return;
        BiometricLoggerImpl.d("BiometricAuthentication.authenticate");

        boolean isAtLeastOneFired = false;
        final HashMap<Integer, BiometricType> hashMap = new HashMap<>();
        Core.cleanModules();
        for (BiometricType type : requestedMethods) {

            BiometricModule biometricModule = getAvailableBiometricModule(type);
            if (biometricModule == null || !biometricModule.hasEnrolled())
                continue;

            Core.registerModule(biometricModule);

            if (biometricModule instanceof FacelockOldModule) {
                ((FacelockOldModule) biometricModule).setCallerView(targetView);
            }

            hashMap.put(biometricModule.tag(), type);
            isAtLeastOneFired = true;
        }

        if (!isAtLeastOneFired) {
            listener.onFailure(AuthenticationFailureReason.NO_BIOMETRICS_REGISTERED, requestedMethods.get(0));
            return;
        } else {
            Core.authenticate(new AuthenticationListener() {
                @Override
                public void onHelp(AuthenticationHelpReason helpReason, CharSequence msg) {
                    listener.onHelp(helpReason, msg);
                }

                @Override
                public void onSuccess(int moduleTag) {
                    listener.onSuccess(hashMap.get(moduleTag));
                }

                @Override
                public void onFailure(AuthenticationFailureReason reason,
                                      int moduleTag) {
                    listener.onFailure(reason, hashMap.get(moduleTag));
                }
            });
        }
    }

    public static void cancelAuthentication() {
        BiometricLoggerImpl.d("BiometricAuthentication.cancelAuthentication");

        for (BiometricType method : getAvailableBiometrics()) {
            BiometricModule biometricModule = getAvailableBiometricModule(method);
            if (biometricModule instanceof FacelockOldModule) {
                ((FacelockOldModule) biometricModule).stopAuth();
            }
        }
        Core.cancelAuthentication();
    }

    public static boolean openSettings(Activity context, BiometricType type) {
        if (getAvailableBiometricMethods().isEmpty()) {
            return false;
        }
        return openSettings(context, type, getAvailableBiometricModule(type));
    }

    private static boolean openSettings(Activity context, BiometricType method, BiometricModule biometricModule) {

        if (biometricModule instanceof SamsungFingerprintModule) {
            if (((SamsungFingerprintModule) biometricModule).openSettings(context))
                return true;
        }

        if (biometricModule instanceof FacelockOldModule && startActivity(new Intent(DevicePolicyManager.ACTION_SET_NEW_PASSWORD), context)) {
            return true;
        }

        if(biometricModule instanceof OnePlusFaceUnlockModule && startActivity(
                new Intent().setClassName("com.android.settings", "com.android.settings.Settings$OPFaceUnlockSettings"),
                context)){
            return true;
        }

        //for unknown reasons on some devices happens SecurityException - "Permission.MANAGE_FINGERPRINT required" - but not should be
        if (BiometricType.BIOMETRIC_FINGERPRINT == method
                && startActivity(new Intent("android.settings.FINGERPRINT_ENROLL"), context)) {
            return true;
        }
        if (method == BiometricType.BIOMETRIC_FINGERPRINT
                && startActivity(new Intent("android.settings.FINGERPRINT_SETTINGS"), context)) {
            return true;
        }

        if (BiometricType.BIOMETRIC_FACE == method
                && startActivity(new Intent("android.settings.FACE_ENROLL"), context)) {
            return true;
        }
        if (method == BiometricType.BIOMETRIC_FACE
                && startActivity(new Intent("android.settings.FACE_SETTINGS"), context)) {
            return true;
        }
//        if (method == BiometricType.BIOMETRIC_FACE
//                && startActivity(new Intent().setClassName("com.android.settings", "com.android.settings.facechecker.unlock.FaceUnLockSettingsActivity"), context)) {
//            return true;
//        }

        if (BiometricType.BIOMETRIC_IRIS == method
                && startActivity(new Intent("android.settings.IRIS_ENROLL"), context)) {
            return true;
        }
        if (method == BiometricType.BIOMETRIC_IRIS
                && startActivity(new Intent("android.settings.IRIS_SETTINGS"), context)) {
            return true;
        }

        return false;
    }

    public static BiometricModule getAvailableBiometricModule(BiometricType biometricMethod) {
        BiometricMethod module = null;
        //lowest  ID == highest priority
        for (BiometricMethod m : moduleHashMap.keySet()) {
            if (m.getBiometricType() == biometricMethod) {
                if (module == null)
                    module = m;
                else if (module.getId() > m.getId()) {
                    module = m;
                }
            }
        }
        return module == null ? null : moduleHashMap.get(module);
    }
}