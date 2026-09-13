package dev.skomlach.biometric.compat.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TruncatedTextFitTest {
    @Test
    fun fittingTextIsPreservedAfterOneMeasurement() {
        val source = StringBuilder("Вхід 請驗證 مرحبا")
        var calls = 0
        val result = truncatePromptText(source, 7) { calls++; true }
        assertSame(source, result)
        assertEquals(1, calls)
    }

    @Test
    fun emptyTextDoesNotNeedMeasurement() {
        assertEquals("", truncatePromptText("", 7) { error("Unexpected measurement") })
    }

    @Test
    fun longTextKeepsTheFieldSpecificReserve() {
        val source = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val fits: (CharSequence) -> Boolean = { it.length <= 12 }
        assertEquals("ABC..", truncatePromptText(source, 7, fits))
        assertEquals("ABCDEFGHI..", truncatePromptText(source, 1, fits))
    }

    @Test
    fun wideGlyphsAreMeasuredInsteadOfUsingACharacterThreshold() {
        val fits: (CharSequence) -> Boolean = { value -> value.sumOf { if (it == 'W') 4 else 1 } <= 12 }
        assertEquals("iiiiiiii", truncatePromptText("iiiiiiii", 0, fits))
        val result = truncatePromptText("WWWWWWWW", 0, fits)
        assertTrue(fits(result))
        assertTrue(result.toString().endsWith(".."))
    }

    @Test
    fun narrowWidthDoesNotProduceNegativeSubstringIndices() {
        assertEquals("..", truncatePromptText("Long title", 7) { it.length <= 2 })
        assertEquals("", truncatePromptText("Long title", 7) { it.length <= 1 })
    }

    @Test
    fun truncationDoesNotSplitASurrogatePair() {
        val result = truncatePromptText("abc😀defghijk", 0) { it.length <= 6 }.toString()
        assertEquals("abc..", result)
        assertFalse(result.any { Character.isSurrogate(it) })
    }

    @Test
    fun newlineDoesNotPassTheSingleLineFitCheck() {
        val fits: (CharSequence) -> Boolean = { '\n' !in it && it.length <= 20 }
        val result = truncatePromptText("Some text\nnext line", 1, fits)
        assertTrue(fits(result))
        assertTrue(result.toString().endsWith(".."))
    }

    @Test
    fun equalTextUsesIndependentCacheEntriesForEveryField() {
        val keys = PromptTextField.entries.map { buildPromptTextCacheKey(it, "Same long text") }
        assertEquals(4, keys.toSet().size)
        assertNotEquals(buildPromptTextCacheKey(PromptTextField.TITLE, "A"),
            buildPromptTextCacheKey(PromptTextField.TITLE, "B"))
    }
}
