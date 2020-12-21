package dev.skomlach.biometric.compat.engine;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.provider.Settings;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import dev.skomlach.biometric.compat.BiometricType;
import dev.skomlach.biometric.compat.engine.internal.DummyBiometricModule;
import dev.skomlach.biometric.compat.engine.internal.core.Core;
import dev.skomlach.biometric.compat.engine.internal.core.interfaces.AuthenticationListener;
import dev.skomlach.biometric.compat.engine.internal.core.interfaces.BiometricModule;
import dev.skomlach.biometric.compat.engine.internal.face.android.AndroidFaceUnlockModule;
import dev.skomlach.biometric.compat.engine.internal.face.facelock.FacelockOldModule;
import dev.skomlach.biometric.compat.engine.internal.face.huawei.HuaweiFaceUnlockEMIUI10Module;
import dev.skomlach.biometric.compat.engine.internal.face.miui.MiuiFaceUnlockModule;
import dev.skomlach.biometric.compat.engine.internal.face.oppo.OppoFaceUnlockModule;
import dev.skomlach.biometric.compat.engine.internal.face.samsung.SamsungFaceUnlockModule;
import dev.skomlach.biometric.compat.engine.internal.face.soter.SoterFaceUnlockModule;
import dev.skomlach.biometric.compat.engine.internal.fingerprint.API23FingerprintModule;
import dev.skomlach.biometric.compat.engine.internal.fingerprint.FlymeFingerprintModule;
import dev.skomlach.biometric.compat.engine.internal.fingerprint.SamsungFingerprintModule;
import dev.skomlach.biometric.compat.engine.internal.fingerprint.SupportFingerprintModule;
import dev.skomlach.biometric.compat.engine.internal.iris.android.AndroidIrisUnlockModule;
import dev.skomlach.biometric.compat.engine.internal.iris.samsung.SamsungIrisUnlockModule;
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl;

import static dev.skomlach.common.misc.Utils.isAtLeastR;
import static dev.skomlach.common.misc.Utils.startActivity;

@RestrictTo(RestrictTo.Scope.LIBRARY)
public class BiometricAuthentication {

    private static final Map<BiometricMethod, BiometricModule> moduleHashMap = Collections
            .synchronizedMap(new HashMap<>());
    private static boolean isReady = false;

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
        allMethods.add(BiometricMethod.FACE_SOTERAPI);

        //Samsung Pass appears on Kitkat
        if(Build.VERSION.SDK_INT >= 19) {
            allMethods.add(BiometricMethod.FINGERPRINT_SAMSUNG);
        }
        //Meizu fingerprint - seems like starts Lollipop
        if(Build.VERSION.SDK_INT >= 21) {
            allMethods.add(BiometricMethod.FINGERPRINT_FLYME);
        }
        //Fingerprint API - Marshmallow
        if(Build.VERSION.SDK_INT >= 23) {
            allMethods.add(BiometricMethod.FINGERPRINT_API23);
            allMethods.add(BiometricMethod.FINGERPRINT_SUPPORT);
        }
        //Samsung, Oppo seems like starts from Oreo
        if(Build.VERSION.SDK_INT >= 26) {
            allMethods.add(BiometricMethod.FACE_SAMSUNG);
            allMethods.add(BiometricMethod.IRIS_SAMSUNG);
            allMethods.add(BiometricMethod.FACE_OPPO);
            allMethods.add(BiometricMethod.FACE_MIUI);
        }
        //Android biometric - Pie
        if(Build.VERSION.SDK_INT >= 28) {
            allMethods.add(BiometricMethod.FACE_ANDROIDAPI);
            allMethods.add(BiometricMethod.IRIS_ANDROIDAPI);
        }
        //Huawei 3D Face - Android Q
        if(Build.VERSION.SDK_INT >= 29) {
            allMethods.add(BiometricMethod.FACE_HUAWEI_EMUI_10);
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
                            + "' moduleReady: " + moduleReady + " remains: " + remains));

                    if (moduleReady) {
                        moduleHashMap.put(method, module);
                    }
                    if (globalInitListener != null)
                        globalInitListener.initFinished(method, module);

                    if (remains == 0)
                        isReady = true;
                    if (isReady) {
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
                startTask(() -> {
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

                            ///****//
                            case FACE_MIUI:
                                biometricModule = new MiuiFaceUnlockModule(initListener);
                                break;
                            case FACE_SOTERAPI:
                                biometricModule = new SoterFaceUnlockModule(initListener);
                                break;
                            case FACE_HUAWEI_EMUI_10:
                                biometricModule = new HuaweiFaceUnlockEMIUI10Module(initListener);
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

    private static void startTask(Runnable task){
        if(isAtLeastR()){
            Executors.newCachedThreadPool().execute(task);
        }else{
            //AsyncTask Deprecated in API 30
            new AsyncTask<Void, Void, Void>(){
                @Override
                protected Void doInBackground(Void... voids) {
                    task.run();
                    return null;
                }
            }.executeOnExecutor(Executors.newCachedThreadPool());
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

    public static boolean isReady() {
        return isReady;
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
                public void onHelp(AuthenticationHelpReason helpReason, String msg) {
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

    public static void openSettings(Activity context) {
        if (getAvailableBiometrics().isEmpty()) {
            openSettings(context, null, null);
            return;
        }

        //at first, try to open not enrolled settings
        for (BiometricType method : getAvailableBiometrics()) {
            BiometricModule biometricModule = getAvailableBiometricModule(method);
            if (biometricModule.isHardwarePresent() && biometricModule.hasEnrolled())
                continue;
            openSettings(context, method, biometricModule);
            return;
        }
        //in case user planing add new finger/face/iris
        openSettings(context, getAvailableBiometrics().get(0), getAvailableBiometricModule(getAvailableBiometrics().get(0)));
    }

    private static void openSettings(Activity context, BiometricType method, BiometricModule biometricModule) {

        if (biometricModule instanceof SamsungFingerprintModule) {
            if (((SamsungFingerprintModule) biometricModule).openSettings(context))
                return;
        }

        if (biometricModule instanceof FacelockOldModule && startActivity(new Intent(DevicePolicyManager.ACTION_SET_NEW_PASSWORD), context)) {
            return;
        }

        //for unknown reasons on some devices happens SecurityException - "Permission.MANAGE_FINGERPRINT required" - but not should be
        if (BiometricType.BIOMETRIC_FINGERPRINT == method
                && startActivity(new Intent("android.settings.FINGERPRINT_ENROLL"), context)) {
            return;
        }
        if (method == BiometricType.BIOMETRIC_FINGERPRINT
                && startActivity(new Intent("android.settings.FINGERPRINT_SETTINGS"), context)) {
            return;
        }

        if (BiometricType.BIOMETRIC_FACE == method
                && startActivity(new Intent("android.settings.FACE_ENROLL"), context)) {
            return;
        }
        if (method == BiometricType.BIOMETRIC_FACE
                && startActivity(new Intent("android.settings.FACE_SETTINGS"), context)) {
            return;
        }

        if (BiometricType.BIOMETRIC_IRIS == method
                && startActivity(new Intent("android.settings.IRIS_ENROLL"), context)) {
            return;
        }
        if (method == BiometricType.BIOMETRIC_IRIS
                && startActivity(new Intent("android.settings.IRIS_SETTINGS"), context)) {
            return;
        }

        if (startActivity(new Intent("android.settings.BIOMETRIC_ENROLL"), context)) {
            return;
        }
        if (startActivity(new Intent("android.settings.BIOMETRIC_SETTINGS"), context)) {
            return;
        }
        if (startActivity(new Intent().setComponent(
                new ComponentName("com.android.settings", "com.android.settings.Settings$BiometricsAndSecuritySettingsActivity")), context)) {
            return;
        }
        if (startActivity(new Intent().setComponent(
                new ComponentName("com.android.settings", "com.android.settings.Settings$SecuritySettingsActivity")), context)) {
            return;
        }

        startActivity(
                new Intent(Settings.ACTION_SETTINGS), context);
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