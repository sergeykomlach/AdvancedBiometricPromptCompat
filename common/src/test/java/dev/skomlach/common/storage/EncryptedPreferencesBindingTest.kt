package dev.skomlach.common.storage

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class EncryptedPreferencesBindingTest {
    @Test fun movingCiphertextBetweenLogicalKeysIsRejected() {
        val fixture = ProtectedPreferenceFixture()
        val raw = fixture.raw
        val prefs = fixture.open()
        prefs.edit().putString("alice", "alice-secret").commit()
        val first = raw.all.toMap()
        prefs.edit().putString("bob", "bob-secret").commit()
        val aliceKey = first.keys.single { it.startsWith("e3_") }
        val bobKey = (raw.all.keys - first.keys).single()
        val alice = raw.getString(aliceKey, null)
        val bob = raw.getString(bobKey, null)
        raw.edit().putString(aliceKey, bob).putString(bobKey, alice).commit()
        assertThrows(ProtectedStorageUnavailableException::class.java) { prefs.getString("alice", "default") }
        assertThrows(ProtectedStorageUnavailableException::class.java) { prefs.getString("bob", "default") }
    }

    @Test fun copyingAnEntirePreferenceFileToAnotherFileIsRejected() {
        val source = ProtectedPreferenceFixture()
        source.open().edit().putString("identity", "alice").commit()
        val target = ProtectedPreferenceFixture()
        target.raw.edit().apply { source.raw.all.forEach { (key, value) -> putString(key, value as String) } }.commit()
        assertThrows(ProtectedStorageUnavailableException::class.java) { target.open().getString("identity", null) }
    }

    @Test fun firstReadDurablyUpgradesLegacyBeforeReturningItsValue() {
        val fixture = ProtectedPreferenceFixture()
        val raw = fixture.raw
        fixture.legacyString("identity", "alice")
        assertEquals("alice", fixture.open().getString("identity", null))
        assertTrue("Values must use the explicit authenticated v3 envelope", raw.all.values.all { (it as String).startsWith("v3_") })
    }

    @Test fun restoringLegacyAfterCompletedMigrationIsRejected() {
        val fixture = ProtectedPreferenceFixture()
        val raw = fixture.raw
        fixture.legacyString("identity", "alice")
        val legacy = raw.all.toMap()
        fixture.open().getString("identity", null)
        raw.edit().clear().apply { legacy.forEach { (key, value) -> putString(key, value as String) } }.commit()
        fixture.simulateProcessRestart()
        assertThrows(ProtectedStorageUnavailableException::class.java) { fixture.open().getString("identity", null) }
    }

    @Test fun valuesCannotMoveBetweenFilesEvenIfTargetNamesAndHeaderRemainValid() {
        val source = ProtectedPreferenceFixture()
        val target = ProtectedPreferenceFixture()
        source.open().edit().putString("identity", "alice").commit()
        target.open().edit().putString("identity", "bob").commit()
        val sourceName = source.raw.all.keys.single { it.startsWith("e3_") }
        val targetName = target.raw.all.keys.single { it.startsWith("e3_") }
        target.raw.edit().putString(targetName, source.raw.getString(sourceName, null)).commit()
        assertThrows(ProtectedStorageUnavailableException::class.java) { target.open().getString("identity", null) }
    }

    @Test fun legacyInjectionAndMissingHeaderAreRejectedAfterMigration() {
        val f = ProtectedPreferenceFixture()
        f.open().edit().putString("identity", "alice").commit()
        f.legacyString("injected", "not-trusted")
        assertThrows(ProtectedStorageUnavailableException::class.java) { f.open().getString("identity", null) }
        f.open().edit().clear().putString("identity", "reset").commit()
        f.raw.edit().remove("__esp_v3_format__").commit()
        f.simulateProcessRestart()
        assertThrows(ProtectedStorageUnavailableException::class.java) { f.open().getString("identity", null) }
        assertEquals(0, f.legacyReads)
    }

    @Test fun ciphertextCannotBeReusedAcrossNameValueAndHeaderDomains() {
        val f = ProtectedPreferenceFixture()
        f.open().edit().putString("secret", "value").commit()
        val name = f.raw.all.keys.single { it.startsWith("e3_") }
        val value = f.raw.getString(name, null)
        f.raw.edit().putString("__esp_v3_format__", value).commit()
        assertThrows(ProtectedStorageUnavailableException::class.java) { f.open().getString("secret", null) }
    }
}
