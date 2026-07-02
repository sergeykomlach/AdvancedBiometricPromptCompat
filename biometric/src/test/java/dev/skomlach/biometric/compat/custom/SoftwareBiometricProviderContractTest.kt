package dev.skomlach.biometric.compat.custom

import android.content.Context
import org.junit.Assert.assertNull
import org.junit.Test

class SoftwareBiometricProviderContractTest {

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
