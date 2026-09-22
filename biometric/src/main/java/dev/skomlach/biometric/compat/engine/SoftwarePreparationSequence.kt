package dev.skomlach.biometric.compat.engine

import dev.skomlach.biometric.compat.custom.AbstractSoftwareBiometricManager.PreparationCallback
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
                    val fallback = fallbackFor(module)
                    if (fallback != null) {
                        prepareNext(remaining.toMutableList().also { it[index] = fallback }, index)
                    } else {
                        prepareNext(remaining, index + 1)
                    }
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
