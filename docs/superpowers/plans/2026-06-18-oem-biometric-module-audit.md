# OEM BiometricModule audit and optimization plan

## Goal

Review `dev.skomlach.biometric.compat.engine.internal` from public route selection down to OEM managers and local decompiled references. Remove avoidable bottlenecks, duplicated checks, and false-positive hardware detection without widening public API unless there is a clear performance or correctness win.

## Baseline contracts

- A single top-level request should evaluate module state from one snapshot where possible.
- `isManagerAccessible` is only acquisition state. It must not be treated as hardware presence.
- `isHardwarePresent` should be cheap, deterministic, and should not start authentication, bind long-lived services, or call destructive lifecycle methods.
- `hasEnrolled` should not branch into manager discovery again.
- ZK software fingerprint remains preferred when a ZK device is connected.
- TF face remains a fallback when no hardware face route is available, or when the caller explicitly chooses software face.
- Android platform `BiometricManager.canAuthenticate(...)` remains the baseline for platform prompt routes because it distinguishes success, no hardware, temporarily unavailable hardware, and no enrolled templates.

## High-confidence findings

### Software modules

Files:
- `biometric/src/main/java/dev/skomlach/biometric/compat/engine/internal/SoftwareBiometricModule.kt`
- `biometric-custom-face-tf/src/main/java/dev/skomlach/biometric/compat/engine/internal/face/tensorflow/TensorFlowFaceUnlockManager.kt`
- `biometric-zkfinger/src/main/java/dev/skomlach/biometric/compat/engine/internal/fingerprint/zk/ZkFingerUnlockManager.kt`

Current issue:
- Normal state reads in `SoftwareBiometricModule` were logged through error-level `e(...)`.
- AUTO/ANY and route snapshots call these state reads often, so this created misleading error logs and avoidable logging overhead.

Implemented fix:
- State read diagnostics now use debug-level logging.

### Lava face unlock

Files:
- `biometric/src/main/java/dev/skomlach/biometric/compat/engine/internal/face/lava/FaceunlockLavaModule.kt`
- `biometric/src/main/java/dev/skomlach/biometric/compat/engine/internal/face/lava/FaceVerifyManager.kt`

Current issue:
- `FaceunlockLavaModule` called `bindFaceVerifyService()` from `init` when `isHardwarePresent` returned true.
- `FaceVerifyManager.onServiceConnected()` immediately called `startVerify()`, so discovery could start verification before an authentication request.
- The hardware-present init branch did not call `listener.initFinished(...)`, which could keep `LegacyBiometric.initInProgress` alive and delay later authentication.
- `stopAuth()` cleared the callback, but the callback was local to `init`, so a later auth request could not restore it.

Implemented fix:
- Module init now creates the helper, restores manager accessibility, and completes `initFinished(...)` synchronously.
- Service bind/start is delayed until `authenticate`.
- The callback is stored as a module field and restored before each auth request.

### Samsung face and iris

Files:
- `biometric/src/main/java/dev/skomlach/biometric/compat/engine/internal/face/samsung/SamsungFaceUnlockModule.kt`
- `biometric/src/main/java/dev/skomlach/biometric/compat/engine/internal/iris/samsung/SamsungIrisUnlockModule.kt`

Current issue:
- `isHardwarePresent` calls `manager?.isHardwareDetected == true`, but if this throws it falls back to `isManagerAccessible`.
- That can register Samsung face/iris as hardware-present when only the manager object is reachable. This increases route fan-out and may delay fallback to TF or other routes.

Recommended fix:
- Return `false` when `isHardwareDetected` throws.
- Keep manager acquisition as-is for now.
- This is low risk and aligns with the snapshot contract.

### Huawei and Honor legacy face

Files:
- `biometric/src/main/java/dev/skomlach/biometric/compat/engine/internal/face/huawei/impl/HuaweiFaceRecognizeManager.kt`
- `biometric/src/main/java/dev/skomlach/biometric/compat/engine/internal/face/hihonor/impl/HihonorFaceRecognizeManager.kt`
- `biometric/src/main/java/dev/skomlach/biometric/compat/engine/internal/face/huawei/impl/HuaweiFaceManagerV1Impl.kt`
- `biometric/src/main/java/dev/skomlach/biometric/compat/engine/internal/face/hihonor/impl/HihonorFaceManagerV1Impl.kt`

Current issue:
- Manager creation calls into `FaceRecognizeManager(...)`, then `init()`, then `release()` during discovery.
- Authentication later calls `init()` again. Cancel calls `cancelAuthenticate()`, `release()`, then `createInstance()`.
- Honor implementation mirrors Huawei. Local evidence does not prove Honor has a separate public API shape; it looks like a copied compatibility surface with package/name substitution.

Recommended fix:
- Do not rewrite these modules in the first pass.
- Add an internal manager-session boundary only after device validation, because Huawei/Honor lifecycle calls may be required by the OEM service.
- First reduce duplicate state reads above these modules with the existing request snapshot.

### MIUI face

Files:
- `biometric/src/main/java/dev/skomlach/biometric/compat/engine/internal/face/miui/MiuiFaceUnlockModule.kt`
- `biometric/src/main/java/dev/skomlach/biometric/compat/engine/internal/face/miui/impl/MiuiFaceFactory.kt`
- `biometric/src/main/java/dev/skomlach/biometric/compat/engine/internal/face/miui/impl/MiuiFaceManagerImpl.kt`
- `biometric/src/main/java/dev/skomlach/biometric/compat/engine/internal/face/miui/impl/Miui3DFaceManagerImpl.kt`

Current issue:
- `MiuiFaceFactory.getCurrentAuthType()` tries 3D first, then 2D, and both paths may instantiate singleton managers.
- `MiuiFaceUnlockModule` calls `getFaceManager(TYPE_DEFAULT)` and then logs `getCurrentAuthType()` again, which can duplicate detection.

Recommended fix:
- Stop the extra `getCurrentAuthType()` log call or reuse the selected type captured by `getFaceManager(TYPE_DEFAULT)`.
- Consider making `MiuiFaceFactory` return a small result object with both manager and selected type for internal use. This is internal API only.

### Oppo / Oplus / OnePlus

Files and references:
- `biometric/src/main/java/dev/skomlach/biometric/compat/engine/internal/face/oppo/OppoFaceUnlockModule.kt`
- `biometric-api/src/main/java/android/hardware/face/OppoMirrorFaceManager.kt`
- `biometric-api/src/main/java/android/hardware/face/OplusFaceManager.kt`
- `SystemUI_source_from_JADX/sources/com/oplus/compat/hardware/face/FaceManagerNative.java`
- `oneplus/OnePlusFacelockModule.kt`

Current state:
- Existing Oppo module uses `OppoMirrorFaceManager` through `ContextCompat.getSystemService(...)`, then `context.getSystemService("face")`.
- Local Oplus decompiled code shows newer `OplusFaceManager` and `FaceManagerNative` paths for R/S+ style APIs, but these look privileged or version-gated.
- OnePlus facelock helper is a separate service-style integration and should not be merged into the Oppo module blindly.

Recommended fix:
- Keep current Oppo module conservative.
- Treat Oplus as a candidate module, not a direct replacement.
- Build Oplus only behind strict API/vendor gates after confirming callable methods on a real device or local reference logs.

## First implementation pass

1. Fix Samsung face/iris `isHardwarePresent` fallback from `isManagerAccessible` to `false`.
2. Remove MIUI duplicate current-auth-type detection during module init.
3. Keep Huawei/Honor lifecycle untouched, but document them as high-cost discovery modules.
4. Keep Oplus/OnePlus as research candidates and do not wire them into `LegacyBiometric` yet.

## Verification

- Run `.\gradlew.bat :biometric:compileDebugKotlin`.
- Run `.\gradlew.bat :app:compileDebugKotlin` if any public or demo-facing route code changes.
- Run `git diff --check`.
- On device, compare AUTO/FACE and AUTO/ANY logs for:
  - fewer module state reads per request;
  - no Samsung false-positive face/iris route when `isHardwareDetected` fails;
  - unchanged ZK priority;
  - unchanged TF fallback behavior when hardware face is absent.
