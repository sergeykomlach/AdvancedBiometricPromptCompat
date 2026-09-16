# R8 adapts the ServiceLoader descriptor. Keep constructors to prevent optional
# engines from being removed while still allowing their names to be obfuscated.
-keep,allowoptimization,allowobfuscation class * implements dev.skomlach.biometric.compat.engine.internal.voice.VoiceEngineProvider { <init>(); }