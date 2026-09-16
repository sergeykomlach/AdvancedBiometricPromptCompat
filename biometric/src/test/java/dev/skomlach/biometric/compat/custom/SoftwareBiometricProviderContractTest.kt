package dev.skomlach.biometric.compat.custom

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SoftwareBiometricProviderContractTest {
    @Test
    fun explicitIdDoesNotReadManagerClassName() {
        val provider = providerWithId(42)

        assertEquals(42, provider.resolveModuleId { error("Class name must not be read") })
    }

    @Test
    fun classRenameDoesNotChangeExplicitId() {
        val provider = providerWithId(42)

        assertEquals(42, provider.resolveModuleId { "example.vendor.FaceManager" })
        assertEquals(42, provider.resolveModuleId { "a.b" })
    }

    @Test
    fun signedAndZeroIdsRemainValid() {
        for (id in listOf(Int.MIN_VALUE, -1, 0, Int.MAX_VALUE)) {
            assertEquals(id, providerWithId(id).resolveModuleId { error("Unused fallback") })
        }
    }

    @Test
    fun oldProviderKeepsItsPreviouslyStoredId() {
        val provider = object : SoftwareBiometricProvider() {
            override fun getCustomManager(context: Context): AbstractSoftwareBiometricManager {
                error("unused")
            }
        }

        assertNull(provider.moduleId)
        assertEquals(-348482624, provider.resolveModuleId { "example.vendor.LegacyBiometricManager" })
    }

    private fun providerWithId(id: Int): SoftwareBiometricProvider =
        object : SoftwareBiometricProvider() {
            override val moduleId: Int = id

            override fun getCustomManager(context: Context): AbstractSoftwareBiometricManager {
                error("unused")
            }
        }


    @Test
    fun defaultPromptFactoryIsNull() {
        val provider = object : SoftwareBiometricProvider() {
            override fun getCustomManager(context: Context): AbstractSoftwareBiometricManager {
                error("unused")
            }
        }

        assertNull(provider.getPromptFactory())
    }
}
