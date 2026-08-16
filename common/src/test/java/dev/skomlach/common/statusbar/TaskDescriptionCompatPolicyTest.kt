package dev.skomlach.common.statusbar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaskDescriptionCompatPolicyTest {

    @Test
    fun opaqueStatusBarColorPopulatesPrimaryAndBackgroundColors() {
        val statusBarColor = 0xFF123456.toInt()
        val navigationBarColor = 0xFF654321.toInt()

        val colors = resolveTaskDescriptionColors(statusBarColor, navigationBarColor)

        assertEquals(statusBarColor, colors.primaryColor)
        assertEquals(statusBarColor, colors.backgroundColor)
        assertEquals(statusBarColor, colors.statusBarColor)
        assertEquals(navigationBarColor, colors.navigationBarColor)
    }

    @Test
    fun translucentStatusBarColorLeavesOpaqueTaskColorsUnset() {
        val statusBarColor = 0x80123456.toInt()
        val navigationBarColor = 0xFF654321.toInt()

        val colors = resolveTaskDescriptionColors(statusBarColor, navigationBarColor)

        assertNull(colors.primaryColor)
        assertNull(colors.backgroundColor)
        assertEquals(statusBarColor, colors.statusBarColor)
        assertEquals(navigationBarColor, colors.navigationBarColor)
    }
}
