package dev.skomlach.biometric.compat.engine.internal

import org.junit.Assert.assertEquals
import org.junit.Test

class EnrollmentRollbackScopeTest {
    @Test fun stagedEnrollmentRollbackRemovesOnlyItsExplicitTag() {
        val templates = mutableSetOf("existing-a", "existing-b")
        val tag = provisionalEnrollment { name -> name }
        templates += tag
        templates.remove(tag)
        assertEquals(setOf("existing-a", "existing-b"), templates)
    }

    @Test fun everyEnrollmentIncludingRetriesGetsItsOwnNonBlankTag() {
        val tags = (1..10).map { provisionalEnrollment { name -> name } }
        org.junit.Assert.assertTrue(tags.all { it.isNotBlank() })
        assertEquals(10, tags.toSet().size)
    }

    @Test fun cancelRemovesOnlyThisAttemptsTemplatesIncludingRetries() {
        val templates = mutableSetOf("existing", "first", "retry")
        val scope = EnrollmentRollbackScope()
        scope.record { templates.remove("first") }
        scope.record { templates.remove("retry") }
        scope.finish(succeeded = false)
        assertEquals(setOf("existing"), templates)
    }

    @Test fun cancelBeforeSuccessCallbackStillRollsBackRecordedTemplate() {
        val templates = mutableSetOf("existing")
        val scope = EnrollmentRollbackScope()
        scope.record { templates.remove("new") }
        templates.add("new") // The manager persists before posting its success callback.
        scope.finish(succeeded = false)
        assertEquals(setOf("existing"), templates)
    }

    @Test fun wholeSetupSuccessCommitsAndIgnoresLaterCancellation() {
        var rollbacks = 0
        val scope = EnrollmentRollbackScope()
        scope.record { rollbacks++ }
        scope.finish(succeeded = true)
        scope.finish(succeeded = false)
        assertEquals(0, rollbacks)
    }

    @Test fun cancellationIsIdempotent() {
        var rollbacks = 0
        val scope = EnrollmentRollbackScope()
        scope.record { rollbacks++ }
        scope.finish(succeeded = false)
        scope.finish(succeeded = false)
        assertEquals(1, rollbacks)
    }

    @Test fun lateRecordCannotEscapeAnAlreadyCanceledScope() {
        var rollbacks = 0
        val scope = EnrollmentRollbackScope()
        scope.finish(succeeded = false)
        scope.record { rollbacks++ }
        assertEquals(1, rollbacks)
    }
}
