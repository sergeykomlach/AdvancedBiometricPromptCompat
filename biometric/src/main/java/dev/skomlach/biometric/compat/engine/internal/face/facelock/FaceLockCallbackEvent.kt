package dev.skomlach.biometric.compat.engine.internal.face.facelock

/** Stable external AIDL names are separate from obfuscatable local method names. */
internal enum class FaceLockCallbackEvent(val wireName: String) {
    UNLOCK("unlock"), CANCEL("cancel"), FAILED("reportFailedAttempt"),
    FALLBACK("exposeFallback"), WAKE("pokeWakelock"), TIMED_WAKE("pokeWakelock");

    /** Capture payload on the Binder thread; no Parcel may escape to the main-thread callback. */
    fun capture(callback: IFaceLockCallback, readMillis: () -> Int): () -> Unit = when (this) {
        UNLOCK -> callback::unlock
        CANCEL -> callback::cancel
        FAILED -> callback::reportFailedAttempt
        FALLBACK -> callback::exposeFallback
        WAKE -> { { callback.pokeWakelock() } }
        TIMED_WAKE -> {
            val millis = readMillis()
            ({ callback.pokeWakelock(millis) })
        }
    }
}
