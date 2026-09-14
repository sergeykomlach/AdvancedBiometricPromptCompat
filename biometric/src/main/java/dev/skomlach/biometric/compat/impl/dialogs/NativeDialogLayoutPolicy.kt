package dev.skomlach.biometric.compat.impl.dialogs

/** Resource capabilities, not SDK/manufacturer names, determine the reader. */
internal object NativeDialogLayoutPolicy {
    private val resourceName = Regex("[a-z0-9_]+")
    private val excluded = listOf("enroll", "keyguard", "settings", "accessibility", "credential")
    fun twoPane(landscape: Boolean, displaySmallestWidthDp: Int): Boolean =
        landscape && displaySmallestWidthDp in 1 until 600

    // AOSP may inflate the FrameLayout XML into an AuthContainerView created in code.
    // The caller must still validate the panel, scroll view and fingerprint contents.
    fun matchesAuthContainer(root: String, oplus: Boolean): Boolean = if (oplus) {
        root == "com.oplus.systemui.biometrics.OplusAuthContainerView"
    } else {
        root == "com.android.systemui.biometrics.AuthContainerView" || root == "FrameLayout"
    }

    fun paneHint(name: String): Boolean? {
        val compact = name.replace("_", "").lowercase()
        val one = "onepane" in compact
        val two = "twopane" in compact
        return if (one == two) null else two
    }

    fun isDiscoveryCandidate(name: String): Boolean =
        ("biometric_" in name || "fingerprint_" in name) &&
            name.matches(resourceName) && excluded.none { it in name }

    fun matchesPane(name: String, twoPane: Boolean, hasMiddleGuideline: Boolean): Boolean {
        val compact = name.replace("_", "").lowercase()
        if ("onepane" in compact && "twopane" in compact) return false
        return hasMiddleGuideline == twoPane && (paneHint(name)?.let { it == twoPane } != false)
    }

    /** Never choose an arbitrary layout when an OEM ships several matching structures. */
    fun <T> unique(candidates: List<T>): T? = candidates.singleOrNull()
}
