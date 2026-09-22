package dev.skomlach.biometric.compat

import dev.skomlach.biometric.compat.custom.SoftwarePromptStatus
import org.junit.Assert.*
import org.junit.Test

/** Exercise the exact JVM entry points called by providers compiled against the three-field API. */
class SoftwarePromptStatusCompatibilityTest {
    @Test fun oldJavaConstructorRemainsCallable() {
        val constructor = SoftwarePromptStatus::class.java.getConstructor(CharSequence::class.java,
            CharSequence::class.java, Boolean::class.javaPrimitiveType)
        val status = constructor.newInstance("one", "two", true)
        assertTrue(status.terminal)
        assertFalse(status.persistent)
    }

    @Test fun oldKotlinDefaultConstructorRemainsCallable() {
        val constructor = SoftwarePromptStatus::class.java.constructors.single { it.parameterCount == 5 }
        val status = constructor.newInstance("one", null, false, 6, null) as SoftwarePromptStatus
        assertEquals("one", status.asLegacyHelpMessage())
        assertFalse(status.terminal)
        assertFalse(status.persistent)
    }

    @Test fun oldKotlinCopyDefaultRetainsNewInstructionPolicy() {
        val original = SoftwarePromptStatus("one", "two", persistent = true)
        val method = SoftwarePromptStatus::class.java.declaredMethods.single {
            it.name == "copy\$default" && it.parameterCount == 6
        }
        val copied = method.invoke(null, original, "replacement", null, false, 6, null) as SoftwarePromptStatus
        assertEquals("replacement\ntwo", copied.asLegacyHelpMessage())
        assertTrue(copied.persistent)
    }
}
