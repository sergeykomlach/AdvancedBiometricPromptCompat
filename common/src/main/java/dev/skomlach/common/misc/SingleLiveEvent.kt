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

package dev.skomlach.common.misc

import androidx.annotation.MainThread
import androidx.arch.core.executor.ArchTaskExecutor
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.Observer

/** Delivers each value once to each registered observer, subject to the LiveData lifecycle. */
class SingleLiveEvent<T> : MediatorLiveData<T>() {

    private val observers = mutableMapOf<Observer<in T>, ObserverWrapper>()
    private var pendingWithoutObservers = false

    @MainThread
    override fun observe(owner: LifecycleOwner, observer: Observer<in T>) {
        checkMainThread()
        if (owner.lifecycle.currentState == Lifecycle.State.DESTROYED) return
        super.observe(owner, wrapperFor(owner, observer))
    }

    @MainThread
    override fun observeForever(observer: Observer<in T>) {
        checkMainThread()
        super.observeForever(wrapperFor(null, observer))
    }

    @MainThread
    override fun removeObserver(observer: Observer<in T>) {
        checkMainThread()
        val wrapper = observers.remove(observer)
        // LiveData also calls this with its registered wrapper on owner destruction.
        if (wrapper != null) {
            super.removeObserver(wrapper)
        } else {
            observers.entries.removeAll { it.value === observer }
            super.removeObserver(observer)
        }
    }

    @MainThread
    override fun setValue(t: T?) {
        checkMainThread()
        pendingWithoutObservers = observers.isEmpty()
        observers.values.forEach { it.pending = true }
        super.setValue(t)
    }

    /**
     * Used for cases where T is Void, to make calls cleaner.
     */
    @MainThread
    fun call() {
        value = null
    }

    private fun wrapperFor(owner: LifecycleOwner?, observer: Observer<in T>): ObserverWrapper {
        return observers.getOrPut(observer) { ObserverWrapper(owner, observer) }.also {
            require(it.owner === owner) { "Cannot add the same observer with different lifecycles" }
        }
    }

    private fun checkMainThread() {
        check(ArchTaskExecutor.getInstance().isMainThread) { "LiveData requires the main thread" }
    }

    private inner class ObserverWrapper(
        val owner: LifecycleOwner?,
        private val observer: Observer<in T>
    ) : Observer<T> {
        var pending = false

        override fun onChanged(value: T) {
            if (!pending && (!pendingWithoutObservers || (value == null && owner != null))) return
            pending = false
            pendingWithoutObservers = false
            observer.onChanged(value)
        }
    }
}

fun SingleLiveEvent<Unit>.post() {
    postValue(Unit)
}
