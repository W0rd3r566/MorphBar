// Adapted from Islet by Dylan Neve (Apache-2.0) for MorphBar, 2026: package migration.
package dev.w0rd3r.morphbar.ui.island

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.Shape
import kotlin.math.sin

/**
 * Four bars that breathe while something is playing and settle flat when it is
 * not. Deliberately synthetic rather than a real [android.media.audiofx.Visualizer]:
 * a live FFT would mean asking for the microphone permission, which is a
 * ridiculous price for a decoration.
 */
@Composable
fun Equalizer(
    playing: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    bars: Int = 4,
) {
    val transition = rememberInfiniteTransition(label = "equalizer")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_100, easing = { it }),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    )
    val settle by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (playing) 1f else 0f,
        animationSpec = tween(320),
        label = "settle",
    )

    Canvas(modifier) {
        val gap = size.width / (bars * 2f - 1f)
        val barWidth = gap
        val radius = CornerRadius(barWidth / 2f, barWidth / 2f)
        repeat(bars) { index ->
            val wave = (sin(phase + index * 1.35f) + 1f) / 2f
            val amplitude = 0.22f + 0.78f * wave * settle
            val height = (size.height * amplitude).coerceAtLeast(barWidth)
            drawRoundRect(
                color = color,
                topLeft = Offset(index * (barWidth + gap), (size.height - height) / 2f),
                size = Size(barWidth, height),
                cornerRadius = radius,
            )
        }
    }
}

/** Album art, or a tasteful stand-in when the player gave us nothing. */
@Composable
fun Artwork(
    bitmap: Bitmap?,
    accent: Color,
    size: Dp,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
) {
    Box(
        modifier = modifier.size(size).clip(shape).background(accent.copy(alpha = 0.22f)),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                painter = BitmapPainter(bitmap.asImageBitmap()),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size),
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(size * 0.55f),
            )
        }
    }
}

/** The app's small icon, tinted onto a soft accent chip. */
@Composable
fun AppGlyph(
    bitmap: Bitmap?,
    accent: Color,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(percent = 34))
            .background(accent.copy(alpha = 0.24f)),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                painter = BitmapPainter(bitmap.asImageBitmap()),
                contentDescription = null,
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(accent),
                modifier = Modifier.size(size * 0.62f),
            )
        }
    }
}

internal fun Long.asClock(): String {
    if (this <= 0L) return "0:00"
    val totalSeconds = this / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes >= 60) {
        val hours = minutes / 60
        "%d:%02d:%02d".format(hours, minutes % 60, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
