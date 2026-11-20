package dev.w0rd3r.morphbar.data

import org.junit.Assert.*
import org.junit.Test

class ArtworkRequestIdentityTest {
    @Test fun lateUriChangesTrackArtworkIdentity() {
        val a = ArtworkRequestIdentity.key("p", "song", "artist", 1000, listOf(""), 0)
        val b = ArtworkRequestIdentity.key("p", "song", "artist", 1000, listOf("content://cover/42"), 0)
        assertNotEquals(a, b)
    }
    @Test fun mediaNotificationArtworkCanInvalidateArtwork() {
        val a = ArtworkRequestIdentity.key("p", "song", "artist", 1000, listOf(""), 0)
        val b = ArtworkRequestIdentity.key("p", "song", "artist", 1000, listOf(""), 123)
        assertNotEquals(a, b)
    }
    @Test fun localUriOnly() {
        assertTrue(ArtworkRequestIdentity.isTrustedLocalUri("content://media/12"))
        assertTrue(ArtworkRequestIdentity.isTrustedLocalUri("android.resource://package/drawable/id"))
        assertFalse(ArtworkRequestIdentity.isTrustedLocalUri("https://example.com/art.jpg"))
        assertFalse(ArtworkRequestIdentity.isTrustedLocalUri("file:///etc/secret"))
        assertFalse(ArtworkRequestIdentity.isTrustedLocalUri(""))
    }
}
