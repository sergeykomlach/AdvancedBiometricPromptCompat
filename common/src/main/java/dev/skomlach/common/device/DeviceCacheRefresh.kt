/*
 *  Copyright (c) 2026 Sergey Komlach aka Salat-Cx65; Original project https://github.com/Salat-Cx65/AdvancedBiometricPromptCompat
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
package dev.skomlach.common.device

import dev.skomlach.common.network.InternetAccess
import dev.skomlach.common.network.NetworkState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean

/** Commits a check timestamp only after every dataset was successfully checked or refreshed. */
internal class DeviceCacheRefresh {
    private val running = AtomicBoolean(false)

    fun start(scope: CoroutineScope, refreshes: List<suspend () -> Boolean>, onSuccess: () -> Unit): Job? {
        if (!running.compareAndSet(false, true)) return null
        val job = scope.launch {
            val successful = coroutineScope { refreshes.map { async { it() } }.awaitAll().all { it } }
            if (successful) onSuccess()
        }
        // Also releases admission when the supplied scope is already cancelled.
        job.invokeOnCompletion { running.set(false) }
        return job
    }

    companion object {
        suspend fun awaitInternet(states: StateFlow<NetworkState>, timeoutMillis: Long = 10_000): Boolean =
            withTimeoutOrNull(timeoutMillis) {
                states.first { it.internetAccess == InternetAccess.AVAILABLE ||
                    it.internetAccess == InternetAccess.UNAVAILABLE }
            }?.internetAccess == InternetAccess.AVAILABLE
    }
}
