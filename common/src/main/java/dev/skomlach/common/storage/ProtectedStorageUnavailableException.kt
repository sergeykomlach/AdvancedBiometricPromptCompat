package dev.skomlach.common.storage

import android.content.SharedPreferences

/** Unavailable or unreadable protected data is never equivalent to a missing preference. */
class ProtectedStorageUnavailableException(
    message: String = "Protected storage is unavailable",
    cause: Throwable? = null
) : IllegalStateException(message, cause)

/** Optional maintenance must not interrupt a successful, independent authentication. */
fun runProtectedStorageMaintenance(onUnavailable: (ProtectedStorageUnavailableException) -> Unit, action: () -> Unit) {
    try {
        action()
    } catch (error: ProtectedStorageUnavailableException) {
        onUnavailable(error)
    }
}

/** Enrollment must not report success until its encrypted template has been persisted. */
fun SharedPreferences.editProtected(action: SharedPreferences.Editor.() -> Unit) {
    val editor = edit()
    editor.action()
    if (!editor.commit()) {
        throw ProtectedStorageUnavailableException("Cannot persist protected preferences")
    }
}
