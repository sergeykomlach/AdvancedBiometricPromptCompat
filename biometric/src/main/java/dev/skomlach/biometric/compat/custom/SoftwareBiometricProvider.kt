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

    fun createPrompt(host: SoftwareBiometricPromptHost): SoftwareBiometricPromptDelegate? {
        return promptFactory?.create(host, manager)
    }

    fun supportsBackgroundPreparation(enroll: Boolean): Boolean {
        return promptFactory?.supportsBackgroundPreparation(enroll) == true
    }

    val requiresReadyExtrasBeforeAuthentication: Boolean
        get() = promptFactory?.requiresReadyExtrasBeforeAuthentication == true
}
