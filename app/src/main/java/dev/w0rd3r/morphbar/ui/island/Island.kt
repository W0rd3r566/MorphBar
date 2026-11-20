// Adapted from Islet by Dylan Neve (Apache-2.0) for MorphBar, 2026: package migration.
package dev.w0rd3r.morphbar.ui.island

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import dev.w0rd3r.morphbar.core.CutoutGeometry
import dev.w0rd3r.morphbar.core.IsletSettings
import dev.w0rd3r.morphbar.data.IslandContent
import dev.w0rd3r.morphbar.data.IslandPhase
import dev.w0rd3r.morphbar.data.IslandUiState
import dev.w0rd3r.morphbar.data.NoticeAction
import dev.w0rd3r.morphbar.data.NoticeSnapshot

/**
 * Transparent breathing room around the island. The overlay window is
 * WRAP_CONTENT, and a window surface resize trails its content by a frame
 * during a spring; with a margin that trailing edge clips transparent pixels
 * instead of the island itself. The service subtracts this from the window's
 * y so the pill still lands exactly on the cutout.
 */
val IslandMargin: Dp = 12.dp

/**
 * Kept small on purpose: the window's y is `cutoutTop - bleed - IslandTopMargin`,
 * and a large top margin would push that negative on devices whose cutout sits
 * only a few pixels below the top edge.
 */
val IslandTopMargin: Dp = 2.dp

private val ExpandedCorner = 28.dp

data class IslandActions(
    val onTap: () -> Unit = {},
    val onCollapse: () -> Unit = {},
    val onExpand: () -> Unit = {},
    val onPlayPause: () -> Unit = {},
    val onNext: () -> Unit = {},
    val onPrevious: () -> Unit = {},
    val onSeek: (Float) -> Unit = {},
    val onOpenMedia: () -> Unit = {},
    val onOpenNotice: (NoticeSnapshot) -> Unit = {},
    val onNoticeAction: (NoticeAction) -> Unit = {},
)

@Composable
fun Island(
    state: IslandUiState,
    geometry: CutoutGeometry,
    settings: IsletSettings,
    actions: IslandActions,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current

    val restingWidth = with(density) { geometry.width.toDp() } + (settings.bleedX * 2f).dp
    val restingHeight = with(density) { geometry.height.toDp() } + (settings.bleedY * 2f).dp
    val displayWidth = with(density) { geometry.displayWidth.toDp() }

    val target = targetSize(state, restingWidth, restingHeight, displayWidth, settings)

    val motion = MaterialTheme.motionScheme
    val width by animateDpAsState(target.width, motion.defaultSpatialSpec(), label = "islandWidth")
    val height by animateDpAsState(target.height, motion.defaultSpatialSpec(), label = "islandHeight")
    val corner by animateDpAsState(
        if (state.phase == IslandPhase.Expanded) ExpandedCorner else target.height / 2f,
        motion.defaultSpatialSpec(),
        label = "islandCorner",
    )

    val accent by animateColorAsState(
        accentFor(state, settings),
        motion.defaultEffectsSpec(),
        label = "islandAccent",
    )

    val shape = RoundedCornerShape(corner)
    val expanded = state.phase == IslandPhase.Expanded

    Box(
        modifier = modifier.padding(
            start = IslandMargin,
            end = IslandMargin,
            top = IslandTopMargin,
            bottom = IslandMargin,
        ),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = Modifier
                .size(width, height)
                .clip(shape)
                .background(if (expanded) Color(0xFF17191D) else Color.Black)
                .then(
                    if (expanded) {
                        Modifier.border(Dp.Hairline, Color.White.copy(alpha = 0.14f), shape)
                    } else {
                        Modifier
                    },
                )
                // Only the compact capsule handles gestures at the container
                // level. A parent pointerInput on expanded cards can conflict
                // with the player's own clickable controls and seek gesture.
                .then(
                    when (state.phase) {
                        IslandPhase.Compact -> Modifier
                            .clickable(onClickLabel = "Раскрыть MorphBar") {
                                if (settings.haptics) {
                                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                                }
                                actions.onTap()
                            }
                            .pointerInput(Unit) {
                                detectVerticalDragGestures { _, dragAmount ->
                                    if (dragAmount > 6f) actions.onExpand()
                                    else if (dragAmount < -6f) actions.onCollapse()
                                }
                            }
                        IslandPhase.Expanded -> Modifier
                        IslandPhase.Resting ->
                            if (settings.tapAtRest) {
                                Modifier.clickable(onClickLabel = "Открыть MorphBar") {
                                    actions.onTap()
                                }
                            } else {
                                Modifier
                            }
                    }
                ),
            contentAlignment = Alignment.TopCenter,
        ) {
            AnimatedContent(
                targetState = IslandBodyKey(state.phase, contentKind(state.content)),
                transitionSpec = {
                    (fadeIn(motion.defaultEffectsSpec()) + scaleIn(motion.defaultSpatialSpec(), 0.88f))
                        .togetherWith(
                            fadeOut(motion.fastEffectsSpec()) + scaleOut(motion.fastSpatialSpec(), 0.92f),
                        )
                },
                label = "islandBody",
            ) { key ->
                when (key.phase) {
                    IslandPhase.Resting -> Box(Modifier.size(restingWidth, restingHeight))
                    IslandPhase.Compact -> CompactBody(
                        state = state,
                        accent = accent,
                        slot = (restingHeight - 4.dp).coerceIn(14.dp, 28.dp),
                    )

                    IslandPhase.Expanded -> ExpandedBody(
                        state = state,
                        accent = accent,
                        actions = actions,
                    )
                }
            }

            if (expanded) {
                // A 48dp close button is independent of media and notification
                // child gesture detectors, unlike the old 56x18dp drag strip.
                IconButton(
                    onClick = actions.onCollapse,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 3.dp, end = 3.dp)
                        .size(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Свернуть MorphBar",
                        tint = Color.White.copy(alpha = 0.82f),
                    )
                }
                // 120x40dp hit area with a visible swipe handle. Swipe up,
                // or tap anywhere in the area, to close the expanded surface.
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .size(width = 120.dp, height = 40.dp)
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onVerticalDrag = { _, dragAmount ->
                                    if (dragAmount < -6f) actions.onCollapse()
                                },
                            )
                        }
                        .clickable(onClickLabel = "Свернуть MorphBar") {
                            actions.onCollapse()
                        },
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        Modifier
                            .padding(bottom = 10.dp)
                            .size(width = 40.dp, height = 4.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.35f)),
                    )
                }
            }
        }

        // This diagnostic is meaningful only while the overlay remains at
        // the physical camera cutout. Active cards are moved under SystemUI.
        if (settings.alignmentGuide && state.phase == IslandPhase.Resting) {
            AlignmentGuide(geometry, restingWidth, restingHeight)
        }
    }
}

private data class IslandBodyKey(val phase: IslandPhase, val kind: Int)

private fun contentKind(content: IslandContent): Int = when (content) {
    IslandContent.Empty -> 0
    is IslandContent.Media -> 1
    is IslandContent.Notice -> 2
    is IslandContent.Power -> 3
}

private fun targetSize(
    state: IslandUiState,
    restingWidth: Dp,
    restingHeight: Dp,
    displayWidth: Dp,
    settings: IsletSettings,
): DpSize = when (state.phase) {
    IslandPhase.Resting -> DpSize(restingWidth, restingHeight)

    IslandPhase.Compact -> DpSize(
        maxOf(settings.compactWidth.dp, restingWidth + 32.dp).coerceAtMost(94.dp),
        maxOf(restingHeight + 2.dp, 30.dp).coerceAtMost(38.dp),
    )

    IslandPhase.Expanded -> DpSize(
        (displayWidth * settings.expandedWidthFraction)
            .coerceAtMost(332.dp)
            .coerceAtMost(displayWidth - 28.dp),
        when (val content = state.content) {
            is IslandContent.Media -> 252.dp
            is IslandContent.Notice ->
                if (content.notice.actions.isEmpty()) 146.dp else 184.dp

            is IslandContent.Power -> 124.dp
            IslandContent.Empty -> maxOf(restingHeight + 2.dp, 26.dp)
        },
    )
}

/**
 * Album art gives the island its colour when there is art to take it from;
 * otherwise it falls back to the wallpaper palette, so the thing always looks
 * like it belongs to this phone rather than to us.
 */
@Composable
private fun accentFor(state: IslandUiState, settings: IsletSettings): Color {
    val fallback = MaterialTheme.colorScheme.primary
    val raw = when (val content = state.content) {
        is IslandContent.Media -> content.media.accent.takeIf { settings.albumArtAccent }
        is IslandContent.Notice -> content.notice.accent
        is IslandContent.Power -> null
        IslandContent.Empty -> null
    }
    return raw?.let { legibleOnBlack(Color(it)) } ?: fallback
}

/**
 * An app's brand colour is chosen to sit on white. Dropped onto a black pill a
 * navy or maroon vanishes, so lift anything too dark towards white until it
 * clears a readable luminance.
 */
private fun legibleOnBlack(color: Color): Color {
    var result = color
    var guard = 0
    while (result.luminance() < 0.30f && guard < 8) {
        result = Color(
            red = result.red + (1f - result.red) * 0.22f,
            green = result.green + (1f - result.green) * 0.22f,
            blue = result.blue + (1f - result.blue) * 0.22f,
        )
        guard++
    }
    return result
}
