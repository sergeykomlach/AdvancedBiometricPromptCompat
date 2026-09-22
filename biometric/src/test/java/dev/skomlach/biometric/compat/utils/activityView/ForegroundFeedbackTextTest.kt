package dev.skomlach.biometric.compat.utils.activityView

import dev.skomlach.biometric.compat.custom.SoftwarePromptStatus
import org.junit.Assert.*
import org.junit.Test

class ForegroundFeedbackTextTest {
    @Test fun retainedMessageIsReboundAfterGeometryHideClearsTheCard() {
        val status = SoftwarePromptStatus(primaryText = "Speak now", persistent = true)
        var text: CharSequence? = null
        var writes = 0
        fun render() = bindForegroundFeedbackText(status, text) { text = it; writes++ }
        render()
        assertEquals("Speak now", text.toString())
        render()
        assertEquals(1, writes) // Same id/text must not restart the presentation.
        text = null // Immediate or animated hide after a viewport resize.
        render()
        assertEquals("Speak now", text.toString())
        assertEquals(2, writes)
    }
}
