package dev.skomlach.biometric.compat

import dev.skomlach.biometric.compat.engine.BiometricMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BiometricManagerCompatPermissionTest {

    @Test
    fun `miui face exact permissions stay empty`() {
        assertEquals(emptyList<String>(), BiometricManagerCompat.getHardwarePermissions(BiometricMethod.FACE_MIUI))
    }

    @Test
    fun `samsung face exact permissions do not include unrelated oem entries`() {
        val permissions = BiometricManagerCompat.getHardwarePermissions(BiometricMethod.FACE_SAMSUNG)

        assertEquals(listOf("com.samsung.android.bio.face.permission.USE_FACE"), permissions)
        assertFalse(permissions.contains("android.permission.USE_FACE_AUTHENTICATION"))
        assertFalse(permissions.contains("oppo.permission.USE_FACE"))
    }
}
