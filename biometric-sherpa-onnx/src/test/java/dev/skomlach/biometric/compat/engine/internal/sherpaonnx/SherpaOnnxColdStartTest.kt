package dev.skomlach.biometric.compat.engine.internal.sherpaonnx

import dev.skomlach.biometric.compat.custom.SoftwareBiometricInitializationState
import dev.skomlach.biometric.compat.custom.newSoftwareBiometricWorker
import java.io.IOException
import java.util.concurrent.ExecutorService

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class SherpaOnnxColdStartTest {
    @Test fun availabilityAndIdentityQueriesDoNotInitializeTheRuntime() {
        val backend = Backend()
        val engine = SherpaOnnxVoiceEngine(backend)
        repeat(3) {
            assertFalse(engine.isAvailable())
            assertNull(engine.templateIdentity)
        }
        assertEquals(0, backend.availabilityReads)
        assertEquals(0, backend.preparations)
    }

    @Test fun extractionBeforePreparationDoesNotStartNativeWork() {
        val backend = Backend()
        val engine = SherpaOnnxVoiceEngine(backend)
        assertEquals(VoiceQualityIssue.EMBEDDING_INVALID, engine.extractEmbedding(sample())?.qualityIssue)
        assertEquals(0, backend.extractions)
        assertEquals(0, backend.preparations)
    }

    @Test fun repeatedPreparationReusesOneInitializedRuntime() {
        val backend = Backend()
        val engine = SherpaOnnxVoiceEngine(backend)
        repeat(3) { assertTrue(engine.prepare()) }
        val readsAfterPreparation = backend.availabilityReads
        repeat(3) { assertTrue(engine.isAvailable()) }
        assertEquals(readsAfterPreparation, backend.availabilityReads)
        assertEquals(1, backend.preparations)
        assertEquals("model-a", engine.templateIdentity)
    }

    @Test fun queriesRemainNonblockingWhileNativePreparationIsRunning() {
        val entered = CountDownLatch(1)
        val proceed = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        val backend = Backend {
            entered.countDown()
            check(proceed.await(5, TimeUnit.SECONDS))
        }
        val engine = SherpaOnnxVoiceEngine(backend)
        try {
            val preparing = executor.submit<Boolean> { engine.prepare() }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            assertEquals(SoftwareBiometricInitializationState.PREPARING, engine.initializationState)
            assertFalse(executor.submit<Boolean> { engine.isAvailable() }.get(1, TimeUnit.SECONDS))
            assertNull(engine.templateIdentity)
            proceed.countDown()
            assertTrue(preparing.get(5, TimeUnit.SECONDS))
            assertTrue(engine.isAvailable())
        } finally {
            proceed.countDown()
            executor.shutdownNow()
        }
    }

    @Test fun lazyFactoryRunsOnlyOnTheExplicitPreparationWorker() {
        val caller = Thread.currentThread()
        var factoryThread: Thread? = null
        val engine = SherpaOnnxVoiceEngine {
            factoryThread = Thread.currentThread()
            Backend()
        }
        assertNull(factoryThread)
        assertFalse(engine.isAvailable())
        assertNull(engine.templateIdentity)
        assertEquals(SoftwareBiometricInitializationState.NEW, engine.initializationState)
        val worker = newSoftwareBiometricWorker("SherpaColdStartTest") as ExecutorService
        try {
            assertTrue(worker.submit<Boolean> { engine.prepare() }.get(5, TimeUnit.SECONDS))
            assertNotSame(caller, factoryThread)
            assertEquals("SherpaColdStartTest", factoryThread?.name)
            assertEquals(SoftwareBiometricInitializationState.READY, engine.initializationState)
        } finally {
            worker.shutdownNow()
        }
    }

    @Test fun concurrentPreparationCreatesOnlyOneRuntime() {
        val entered = CountDownLatch(1)
        val proceed = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        var creates = 0
        val backend = Backend {
            entered.countDown()
            check(proceed.await(5, TimeUnit.SECONDS))
        }
        val engine = SherpaOnnxVoiceEngine { creates++; backend }
        try {
            val first = executor.submit<Boolean> { engine.prepare() }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val second = executor.submit<Boolean> { engine.prepare() }
            proceed.countDown()
            assertTrue(first.get(5, TimeUnit.SECONDS))
            assertTrue(second.get(5, TimeUnit.SECONDS))
            assertEquals(1, creates)
            assertEquals(1, backend.preparations)
        } finally {
            proceed.countDown()
            executor.shutdownNow()
        }
    }

    @Test fun missingModelRuntimeAndAbiProduceACachedInitialFailure() {
        for (failure in listOf(IOException("model"), NoClassDefFoundError("AAR"), UnsatisfiedLinkError("ABI"))) {
            var creates = 0
            val engine = SherpaOnnxVoiceEngine { creates++; throw failure }
            assertEquals(SoftwareBiometricInitializationState.NEW, engine.initializationState)
            repeat(3) {
                assertFalse(engine.prepare())
                assertFalse(engine.isAvailable())
                assertNull(engine.templateIdentity)
            }
            assertEquals(1, creates)
            assertEquals(SoftwareBiometricInitializationState.FAILED, engine.initializationState)
        }
    }

    @Test fun laterRuntimeFailureDoesNotBecomeAnInitialFailureOrRetry() {
        var preparations = 0
        val engine = SherpaOnnxVoiceEngine(object : SherpaOnnxEmbeddingRuntime {
            override val templateIdentity = "model-a"
            override fun prepare(): Boolean { preparations++; return true }
            override fun isAvailable() = true
            override fun extractEmbedding(pcm: FloatArray, sampleRateHz: Int): FloatArray? =
                throw UnsatisfiedLinkError("runtime fault")
        })
        assertTrue(engine.prepare())
        assertEquals(VoiceQualityIssue.EMBEDDING_INVALID, engine.extractEmbedding(sample())?.qualityIssue)
        assertFalse(engine.isAvailable())
        assertFalse(engine.prepare())
        assertEquals(SoftwareBiometricInitializationState.READY, engine.initializationState)
        assertEquals(1, preparations)
    }

    @Test fun missingPreparedIdentityFailsClosed() {
        val engine = SherpaOnnxVoiceEngine(object : SherpaOnnxEmbeddingRuntime {
            override fun prepare() = true
            override fun isAvailable() = true
            override fun extractEmbedding(pcm: FloatArray, sampleRateHz: Int): FloatArray? = null
        })
        assertFalse(engine.prepare())
        assertFalse(engine.isAvailable())
        assertEquals(SoftwareBiometricInitializationState.FAILED, engine.initializationState)
    }

    private fun sample() = VoiceSample(16_000, floatArrayOf(0.1f, 0.2f), null, null)

    private class Backend(private val onPrepare: () -> Unit = {}) : SherpaOnnxEmbeddingRuntime {
        var preparations = 0
        var availabilityReads = 0
        var extractions = 0
        override val templateIdentity = "model-a"
        override fun prepare(): Boolean { preparations++; onPrepare(); return true }
        override fun isAvailable(): Boolean { availabilityReads++; return true }
        override fun extractEmbedding(pcm: FloatArray, sampleRateHz: Int): FloatArray {
            extractions++
            return FloatArray(8) { 0.1f }
        }
    }
}
