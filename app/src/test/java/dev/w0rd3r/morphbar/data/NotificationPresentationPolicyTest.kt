package dev.w0rd3r.morphbar.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPresentationPolicyTest {
    @Test fun highImportanceIsNotMirroredByDefault() {
        assertFalse(NotificationPresentationPolicy.shouldMirror(4, true))
        assertFalse(NotificationPresentationPolicy.shouldMirror(5, true))
    }

    @Test fun defaultImportanceStillWorks() {
        assertTrue(NotificationPresentationPolicy.shouldMirror(3, true))
        assertTrue(NotificationPresentationPolicy.shouldMirror(2, true))
    }

    @Test fun userCanOptInToAllNotifications() {
        assertTrue(NotificationPresentationPolicy.shouldMirror(4, false))
        assertTrue(NotificationPresentationPolicy.shouldMirror(5, false))
    }

    @Test fun unknownRankingDoesNotSilentlyDropAlerts() {
        assertTrue(NotificationPresentationPolicy.shouldMirror(null, true))
    }
}
