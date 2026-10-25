# Face Route Resolver Enroll/Auth Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `setupBiometric()` and `authenticate()` use one route-selection source of truth so preferred system face routes stay selected for enroll/auth when available, while software biometrics remain valid fallbacks.

**Architecture:** Introduce a small shared resolver in the biometric compat core that decides the selected route per biometric type for the current builder/request context. Reuse that resolver from enroll-effective-type filtering, permission gathering, and route-aware auth selection instead of having partially duplicated selection logic in each stage.

**Tech Stack:** Kotlin, AndroidX Biometric, JUnit4 unit tests, existing biometric compat routing/helpers

---

### Task 1: Lock The Contract With Failing Tests

**Files:**
- Modify: `biometric/src/test/java/dev/skomlach/biometric/compat/EffectiveEnrollTypesTest.kt`
- Create: `biometric/src/test/java/dev/skomlach/biometric/compat/SelectedBiometricRouteResolverTest.kt`

- [ ] **Step 1: Write the failing tests for enroll route retention**

```kotlin
@Test
fun `keeps preferred system face route during enroll when hardware route is selected`() {
    val routes = mapOf(
        BiometricType.BIOMETRIC_FACE to SelectedBiometricRoute(
            type = BiometricType.BIOMETRIC_FACE,
            provider = BiometricProviderType.HARDWARE,
            usesBiometricPromptHardware = true,
            permissions = emptyList()
        )
    )

    val effectiveTypes = resolveEffectiveEnrollTypes(
        types = listOf(BiometricType.BIOMETRIC_FACE),
        hasSystemHardware = { true },
        keepSystemType = { type -> routes[type]?.usesBiometricPromptHardware == true },
        isActive = { true }
    )

    assertEquals(listOf(BiometricType.BIOMETRIC_FACE), effectiveTypes)
}
```

```kotlin
@Test
fun `prefers hardware face permissions over software camera permissions when hardware route is selected`() {
    val route = SelectedBiometricRoute(
        type = BiometricType.BIOMETRIC_FACE,
        provider = BiometricProviderType.HARDWARE,
        usesBiometricPromptHardware = true,
        permissions = listOf("com.samsung.android.bio.face.permission.USE_FACE")
    )

    assertEquals(
        listOf("com.samsung.android.bio.face.permission.USE_FACE"),
        route.permissions
    )
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `.\gradlew.bat :biometric:testDebugUnitTest --tests "dev.skomlach.biometric.compat.EffectiveEnrollTypesTest" --tests "dev.skomlach.biometric.compat.SelectedBiometricRouteResolverTest"`

Expected: FAIL because `SelectedBiometricRoute` / shared resolver do not exist yet, and the current enroll contract does not explicitly preserve the selected hardware face route.

- [ ] **Step 3: Keep the tests focused on route selection, not Samsung-only branches**

```kotlin
@Test
fun `software face route remains available when hardware route is not selected`() {
    val route = SelectedBiometricRoute(
        type = BiometricType.BIOMETRIC_FACE,
        provider = BiometricProviderType.SOFTWARE,
        usesBiometricPromptHardware = false,
        permissions = listOf(Manifest.permission.CAMERA)
    )

    assertEquals(listOf(Manifest.permission.CAMERA), route.permissions)
    assertFalse(route.usesBiometricPromptHardware)
}
```

- [ ] **Step 4: Commit**

```bash
git add biometric/src/test/java/dev/skomlach/biometric/compat/EffectiveEnrollTypesTest.kt biometric/src/test/java/dev/skomlach/biometric/compat/SelectedBiometricRouteResolverTest.kt
git commit -m "test: lock route selection contract for enroll and auth"
```

### Task 2: Introduce A Shared Selected Route Resolver

**Files:**
- Modify: `biometric/src/main/java/dev/skomlach/biometric/compat/BiometricAuthState.kt`
- Modify: `biometric/src/main/java/dev/skomlach/biometric/compat/BiometricPromptCompat.kt`

- [ ] **Step 1: Add a small route model and resolver helpers**

```kotlin
internal data class SelectedBiometricRoute(
    val type: BiometricType,
    val provider: BiometricProviderType,
    val usesBiometricPromptHardware: Boolean,
    val permissions: List<String>
)
```

```kotlin
internal fun shouldKeepSystemEnrollType(route: SelectedBiometricRoute?): Boolean {
    return route?.usesBiometricPromptHardware == true
}
```

- [ ] **Step 2: Run tests to verify they still fail for the right reason**

Run: `.\gradlew.bat :biometric:testDebugUnitTest --tests "dev.skomlach.biometric.compat.SelectedBiometricRouteResolverTest"`

Expected: FAIL because `BiometricPromptCompat.Builder` is not using the new resolver yet.

- [ ] **Step 3: Implement a builder-scoped resolver and reuse it**

```kotlin
private fun selectedRoute(type: BiometricType): SelectedBiometricRoute? {
    if (getPrimaryAvailableTypes().contains(type) && hasBiometricPromptHardwareType(type)) {
        val request = biometricAuthRequest
            .withType(type)
            .withApi(BiometricApi.BIOMETRIC_API)
            .withProvider(BiometricProviderType.HARDWARE)
        return SelectedBiometricRoute(
            type = type,
            provider = BiometricProviderType.HARDWARE,
            usesBiometricPromptHardware = true,
            permissions = BiometricManagerCompat.getUsedPermissions(listOf(type), request, enroll)
        )
    }

    val module = LegacyBiometric.getSelectedBiometricModule(
        type,
        biometricAuthRequest.provider,
        enroll,
        getDisabledModuleTags()
    ) ?: return null

    return SelectedBiometricRoute(
        type = type,
        provider = if (module is SoftwareBiometricModule) {
            BiometricProviderType.SOFTWARE
        } else {
            biometricAuthRequest.provider
        },
        usesBiometricPromptHardware = false,
        permissions = BiometricManagerCompat.getUsedPermissions(module)
    )
}
```

- [ ] **Step 4: Replace duplicated route decisions with the shared resolver**

```kotlin
internal fun getSelectedTypePermissions(type: BiometricType): List<String> {
    return selectedRoute(type)?.permissions ?: emptyList()
}
```

```kotlin
return resolveEffectiveEnrollTypes(
    types = allTypes,
    hasSystemHardware = { type -> ... },
    keepSystemType = { type -> shouldKeepSystemEnrollType(selectedRoute(type)) },
    isActive = { type -> ... }
)
```

- [ ] **Step 5: Commit**

```bash
git add biometric/src/main/java/dev/skomlach/biometric/compat/BiometricAuthState.kt biometric/src/main/java/dev/skomlach/biometric/compat/BiometricPromptCompat.kt
git commit -m "fix: share selected biometric route resolution"
```

### Task 3: Verify Auth/Enroll Behavior And Guard Against Regressions

**Files:**
- Modify: `biometric/src/test/java/dev/skomlach/biometric/compat/SelectedBiometricRouteResolverTest.kt`
- Test: `biometric/src/test/java/dev/skomlach/biometric/compat/EffectiveEnrollTypesTest.kt`

- [ ] **Step 1: Add regression coverage for auth and software fallback**

```kotlin
@Test
fun `auth route can still use software face when hardware route is unavailable`() {
    val route = SelectedBiometricRoute(
        type = BiometricType.BIOMETRIC_FACE,
        provider = BiometricProviderType.SOFTWARE,
        usesBiometricPromptHardware = false,
        permissions = listOf(Manifest.permission.CAMERA)
    )

    assertEquals(BiometricProviderType.SOFTWARE, route.provider)
}
```

```kotlin
@Test
fun `hardware route selection does not require camera permission`() {
    val route = SelectedBiometricRoute(
        type = BiometricType.BIOMETRIC_FACE,
        provider = BiometricProviderType.HARDWARE,
        usesBiometricPromptHardware = true,
        permissions = listOf("com.samsung.android.bio.face.permission.USE_FACE")
    )

    assertFalse(route.permissions.contains(Manifest.permission.CAMERA))
}
```

- [ ] **Step 2: Run targeted unit tests**

Run: `.\gradlew.bat :biometric:testDebugUnitTest --tests "dev.skomlach.biometric.compat.EffectiveEnrollTypesTest" --tests "dev.skomlach.biometric.compat.SelectedBiometricRouteResolverTest" --tests "dev.skomlach.biometric.compat.BiometricManagerCompatPermissionTest"`

Expected: PASS for all targeted route-selection and permission tests.

- [ ] **Step 3: Run compile verification**

Run: `.\gradlew.bat :biometric:compileDebugKotlin :biometric:compileDebugUnitTestKotlin`

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add biometric/src/test/java/dev/skomlach/biometric/compat/SelectedBiometricRouteResolverTest.kt biometric/src/test/java/dev/skomlach/biometric/compat/EffectiveEnrollTypesTest.kt
git commit -m "test: cover shared route resolver regressions"
```
