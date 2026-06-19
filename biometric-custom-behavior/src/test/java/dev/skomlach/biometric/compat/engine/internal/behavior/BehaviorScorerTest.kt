package dev.skomlach.biometric.compat.engine.internal.behavior

import android.os.Bundle
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class BehaviorScorerTest {
    @Test
    fun typingScoreAcceptsSimilarTiming() {
        val enrolled = BehaviorSample(
            mode = BehaviorMode.TYPING,
            phrase = "open sesame",
            keyDownTimesMs = listOf(0, 120, 260, 390, 520),
            keyUpTimesMs = listOf(80, 190, 330, 455, 595),
            strokePoints = emptyList()
        )
        val probe = enrolled.copy(
            keyDownTimesMs = listOf(0, 125, 255, 396, 528),
            keyUpTimesMs = listOf(78, 195, 328, 460, 600)
        )

        assertTrue(BehaviorScorer.score(enrolled, probe) >= 0.82f)
    }

    @Test
    fun signatureScoreRejectsDifferentPath() {
        val enrolled = BehaviorSample(
            mode = BehaviorMode.SIGNATURE,
            phrase = null,
            keyDownTimesMs = emptyList(),
            keyUpTimesMs = emptyList(),
            strokePoints = listOf(
                BehaviorPoint(0f, 0f, 0),
                BehaviorPoint(10f, 10f, 10),
                BehaviorPoint(20f, 20f, 20),
                BehaviorPoint(30f, 30f, 30),
                BehaviorPoint(40f, 40f, 40),
                BehaviorPoint(50f, 50f, 50),
                BehaviorPoint(60f, 60f, 60),
                BehaviorPoint(70f, 70f, 70)
            )
        )
        val probe = enrolled.copy(
            strokePoints = listOf(
                BehaviorPoint(0f, 20f, 0),
                BehaviorPoint(10f, 10f, 10),
                BehaviorPoint(20f, 0f, 20),
                BehaviorPoint(30f, -10f, 30),
                BehaviorPoint(40f, -20f, 40),
                BehaviorPoint(50f, -30f, 50),
                BehaviorPoint(60f, -40f, 60),
                BehaviorPoint(70f, -50f, 70)
            )
        )

        assertTrue(BehaviorScorer.score(enrolled, probe) < 0.70f)
    }

    @Test
    fun combinedScoreFailsClosedWhenSignatureIsMissing() {
        val enrolled = BehaviorSample(
            mode = BehaviorMode.COMBINED,
            phrase = "open sesame",
            keyDownTimesMs = listOf(0, 100, 210),
            keyUpTimesMs = listOf(40, 140, 260),
            strokePoints = listOf(
                BehaviorPoint(0f, 0f, 0),
                BehaviorPoint(1f, 1f, 1),
                BehaviorPoint(2f, 2f, 2),
                BehaviorPoint(3f, 3f, 3),
                BehaviorPoint(4f, 4f, 4),
                BehaviorPoint(5f, 5f, 5),
                BehaviorPoint(6f, 6f, 6),
                BehaviorPoint(7f, 7f, 7)
            )
        )
        val probe = enrolled.copy(strokePoints = emptyList())

        assertTrue(BehaviorScorer.score(enrolled, probe) == 0f)
    }

    @Test
    fun qualityRejectsTooShortTypingPhrase() {
        val sample = BehaviorSample(
            mode = BehaviorMode.TYPING,
            phrase = "pin",
            keyDownTimesMs = listOf(0, 100, 210, 330, 460),
            keyUpTimesMs = listOf(50, 150, 260, 380, 520),
            strokePoints = emptyList()
        )

        assertTrue(sample.qualityIssue() == BehaviorQualityIssue.TYPING_PHRASE_TOO_SHORT)
    }

    @Test
    fun qualityRejectsPasteLikeTypingSample() {
        val sample = BehaviorSample(
            mode = BehaviorMode.TYPING,
            phrase = "open sesame pasted",
            keyDownTimesMs = listOf(0, 120, 260, 390, 520),
            keyUpTimesMs = listOf(80, 190, 330, 455, 595),
            strokePoints = emptyList()
        )

        assertEquals(BehaviorQualityIssue.TYPING_EVENT_MISMATCH, sample.qualityIssue())
    }

    @Test
    fun qualityRejectsUltraFastTypingSample() {
        val sample = BehaviorSample(
            mode = BehaviorMode.TYPING,
            phrase = "open sesame",
            keyDownTimesMs = listOf(0, 4, 8, 12, 16, 20, 24, 28, 32, 36, 40),
            keyUpTimesMs = listOf(2, 6, 10, 14, 18, 22, 26, 30, 34, 38, 42),
            strokePoints = emptyList()
        )

        assertEquals(BehaviorQualityIssue.TYPING_TIMING_TOO_FAST, sample.qualityIssue())
    }

    @Test
    fun qualityAcceptsUsableSignature() {
        val sample = BehaviorSample(
            mode = BehaviorMode.SIGNATURE,
            phrase = null,
            keyDownTimesMs = emptyList(),
            keyUpTimesMs = emptyList(),
            strokePoints = signaturePoints(stepMs = 10) + signaturePoints(
                stepMs = 10,
                xOffset = 8f,
                yOffset = 28f,
                strokeId = 1
            )
        )

        assertTrue(sample.qualityIssue() == BehaviorQualityIssue.NONE)
    }

    @Test
    fun qualityRejectsStraightLineSignature() {
        val sample = BehaviorSample(
            mode = BehaviorMode.SIGNATURE,
            phrase = null,
            keyDownTimesMs = emptyList(),
            keyUpTimesMs = emptyList(),
            strokePoints = (0 until 20).map { index ->
                BehaviorPoint(index * 8f, index * 8f, index * 18L)
            }
        )

        assertEquals(BehaviorQualityIssue.SIGNATURE_SHAPE_TOO_SIMPLE, sample.qualityIssue())
    }

    @Test
    fun qualityRejectsDuplicatePointHeavySignature() {
        val base = signaturePoints(stepMs = 15)
        val sample = BehaviorSample(
            mode = BehaviorMode.SIGNATURE,
            phrase = null,
            keyDownTimesMs = emptyList(),
            keyUpTimesMs = emptyList(),
            strokePoints = base.flatMap { point -> listOf(point, point.copy(timestampMs = point.timestampMs + 1)) }
        )

        assertEquals(BehaviorQualityIssue.SIGNATURE_DUPLICATE_POINTS, sample.qualityIssue())
    }

    @Test
    fun typingScoreRejectsDifferentDigraphRhythm() {
        val enrolled = BehaviorSample(
            mode = BehaviorMode.TYPING,
            phrase = "open sesame",
            keyDownTimesMs = listOf(0, 120, 260, 390, 520),
            keyUpTimesMs = listOf(80, 190, 330, 455, 595),
            strokePoints = emptyList()
        )
        val probe = enrolled.copy(
            keyDownTimesMs = listOf(0, 300, 640, 980, 1330),
            keyUpTimesMs = listOf(40, 340, 685, 1020, 1380)
        )

        assertTrue(BehaviorScorer.score(enrolled, probe) < 0.82f)
    }

    @Test
    fun fromBundleRejectsOversizedTypingPayload() {
        val extras = Bundle().apply {
            putString(BehaviorSample.EXTRA_BEHAVIOR_MODE, BehaviorMode.TYPING.name)
            putString(BehaviorSample.EXTRA_BEHAVIOR_PHRASE, "open sesame")
            putLongArray(BehaviorSample.EXTRA_BEHAVIOR_KEY_DOWNS, LongArray(513) { it * 100L })
            putLongArray(BehaviorSample.EXTRA_BEHAVIOR_KEY_UPS, LongArray(513) { it * 100L + 40L })
        }

        assertTrue(BehaviorSample.fromBundle(extras) == null)
    }

    @Test
    fun fromBundleRejectsInvalidSignaturePointPayload() {
        val points = FloatArray(16 * 6) { index -> index.toFloat() }
        points[0] = Float.NaN
        val extras = Bundle().apply {
            putString(BehaviorSample.EXTRA_BEHAVIOR_MODE, BehaviorMode.SIGNATURE.name)
            putFloatArray(BehaviorSample.EXTRA_BEHAVIOR_POINTS, points)
            putInt(BehaviorSample.EXTRA_BEHAVIOR_POINTS_STRIDE, 6)
        }

        assertTrue(BehaviorSample.fromBundle(extras) == null)
    }

    @Test
    fun signatureScoreAcceptsSimilarPathWithDifferentSpeed() {
        val enrolled = BehaviorSample(
            mode = BehaviorMode.SIGNATURE,
            phrase = null,
            keyDownTimesMs = emptyList(),
            keyUpTimesMs = emptyList(),
            strokePoints = signaturePoints(stepMs = 10)
        )
        val probe = enrolled.copy(strokePoints = signaturePoints(stepMs = 17, xOffset = 3f, yOffset = 2f))

        assertTrue(BehaviorScorer.score(enrolled, probe) >= 0.82f)
    }

    private fun signaturePoints(
        stepMs: Long,
        xOffset: Float = 0f,
        yOffset: Float = 0f,
        strokeId: Int = 0
    ): List<BehaviorPoint> {
        return listOf(
            BehaviorPoint(0f + xOffset, 0f + yOffset, 0L * stepMs, 0.4f, 0.5f, strokeId),
            BehaviorPoint(10f + xOffset, 6f + yOffset, 1L * stepMs, 0.45f, 0.5f, strokeId),
            BehaviorPoint(20f + xOffset, 14f + yOffset, 2L * stepMs, 0.5f, 0.5f, strokeId),
            BehaviorPoint(34f + xOffset, 18f + yOffset, 3L * stepMs, 0.55f, 0.5f, strokeId),
            BehaviorPoint(48f + xOffset, 16f + yOffset, 4L * stepMs, 0.6f, 0.5f, strokeId),
            BehaviorPoint(61f + xOffset, 8f + yOffset, 5L * stepMs, 0.55f, 0.5f, strokeId),
            BehaviorPoint(72f + xOffset, 12f + yOffset, 6L * stepMs, 0.5f, 0.5f, strokeId),
            BehaviorPoint(86f + xOffset, 22f + yOffset, 7L * stepMs, 0.45f, 0.5f, strokeId),
            BehaviorPoint(100f + xOffset, 26f + yOffset, 8L * stepMs, 0.4f, 0.5f, strokeId)
        )
    }
}
