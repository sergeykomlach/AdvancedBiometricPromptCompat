package dev.skomlach.common.contextprovider

import org.junit.Assert.*
import org.junit.Test

class ResumedActivityStateTest {
    @Test
    fun resumedActivityIsAvailableImmediately() {
        val state = ResumedActivityState<Any>()
        val activity = Any()
        assertNull(state.current)
        state.resume(activity)
        assertSame(activity, state.current)
    }

    @Test
    fun immediatePauseClearsActivityWithoutWaitingForLiveData() {
        val state = ResumedActivityState<Any>()
        val activity = Any()
        state.resume(activity)
        assertSame(activity, state.current)
        state.remove(activity)
        assertNull(state.current)
    }

    @Test
    fun latePauseFromPreviousActivityCannotClearReplacement() {
        val state = ResumedActivityState<Any>()
        val previous = Any()
        val replacement = Any()
        state.resume(previous)
        state.resume(replacement)
        state.remove(previous)
        assertSame(replacement, state.current)
    }

    @Test
    fun pausingTopActivityRestoresStillResumedMultiwindowActivity() {
        val state = ResumedActivityState<Any>()
        val first = Any()
        val second = Any()
        state.resume(first)
        state.resume(second)
        state.remove(second)
        assertSame(first, state.current)
    }

    @Test
    fun repeatedResumeDoesNotLeaveDuplicateActivityAfterRemoval() {
        val state = ResumedActivityState<Any>()
        val activity = Any()
        repeat(3) { state.resume(activity) }
        assertSame(activity, state.current)
        state.remove(activity)
        assertNull(state.current)
    }

    @Test
    fun equalActivitiesAreTrackedByIdentity() {
        data class EqualActivity(val name: String)
        val state = ResumedActivityState<EqualActivity>()
        val first = EqualActivity("same")
        val second = EqualActivity("same")
        state.resume(first)
        state.resume(second)
        state.remove(second)
        assertSame(first, state.current)
        state.remove(first)
        assertNull(state.current)
    }
}
