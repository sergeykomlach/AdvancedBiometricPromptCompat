# Software Biometric Thin Prompt Delegates Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add thin prompt delegates for `FaceTF` and `ZKFinger` so all software biometrics participate in one prompt lifecycle contract without moving camera or USB acquisition logic out of their existing managers.

**Architecture:** Add one reusable manager-backed prompt delegate in `:biometric` that only emits localized start/help text, becomes ready immediately, and turns inert on `cancel` / `dispose`. Then wire `FaceTF` and `ZKFinger` providers to module-owned prompt factories that configure that shared delegate while keeping engine and session ownership inside the current managers.

**Tech Stack:** Kotlin, Android library modules, `ServiceLoader` provider registry, `LocalizationHelper`, JUnit4, Gradle compile gates.

---

### Task 1: Add The Shared Thin Prompt Delegate In `:biometric`

**Files:**
- Create: `biometric/src/main/java/dev/skomlach/biometric/compat/custom/EngineBackedSoftwarePromptDelegate.kt`
- Create: `biometric/src/test/java/dev/skomlach/biometric/compat/custom/EngineBackedSoftwarePromptDelegateTest.kt`
- Modify: `biometric/src/test/java/dev/skomlach/biometric/compat/custom/SoftwareBiometricPromptRegistryTest.kt`

- [ ] **Step 1: Write the failing delegate behavior test**

```kotlin
package dev.skomlach.biometric.compat.custom

import android.os.Bundle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineBackedSoftwarePromptDelegateTest {

    @Test
    fun startPostsHelpAndSignalsReadyOnce() {
        val callbacks = RecordingCallbacks()
        val delegate = EngineBackedSoftwarePromptDelegate(
            host = fakeHost(callbacks),
            startMessage = { enroll -> if (enroll) "ENROLL_START" else "AUTH_START" }
        )

        delegate.start()

        assertEquals(listOf("AUTH_START"), callbacks.helpMessages)
        assertEquals(1, callbacks.readyCalls)
        assertTrue(delegate.isReadyToStartAuth())
    }

    @Test
    fun cancelMakesDelegateInert() {
        val callbacks = RecordingCallbacks()
        val delegate = EngineBackedSoftwarePromptDelegate(
            host = fakeHost(callbacks),
            startMessage = { "AUTH_START" }
        )

        delegate.cancel()
        delegate.start()

        assertTrue(callbacks.helpMessages.isEmpty())
        assertEquals(0, callbacks.readyCalls)
    }

    private fun fakeHost(callbacks: RecordingCallbacks): SoftwareBiometricPromptHost {
        return SoftwareBiometricPromptHost(
            context = android.test.mock.MockContext(),
            builder = throw UnsupportedOperationException("builder not used in this test"),
            enroll = false,
            rootView = null,
            callbacks = callbacks
        )
    }

    private class RecordingCallbacks : SoftwareBiometricPromptHost.Callbacks {
        val helpMessages = mutableListOf<String>()
        var readyCalls = 0

        override fun onHelp(message: CharSequence) {
            helpMessages += message.toString()
        }

        override fun onReady(extras: Bundle?) {
            readyCalls += 1
        }

        override fun onFailure(result: dev.skomlach.biometric.compat.AuthenticationResult) = Unit

        override fun isPromptActive(): Boolean = true
    }
}
```

- [ ] **Step 2: Run the biometric unit-test compile gate to verify the new type is missing**

Run: `.\gradlew.bat :biometric:compileDebugUnitTestKotlin`
Expected: FAIL with unresolved `EngineBackedSoftwarePromptDelegate`

- [ ] **Step 3: Add the shared thin delegate**

```kotlin
package dev.skomlach.biometric.compat.custom

internal class EngineBackedSoftwarePromptDelegate(
    private val host: SoftwareBiometricPromptHost,
    private val startMessage: (Boolean) -> CharSequence?
) : SoftwareBiometricPromptDelegate {
    private var active = true
    private var started = false

    override fun start() {
        if (!active || started || !host.callbacks.isPromptActive()) {
            return
        }
        started = true
        startMessage(host.enroll)?.let { host.callbacks.onHelp(it) }
        host.callbacks.onReady(null)
    }

    override fun cancel() {
        active = false
    }

    override fun dispose() {
        active = false
    }
}
```

- [ ] **Step 4: Add one registry-facing test for factory creation shape**

Append to `SoftwareBiometricPromptRegistryTest.kt`:

```kotlin
    @Test
    fun resolveReturnsMatchingFactoryWhenProviderExposesThinPromptFactory() {
        val factory = FakeFactory(BiometricType.BIOMETRIC_FACE)
        val provider = object : SoftwareBiometricProvider() {
            override fun getCustomManager(context: Context): AbstractSoftwareBiometricManager {
                error("unused")
            }

            override fun getPromptFactory(): SoftwareBiometricPromptFactory = factory
        }

        val resolved = SoftwareBiometricPromptRegistry.resolve(
            BiometricType.BIOMETRIC_FACE,
            listOf(provider)
        )

        assertSame(factory, resolved)
    }
```

- [ ] **Step 5: Re-run the biometric compile gate**

Run: `.\gradlew.bat :biometric:compileDebugKotlin :biometric:compileDebugUnitTestKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add biometric/src/main/java/dev/skomlach/biometric/compat/custom/EngineBackedSoftwarePromptDelegate.kt biometric/src/test/java/dev/skomlach/biometric/compat/custom/EngineBackedSoftwarePromptDelegateTest.kt biometric/src/test/java/dev/skomlach/biometric/compat/custom/SoftwareBiometricPromptRegistryTest.kt
git commit -m "feat: add engine-backed software prompt delegate"
```

### Task 2: Wire FaceTF To The Shared Thin Prompt Contract

**Files:**
- Create: `biometric-custom-face-tf/src/main/java/dev/skomlach/biometric/compat/engine/internal/face/tensorflow/TensorFlowFacePromptFactory.kt`
- Modify: `biometric-custom-face-tf/src/main/java/dev/skomlach/biometric/compat/engine/internal/face/tensorflow/TensorFlowProvider.kt`
- Modify: `biometric-custom-face-tf/src/main/res/values/strings.xml`
- Modify: `biometric-custom-face-tf/src/test/java/dev/skomlach/biometric/compat/engine/internal/face/tensorflow/TensorFlowProviderTest.kt`

- [ ] **Step 1: Replace the old provider expectation with a failing prompt-factory test**

Update `TensorFlowProviderTest.kt` to:

```kotlin
package dev.skomlach.biometric.compat.engine.internal.face.tensorflow

import dev.skomlach.biometric.compat.BiometricType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class TensorFlowProviderTest {

    @Test
    fun faceTfProviderExposesFacePromptFactory() {
        val factory = TensorFlowProvider().getPromptFactory()

        assertNotNull(factory)
        assertEquals(BiometricType.BIOMETRIC_FACE, factory?.biometricType)
    }
}
```

- [ ] **Step 2: Run the FaceTF compile gate to verify it fails**

Run: `.\gradlew.bat :biometric-custom-face-tf:compileDebugUnitTestKotlin`
Expected: FAIL because `TensorFlowProvider` still returns `null`

- [ ] **Step 3: Add module-owned prompt start strings**

Append to `biometric-custom-face-tf/src/main/res/values/strings.xml`:

```xml
<string name="biometriccompat_tf_face_prompt_start_auth">Align your face with the camera</string>
<string name="biometriccompat_tf_face_prompt_start_enroll">Align your face to enroll FaceTF</string>
```

- [ ] **Step 4: Add the thin FaceTF prompt factory**

```kotlin
package dev.skomlach.biometric.compat.engine.internal.face.tensorflow

import dev.skomlach.biometric.compat.BiometricType
import dev.skomlach.biometric.compat.custom.EngineBackedSoftwarePromptDelegate
import dev.skomlach.biometric.compat.custom.SoftwareBiometricPromptDelegate
import dev.skomlach.biometric.compat.custom.SoftwareBiometricPromptFactory
import dev.skomlach.biometric.compat.custom.SoftwareBiometricPromptHost
import dev.skomlach.biometric.custom.face.tf.R
import dev.skomlach.common.translate.LocalizationHelper

class TensorFlowFacePromptFactory : SoftwareBiometricPromptFactory {
    override val biometricType: BiometricType = BiometricType.BIOMETRIC_FACE

    override fun create(host: SoftwareBiometricPromptHost): SoftwareBiometricPromptDelegate {
        return EngineBackedSoftwarePromptDelegate(host) { enroll ->
            LocalizationHelper.getLocalizedString(
                host.context,
                if (enroll) {
                    R.string.biometriccompat_tf_face_prompt_start_enroll
                } else {
                    R.string.biometriccompat_tf_face_prompt_start_auth
                }
            )
        }
    }
}
```

- [ ] **Step 5: Expose the new factory from the provider**

Update `TensorFlowProvider.kt`:

```kotlin
    override fun getPromptFactory(): SoftwareBiometricPromptFactory {
        return TensorFlowFacePromptFactory()
    }
```

- [ ] **Step 6: Re-run the FaceTF compile gate**

Run: `.\gradlew.bat :biometric-custom-face-tf:compileDebugKotlin :biometric-custom-face-tf:compileDebugUnitTestKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add biometric-custom-face-tf/src/main/java/dev/skomlach/biometric/compat/engine/internal/face/tensorflow/TensorFlowFacePromptFactory.kt biometric-custom-face-tf/src/main/java/dev/skomlach/biometric/compat/engine/internal/face/tensorflow/TensorFlowProvider.kt biometric-custom-face-tf/src/main/res/values/strings.xml biometric-custom-face-tf/src/test/java/dev/skomlach/biometric/compat/engine/internal/face/tensorflow/TensorFlowProviderTest.kt
git commit -m "feat: add thin FaceTF prompt factory"
```

### Task 3: Wire ZKFinger To The Shared Thin Prompt Contract

**Files:**
- Create: `biometric-zkfinger/src/main/java/dev/skomlach/biometric/compat/engine/internal/fingerprint/zk/ZkFingerPromptFactory.kt`
- Modify: `biometric-zkfinger/src/main/java/dev/skomlach/biometric/compat/engine/internal/fingerprint/zk/ZkFingerProvider.kt`
- Modify: `biometric-zkfinger/src/main/res/values/strings.xml`
- Modify: `biometric-zkfinger/src/test/java/dev/skomlach/biometric/compat/engine/internal/fingerprint/zk/ZkFingerProviderTest.kt`

- [ ] **Step 1: Replace the old provider expectation with a failing prompt-factory test**

Update `ZkFingerProviderTest.kt` to:

```kotlin
package dev.skomlach.biometric.compat.engine.internal.fingerprint.zk

import dev.skomlach.biometric.compat.BiometricType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ZkFingerProviderTest {

    @Test
    fun zkFingerProviderExposesFingerprintPromptFactory() {
        val factory = ZkFingerProvider().getPromptFactory()

        assertNotNull(factory)
        assertEquals(BiometricType.BIOMETRIC_FINGERPRINT, factory?.biometricType)
    }
}
```

- [ ] **Step 2: Run the ZKFinger compile gate to verify it fails**

Run: `.\gradlew.bat :biometric-zkfinger:compileDebugUnitTestKotlin`
Expected: FAIL because `ZkFingerProvider` still returns `null`

- [ ] **Step 3: Add module-owned prompt start strings**

Append to `biometric-zkfinger/src/main/res/values/strings.xml`:

```xml
<string name="biometriccompat_zkfinger_prompt_start_auth">Place your finger on the ZK reader</string>
<string name="biometriccompat_zkfinger_prompt_start_enroll">Place your finger on the ZK reader to enroll</string>
```

- [ ] **Step 4: Add the thin ZKFinger prompt factory**

```kotlin
package dev.skomlach.biometric.compat.engine.internal.fingerprint.zk

import dev.skomlach.biometric.compat.BiometricType
import dev.skomlach.biometric.compat.custom.EngineBackedSoftwarePromptDelegate
import dev.skomlach.biometric.compat.custom.SoftwareBiometricPromptDelegate
import dev.skomlach.biometric.compat.custom.SoftwareBiometricPromptFactory
import dev.skomlach.biometric.compat.custom.SoftwareBiometricPromptHost
import dev.skomlach.biometric.zkfinger.R
import dev.skomlach.common.translate.LocalizationHelper

class ZkFingerPromptFactory : SoftwareBiometricPromptFactory {
    override val biometricType: BiometricType = BiometricType.BIOMETRIC_FINGERPRINT

    override fun create(host: SoftwareBiometricPromptHost): SoftwareBiometricPromptDelegate {
        return EngineBackedSoftwarePromptDelegate(host) { enroll ->
            LocalizationHelper.getLocalizedString(
                host.context,
                if (enroll) {
                    R.string.biometriccompat_zkfinger_prompt_start_enroll
                } else {
                    R.string.biometriccompat_zkfinger_prompt_start_auth
                }
            )
        }
    }
}
```

- [ ] **Step 5: Expose the new factory from the provider**

Update `ZkFingerProvider.kt`:

```kotlin
    override fun getPromptFactory(): SoftwareBiometricPromptFactory {
        return ZkFingerPromptFactory()
    }
```

- [ ] **Step 6: Re-run the ZKFinger compile gate**

Run: `.\gradlew.bat :biometric-zkfinger:compileDebugKotlin :biometric-zkfinger:compileDebugUnitTestKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add biometric-zkfinger/src/main/java/dev/skomlach/biometric/compat/engine/internal/fingerprint/zk/ZkFingerPromptFactory.kt biometric-zkfinger/src/main/java/dev/skomlach/biometric/compat/engine/internal/fingerprint/zk/ZkFingerProvider.kt biometric-zkfinger/src/main/res/values/strings.xml biometric-zkfinger/src/test/java/dev/skomlach/biometric/compat/engine/internal/fingerprint/zk/ZkFingerProviderTest.kt
git commit -m "feat: add thin ZKFinger prompt factory"
```

### Task 4: Run End-To-End Prompt Contract Verification

**Files:**
- Verify: `biometric/src/main/java/dev/skomlach/biometric/compat/impl/dialogs/BiometricPromptCompatDialogImpl.kt`
- Verify: `biometric-custom-face-tf/src/main/java/dev/skomlach/biometric/compat/engine/internal/face/tensorflow/TensorFlowProvider.kt`
- Verify: `biometric-zkfinger/src/main/java/dev/skomlach/biometric/compat/engine/internal/fingerprint/zk/ZkFingerProvider.kt`

- [ ] **Step 1: Run the combined compile proof**

Run: `.\gradlew.bat :biometric:compileDebugKotlin :biometric:compileDebugUnitTestKotlin :biometric-custom-face-tf:compileDebugKotlin :biometric-custom-face-tf:compileDebugUnitTestKotlin :biometric-zkfinger:compileDebugKotlin :biometric-zkfinger:compileDebugUnitTestKotlin :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Run regression sweeps**

Run: `git -c safe.directory=C:/Users/skoml/StudioProjects_5/AdvancedBiometricPromptCompat diff --check`
Expected: no whitespace errors

Run: `rg -n "getPromptFactory\\(\\): SoftwareBiometricPromptFactory\\? = null|override fun getPromptFactory\\(\\): SoftwareBiometricPromptFactory\\? = null" biometric-custom-face-tf biometric-zkfinger`
Expected: no matches

- [ ] **Step 3: Commit any final verification-only doc tweaks if needed**

```bash
git status --short
```

Expected: no uncommitted code changes

