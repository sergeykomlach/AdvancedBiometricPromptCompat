# biometric-zkfinger

Production wrapper for the ZKTeco ZKFinger Android SDK. The module is discovered through
`SoftwareBiometricProvider` in the same way as `biometric-custom-face-tf`.

Bundled SDK artifacts:

- `zkandroidcore.jar`
- `zkandroidfingerservice.jar`
- `zkandroidfpreader.jar`
- `arm64-v8a` and `armeabi-v7a` native libraries

The manager stores enrolled templates in protected encrypted preferences and reloads them into
`ZKFingerService` for each active capture session. USB device permission is requested at runtime.

App integration:

```groovy
implementation project(":biometric-zkfinger")
```

Enrollment uses the normal `BiometricPromptCompat` software enrollment flow. The provider returns
`BiometricType.BIOMETRIC_FINGERPRINT`, so callers can keep requesting fingerprint authentication.
