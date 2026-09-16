package dev.skomlach.biometric.compat.crypto

import dev.skomlach.common.storage.ProtectedStorageUnavailableException
import java.security.ProviderException
import java.security.UnrecoverableKeyException
import org.junit.Assert.assertFalse
import org.junit.Test

class KeyInvalidationPolicyTest {
    @Test fun providerOutageMustNotDeleteExistingKeys() {
        assertFalse(isPermanentlyInvalidatedKey(BiometricCryptoException(ProviderException("unavailable"))))
    }

    @Test fun unreadablePreferencesMustNotDeleteExistingKeys() {
        assertFalse(isPermanentlyInvalidatedKey(BiometricCryptoException(ProtectedStorageUnavailableException())))
    }

    @Test fun unrecoverableKeyIsNotProofOfPermanentInvalidation() {
        assertFalse(isPermanentlyInvalidatedKey(UnrecoverableKeyException("temporarily locked")))
    }
}
