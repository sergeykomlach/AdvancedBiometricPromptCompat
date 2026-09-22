# ZK classes are reached through the adapter's direct public API and vendor JNI/runtime contract.
-keep class com.zkteco.** { *; }
# The vendor runtime is intentionally compileOnly. Missing classes fail closed at runtime.
-dontwarn com.zkteco.**
# Preserve the optional linkage boundary: do not inline/merge vendor references into callers.
-keep class dev.skomlach.biometric.compat.engine.internal.fingerprint.zk.DirectZkFingerSdkBridge** { *; }
