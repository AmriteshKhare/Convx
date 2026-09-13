/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * Port of React Bits' ElasticSlider (github.com/DavidHDev/react-bits): drag past
 * either end and the track stretches/squashes like rubber instead of just
 * clamping, with the edge icon nudging outward and pulsing, then springs back
 * on release. The web version drives this off Framer Motion's clientX/overflow
 * motion values (`.jump()` while dragging, `animate()` only on release); here
 * that same shape comes from a plain float while dragging and an [Animatable]
 * only for the release bounce, since Compose's [Animatable.snapTo] is suspend
 * and drag callbacks below aren't.
 */

package com.convx.music.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.convx.music.ui.component.shapes.ContinuousCapsule
import kotlinx.coroutines.launch
import kotlin.math.exp

/** How far the track can visually stretch past its own bounds, no matter how
 *  far past the edge the finger actually goes — [decay] softly caps toward
 *  this without ever quite reaching it. */
private val MaxOverflow = 28.dp

private enum class ElasticRegion { LEFT, MIDDLE, RIGHT }

/**
 * Volume-style slider (no thumb, a filling capsule track) with the React Bits
 * ElasticSlider drag-past-the-edge feel. Icons are supplied by the caller so
 * this stays agnostic to which glyphs a volume/brightness/whatever control uses.
 */
@Composable
fun ElasticVolumeSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
    activeColor: Color,
    inactiveColor: Color,
    restingTrackHeight: Dp = 6.dp,
    activeTrackHeight: Dp = 12.dp,
    leftIcon: @Composable () -> Unit,
    rightIcon: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val maxOverflowPx = with(density) { MaxOverflow.toPx() }

    var trackWidthPx by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var region by remember { mutableStateOf(ElasticRegion.MIDDLE) }

    // Tracks the finger 1:1 while dragging (mutableFloatStateOf, not
    // Animatable — this needs a synchronous write from a non-suspend drag
    // callback). Only the release bounce-back is a real spring animation.
    var liveOverflowPx by remember { mutableFloatStateOf(0f) }
    var releasing by remember { mutableStateOf(false) }
    val releaseOverflow = remember { Animatable(0f) }
    val overflowPx = if (releasing) releaseOverflow.value else liveOverflowPx

    val scale by animateFloatAsState(
        targetValue = if (dragging) 1.12f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "elasticVolumeScale",
    )
    val trackHeight by animateDpAsState(
        targetValue = if (dragging) activeTrackHeight else restingTrackHeight,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "elasticVolumeTrackHeight",
    )

    // One-shot pulse when a hand crosses INTO an overflow region — a nudge on
    // arrival, not a permanent bulge held for as long as it stays there.
    val leftPulse = remember { Animatable(1f) }
    val rightPulse = remember { Animatable(1f) }
    LaunchedEffect(region) {
        when (region) {
            ElasticRegion.LEFT -> {
                leftPulse.snapTo(1f)
                leftPulse.animateTo(1.4f, tween(120))
                leftPulse.animateTo(1f, tween(130))
            }
            ElasticRegion.RIGHT -> {
                rightPulse.snapTo(1f)
                rightPulse.animateTo(1.4f, tween(120))
                rightPulse.animateTo(1f, tween(130))
            }
            ElasticRegion.MIDDLE -> Unit
        }
    }

    fun onMove(rawX: Float) {
        releasing = false
        region = regionFor(rawX, trackWidthPx)
        liveOverflowPx = decay(overflowPast(rawX, trackWidthPx), maxOverflowPx)
        if (trackWidthPx > 0f) onValueChange((rawX / trackWidthPx).coerceIn(0f, 1f))
    }

    fun onRelease() {
        dragging = false
        region = ElasticRegion.MIDDLE
        val startFrom = liveOverflowPx
        liveOverflowPx = 0f
        releasing = true
        scope.launch {
            releaseOverflow.snapTo(startFrom)
            releaseOverflow.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium))
            releasing = false
        }
        onValueChangeFinished?.invoke()
    }

    Row(
        modifier = modifier.graphicsLayer { scaleX = scale; scaleY = scale },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ElasticIcon(pulse = leftPulse, pullPx = if (region == ElasticRegion.LEFT) -overflowPx else 0f) {
            leftIcon()
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .height(activeTrackHeight)
                .onSizeChanged { trackWidthPx = it.width.toFloat() }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures(
                        onPress = {
                            dragging = true
                            onMove(it.x)
                            tryAwaitRelease()
                            onRelease()
                        }
                    )
                }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { dragging = true },
                        onDragEnd = { onRelease() },
                        onDragCancel = { onRelease() },
                    ) { change, _ -> onMove(change.position.x) }
                },
            contentAlignment = Alignment.Center,
        ) {
            // Squash+stretch: scaleX widens with overflow, scaleY thins to sell
            // the rubber-band feel, transform origin anchored at whichever end
            // is being pulled so the OTHER end stays put.
            val stretchOrigin = if (region == ElasticRegion.RIGHT) 0f else 1f
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(trackHeight)
                    .clip(ContinuousCapsule())
                    .background(inactiveColor)
                    .graphicsLayer {
                        if (size.width > 0f) {
                            scaleX = 1f + overflowPx / size.width
                        }
                        scaleY = 1f - (overflowPx / maxOverflowPx) * 0.2f
                        transformOrigin = TransformOrigin(stretchOrigin, 0.5f)
                    }
            ) {
                val filled = trackWidthPx * value.coerceIn(0f, 1f)
                if (filled > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(trackHeight)
                            .drawBehind {
                                drawRect(activeColor, size = Size(filled, size.height))
                            }
                    )
                }
            }
        }

        ElasticIcon(pulse = rightPulse, pullPx = if (region == ElasticRegion.RIGHT) overflowPx else 0f) {
            rightIcon()
        }
    }
}

@Composable
private fun ElasticIcon(pulse: Animatable<Float, AnimationVector1D>, pullPx: Float, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.graphicsLayer {
            scaleX = pulse.value
            scaleY = pulse.value
            translationX = pullPx
        }
    ) {
        content()
    }
}

private fun regionFor(rawX: Float, widthPx: Float): ElasticRegion = when {
    rawX < 0f -> ElasticRegion.LEFT
    rawX > widthPx -> ElasticRegion.RIGHT
    else -> ElasticRegion.MIDDLE
}

private fun overflowPast(rawX: Float, widthPx: Float): Float = when {
    rawX < 0f -> -rawX
    rawX > widthPx -> rawX - widthPx
    else -> 0f
}

/** Same soft-cap the reference uses: a sigmoid that approaches [max] but never
 *  reaches it, so the elastic pull always has somewhere left to give. */
private fun decay(value: Float, max: Float): Float {
    if (max == 0f) return 0f
    val entry = value / max
    val sigmoid = 2 * (1 / (1 + exp(-entry)) - 0.5f)
    return sigmoid * max
}
