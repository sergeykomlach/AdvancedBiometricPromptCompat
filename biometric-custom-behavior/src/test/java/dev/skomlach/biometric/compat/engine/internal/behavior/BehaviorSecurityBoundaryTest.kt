package dev.skomlach.biometric.compat.engine.internal.behavior

import android.content.Context
import android.os.Bundle
import android.util.Base64
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class BehaviorSecurityBoundaryTest {
    private fun typing() = BehaviorSample(BehaviorMode.TYPING, "abcde",
        listOf(0L, 120L, 270L, 420L, 610L), listOf(60L, 190L, 345L, 500L, 705L), emptyList())

    private fun signature() = BehaviorSample(BehaviorMode.SIGNATURE, null, emptyList(), emptyList(),
        (0 until 20).map { BehaviorPoint(it * 7f, (it % 3) * 40f, it * 35L, .5f, .4f) })

    private fun extras(sample: BehaviorSample) = buildBehaviorExtras(null, sample.mode, sample.phrase,
        sample.keyDownTimesMs.toLongArray(), sample.keyUpTimesMs.toLongArray(),
        sample.strokePoints.flatMap { listOf(it.x, it.y, it.timestampMs.toFloat(),
            it.pressure ?: -1f, it.size ?: -1f, it.strokeId.toFloat()) }.toFloatArray(), false)

    @Test fun unknownModeDoesNotBecomeCombined() {
        val both = typing().copy(mode = BehaviorMode.COMBINED, strokePoints = signature().strokePoints)
        assertNull(BehaviorSample.fromBundle(extras(both).apply {
            putString(BehaviorSample.EXTRA_BEHAVIOR_MODE, "unrecognized")
        }))
    }

    @Test fun presentMalformedMetadataCannotUseLegacyDefaults() {
        val both = typing().copy(mode = BehaviorMode.COMBINED, strokePoints = signature().strokePoints)
        assertNull(BehaviorSample.fromBundle(extras(both).apply { putInt(BehaviorSample.EXTRA_BEHAVIOR_MODE, 7) }))
        assertNull(BehaviorSample.fromBundle(extras(both).apply { putString(BehaviorSample.EXTRA_BEHAVIOR_MODE, null) }))
        val legacy = signature().strokePoints.flatMap { listOf(it.x, it.y, it.timestampMs.toFloat(), .5f, .4f) }
        assertNull(BehaviorSample.fromBundle(extras(signature()).apply {
            putFloatArray(BehaviorSample.EXTRA_BEHAVIOR_POINTS, legacy.toFloatArray())
            putString(BehaviorSample.EXTRA_BEHAVIOR_POINTS_STRIDE, "5")
        }))
    }

    @Test fun saveUsesItsValidatedSnapshotWhenCallerChangesItsLists() {
        val backing = RuntimeEnvironment.getApplication().getSharedPreferences("mutating-store", Context.MODE_PRIVATE)
        val downs = typing().keyDownTimesMs.toMutableList()
        val prefs = java.lang.reflect.Proxy.newProxyInstance(javaClass.classLoader,
            arrayOf(android.content.SharedPreferences::class.java)) { _, method, args ->
            if (method.name == "getAll") downs[1] = 999L
            method.invoke(backing, *(args ?: emptyArray()))
        } as android.content.SharedPreferences
        val store = BehaviorTemplateStore { prefs }
        store.save("profile", typing().copy(keyDownTimesMs = downs))
        assertEquals(listOf("profile" to typing()), store.loadTemplates())
    }

    @Test fun legacyOverflowIsUnavailableAndCanBeRecoveredByExplicitRemoval() {
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("overflow-upgrade", Context.MODE_PRIVATE)
        val store = BehaviorTemplateStore { prefs }
        store.save("profile_0", typing())
        val raw = prefs.getString("template_profile_0", null)
        repeat(32) { prefs.edit().putString("template_profile_${it + 1}", raw).commit() }
        assertThrows(dev.skomlach.common.storage.ProtectedStorageUnavailableException::class.java) { store.hasTemplate() }
        store.remove("profile_32")
        assertEquals(32, store.loadTemplates().size)
        store.remove(null)
        assertFalse(store.hasTemplate())
    }

    @Test fun invalidDeclaredStrideIsNotReinterpretedAsLegacy() {
        val bundle = extras(signature()).apply { putInt(BehaviorSample.EXTRA_BEHAVIOR_POINTS_STRIDE, 7) }
        // Explicit legacy-shaped data must not make an invalid declaration valid.
        bundle.putFloatArray(BehaviorSample.EXTRA_BEHAVIOR_POINTS, signature().strokePoints.flatMap {
            listOf(it.x, it.y, it.timestampMs.toFloat(), .5f, .4f)
        }.toFloatArray())
        assertNull(BehaviorSample.fromBundle(bundle))
    }

    @Test fun incompleteLegacyPointIsRejectedInsteadOfDropped() {
        val raw = signature().strokePoints.flatMap { listOf(it.x, it.y, it.timestampMs.toFloat(), .5f, .4f) }
        assertNull(BehaviorSample.fromBundle(Bundle().apply {
            putString(BehaviorSample.EXTRA_BEHAVIOR_MODE, "SIGNATURE")
            putFloatArray(BehaviorSample.EXTRA_BEHAVIOR_POINTS, (raw + 1f).toFloatArray())
        }))
    }

    @Test fun oversizedPhraseIsRejectedInsteadOfTruncated() {
        assertNull(BehaviorSample.fromBundle(extras(typing()).apply {
            putString(BehaviorSample.EXTRA_BEHAVIOR_PHRASE, "x".repeat(257))
        }))
    }

    @Test fun publicStoreRejectsWeakEnrollmentWithoutOverwritingExistingProfile() {
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("weak-store", Context.MODE_PRIVATE)
        val store = BehaviorTemplateStore { prefs }
        store.save("profile", typing())
        val before = prefs.all
        assertThrows(IllegalArgumentException::class.java) {
            store.save("profile", typing().copy(phrase = "abc", keyDownTimesMs = listOf(0, 120, 270),
                keyUpTimesMs = listOf(60, 190, 345)))
        }
        assertEquals(before, prefs.all)
    }

    @Test fun malformedStoredTimingDoesNotSilentlyBecomeValid() {
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("corrupt-store", Context.MODE_PRIVATE)
        val store = BehaviorTemplateStore { prefs }
        store.save("profile", typing())
        val entry = prefs.getString("template_profile", null)!!.substringAfter("|")
        val decoded = String(Base64.decode(entry, Base64.NO_WRAP), Charsets.UTF_8)
        val malformed = decoded.replace("0,120,270,420,610", "0,garbage,120,270,420,610")
        prefs.edit().putString("template_profile", "v2|" + Base64.encodeToString(
            malformed.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)).commit()
        assertThrows(dev.skomlach.common.storage.ProtectedStorageUnavailableException::class.java) { store.loadTemplates() }
    }

    @Test fun profileCountIsBoundedWithoutEvictingExistingEnrollment() {
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("bounded-store", Context.MODE_PRIVATE)
        val store = BehaviorTemplateStore { prefs }
        repeat(32) { store.save("profile_$it", typing()) }
        val before = prefs.all
        assertThrows(IllegalArgumentException::class.java) { store.save("overflow", typing()) }
        assertEquals(before, prefs.all)
        assertEquals(32, store.loadTemplates().size)
    }

    @Test fun diagnosticSampleStringDoesNotExposePhraseOrRawTrace() {
        val sample = typing().copy(phrase = "sensitive-phrase")
        assertFalse(sample.toString().contains("sensitive-phrase"))
        assertFalse(sample.toString().contains("120"))
        assertFalse(signature().toString().contains("timestampMs="))
    }
}
