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
package dev.skomlach.common.network

import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Shared active Internet verdict. Reads never perform DNS or socket I/O. */
internal class InternetConnectivityMonitor(
    private val rawStates: StateFlow<NetworkState>,
    private val refreshRaw: () -> NetworkState,
    private val check: suspend (NetworkState) -> Boolean,
    private val parentScope: CoroutineScope,
    private val clockMillis: () -> Long = { SystemClock.elapsedRealtime() },
    private val timeoutMillis: Long = InternetProbe.ROUND_TIMEOUT_MILLIS,
    private val recheckMillis: Long = 2_500,
    private val confirmationMillis: Long = 1_000,
    private val retryMillis: List<Long> = listOf(1_000, 2_000, 5_000),
    private val backgroundRecheckMillis: Long = 60_000
) {
    init {
        require(timeoutMillis > 0 && recheckMillis > 0 && confirmationMillis > 0)
        require(retryMillis.isNotEmpty() && retryMillis.all { it > 0 })
        require(backgroundRecheckMillis >= recheckMillis)
    }

    private class Effects {
        var state: NetworkState? = null
        var raw: NetworkState? = null
        val cancel = mutableListOf<Job>()
        val start = mutableListOf<Job>()
    }

    private val lock = Any()
    private var scope: CoroutineScope? = null
    private var route: NetworkState? = null
    private var request: Job? = null
    private var timer: Job? = null
    private var ticket = 0L
    private var nextCheckAt = 0L
    private var unusableSince: Long? = null
    private var failures = 0
    private var foreground = true
    private var reduced = NetworkState(internetAccess = InternetAccess.CHECKING)
    private val mutableState = MutableStateFlow(reduced)
    val states: StateFlow<NetworkState> = mutableState.asStateFlow()

    /** Legacy listeners receive confirmed Boolean transitions, not check/route churn. */
    val connectionChanges: Flow<Boolean> = states.map { read() }
        .filter { it.internetAccess == InternetAccess.AVAILABLE || it.internetAccess == InternetAccess.UNAVAILABLE }
        .map { it.isConnected }.distinctUntilChanged()

    fun start() {
        val effects = synchronized(lock) {
            if (scope != null) return
            val childScope = CoroutineScope(parentScope.coroutineContext +
                SupervisorJob(parentScope.coroutineContext[Job]))
            scope = childScope
            Effects().also { effects ->
                effects.start += childScope.launch(start = CoroutineStart.LAZY) {
                    rawStates.collect { read() }
                }
            }
        }
        apply(effects)
        read()
    }

    fun stop() {
        val effects = synchronized(lock) {
            Effects().also {
                scope?.coroutineContext?.get(Job)?.let(it.cancel::add)
                scope = null
                ticket++
                request = null
                timer = null
                route = null
                unusableSince = null
                failures = 0
                nextCheckAt = 0
                setState(NetworkState(), InternetAccess.UNAVAILABLE, it, force = true)
            }
        }
        apply(effects)
    }

    fun invalidate() {
        val effects = synchronized(lock) {
            Effects().also {
                request?.let(it.cancel::add)
                timer?.let(it.cancel::add)
                request = null
                timer = null
                route = null
                ticket++
            }
        }
        apply(effects)
        read()
    }

    /** Background checks are sparse; returning to an interactive app checks immediately. */
    fun setForeground(active: Boolean) {
        val effects = synchronized(lock) {
            if (foreground == active) return
            foreground = active
            Effects().also { effects ->
                val childScope = scope
                // Do not postpone a pending offline confirmation or the initial verdict.
                if (request == null && (active ||
                    (reduced.internetAccess != InternetAccess.CHECKING && failures != 1))) {
                    timer?.let(effects.cancel::add)
                    timer = null
                    nextCheckAt = if (active) 0 else clockMillis() + backgroundRecheckMillis
                    if (childScope != null && !active) {
                        scheduleTimer(childScope, backgroundRecheckMillis, effects)
                    }
                }
            }
        }
        apply(effects)
        read()
    }

    fun read(): NetworkState {
        val effects = synchronized(lock) {
            val current = rawStates.value
            val effects = Effects()
            val currentEligible = eligible(current)
            val now = clockMillis()
            if (route != current) {
                val previous = route
                val sameUsableRoute = previous != null && eligible(previous) && currentEligible &&
                    previous.networkId == current.networkId
                val importantChange = previous == null || previous.availability != current.availability ||
                    previous.isVpn != current.isVpn || previous.transports != current.transports
                // A capability/link refresh on the same usable Network must not starve a
                // running check. A lost/blocked/replaced Network cancels it immediately.
                // Ordinary background metadata must not defeat the sparse check schedule.
                // Wake explicitly invalidates; routing/validation/transport changes still check now.
                if (!sameUsableRoute || (request == null && (foreground || importantChange))) {
                    request?.let(effects.cancel::add)
                    timer?.let(effects.cancel::add)
                    request = null
                    timer = null
                    ticket++
                    nextCheckAt = 0
                }
                route = current
                // Metadata churn is not recovery. Keep a continuous no-route interval
                // and a first failed check until a successful check confirms recovery.
                if (!sameUsableRoute && reduced.internetAccess != InternetAccess.AVAILABLE) failures = 0
                // Route metadata changes never replace the last confirmed verdict with a guess.
                setState(current, reduced.internetAccess, effects, force = true)
            }
            val childScope = scope
            if (currentEligible) unusableSince = null
            if (childScope != null && !currentEligible) {
                val since = unusableSince ?: now.also { unusableSince = it }
                val deadline = since + confirmationMillis
                if (now >= deadline) {
                    setState(current, InternetAccess.UNAVAILABLE, effects)
                    if (timer == null || now >= nextCheckAt) {
                        val retry = retryDelay(retryMillis.last())
                        nextCheckAt = now + retry
                        scheduleTimer(childScope, retry, effects)
                    }
                } else if (timer == null) {
                    nextCheckAt = deadline
                    scheduleTimer(childScope, deadline - now, effects)
                }
            } else if (childScope != null && currentEligible && request == null && now >= nextCheckAt) {
                timer?.let(effects.cancel::add)
                timer = null
                val currentTicket = ++ticket
                request = childScope.launch(start = CoroutineStart.LAZY) {
                    val available = try {
                        withTimeoutOrNull(timeoutMillis) { check(current) } ?: false
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        false
                    }
                    complete(current, currentTicket, available)
                }.also(effects.start::add)
            }
            effects
        }
        apply(effects)
        return states.value
    }

    private fun complete(expected: NetworkState, expectedTicket: Long, available: Boolean) {
        val effects = synchronized(lock) {
            val effects = Effects()
            val childScope = scope
            val current = rawStates.value
            if (childScope != null && ticket == expectedTicket && eligible(current) &&
                route?.networkId == expected.networkId && current.networkId == expected.networkId) {
                request = null
                val now = clockMillis()
                val delayMillis = if (available) {
                    failures = 0
                    setState(current, InternetAccess.AVAILABLE, effects)
                    if (foreground) recheckMillis else backgroundRecheckMillis
                } else {
                    failures = (failures + 1).coerceAtMost(retryMillis.size + 1)
                    if (failures >= 2) setState(current, InternetAccess.UNAVAILABLE, effects)
                    if (failures == 1) confirmationMillis else retryDelay(retryMillis[failures - 2])
                }
                nextCheckAt = now + delayMillis
                scheduleTimer(childScope, delayMillis, effects)
            }
            effects
        }
        apply(effects)
        // An OS update may have arrived before its collector had a chance to run.
        read()
    }

    private fun scheduleTimer(childScope: CoroutineScope, delayMillis: Long, effects: Effects) {
        timer?.let(effects.cancel::add)
        val expectedTicket = ticket
        lateinit var scheduled: Job
        scheduled = childScope.launch(start = CoroutineStart.LAZY) {
            delay(delayMillis)
            val active = synchronized(lock) {
                if (scope !== childScope || timer !== scheduled || ticket != expectedTicket) false
                else { timer = null; true }
            }
            if (active) {
                refreshRaw()
                read()
            }
        }
        timer = scheduled
        effects.start += scheduled
    }

    private fun setState(raw: NetworkState, status: InternetAccess, effects: Effects, force: Boolean = false) {
        val next = raw.copy(internetAccess = status, generation = reduced.generation)
        if (force || next != reduced) {
            reduced = next.copy(generation = reduced.generation + 1)
            effects.state = reduced
            effects.raw = raw
        }
    }

    private fun apply(effects: Effects) {
        effects.cancel.forEach { it.cancel() }
        effects.state?.let { state ->
            if (state.internetAccess != InternetAccess.AVAILABLE || effects.raw == rawStates.value) {
                mutableState.update { current -> if (state.generation > current.generation) state else current }
            }
        }
        effects.start.forEach { it.start() }
    }

    private fun eligible(raw: NetworkState): Boolean = when (raw.availability) {
        NetworkAvailability.VALIDATED, NetworkAvailability.UNVALIDATED, NetworkAvailability.CAPTIVE_PORTAL ->
            raw.hasNetworkTransport && raw.hasInternetCapability
        else -> false
    }

    private fun retryDelay(delay: Long) = if (foreground) delay else maxOf(delay, backgroundRecheckMillis)
}
