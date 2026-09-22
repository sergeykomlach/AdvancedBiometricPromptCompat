# The wrapper uses sherpa-onnx's direct public API, while the runtime remains optional for
# consumers. Retain a supplied runtime in a shrinker.
-keep class com.k2fsa.sherpa.onnx.** { *; }
# The vendor runtime is intentionally compileOnly. Missing classes fail closed at runtime.
-dontwarn com.k2fsa.sherpa.onnx.**
# Preserve the optional linkage boundary: do not inline/merge vendor references into callers.
-keep class dev.skomlach.biometric.compat.engine.internal.sherpaonnx.DirectSherpaOnnxEmbeddingRuntime** { *; }
