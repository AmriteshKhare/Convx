package com.convx.music.ui.component

import android.animation.ValueAnimator
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import com.convx.music.ui.utils.Motion
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private const val WAVE_MS = 550

/**
 * Gap after a lyric line that opens while the list scrolls forward, so lines
 * further down trail the active one instead of moving as a block.
 * [itemIndex] and [anchorItemIndex] are lazy-list indices.
 */
@Composable
internal fun LyricWaveSpacer(
    state: LazyListState,
    itemIndex: Int,
    anchorItemIndex: Int,
    enabled: Boolean,
) {
    var gap by remember { mutableFloatStateOf(0f) }
    val animatedGap = animateFloatAsState(
        targetValue = gap,
        animationSpec = if (gap == 0f) {
            spring(stiffness = 105f, dampingRatio = 1f, visibilityThreshold = 0.5f)
        } else {
            tween(WAVE_MS, easing = Motion.LyricEasing)
        },
        label = "lyric_wave_gap",
    )

    LaunchedEffect(anchorItemIndex, enabled) {
        gap = 0f
        if (!enabled || !ValueAnimator.areAnimatorsEnabled() || itemIndex < anchorItemIndex - 1) return@LaunchedEffect
        val info = state.layoutInfo
        val visible = info.visibleItemsInfo
        val anchor = visible.firstOrNull { it.index == anchorItemIndex } ?: return@LaunchedEffect
        val distance = lyricCenterDistance(info.viewportStartOffset, info.viewportEndOffset, anchor.offset, anchor.size)
        if (distance <= 0f) return@LaunchedEffect
        val weight = (1f - (itemIndex - visible.first().index).toFloat() / visible.size).coerceIn(0f, 1f)
        delay((WAVE_MS * (1f - weight)).toLong())
        gap = distance / visible.size * weight
        delay((WAVE_MS / 1.95f * weight).toLong())
        gap = 0f
    }

    Box(
        Modifier
            .fillMaxWidth()
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(placeable.width, animatedGap.value.roundToInt().coerceAtLeast(0)) {}
            },
    )
}
