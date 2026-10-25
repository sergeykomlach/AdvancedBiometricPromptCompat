package dev.skomlach.biometric.compat.engine.internal.behavior

import android.content.Context
import android.content.SharedPreferences
import dev.skomlach.common.storage.EncryptedSharedPreferences
import dev.skomlach.common.storage.SharedPreferenceProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class BehaviorProtectedStoreTest {
    @Test fun removalEnumeratesNamesWithoutDecryptingCorruptTemplateValues() {
        val backing = RuntimeEnvironment.getApplication().getSharedPreferences("encrypted-corrupt", Context.MODE_PRIVATE)
        val config = { SharedPreferenceProvider.EncryptionConfig(ByteArray(32) { (it + 1).toByte() }, ByteArray(128) { (it + 2).toByte() }) }
        val fenceType = Class.forName("dev.skomlach.common.storage.PreferenceMigrationFence")
        var fenceState: String? = null
        val fence = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(fenceType)) { _, method, args ->
            when (method.name) {
                "state" -> fenceState
                "begin" -> { fenceState = args!![0] as String; null }
                "complete" -> { fenceState = "committed"; null }
                else -> null
            }
        }
        val prefs = EncryptedSharedPreferences::class.java.declaredConstructors.single {
            it.parameterCount == 5 && it.parameterTypes[0] == SharedPreferences::class.java
        }.apply { isAccessible = true }.newInstance(backing, "behavior-corrupt-value", fence,
            emptyList<() -> SharedPreferenceProvider.EncryptionConfig?>(), config) as SharedPreferences
        prefs.edit().putString("template_broken", "will-be-corrupted").putString("other", "kept").commit()
        val encryptedKey = backing.all.keys.filter { it.startsWith("e3_") }.first { encrypted ->
            // Identify the template using the valid protected read before damaging ciphertext.
            val value = backing.getString(encrypted, null)
            backing.edit().remove(encrypted).commit()
            val isTemplate = !prefs.contains("template_broken")
            backing.edit().putString(encrypted, value).commit()
            isTemplate
        }
        backing.edit().putString(encryptedKey, "not-valid-ciphertext").commit()
        BehaviorTemplateStore { prefs }.remove(null)
        assertFalse(prefs.contains("template_broken"))
        assertEquals("kept", prefs.getString("other", null))
    }
}
