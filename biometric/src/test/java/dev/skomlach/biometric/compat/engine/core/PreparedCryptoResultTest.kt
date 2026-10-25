package dev.skomlach.biometric.compat.engine.core

import dev.skomlach.biometric.compat.AuthenticationFailureReason
import dev.skomlach.biometric.compat.BiometricCryptoObject
import dev.skomlach.biometric.compat.CryptoSecurityLevel
import dev.skomlach.biometric.compat.engine.core.interfaces.AuthenticationListener
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.*
import org.junit.Test

class PreparedCryptoResultTest {
    private class Recorder : AuthenticationListener {
        var result: BiometricCryptoObject? = null
        var tag: Int? = null
        override fun onHelp(msg: CharSequence?) = Unit
        override fun onSuccess(moduleTag: Int, biometricCryptoObject: BiometricCryptoObject?) {
            tag = moduleTag
            result = biometricCryptoObject
        }
        override fun onFailure(moduleTag: Int, reason: AuthenticationFailureReason?, description: CharSequence?) = Unit
        override fun onCanceled(moduleTag: Int, reason: AuthenticationFailureReason?, description: CharSequence?) = Unit
    }

    // Exercise the actual Core adapter without starting Android Keystore or a biometric sensor.
    private fun adapter(recorder: Recorder, prepared: BiometricCryptoObject?): AuthenticationListener {
        val method = Core::class.java.getDeclaredMethod(
            "withPreparedCryptoObject", AuthenticationListener::class.java, BiometricCryptoObject::class.java
        )
        method.isAccessible = true
        return method.invoke(Core, recorder, prepared) as AuthenticationListener
    }

    private fun verify(providerResult: BiometricCryptoObject?) {
        val key = SecretKeySpec(ByteArray(32) { (it + 1).toByte() }, "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
        val prepared = BiometricCryptoObject(cipher = cipher,
            cryptoSecurityLevel = CryptoSecurityLevel.APP_FLOW_NOT_BIOMETRIC_BOUND)
        val recorder = Recorder()
        adapter(recorder, prepared).onSuccess(42, providerResult)
        assertEquals(42, recorder.tag)
        assertSame(prepared, recorder.result)
        assertEquals(CryptoSecurityLevel.APP_FLOW_NOT_BIOMETRIC_BOUND, recorder.result?.cryptoSecurityLevel)
        val clear = "synthetic fallback payload".toByteArray()
        val encrypted = recorder.result!!.cipher!!.doFinal(clear)
        val decrypt = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, cipher.iv))
        }
        assertArrayEquals(clear, decrypt.doFinal(encrypted))
    }

    @Test fun nullProviderCryptoPreservesPreparedCipherAndAssurance() = verify(null)
    @Test fun emptyProviderCryptoCannotReplacePreparedCipher() = verify(BiometricCryptoObject())
    @Test fun providerCryptoCannotUpgradeAnAppFlowCipherToHardwareBacked() = verify(
        BiometricCryptoObject(cipher = Cipher.getInstance("AES/GCM/NoPadding")))
    @Test fun absentPreparedCipherCannotBeInventedByProvider() {
        val recorder = Recorder()
        adapter(recorder, null).onSuccess(9, BiometricCryptoObject())
        assertEquals(9, recorder.tag)
        assertNull(recorder.result)
    }
}
