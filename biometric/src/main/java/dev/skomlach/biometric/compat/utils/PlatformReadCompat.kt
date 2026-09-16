package dev.skomlach.biometric.compat.utils

internal fun <T> readPlatformOrLegacy(
    platformAvailable: Boolean,
    platformRead: () -> T,
    legacyRead: () -> T,
    onLinkageError: (LinkageError) -> Unit = {}
): T {
    if (platformAvailable) {
        try {
            // Null and empty results are valid; neither warrants hidden API access.
            return platformRead()
        } catch (error: LinkageError) {
            onLinkageError(error)
        }
    }
    return legacyRead()
}
