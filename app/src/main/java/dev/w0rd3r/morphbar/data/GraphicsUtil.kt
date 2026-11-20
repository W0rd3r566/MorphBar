// Adapted from Islet by Dylan Neve (Apache-2.0) for MorphBar, 2026: package migration.
package dev.w0rd3r.morphbar.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.core.graphics.createBitmap
import androidx.palette.graphics.Palette
import kotlin.math.max
import kotlin.math.roundToInt

private const val MAX_ART_EDGE = 192

/**
 * Album art arrives at whatever size the player felt like, sometimes hardware
 * backed. The island draws it at 24–64dp, so downscale aggressively: it keeps
 * the overlay's memory flat and makes Palette fast enough to run inline.
 */
fun Bitmap.toIslandArtwork(): Bitmap? {
    if (width <= 0 || height <= 0) return null
    val software = if (config == Bitmap.Config.HARDWARE) {
        copy(Bitmap.Config.ARGB_8888, false)
    } else {
        this
    } ?: return null

    val longest = max(software.width, software.height)
    if (longest <= MAX_ART_EDGE) return software
    val scale = MAX_ART_EDGE.toFloat() / longest
    return software.scale(
        (software.width * scale).roundToInt().coerceAtLeast(1),
        (software.height * scale).roundToInt().coerceAtLeast(1),
    )
}

private fun Bitmap.scale(w: Int, h: Int): Bitmap = Bitmap.createScaledBitmap(this, w, h, true)

fun Drawable.toSoftwareBitmap(): Bitmap? {
    (this as? BitmapDrawable)?.bitmap?.let { return it.toIslandArtwork() }
    val w = intrinsicWidth.takeIf { it > 0 } ?: return null
    val h = intrinsicHeight.takeIf { it > 0 } ?: return null
    val bitmap = createBitmap(w.coerceAtMost(MAX_ART_EDGE), h.coerceAtMost(MAX_ART_EDGE))
    val canvas = Canvas(bitmap)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bitmap
}

/**
 * Pull one accent out of the artwork. Vibrant first — it is what the eye reads
 * as "the colour of this album" — then fall back through muted and dominant so
 * a monochrome cover still yields something rather than nothing.
 */
fun Bitmap.accentColor(): Int? = runCatching {
    val palette = Palette.from(this).clearFilters().maximumColorCount(16).generate()
    palette.vibrantSwatch?.rgb
        ?: palette.lightVibrantSwatch?.rgb
        ?: palette.darkVibrantSwatch?.rgb
        ?: palette.mutedSwatch?.rgb
        ?: palette.dominantSwatch?.rgb
}.getOrNull()
