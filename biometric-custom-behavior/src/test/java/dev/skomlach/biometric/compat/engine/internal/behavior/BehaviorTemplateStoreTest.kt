package dev.skomlach.biometric.compat.engine.internal.behavior

import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class BehaviorTemplateStoreTest {
    @Test fun corruptRecordCannotBeReplacedOrBypassedByAnotherEnrollmentWithoutExplicitRemoval() {
        val sample = BehaviorSample(BehaviorMode.TYPING, "abcde", listOf(0L, 120L, 270L, 420L, 610L),
            listOf(60L, 190L, 350L, 510L, 710L), emptyList())
        listOf("v2|bad", "v2|", "unknown-record").forEachIndexed { index, corrupt ->
            val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("corrupt-enroll-$index", Context.MODE_PRIVATE)
            val store = BehaviorTemplateStore { prefs }
            store.save("profile", sample)
            prefs.edit().putString("template_profile", corrupt).commit()
            val before = prefs.all
            for (tag in listOf("profile", "new-profile")) {
                assertThrows(dev.skomlach.common.storage.ProtectedStorageUnavailableException::class.java) {
                    store.save(tag, sample)
                }
                assertEquals(before, prefs.all)
            }
            store.remove("profile")
            store.save("profile", sample)
            assertEquals(listOf("profile" to sample), store.loadTemplates())
        }
    }

    @Test
    fun oversizedSingleCaptureDoesNotOverwriteReadableProfile() {
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("oversize", Context.MODE_PRIVATE)
        val store = BehaviorTemplateStore { prefs }
        val saved = BehaviorSample(BehaviorMode.TYPING, "abcde", listOf(0L, 120L, 270L, 420L, 610L),
            listOf(60L, 190L, 350L, 510L, 710L), emptyList())
        store.save("profile", saved)
        val rawBefore = prefs.getString("template_profile", null)
        val downs = (0 until 256).map { Long.MAX_VALUE - 1_700_000L + it * 120L + it % 3 }
        // These finite floats need nine significant digits even on newer JDK serializers.
        val first = Float.fromBits(62_023_995)
        val second = Float.fromBits(62_024_004)
        val large = BehaviorSample(BehaviorMode.COMBINED, "x".repeat(256), downs,
            downs.mapIndexed { index, down -> down + 50L + index % 5 },
            (0 until 2048).map { i ->
                BehaviorPoint(
                    if (i < 2) 100f else if (i % 2 == 0) -first else -second,
                    when (i) { 0 -> 200f; 1 -> 0f; else -> if (i % 2 == 0) -second else -first },
                    Long.MAX_VALUE - 4096L + i, first, first, 1_000_000_000)
            })
        val external = BehaviorSample.fromBundle(buildBehaviorExtras(null, large.mode, large.phrase,
            large.keyDownTimesMs.toLongArray(), large.keyUpTimesMs.toLongArray(),
            large.strokePoints.flatMap { listOf(it.x, it.y, it.timestampMs.toFloat(),
                it.pressure!!, it.size!!, it.strokeId.toFloat()) }.toFloatArray(), true))!!
        assertTrue(external.hasRequiredDataForMode())
        assertEquals(BehaviorQualityIssue.NONE, external.qualityIssue())

        assertThrows(IllegalArgumentException::class.java) { store.save("profile", external) }

        assertEquals(rawBefore, prefs.getString("template_profile", null))
        assertEquals(listOf("profile" to saved), BehaviorTemplateStore { prefs }.loadTemplates())
    }

    @Test
    fun repeatedMaximumSizeSignaturesRemainReadableAfterReopeningStore() {
        val prefs = RuntimeEnvironment.getApplication()
            .getSharedPreferences("roundtrip", Context.MODE_PRIVATE)
        val store = BehaviorTemplateStore { prefs }
        val sample = BehaviorSample(BehaviorMode.SIGNATURE, null, emptyList(), emptyList(),
            (0 until 2048).map { i ->
                BehaviorPoint((i % 128) * 2f, (i % 2) * 80f, i * 10L, .5f, .4f)
            })
        assertEquals(BehaviorQualityIssue.NONE, sample.qualityIssue())

        repeat(7) { index ->
            store.save("profile", sample.copy(strokePoints = sample.strokePoints.map { it.copy(x = it.x + index) }))
            val loaded = BehaviorTemplateStore { prefs }.loadTemplates()
            assertEquals("retain as many recent captures as fit", minOf(index + 1, 3), loaded.size)
            assertEquals(sample.strokePoints.size, loaded.last().second.strokePoints.size)
            assertEquals(index.toFloat(), loaded.last().second.strokePoints.first().x)
            assertTrue(loaded.size <= 5)
            assertTrue(prefs.getString("template_profile", null)!!.length <= 262_144)
            assertEquals((maxOf(0, index - 2)..index).map { it.toFloat() },
                loaded.map { it.second.strokePoints.first().x })
            assertTrue(loaded.all { it.first == "profile" })
            assertEquals(sample.copy(strokePoints = sample.strokePoints.map { it.copy(x = it.x + index) }),
                loaded.last().second)
        }
    }
}
