package dev.skomlach.biometric.compat.engine.internal.face.tensorflow

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.face.Face
import dev.skomlach.common.storage.SharedPreferenceProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.ConcurrentMap

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE, qualifiers = "en")
class FaceChallengeInvalidFrameTest {
    @Test fun unusableFrameDiscardsPartialChallengeBeforeSameIdentityCanContinue() =
        assertChallengeRestarted(listOf(FaceChallengeAction.CENTER, FaceChallengeAction.LEFT), 1)

    @Test fun unusableFrameDiscardsCompletedChallengeBeforeSameIdentityCanReuseIt() =
        assertChallengeRestarted(emptyList(), 0)

    private fun assertChallengeRestarted(actions: List<FaceChallengeAction>, index: Int) {
        val managerClass = TensorFlowFaceUnlockManager::class.java
        val configField = managerClass.getDeclaredField("config").apply { isAccessible = true }
        val originalConfig = configField.get(null) as TensorFlowFaceConfig
        @Suppress("UNCHECKED_CAST")
        val cache = SharedPreferenceProvider::class.java.getDeclaredField("protectedPreferencesCache")
            .apply { isAccessible = true }.get(null) as ConcurrentMap<String, SharedPreferences>
        val context = RuntimeEnvironment.getApplication()
        val originalPrefs = cache.put("tf_storage_v2", context.getSharedPreferences("face-invalid-frame", Context.MODE_PRIVATE))
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        try {
            TensorFlowFaceUnlockManager.setTensorFlowFaceConfig(TensorFlowFaceConfig(
                faceChallengeEnabled = true, forceDeviceClass = DevicePerformanceClass.MID_RANGE,
                allowAdaptiveDeviceTuning = false, antiSpoofingMode = AntiSpoofingMode.OFF))
            val manager = TensorFlowFaceUnlockManager(context)
            fun field(name: String) = managerClass.getDeclaredField(name).apply { isAccessible = true }
            (field("faceChallengeIdentity").get(manager) as FaceChallengeIdentityBinding).update("alice")
            field("faceChallengeActions").set(manager, actions)
            field("faceChallengeIndex").setInt(manager, index)
            field("faceChallengeStepStartedAtMs").setLong(manager, android.os.SystemClock.elapsedRealtime())
            // A real tiny MLKit face takes the production InvalidFace branch before crop/inference/camera.
            val faceConstructor = Face::class.java.constructors.single { it.parameterTypes[0].simpleName == "zzow" }
            val payload = faceConstructor.parameterTypes[0].constructors.single().newInstance(
                1, Rect(0, 0, 1, 1), 0f, -12f, 0f, 0f, 0f, 0f, 0f, emptyList<Any>(), emptyList<Any>())
            val face = faceConstructor.newInstance(payload, null) as Face
            assertEquals(-12f, face.headEulerAngleY, 0f)
            managerClass.getDeclaredMethod("processFaces", Bitmap::class.java, List::class.java)
                .apply { isAccessible = true }.invoke(manager, bitmap, listOf(face))
            val satisfied = managerClass.getDeclaredMethod("isFaceChallengeSatisfied", Face::class.java)
                .apply { isAccessible = true }.invoke(manager, face) as Boolean
            assertFalse("A valid identity must restart CENTER after an unusable frame", satisfied)
            assertEquals(0, field("faceChallengeIndex").getInt(manager))
            assertEquals(FaceChallengeAction.CENTER, (field("faceChallengeActions").get(manager) as List<*>).first())
        } finally {
            bitmap.recycle()
            TensorFlowFaceUnlockManager.setTensorFlowFaceConfig(originalConfig)
            if (originalPrefs == null) cache.remove("tf_storage_v2") else cache["tf_storage_v2"] = originalPrefs
        }
    }
}
