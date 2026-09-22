package dev.skomlach.biometric.compat.engine.internal.voice

import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceEngineSelectorTest {
    @Test fun brokenProviderConstructionDoesNotHideFollowingEngine() {
        val healthy = FakeProvider(priority = 1, available = true)
        val providers = Iterable {
            object : Iterator<VoiceEngineProvider> {
                var index = 0
                override fun hasNext() = index < 2
                override fun next(): VoiceEngineProvider {
                    if (index++ == 0) throw java.util.ServiceConfigurationError("broken constructor")
                    return healthy
                }
            }
        }
        assertSame(healthy.engine, VoiceEngineSelector.select(providers))
    }

    @Test fun availabilityLinkageFailureDoesNotHideFollowingEngine() {
        val broken = object : VoiceEngineProvider {
            override val priority = 10
            override fun createEngine() = object : VoiceEngine {
                override fun isAvailable(): Boolean = throw UnsatisfiedLinkError("missing native runtime")
                override fun extractEmbedding(sample: VoiceSample): VoiceEmbeddingResult? = null
            }
        }
        val healthy = FakeProvider(priority = 1, available = true)
        assertSame(healthy.engine, VoiceEngineSelector.select(listOf(broken, healthy)))
    }

    @Test
    fun selectUsesHighestPriorityAvailableProvider() {
        val lowPriority = FakeProvider(priority = 1, available = true)
        val highPriority = FakeProvider(priority = 10, available = true)

        val selected = VoiceEngineSelector.select(listOf(lowPriority, highPriority))

        assertSame(highPriority.engine, selected)
    }

    @Test
    fun selectIgnoresUnavailableProviders() {
        val unavailable = FakeProvider(priority = 10, available = false)
        val available = FakeProvider(priority = 1, available = true)

        val selected = VoiceEngineSelector.select(listOf(unavailable, available))

        assertSame(available.engine, selected)
    }

    @Test
    fun selectFallsBackToCepstralEngineWhenNoProviderIsAvailable() {
        val selected = VoiceEngineSelector.select(emptyList())

        assertTrue(selected is CepstralVoiceEngine)
    }

    private class FakeProvider(
        override val priority: Int,
        available: Boolean
    ) : VoiceEngineProvider {
        val engine = FakeEngine(available)

        override fun createEngine(): VoiceEngine = engine
    }

    private class FakeEngine(
        private val available: Boolean
    ) : VoiceEngine {
        override fun isAvailable(): Boolean = available

        override fun extractEmbedding(sample: VoiceSample): VoiceEmbeddingResult? = null
    }
}
