/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */
package com.convx.music.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.convx.music.ui.component.PLAYER_MINI_FADE_END

/**
 * Q2/Q4 lock: mini + full player as two states of ONE tree, one file.
 *
 * Live-tree flight: the sheet's own nodes travel inside [morphLiveCard]
 * (scale/translate/clip, draw-phase only), content resolves late via
 * [unifiedContentResolve], artwork via [PlayerArtworkMorphOverlay]. No
 * GraphicsLayer recordings anywhere: one progress, one composition, zero
 * re-capture cost, no 0/1 handoff gap class.
 *
 * Rules for anything living in this file:
 * - one [progress] source, draw-phase reads only (graphicsLayer/drawWithContent
 *   lambdas), never a Box sized from progress in composition;
 * - shared logic, not shared nodes: mini and full copies crossfade inside one
 *   component ([MorphSwap]), no sharedElement on text/icons (that nested
 *   overlay-inside-overlay is what made the nav icons readjust);
 * - endpoints pixel-identical: off-flight every modifier below is a passthrough.
 */

/** Full-content resolve start: ghost enters early WITH motion, crisp at rest. */
const val UNIFIED_CONTENT_RESOLVE_START = 0.35f

/** Ghost-to-crisp blur diameter for the resolving full content. */
private val UnifiedContentBlurMaxDp = 8.dp

/** Full-content alpha: invisible until late flight, ease-out resolve to crisp. */
fun unifiedContentAlpha(progress: Float): Float =
    easeOutCubic(
        ((progress - UNIFIED_CONTENT_RESOLVE_START) / (1f - UNIFIED_CONTENT_RESOLVE_START)).coerceIn(0f, 1f),
    )

/**
 * The live card transform: the sheet's own content drawn scaled/translated/
 * clipped into the interpolated mini <-> full container -- no GraphicsLayer
 * recordings, no overlay copies. Entirely draw-phase ([drawWithContent]):
 * progress, the mini rect and the radius are read per frame with zero
 * recomposition, which is what the recording overlay could never do without
 * re-capturing the full screen every frame.
 *
 * Rest behavior untouched: off-flight this is a plain drawContent passthrough.
 */
fun Modifier.morphLiveCard(
    morphEnabled: Boolean,
    expandedCornerRadius: Dp,
): Modifier = drawWithContent drawBlock@{
    if (!morphEnabled || !PlayerMorph.active) {
        drawContent()
        return@drawBlock
    }
    val fraction = PlayerMorph.progress
    val mini = miniPlayerContainerRect
    if (mini == null || mini.width <= 0f || mini.height <= 0f || size.width <= 0f || size.height <= 0f) {
        drawContent()
        return@drawBlock
    }
    val full = Rect(0f, 0f, size.width, size.height)
    val rect = sharedContainerRect(mini, full, fraction)
    val radius = sharedContainerCornerRadius(
        collapsedCornerRadius = mini.height / 2f,
        expandedCornerRadius = expandedCornerRadius.toPx(),
        progress = fraction,
        screenCornerExpansionProgress = 0f,
    )
    val clipPath = Path().apply {
        addRoundRect(RoundRect(rect, CornerRadius(radius, radius)))
    }
    clipPath(clipPath) {
        translate(left = rect.left, top = rect.top) {
            scale(
                scaleX = rect.width / size.width,
                scaleY = rect.height / size.height,
                pivot = Offset.Zero,
            ) {
                this@drawBlock.drawContent()
            }
        }
    }
}

/**
 * Resolve treatment for the sheet's content layer while the live card owns
 * the frame: ghost alpha + resolving blur + 0.94 -> 1 settle, so icons and
 * artist/title visibly animate through the flight instead of popping in at
 * the end. Background stays opaque -- it IS the travelling card. Draw-phase
 * only; [blurMaxPx] is captured once at the call site (composition, static)
 * since graphicsLayer has no Density.
 */
fun Modifier.unifiedContentResolve(
    morphEnabled: Boolean,
    blurMaxPx: Float,
): Modifier = graphicsLayer {
    if (!(morphEnabled && PlayerMorph.active)) return@graphicsLayer
    val p = PlayerMorph.progress
    val window = ((p - UNIFIED_CONTENT_RESOLVE_START) / (1f - UNIFIED_CONTENT_RESOLVE_START)).coerceIn(0f, 1f)
    val ease = easeOutCubic(window)
    alpha = ease
    val settle = 0.94f + 0.06f * ease
    scaleX = settle
    scaleY = settle
    transformOrigin = TransformOrigin(0.5f, 0.5f)
    val blur = blurMaxPx * (1f - window)
    renderEffect = if (blur > 0.5f) BlurEffect(blur, blur) else null
}

/**
 * One morphing piece of content: [miniContent] out early with the card,
 * [fullContent] resolving late as ghost-to-crisp. At rest exactly one copy
 * draws, so adopting this changes no resting pixel.
 */
@Composable
fun MorphSwap(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    miniContent: @Composable BoxScope.() -> Unit,
    fullContent: @Composable BoxScope.() -> Unit,
) {
    Box(modifier) {
        Box(
            modifier = Modifier.graphicsLayer {
                val p = progress().coerceIn(0f, 1f)
                alpha = if (p <= 0f) 1f else 1f - easeOutCubic((p / PLAYER_MINI_FADE_END).coerceIn(0f, 1f))
            },
            content = miniContent,
        )
        Box(
            modifier = Modifier.graphicsLayer {
                val p = progress().coerceIn(0f, 1f)
                alpha = if (p >= 1f) {
                    1f
                } else {
                    easeOutCubic(
                        ((p - UNIFIED_CONTENT_RESOLVE_START) / (1f - UNIFIED_CONTENT_RESOLVE_START)).coerceIn(0f, 1f),
                    )
                }
            },
            content = fullContent,
        )
    }
}

/** Container color travelling mini -> full across the flight. */
fun unifiedContainerColor(mini: Color, full: Color, progress: Float): Color {
    val p = progress.coerceIn(0f, 1f)
    return Color(
        red = lerp(mini.red, full.red, p),
        green = lerp(mini.green, full.green, p),
        blue = lerp(mini.blue, full.blue, p),
        alpha = lerp(mini.alpha, full.alpha, p),
    )
}

/**
 * The single tree: [miniPlayer] and [fullPlayer] composed once, visibility
 * owned by [progress]. Cutover target for [PlayerMorphHost]; until then the
 * overlays own the flight and this stays the reference implementation the
 * recording windows are checked against.
 */
@Composable
fun UnifiedPlayerHost(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    miniPlayer: @Composable BoxScope.() -> Unit,
    fullPlayer: @Composable BoxScope.() -> Unit,
) {
    Box(modifier) {
        Box(
            modifier = Modifier.graphicsLayer {
                val p = progress().coerceIn(0f, 1f)
                alpha = if (p <= 0f) 1f else 1f - easeOutCubic((p / PLAYER_MINI_FADE_END).coerceIn(0f, 1f))
            },
            content = miniPlayer,
        )
        Box(
            modifier = Modifier.graphicsLayer {
                val p = progress().coerceIn(0f, 1f)
                alpha = if (p >= 1f) {
                    1f
                } else {
                    easeOutCubic(
                        ((p - UNIFIED_CONTENT_RESOLVE_START) / (1f - UNIFIED_CONTENT_RESOLVE_START)).coerceIn(0f, 1f),
                    )
                }
            },
            content = fullPlayer,
        )
    }
}
