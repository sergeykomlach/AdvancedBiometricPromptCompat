# The wrapper uses sherpa-onnx's direct public API, while the runtime remains optional for
# consumers. Retain a supplied runtime in a shrinker.
-keep class com.k2fsa.sherpa.onnx.** { *; }
