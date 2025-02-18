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
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.Observer
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/*LiveData that doesn't need to be unregistered from a lifecycle owner,
* it returns a single event for a single observer.
* Works with single instances that return a single event such as interface callbacks*/

class SingleLiveEvent<T> : MediatorLiveData<T>() {

    private val observers = ConcurrentHashMap<LifecycleOwner, MutableSet<ObserverWrapper<T>>>()

    @MainThread
    override fun observe(owner: LifecycleOwner, observer: Observer<in T>) {
        val wrapper = ObserverWrapper(observer)
        val set = observers[owner]
        set?.apply {
            add(wrapper)
        } ?: run {
            val newSet = Collections.newSetFromMap(ConcurrentHashMap<ObserverWrapper<T>, Boolean>())
            newSet.add(wrapper)
            observers[owner] = newSet
        }
        if (!dataIsSent.get()) value?.let {
            observer.onChanged(it)
            dataIsSent.set(true)
        }
        super.observe(owner, wrapper)
    }

    private val observeIsStart = AtomicBoolean(false)
    private val dataIsSent = AtomicBoolean(false)
    private var targetObserver: Observer<in T>? = null
    private val waitFirstValueObserver = Observer<T> {
        if (observeIsStart.get()) {
            removeWaitFirstValueObserver()
            targetObserver?.let { it1 -> super.observeForever(it1) }
        }

    }

    private fun removeWaitFirstValueObserver() {
        super.removeObserver(waitFirstValueObserver)
    }

    override fun postValue(value: T?) {
        if (!hasObservers()) dataIsSent.set(false)
        else dataIsSent.set(true)
        observeIsStart.set(true)
        super.postValue(value)
    }

    override fun observeForever(observer: Observer<in T>) {
        if (value != null && dataIsSent.get()) {
            observeIsStart.set(false)
            targetObserver = observer
            super.observeForever(waitFirstValueObserver)
        } else super.observeForever(observer)
    }

    override fun removeObservers(owner: LifecycleOwner) {
        observers.remove(owner)
        super.removeObservers(owner)
    }

    override fun removeObserver(observer: Observer<in T>) {
        observers.forEach {
            if (it.value.remove(observer as Observer<T>)) {
                if (it.value.isEmpty()) {
                    observers.remove(it.key)
                }
                return@forEach
            }
        }
        super.removeObserver(observer)
    }

    @MainThread
    override fun setValue(t: T?) {
        if (!hasObservers()) dataIsSent.set(false)
        else dataIsSent.set(true)
        observeIsStart.set(true)
        observers.forEach { it.value.forEach { wrapper -> wrapper.newValue() } }
        super.setValue(t)
    }

    /**
     * Used for cases where T is Void, to make calls cleaner.
     */
    @MainThread
    fun call() {
        value = null
    }

    private class ObserverWrapper<in T>(private val observer: Observer<in T>) :
        Observer<@UnsafeVariance T> {

        private val pending = AtomicBoolean(false)

        override fun onChanged(value: T) {
            if (pending.compareAndSet(true, false)) {
                observer.onChanged(value)
            }
        }

        fun newValue() {
            pending.set(true)
        }
    }

}

fun SingleLiveEvent<Unit>.post() {
    postValue(Unit)
}