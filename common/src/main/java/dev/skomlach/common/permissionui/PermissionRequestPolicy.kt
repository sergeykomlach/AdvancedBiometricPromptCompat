package dev.skomlach.common.permissionui

internal enum class PermissionRequestDecision {
    REQUEST_RUNTIME,
    SHOW_RATIONALE,
    SHOW_MANDATORY_SETTINGS
}

internal fun permissionRequestDecision(
    permissions: Collection<String>,
    permissionsWithRationale: Set<String>,
    previouslyDeniedPermissions: Set<String>
): PermissionRequestDecision {
    val requestedPermissions = permissions.toSet()
    val freshRuntimePermissions = requestedPermissions
        .filterNot { permissionsWithRationale.contains(it) }
        .filterNot { previouslyDeniedPermissions.contains(it) }
    if (freshRuntimePermissions.isNotEmpty()) {
        return PermissionRequestDecision.REQUEST_RUNTIME
    }
    return when {
        requestedPermissions.any { permissionsWithRationale.contains(it) } ->
            PermissionRequestDecision.SHOW_RATIONALE

        requestedPermissions.any { previouslyDeniedPermissions.contains(it) } ->
            PermissionRequestDecision.SHOW_MANDATORY_SETTINGS

        else -> PermissionRequestDecision.REQUEST_RUNTIME
    }
}
