package dev.skomlach.common.storage

import android.content.SharedPreferences
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.nio.ByteBuffer

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class EncryptedPreferencesMigrationTest {
    @Test fun allLegacyTypesAndOldKeysMigrateTogetherWithoutLogicalNotifications() {
        val f = ProtectedPreferenceFixture()
        f.legacyString("", "порожній ключ")
        f.legacyString(null, "null-key")
        f.legacyString("null", "__NULL__")
        f.legacyString("legacy-old", "old-key-data", true)
        f.legacy("int", ByteBuffer.allocate(8).putInt(2).putInt(17).array())
        f.legacy("long", ByteBuffer.allocate(12).putInt(3).putLong(Long.MAX_VALUE).array())
        f.legacy("float", ByteBuffer.allocate(8).putInt(4).putFloat(1.25f).array())
        f.legacy("boolean", ByteBuffer.allocate(5).putInt(5).put(1).array())
        f.legacy("set", ByteBuffer.allocate(18).putInt(1).putInt(1).put('a'.code.toByte()).putInt(5).put("hello".toByteArray()).array())
        f.legacy("empty-set", ByteBuffer.allocate(4).putInt(1).array())
        val prefs = f.open()
        var notifications = 0
        prefs.registerOnSharedPreferenceChangeListener { _, _ -> notifications++ }
        val all = prefs.all
        assertEquals("порожній ключ", all[""])
        assertEquals("null-key", all[null])
        assertNull(all["null"])
        assertEquals("old-key-data", all["legacy-old"])
        assertEquals(17, all["int"])
        assertEquals(Long.MAX_VALUE, all["long"])
        assertEquals(1.25f, all["float"])
        assertEquals(true, all["boolean"])
        assertEquals(setOf("a", "hello"), all["set"])
        assertEquals(emptySet<String>(), all["empty-set"])
        assertEquals(0, notifications)
        assertEquals(1, f.fence.begins)
        assertEquals(PreferenceMigrationFence.COMMITTED, f.fence.value)
        assertTrue(f.raw.all.keys.all { it.startsWith("e3_") || it == "__esp_v3_format__" })
        val migrated = f.raw.all.toMap()
        val legacyReads = f.legacyReads
        f.simulateProcessRestart()
        assertEquals(all, f.open().all)
        assertEquals(legacyReads, f.legacyReads)
        assertEquals(migrated, f.raw.all)
    }

    @Test fun v3SeparatesNullableEmptyAndSentinelKeysAndPreservesClearPutOrder() {
        val f = ProtectedPreferenceFixture()
        val p = f.open()
        p.edit().putString(null, "null").putString("", "empty").putString("__NULL__", "literal").commit()
        assertEquals(setOf(null, "", "__NULL__"), p.keySet())
        assertEquals("null", p.getString(null, null))
        assertEquals("empty", p.getString("", null))
        assertEquals("literal", p.getString("__NULL__", null))
        p.edit().putString("one", "1").clear().putString("two", "2").commit()
        assertEquals(setOf("one", "two"), p.keySet())
        p.edit().clear().commit()
        assertTrue(p.all.isEmpty())
        assertEquals(1, f.raw.all.size) // authenticated header is retained
    }

    @Test fun malformedLegacyAbortsWithoutChangingDataOrBeginningMigration() {
        val f = ProtectedPreferenceFixture()
        f.legacyString("valid", "keep")
        f.legacyString("broken", "bad")
        val physical = f.raw.all.keys.last()
        f.raw.edit().putString(physical, "corrupt").commit()
        val before = f.raw.all.toMap()
        assertThrows(ProtectedStorageUnavailableException::class.java) { f.open().all }
        assertEquals(before, f.raw.all)
        assertEquals(0, f.fence.begins)
    }

    @Test fun duplicateLegacyAliasesAreNeverArbitrarilySelected() {
        val f = ProtectedPreferenceFixture()
        f.legacyString("same", "first")
        f.legacyString("same", "second")
        val before = f.raw.all.toMap()
        assertThrows(ProtectedStorageUnavailableException::class.java) { f.open().getString("same", null) }
        assertEquals(before, f.raw.all)
        assertEquals(0, f.fence.begins)
    }

    @Test fun invalidTypedLegacyRecordCannotBeLaunderedIntoV3() {
        val f = ProtectedPreferenceFixture()
        f.legacy("bad-type", ByteBuffer.allocate(4).putInt(99).array())
        val before = f.raw.all.toMap()
        assertThrows(ProtectedStorageUnavailableException::class.java) { f.open().all }
        assertEquals(before, f.raw.all)
        assertEquals(0, f.fence.begins)
    }

    @Test fun pendingFenceRetriesOnlyTheExactOriginalSnapshot() {
        val f = ProtectedPreferenceFixture()
        f.legacyString("identity", "alice")
        f.open(failingCommit(f.raw, updateMemory = false)).let { p ->
            assertThrows(ProtectedStorageUnavailableException::class.java) { p.getString("identity", null) }
        }
        assertNotNull(f.fence.value)
        f.simulateProcessRestart()
        assertEquals("alice", f.open().getString("identity", null))
        assertEquals(1, f.fence.begins)
        assertEquals(PreferenceMigrationFence.COMMITTED, f.fence.value)
    }

    @Test fun pendingFenceRejectsAChangedLegacySnapshotAfterRestart() {
        val f = ProtectedPreferenceFixture()
        f.legacyString("identity", "alice")
        assertThrows(ProtectedStorageUnavailableException::class.java) {
            f.open(failingCommit(f.raw, false)).getString("identity", null)
        }
        f.legacyString("new", "injected")
        val changed = f.raw.all.toMap()
        f.simulateProcessRestart()
        assertThrows(ProtectedStorageUnavailableException::class.java) { f.open().getString("identity", null) }
        assertEquals(changed, f.raw.all)
        // Only an explicit reset may discard the changed source and establish a fresh v3 store.
        f.open().edit().clear().putString("fresh", "new").commit()
        assertEquals(mapOf("fresh" to "new"), f.open().all)
        assertEquals(PreferenceMigrationFence.COMMITTED, f.fence.value)
    }

    @Test fun commitFailurePoisonsAllWrappersEvenIfBackingMemoryAlreadyChanged() {
        for (throws in listOf(false, true)) {
            val f = ProtectedPreferenceFixture()
            f.legacyString("identity", "alice")
            val other = f.open()
            val failed = f.open(failingCommit(f.raw, true, throws))
            var notifications = 0
            failed.registerOnSharedPreferenceChangeListener { _, _ -> notifications++ }
            assertThrows(ProtectedStorageUnavailableException::class.java) { failed.getString("identity", null) }
            assertTrue(f.raw.all.values.all { (it as String).startsWith("v3_") })
            assertThrows(ProtectedStorageUnavailableException::class.java) { other.getString("identity", null) }
            assertThrows(ProtectedStorageUnavailableException::class.java) { other.edit().clear().commit() }
            assertNotEquals(PreferenceMigrationFence.COMMITTED, f.fence.value)
            assertEquals(0, notifications)
        }
    }

    @Test fun interruptedCompletionResumesFromAuthenticatedV3WithoutLegacyFallback() {
        val f = ProtectedPreferenceFixture()
        f.legacyString("identity", "alice")
        f.fence.failComplete = true
        assertThrows(ProtectedStorageUnavailableException::class.java) { f.open().getString("identity", null) }
        val v3 = f.raw.all.toMap()
        f.fence.failComplete = false
        f.simulateProcessRestart()
        assertEquals("alice", f.open().getString("identity", null))
        assertEquals(v3, f.raw.all)
        assertEquals(PreferenceMigrationFence.COMMITTED, f.fence.value)
    }

    @Test fun unknownVersionAndTamperingNeverTryLegacyOrReturnDefaults() {
        for (bad in listOf("v99_unknown", "v3_AAAA", "legacy:looking:value")) {
            val f = ProtectedPreferenceFixture()
            f.open().edit().putString("secret", "value").commit()
            val key = f.raw.all.keys.single { it.startsWith("e3_") }
            f.raw.edit().putString(key, bad).commit()
            val before = f.raw.all.toMap()
            assertThrows(ProtectedStorageUnavailableException::class.java) { f.open().getString("secret", "default") }
            assertEquals(0, f.legacyReads)
            assertEquals(before, f.raw.all)
        }
    }

    @Test fun removingCorruptValuesDoesNotRequireTheirDecryption() {
        val f = ProtectedPreferenceFixture()
        val p = f.open()
        p.edit().putString("broken", "value").commit()
        val broken = f.raw.all.keys.single { it.startsWith("e3_") }
        p.edit().putString("keep", "kept").commit()
        f.raw.edit().putString(broken, "broken").commit()
        assertEquals(setOf("broken", "keep"), p.keySet())
        p.edit().remove("broken").commit()
        assertEquals("kept", p.getString("keep", null))
    }

    @Test fun editorsCreatedBeforeAnotherCommitCannotCreateDuplicateAliasesOrResurrectRemovedKeys() {
        val f = ProtectedPreferenceFixture()
        val first = f.open()
        val second = f.open()
        val oldEditor = first.edit().putString("same", "old")
        second.edit().putString("same", "new").commit()
        oldEditor.commit()
        assertEquals("old", second.getString("same", null))
        assertEquals(1, f.raw.all.keys.count { it.startsWith("e3_") })
        second.edit().remove("same").commit()
        first.edit().putString("another", "one").commit()
        assertFalse(first.contains("same"))
        assertEquals(setOf("another"), second.keySet())
    }

    @Test fun fileIdentityNormalizesDefaultNameAndSeparatesProtectedStorageContexts() {
        val context = RuntimeEnvironment.getApplication()
        val default = EncryptedSharedPreferences.fileIdentity(context, null)
        assertEquals(default, EncryptedSharedPreferences.fileIdentity(context, ""))
        assertEquals(default, EncryptedSharedPreferences.fileIdentity(context, context.packageName + "_preferences"))
        assertNotEquals(default, EncryptedSharedPreferences.fileIdentity(context.createDeviceProtectedStorageContext(), null))
    }

    @Test fun invalidV3SnapshotCannotFinalizeAnInterruptedFence() {
        val f = ProtectedPreferenceFixture()
        f.legacyString("identity", "alice")
        f.fence.failComplete = true
        assertThrows(ProtectedStorageUnavailableException::class.java) { f.open().getString("identity", null) }
        val physical = f.raw.all.keys.single { it.startsWith("e3_") }
        f.raw.edit().putString(physical, "v3_broken").commit()
        f.fence.failComplete = false
        f.simulateProcessRestart()
        assertThrows(ProtectedStorageUnavailableException::class.java) { f.open().getString("identity", null) }
        assertNotEquals(PreferenceMigrationFence.COMMITTED, f.fence.value)
    }

    @Test fun v3WritesRoundTripAllTypesAndApplyPublishesOnlyAfterDurability() {
        val f = ProtectedPreferenceFixture()
        val p = f.open()
        val events = mutableListOf<String?>()
        p.registerOnSharedPreferenceChangeListener { prefs, key ->
            assertEquals(PreferenceMigrationFence.COMMITTED, f.fence.value)
            assertTrue(prefs.contains(key))
            events += key
        }
        p.edit().putInt("int", 9).putLong("long", 10L).putFloat("float", 3.5f)
            .putBoolean("bool", true).putString("str", "кирилиця")
            .putStringSet("set", mutableSetOf("a", "b")).putString("nil", null).apply()
        val reopened = f.open()
        assertEquals(mapOf("int" to 9, "long" to 10L, "float" to 3.5f, "bool" to true,
            "str" to "кирилиця", "set" to setOf("a", "b"), "nil" to null), reopened.all)
        assertEquals(7, events.distinct().size)
    }

    @Test fun unrelatedLegacyConfigIsNeverInitializedAfterAnEarlierLegacyKeyMatches() {
        val f = ProtectedPreferenceFixture()
        f.legacyString("identity", "alice", true)
        var unnecessaryFallback = false
        val p = EncryptedSharedPreferences(f.raw, f.id, f.fence,
            listOf({ f.legacyConfig }, { unnecessaryFallback = true; error("Unrelated legacy material") }), { f.config })
        assertEquals("alice", p.getString("identity", null))
        assertFalse(unnecessaryFallback)
    }

    @Test fun concurrentEditorsAcrossWrappersRemainOneLogicalRecord() {
        val f = ProtectedPreferenceFixture()
        val left = f.open().edit().putString("same", "left")
        val right = f.open().edit().putString("same", "right")
        val start = java.util.concurrent.CountDownLatch(1)
        val pool = java.util.concurrent.Executors.newFixedThreadPool(2)
        try {
            val jobs = listOf(left, right).map { editor -> pool.submit<Boolean> { start.await(); editor.commit() } }
            start.countDown()
            jobs.forEach { assertTrue(it.get(30, java.util.concurrent.TimeUnit.SECONDS)) }
        } finally { pool.shutdownNow() }
        assertEquals(setOf("same"), f.open().keySet())
        assertEquals(1, f.raw.all.keys.count { it.startsWith("e3_") })
        assertTrue(f.open().getString("same", null) in setOf("left", "right"))
    }

    @Test fun publicV3StorageNeverCreatesLegacyMaterialEvenWhenLegacyFallbackWasExplicitlyEnabled() {
        val context = RuntimeEnvironment.getApplication()
        android.provider.Settings.Secure.putString(context.contentResolver,
            android.provider.Settings.Secure.ANDROID_ID, "fixture-device-id")
        val salt = java.io.File(context.applicationInfo.dataDir, "bio_hash")
        assertFalse("Fixture starts without legacy material", salt.exists())
        val original = SharedPreferenceProvider.EncryptionConfig.failClosedWhenKeyStoreUnavailable
        try {
            SharedPreferenceProvider.EncryptionConfig.failClosedWhenKeyStoreUnavailable = false
            // Robolectric has no AndroidKeyStore. The public v3 route must reject, never fall back.
            val p = EncryptedSharedPreferences(context, "strict-keystore-fixture")
            assertThrows(ProtectedStorageUnavailableException::class.java) { p.getString("key", null) }
            assertFalse("V3 must never initialize device-ID legacy material", salt.exists())
        } finally {
            SharedPreferenceProvider.EncryptionConfig.failClosedWhenKeyStoreUnavailable = original
        }
    }

    @Test fun migrationDigestCannotConfuseSetElementsWithAnotherRawRecord() {
        val f = ProtectedPreferenceFixture()
        val p = f.open()
        val digest = EncryptedSharedPreferences::class.java.getDeclaredMethod("snapshotDigest", Map::class.java)
            .apply { isAccessible = true }
        // Corrupt backing types may be encountered during explicit reset. Their retry digest must
        // authenticate record boundaries too, not just the concatenation of individual fields.
        val first = mapOf("a" to setOf("b", "java.lang.String", "x"))
        val second = mapOf("a" to emptySet<String>(), "b" to "x")
        assertNotEquals(digest.invoke(p, first), digest.invoke(p, second))
    }

    private fun failingCommit(raw: SharedPreferences, updateMemory: Boolean, throws: Boolean = false): SharedPreferences =
        Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SharedPreferences::class.java)) { _, method, args ->
            if (method.name == "edit") {
                val editor = raw.edit()
                Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SharedPreferences.Editor::class.java)) { proxy, edit, values ->
                    if (edit.name == "commit") {
                        if (updateMemory) editor.commit()
                        if (throws) throw java.io.IOException("Fixture persistence failure")
                        false
                    } else {
                        try { edit.invoke(editor, *(values ?: emptyArray())) } catch (error: InvocationTargetException) { throw error.cause!! }
                        proxy
                    }
                }
            } else try { method.invoke(raw, *(args ?: emptyArray())) } catch (error: InvocationTargetException) { throw error.cause!! }
        } as SharedPreferences
}
