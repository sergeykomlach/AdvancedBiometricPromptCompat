package dev.skomlach.biometric.compat;

import androidx.annotation.RestrictTo;

@RestrictTo(RestrictTo.Scope.LIBRARY)
public enum BiometricApi {
    AUTO, LEGACY_API, BIOMETRIC_API
    //TODO:
    //Typing https://github.com/TypingDNA/TypingDNARecorder-Android + https://api.typingdna.com/
    //Voice https://github.com/microsoft/Cognitive-SpeakerRecognition-Android
}