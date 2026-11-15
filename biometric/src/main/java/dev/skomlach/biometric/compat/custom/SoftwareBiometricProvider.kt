/*
 *  Copyright (c) 2023 Sergey Komlach aka Salat-Cx65; Original project https://github.com/Salat-Cx65/AdvancedBiometricPromptCompat
 *  All rights reserved.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package dev.skomlach.biometric.compat.custom

import android.content.Context
import androidx.annotation.RestrictTo
import dev.skomlach.biometric.compat.AuthenticationFailureReason
import dev.skomlach.biometric.compat.AuthenticationResult
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl
import dev.skomlach.common.misc.ExecutorHelper
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

private val resourceCleanup = WeakHashMap<AbstractSoftwareBiometricManager, (AbstractSoftwareBiometricManager) -> Unit>()

/** Registers native cleanup for an optional module without adding a provider lifecycle ABI method. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@JvmSynthetic
fun <T : AbstractSoftwareBiometricManager> T.withRuntimeCleanup(cleanup: (T) -> Unit): T {
    synchronized(resourceCleanup) {
        @Suppress("UNCHECKED_CAST")
        resourceCleanup[this] = { manager -> cleanup(manager as T) }
    }
    return this
}

/**
 * Automatic discovery constructs managers and prompt factories on a background worker.
 * Constructors must not touch views or assume a thread-local Looper. Create UI in the prompt's
 * UI lifecycle instead; bind any UI handlers explicitly to the main Looper.
 */
abstract class SoftwareBiometricProvider {
    /**
     * Stable module ID, unique across installed software and hardware modules.
     * It namespaces enrollment snapshots and cryptographic keys, so never derive it from a
     * runtime class name or change it after release. When upgrading an existing provider,
     * retain its previously shipped ID to keep access to its stored state.
     *
     * Null preserves the legacy manager-class-name hash for providers built against the old API.
     * New providers should override this with a fixed value; duplicate IDs are rejected.
     */
    open val moduleId: Int? = null

    // Resolve the class name lazily: providers with an explicit ID never use this legacy path.
    internal fun resolveModuleId(legacyManagerClassName: () -> String): Int =
        moduleId ?: legacyManagerClassName().hashCode()

    /** Tie-breaker after the manager priority when prompt factories target one modality. */
    open val promptFactoryPriority: Int = 0

    abstract fun getCustomManager(context: Context): AbstractSoftwareBiometricManager

    open fun getPromptFactory(): SoftwareBiometricPromptFactory? = null

    /**
     * Creates the manager and prompt factory as one runtime unit. The core keeps this pairing for
     * the whole provider lifetime so prompt preparation, authentication and cleanup cannot drift
     * to independently loaded provider instances.
     */
    internal fun createRuntime(context: Context): SoftwareBiometricRuntime {
        val manager = getCustomManager(context)
        return SoftwareBiometricRuntime(
            moduleId = resolveModuleId { manager::class.java.name },
            manager = manager,
            promptFactory = getPromptFactory(),
            promptFactoryPriority = promptFactoryPriority
        )
    }
}

/** Internal, manager-bound runtime for one discovered software provider. */
internal class SoftwareBiometricRuntime(
    val moduleId: Int,
    val manager: AbstractSoftwareBiometricManager,
    val promptFactory: SoftwareBiometricPromptFactory?,
    val promptFactoryPriority: Int
) {
    val priority: Int
        get() = manager.priority

    private val retired = AtomicBoolean()
    private val prompts = Collections.newSetFromMap(ConcurrentHashMap<RuntimePrompt, Boolean>())
    internal val isRetired: Boolean get() = retired.get()

    // A deferred fallback is registered but cannot participate until its whole runtime is selected.
    @Volatile internal var selectionIsActive: () -> Boolean = { true }
    internal val isSelected: Boolean get() = !isRetired && selectionIsActive()

    fun createPrompt(host: SoftwareBiometricPromptHost): SoftwareBiometricPromptDelegate? {
        if (isRetired) return null
        val callbacks = host.callbacks
        val guarded = object : SoftwareBiometricPromptHost.Callbacks {
            override fun onHelp(message: CharSequence) {
                if (!isRetired) callbacks.onHelp(message)
            }
            override fun onStatus(status: SoftwarePromptStatus) {
                if (!isRetired) callbacks.onStatus(status)
            }
            override fun onReady(extras: android.os.Bundle?) {
                if (!isRetired) callbacks.onReady(extras)
            }
            override fun onFailure(result: AuthenticationResult) {
                if (!isRetired) callbacks.onFailure(result)
            }
            override fun isPromptActive(): Boolean = !isRetired && callbacks.isPromptActive()
        }
        val boundCallbacks = if (callbacks is SoftwareBiometricFragmentHost) {
            object : SoftwareBiometricPromptHost.Callbacks by guarded, SoftwareBiometricFragmentHost by callbacks {}
        } else guarded
        val delegate = promptFactory?.create(host.copy(callbacks = boundCallbacks), manager) ?: return null
        return RuntimePrompt(delegate, host).also {
            prompts.add(it)
            if (isRetired) ExecutorHelper.post(it::retire)
        }
    }

    internal fun retire() {
        if (retired.compareAndSet(false, true)) prompts.toList().forEach { prompt ->
            ExecutorHelper.post(prompt::retire)
        }
    }

    internal fun close() {
        val cleanup = synchronized(resourceCleanup) { resourceCleanup.remove(manager) }
        cleanup?.invoke(manager)
    }

    private inner class RuntimePrompt(
        private val delegate: SoftwareBiometricPromptDelegate,
        private val host: SoftwareBiometricPromptHost
    ) : SoftwareBiometricPromptDelegate {
        private var disposed = false

        override fun shouldInstall() = !isRetired && !disposed && delegate.shouldInstall()
        override fun install() { if (!isRetired && !disposed) delegate.install() }
        override fun start() { if (!isRetired && !disposed) delegate.start() }
        override fun cancel() { if (!disposed) delegate.cancel() }
        override fun isReadyToStartAuth() = !isRetired && !disposed && delegate.isReadyToStartAuth()

        override fun dispose() {
            if (disposed) return
            disposed = true
            prompts.remove(this)
            delegate.dispose()
        }

        fun retire() {
            if (disposed) return
            runCatching { cancel() }.onFailure { BiometricLoggerImpl.e(it) }
            runCatching { dispose() }.onFailure { BiometricLoggerImpl.e(it) }
            if (host.callbacks.isPromptActive()) {
                host.callbacks.onFailure(AuthenticationResult(manager.biometricType,
                    reason = AuthenticationFailureReason.CANCELED))
            }
        }
    }

    fun supportsBackgroundPreparation(enroll: Boolean): Boolean {
        return promptFactory?.supportsBackgroundPreparation(enroll) == true
    }

    val requiresReadyExtrasBeforeAuthentication: Boolean
        get() = promptFactory?.requiresReadyExtrasBeforeAuthentication == true
}
