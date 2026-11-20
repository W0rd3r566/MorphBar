package dev.w0rd3r.morphbar.ui.island

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CollapseGesturePolicyTest {
    @Test fun slowSwipeAccumulatesAcrossFrames() {
        val slowFrames = listOf(-3f, -4f, -2f, -5f, -6f, -8f)
        assertTrue(CollapseGesturePolicy.shouldCollapse(slowFrames.sum(), 24f))
    }

    @Test fun shortOrDownwardMovesDoNotClose() {
        assertFalse(CollapseGesturePolicy.shouldCollapse(-23.9f, 24f))
        assertFalse(CollapseGesturePolicy.shouldCollapse(25f, 24f))
        assertFalse(CollapseGesturePolicy.shouldCollapse(0f, 24f))
    }

    @Test fun thresholdBoundaryAndInvalidValues() {
        assertTrue(CollapseGesturePolicy.shouldCollapse(-24f, 24f))
        assertFalse(CollapseGesturePolicy.shouldCollapse(Float.NaN, 24f))
        assertFalse(CollapseGesturePolicy.shouldCollapse(-100f, 0f))
    }
}
