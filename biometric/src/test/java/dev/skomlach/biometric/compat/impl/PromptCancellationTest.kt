package dev.skomlach.biometric.compat.impl

import org.junit.Assert.assertEquals
import org.junit.Test

class PromptCancellationTest {
    @Test
    fun missingFragmentStillCancelsThroughPublicApiWithoutReportingFailure() {
        val events = mutableListOf<String>()
        val cancellation = PromptCancellation<String>(
            cancelPrompt = { events += "public" },
            cancelFragment = { events += "fragment:$it" },
            onError = { events += "error" }
        )

        cancellation.cancel()

        assertEquals(listOf("public"), events)
    }

    @Test
    fun capturedFragmentPreservesForceCancellationAlongsidePublicCancellation() {
        val events = mutableListOf<String>()
        val cancellation = PromptCancellation<String>(
            { events += "public" }, { events += "fragment:$it" }, { events += "error" }
        )
        cancellation.capture("owned")

        cancellation.cancel()
        cancellation.cancel()

        assertEquals(listOf("public", "fragment:owned"), events)
    }

    @Test
    fun publicCancellationFailureStillCancelsCapturedFragment() {
        val events = mutableListOf<String>()
        val cancellation = PromptCancellation<String>(
            { events += "public"; throw IllegalStateException("public failure") },
            { events += "fragment:$it" },
            { events += it.message!! }
        )
        cancellation.capture("detached")

        cancellation.cancel()

        assertEquals(listOf("public", "public failure", "fragment:detached"), events)
    }

    @Test
    fun fallbackFailureDoesNotKeepTheClosedSessionAlive() {
        val events = mutableListOf<String>()
        val cancellation = PromptCancellation<String>(
            { events += "public" },
            { events += "fragment:$it"; throw IllegalStateException("fallback failure") },
            { events += it.message!! }
        )
        cancellation.capture("owned")

        cancellation.cancel()
        cancellation.capture("late")
        cancellation.cancel()

        assertEquals(listOf("public", "fragment:owned", "fallback failure"), events)
    }

    @Test
    fun lateCaptureAndCancelFromOldSessionCannotCancelReplacement() {
        val events = mutableListOf<String>()
        val old = PromptCancellation<String>(
            { events += "old public" }, { events += "old:$it" }, { events += "error" }
        )
        old.capture("old fragment")
        old.cancel()
        val replacement = PromptCancellation<String>(
            { events += "new public" }, { events += "new:$it" }, { events += "error" }
        )
        replacement.capture("new fragment")

        old.capture("new fragment")
        old.cancel()
        assertEquals(listOf("old public", "old:old fragment"), events)

        replacement.cancel()
        assertEquals(listOf("old public", "old:old fragment", "new public", "new:new fragment"), events)
    }

    @Test
    fun cancellationClosesOwnershipBeforeCallingExternalCode() {
        val events = mutableListOf<String>()
        lateinit var cancellation: PromptCancellation<String>
        cancellation = PromptCancellation(
            {
                events += "public"
                cancellation.capture("replacement")
                cancellation.cancel()
            },
            { events += "fragment:$it" },
            { events += "error" }
        )
        cancellation.capture("owned")

        cancellation.cancel()

        assertEquals(listOf("public", "fragment:owned"), events)
    }
}
