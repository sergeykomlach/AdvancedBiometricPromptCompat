package dev.skomlach.biometric.compat.utils.activityView

import dev.skomlach.biometric.compat.custom.SoftwarePromptStatus

/** Geometry/animation can clear text without changing the retained message id. */
internal fun bindForegroundFeedbackText(
    status: SoftwarePromptStatus?, currentText: CharSequence?, bind: (CharSequence) -> Unit
) {
    val expected = status?.asLegacyHelpMessage() ?: return
    if (currentText?.toString() != expected.toString()) bind(expected)
}
