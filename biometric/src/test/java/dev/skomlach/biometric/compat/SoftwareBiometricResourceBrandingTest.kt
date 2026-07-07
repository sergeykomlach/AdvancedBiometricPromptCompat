package dev.skomlach.biometric.compat

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SoftwareBiometricResourceBrandingTest {

    @Test
    fun softwareBiometricStringsDoNotExposeTechnicalNames() {
        val offenders = listOf(
            File("../biometric-custom-face-tf/src/main/res"),
            File("../biometric-zkfinger/src/main/res"),
            File("../biometric-custom-voice/src/main/res"),
            File("../biometric-custom-behavior/src/main/res"),
            File("../biometric/src/main/res")
        )
            .flatMap { root ->
                root.walkTopDown()
                    .filter { it.isFile && it.name == "strings.xml" }
                    .toList()
            }
            .flatMap { file ->
                Regex(
                    ">([^<]*(FaceTF|ZK|WideGamut|DeviceCredential|CryptoObject|PCM|precomputed|embedding|Biometric API)[^<]*)<",
                    RegexOption.IGNORE_CASE
                )
                    .findAll(file.readText())
                    .map { match ->
                        "${file.path}: ${match.groupValues[1].trim()}"
                    }
                    .toList()
            }

        assertTrue(
            "User-facing biometric strings should use generic terminology:\n${offenders.joinToString("\n")}",
            offenders.isEmpty()
        )
    }
}
