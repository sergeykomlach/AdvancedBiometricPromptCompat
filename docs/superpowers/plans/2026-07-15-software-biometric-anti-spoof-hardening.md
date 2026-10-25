# Software Biometric Anti-Spoof Hardening Implementation Plan

> **For agentic workers:** Execute task-by-task with test-first checkpoints. Do not commit automatically.

**Goal:** Harden `SoftwareBiometric` face and voice flows against presentation attacks and make unavailable anti-spoof controls fail closed instead of being interpreted as a successful biometric check.

**Architecture:** Keep `:biometric` as the shared contract owner. Each software modality exposes a typed assurance outcome internally; face owns PAD/capture policy, and voice owns PCM/session ownership and voice anti-spoof policy. The first implementation slice is deliberately limited to deterministic policy and contract fixes; ML model additions are a separate follow-up after an attack corpus and calibration protocol exist.

**Tech Stack:** Kotlin, Android library modules, JUnit4, Gradle module compile/unit-test gates, existing `AbstractSoftwareBiometricManager` callback contract.

## Global Constraints

- Do not change or stage unrelated existing working-tree changes.
- Do not claim software face/voice is hardware-backed or suitable as the sole high-assurance factor.
- Do not accept a PAD/model failure as a clean biometric pass.
- Do not add a new ML dependency before defining model provenance, licensing, asset integrity, calibration data, and fallback behavior.
- Do not commit changes without explicit user approval.

---

### Task 1: Add shared software-biometric assurance semantics

**Files:**
- Create: `biometric/src/main/java/dev/skomlach/biometric/compat/custom/SoftwareBiometricAssurance.kt`
- Create: `biometric/src/test/java/dev/skomlach/biometric/compat/custom/SoftwareBiometricAssuranceTest.kt`
- Modify: `biometric/src/main/java/dev/skomlach/biometric/compat/custom/AbstractSoftwareBiometricManager.kt`

**Interfaces:**
- Produces `SoftwareBiometricAssurance` and `SoftwareBiometricAssurancePolicy` for modality managers.
- The default policy remains compatible with existing managers: PAD is optional unless a manager explicitly requires it.

- [ ] Write a failing test proving that `UNAVAILABLE` is not accepted as a successful assurance outcome.
- [ ] Run `.:biometric:compileDebugUnitTestKotlin` and confirm the new symbols are missing.
- [ ] Add a small enum/data class with `PASS`, `SPOOF`, `MISMATCH`, `UNAVAILABLE`, `CAPTURE_UNTRUSTED` and a policy helper that returns `false` for every non-`PASS` result.
- [ ] Expose only the typed policy surface from `AbstractSoftwareBiometricManager`; keep callback error mapping in existing managers.
- [ ] Re-run the focused test and shared compile gate.

### Task 2: Make FaceTF PAD fail closed and mandatory in strict mode

**Files:**
- Create: `biometric-custom-face-tf/src/test/java/dev/skomlach/biometric/compat/engine/internal/face/tensorflow/FaceAntiSpoofingPolicyTest.kt`
- Create: `biometric-custom-face-tf/src/main/java/dev/skomlach/biometric/compat/engine/internal/face/tensorflow/FaceAntiSpoofingPolicy.kt`
- Modify: `biometric-custom-face-tf/src/main/java/dev/skomlach/biometric/compat/engine/internal/face/tensorflow/TensorFlowFaceUnlockManager.kt`
- Modify: `biometric-custom-face-tf/src/main/java/dev/skomlach/biometric/compat/engine/internal/face/tensorflow/TensorFlowFaceConfig.kt`

**Interfaces:**
- `FaceAntiSpoofingPolicy.evaluate(score, error, config)` returns a typed decision; exceptions and missing engines become `UNAVAILABLE`.
- Strict mode blocks on unavailable PAD. Existing convenience mode may preserve a documented compatibility fallback.

- [ ] Add failing tests for PAD exception, missing engine, score above threshold, and valid live score.
- [ ] Run the focused FaceTF unit-test compile gate and verify the tests fail for the intended missing policy.
- [ ] Replace `catch { false }` in `isSpoofDetected()` with typed policy evaluation and an explicit callback error for unavailable PAD in strict mode.
- [ ] Add a configuration flag with a secure default that does not silently relax PAD on low-end devices.
- [ ] Run FaceTF compile/unit tests and inspect the diff for unrelated changes.

### Task 3: Close the voice input trust boundary

**Files:**
- Create: `biometric-custom-voice/src/test/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceSampleTrustPolicyTest.kt`
- Create: `biometric-custom-voice/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceSampleTrustPolicy.kt`
- Modify: `biometric-custom-voice/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceSample.kt`
- Modify: `biometric-custom-voice/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceBiometricManager.kt`

**Interfaces:**
- Authentication accepts raw PCM only from the active capture path by default.
- Precomputed embeddings are rejected for authentication unless an explicit internal/trusted flag is present.
- Existing enrollment and test fixtures remain available through an internal-only parser path.

- [ ] Add failing tests proving that an embedding-only authentication Bundle is rejected and PCM authentication remains accepted.
- [ ] Run the focused voice unit-test compile gate and confirm the intended failure.
- [ ] Add a pure trust-policy helper and route `VoiceBiometricManager` through it before matching.
- [ ] Preserve current sample size/rate validation and lockout behavior.
- [ ] Run voice compile/unit tests and verify no embedding data is logged.

### Task 4: Add challenge and attack-oriented verification seams

**Files:**
- Create: `biometric-custom-face-tf/src/main/java/dev/skomlach/biometric/compat/engine/internal/face/tensorflow/FaceChallengePolicy.kt`
- Create: `biometric-custom-voice/src/main/java/dev/skomlach/biometric/compat/engine/internal/voice/VoiceChallengePolicy.kt`
- Create: focused unit tests for both policies.
- Create: `docs/superpowers/reports/2026-07-15-software-biometric-anti-spoof-readiness.md`

**Interfaces:**
- Challenges are session-scoped, randomized, and never treated as proof by themselves.
- The report records covered attack classes, limitations, and verification evidence.

- [ ] Define deterministic challenge validation helpers before UI/camera integration.
- [ ] Add tests for replayed, expired, incomplete, and valid challenges.
- [ ] Integrate only after Tasks 1–3 are green.
- [ ] Document that virtual camera, rooted-device injection, and high-quality real-time face/voice morphing require app/device integrity and/or server-side step-up controls.
- [ ] Run the final module compile gates, `git diff --check`, and targeted unit tests.

## Current Execution Checkpoint

- Task 1 implemented: shared `SoftwareBiometricAssurance` contract.
- Task 2 implemented: FaceTF PAD score/error classification, fail-closed PAD decisions, and secure-by-default authentication preflight when PAD is unavailable.
- Task 3 implemented: voice authentication rejects precomputed embedding-only input and requires PCM capture data.
- Task 3 hardened further: any precomputed embedding is rejected, even when PCM is present.
- Face spoof detections now consume the authentication failed-attempt budget; enrollment spoof detections do not consume that budget.
- Task 4 implemented in the code-only scope: voice phrase challenge validation is wired into `VoiceBiometricManager`, and FaceTF uses a per-auth-session randomized head-pose challenge with localized instructions. Enrollment remains unchanged.
- Compile gates pass for `:biometric`, `:biometric-custom-face-tf`, and `:biometric-custom-voice`.
- Targeted JUnit runtime execution passes when Gradle is launched with the malformed `PATH` entries removed. The original `VS` error is an environment issue caused by a broken PATH entry, not a repository test failure.
- Face challenge integration still requires device UX validation for timing, false rejects, OEM head-angle behavior, and accessibility; it is now implemented but not device-certified.
