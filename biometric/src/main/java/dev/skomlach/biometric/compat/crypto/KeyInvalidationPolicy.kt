package dev.skomlach.biometric.compat.crypto

internal fun isPermanentlyInvalidatedKey(error: Throwable): Boolean {
    val visited = HashSet<Throwable>()
    var current: Throwable? = error
    while (current != null && visited.add(current)) {
        // Avoid resolving an API 23 exception class on older Android versions.
        if (current.javaClass.name == "android.security.keystore.KeyPermanentlyInvalidatedException") {
            return true
        }
        current = current.cause
    }
    return false
}
