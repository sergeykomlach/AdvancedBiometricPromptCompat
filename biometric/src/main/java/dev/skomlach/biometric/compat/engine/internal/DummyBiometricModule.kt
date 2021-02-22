package dev.skomlach.biometric.compat.engine.internal

import androidx.annotation.RestrictTo
import androidx.core.os.CancellationSignal
import dev.skomlach.biometric.compat.engine.AuthenticationFailureReason
import dev.skomlach.biometric.compat.engine.BiometricInitListener
import dev.skomlach.biometric.compat.engine.BiometricMethod
import dev.skomlach.biometric.compat.engine.internal.core.interfaces.AuthenticationListener
import dev.skomlach.biometric.compat.engine.internal.core.interfaces.RestartPredicate
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.d
import dev.skomlach.common.misc.ExecutorHelper

@RestrictTo(RestrictTo.Scope.LIBRARY)
class DummyBiometricModule(listener: BiometricInitListener?) :
    AbstractBiometricModule(BiometricMethod.DUMMY_BIOMETRIC) {

    init {
        listener?.initFinished(biometricMethod, this@DummyBiometricModule)
    }
    //BuildConfig.DEBUG;
    override val isManagerAccessible: Boolean
        get() = false //BuildConfig.DEBUG;
    override val isHardwarePresent: Boolean
        get() = true

    override fun hasEnrolled(): Boolean {
        return true
    }

    @Throws(SecurityException::class)
    override fun authenticate(
        cancellationSignal: CancellationSignal?,
        listener: AuthenticationListener?,
        restartPredicate: RestartPredicate?
    ) {
        d("$name.authenticate - $biometricMethod")
        ExecutorHelper.INSTANCE.handler.postDelayed({
            listener?.onFailure(
                AuthenticationFailureReason.AUTHENTICATION_FAILED,
                biometricMethod.id
            )
        }, 2500)
    }

}