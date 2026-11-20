// Adapted from Islet by Dylan Neve (Apache-2.0) for MorphBar, 2026: package migration and branding.
package dev.w0rd3r.morphbar.ui.island

import android.os.SystemClock
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.w0rd3r.morphbar.core.CutoutGeometry
import dev.w0rd3r.morphbar.data.IslandContent
import dev.w0rd3r.morphbar.data.IslandUiState
import kotlinx.coroutines.delay

private val Ink = Color.White
private val InkMuted = Color.White.copy(alpha = 0.66f)

// --- compact ----------------------------------------------------------------

@Composable
internal fun CompactBody(
    state: IslandUiState,
    accent: Color,
    slot: Dp,
) {
    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        when (val content = state.content) {
            is IslandContent.Media -> {
                Artwork(
                    bitmap = content.media.artwork,
                    accent = accent,
                    size = slot,
                    shape = CircleShape,
                )
                Equalizer(
                    playing = content.media.playing,
                    color = accent,
                    modifier = Modifier.width(slot * 0.78f).height(slot * 0.62f),
                )
            }

            is IslandContent.Power -> {
                Icon(
                    imageVector = Icons.Rounded.BatteryChargingFull,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(slot),
                )
                Text(
                    text = "${content.power.level}%",
                    color = Ink,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            is IslandContent.Notice -> {
                AppGlyph(content.notice.smallIcon, accent, slot)
                Box(
                    Modifier
                        .size(slot * 0.34f)
                        .drawBehind { drawCircle(accent) },
                )
            }

            IslandContent.Empty -> Unit
        }
    }
}

// --- expanded ---------------------------------------------------------------

@Composable
internal fun ExpandedBody(
    state: IslandUiState,
    accent: Color,
    actions: IslandActions,
) {
    when (val content = state.content) {
        is IslandContent.Media -> MediaExpanded(content, accent, actions)
        is IslandContent.Notice -> NoticeExpanded(content, accent, actions)
        is IslandContent.Power -> PowerExpanded(content, accent)
        IslandContent.Empty -> Box(Modifier.fillMaxSize())
    }
}

@Composable
private fun MediaExpanded(
    content: IslandContent.Media,
    accent: Color,
    actions: IslandActions,
) {
    val media = content.media
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    // Keep a local preview while the user drags the native Material 3 slider.
    // Remote MediaSession updates arrive asynchronously; an optimistic preview
    // prevents the thumb from jumping backwards immediately after release.
    var dragging by remember(media.packageName, media.title, media.durationMs) {
        mutableStateOf(false)
    }
    var previewFraction by remember(media.packageName, media.title, media.durationMs) {
        mutableFloatStateOf(0f)
    }
    var awaitingSeek by remember(media.packageName, media.title, media.durationMs) {
        mutableStateOf<Float?>(null)
    }
    LaunchedEffect(awaitingSeek) {
        if (awaitingSeek != null) {
            delay(1_200)
            awaitingSeek = null
        }
    }
    val displayFraction = when {
        dragging -> previewFraction
        awaitingSeek != null -> awaitingSeek!!
        else -> media.progress(now)
    }.coerceIn(0f, 1f)
    val displayPosition = if (dragging || awaitingSeek != null) {
        (media.durationMs * displayFraction).toLong()
    } else {
        media.livePositionMs(now)
    }

    LaunchedEffect(media.playing, media.positionUpdatedAtUptime) {
        while (media.playing) {
            now = SystemClock.elapsedRealtime()
            delay(450)
        }
        now = SystemClock.elapsedRealtime()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 50.dp) // room for the 48dp close button
                .clip(RoundedCornerShape(20.dp))
                .clickable(onClick = actions.onOpenMedia),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(
                bitmap = media.artwork,
                accent = accent,
                size = 54.dp,
                shape = RoundedCornerShape(18.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = media.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Ink,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                )
                Text(
                    text = media.artist.ifBlank { media.appLabel },
                    style = MaterialTheme.typography.bodySmall,
                    color = InkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // Official Material 3 Slider supports press-and-drag, tapping,
        // accessibility semantics, and touch targets (unlike our old
        // detectTapGestures on a 14dp progress indicator).
        if (media.canSeek && media.durationMs > 0L) {
            Slider(
                value = displayFraction,
                onValueChange = { value ->
                    dragging = true
                    previewFraction = value.coerceIn(0f, 1f)
                    awaitingSeek = null
                },
                onValueChangeFinished = {
                    if (dragging) {
                        val to = previewFraction.coerceIn(0f, 1f)
                        dragging = false
                        awaitingSeek = to
                        actions.onSeek(to)
                    }
                },
                valueRange = 0f..1f,
                modifier = Modifier.fillMaxWidth().height(42.dp),
                colors = SliderDefaults.colors(
                    thumbColor = accent,
                    activeTrackColor = accent,
                    inactiveTrackColor = accent.copy(alpha = 0.24f),
                ),
            )
        } else {
            Box(
                modifier = Modifier.fillMaxWidth().height(42.dp),
                contentAlignment = Alignment.Center,
            ) {
                LinearWavyProgressIndicator(
                    progress = { displayFraction },
                    modifier = Modifier.fillMaxWidth().height(10.dp),
                    color = accent,
                    trackColor = accent.copy(alpha = 0.20f),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(displayPosition.asClock(), color = InkMuted, fontSize = 11.sp)
            Text(media.durationMs.asClock(), color = InkMuted, fontSize = 11.sp)
        }

        Spacer(Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = actions.onPrevious, enabled = media.canPrevious) {
                Icon(
                    Icons.Rounded.SkipPrevious,
                    contentDescription = "Previous",
                    tint = if (media.canPrevious) Ink else InkMuted.copy(alpha = 0.3f),
                    modifier = Modifier.size(26.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            FilledIconButton(
                onClick = actions.onPlayPause,
                modifier = Modifier.size(48.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = accent,
                    contentColor = Color.Black,
                ),
            ) {
                Icon(
                    imageVector = if (media.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (media.playing) "Pause" else "Play",
                    modifier = Modifier.size(26.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            IconButton(onClick = actions.onNext, enabled = media.canNext) {
                Icon(
                    Icons.Rounded.SkipNext,
                    contentDescription = "Next",
                    tint = if (media.canNext) Ink else InkMuted.copy(alpha = 0.3f),
                    modifier = Modifier.size(26.dp),
                )
            }
        }
    }
}

@Composable
private fun NoticeExpanded(
    content: IslandContent.Notice,
    accent: Color,
    actions: IslandActions,
) {
    val notice = content.notice
    Column(
        modifier = Modifier
            .fillMaxSize()
            .clickable(enabled = notice.contentIntent != null) { actions.onOpenNotice(notice) }
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(end = 46.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppGlyph(notice.smallIcon, accent, 34.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = notice.appLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = notice.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Text(
            text = notice.text,
            style = MaterialTheme.typography.bodySmall,
            color = InkMuted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )

        if (notice.actions.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                notice.actions.take(3).forEach { action ->
                    TextButton(onClick = { actions.onNoticeAction(action) }) {
                        Text(
                            text = action.title,
                            color = accent,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PowerExpanded(content: IslandContent.Power, accent: Color) {
    val power = content.power
    Row(
        modifier = Modifier.fillMaxSize().padding(start = 24.dp, end = 58.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.BatteryChargingFull,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(34.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column {
            Text(
                text = if (power.charging) "Заряжается" else "Отключено",
                style = MaterialTheme.typography.titleMedium,
                color = Ink,
            )
            Text(
                text = "${power.level}% заряда",
                style = MaterialTheme.typography.bodySmall,
                color = InkMuted,
            )
        }
    }
}

// --- calibration ------------------------------------------------------------

/**
 * A one-pixel outline of exactly what the platform told us the cutout is, drawn
 * on top of the island. If the magenta box does not sit on the camera, the
 * nudge sliders in settings have something concrete to correct.
 */
@Composable
internal fun AlignmentGuide(
    geometry: CutoutGeometry,
    restingWidth: Dp,
    restingHeight: Dp,
) {
    Box(
        modifier = Modifier
            .size(restingWidth, restingHeight)
            .drawBehind {
                val stroke = 2f
                drawRect(
                    color = Color.Magenta,
                    topLeft = Offset.Zero,
                    size = Size(size.width, size.height),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke),
                )
                drawLine(
                    color = Color.Cyan,
                    start = Offset(size.width / 2f, -18f),
                    end = Offset(size.width / 2f, size.height + 18f),
                    strokeWidth = stroke,
                )
            },
    )
}
