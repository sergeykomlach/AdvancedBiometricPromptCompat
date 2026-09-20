# biometric-zkfinger

Source-only integration adapter for the ZKTeco ZKFinger Android SDK. The module is discovered
through `SoftwareBiometricProvider` in the same way as `biometric-custom-face-tf`.

This repository and its Maven AAR deliberately contain **no** ZKTeco JARs, native libraries,
licence files, keys, models, samples, or documentation. The adapter compiles against the vendor's
public typed API as `compileOnly`, so a missing or unlicensed SDK is reported as unavailable rather
than copied into an app.

## Vendor SDK setup

The application developer must obtain a ZKFinger Android SDK distribution and all required
licences directly from ZKTeco. They must add the vendor JARs and ABI-matched native libraries to
their own application package according to ZKTeco's terms. Do not commit those artifacts to this
repository or publish them through this module.

For local Debug development only, private vendor artifacts may be kept under
`biometric-zkfinger/vendor-sdk/`. Its three JARs are required to compile this direct adapter; its
ABI-matched native libraries are consumed only in the Debug variant. This directory is Git-ignored
and never becomes a release or published Maven dependency. A consumer APK that omits the runtime
fails closed: the provider is unavailable and does not crash the process.

The manager stores enrolled templates in protected encrypted preferences and reloads them into
`ZKFingerService` for each active capture session. USB device permission is requested at runtime.

App integration:

```groovy
implementation project(":biometric-zkfinger")
```

The application must also package the separately obtained ZKFinger SDK. Adding this module alone
does not grant a ZKTeco SDK licence and intentionally does not make the scanner available.

Enrollment uses the normal `BiometricPromptCompat` software enrollment flow. The provider returns
`BiometricType.BIOMETRIC_FINGERPRINT`, so callers can keep requesting fingerprint authentication.
