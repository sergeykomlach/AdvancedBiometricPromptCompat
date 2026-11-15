package dev.skomlach.biometric.app.utils

import dev.skomlach.biometric.app.devtools.LogCat
import org.junit.Assert.assertEquals
import org.junit.Test

class MailToTest {
    @Test
    fun exportsCapturedDiagnosticsWithCredentialLinesRedacted() {
        val captured = """
            Start biometric prompt
            Authorization: Bearer fake-authorization
            {"refresh_token":"fake-token"}
            PIN fallback cancelled
        """.trimIndent()

        assertEquals(
            "Start biometric prompt\n[Redacted]\n[Redacted]\nPIN fallback cancelled",
            MailTo.logAttachment(captured)
        )
    }

    @Test
    fun droppingTheBeginningOfASnapshotDoesNotExportAPartialCredentialLine() {
        val oversized = "Password: " + "x".repeat(LogCat.MAX_TEXT_LENGTH) + "\nPrompt closed"

        assertEquals("Prompt closed", MailTo.logAttachment(oversized))
    }
}
