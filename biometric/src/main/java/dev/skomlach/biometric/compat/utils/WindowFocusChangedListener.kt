package dev.skomlach.biometric.compat.utils;

import androidx.annotation.RestrictTo;

@RestrictTo(RestrictTo.Scope.LIBRARY)
public interface WindowFocusChangedListener {
    void onStartWatching();

    void hasFocus(boolean hasFocus);
}
