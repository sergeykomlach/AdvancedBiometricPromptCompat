package dev.skomlach.common.misc

/** PackageManager-controlled provenance, never inferred from an APK path or package name. */
internal data class SystemPackageCodeIdentity(
    val packageName: String?, val sourceDir: String?, val uid: Int,
    val isSystem: Boolean, val isUpdatedSystem: Boolean
)

internal class SystemPackageCodeLoader<T>(
    private val lookup: (String) -> SystemPackageCodeIdentity,
    private val create: (String) -> T
) {
    private val cache = mutableMapOf<String, Pair<SystemPackageCodeIdentity, T>>()

    @Synchronized fun get(packageName: String): T {
        // Resolve trust before consulting the cache, including after package replacement.
        val identity = lookup(packageName)
        val path = identity.sourceDir
        if (identity.packageName != packageName || path.isNullOrBlank() ||
            (!identity.isSystem && !identity.isUpdatedSystem)) {
            cache.remove(packageName)
            throw SecurityException("External code loading requires a verified system package")
        }
        cache[packageName]?.takeIf { it.first == identity }?.let { return it.second }
        return create(path).also { cache[packageName] = identity to it }
    }
}
