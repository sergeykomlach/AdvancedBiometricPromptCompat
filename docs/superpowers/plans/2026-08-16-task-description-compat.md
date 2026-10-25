# TaskDescription Compatibility Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Synchronize the owning activity's task-description colors with the final system-bar colors on every Android API supported by `common`, without changing the existing public API.

**Architecture:** Add a small `TaskDescriptionCompat` adapter beside `StatusBarTools`. A pure color-policy function isolates and unit-tests the opacity rule; the Android adapter uses guarded public framework APIs for API 33+, API 28–32, and API 23–27. `StatusBarTools` returns its final resolved bar colors internally and invokes the adapter only after both window colors have been applied.

**Tech Stack:** Kotlin, Android SDK 23–36, `ActivityManager.TaskDescription`, JUnit 4, Gradle.

## Global Constraints

- Keep `StatusBarTools.setNavBarAndStatusBarColors(...)` source- and binary-compatible.
- Support API 23 and newer; `common` has `minSdk 23` and `compileSdk 36`.
- Use only public Android SDK APIs; no reflection or hidden APIs.
- Use final resolved system-bar colors, after the existing contrast and icon-mode adjustments.
- Use status-bar color for task primary/background/status fields and navigation-bar color for the task navigation field.
- Do not set non-opaque primary or background task colors.
- Do not add dependencies or modify call sites outside `common`.
- Leave the task label unspecified. On API 23–27 render `ApplicationInfo.loadIcon(PackageManager)` to a valid bitmap for the legacy constructor; do not introduce custom task labels or icons.
- Do not commit without explicit user approval.

---

### Task 1: Add TaskDescription compatibility and integrate resolved bar colors

**Files:**
- Create: `common/src/main/java/dev/skomlach/common/statusbar/TaskDescriptionCompat.kt`
- Modify: `common/src/main/java/dev/skomlach/common/statusbar/StatusBarTools.kt:57-185`
- Test: `common/src/test/java/dev/skomlach/common/statusbar/TaskDescriptionCompatPolicyTest.kt`

**Interfaces:**
- Consumes: final `statusBarColor: Int` and `navigationBarColor: Int` returned internally by `StatusBarTools`.
- Produces: `resolveTaskDescriptionColors(statusBarColor: Int, navigationBarColor: Int): TaskDescriptionColors` and `updateTaskDescriptionCompat(window: Window, statusBarColor: Int, navigationBarColor: Int)`.
- Public API impact: none.

- [ ] **Step 1: Write the failing color-policy tests**

Create `common/src/test/java/dev/skomlach/common/statusbar/TaskDescriptionCompatPolicyTest.kt`:

```kotlin
package dev.skomlach.common.statusbar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaskDescriptionCompatPolicyTest {

    @Test
    fun opaqueStatusBarColorPopulatesPrimaryAndBackgroundColors() {
        val statusBarColor = 0xFF123456.toInt()
        val navigationBarColor = 0xFF654321.toInt()

        val colors = resolveTaskDescriptionColors(statusBarColor, navigationBarColor)

        assertEquals(statusBarColor, colors.primaryColor)
        assertEquals(statusBarColor, colors.backgroundColor)
        assertEquals(statusBarColor, colors.statusBarColor)
        assertEquals(navigationBarColor, colors.navigationBarColor)
    }

    @Test
    fun translucentStatusBarColorLeavesOpaqueTaskColorsUnset() {
        val statusBarColor = 0x80123456.toInt()
        val navigationBarColor = 0xFF654321.toInt()

        val colors = resolveTaskDescriptionColors(statusBarColor, navigationBarColor)

        assertNull(colors.primaryColor)
        assertNull(colors.backgroundColor)
        assertEquals(statusBarColor, colors.statusBarColor)
        assertEquals(navigationBarColor, colors.navigationBarColor)
    }
}
```

The first test catches accidental remapping of primary/background/status to the navigation color. The second catches passing a translucent primary color to pre-33 constructors, which would throw at runtime.

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```powershell
.\gradlew.bat :common:testDebugUnitTest --tests dev.skomlach.common.statusbar.TaskDescriptionCompatPolicyTest '-Pkotlin.incremental=false' --no-daemon --console=plain
```

Expected: FAIL during test compilation because `resolveTaskDescriptionColors` and `TaskDescriptionColors` do not exist. This is the intended missing-feature failure.

- [ ] **Step 3: Implement the minimal color policy and public-SDK adapter**

Create `common/src/main/java/dev/skomlach/common/statusbar/TaskDescriptionCompat.kt`:

```kotlin
package dev.skomlach.common.statusbar

import android.app.Activity
import android.app.ActivityManager
import android.os.Build
import android.view.Window
import androidx.annotation.ColorInt
import androidx.core.graphics.drawable.toBitmap
import dev.skomlach.common.logging.LogCat

internal data class TaskDescriptionColors(
    @param:ColorInt val primaryColor: Int?,
    @param:ColorInt val backgroundColor: Int?,
    @param:ColorInt val statusBarColor: Int,
    @param:ColorInt val navigationBarColor: Int
)

internal fun resolveTaskDescriptionColors(
    @ColorInt statusBarColor: Int,
    @ColorInt navigationBarColor: Int
): TaskDescriptionColors {
    val opaqueTaskColor = statusBarColor.takeIf { it ushr 24 == 0xFF }
    return TaskDescriptionColors(
        primaryColor = opaqueTaskColor,
        backgroundColor = opaqueTaskColor,
        statusBarColor = statusBarColor,
        navigationBarColor = navigationBarColor
    )
}

@Suppress("DEPRECATION")
internal fun updateTaskDescriptionCompat(
    window: Window,
    @ColorInt statusBarColor: Int,
    @ColorInt navigationBarColor: Int
) {
    val activity = window.callback as? Activity ?: return
    val colors = resolveTaskDescriptionColors(statusBarColor, navigationBarColor)
    val iconRes = activity.applicationInfo.icon

    try {
        val taskDescription = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                ActivityManager.TaskDescription.Builder().apply {
                    colors.primaryColor?.let { setPrimaryColor(it) }
                    colors.backgroundColor?.let { setBackgroundColor(it) }
                    setStatusBarColor(colors.statusBarColor)
                    setNavigationBarColor(colors.navigationBarColor)
                }.build()
            }

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> {
                if (iconRes != 0) {
                    ActivityManager.TaskDescription(null, iconRes, colors.primaryColor ?: 0)
                } else {
                    ActivityManager.TaskDescription(
                        null,
                        activity.applicationInfo.loadIcon(activity.packageManager).toBitmap(),
                        colors.primaryColor ?: 0
                    )
                }
            }

            else -> ActivityManager.TaskDescription(
                null,
                activity.applicationInfo.loadIcon(activity.packageManager).toBitmap(),
                colors.primaryColor ?: 0
            )
        }
        activity.setTaskDescription(taskDescription)
    } catch (e: Throwable) {
        LogCat.logException(e)
    }
}
```

This adapter deliberately does not mock or reimplement Android framework behavior. The pure mapping is the library-owned contract; guarded constructors are verified by compilation and lint.

- [ ] **Step 4: Integrate the adapter with final resolved colors**

In `StatusBarTools.setNavBarAndStatusBarColors(...)`, replace the runnable body with:

```kotlin
val runnable = Runnable {
    val resolvedStatusBarColor =
        setStatusBarColor(window, colorStatusBar, isAppLightTheme)
    val resolvedNavigationBarColor =
        setNavBarColor(window, colorNavBar, dividerColor, isAppLightTheme)
    if (resolvedStatusBarColor != null && resolvedNavigationBarColor != null) {
        updateTaskDescriptionCompat(
            window,
            resolvedStatusBarColor,
            resolvedNavigationBarColor
        )
    }
}
```

Change both private helpers to return `Int?`. Keep all existing behavior, and return the final color only after the corresponding window fields have been updated:

```kotlin
private fun setNavBarColor(
    window: Window,
    @ColorInt c: Int,
    @ColorInt dividerColor: Int,
    isAppLightTheme: Boolean?
): Int? {
    var color = c
    if (TURNOFF_TINT) return null
    return try {
        if (translucentNavBar) color = Color.TRANSPARENT
        val isDark = isAppLightTheme?.let {
            if (ColorUtil.colorDistance(color, Color.TRANSPARENT) <= 0.25) {
                !it
            } else {
                ColorUtil.isDark(color)
            }
        } ?: run {
            if (ColorUtil.colorDistance(color, Color.TRANSPARENT) <= 0.1) {
                isNightMode(window.context)
            } else {
                ColorUtil.isDark(color)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        if (!StatusBarIconsDarkMode.setDarkIconMode(window, !isDark, BarType.NAVBAR)) {
            color = if (!isDark) {
                ColorUtil.blend(color, Color.BLACK, alpha.toDouble())
            } else {
                ColorUtil.blend(color, Color.WHITE, alpha.toDouble())
            }
        }
        if (Build.VERSION.SDK_INT >= 21) {
            window.navigationBarColor = color
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.navigationBarDividerColor = dividerColor
        }
        color
    } catch (e: Throwable) {
        LogCat.logException(e)
        null
    }
}

private fun setStatusBarColor(
    window: Window,
    @ColorInt c: Int,
    isAppLightTheme: Boolean?
): Int? {
    var color = c
    if (TURNOFF_TINT) return null
    return try {
        if (translucentStatusBar) color = Color.TRANSPARENT
        val isDark = isAppLightTheme?.let {
            if (ColorUtil.colorDistance(color, Color.TRANSPARENT) <= 0.25) {
                !it
            } else {
                ColorUtil.isDark(color)
            }
        } ?: run {
            if (ColorUtil.colorDistance(color, Color.TRANSPARENT) <= 0.1) {
                isNightMode(window.context)
            } else {
                ColorUtil.isDark(color)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
        }
        if (!StatusBarIconsDarkMode.setDarkIconMode(window, !isDark, BarType.STATUSBAR)) {
            color = if (!isDark) {
                ColorUtil.blend(color, Color.BLACK, alpha.toDouble())
            } else {
                ColorUtil.blend(color, Color.WHITE, alpha.toDouble())
            }
        }
        if (Build.VERSION.SDK_INT >= 21) {
            window.statusBarColor = color
        }
        color
    } catch (e: Throwable) {
        LogCat.logException(e)
        null
    }
}
```

- [ ] **Step 5: Run the focused test and verify GREEN**

Run:

```powershell
.\gradlew.bat :common:testDebugUnitTest --tests dev.skomlach.common.statusbar.TaskDescriptionCompatPolicyTest '-Pkotlin.incremental=false' --no-daemon --console=plain
```

Expected: `BUILD SUCCESSFUL`, with both focused tests passing and no test failures.

- [ ] **Step 6: Verify compilation, API guards, and the full common unit suite**

Run each command and require exit code 0:

```powershell
.\gradlew.bat :common:compileDebugKotlin '-Pkotlin.incremental=false' --no-daemon --console=plain
.\gradlew.bat :common:lintDebug '-Pkotlin.incremental=false' --no-daemon --console=plain
.\gradlew.bat :common:testDebugUnitTest '-Pkotlin.incremental=false' --no-daemon --console=plain
```

The compile and lint runs must report no invalid API access for `TaskDescription.Builder` or the API 28 constructor.

- [ ] **Step 7: Review the final diff and security properties**

Run:

```powershell
git -c safe.directory=C:/Users/skoml/StudioProjects_5/AdvancedBiometricPromptCompat diff --check -- common/src/main/java/dev/skomlach/common/statusbar/StatusBarTools.kt common/src/main/java/dev/skomlach/common/statusbar/TaskDescriptionCompat.kt common/src/test/java/dev/skomlach/common/statusbar/TaskDescriptionCompatPolicyTest.kt
git -c safe.directory=C:/Users/skoml/StudioProjects_5/AdvancedBiometricPromptCompat diff -- common/src/main/java/dev/skomlach/common/statusbar/StatusBarTools.kt common/src/main/java/dev/skomlach/common/statusbar/TaskDescriptionCompat.kt common/src/test/java/dev/skomlach/common/statusbar/TaskDescriptionCompatPolicyTest.kt
```

Confirm all of the following from the diff:

- no public signature changed;
- no hidden API or reflection was introduced;
- API 33 and API 28 calls are guarded;
- translucent primary/background colors are omitted;
- only an actual `Activity` callback receives a task description;
- no secrets, identifiers, user data, or sensitive values are logged or stored;
- no files outside the three implementation/test targets were changed by implementation;
- no commit is created.
