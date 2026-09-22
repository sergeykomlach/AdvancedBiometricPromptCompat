# SoftwareBiometric hardening — roadmap and implementation ledger

> **For agentic workers:** Execute in small reviewed batches. The repository's AGENTS.md overrides generic workflow defaults: no commits, pushes, clean, full suites or minified builds without explicit approval. Preserve unrelated files.

**Goal:** Make software biometric security claims, session ownership, licensing and feedback match the behavior actually delivered.

**Architecture:** Keep the existing provider/runtime/manager/prompt contracts and priority system. Security decisions must be explicit and fail closed; UI rendering must not decide authentication outcomes. Keep Sherpa independent of VoiceAuth.

**Tech Stack:** Android/Kotlin, Gradle, JUnit 4, TensorFlow Lite/LiteRT, optional typed compileOnly vendor runtimes.

**Spec:** The audit and user's implementation approval in this conversation, captured below.

## Global constraints / approved specification

- Never equate a software match, vendor priority or encrypted storage with Android hardware-backed biometric authentication.
- Native authentication remains independent of software-provider failures.
- Sherpa has priority over VoiceAuth; initial unavailability must allow VoiceAuth selection.
- A prompt and authentication manager must use the same selected runtime and enrollment namespace.
- No SDK reflection, vendor-binary redistribution, model replacement or license acceptance on the user's behalf.
- No claim of guaranteed app-view placement above the native BiometricPrompt window.
- Keep parallel native/software support; interactive staging is a configurable policy, not a mandatory replacement.
- Existing caller-supplied PCM/behavior inputs remain explicit compatibility inputs, not evidence of trusted sensor capture.
- Run only focused Debug verification, one Gradle process at a time. Device, release/R8, full-suite and public-artifact proof are separate gates.

## Review focus

1. Matching face frames must not finish before anti-spoofing has collected enough valid samples, including STRICT and frame-stride configurations.
2. Camera loss, invalid inference and session restart must clear earlier liveness evidence.
3. A callback/retry from a canceled or superseded session must not mutate lockout or restart capture.
4. Missing Sherpa runtime/model, changed provider availability and permanent lockout must not mix prompt and manager identities.
5. Instructions must remain visible until the next stage; a terminal status must not become an ordinary timed hint.

## Ordered backlog

| ID | Priority | Deliverable / acceptance criteria | Status |
|---|---|---|---|
| SEC-01 | P1 | TFFace distinguishes pending PAD from pass; enrollment and authentication require a completed PAD decision when enabled; unavailable required PAD fails closed; default requires PAD; explicit opt-out remains documented. | Implemented; focused unit tests/compile passed; device PAD evaluation pending |
| SEC-02 | P1 | Error/failed/retry paths share session-ownership checks before side effects; existing success/crypto/storage protections retained. | Implemented; gate/session tests and compile passed; SDK race/device tests pending |
| SEC-03 | P1 | Face assurance follows actual challenge configuration; Voice/Sherpa describe passive speaker matching; supplied PCM is not labeled trusted capture; Behavior compatibility is explicit. No invented liveness guarantees. | Implemented; policy tests and all affected module compiles passed |
| CORE-01 | P1 | One runtime selection across prompt and manager; initial Sherpa failure selects VoiceAuth; no fallback through permanent lockout without explicit policy. | Implemented per registration generation; selection tests passed; actual absent-AAR/ABI tests pending |
| LIC-01 | P1 | Correct Soter BSD-3-Clause notice, retain third-party texts in source and published AAR/source artifacts; exact origins/checksums recorded. | Planned |
| LIC-02 | P1 | Establish redistribution rights for Huawei LocalAuthentication and MobileFaceNet/deepfake assets; get the applicable ZKTeco SDK agreement. If unavailable, choose a compatible removal/application-supplied migration before publishing. | External evidence required |
| UI-01 | P2 | Preserve typed status, terminal flag, source and session for every legacy module; automatically retain non-terminal instructions while native UI is active, bound terminal messages by timeout, reject post-completion/cancel events. | Implemented as a shared policy, not a VoiceAuth/Sherpa exception; focused state/session/routing and ABI tests passed; device races pending |
| UI-02 | P2 | User-approved replacement: WindowForeground message card above icons, measured fallback bounds / reconstructed native bounds, timeout and fade/fold options. | Implemented; focused tests and Debug compiles passed; native geometry, animations and TalkBack require device QA |
| UI-03 | P2 | Optional staged preparation for mandatory instructions when native UI leaves no usable app area. | Deferred by approved foreground-first design; parallel capture preserved |
| QA-01 | P1 before release | Missing-runtime tests, stale-callback tests, model/ABI matrix, spoof/replay evaluation, physical OEM native+legacy UI and TalkBack checks. | Runtime/device evidence required |

## Iteration 1 — TFFace security gate (smallest module: :biometric-custom-face-tf, Debug)

Direct dependants: sample :app. No public ABI, resources, dependency or packaging change in this iteration.

**Files:**
- Modify `biometric-custom-face-tf/src/main/java/dev/skomlach/biometric/compat/engine/internal/face/tensorflow/FaceAntiSpoofingPolicy.kt`.
- Modify adjacent `TensorFlowFaceUnlockManager.kt`, `TensorFlowFaceConfig.kt`, `TensorFlowFacePreflight.kt`.
- Modify `biometric-custom-face-tf/src/test/java/dev/skomlach/biometric/compat/engine/internal/face/tensorflow/FaceAntiSpoofingPolicyTest.kt`.
- Create adjacent `FaceAntiSpoofingWindowTest.kt` for the production state machine.

**Interface:** Internal `FaceAntiSpoofingWindow(windowSize, minimumFrames, threshold)` exposes `add(score: Float?): FaceAntiSpoofingDecision` and `reset()`. `PENDING` never authorizes enrollment/authentication. Each successful final comparison takes a current PAD measurement; samples cannot be borrowed from a different candidate or session. Existing shared assurance enum remains ABI-unchanged.

- [x] Write regression: default config rejects unavailable PAD at preflight (observed assertion failure).
- [x] Add window cases: insufficient samples remain pending, completed live window passes, spoof window rejects, negative/non-finite/sentinel scores invalidate earlier evidence, reset clears evidence. Do not assume this model's weighted leaf score is a normalized probability capped at one.
- [x] Implement window and integrate before enrollment registration / authentication success. Pending continues capture without counting failed attempts; unavailable required PAD reports hardware unavailable, not a spoof accusation.
- [x] Keep head-turn challenge opt-in because its reliable native+legacy instruction UI belongs to UI-01/03. Derive Face assurance from actual challenge configuration.
- [x] Run focused Face PAD, preflight and authentication-attempt tests and scoped diff check; exact commands/results are in `docs/reviews/2026-09-21-software-biometric-hardening-iteration-1.md`.

## Following iteration contracts

### SEC-02: callback ownership

Touch `biometric/.../engine/internal/SoftwareBiometricModule.kt`, its callback gate and tests. Check `sessionGuard.isActive(sessionToken)` and the original cancellation signal at retry entry; call `callbackGate.canDispatch()` before error/failed side effects. Test late errors, delayed retries, cancellation and replacement-session ownership with real session guards. Do not reset global enrollment/lockout state as a fallback.

### SEC-03 / CORE-01: honest assurance and runtime identity

Touch provider managers, `SoftwareBiometricPromptRegistry`, `LegacyBiometric`, session orchestration and their tests. Session selection produces a runtime identity consumed by both prompt and manager; availability changes cannot trigger independent reselection. Test absent Sherpa, unavailable model, late availability change, permanent lockout and independently enrolled VoiceAuth. A lockout is not an initialization failure and must not silently downgrade authentication.

### LIC-01 / LIC-02: attribution and release eligibility

Touch notices, `scripts/publish-mavencentral.gradle` and a machine-readable artifact/provenance inventory. Test archive contents of generated artifacts when packaging is changed. Preserve binary/source license texts and copyright notices. A missing agreement stays unresolved; do not describe compileOnly as legal authorization. Keep public publication blocked in the ledger until Huawei/model/ZK evidence is supplied or an approved migration removes those artifacts.

### UI-01 / UI-02 / UI-03: feedback

The user approved extending the existing WindowForeground rather than a separate public renderer/host. Preserve structured status and session/source ownership. Position the message/icons group above measured compat-dialog bounds or a resource-derived native-dialog estimate using the existing NativeDialogStyle mapping. Unknown profiles use a top-of-host fallback; known panels without space must not be overlapped. For our dialog, reuse its inline status when no space remains. Builder options control timeout and NONE/FADE/FOLD animation. Persistent instructions survive transient hints from other sources. Native placement is not an observed SystemUI rectangle and needs device validation, especially sensor offsets, multiwindow and full-height two-pane layouts. No overlay permissions or mandatory serialization.

## Execution ledger

- 2026-09-21: Current tree reviewed; no tracked changes at start. Existing untracked audit/notices/binaries are user files and remain untouched unless explicitly listed by an implementation batch.
- Plan implements the user's explicit request to proceed iteratively; no additional planning-only pause. TDD/verification skills are used within the repository's narrower verification rules; no full-suite runs or commits.
- Public release is not authorized by this task. License investigation is not a legal clearance.
- Completed SEC-01/02/03 and CORE-01 in sequential batches. 55 focused tests passed; VoiceAuth/Sherpa/Behavior Debug compiles passed. No device, full-suite, release/R8 or publishing run.
- CORE-01 ruling: selection is pinned for the existing registry/Legacy registration generation (stronger lifetime than one session). Availability changes can suppress its prompt but cannot change the engine; unload/reset is required to reconsider a newly available engine. No automatic in-flight provider replacement.
- Foreground feedback iteration: UI-01 and the user-revised UI-02 implemented. 65 focused tests passed, including old status JVM signatures, typed source routing, queued-event ownership and placement. VoiceAuth/Sherpa compile passed. See `docs/reviews/2026-09-21-foreground-feedback.md` for exact commands, changed files and runtime limits. UI-03 remains deferred, not implemented.
- General legacy feedback follow-up: system-owned stages retain non-terminal help/status from all legacy sources without provider opt-in; completed sources reject late feedback. 52 focused tests passed (including 10 new cases); Debug library compilation passed. No provider implementation, dependency or authentication acceptance changes. Device timing/visibility remains unverified.
- Next: device QA of foreground/native/fallback placement and accessibility; LIC-01 attribution can proceed independently. LIC-02 still requires exact grants/model provenance. No release clearance implied.
- Production-review fix iteration (2026-09-21): Behavior explicitly deferred as MVP; licenses excluded. Fixed the other nine findings across ZK callback binding, TFFace worker/session/capture continuity, VoiceAuth/Sherpa background inference and cancellation-safe commits, model identity/dimensions, native terminal error routing, and retained foreground text. Independent static re-review completed; final focused Debug gate passed 76 tests. Voice templates without model identity require re-enrollment; custom engines need VoiceTemplateIdentityProvider. Existing VoiceTemplateTrainingTest outlier-policy failure remains separate and was not counted as passing. Exact commands, changed files and runtime limits: `docs/reviews/2026-09-21-production-review-fixes.md`. No release/R8/APK/device QA or publication performed.
- Code-only follow-up (2026-09-21): resolved the outstanding voice outlier-policy test with conservative majority consensus and aligned GMM training in VoiceAuth/Sherpa. Added explicit re-enrollment status/messages in all module locales, complete membership snapshots, consensus-versioned identities, and worker-side cached Sherpa hashing. Final focused Debug gate passed 67 tests; resource and scoped diff checks passed. Older profiles require re-enrollment and are not silently deleted or converted. User explicitly excluded APK/device QA; native cold-start latency, real storage migration, packaging and UI visibility remain runtime-unverified. Details: `docs/reviews/2026-09-21-voice-consensus-migration.md`.
- Sherpa cold-start follow-up (2026-09-21, approved next-step item 1): native runtime/extractor creation now occurs only through explicit worker preparation; synchronous getters read cached state. Initial NEW/PREPARING retains priority, confirmed FAILED can select and prepare a registered standby, and READY pins the complete runtime. Lockout/storage failures cannot authorize fallback, including early-known and intermediate-standby failures. This refines CORE-01's prior pin-at-registration rule for providers opting into deferred initialization; non-deferred providers retain the previous behavior. 55 focused tests passed across core/Sherpa gates; core, Sherpa and fallback VoiceAuth Debug compilation passed. No APK/device/R8 or full-suite run. Full manager/storage integration and native-call timeout/device proof remain separate work. Details: `docs/reviews/2026-09-21-sherpa-cold-start.md`.
