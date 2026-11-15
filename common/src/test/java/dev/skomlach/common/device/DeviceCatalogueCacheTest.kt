package dev.skomlach.common.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class DeviceCatalogueCacheTest {
    @Test
    fun knownCataloguesRejectWrongRootsAndUnusableRecords() {
        assertFalse(isUsableCatalogue("by_model.json", "[]"))
        assertFalse(isUsableCatalogue("by_model.json", """{"model":[{}]}"""))
        assertTrue(isUsableCatalogue("by_model.json", """{"model":[{"brand":"Test","name":"Phone"}]}"""))
        assertFalse(isUsableCatalogue("devices.json", """[{"brand":"Test","name":null}]"""))
        assertFalse(isUsableCatalogue("devices.json", """[{"brand":"Test","name":"Phone"}]"""))
        assertTrue(isUsableCatalogue("devices.json", """[{"brand":"Test","name":"Phone","specs":{"sensors":"gyro"}}]"""))
        assertFalse(isUsableCatalogue("specifications.json", """[{"phone_name":" "}]"""))
        assertFalse(isUsableCatalogue("specifications.json", """[{"phone_name":"Phone","Comms":{"Sensors":"gyro"}}] null"""))
        assertTrue(isUsableCatalogue("specifications.json", """[{"phone_name":"Phone","Comms":{"Sensors":"gyro"}}]"""))
    }

    @Test
    fun unusableCacheAndUsableCacheMissBothFallBackToBundledCatalogue() {
        val context = RuntimeEnvironment.getApplication()
        val name = "devices.json"
        val cache = File(context.cacheDir, name)
        val url = "https://example.test/$name"
        val bundled = context.assets.open("devices/$name").bufferedReader().use { it.readText() }

        try {
            cache.writeText("[]")
            assertTrue(DataProviders.getOrCacheJSON(url) == bundled)
            val cached = """[{"brand":"Test","name":"Different profile","specs":{"sensors":"gyro"}}]"""
            cache.writeText(cached)
            assertEquals(cached, DataProviders.getOrCacheJSON(url))
            assertEquals("bundled", DataProviders.findInCacheOrAsset(url) {
                if (it == bundled) "bundled" else null
            })
        } finally {
            cache.delete()
        }
    }
}
