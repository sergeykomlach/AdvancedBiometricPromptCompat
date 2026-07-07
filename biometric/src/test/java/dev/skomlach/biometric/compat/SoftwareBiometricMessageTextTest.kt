package dev.skomlach.biometric.compat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SoftwareBiometricMessageTextTest {

    @Test
    fun softwareBiometricCryptoFallbackIsBackedByResources() {
        val moduleSource = File(
            "src/main/java/dev/skomlach/biometric/compat/engine/internal/SoftwareBiometricModule.kt"
        ).readText()
        val stringsSource = File("src/main/res/values/strings.xml").readText()

        assertFalse(
            "SoftwareBiometricModule should not keep the crypto fallback as a hardcoded literal",
            moduleSource.contains(
                "Software biometric module \$name cannot satisfy a hardware-backed CryptoObject"
            )
        )
        assertTrue(
            "Base biometric strings should define a dedicated resource key for the crypto fallback",
            stringsSource.contains("biometriccompat_software_hardware_backed_crypto_unsupported")
        )
    }
}
