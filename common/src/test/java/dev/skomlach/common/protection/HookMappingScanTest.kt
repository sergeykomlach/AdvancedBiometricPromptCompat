package dev.skomlach.common.protection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HookMappingScanTest {
    @Test
    fun detectsOnlyExactExecutableLibraryMapping() {
        val mappings = """
            1000-2000 r-xp 00000000 00:00 1 /data/frida/gmain.so
            2000-3000 r--p 00000000 00:00 1 /data/libfrida-gadget.so
            3000-4000 r-xp 00000000 00:00 1 /data/libfrida-agent-64.so.backup
        """.trimIndent()
        assertEquals(HookMappingScan.COMPLETE, scanHookMappings(mappings.reader().buffered()) { true })
        val detected = "$mappings\n4000-5000 r-xp 00000000 00:00 1 /data/libfrida-agent-64.so (deleted)"
        assertEquals(HookMappingScan.DETECTED, scanHookMappings(detected.reader().buffered()) { true })
    }

    @Test
    fun expiredScanDoesNotReportCompleteCoverage() {
        assertEquals(HookMappingScan.INCOMPLETE, scanHookMappings("".reader().buffered()) { false })
    }

    @Test
    fun detectsAgentInDeletedMemfdMapping() {
        val mapping = "1000-2000 r-xp 00000000 00:00 1 /memfd:frida-agent-64.so (deleted)"
        assertTrue(isExecutableHookMapping(mapping))
        assertFalse(isExecutableHookMapping(mapping.replace(".so", ".so.fixture")))
    }

    @Test
    fun recognizesHookDispatchWithoutBlockingOrdinaryBridgeFrames() {
        val bridge = "de.robv.android.xposed.XposedBridge"
        assertTrue(isHookDispatchFrame(StackTraceElement(bridge, "handleHookedMethod", null, -1)))
        assertFalse(isHookDispatchFrame(StackTraceElement(bridge, "log", null, -1)))
        assertFalse(isHookDispatchFrame(StackTraceElement("app.$bridge", "handleHookedMethod", null, -1)))
    }
}
