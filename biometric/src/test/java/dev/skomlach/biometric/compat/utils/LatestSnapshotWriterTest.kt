package dev.skomlach.biometric.compat.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LatestSnapshotWriterTest {
    @Test fun pendingSnapshotsAreCoalescedIntoOneWorker() {
        val queue = mutableListOf<Runnable>(); val writes = mutableListOf<Int>()
        val writer = LatestSnapshotWriter<Int>({ queue.add(it) }, { writes.add(it) }, { throw it })
        repeat(100) { writer.offer(it) }
        assertEquals(1, queue.size)
        queue.removeAt(0).run()
        assertEquals(listOf(99), writes)
        writer.offer(100)
        assertEquals(1, queue.size)
    }

    @Test fun arrivalDuringWriteIsWrittenAfterTheInFlightSnapshot() {
        val queue = mutableListOf<Runnable>(); val writes = mutableListOf<Int>()
        lateinit var writer: LatestSnapshotWriter<Int>
        writer = LatestSnapshotWriter({ queue.add(it) }, {
            writes.add(it)
            if (it == 1) { writer.offer(2); writer.offer(3) }
        }, { throw it })
        writer.offer(1); queue.removeAt(0).run()
        assertEquals(listOf(1, 3), writes)
        assertTrue(queue.isEmpty())
    }

    @Test fun failedWriteDoesNotBlockNewerSnapshot() {
        val queue = mutableListOf<Runnable>(); val writes = mutableListOf<Int>()
        var failures = 0
        lateinit var writer: LatestSnapshotWriter<Int>
        writer = LatestSnapshotWriter({ queue.add(it) }, {
            if (it == 1) { writer.offer(2); error("Failed persistence") }
            writes.add(it)
        }, { failures++ })
        writer.offer(1); queue.removeAt(0).run()
        assertEquals(1, failures)
        assertEquals(listOf(2), writes)
    }

    @Test fun cacheKeepsAtMost128RecentEntries() {
        val entries = (0..200).associate { "TITLE:$it" to "$it" }
        val bounded = boundPromptTextEntries(entries)
        assertEquals(128, bounded.size)
        assertFalse(bounded.containsKey("TITLE:0"))
        assertEquals("200", bounded["TITLE:200"])
    }

    @Test fun cacheAlsoBoundsRetainedCharactersAndRejectsOversizedEntries() {
        val entries = (0..127).associate { "TITLE:$it" to "x".repeat(2_000) }.toMutableMap()
        entries["oversized"] = "y".repeat(10_000)
        val bounded = boundPromptTextEntries(entries)
        assertTrue(bounded.entries.sumOf { it.key.length + (it.value?.length ?: 0) } <= 65_536)
        assertFalse(bounded.containsKey("oversized"))
        assertTrue(bounded.containsKey("TITLE:127"))
    }

    @Test fun boundingDoesNotMutateOrAliasInputSnapshot() {
        val original = linkedMapOf<String, String?>("TITLE:a" to "a")
        val snapshot = boundPromptTextEntries(original)
        original["TITLE:a"] = "changed"
        assertEquals("a", snapshot["TITLE:a"])
    }
}
