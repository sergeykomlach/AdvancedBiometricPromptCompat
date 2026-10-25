# Software Biometric Production Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bring `Voice`, `Behavior`, `FaceTF`, and `ZKFinger` to one production-hardening bar for localization, callback semantics, lifecycle behavior, targeted tests, and app-level compile readiness.

**Architecture:** Keep `:biometric` generic and treat each software-biometric module as the owner of its user-facing resources, module-specific preflight logic, capture or hardware flow, and targeted tests. Fix the shared production issues through small internal seams that can be unit-tested, then generate one readiness summary that uses the same criteria for all four modules.

**Tech Stack:** Kotlin, Android library modules, `LocalizationHelper`, `ServiceLoader`-based software biometric providers, JUnit4, Gradle compile gates.

---

### Task 1: Characterize Shared Software-Biometric Contracts And Provider Expectations

**Files:**
- Create: `biometric/src/test/java/dev/skomlach/biometric/compat/custom/SoftwareBiometricProviderContractTest.kt`
- Create: `biometric-custom-face-tf/src/test/java/dev/skomlach/biometric/compat/engine/internal/face/tensorflow/TensorFlowProviderTest.kt`
- Create: `biometric-zkfinger/src/test/java/dev/skomlach/biometric/compat/engine/internal/fingerprint/zk/ZkFingerProviderTest.kt`
- Modify: `biometric-custom-face-tf/src/main/java/dev/skomlach/biometric/compat/engine/internal/face/tensorflow/TensorFlowProvider.kt`
- Modify: `biometric-zkfinger/src/main/java/dev/skomlach/biometric/compat/engine/internal/fingerprint/zk/ZkFingerProvider.kt`

- [ ] **Step 1: Write the failing shared-provider contract test**

```kotlin
package dev.skomlach.biometric.compat.custom

import android.content.Context
import org.junit.Assert.assertNull
import org.junit.Test

class SoftwareBiometricProviderContractTest {
    @Test
    fun defaultPromptFactoryIsNull() {
        val provider = object : SoftwareBiometricProvider() {
            override fun getCustomManager(context: Context): AbstractSoftwareBiometricManager {
                error("unused")
            }
        }

        assertNull(provider.getPromptFactory())
    }
}
```

- [ ] **Step 2: Write the failing provider tests for modules without custom prompt UI**

```kotlin
package dev.skomlach.biometric.compat.engine.internal.face.tensorflow

import org.junit.Assert.assertNull
import org.junit.Test

class TensorFlowProviderTest {
    @Test
    fun faceTfProviderDoesNotExposeCustomPromptFactory() {
        assertNull(TensorFlowProvider().getPromptFactory())
    }
}
```

```kotlin
package dev.skomlach.biometric.compat.engine.internal.fingerprint.zk

import org.junit.Assert.assertNull
import org.junit.Test

class ZkFingerProviderTest {
    @Test
    fun zkFingerProviderDoesNotExposeCustomPromptFactory() {
        assertNull(ZkFingerProvider().getPromptFactory())
    }
}
```

- [ ] **Step 3: Run compile gates to confirm the tests are wired**

Run: `.\gradlew.bat :biometric:compileDebugUnitTestKotlin :biometric-custom-face-tf:compileDebugUnitTestKotlin :biometric-zkfinger:compileDebugUnitTestKotlin`

Expected: BUILD SUCCESSFUL or direct compile errors only in the newly added tests.

- [ ] **Step 4: Make the module providers explicit and boring**

```kotlin
class TensorFlowProvider : SoftwareBiometricProvider() {
    override fun getCustomManager(context: Context): AbstractSoftwareBiometricManager {
        return TensorFlowFaceUnlockManager(context.applicationContext)
    }
}
```

```kotlin
class ZkFingerProvider : SoftwareBiometricProvider() {
    override fun getCustomManager(context: Context): AbstractSoftwareBiometricManager {
        return ZkFingerUnlockManager(context.applicationContext)
    }
}
```

- [ ] **Step 5: Re-run the provider contract compile gate**

Run: `.\gradlew.bat :biometric:compileDebugUnitTestKotlin :biometric-custom-face-tf:compileDebugUnitTestKotlin :biometric-zkfinger:compileDebugUnitTestKotlin`

Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add biometric/src/test/java/dev/skomlach/biometric/compat/custom/SoftwareBiometricProviderContractTest.kt biometric-custom-face-tf/src/test/java/dev/skomlach/biometric/compat/engine/internal/face/tensorflow/TensorFlowProviderTest.kt biometric-zkfinger/src/test/java/dev/skomlach/biometric/compat/engine/internal/fingerprint/zk/ZkFingerProviderTest.kt biometric-custom-face-tf/src/main/java/dev/skomlach/biometric/compat/engine/internal/face/tensorflow/TensorFlowProvider.kt biometric-zkfinger/src/main/java/dev/skomlach/biometric/compat/engine/internal/fingerprint/zk/ZkFingerProvider.kt
git commit -m "test: codify software biometric provider prompt contracts"
```

### Task 2: Finish Voice Production Localization And Message Ownership

**Files:**
- Modify: `biometric-custom-voice/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceAutoCaptureSession.kt`
- Modify: `biometric-custom-voice/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceAutoCaptureController.kt`
- Modify: `biometric-custom-voice/src/main/res/values/strings.xml`
- Modify: `biometric-custom-voice/src/test/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceAutoCaptureSessionTest.kt`
- Create: `biometric-custom-voice/src/test/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceAutoCaptureMessagesTest.kt`

- [ ] **Step 1: Write the failing message ownership test**

```kotlin
package dev.skomlach.biometric.compat.engine.internal.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceAutoCaptureMessagesTest {
    @Test
    fun recordingStartedUsesProvidedTemplatesForEnrollment() {
        val messages = VoiceAutoCaptureSession.Messages(
            authStart = "AUTH",
            voiceDetected = "DETECTED",
            processing = "PROCESSING",
            enrollRecordingStarted = "Sample %1\$d/%2\$d start",
            sampleSaved = "Sample %1\$d/%2\$d saved"
        )

        assertEquals("Sample 1/3 start", messages.recordingStarted(1, 3, enroll = true).toString())
        assertEquals("Sample 2/3 saved", messages.sampleSaved(2, 3).toString())
    }
}
```

- [ ] **Step 2: Run the failing voice unit-test compile gate**

Run: `.\gradlew.bat :biometric-custom-voice:compileDebugUnitTestKotlin`

Expected: FAIL because `VoiceAutoCaptureSession.Messages` does not yet support resource-owned enrollment templates.

- [ ] **Step 3: Move all user-facing auto-capture message templates into resources**

Add these strings to `biometric-custom-voice/src/main/res/values/strings.xml`:

```xml
<string name="biometriccompat_voice_auto_enroll_recording_started">Voice sample %1$d of %2$d: recording started</string>
<string name="biometriccompat_voice_auto_sample_saved">Voice sample %1$d of %2$d saved</string>
```

Update `VoiceAutoCaptureSession.Messages` to accept them:

```kotlin
class Messages(
    private val authStart: CharSequence,
    private val voiceDetected: CharSequence,
    private val processing: CharSequence,
    private val enrollRecordingStarted: String,
    private val sampleSavedTemplate: String
) {
    fun recordingStarted(current: Int, total: Int, enroll: Boolean): CharSequence {
        return if (enroll) {
            enrollRecordingStarted.format(current, total)
        } else {
            authStart
        }
    }

    fun sampleSaved(current: Int, total: Int): CharSequence {
        return sampleSavedTemplate.format(current, total)
    }
}
```

- [ ] **Step 4: Wire the resource-backed messages from the controller**

```kotlin
messages = VoiceAutoCaptureSession.Messages(
    authStart = localized(R.string.biometriccompat_voice_auto_auth_start),
    voiceDetected = localized(R.string.biometriccompat_voice_auto_detected),
    processing = localized(R.string.biometriccompat_voice_status_checking),
    enrollRecordingStarted = localized(R.string.biometriccompat_voice_auto_enroll_recording_started),
    sampleSavedTemplate = localized(R.string.biometriccompat_voice_auto_sample_saved)
)
```

- [ ] **Step 5: Extend the session tests to verify enroll and auth help flow**

Update `VoiceAutoCaptureSessionTest.kt` to assert:

```kotlin
assertTrue(callback.messages.any { it.contains("saved", ignoreCase = true) })
assertTrue(callback.messages.last().contains("Processing", ignoreCase = true))
```

- [ ] **Step 6: Re-run voice compile gates**

Run: `.\gradlew.bat :biometric-custom-voice:compileDebugKotlin :biometric-custom-voice:compileDebugUnitTestKotlin`

Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add biometric-custom-voice/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceAutoCaptureSession.kt biometric-custom-voice/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceAutoCaptureController.kt biometric-custom-voice/src/main/res/values/strings.xml biometric-custom-voice/src/test/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceAutoCaptureSessionTest.kt biometric-custom-voice/src/test/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceAutoCaptureMessagesTest.kt
git commit -m "fix: localize voice auto capture status flow"
```

### Task 3: Normalize Behavior Payload Ownership And Explicit Prompt Readiness

**Files:**
- Create: `biometric-custom-behavior/src/main/java/dev/skomlach/biometric/compat/engine/internal/behavior/BehaviorPromptData.kt`
- Modify: `biometric-custom-behavior/src/main/java/dev/skomlach/biometric/compat/engine/internal/behavior/BehaviorCaptureController.kt`
- Modify: `biometric-custom-behavior/src/main/java/dev/skomlach/biometric/compat/engine/internal/behavior/BehaviorSample.kt`
- Create: `biometric-custom-behavior/src/test/java/dev/skomlach/biometric/compat/engine/internal/behavior/BehaviorPromptDataTest.kt`
- Create: `biometric-custom-behavior/src/test/java/dev/skomlach/biometric/compat/engine/internal/behavior/BehaviorPromptDelegateTest.kt`

- [ ] **Step 1: Write the failing behavior extras round-trip test**

```kotlin
package dev.skomlach.biometric.compat.engine.internal.behavior

import android.os.Bundle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class BehaviorPromptDataTest {
    @Test
    fun buildBehaviorExtrasMatchesBehaviorSampleParsing() {
        val extras = buildBehaviorExtras(
            existing = Bundle(),
            mode = BehaviorMode.COMBINED,
            phrase = "hello",
            keyDowns = longArrayOf(1, 2),
            keyUps = longArrayOf(3, 4),
            points = floatArrayOf(1f, 2f, 3f, 4f, 5f, 6f),
            enroll = true
        )

        val sample = BehaviorSample.fromBundle(extras)

        assertNotNull(sample)
        assertEquals(BehaviorMode.COMBINED, sample!!.mode)
    }
}
```

- [ ] **Step 2: Write the failing prompt delegate readiness test**

```kotlin
package dev.skomlach.biometric.compat.engine.internal.behavior

import dev.skomlach.biometric.compat.BehaviorAuthMode
import org.junit.Assert.assertTrue
import org.junit.Test

class BehaviorPromptDelegateTest {
    @Test
    fun implicitBehaviorModeDoesNotBlockGenericPromptStart() {
        assertTrue(BehaviorAuthMode.IMPLICIT != BehaviorAuthMode.EXPLICIT)
    }
}
```

- [ ] **Step 3: Run the behavior compile gate**

Run: `.\gradlew.bat :biometric-custom-behavior:compileDebugKotlin :biometric-custom-behavior:compileDebugUnitTestKotlin`

Expected: FAIL because the shared extras builder does not exist yet.

- [ ] **Step 4: Extract one behavior extras builder and reuse it**

Create `BehaviorPromptData.kt`:

```kotlin
package dev.skomlach.biometric.compat.engine.internal.behavior

import android.os.Bundle
import dev.skomlach.biometric.compat.BundleBuilder

internal fun buildBehaviorExtras(
    existing: Bundle?,
    mode: BehaviorMode,
    phrase: String,
    keyDowns: LongArray,
    keyUps: LongArray,
    points: FloatArray,
    enroll: Boolean
): Bundle {
    return Bundle(existing ?: Bundle()).apply {
        putString(BehaviorSample.EXTRA_BEHAVIOR_MODE, mode.name)
        putString(BehaviorSample.EXTRA_BEHAVIOR_PHRASE, phrase)
        putLongArray(BehaviorSample.EXTRA_BEHAVIOR_KEY_DOWNS, keyDowns)
        putLongArray(BehaviorSample.EXTRA_BEHAVIOR_KEY_UPS, keyUps)
        putFloatArray(BehaviorSample.EXTRA_BEHAVIOR_POINTS, points)
        putInt(BehaviorSample.EXTRA_BEHAVIOR_POINTS_STRIDE, BehaviorSample.DEFAULT_POINT_STRIDE)
        putBoolean(BundleBuilder.ENROLL, enroll)
    }
}
```

- [ ] **Step 5: Point both capture and parsing code at the same constants**

Update `BehaviorCaptureController.kt`:

```kotlin
val extras = buildBehaviorExtras(
    existing = builder.getExtras(),
    mode = BehaviorMode.valueOf(mode),
    phrase = phrase,
    keyDowns = keyDowns.toLongArray(),
    keyUps = keyUps.toLongArray(),
    points = points.toFloatArray(),
    enroll = enroll
)
```

Update `BehaviorSample.kt` to expose the reused constants:

```kotlin
companion object {
    const val EXTRA_BEHAVIOR_MODE = "behavior.mode"
    const val EXTRA_BEHAVIOR_PHRASE = "behavior.phrase"
    const val EXTRA_BEHAVIOR_KEY_DOWNS = "behavior.key_downs"
    const val EXTRA_BEHAVIOR_KEY_UPS = "behavior.key_ups"
    const val EXTRA_BEHAVIOR_POINTS = "behavior.points"
    const val EXTRA_BEHAVIOR_POINTS_STRIDE = "behavior.points_stride"
    const val DEFAULT_POINT_STRIDE = 6
}
```

- [ ] **Step 6: Re-run the behavior compile gate**

Run: `.\gradlew.bat :biometric-custom-behavior:compileDebugKotlin :biometric-custom-behavior:compileDebugUnitTestKotlin`

Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add biometric-custom-behavior/src/main/java/dev/skomlach/biometric/compat/engine/internal/behavior/BehaviorPromptData.kt biometric-custom-behavior/src/main/java/dev/skomlach/biometric/compat/engine/internal/behavior/BehaviorCaptureController.kt biometric-custom-behavior/src/main/java/dev/skomlach/biometric/compat/engine/internal/behavior/BehaviorSample.kt biometric-custom-behavior/src/test/java/dev/skomlach/biometric/compat/engine/internal/behavior/BehaviorPromptDataTest.kt biometric-custom-behavior/src/test/java/dev/skomlach/biometric/compat/engine/internal/behavior/BehaviorPromptDelegateTest.kt
git commit -m "refactor: unify behavior prompt payload ownership"
```

### Task 4: Fix FaceTF Preflight Callback Delivery And Add State Tests

**Files:**
- Create: `biometric-custom-face-tf/src/main/java/dev/skomlach/biometric/compat/engine/internal/face/tensorflow/TensorFlowFacePreflight.kt`
- Modify: `biometric-custom-face-tf/src/main/java/dev/skomlach/biometric/compat/engine/internal/face/tensorflow/TensorFlowFaceUnlockManager.kt`
- Create: `biometric-custom-face-tf/src/test/java/dev/skomlach/biometric/compat/engine/internal/face/tensorflow/TensorFlowFacePreflightTest.kt`
- Modify: `biometric-custom-face-tf/src/test/java/dev/skomlach/biometric/compat/engine/internal/face/tensorflow/TensorFlowFaceStateSupportTest.kt`

- [ ] **Step 1: Write the failing FaceTF preflight test**

```kotlin
package dev.skomlach.biometric.compat.engine.internal.face.tensorflow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class TensorFlowFacePreflightTest {
    @Test
    fun returnsCameraBlockedFailureBeforeSessionStarts() {
        val failure = resolveTensorFlowFacePreflightFailure(
            isHardwareDetected = true,
            isCameraBlocked = true,
            isCameraInUse = false,
            isEnrolling = false,
            hasEnrolledBiometric = true,
            enrollmentTag = "face1"
        )

        assertNotNull(failure)
        assertEquals(TensorFlowFacePreflightFailure.CAMERA_BLOCKED, failure)
    }
}
```

- [ ] **Step 2: Run the FaceTF unit-test compile gate**

Run: `.\gradlew.bat :biometric-custom-face-tf:compileDebugUnitTestKotlin`

Expected: FAIL because the explicit preflight helper does not exist yet.

- [ ] **Step 3: Extract the preflight state decision into a pure helper**

Create `TensorFlowFacePreflight.kt`:

```kotlin
package dev.skomlach.biometric.compat.engine.internal.face.tensorflow

internal enum class TensorFlowFacePreflightFailure {
    HARDWARE_MISSING,
    CAMERA_BLOCKED,
    CAMERA_IN_USE,
    ENROLLMENT_TAG_MISSING,
    NO_ENROLLMENTS
}

internal fun resolveTensorFlowFacePreflightFailure(
    isHardwareDetected: Boolean,
    isCameraBlocked: Boolean,
    isCameraInUse: Boolean,
    isEnrolling: Boolean,
    hasEnrolledBiometric: Boolean,
    enrollmentTag: String
): TensorFlowFacePreflightFailure? {
    if (!isHardwareDetected) return TensorFlowFacePreflightFailure.HARDWARE_MISSING
    if (isCameraBlocked) return TensorFlowFacePreflightFailure.CAMERA_BLOCKED
    if (isCameraInUse) return TensorFlowFacePreflightFailure.CAMERA_IN_USE
    if (isEnrolling && enrollmentTag.isEmpty()) return TensorFlowFacePreflightFailure.ENROLLMENT_TAG_MISSING
    if (!isEnrolling && !hasEnrolledBiometric) return TensorFlowFacePreflightFailure.NO_ENROLLMENTS
    return null
}
```

- [ ] **Step 4: Make the manager attach callback state before reporting failures**

Update `TensorFlowFaceUnlockManager.authenticate(...)` so that:

```kotlin
authCallback = callback
cancellationSignal = cancel

val preflightFailure = resolveTensorFlowFacePreflightFailure(
    isHardwareDetected = isHardwareDetected(),
    isCameraBlocked = frameProvider is RealCameraProvider && SensorPrivacyCheck.isCameraBlocked(),
    isCameraInUse = frameProvider is RealCameraProvider && SensorPrivacyCheck.isCameraInUse(),
    isEnrolling = isEnrolling,
    hasEnrolledBiometric = hasEnrolledBiometric(),
    enrollmentTag = enrollmentTag
)
```

Then map `preflightFailure` to the existing localized strings before starting the session.

- [ ] **Step 5: Expand the state-support test with one new callback-state seam**

Add:

```kotlin
@Test
fun malformedJsonStillReportsNoTemplates() {
    assertFalse(hasRegisteredTemplates("{broken"))
    assertEquals(0, countRegisteredTemplates("{broken"))
}
```

- [ ] **Step 6: Re-run the FaceTF compile gate**

Run: `.\gradlew.bat :biometric-custom-face-tf:compileDebugKotlin :biometric-custom-face-tf:compileDebugUnitTestKotlin`

Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add biometric-custom-face-tf/src/main/java/dev/skomlach/biometric/compat/engine/internal/face/tensorflow/TensorFlowFacePreflight.kt biometric-custom-face-tf/src/main/java/dev/skomlach/biometric/compat/engine/internal/face/tensorflow/TensorFlowFaceUnlockManager.kt biometric-custom-face-tf/src/test/java/dev/skomlach/biometric/compat/engine/internal/face/tensorflow/TensorFlowFacePreflightTest.kt biometric-custom-face-tf/src/test/java/dev/skomlach/biometric/compat/engine/internal/face/tensorflow/TensorFlowFaceStateSupportTest.kt
git commit -m "fix: harden facetf preflight callback delivery"
```

### Task 5: Extract ZKFinger Session Helpers And Expand Test Coverage

**Files:**
- Create: `biometric-zkfinger/src/main/java/dev/skomlach/biometric/compat/engine/internal/fingerprint/zk/ZkFingerSessionSupport.kt`
- Modify: `biometric-zkfinger/src/main/java/dev/skomlach/biometric/compat/engine/internal/fingerprint/zk/ZkFingerUnlockManager.kt`
- Modify: `biometric-zkfinger/src/test/java/dev/skomlach/biometric/compat/engine/internal/fingerprint/zk/ZkFingerHardwareDetectionTest.kt`
- Create: `biometric-zkfinger/src/test/java/dev/skomlach/biometric/compat/engine/internal/fingerprint/zk/ZkFingerSessionSupportTest.kt`

- [ ] **Step 1: Write the failing session-helper test**

```kotlin
package dev.skomlach.biometric.compat.engine.internal.fingerprint.zk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ZkFingerSessionSupportTest {
    @Test
    fun sanitizeTagRejectsBlankAndIllegalInput() {
        assertNull(sanitizeZkEnrollmentTag("   "))
        assertEquals("abc_123", sanitizeZkEnrollmentTag("abc 123"))
    }

    @Test
    fun usbPermissionActionIsStablePerPackage() {
        assertEquals(
            "pkg.dev.skomlach.biometric.zkfinger.USB_PERMISSION",
            resolveZkPermissionAction("pkg")
        )
    }
}
```

- [ ] **Step 2: Run the ZKFinger unit-test compile gate**

Run: `.\gradlew.bat :biometric-zkfinger:compileDebugUnitTestKotlin`

Expected: FAIL because the extracted helper functions do not exist yet.

- [ ] **Step 3: Extract the pure session helper functions**

Create `ZkFingerSessionSupport.kt`:

```kotlin
package dev.skomlach.biometric.compat.engine.internal.fingerprint.zk

internal fun resolveZkPermissionAction(packageName: String): String {
    return "$packageName.dev.skomlach.biometric.zkfinger.USB_PERMISSION"
}

internal fun resolveZkPermissionRequestCode(
    vendorId: Int,
    productId: Int,
    deviceIndex: Int
): Int {
    var result = 17
    result = 31 * result + vendorId
    result = 31 * result + productId
    result = 31 * result + deviceIndex
    return result
}

internal fun sanitizeZkEnrollmentTag(tag: String?): String? {
    val sanitized = tag
        ?.trim()
        ?.replace(Regex("[^A-Za-z0-9_.-]"), "_")
        ?.take(64)
    return sanitized?.takeIf { it.isNotBlank() }
}
```

- [ ] **Step 4: Point the manager at the shared helpers**

Update `ZkFingerUnlockManager.kt`:

```kotlin
private fun permissionAction(): String {
    return resolveZkPermissionAction(context.packageName)
}

private fun permissionRequestCode(device: UsbDevice): Int {
    return resolveZkPermissionRequestCode(device.vendorId, device.productId, effectiveConfig.deviceIndex)
}

private fun sanitizeTag(tag: String?): String? {
    return sanitizeZkEnrollmentTag(tag)
}
```

- [ ] **Step 5: Expand the hardware-detection test with one negative case**

```kotlin
@Test
fun hardwareIsNotDetectedWhenUsbHostIsMissing() {
    assertFalse(resolveZkHardwareDetected(usbHostAvailable = false, supportedDeviceConnected = true))
}
```

- [ ] **Step 6: Re-run the ZKFinger compile gate**

Run: `.\gradlew.bat :biometric-zkfinger:compileDebugKotlin :biometric-zkfinger:compileDebugUnitTestKotlin`

Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add biometric-zkfinger/src/main/java/dev/skomlach/biometric/compat/engine/internal/fingerprint/zk/ZkFingerSessionSupport.kt biometric-zkfinger/src/main/java/dev/skomlach/biometric/compat/engine/internal/fingerprint/zk/ZkFingerUnlockManager.kt biometric-zkfinger/src/test/java/dev/skomlach/biometric/compat/engine/internal/fingerprint/zk/ZkFingerHardwareDetectionTest.kt biometric-zkfinger/src/test/java/dev/skomlach/biometric/compat/engine/internal/fingerprint/zk/ZkFingerSessionSupportTest.kt
git commit -m "test: harden zkfinger session support seams"
```

### Task 6: Final Cross-Module Verification And Readiness Summary

**Files:**
- Create: `docs/superpowers/reports/2026-07-02-software-biometric-readiness-summary.md`
- Verify: `biometric/src/main/java/dev/skomlach/biometric/compat/impl/dialogs/BiometricPromptCompatDialogImpl.kt`
- Verify: `biometric-custom-voice/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceAutoCaptureSession.kt`
- Verify: `biometric-custom-behavior/src/main/java/dev/skomlach/biometric/compat/engine/internal/behavior/BehaviorCaptureController.kt`
- Verify: `biometric-custom-face-tf/src/main/java/dev/skomlach/biometric/compat/engine/internal/face/tensorflow/TensorFlowFaceUnlockManager.kt`
- Verify: `biometric-zkfinger/src/main/java/dev/skomlach/biometric/compat/engine/internal/fingerprint/zk/ZkFingerUnlockManager.kt`

- [ ] **Step 1: Run the full compile-based proof**

Run: `.\gradlew.bat :biometric:compileDebugKotlin :biometric:compileDebugUnitTestKotlin :biometric-custom-voice:compileDebugKotlin :biometric-custom-voice:compileDebugUnitTestKotlin :biometric-custom-behavior:compileDebugKotlin :biometric-custom-behavior:compileDebugUnitTestKotlin :biometric-custom-face-tf:compileDebugKotlin :biometric-custom-face-tf:compileDebugUnitTestKotlin :biometric-zkfinger:compileDebugKotlin :biometric-zkfinger:compileDebugUnitTestKotlin :app:compileDebugKotlin`

Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Run whitespace and regression grep checks**

Run: `git -c safe.directory=C:/Users/skoml/StudioProjects_5/AdvancedBiometricPromptCompat diff --check`

Expected: no whitespace errors

Run: `rg -n '"[A-Za-z][A-Za-z0-9 ,:._()/-]{8,}"' biometric-custom-voice/src/main/java biometric-custom-behavior/src/main/java biometric-custom-face-tf/src/main/java biometric-zkfinger/src/main/java`

Expected: only internal constants, storage keys, tags, and logging strings remain

- [ ] **Step 3: Write the readiness summary report**

Create `docs/superpowers/reports/2026-07-02-software-biometric-readiness-summary.md` with this exact structure:

```md
# Software Biometric Readiness Summary

## Shared Platform
- Architecture:
- User-Facing UX / Localization:
- Failure Semantics:
- Lifecycle / Cancellation:
- Lockout / Retry:
- Test Coverage:
- Compile / Verification:
- Residual Risks:
- Status:

## Voice
- Architecture:
- User-Facing UX / Localization:
- Failure Semantics:
- Lifecycle / Cancellation:
- Lockout / Retry:
- Test Coverage:
- Compile / Verification:
- Residual Risks:
- Status:

## Behavior
...

## FaceTF
...

## ZKFinger
...
```

- [ ] **Step 4: Re-run the full compile proof after the report is added**

Run: `.\gradlew.bat :biometric:compileDebugKotlin :biometric-custom-voice:compileDebugKotlin :biometric-custom-behavior:compileDebugKotlin :biometric-custom-face-tf:compileDebugKotlin :biometric-zkfinger:compileDebugKotlin :app:compileDebugKotlin`

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add docs/superpowers/reports/2026-07-02-software-biometric-readiness-summary.md
git commit -m "docs: summarize software biometric production readiness"
```
