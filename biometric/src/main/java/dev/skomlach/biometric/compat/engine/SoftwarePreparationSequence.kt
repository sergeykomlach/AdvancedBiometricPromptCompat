package dev.skomlach.biometric.compat.engine

import dev.skomlach.biometric.compat.custom.AbstractSoftwareBiometricManager.PreparationCallback
import dev.skomlach.biometric.compat.custom.AbstractSoftwareBiometricManager.Companion.CUSTOM_BIOMETRIC_ERROR_HW_UNAVAILABLE
import dev.skomlach.biometric.compat.isSkippablePreparationError
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Sequential preparation keeps fallback in the failed provider's position.
 * Every asynchronous attempt may advance once, and only while its owning flow is active.
 */
internal fun <T> prepareSoftwareSequence(
    modules: List<T>,
    prepare: (T, PreparationCallback) -> Unit,
    onModuleSkipped: (T) -> Unit,
    callback: PreparationCallback,
    isActive: () -> Boolean,
    dispatchFallback: (Runnable) -> Unit = Runnable::run,
    dispatchContinuation: (Runnable) -> Unit = Runnable::run,
    fallbackFor: (T) -> T?
) {
    fun prepareNext(remaining: List<T>, index: Int) {
        if (!isActive()) return
        if (index >= remaining.size) {
            callback.onPrepared()
            return
        }
        val module = remaining[index]
        val completed = AtomicBoolean(false)
        prepare(module, object : PreparationCallback() {
            override fun onPrepared() {
                if (!completed.compareAndSet(false, true) || !isActive()) return
                prepareNext(remaining, index + 1)
            }

            override fun onPreparationError(errMsgId: Int, errString: CharSequence?) {
                if (!completed.compareAndSet(false, true) || !isActive()) return
                if (isSkippablePreparationError(errMsgId)) {
                    onModuleSkipped(module)
                    if (!isActive()) return
                    dispatchFallback(Runnable {
                        if (!isActive()) return@Runnable
                        val fallback = runCatching { fallbackFor(module) }
                        dispatchContinuation(Runnable {
                            if (!isActive()) return@Runnable
                            fallback.onSuccess {
                                if (it != null) {
                                    prepareNext(remaining.toMutableList().also { next -> next[index] = it }, index)
                                } else prepareNext(remaining, index + 1)
                            }.onFailure { callback.onPreparationError(CUSTOM_BIOMETRIC_ERROR_HW_UNAVAILABLE, it.message) }
                        })
                    })
                    return
                }
                callback.onPreparationError(errMsgId, errString)
            }

            override fun onPreparationCanceled() {
                if (completed.compareAndSet(false, true) && isActive()) {
                    callback.onPreparationCanceled()
                }
            }
        })
    }
    prepareNext(modules, 0)
}
