package dev.skomlach.biometric.compat.engine.internal.behavior

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BehaviorStrokeIdentityTest {
    @Test fun identicalGeometryWithDifferentPenLiftsIsNotAnIdenticalSignature() {
        val continuous = signature(listOf(0, 0, 0, 0))
        val lifted = signature(listOf(0, 1, 2, 3))

        assertEquals(1f, BehaviorScorer.score(continuous, continuous), 0.00001f)
        assertTrue(BehaviorScorer.score(continuous, lifted) < 0.82f)
    }

    @Test fun equivalentStrokeLabelsDoNotChangeTheMatch() {
        val enrolled = signature(listOf(0, 1, 2, 3))
        val renumbered = signature(listOf(7, 19, 42, 99))

        assertEquals(BehaviorScorer.score(enrolled, enrolled),
            BehaviorScorer.score(enrolled, renumbered), 0.00001f)
    }

    private fun signature(ids: List<Int>): BehaviorSample {
        val vertices = listOf(0f to 0f, 80f to 0f, 80f to 80f, 0f to 80f, 0f to 0f)
        val points = ids.flatMapIndexed { stroke, id ->
            val (ax, ay) = vertices[stroke]
            val (bx, by) = vertices[stroke + 1]
            (0..8).map { step ->
                val ratio = step / 8f
                BehaviorPoint(ax + (bx - ax) * ratio, ay + (by - ay) * ratio,
                    stroke * 160L + step * 20L, 0.5f, 0.4f, id)
            }
        }
        return BehaviorSample(BehaviorMode.SIGNATURE, null, emptyList(), emptyList(), points)
    }
}
