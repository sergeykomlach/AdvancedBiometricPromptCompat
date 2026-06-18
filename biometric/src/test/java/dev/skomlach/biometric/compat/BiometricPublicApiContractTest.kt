package dev.skomlach.biometric.compat

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class BiometricPublicApiContractTest {

    @Test
    fun defaultAuthRequestUsesAutomaticCombinedRoute() {
        val request = BiometricAuthRequest.default()

        assertEquals(BiometricApi.AUTO, request.api)
        assertEquals(BiometricType.BIOMETRIC_ANY, request.type)
        assertEquals(BiometricConfirmation.ANY, request.confirmation)
        assertEquals(BiometricProviderType.COMBINED, request.provider)
    }

    @Test
    fun authRequestHelpersReturnModifiedCopies() {
        val original = BiometricAuthRequest.default()
        val modified = original
            .withApi(BiometricApi.BIOMETRIC_API)
            .withType(BiometricType.BIOMETRIC_FACE)
            .withConfirmation(BiometricConfirmation.ALL)
            .withProvider(BiometricProviderType.HARDWARE)

        assertEquals(BiometricApi.AUTO, original.api)
        assertEquals(BiometricType.BIOMETRIC_ANY, original.type)
        assertEquals(BiometricConfirmation.ANY, original.confirmation)
        assertEquals(BiometricProviderType.COMBINED, original.provider)
        assertEquals(BiometricApi.BIOMETRIC_API, modified.api)
        assertEquals(BiometricType.BIOMETRIC_FACE, modified.type)
        assertEquals(BiometricConfirmation.ALL, modified.confirmation)
        assertEquals(BiometricProviderType.HARDWARE, modified.provider)
    }

    @Test
    fun cryptographyPurposeComparesInitVectorByContent() {
        val first = BiometricCryptographyPurpose(
            BiometricCryptographyPurpose.DECRYPT,
            byteArrayOf(1, 2, 3)
        )
        val second = BiometricCryptographyPurpose(
            BiometricCryptographyPurpose.DECRYPT,
            byteArrayOf(1, 2, 3)
        )
        val different = BiometricCryptographyPurpose(
            BiometricCryptographyPurpose.DECRYPT,
            byteArrayOf(1, 2, 4)
        )

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
        assertNotEquals(first, different)
    }

    @Test
    fun cryptographyResultComparesPayloadAndIvByContent() {
        val first = BiometricCryptographyResult(
            biometricType = BiometricType.BIOMETRIC_FINGERPRINT,
            data = byteArrayOf(10, 20),
            initializationVector = byteArrayOf(30, 40)
        )
        val second = BiometricCryptographyResult(
            biometricType = BiometricType.BIOMETRIC_FINGERPRINT,
            data = byteArrayOf(10, 20),
            initializationVector = byteArrayOf(30, 40)
        )
        val different = second.copy(data = byteArrayOf(10, 21))

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
        assertNotEquals(first, different)
        assertArrayEquals(byteArrayOf(30, 40), first.initializationVector)
    }

    @Test
    fun authenticationResultDefaultsCryptoSecurityLevelFromCryptoObject() {
        val cryptoObject = BiometricCryptoObject(
            cryptoSecurityLevel = CryptoSecurityLevel.APP_FLOW_NOT_BIOMETRIC_BOUND
        )

        val result = AuthenticationResult(
            type = BiometricType.BIOMETRIC_FACE,
            cryptoObject = cryptoObject
        )

        assertEquals(CryptoSecurityLevel.APP_FLOW_NOT_BIOMETRIC_BOUND, result.cryptoSecurityLevel)
    }
}
