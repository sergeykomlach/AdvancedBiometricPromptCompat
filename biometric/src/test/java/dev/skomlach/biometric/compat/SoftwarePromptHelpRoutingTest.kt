package dev.skomlach.biometric.compat

import dev.skomlach.biometric.compat.custom.SoftwareBiometricPromptHost
import dev.skomlach.biometric.compat.custom.SoftwarePromptStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class SoftwarePromptHelpRoutingTest {
    @Test
    fun statusFallbackBridgeComposesPrimaryAndSecondaryTextForLegacyHelpConsumers() {
        var helpMessage: CharSequence? = null
        val callbacks = object : SoftwareBiometricPromptHost.Callbacks {
            override fun onHelp(message: CharSequence) {
                helpMessage = message
            }

            override fun onReady(extras: android.os.Bundle?) = Unit

            override fun onFailure(result: AuthenticationResult) = Unit

            override fun isPromptActive(): Boolean = true
        }

        callbacks.onStatus(
            SoftwarePromptStatus(
                primaryText = "Repeat the same code phrase again. Attempt 2 of 3.",
                secondaryText = "The phrase was too short. Repeat the same phrase again.",
                terminal = false
            )
        )

        assertEquals(
            "Repeat the same code phrase again. Attempt 2 of 3.\nThe phrase was too short. Repeat the same phrase again.",
            helpMessage
        )
    }

    @Test
    fun statusFallbackBridgeDoesNotAppendBlankSecondaryText() {
        var helpMessage: CharSequence? = null
        val callbacks = object : SoftwareBiometricPromptHost.Callbacks {
            override fun onHelp(message: CharSequence) {
                helpMessage = message
            }

            override fun onReady(extras: android.os.Bundle?) = Unit

            override fun onFailure(result: AuthenticationResult) = Unit

            override fun isPromptActive(): Boolean = true
        }

        callbacks.onStatus(
            SoftwarePromptStatus(
            primaryText = "Repeat the same code phrase again. Attempt 2 of 3.",
                secondaryText = "",
                terminal = true
            )
        )

        assertEquals("Repeat the same code phrase again. Attempt 2 of 3.", helpMessage)
    }
}
