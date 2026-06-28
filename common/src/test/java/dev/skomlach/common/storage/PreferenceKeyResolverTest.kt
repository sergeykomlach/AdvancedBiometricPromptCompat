package dev.skomlach.common.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class PreferenceKeyResolverTest {

    @Test
    fun `resolved key is cached after first scan`() {
        val resolver = PreferenceKeyResolver()
        val scans = AtomicInteger(0)

        val first = resolver.resolve(
            plainKey = "face_templates",
            directLookup = { null },
            scanStoredKeys = {
                scans.incrementAndGet()
                sequenceOf("enc_a", "enc_b")
            },
            decryptStoredKey = { encrypted ->
                when (encrypted) {
                    "enc_b" -> "face_templates"
                    else -> null
                }
            }
        )
        val second = resolver.resolve(
            plainKey = "face_templates",
            directLookup = { null },
            scanStoredKeys = {
                scans.incrementAndGet()
                emptySequence()
            },
            decryptStoredKey = { null }
        )

        assertEquals("enc_b", first)
        assertEquals("enc_b", second)
        assertEquals(1, scans.get())
    }

    @Test
    fun `remembered key is returned without scan`() {
        val resolver = PreferenceKeyResolver()
        resolver.remember("lockout", "enc_lockout")

        val resolved = resolver.resolve(
            plainKey = "lockout",
            directLookup = { null },
            scanStoredKeys = {
                throw AssertionError("scan should not be used for remembered key")
            },
            decryptStoredKey = { null }
        )

        assertEquals("enc_lockout", resolved)
    }

    @Test
    fun `forgotten key no longer resolves from cache`() {
        val resolver = PreferenceKeyResolver()
        resolver.remember("lockout", "enc_lockout")
        resolver.forget("lockout")

        val resolved = resolver.resolve(
            plainKey = "lockout",
            directLookup = { null },
            scanStoredKeys = { emptySequence() },
            decryptStoredKey = { null }
        )

        assertNull(resolved)
    }
}
