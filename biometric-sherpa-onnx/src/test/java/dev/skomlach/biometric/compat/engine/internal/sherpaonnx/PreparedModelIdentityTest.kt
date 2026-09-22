package dev.skomlach.biometric.compat.engine.internal.sherpaonnx

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test

class PreparedModelIdentityTest {
    @Test fun readingIdentityDoesNotOpenModelAndPreparationIsCached() {
        var opens = 0
        var closes = 0
        val identity = PreparedModelIdentity {
            opens++
            object : ByteArrayInputStream("abc".toByteArray()) {
                override fun close() { closes++; super.close() }
            }
        }
        repeat(3) { assertNull(identity.value) }
        assertEquals(0, opens)
        val expected = "sherpa-speaker:waveform-v1:" +
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        assertEquals(expected, identity.prepare())
        assertEquals(expected, identity.prepare())
        assertEquals(expected, identity.value)
        assertEquals(1, opens)
        assertEquals(1, closes)
    }

    @Test fun modelUpdateChangesIdentityWithoutComparingEmbeddingDimensions() {
        val before = PreparedModelIdentity { ByteArrayInputStream(byteArrayOf(1, 2, 3)) }
        val after = PreparedModelIdentity { ByteArrayInputStream(byteArrayOf(1, 2, 4)) }
        assertNotEquals(before.prepare(), after.prepare())
    }

    @Test fun failedReadDoesNotPublishPartialHashAndCanRetry() {
        var fail = true
        var closed = false
        val identity = PreparedModelIdentity {
            if (!fail) ByteArrayInputStream("abc".toByteArray())
            else object : InputStream() {
                override fun read(): Int = throw IOException("broken asset")
                override fun close() { closed = true }
            }
        }
        assertNull(identity.prepare())
        assertNull(identity.value)
        assertTrue(closed)
        fail = false
        assertNotNull(identity.prepare())
    }

    @Test fun uiReadDoesNotWaitForPreparationAndConcurrentPreparationHashesOnce() {
        val opened = CountDownLatch(1)
        val continueRead = CountDownLatch(1)
        val opens = AtomicInteger()
        val executor = Executors.newFixedThreadPool(3)
        val identity = PreparedModelIdentity {
            opens.incrementAndGet()
            opened.countDown()
            check(continueRead.await(5, TimeUnit.SECONDS))
            ByteArrayInputStream("abc".toByteArray())
        }
        try {
            val preparation = executor.submit<String?> { identity.prepare() }
            assertTrue(opened.await(5, TimeUnit.SECONDS))
            val secondPreparation = executor.submit<String?> { identity.prepare() }
            // A getter that takes the preparation monitor would time out here.
            assertNull(executor.submit<String?> { identity.value }.get(1, TimeUnit.SECONDS))
            continueRead.countDown()
            assertEquals(preparation.get(5, TimeUnit.SECONDS), secondPreparation.get(5, TimeUnit.SECONDS))
            assertEquals(1, opens.get())
        } finally {
            continueRead.countDown()
            executor.shutdownNow()
        }
    }

    @Test fun engineIdentityGetterDoesNotTriggerPreparation() {
        val identity = PreparedModelIdentity { ByteArrayInputStream("abc".toByteArray()) }
        var preparations = 0
        val engine = SherpaOnnxVoiceEngine(object : SherpaOnnxEmbeddingRuntime {
            override val templateIdentity get() = identity.value
            override fun isAvailable() = true
            override fun prepare(): Boolean { preparations++; return identity.prepare() != null }
            override fun extractEmbedding(pcm: FloatArray, sampleRateHz: Int): FloatArray? = null
        })
        repeat(3) { assertNull(engine.templateIdentity) }
        assertEquals(0, preparations)
        assertTrue(prepareVoiceEngine(engine))
        assertNotNull(engine.templateIdentity)
        assertEquals(1, preparations)
    }
}
