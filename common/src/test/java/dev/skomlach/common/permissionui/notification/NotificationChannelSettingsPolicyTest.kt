package dev.skomlach.common.permissionui.notification

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationChannelSettingsPolicyTest {

    @Test
    fun unknownOneUiVersionKeepsChannelId() {
        assertTrue(shouldIncludeNotificationChannelId(""))
    }

    @Test
    fun oneUi60KeepsChannelId() {
        assertTrue(shouldIncludeNotificationChannelId("6.0"))
    }

    @Test
    fun oneUi61OmitsChannelId() {
        assertFalse(shouldIncludeNotificationChannelId("6.1"))
    }

    @Test
    fun newerOneUiOmitsChannelId() {
        assertFalse(shouldIncludeNotificationChannelId("7.0"))
    }

    @Test
    fun twoDigitMinorVersionComparesNumerically() {
        assertFalse(shouldIncludeNotificationChannelId("6.10"))
    }

    @Test
    fun malformedOneUiVersionKeepsChannelId() {
        assertTrue(shouldIncludeNotificationChannelId("not-a-version"))
    }
}
