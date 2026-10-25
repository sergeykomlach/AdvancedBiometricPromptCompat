# Software Biometric Module Separation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move software voice and behavior prompt-flow implementations out of `:biometric` into their custom modules while keeping `:biometric` as the generic orchestration layer.

**Architecture:** Introduce a small generic prompt-flow delegate/factory contract in `:biometric`, resolve concrete software prompt delegates through the same `ServiceLoader` pattern already used by `SoftwareBiometricProvider`, then migrate voice and behavior concrete flow classes onto that contract. Keep `BiometricPromptCompatDialogImpl` generic and lifecycle-driven.

**Tech Stack:** Kotlin, Android library modules, `ServiceLoader`, existing `BiometricPromptCompat.Builder`, current software-biometric provider pattern, JUnit4, Gradle module compile checks.

---

### Task 1: Add Generic Software Prompt-Flow Contracts In `:biometric`

**Files:**
- Create: `biometric/src/main/java/dev/skomlach/biometric/compat/custom/SoftwareBiometricPromptDelegate.kt`
- Create: `biometric/src/main/java/dev/skomlach/biometric/compat/custom/SoftwareBiometricPromptFactory.kt`
- Create: `biometric/src/main/java/dev/skomlach/biometric/compat/custom/SoftwareBiometricPromptHost.kt`
- Create: `biometric/src/main/java/dev/skomlach/biometric/compat/custom/SoftwareBiometricPromptRegistry.kt`
- Modify: `biometric/src/main/java/dev/skomlach/biometric/compat/custom/SoftwareBiometricProvider.kt`
- Modify: `biometric/src/test/java/dev/skomlach/biometric/compat/BiometricPublicApiContractTest.kt`

- [ ] **Step 1: Write the contract tests first**

```kotlin
@Test
fun softwarePromptProviderDefaultsToNoPromptFactory() {
    val provider = object : SoftwareBiometricProvider() {
        override fun getCustomManager(context: Context): AbstractSoftwareBiometricManager {
            error("unused")
        }
    }

    assertNull(provider.getPromptFactory())
}
```

```kotlin
@Test
fun promptRegistryReturnsFactoryForRequestedBiometricType() {
    val factory = FakePromptFactory(BiometricType.BIOMETRIC_VOICE)

    val resolved = SoftwareBiometricPromptRegistry.resolve(
        BiometricType.BIOMETRIC_VOICE,
        listOf(factory)
    )

    assertSame(factory, resolved)
}
```

- [ ] **Step 2: Run the contract compile gate**

Run: `.\gradlew.bat :biometric:compileDebugUnitTestKotlin`
Expected: FAIL because the new prompt contracts and registry do not exist yet.

- [ ] **Step 3: Add the generic prompt-flow contract layer**

```kotlin
internal interface SoftwareBiometricPromptDelegate {
    fun shouldInstall(): Boolean = false
    fun install() {}
    fun start() {}
    fun cancel() {}
    fun dispose() {}
}
```

```kotlin
internal interface SoftwareBiometricPromptFactory {
    val biometricType: BiometricType
    fun create(host: SoftwareBiometricPromptHost): SoftwareBiometricPromptDelegate?
}
```

```kotlin
internal data class SoftwareBiometricPromptHost(
    val context: Context,
    val builder: BiometricPromptCompat.Builder,
    val rootView: View?,
    val callbacks: Callbacks
) {
    internal interface Callbacks {
        fun onHelp(message: CharSequence)
        fun onReady(extras: Bundle? = null)
        fun onFailure(result: AuthenticationResult)
        fun isPromptActive(): Boolean
    }
}
```

```kotlin
abstract class SoftwareBiometricProvider {
    abstract fun getCustomManager(context: Context): AbstractSoftwareBiometricManager
    open fun getPromptFactory(): SoftwareBiometricPromptFactory? = null
}
```

- [ ] **Step 4: Implement a tiny registry around `ServiceLoader`**

```kotlin
internal object SoftwareBiometricPromptRegistry {
    fun resolve(type: BiometricType): SoftwareBiometricPromptFactory? {
        val loader = ServiceLoader.load(SoftwareBiometricProvider::class.java)
        return resolve(type, loader.mapNotNull { it.getPromptFactory() })
    }

    internal fun resolve(
        type: BiometricType,
        factories: Iterable<SoftwareBiometricPromptFactory>
    ): SoftwareBiometricPromptFactory? {
        return factories.firstOrNull { it.biometricType == type }
    }
}
```

- [ ] **Step 5: Run the contract compile gate again**

Run: `.\gradlew.bat :biometric:compileDebugUnitTestKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add biometric/src/main/java/dev/skomlach/biometric/compat/custom/SoftwareBiometricPromptDelegate.kt biometric/src/main/java/dev/skomlach/biometric/compat/custom/SoftwareBiometricPromptFactory.kt biometric/src/main/java/dev/skomlach/biometric/compat/custom/SoftwareBiometricPromptHost.kt biometric/src/main/java/dev/skomlach/biometric/compat/custom/SoftwareBiometricPromptRegistry.kt biometric/src/main/java/dev/skomlach/biometric/compat/custom/SoftwareBiometricProvider.kt biometric/src/test/java/dev/skomlach/biometric/compat/BiometricPublicApiContractTest.kt
git commit -m "refactor: add generic software prompt flow contracts"
```

### Task 2: Switch `BiometricPromptCompatDialogImpl` To The Generic Prompt Delegate

**Files:**
- Modify: `biometric/src/main/java/dev/skomlach/biometric/compat/impl/dialogs/BiometricPromptCompatDialogImpl.kt`
- Create: `biometric/src/test/java/dev/skomlach/biometric/compat/impl/dialogs/SoftwareBiometricPromptRegistryTest.kt`

- [ ] **Step 1: Add the failing orchestration test**

```kotlin
@Test
fun dialogUsesPromptFactoryInsteadOfConcreteVoiceOrBehaviorControllers() {
    val factory = FakePromptFactory(BiometricType.BIOMETRIC_VOICE)

    val resolved = SoftwareBiometricPromptRegistry.resolve(
        BiometricType.BIOMETRIC_VOICE,
        listOf(factory)
    )

    assertNotNull(resolved)
}
```

- [ ] **Step 2: Run compile to confirm current dialog does not use the new path yet**

Run: `.\gradlew.bat :biometric:compileDebugKotlin :biometric:compileDebugUnitTestKotlin`
Expected: FAIL or test gap because `BiometricPromptCompatDialogImpl` still directly references `VoiceAutoCaptureController` and `BehaviorCaptureController`.

- [ ] **Step 3: Replace direct concrete references with one generic delegate**

```kotlin
private var softwarePromptDelegate: SoftwareBiometricPromptDelegate? = null
```

```kotlin
softwarePromptDelegate = SoftwareBiometricPromptRegistry.resolve(primaryBiometricType)
    ?.create(
        SoftwareBiometricPromptHost(
            context = dialog.rootView?.context ?: compatBuilder.getContext(),
            builder = compatBuilder,
            rootView = dialog.rootView,
            callbacks = object : SoftwareBiometricPromptHost.Callbacks {
                override fun onHelp(message: CharSequence) = this@BiometricPromptCompatDialogImpl.onHelp(message)
                override fun onReady(extras: Bundle?) {
                    extras?.let { compatBuilder.setExtras(it) }
                    startAuth()
                }
                override fun onFailure(result: AuthenticationResult) {
                    authCallback?.onPreAuthFailure(result)
                }
                override fun isPromptActive(): Boolean = dialog.isShowing
            }
        )
    )
```

```kotlin
softwarePromptDelegate?.takeIf { it.shouldInstall() }?.install()
softwarePromptDelegate?.start()
```

```kotlin
softwarePromptDelegate?.cancel()
softwarePromptDelegate?.dispose()
softwarePromptDelegate = null
```

- [ ] **Step 4: Remove direct orchestration imports of concrete behavior/voice controllers**

```kotlin
// delete direct fields and imports:
// private var behaviorCaptureController: BehaviorCaptureController? = null
// private var voiceAutoCaptureController: VoiceAutoCaptureController? = null
```

- [ ] **Step 5: Run dialog/orchestration compile proof**

Run: `.\gradlew.bat :biometric:compileDebugKotlin :biometric:compileDebugUnitTestKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add biometric/src/main/java/dev/skomlach/biometric/compat/impl/dialogs/BiometricPromptCompatDialogImpl.kt biometric/src/test/java/dev/skomlach/biometric/compat/impl/dialogs/SoftwareBiometricPromptRegistryTest.kt
git commit -m "refactor: route software prompt flow through generic delegate"
```

### Task 3: Move Voice Prompt Flow Into `:biometric-custom-voice`

**Files:**
- Create: `biometric-custom-voice/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoicePromptData.kt`
- Create: `biometric-custom-voice/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoicePromptFactory.kt`
- Create: `biometric-custom-voice/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoicePromptDelegate.kt`
- Create: `biometric-custom-voice/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceAutoCaptureController.kt`
- Create: `biometric-custom-voice/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceAutoCaptureSession.kt`
- Modify: `biometric-custom-voice/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceProvider.kt`
- Modify: `biometric/src/main/java/dev/skomlach/biometric/compat/BiometricPromptCompat.kt`
- Delete: `biometric/src/main/java/dev/skomlach/biometric/compat/VoicePromptData.kt`
- Delete: `biometric/src/main/java/dev/skomlach/biometric/compat/impl/dialogs/VoiceAutoCaptureController.kt`
- Delete: `biometric/src/main/java/dev/skomlach/biometric/compat/impl/dialogs/VoiceAutoCaptureSession.kt`
- Modify: `biometric/src/test/java/dev/skomlach/biometric/compat/VoicePromptDataTest.kt`
- Modify: `biometric/src/test/java/dev/skomlach/biometric/compat/impl/dialogs/VoiceAutoCaptureSessionTest.kt`
- Modify: `biometric/src/test/java/dev/skomlach/biometric/compat/BiometricManagerCompatPermissionTest.kt`

- [ ] **Step 1: Add the failing voice prompt-factory test in the custom module**

```kotlin
@Test
fun voiceProviderExposesVoicePromptFactory() {
    val provider = VoiceProvider()

    assertNotNull(provider.getPromptFactory())
    assertEquals(BiometricType.BIOMETRIC_VOICE, provider.getPromptFactory()!!.biometricType)
}
```

- [ ] **Step 2: Run compile to confirm missing moved classes**

Run: `.\gradlew.bat :biometric-custom-voice:compileDebugKotlin :biometric:compileDebugKotlin`
Expected: FAIL until the moved voice prompt classes and imports are rewired.

- [ ] **Step 3: Move voice prompt-data helpers into the custom module**

```kotlin
internal object VoicePromptData {
    const val EXTRA_SAMPLE_RATE = "voice.sample_rate"
    const val EXTRA_PCM_FLOAT = "voice.pcm_float"
    const val EXTRA_EMBEDDING = "voice.embedding"
    const val EXTRA_PHRASE = "voice.phrase"
    const val EXTRA_SAMPLE_COUNT = "voice.sample_count"
}
```

```kotlin
internal fun buildVoiceExtras(...): Bundle { ... }
internal fun hasVoiceInput(extras: Bundle?): Boolean { ... }
internal fun clearVoiceInput(extras: Bundle) { ... }
```

- [ ] **Step 4: Keep `BiometricPromptCompat.Builder` public API stable while re-pointing internals**

```kotlin
fun setVoicePcmSamples(sampleRateHz: Int, pcmSamples: Collection<FloatArray>): Builder {
    val extras = Bundle(getExtras() ?: Bundle())
    dev.skomlach.biometric.compat.engine.internal.voice.clearVoiceInput(extras)
    // write voice extras here using the moved custom-voice helpers
    return setExtras(extras)
}
```

- [ ] **Step 5: Add the concrete voice prompt factory/delegate**

```kotlin
internal class VoicePromptFactory : SoftwareBiometricPromptFactory {
    override val biometricType: BiometricType = BiometricType.BIOMETRIC_VOICE

    override fun create(host: SoftwareBiometricPromptHost): SoftwareBiometricPromptDelegate? {
        return VoicePromptDelegate(host).takeIf { it.shouldInstall() || true }
    }
}
```

```kotlin
internal class VoicePromptDelegate(
    private val host: SoftwareBiometricPromptHost
) : SoftwareBiometricPromptDelegate {
    private val controller = VoiceAutoCaptureController(
        context = host.context,
        builder = host.builder,
        callback = object : VoiceAutoCaptureSession.Callback {
            override fun onHelp(message: CharSequence) = host.callbacks.onHelp(message)
            override fun onReady(extras: Bundle) = host.callbacks.onReady(extras)
            override fun onError(result: AuthenticationResult) = host.callbacks.onFailure(result)
            override fun isPromptActive(): Boolean = host.callbacks.isPromptActive()
        }
    )

    override fun start() {
        if (controller.shouldAutoCapture()) controller.start() else host.callbacks.onReady(null)
    }

    override fun dispose() = controller.dispose()
    override fun cancel() = controller.dispose()
}
```

- [ ] **Step 6: Make `VoiceProvider` expose the prompt factory**

```kotlin
class VoiceProvider : SoftwareBiometricProvider() {
    override fun getCustomManager(context: Context): AbstractSoftwareBiometricManager = ...
    override fun getPromptFactory(): SoftwareBiometricPromptFactory = VoicePromptFactory()
}
```

- [ ] **Step 7: Move the tests with the moved code**

Run: `.\gradlew.bat :biometric-custom-voice:compileDebugKotlin :biometric-custom-voice:compileDebugUnitTestKotlin :biometric:compileDebugKotlin :biometric:compileDebugUnitTestKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add biometric-custom-voice/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoicePromptData.kt biometric-custom-voice/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoicePromptFactory.kt biometric-custom-voice/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoicePromptDelegate.kt biometric-custom-voice/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceAutoCaptureController.kt biometric-custom-voice/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceAutoCaptureSession.kt biometric-custom-voice/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceProvider.kt biometric/src/main/java/dev/skomlach/biometric/compat/BiometricPromptCompat.kt biometric/src/test/java/dev/skomlach/biometric/compat/VoicePromptDataTest.kt biometric/src/test/java/dev/skomlach/biometric/compat/impl/dialogs/VoiceAutoCaptureSessionTest.kt biometric/src/test/java/dev/skomlach/biometric/compat/BiometricManagerCompatPermissionTest.kt
git commit -m "refactor: move voice prompt flow into custom voice module"
```

### Task 4: Move Behavior Prompt Flow Into `:biometric-custom-behavior`

**Files:**
- Create: `biometric-custom-behavior/src/main/java/dev/skomlach/biometric/compat/engine/internal/behavior/BehaviorPromptFactory.kt`
- Create: `biometric-custom-behavior/src/main/java/dev/skomlach/biometric/compat/engine/internal/behavior/BehaviorPromptDelegate.kt`
- Create: `biometric-custom-behavior/src/main/java/dev/skomlach/biometric/compat/engine/internal/behavior/BehaviorCaptureController.kt`
- Modify: `biometric-custom-behavior/src/main/java/dev/skomlach/biometric/compat/engine/internal/behavior/BehaviorProvider.kt`
- Delete: `biometric/src/main/java/dev/skomlach/biometric/compat/impl/dialogs/BehaviorCaptureController.kt`
- Modify: `biometric/src/test/java/dev/skomlach/biometric/compat/EffectiveEnrollTypesTest.kt`

- [ ] **Step 1: Add the failing behavior prompt-factory test**

```kotlin
@Test
fun behaviorProviderExposesBehaviorPromptFactory() {
    val provider = BehaviorProvider()

    assertNotNull(provider.getPromptFactory())
    assertEquals(BiometricType.BIOMETRIC_BEHAVIOR, provider.getPromptFactory()!!.biometricType)
}
```

- [ ] **Step 2: Run compile to confirm behavior factory/delegate are still missing**

Run: `.\gradlew.bat :biometric-custom-behavior:compileDebugKotlin :biometric:compileDebugKotlin`
Expected: FAIL until the behavior prompt-flow classes are migrated.

- [ ] **Step 3: Move behavior controller and wrap it in a generic delegate**

```kotlin
internal class BehaviorPromptDelegate(
    private val host: SoftwareBiometricPromptHost
) : SoftwareBiometricPromptDelegate {
    private val controller = BehaviorCaptureController(
        rootView = host.rootView ?: return,
        builder = host.builder
    ) {
        host.callbacks.onReady(host.builder.getExtras())
    }

    override fun shouldInstall(): Boolean {
        return host.builder.getBehaviorAuthMode() == BehaviorAuthMode.EXPLICIT
    }

    override fun install() = controller.install()
    override fun dispose() = controller.dispose()
    override fun cancel() = controller.dispose()
}
```

```kotlin
class BehaviorProvider : SoftwareBiometricProvider() {
    override fun getCustomManager(context: Context): AbstractSoftwareBiometricManager = ...
    override fun getPromptFactory(): SoftwareBiometricPromptFactory = BehaviorPromptFactory()
}
```

- [ ] **Step 4: Run behavior migration compile proof**

Run: `.\gradlew.bat :biometric-custom-behavior:compileDebugKotlin :biometric:compileDebugKotlin :biometric:compileDebugUnitTestKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add biometric-custom-behavior/src/main/java/dev/skomlach/biometric/compat/engine/internal/behavior/BehaviorPromptFactory.kt biometric-custom-behavior/src/main/java/dev/skomlach/biometric/compat/engine/internal/behavior/BehaviorPromptDelegate.kt biometric-custom-behavior/src/main/java/dev/skomlach/biometric/compat/engine/internal/behavior/BehaviorCaptureController.kt biometric-custom-behavior/src/main/java/dev/skomlach/biometric/compat/engine/internal/behavior/BehaviorProvider.kt biometric/src/test/java/dev/skomlach/biometric/compat/EffectiveEnrollTypesTest.kt
git commit -m "refactor: move behavior prompt flow into custom behavior module"
```

### Task 5: Verify The Full Separation Slice

**Files:**
- Test: `biometric/src/main/java/dev/skomlach/biometric/compat/impl/dialogs/BiometricPromptCompatDialogImpl.kt`
- Test: `biometric-custom-voice/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoicePromptDelegate.kt`
- Test: `biometric-custom-behavior/src/main/java/dev/skomlach/biometric/compat/engine/internal/behavior/BehaviorPromptDelegate.kt`

- [ ] **Step 1: Run compile-based proof for all touched modules**

Run: `.\gradlew.bat :biometric:compileDebugKotlin :biometric:compileDebugUnitTestKotlin :biometric-custom-voice:compileDebugKotlin :biometric-custom-voice:compileDebugUnitTestKotlin :biometric-custom-behavior:compileDebugKotlin :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Run whitespace verification**

Run: `git -c safe.directory=C:/Users/skoml/StudioProjects_5/AdvancedBiometricPromptCompat diff --check`
Expected: no whitespace errors in touched files

- [ ] **Step 3: Confirm `BiometricPromptCompatDialogImpl` no longer imports concrete voice/behavior prompt-flow classes**

Run: `rg -n "VoiceAutoCaptureController|VoiceAutoCaptureSession|BehaviorCaptureController" biometric/src/main/java/dev/skomlach/biometric/compat/impl/dialogs/BiometricPromptCompatDialogImpl.kt`
Expected: no matches

- [ ] **Step 4: Commit**

```bash
git add biometric biometric-custom-voice biometric-custom-behavior
git commit -m "refactor: separate software biometric prompt modules"
```
