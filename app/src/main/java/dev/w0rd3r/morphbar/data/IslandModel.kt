// Adapted from Islet by Dylan Neve (Apache-2.0) for MorphBar, 2026: package migration.
package dev.w0rd3r.morphbar.data

import android.app.PendingIntent
import android.graphics.Bitmap
import android.os.SystemClock

/** How much of itself the island is currently showing. */
enum class IslandPhase {
    /** Exactly the cutout. Invisible on an OLED Pixel — that is the point. */
    Resting,

    /** Cutout plus a glyph on either side. */
    Compact,

    /** Full card hanging below the cutout. */
    Expanded,
}

data class MediaSnapshot(
    val packageName: String,
    val appLabel: String,
    val title: String,
    val artist: String,
    val artwork: Bitmap?,
    val accent: Int?,
    val playing: Boolean,
    val positionMs: Long,
    val positionUpdatedAtUptime: Long,
    val speed: Float,
    val durationMs: Long,
    val canNext: Boolean,
    val canPrevious: Boolean,
    val canSeek: Boolean,
) {
    /** Interpolated playback position, so the progress bar moves between callbacks. */
    fun livePositionMs(now: Long = SystemClock.elapsedRealtime()): Long {
        if (!playing || positionUpdatedAtUptime <= 0L) return positionMs
        val delta = ((now - positionUpdatedAtUptime) * speed).toLong()
        return (positionMs + delta).coerceIn(0L, if (durationMs > 0) durationMs else Long.MAX_VALUE)
    }

    fun progress(now: Long = SystemClock.elapsedRealtime()): Float =
        if (durationMs <= 0L) 0f else (livePositionMs(now).toFloat() / durationMs).coerceIn(0f, 1f)
}

data class NoticeAction(
    val title: String,
    val intent: PendingIntent?,
    val isReply: Boolean,
)

data class NoticeSnapshot(
    val key: String,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val smallIcon: Bitmap?,
    val largeIcon: Bitmap?,
    val accent: Int?,
    val contentIntent: PendingIntent?,
    val actions: List<NoticeAction>,
    val postedAtUptime: Long = SystemClock.elapsedRealtime(),
)

data class PowerSnapshot(
    val level: Int,
    val charging: Boolean,
    val changedAtUptime: Long = SystemClock.elapsedRealtime(),
)

/** What the island is showing right now, after priority resolution. */
sealed interface IslandContent {
    data object Empty : IslandContent
    data class Media(val media: MediaSnapshot) : IslandContent
    data class Notice(val notice: NoticeSnapshot) : IslandContent
    data class Power(val power: PowerSnapshot) : IslandContent
}

data class IslandUiState(
    val phase: IslandPhase = IslandPhase.Resting,
    val content: IslandContent = IslandContent.Empty,
    /** Content shown in the compact trailing slot while [content] is a transient peek. */
    val background: IslandContent = IslandContent.Empty,
    val visible: Boolean = true,
)
