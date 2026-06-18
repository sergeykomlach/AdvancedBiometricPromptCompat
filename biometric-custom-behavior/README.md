# Biometric Custom Behavior

Experimental software biometric provider for `BiometricType.BIOMETRIC_BEHAVIOR`.

Supported modes:

- `TYPING`: compares phrase typing dwell and digraph timings.
- `SIGNATURE`: compares touch/stylus stroke path with DTW and shape features.
- `COMBINED`: requires both typing and signature data and fuses both scores.

The module is registered through `ServiceLoader` and uses the existing
`SoftwareBiometricModule` route. It does not provide Android Keystore-backed
crypto authentication.

Pass samples through `BiometricPromptCompat.Builder.setExtras(...)` using keys
from `BehaviorSample`:

```kotlin
val extras = Bundle().apply {
    putString(BehaviorSample.EXTRA_BEHAVIOR_MODE, BehaviorMode.COMBINED.name)
    putString(BehaviorSample.EXTRA_BEHAVIOR_PHRASE, "open sesame")
    putLongArray(BehaviorSample.EXTRA_BEHAVIOR_KEY_DOWNS, longArrayOf(0, 120, 260))
    putLongArray(BehaviorSample.EXTRA_BEHAVIOR_KEY_UPS, longArrayOf(80, 190, 330))
    putFloatArray(
        BehaviorSample.EXTRA_BEHAVIOR_POINTS,
        floatArrayOf(
            0f, 0f, 0f, -1f, -1f, 0f,
            10f, 10f, 10f, -1f, -1f, 0f,
            20f, 20f, 20f, -1f, -1f, 0f
        )
    )
    putInt(BehaviorSample.EXTRA_BEHAVIOR_POINTS_STRIDE, 6)
}
```

For enrollment, call the normal enrollment flow and provide the same extras.
For authentication, provide a fresh probe sample in the same format. Repeating
enrollment with the same enrollment tag stores multiple recent samples, which
improves tolerance for natural behavior variance. Authentication evaluates the
recent templates with a small top-k aggregation, similar to lightweight KNN
matching, instead of trusting a single best sample.

Enrollment and authentication reject low-quality samples before matching:
typing phrases must be long enough and have useful timing variation, while
signature samples must contain enough points, path length, and visible shape
area. Signature points support an optional `strokeId` field so separate pen
strokes are not treated as one continuous path. Legacy five-value points are
still accepted and are treated as a single stroke.

Alternatively, let the library collect samples from consumer-owned views:

```kotlin
BiometricPromptCompat.Builder(request, activity)
    .setBehaviorAuthMode(BehaviorAuthMode.EXPLICIT)
    .setBehaviorTypingView(passphraseEditText)
    .setBehaviorSignatureContainer(signatureContainer)
```

`BehaviorAuthMode.EXPLICIT` adds a small behavior action button to the existing
compat dialog. Pressing it opens an overlay that can collect typing, signature,
or combined samples without changing the base dialog layout. When consumer-owned
views are provided, the prompt attaches temporary listeners and reuses those
views; otherwise it creates temporary input controls inside the overlay.
The built-in typing overlay uses a compact touch keyboard so the module can
capture real key down/up timings instead of relying on soft-keyboard text change
events.

The current implementation intentionally avoids bundling TensorFlow Lite or
Python/SciPy. Public Android references for keystroke and online-signature
authentication commonly require trained user/model data or heavy runtime stacks;
this module keeps the default provider deterministic and dependency-light. A
future optional ML backend can be added once a model format and calibration
dataset are available.

`BehaviorAuthMode.PASSIVE` does not show behavior UI and does not delay prompt
startup. Use it when the app already collected a behavior sample and passed it
through `setExtras(...)`, or when the provider should fail fast because no
sample is available.
