package dev.skomlach.biometric.compat.engine.internal.core;

import androidx.annotation.RestrictTo;
import androidx.core.os.CancellationSignal;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import dev.skomlach.biometric.compat.engine.AuthenticationFailureReason;
import dev.skomlach.biometric.compat.engine.internal.core.interfaces.AuthenticationListener;
import dev.skomlach.biometric.compat.engine.internal.core.interfaces.BiometricModule;
import dev.skomlach.biometric.compat.engine.internal.core.interfaces.RestartPredicate;
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl;

@RestrictTo(RestrictTo.Scope.LIBRARY)
public class Core {

    private static final Map<BiometricModule, CancellationSignal> cancellationSignals = Collections.synchronizedMap(new HashMap<>());
    private static final Map<Integer, BiometricModule> reprintModuleHashMap = Collections.synchronizedMap(new HashMap<>());
    public static void cleanModules() {
        reprintModuleHashMap.clear();
    }
    public static void registerModule(BiometricModule module) {
        if (module == null || reprintModuleHashMap.containsKey(module.tag())) {
            return;
        }
        if (module.isHardwarePresent()) {
            reprintModuleHashMap.put(module.tag(), module);
        }
    }

    static boolean isLockOut() {
        for (BiometricModule module : reprintModuleHashMap.values()) {
            if (module.isLockOut()) {
                return true;
            }
        }

        return false;
    }

    static boolean isHardwareDetected() {
        for (BiometricModule module : reprintModuleHashMap.values()) {
            if (module.isHardwarePresent())
                return true;
        }
        return false;
    }

    static boolean hasEnrolled() {
        for (BiometricModule module : reprintModuleHashMap.values()) {
            if (module.hasEnrolled())
                return true;
        }

        return false;
    }

    /**
     * Start an authentication request.
     *
     * @param listener         The listener to be notified.
     * @param restartPredicate The predicate that determines whether to restart or not.
     */
    public static void authenticate(final AuthenticationListener listener, RestartPredicate restartPredicate) {


        for (BiometricModule module : reprintModuleHashMap.values()) {
            authenticate(module, listener, restartPredicate);
        }

    }

    public static void authenticate(BiometricModule module, final AuthenticationListener listener, RestartPredicate restartPredicate) {
        if (!module.isHardwarePresent() || !module.hasEnrolled() || module.isLockOut())
            throw new RuntimeException("Module " + module.getClass().getSimpleName() + " not ready");

        CancellationSignal cancellationSignal = cancellationSignals.get(module);
        if (cancellationSignal != null && !cancellationSignal.isCanceled())
            cancelAuthentication(module);

        cancellationSignal = new CancellationSignal();

        cancellationSignals.put(module, cancellationSignal);
        module.authenticate(cancellationSignal, listener, restartPredicate);
    }

    public static void cancelAuthentication() {
        for (BiometricModule module : reprintModuleHashMap.values()) {
            cancelAuthentication(module);
        }
    }

    public static void cancelAuthentication(BiometricModule module) {
        final CancellationSignal signal = cancellationSignals.get(module);
        if (signal != null && !signal.isCanceled()) {
            signal.cancel();
        }
        cancellationSignals.remove(module);
    }

    /**
     * Start a fingerprint authentication request.
     * <p/>
     * Equivalent to calling {@link #authenticate(AuthenticationListener, RestartPredicate)} with
     * {@link RestartPredicatesImpl#defaultPredicate()}
     *
     * @param listener The listener that will be notified of authentication events.
     */
    public static void authenticate(AuthenticationListener listener) {
        authenticate(listener, RestartPredicatesImpl.defaultPredicate());
    }

    /**
     * Start a fingerprint authentication request.
     * <p/>
     * This variant will not restart the fingerprint reader after any failure, including non-fatal
     * failures.
     *
     * @param listener The listener that will be notified of authentication events.
     */
    public static void authenticateWithoutRestart(AuthenticationListener listener) {
        authenticate(listener, RestartPredicatesImpl.neverRestart());
    }
}
