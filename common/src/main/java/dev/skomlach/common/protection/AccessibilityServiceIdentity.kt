package dev.skomlach.common.protection

import android.content.pm.ApplicationInfo

/** Package names and accessibility-tool metadata alone do not establish provenance. */
internal fun isSystemAccessibilitySource(applicationFlags: Int): Boolean =
    applicationFlags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

/** Canonical package/class identity; relative classes follow Android ComponentName semantics. */
internal data class AccessibilityServiceIdentity private constructor(
    val packageName: String,
    val className: String,
) {
    companion object {
        fun from(packageName: String?, className: String?): AccessibilityServiceIdentity? {
            if (packageName.isNullOrBlank() || className.isNullOrBlank() || className == ".") return null
            // These are individual PackageItemInfo fields, not serialized component lists.
            if (packageName.any { it.isWhitespace() || it == '/' || it == ':' } ||
                className.any { it.isWhitespace() || it == '/' || it == ':' }) return null
            return AccessibilityServiceIdentity(
                packageName,
                if (className.startsWith('.')) packageName + className else className,
            )
        }
    }
}

/** A failed identity lookup must not disappear from the trust decision. */
internal fun areAllAccessibilityServicesTrusted(
    services: Iterable<AccessibilityServiceIdentity?>,
    isTrusted: (AccessibilityServiceIdentity) -> Boolean,
): Boolean = services.all { it != null && isTrusted(it) }
