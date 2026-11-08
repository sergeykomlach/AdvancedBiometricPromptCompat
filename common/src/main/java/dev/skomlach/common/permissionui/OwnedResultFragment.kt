package dev.skomlach.common.permissionui

import androidx.annotation.RestrictTo
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import dev.skomlach.common.logging.LogCat
import dev.skomlach.common.misc.ExecutorHelper

/** Owns one result on one host. A null result means the request was abandoned. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
abstract class OwnedResultFragment<T : Any> : Fragment() {
    private var completion: ((T?) -> Unit)? = null
    private var launched = false
    private var resultPending = false
    private var removalPosted = false

    protected abstract fun launchRequest()

    final override fun onResume() {
        super.onResume()
        if (completion == null) {
            finish(null)
        } else if (!launched) {
            launched = true
            try {
                launchRequest()
            } catch (error: RuntimeException) {
                LogCat.logException(error)
                finish(null)
            }
        }
    }

    protected fun completeRequest(result: T) {
        if (completion == null || resultPending) return
        resultPending = true
        lifecycleScope.launchWhenResumed { finish(result) }
    }

    override fun onDestroy() {
        finish(null)
        super.onDestroy()
    }

    private fun finish(result: T?) {
        if (removalPosted) return
        removalPosted = true
        val host = activity
        ExecutorHelper.post {
            val callback = completion
            completion = null
            try {
                if (isAdded && !parentFragmentManager.isDestroyed) {
                    parentFragmentManager.beginTransaction().remove(this).commitNowAllowingStateLoss()
                }
            } catch (error: IllegalStateException) {
                LogCat.logException(error)
            } finally {
                val resumed = host != null && !host.isFinishing && !host.isDestroyed &&
                    !host.supportFragmentManager.isStateSaved &&
                    host.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                callback?.invoke(result.takeIf { resumed })
            }
        }
    }

    companion object {
        /** Launch and cancel on main, like other FragmentManager operations. */
        fun <T : Any> launchOwnedResult(
            host: FragmentActivity,
            fragment: OwnedResultFragment<T>,
            tag: String,
            callback: (T?) -> Unit
        ): () -> Unit {
            fragment.completion = callback
            val attach = Runnable {
                if (fragment.completion == null) return@Runnable
                val manager = host.supportFragmentManager
                if (host.isFinishing || host.isDestroyed || manager.isStateSaved ||
                    manager.findFragmentByTag(tag) != null
                ) {
                    fragment.finish(null)
                } else {
                    try {
                        manager.beginTransaction().add(fragment, tag).commitNow()
                    } catch (error: IllegalStateException) {
                        LogCat.logException(error)
                        fragment.finish(null)
                    }
                }
            }
            ExecutorHelper.post(attach)
            return {
                ExecutorHelper.removeCallbacks(attach)
                fragment.completion = null
                fragment.finish(null)
            }
        }
    }
}
