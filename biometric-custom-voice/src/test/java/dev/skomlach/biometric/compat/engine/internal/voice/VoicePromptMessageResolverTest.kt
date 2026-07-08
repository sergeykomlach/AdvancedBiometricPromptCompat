package dev.skomlach.biometric.compat.engine.internal.voice

import dev.skomlach.biometric.custom.voice.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoicePromptMessageResolverTest {
    @Test
    fun resolvesFirstEnrollInstructionWithPhrase() {
        val render = VoicePromptMessageResolver.resolve(
            state = VoicePromptState.EnrollInstruction(
                step = 1,
                total = 3,
                retryReason = null
            ),
            phrase = "open sesame"
        )

        assertTrue(render.primaryMessage.contains("1"))
        assertTrue(render.primaryMessage.contains("3"))
        assertTrue(render.primaryMessage.contains("open sesame"))
    }

    @Test
    fun resolvesRetryInstructionForShortSample() {
        val render = VoicePromptMessageResolver.resolve(
            state = VoicePromptState.EnrollInstruction(
                step = 2,
                total = 3,
                retryReason = VoiceRetryReason.SAMPLE_TOO_SHORT
            ),
            phrase = "open sesame"
        )

        assertTrue(render.primaryMessage.contains("2"))
        assertTrue(render.primaryMessage.contains("3"))
        assertTrue(render.secondaryMessage!!.isNotBlank())
    }

    @Test
    fun resolvesMatchingStateWithoutListeningLanguage() {
        val render = VoicePromptMessageResolver.resolve(
            state = VoicePromptState.Matching,
            phrase = null
        )

        assertEquals("Checking voice", render.primaryMessage)
    }

    @Test
    fun resolvesUsingLocalizedStringProviderWhenAvailable() {
        val render = VoicePromptMessageResolver.resolve(
            state = VoicePromptState.AuthInstruction(
                retryReason = VoiceRetryReason.SAMPLE_TOO_SHORT
            ),
            phrase = "open sesame",
            stringResolver = { resId: Int, formatArgs: Array<out Any?> ->
                when (resId) {
                    R.string.biometriccompat_voice_prompt_auth_instruction -> "Промовте кодову фразу."
                    R.string.biometriccompat_voice_overlay_phrase -> "Фраза: ${formatArgs[0]}"
                    R.string.biometriccompat_voice_prompt_retry_sample_too_short -> "Фраза була закороткою."
                    else -> null
                }
            }
        )

        assertEquals("Промовте кодову фразу. Фраза: open sesame", render.primaryMessage)
        assertEquals("Фраза була закороткою.", render.secondaryMessage)
    }
}
