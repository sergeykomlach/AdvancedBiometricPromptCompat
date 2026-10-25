# Sample signature and mandatory system biometric implementation plan

> **For agentic workers:** Use superpowers:executing-plans. Follow checkbox tasks; no commit or push.

**Goal:** Demonstrate caller-owned signature capture as advisory behavior followed by mandatory native system biometric authentication.

**Architecture:** A sample-local coordinator owns two sequential cancellable operations. BehaviorBiometricManager evaluates fresh compatibility input without authorizing the demo action; a BIOMETRIC_API/HARDWARE request with SYSTEM_BIOMETRIC floor and disabled credentials is the sole authorization source. An app-owned drawing View supplies fresh events and clears raw data after submission/background/destruction. Existing library APIs remain unchanged.

**Tech Stack:** Kotlin, Android Views/Fragment navigation, BiometricPromptCompat, BehaviorBiometricManager, JUnit/Robolectric.

**Spec:** User-selected signature + mandatory bio scenario; `docs/security/behavior-production-security.md` applies. Existing Signature enrollment is a prerequisite for MATCHED, not for mandatory bio execution.

## Constraints and review focus

- Only `:app` source/build/test configuration plus documentation; preserve current dirty work/index.
- Behavior MATCHED, NOT_MATCHED, UNAVAILABLE and provider errors all proceed to system bio; none authorize independently.
- System failure/cancel/unavailable, empty or behavior-only results cannot authorize.
- No software or device-credential fallback; no adoption of global sample preferences.
- Guard duplicate/late callbacks and repeat submission; cancel on stop/destroy, clear view/payload, avoid saved capture/auth state.
- Own signature renderer, not an injected library pad; MotionEvent history, finite bounds, cancellation/obscured input and 30s deadline remain explicit compatibility checks.
- No Gradle during exploration; one process; targeted tests then `:app:assembleDebug`; no clean/release/device QA without need.

## Tasks

- [x] Add failing coordinator, policy, capture and Fragment lifecycle regressions; run targeted RED.
- [x] Implement sample flow/behavior and system clients, own SignatureCaptureView, Fragment/layout/entry navigation and English/Ukrainian resources.
- [x] Run target regressions, source review and fix confirmed failures; build debug APK.
- [x] Verify scoped diff and unchanged index, save evidence and report runtime limits.
