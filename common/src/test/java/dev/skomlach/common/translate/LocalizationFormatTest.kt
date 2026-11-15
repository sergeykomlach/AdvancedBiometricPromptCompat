package dev.skomlach.common.translate

import dev.skomlach.common.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], qualifiers = "en-rUS")
class LocalizationFormatTest {
    @Test
    fun collidingArgumentsAreFormattedIndependently() {
        val context = RuntimeEnvironment.getApplication()
        val resource = R.string.biometriccompat_permissions_settings_message
        assertEquals("Aa".hashCode(), "BB".hashCode())

        val first = LocalizationHelper.getLocalizedString(context, resource, "Aa")
        val second = LocalizationHelper.getLocalizedString(context, resource, "BB")

        assertTrue(first.endsWith("Aa"))
        assertTrue(second.endsWith("BB"))
    }
}
