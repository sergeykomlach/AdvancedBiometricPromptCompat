package dev.skomlach.common.permissionui

import android.Manifest
import org.junit.Assert.assertEquals
import org.junit.Test

class PermissionRequestPolicyTest {

    @Test
    fun previouslyDeniedCameraDoesNotAffectNewMicrophoneRequest() {
        val decision = permissionRequestDecision(
            permissions = listOf(Manifest.permission.RECORD_AUDIO),
            permissionsWithRationale = emptySet(),
            previouslyDeniedPermissions = setOf(Manifest.permission.CAMERA)
        )

        assertEquals(PermissionRequestDecision.REQUEST_RUNTIME, decision)
    }

    @Test
    fun cameraRationaleDoesNotBlockFreshMicrophoneRuntimeRequestInSameBatch() {
        val decision = permissionRequestDecision(
            permissions = listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO),
            permissionsWithRationale = setOf(Manifest.permission.CAMERA),
            previouslyDeniedPermissions = emptySet()
        )

        assertEquals(PermissionRequestDecision.REQUEST_RUNTIME, decision)
    }

    @Test
    fun currentPermissionWithRationaleShowsRationaleDialog() {
        val decision = permissionRequestDecision(
            permissions = listOf(Manifest.permission.RECORD_AUDIO),
            permissionsWithRationale = setOf(Manifest.permission.RECORD_AUDIO),
            previouslyDeniedPermissions = emptySet()
        )

        assertEquals(PermissionRequestDecision.SHOW_RATIONALE, decision)
    }

    @Test
    fun currentPreviouslyDeniedPermissionWithoutRationaleOpensMandatoryDialog() {
        val decision = permissionRequestDecision(
            permissions = listOf(Manifest.permission.RECORD_AUDIO),
            permissionsWithRationale = emptySet(),
            previouslyDeniedPermissions = setOf(Manifest.permission.RECORD_AUDIO)
        )

        assertEquals(PermissionRequestDecision.SHOW_MANDATORY_SETTINGS, decision)
    }
}
