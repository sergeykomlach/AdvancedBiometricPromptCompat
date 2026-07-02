package dev.skomlach.biometric.compat.engine.internal.fingerprint.zk

private const val ZK_PERMISSION_SUFFIX = ".dev.skomlach.biometric.zkfinger.USB_PERMISSION"
private const val ZK_ENROLLMENT_TAG_MAX_LENGTH = 64
private val ZK_ENROLLMENT_TAG_INVALID_CHARS = Regex("[^A-Za-z0-9_.-]")

internal fun resolveZkHardwareDetected(
    usbHostAvailable: Boolean,
    supportedDeviceConnected: Boolean
): Boolean {
    return usbHostAvailable && supportedDeviceConnected
}

internal fun sanitizeZkEnrollmentTag(tag: String?): String? {
    val sanitized = tag
        ?.trim()
        ?.replace(ZK_ENROLLMENT_TAG_INVALID_CHARS, "_")
        ?.take(ZK_ENROLLMENT_TAG_MAX_LENGTH)
    return sanitized?.takeIf { it.isNotBlank() }
}

internal fun resolveZkPermissionAction(packageName: String): String {
    return packageName + ZK_PERMISSION_SUFFIX
}

internal fun resolveZkPermissionRequestCode(
    vendorId: Int,
    productId: Int,
    deviceIndex: Int
): Int {
    var result = 17
    result = 31 * result + vendorId
    result = 31 * result + productId
    result = 31 * result + deviceIndex
    return result
}
