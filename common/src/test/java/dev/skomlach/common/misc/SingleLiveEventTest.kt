package dev.skomlach.common.misc

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.Observer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class SingleLiveEventTest {
    @Test
    fun repeatedForeverRegistrationUsesTheSameWrapper() {
        val event = SingleLiveEvent<Int>()
        val values = mutableListOf<Int>()
        val observer = Observer<Int> { values.add(it) }
        event.observeForever(observer)
        event.value = 1
        event.observeForever(observer)
        event.value = 2
        event.removeObserver(observer)
        event.value = 3

        assertEquals(listOf(1, 2), values)
        assertFalse(event.hasObservers())
    }

    @Test
    fun pendingValueWaitsForStartAndObserverCanRemoveItself() {
        val owner = Owner()
        val event = SingleLiveEvent<Int>()
        val values = mutableListOf<Int>()
        lateinit var observer: Observer<Int>
        observer = Observer {
            values.add(it)
            event.removeObserver(observer)
        }
        event.value = 1
        event.observe(owner, observer)
        assertEquals(emptyList<Int>(), values)

        owner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)
        event.value = 2
        assertEquals(listOf(1), values)
        assertFalse(event.hasObservers())
    }

    @Test
    fun destroyingOwnerReleasesObserverForReuse() {
        val first = Owner()
        val second = Owner()
        val event = SingleLiveEvent<Int>()
        val values = mutableListOf<Int>()
        val observer = Observer<Int> { values.add(it) }
        event.observe(first, observer)
        first.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        assertFalse(event.hasObservers())

        event.observe(second, observer)
        second.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)
        event.value = 1
        assertEquals(listOf(1), values)
    }

    private class Owner : LifecycleOwner {
        override val lifecycle = LifecycleRegistry(this).apply {
            handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        }
    }
}
