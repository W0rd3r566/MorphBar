package dev.w0rd3r.morphbar.data

internal object ArtworkRequestIdentity {
    fun key(
        packageName: String, title: String, artist: String, durationMs: Long,
        uris: List<String>, hintRevision: Long,
    ): String = listOf(packageName, title, artist, durationMs.toString(),
        uris.joinToString("|"), hintRevision.toString()).joinToString("\u0000")

    fun isTrustedLocalUri(raw: String): Boolean {
        val scheme = raw.substringBefore(":", "").lowercase()
        return scheme == "content" || scheme == "android.resource"
    }
}
