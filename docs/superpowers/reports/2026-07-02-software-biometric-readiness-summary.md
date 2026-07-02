# Software Biometric Readiness Summary

## Shared Platform
- Architecture: `BiometricPromptCompatDialogImpl` remains the shared software-prompt bridge: it resolves the primary biometric type, creates a `SoftwareBiometricPromptDelegate` through `SoftwareBiometricPromptRegistry`, injects prompt-owned extras back into the builder, and only starts auth when the delegate reports readiness.
- User-Facing UX / Localization: Lockout and retry copy is localized through framework strings with `LocalizationHelper` fallback, and the negative action uses the platform `cancel` string; no scoped hardcoded end-user copy was found in this file.
- Failure Semantics: Prompt-side failures flow through `onPreAuthFailure`, help text updates the shared status area, and dialog-level retry/lockout messaging stays consistent across software modules.
- Lifecycle / Cancellation: Show, dismiss, cancel, focus-loss, and in-screen visibility all detach listeners, dispose the active software delegate, and stop auth when the dialog is no longer valid.
- Lockout / Retry: The shared dialog renders localized `too_many_attempts` and `not_recognized` states and restores the normal prompt state through the existing delayed handler flow.
- Test Coverage: Shared contract coverage exists in `SoftwareBiometricPromptRegistryTest.kt`, `SoftwareBiometricProviderContractTest.kt`, and `SoftwareBiometricFailureReasonTest.kt`; this dialog file itself is compile-covered rather than directly unit-tested.
- Compile / Verification: The requested compile sweep passed, including `:biometric:compileDebugKotlin`, `:biometric:compileDebugUnitTestKotlin`, and `:app:compileDebugKotlin`; `git diff --check` returned clean.
- Residual Risks: Dialog lifecycle behavior is still validated mainly through adjacent contract tests plus compile proof, not direct dialog-runtime unit tests in this task.
- Status: Ready.

## Voice
- Architecture: `VoiceAutoCaptureSession` keeps voice capture prompt-side, accumulates the required samples, builds extras only when the sample target is met, and hands the prepared bundle back through `onReady`.
- User-Facing UX / Localization: The session now consumes injected `Messages` instead of embedding UI literals; the scoped hardcoded-string scan did not surface end-user copy in `VoiceAutoCaptureSession.kt`.
- Failure Semantics: Recoverable capture problems re-announce the current recording step, while fatal failures return a typed `AuthenticationResult` with the original failure reason and message.
- Lifecycle / Cancellation: Every callback path is guarded by `isPromptActive()`, and `dispose()` clears captured samples, prepared extras, and transient speech-detection state.
- Lockout / Retry: This session deliberately separates prompt retry flow from manager lockout flow; missing or partial samples do not get translated here into lockout states.
- Test Coverage: Voice coverage includes `VoiceAutoCaptureSessionTest.kt`, `VoiceAutoCaptureMessagesTest.kt`, `VoicePromptDataTest.kt`, `VoiceProviderTest.kt`, plus broader engine tests such as `VoiceAudioPreprocessorTest.kt` and `VoiceStreamingDetectorTest.kt`.
- Compile / Verification: The requested compile sweep passed for `:biometric-custom-voice:compileDebugKotlin` and `:biometric-custom-voice:compileDebugUnitTestKotlin`; the scoped string scan only reported internal constants in the voice module.
- Residual Risks: This task did not run direct `testDebugUnitTest`; in this environment that remains a known local `Could not find or load main class VS` risk, so compile-unit-test Kotlin remains the dependable proof gate.
- Status: Ready.

## Behavior
- Architecture: `BehaviorCaptureController` owns the explicit prompt-side collection flow for typing, signature, and combined modes, builds the shared extras payload through `buildBehaviorExtras(...)`, and only signals readiness after local validation passes.
- User-Facing UX / Localization: Overlay titles, launcher copy, hints, action labels, and validation errors are resource-backed and theme-backed inside the behavior module.
- Failure Semantics: Invalid phrase/signature states are blocked locally with targeted error text before auth starts, and successful preparation writes the shared status text to the checking state before calling `onReady`.
- Lifecycle / Cancellation: `dispose()` removes listeners, clears screen-protection hooks, detaches owned views, and tears down the temporary overlay/button without touching unrelated host UI.
- Lockout / Retry: The controller itself does not own lockout accounting; it enforces sample completeness locally and leaves actual attempt counting to manager-side code.
- Test Coverage: Behavior coverage exists in `BehaviorPromptDataTest.kt`, `BehaviorPromptDelegateTest.kt`, `BehaviorProviderTest.kt`, and `BehaviorScorerTest.kt`; the controller UI flow itself is compile-covered rather than directly unit-tested.
- Compile / Verification: The requested compile sweep passed for `:biometric-custom-behavior:compileDebugKotlin` and `:biometric-custom-behavior:compileDebugUnitTestKotlin`; the scoped string scan plus manual review no longer showed a remaining behavior-module user-facing literal in code.
- Residual Risks: Direct controller-runtime tests are still absent from the scoped coverage, so prompt-UI behavior remains compile-backed plus helper-test-backed rather than interaction-tested.
- Status: Ready.

## FaceTF
- Architecture: `TensorFlowFaceUnlockManager` remains a manager-owned flow with no prompt factory, combining preflight checks, backend selection, anti-spoofing, enrollment/auth session ownership, and lockout policy in one place.
- User-Facing UX / Localization: End-user help and error copy in the target file is resource-backed through `LocalizationHelper`; the string scan hits in this module were internal keys, log tags, model/config strings, and debug helpers rather than obvious prompt text.
- Failure Semantics: Early preparation/auth failures are explicit (`HARDWARE_MISSING`, camera blocked/in use, no enrolled biometric, model unavailable), and mismatch handling stays separate from true lockout escalation.
- Lifecycle / Cancellation: The current tree binds callbacks before preflight exits, cancels any prior active manager, honors `CancellationSignal`, re-checks `canStartAuthenticationSession()` after background-thread startup, and clears session state in `stopAuthentication()`.
- Lockout / Retry: Temporary/permanent lockout is policy-driven, mismatch accounting is threshold-aware, and spoof/no-face/invalid-face paths return dedicated localized outcomes instead of collapsing into one generic failure.
- Test Coverage: Coverage includes `TensorFlowFacePreflightTest.kt`, `TensorFlowFaceStateSupportTest.kt`, and `TensorFlowProviderTest.kt`; the preflight and session-start guards added earlier are covered as pure helper behavior.
- Compile / Verification: The requested compile sweep passed for `:biometric-custom-face-tf:compileDebugKotlin` and `:biometric-custom-face-tf:compileDebugUnitTestKotlin`; no whitespace regressions were reported.
- Residual Risks: This task did not exercise live camera/provider runtime paths, so OEM camera-state and permission interactions remain compile-backed rather than device-proven here; direct `testDebugUnitTest` execution is still subject to the known local `VS` runner issue.
- Status: Ready with device-runtime caveat.

## ZKFinger
- Architecture: `ZkFingerUnlockManager` also exposes no prompt factory and keeps USB permission handling, active-session arbitration, enrollment/auth processing, and template persistence inside the manager, with the newer pure helper seams covered by unit tests.
- User-Facing UX / Localization: Sensor/help/error messages in the target file are localized through `LocalizationHelper`; the requested string scan only surfaced internal IDs, storage keys, thread names, and developer-facing `require(...)` messages.
- Failure Semantics: The manager distinguishes no hardware, no permission, detached sensor, no enrolled biometric, duplicate enrollment, mismatched enrollment finger, template-processing failure, and ordinary auth failure.
- Lifecycle / Cancellation: Active sessions cancel prior managers, `CancellationSignal` posts a clean cancel callback, USB receivers are unregistered on shutdown, and `stopAuthentication()` closes/destroys the sensor and frees `ZKFingerService`.
- Lockout / Retry: Failed matches increment the lockout policy, success resets permanent lockout state, and enrollment/auth help text keeps retry progress separate from hard failures.
- Test Coverage: Coverage includes `ZkFingerSessionSupportTest.kt`, `ZkFingerHardwareDetectionTest.kt`, and `ZkFingerProviderTest.kt`, which cover the extracted session helpers, hardware-detection helper, and provider contract.
- Compile / Verification: The requested compile sweep passed for `:biometric-zkfinger:compileDebugKotlin` and `:biometric-zkfinger:compileDebugUnitTestKotlin`; `git diff --check` remained clean.
- Residual Risks: Live USB permission, hot-plug, and vendor-device runtime behavior were not exercised in this task, so readiness is compile-backed plus helper-test-backed rather than hardware-session-proven.
- Status: Ready with hardware-runtime caveat.
