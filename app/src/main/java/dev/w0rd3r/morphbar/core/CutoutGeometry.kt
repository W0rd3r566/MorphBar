// Adapted from Islet by Dylan Neve (Apache-2.0) for MorphBar, 2026: package migration.
package dev.w0rd3r.morphbar.core

import android.content.Context
import android.graphics.Rect
import android.view.Surface
import android.view.WindowInsets
import android.view.WindowManager
import kotlin.math.roundToInt

/**
 * The measured geometry of the display cutout, in raw display pixels.
 *
 * Everything the island does positionally is derived from this. There is
 * deliberately no per-model lookup table: Pixels differ (4a/5/5a/6a punch left,
 * 6/7/8/9/10 punch centre, sizes vary by a few px between generations) and a
 * table would rot the moment a new one ships. [measure] reads the real cutout
 * from the platform instead, so a device that reports a cutout is positioned
 * exactly, and a device that does not gets a sane synthetic pill.
 */
data class CutoutGeometry(
    val detected: Boolean,
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
    val displayWidth: Int,
    val displayHeight: Int,
    val statusBarHeight: Int,
    val rotation: Int,
    val density: Float,
) {
    val centerX: Int get() = left + width / 2
    val bottom: Int get() = top + height

    /**
     * Horizontal distance from the centre of the display to the centre of the
     * cutout. This is the value handed to [android.view.WindowManager.LayoutParams.x]
     * with `Gravity.CENTER_HORIZONTAL`, which is what keeps the island locked to
     * the camera on left-punch Pixels as well as centre-punch ones — and keeps it
     * locked while the island resizes, because the window stays centre-gravity.
     */
    val centerOffsetX: Int get() = centerX - displayWidth / 2

    val isUpright: Boolean get() = rotation == Surface.ROTATION_0

    /** Longest edge of the cutout, used as the resting pill height. */
    val shortSide: Int get() = minOf(width, height)

    fun widthDp(): Float = width / density
    fun heightDp(): Float = height / density

    companion object {
        /** Used when the platform reports no cutout at all (tablets, emulators). */
        private const val FALLBACK_DIAMETER_DP = 26f

        fun measure(context: Context): CutoutGeometry {
            val density = context.resources.displayMetrics.density
            val wm = context.getSystemService(WindowManager::class.java)
            val metrics = wm.currentWindowMetrics
            val bounds: Rect = metrics.bounds
            val displayWidth = bounds.width()
            val displayHeight = bounds.height()

            val insets: WindowInsets = metrics.windowInsets
            val statusBar = insets.getInsets(WindowInsets.Type.statusBars()).top
                .takeIf { it > 0 }
                ?: legacyStatusBarHeight(context, density)

            val rotation = context.display.rotation

            val cutout = insets.displayCutout
            // Android can report the full top exclusion region instead of the tiny
            // physical camera. Never turn that region into a giant black capsule.
            val rawRect = cutout?.let { pickTopRect(it.boundingRects, it.boundingRectTop, displayHeight) }
            val rect = rawRect?.let { raw ->
                val maxCameraSize = (36f * density).roundToInt()
                if (raw.width() <= maxCameraSize && raw.height() <= maxCameraSize) {
                    raw
                } else {
                    // Conservative Pixel-like camera hole. Keep the reported X
                    // position, center vertically inside the status bar.
                    val d = (28f * density).roundToInt()
                    val x = raw.centerX()
                    val y = if (raw.top <= 0 && raw.height() >= statusBar / 2) statusBar / 2 else raw.centerY()
                    Rect(x - d / 2, y - d / 2, x + (d + 1) / 2, y + (d + 1) / 2)
                }
            }

            return if (rect != null && !rect.isEmpty) {
                CutoutGeometry(
                    detected = true,
                    left = rect.left,
                    top = rect.top,
                    width = rect.width(),
                    height = rect.height(),
                    displayWidth = displayWidth,
                    displayHeight = displayHeight,
                    statusBarHeight = statusBar,
                    rotation = rotation,
                    density = density,
                )
            } else {
                val d = (FALLBACK_DIAMETER_DP * density).roundToInt()
                val top = ((statusBar - d) / 2).coerceAtLeast(0)
                CutoutGeometry(
                    detected = false,
                    left = (displayWidth - d) / 2,
                    top = top,
                    width = d,
                    height = d,
                    displayWidth = displayWidth,
                    displayHeight = displayHeight,
                    statusBarHeight = statusBar,
                    rotation = rotation,
                    density = density,
                )
            }
        }

        /**
         * [android.view.DisplayCutout.getBoundingRectTop] is the right answer on
         * every single-hole Pixel. Devices with two holes (or a hole plus a
         * sensor slot) report several rects, so union everything sitting in the
         * top eighth of the display — that is still the camera island, and
         * unioning keeps both lenses covered.
         */
        private fun pickTopRect(all: List<Rect>, boundingTop: Rect, displayHeight: Int): Rect? {
            val topBand = displayHeight / 8
            val candidates = all.filter { !it.isEmpty && it.top < topBand }
            if (candidates.isEmpty()) return boundingTop.takeIf { !it.isEmpty }
            val union = Rect(candidates.first())
            candidates.drop(1).forEach(union::union)
            return union
        }

        private fun legacyStatusBarHeight(context: Context, density: Float): Int {
            @Suppress("DiscouragedApi", "InternalInsetResource")
            val id = context.resources.getIdentifier("status_bar_height", "dimen", "android")
            return if (id > 0) {
                context.resources.getDimensionPixelSize(id)
            } else {
                (24f * density).roundToInt()
            }
        }
    }
}
