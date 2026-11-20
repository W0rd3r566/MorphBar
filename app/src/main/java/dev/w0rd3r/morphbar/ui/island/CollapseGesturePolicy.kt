package dev.w0rd3r.morphbar.ui.island

/**
 * Applies the same threshold to fast and slow swipes. Per-frame deltas
 * must be accumulated by the caller over the full gesture.
 */
internal object CollapseGesturePolicy {
    fun shouldCollapse(totalVerticalDragPx: Float, thresholdPx: Float): Boolean =
        thresholdPx > 0f &&
            totalVerticalDragPx.isFinite() &&
            thresholdPx.isFinite() &&
            totalVerticalDragPx <= -thresholdPx
}
