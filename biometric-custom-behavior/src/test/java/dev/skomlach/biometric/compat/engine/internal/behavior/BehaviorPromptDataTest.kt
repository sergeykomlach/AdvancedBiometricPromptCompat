package dev.skomlach.biometric.compat.engine.internal.behavior

import android.os.Bundle
import dev.skomlach.biometric.compat.BundleBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BehaviorPromptDataTest {

    @Test
    fun buildBehaviorExtrasRoundTripsIntoBehaviorSample() {
        val extras = buildBehaviorExtras(
            existing = Bundle().apply {
                putString("other", "value")
                putString(BehaviorSample.EXTRA_BEHAVIOR_MODE, BehaviorMode.TYPING.name)
                putFloatArray(BehaviorSample.EXTRA_BEHAVIOR_POINTS, floatArrayOf(99f, 88f))
            },
            mode = BehaviorMode.COMBINED,
            phrase = "  open sesame  ",
            keyDownTimesMs = longArrayOf(0L, 120L, 275L, 430L, 610L, 810L),
            keyUpTimesMs = longArrayOf(60L, 190L, 350L, 515L, 700L, 915L),
            strokePoints = floatArrayOf(
                0f, 0f, 0f, 0.5f, 0.4f, 0f,
                12f, 8f, 14f, 0.6f, 0.5f, 0f,
                24f, 18f, 29f, 0.7f, 0.5f, 0f,
                36f, 28f, 45f, 0.8f, 0.6f, 0f,
                42f, 38f, 60f, 0.7f, 0.5f, 0f,
                34f, 52f, 77f, 0.6f, 0.4f, 0f,
                21f, 65f, 95f, 0.5f, 0.4f, 0f,
                8f, 74f, 114f, 0.4f, 0.3f, 0f,
                4f, 70f, 135f, 0.5f, 0.4f, 1f,
                16f, 58f, 152f, 0.6f, 0.4f, 1f,
                29f, 47f, 168f, 0.7f, 0.5f, 1f,
                41f, 36f, 186f, 0.8f, 0.6f, 1f,
                55f, 28f, 205f, 0.7f, 0.5f, 1f,
                68f, 24f, 226f, 0.6f, 0.4f, 1f,
                80f, 22f, 248f, 0.5f, 0.4f, 1f,
                92f, 20f, 271f, 0.4f, 0.3f, 1f
            ),
            enroll = true
        )

        val sample = BehaviorSample.fromBundle(extras)

        assertEquals("value", extras.getString("other"))
        assertEquals(BehaviorMode.COMBINED.name, extras.getString(BehaviorSample.EXTRA_BEHAVIOR_MODE))
        assertEquals(BehaviorSample.POINT_STRIDE, extras.getInt(BehaviorSample.EXTRA_BEHAVIOR_POINTS_STRIDE))
        assertTrue(extras.getBoolean(BundleBuilder.ENROLL))
        assertNotNull(sample)
        assertEquals(BehaviorMode.COMBINED, sample?.mode)
        assertEquals("open sesame", sample?.phrase)
        assertEquals(listOf(0L, 120L, 275L, 430L, 610L, 810L), sample?.keyDownTimesMs)
        assertEquals(listOf(60L, 190L, 350L, 515L, 700L, 915L), sample?.keyUpTimesMs)
        assertEquals(16, sample?.strokePoints?.size)
        assertEquals(1, sample?.strokePoints?.last()?.strokeId)
    }

    @Test
    fun buildBehaviorExtrasCarriesOnlyInternalSessionNonceWhenProvided() {
        val token = BehaviorCaptureSessionToken(nonce = 42L, startedAtMs = 1_000L)

        val internalExtras = buildBehaviorExtras(
            existing = null,
            mode = BehaviorMode.TYPING,
            phrase = "open sesame",
            keyDownTimesMs = longArrayOf(1_000L, 1_120L, 1_260L),
            keyUpTimesMs = longArrayOf(1_080L, 1_190L, 1_330L),
            strokePoints = floatArrayOf(),
            enroll = false,
            sessionToken = token
        )
        val compatibilityExtras = buildBehaviorExtras(
            existing = null,
            mode = BehaviorMode.TYPING,
            phrase = "open sesame",
            keyDownTimesMs = longArrayOf(1_000L, 1_120L, 1_260L),
            keyUpTimesMs = longArrayOf(1_080L, 1_190L, 1_330L),
            strokePoints = floatArrayOf(),
            enroll = false
        )

        assertEquals(42L, internalExtras.getLong(EXTRA_BEHAVIOR_SESSION_NONCE))
        assertTrue(!compatibilityExtras.containsKey(EXTRA_BEHAVIOR_SESSION_NONCE))
    }
}
