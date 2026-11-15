package dev.skomlach.biometric.compat.engine.internal.face.tensorflow

import android.content.Context
import android.content.SharedPreferences
import android.graphics.RectF
import dev.skomlach.common.storage.SharedPreferenceProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.ConcurrentMap

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class FaceTemplateCapacityTest {
    @Test fun fullStoreRejectsANewIdBeforeWritingAndAllowsAReplacement() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("face-capacity-test", Context.MODE_PRIVATE)
        @Suppress("UNCHECKED_CAST")
        val cache = SharedPreferenceProvider::class.java.getDeclaredField("protectedPreferencesCache")
            .apply { isAccessible = true }.get(null) as ConcurrentMap<String, SharedPreferences>
        val namespace = TFLiteObjectDetectionAPIModel.STORAGE_NAME
        val previous = cache.put(namespace, prefs)
        val model = TFLiteObjectDetectionAPIModel::class.java.getDeclaredConstructor()
            .apply { isAccessible = true }.newInstance()
        fun recognition(id: String) =
            SimilarityClassifier.Recognition(id, id, 0f, RectF()).also {
                it.extra = arrayOf(FloatArray(192) { 1f })
            }
        try {
            repeat(32) { model.register("face_$it", recognition("profile_$it")) }
            val saved = prefs.getString(REGISTERED_TEMPLATES_PREF_KEY, null)
            assertEquals(32, model.registeredCount())
            assertThrows(FaceTemplateCapacityException::class.java) {
                model.register("face_new", recognition("new"))
            }
            assertEquals(saved, prefs.getString(REGISTERED_TEMPLATES_PREF_KEY, null))
            model.register("face_0", recognition("replacement"))
            assertTrue(model.getEnrolls().contains("face_0"))
            assertFalse(model.getEnrolls().contains("face_new"))
            TensorFlowFaceUnlockManager(context).remove(null)
            assertTrue(model.getEnrolls().isEmpty())
        } finally {
            if (previous == null) cache.remove(namespace) else cache[namespace] = previous
        }
    }
}
