# Foreground biometric feedback — 2026-09-21

## Scope and behavior

User-approved UI-01 plus revised UI-02. Primary module: `biometric` / Debug. Direct provider changes: `biometric-custom-voice`, `biometric-sherpa-onnx`. Sample `app` consumes these modules but was not assembled. Existing security-iteration edits and unrelated user files remain untouched.

- Replace Api28 software-feedback Toasts with a card in the existing app-window foreground, above biometric icons. No new windows or permissions, no changes to authentication acceptance/crypto/provider priority.
- Three-line card with dialog-like palette/corners, 4-second default timeout, configurable NONE/FADE/FOLD. Reserve card height so icons do not jump as messages appear/disappear. Duplicate current messages do not restart their timer/animation.
- Read actual compat-card screen coordinates and follow its layout changes. If there is no room above it, display feedback in the owned dialog's status field.
- For native mode, reuse `SystemBiometricDialogResources` / `NativeDialogStyleApplier` to measure an unattached local view with native prompt texts. Hide the local camera preview from measurement. Compute a panel rectangle from the full display's available area, gravity and mapped insets, then convert screen coordinates into the foreground host. Results are cached by profile/configuration/content. No foreign SystemUI code is instantiated.
- A reconstructed native panel gets an extra gap. Unknown/missing profiles, forced credentials and profiles that explicitly preserve app-only width use the inset-aware upper host area. Known panels without enough free space suppress the foreground group; we cannot put an inline status inside native SystemUI.
- Typed status preserves primary/secondary text, terminal state and persistence. Internal optional listener extensions retain source across Core/Legacy; original listener interfaces remain unchanged for old binaries. Old `SoftwarePromptStatus` constructor, default constructor, copy and copy-default entry points are retained.
- While system UI owns the active prompt, the shared feedback session retains every legacy non-terminal help/status until that source changes state or finishes. This includes plain `onHelp` from hardware/OEM modules and structured software statuses; no provider-name checks or persistence opt-in are required. Source identity and terminal/session metadata use the same route for every module. Existing explicit persistence flags still control compat-only rendering outside native mode.
- Terminal errors temporarily outrank other sources and remain timeout-bounded. Afterwards the latest still-live instruction is restored. Success, cancellation and terminal failure (including missing/blank descriptions) retire the source; its late status cannot resurrect an instruction. Native-to-compat mode changes do not silently expire an already displayed instruction, but subsequent messages follow the new mode.
- Request-owned session and stage generation reject queued feedback/clears after cancellation or replacement. Dialog callbacks also check their view generation; synchronous main-thread onReady still binds extras before authentication starts. Cleanup discards text, timers, animations, observers and owned-view references. UI cleanup cannot prevent engine cancellation.
- Accessibility timeout is extended with the platform recommendation when available. Animation disablement is honored. The card has a polite live region and theme-aware contrast; native-window accessibility remains a device-level limitation.

## Configuration

```kotlin
builder.setBiometricFeedbackOptions(
    BiometricFeedbackOptions(
        timeoutMillis = 4_000L,
        animation = BiometricFeedbackOptions.Animation.FOLD
    )
)
```

Default is enabled with FADE. Timeout range: 1,000–60,000 ms; persistent instructions remain until their source advances/completes or the session ends. `enabled = false` disables the card, not icons or biometric engines; compat-dialog status rendering remains available.

## Changed files in this iteration

Paths below are relative to the repository. Previously modified security-manager files are not part of this iteration.

Under `biometric/src/main/java/dev/skomlach/biometric/compat/`:

- `BiometricPromptCompat.kt`
- `custom/SoftwareBiometricPromptHost.kt`, new `custom/BiometricFeedbackOptions.kt`
- `engine/LegacyBiometric.kt`, `engine/LegacyBiometricAuthenticationListener.kt`
- `engine/core/Core.kt`, `engine/core/interfaces/AuthenticationListener.kt`, new `engine/core/ModuleFeedbackListener.kt`
- `impl/BiometricPromptApi28Impl.kt`, `impl/BiometricPromptGenericImpl.kt`
- `impl/dialogs/BiometricPromptCompatDialogImpl.kt`, new `impl/dialogs/SystemPromptGeometry.kt`
- `utils/activityView/WindowForegroundBlurring.kt`
- New `utils/activityView/ForegroundFeedbackState.kt`, `ForegroundFeedbackSession.kt`, `ForegroundFeedbackView.kt`, `ForegroundPlacement.kt`

Provider files:

- `biometric-custom-voice/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoicePromptDelegate.kt`
- `biometric-sherpa-onnx/src/main/java/dev/skomlach/biometric/compat/engine/internal/sherpaonnx/SherpaOnnxPromptDelegate.kt`

New tests under `biometric/src/test/java/dev/skomlach/biometric/compat/`:

- `SoftwarePromptStatusCompatibilityTest.kt`
- `engine/core/ModuleFeedbackListenerTest.kt`
- `utils/activityView/ForegroundFeedbackStateTest.kt`, `ForegroundFeedbackSessionTest.kt`, `ForegroundPlacementTest.kt`

Docs: this report and `docs/superpowers/plans/2026-09-21-biometric-hardening-roadmap.md`.

## Verification ledger

1. Initial missing-API red check (test compilation failed because the new state/placement/persistence APIs did not yet exist):

```powershell
.\gradlew.bat :biometric:testDebugUnitTest --tests '*ForegroundFeedbackStateTest' --tests '*ForegroundPlacementTest' --console=plain
```

2. First implementation gate passed:

```powershell
.\gradlew.bat :biometric:testDebugUnitTest --tests '*ForegroundFeedbackStateTest' --tests '*ForegroundPlacementTest' --tests '*SoftwarePromptHelpRoutingTest' --tests '*NativeDialogStyleTest' --tests '*NativeDialogLayoutPolicyTest' :biometric-custom-voice:compileDebugKotlin :biometric-sherpa-onnx:compileDebugKotlin --console=plain
```

3. Stage-race regression gate: 3 tests, 2 expected assertion failures (`stoppedStageRejectsPreviouslyQueuedStatus`, `clearedStageCannotClearNextStagesSameSource`). Fixed with stage-owned queued operations:

```powershell
.\gradlew.bat :biometric:testDebugUnitTest --tests '*ForegroundFeedbackSessionTest' --console=plain
```

4. Expanded gate passed, then rerun after lifecycle/geometry-input fixes: 65 tests, zero failures/errors. Includes placement, session/state, source bridge, original JVM ABI calls, native mapping and Legacy cancellation; VoiceAuth/Sherpa Debug compilation passed:

```powershell
.\gradlew.bat :biometric:testDebugUnitTest --tests '*ForegroundFeedback*Test' --tests '*ForegroundPlacementTest' --tests '*ModuleFeedbackListenerTest' --tests '*SoftwarePrompt*Test' --tests '*NativeDialog*Test' --tests '*LegacyBiometricCancellationTest' :biometric-custom-voice:compileDebugKotlin :biometric-sherpa-onnx:compileDebugKotlin --console=plain
```

5. Final Android-measurement-only adjustment hides the unattached camera SurfaceView. The isolated module compile passed (`BUILD SUCCESSFUL`, 9s). Pure state/routing tests were not repeated because their inputs did not change:

```powershell
.\gradlew.bat :biometric:compileDebugKotlin --console=plain
```

6. Scoped whitespace diff check (exit 0; line-ending conversion warnings only):

```powershell
git -c safe.directory=C:/Users/skoml/StudioProjects_5/AdvancedBiometricPromptCompat diff --check -- biometric biometric-custom-voice biometric-sherpa-onnx
```

Existing Gradle/Android deprecated-API warnings remain. No clean, full suite, APK, release/R8, device install, instrumentation, commit or push. App Debug is minified; it was not used to bypass the repository's no-minified-build rule.

### Follow-up: general legacy retention policy

Affected module: `biometric` / Debug only. No provider code, dependencies, resources or authentication acceptance rules changed in this follow-up. VoiceAuth/Sherpa's existing flags remain backward-compatible for non-native UI, but do not determine native-mode retention.

Files changed in this follow-up (relative to `biometric/src/`):

- `main/java/dev/skomlach/biometric/compat/utils/activityView/ForegroundFeedbackSession.kt`: shared system-prompt retention policy, stage-owned source completion.
- `main/java/dev/skomlach/biometric/compat/utils/activityView/ForegroundFeedbackState.kt`: source retirement rejects post-completion messages.
- `main/java/dev/skomlach/biometric/compat/impl/BiometricPromptApi28Impl.kt`: connect actual prompt-start state and route every legacy completion through source retirement.
- `main/java/dev/skomlach/biometric/compat/impl/BiometricPromptGenericImpl.kt`: connect system-owned stages and retire completed sources, including errors without descriptions.
- `main/java/dev/skomlach/biometric/compat/custom/SoftwareBiometricPromptHost.kt`: document automatic native-mode retention versus explicit compat-mode persistence.
- `test/java/dev/skomlach/biometric/compat/utils/activityView/ForegroundFeedbackSessionTest.kt`: ten additional regression tests cover all modalities, plain legacy help, terminal timeout, mode changes, source retirement and queued events.

Also updated this report and `docs/superpowers/plans/2026-09-21-biometric-hardening-roadmap.md`.

TDD red: the focused session command failed at test compilation because `setSystemPromptActive` / `finishSource` were not implemented yet:

```powershell
.\gradlew.bat :biometric:testDebugUnitTest --tests '*ForegroundFeedbackSessionTest' --console=plain
```

Green: **52 tests passed, zero failures/errors**, including all 13 session tests; the command also compiled the Debug library. `BUILD SUCCESSFUL`, 16s:

```powershell
.\gradlew.bat :biometric:testDebugUnitTest --tests '*ForegroundFeedback*Test' --tests '*ModuleFeedbackListenerTest' --tests '*SoftwarePrompt*Test' --tests '*LegacyBiometricCancellationTest' --tests '*Api28StartAuthPlanTest' --tests '*AuthSessionStateTest' --console=plain
```

After removing the compiler-reported redundant safe call in Generic routing, the final compile passed (`BUILD SUCCESSFUL`, 5s). No behavior change; the passing tests were not repeated:

```powershell
.\gradlew.bat :biometric:compileDebugKotlin --console=plain
```

Tracked-file whitespace check passed:

```powershell
git -c safe.directory=C:/Users/skoml/StudioProjects_5/AdvancedBiometricPromptCompat diff --check -- biometric
```

The new session/state/test files and both reports were additionally checked using `git diff --no-index --check` against `NUL`; no whitespace diagnostics. A first wrapper incorrectly treated no-index's normal “files differ” exit code 1 as an error; the corrected wrapper accepts 0/1 only without error diagnostics.

Provider builds, geometry tests, full suite, APK, release/R8 and device QA were intentionally not rerun: those implementation inputs were not changed. Real native-window timing/visibility remains runtime-unverified.

## Remaining runtime / release risks

- Native rectangle is reconstructed, not observed. Dynamic native states, UDF sensor offsets, OEM layout changes and mismatched multiwindow/display configurations can shift the panel. Unknown profiles are explicitly best effort.
- In a full-height native/two-pane layout there may be no app-owned area above the panel. This iteration does not promise visible instructions there and does not serialize native/software authentication automatically.
- Long messages are capped at three visible lines with end ellipsis. Full text is retained in the TextView, but TalkBack reading and focus while native SystemUI is open are not guaranteed. Mandatory long instructions may need the deferred staged flow.
- Physical QA still needed: native+legacy, own bottom/center dialog, rotation/Fold/split-screen, large fonts, disabled icons, cancel/reopen, rapid mixed-source hints, timeout/fold/fade, TalkBack and system animation disablement.
- Android View measurement, actual animation and visual styling are compile-checked only; pure-JVM tests do not prove screen placement.
- Licensing/release blockers in LIC-01/02 are unchanged by this UI iteration.
