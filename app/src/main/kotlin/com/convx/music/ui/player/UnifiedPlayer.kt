/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */
package com.convx.music.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.util.lerp
import com.convx.music.ui.component.PLAYER_MINI_FADE_END

/**
 * Q2/Q4 lock: mini + full player as two states of ONE tree, one file.
 *
 * The recording overlays ([PlayerMorphHost]) stay the active flight mechanism
 * until this host is cut over on-device: recordings capture pixels, this host
 * keeps live nodes, and the two must agree on windows before the swap. Every
 * window here intentionally mirrors the overlay's ([playerSheetBarAlpha],
 * [playerSheetPageAlpha]) so cutover changes the mechanism, not the motion.
 *
 * Rules for anything living in this file:
 * - one [progress] source, draw-phase reads only (graphicsLayer lambdas),
 *   never a Box sized from progress in composition;
 * - shared logic, not shared nodes: mini and full copies crossfade inside one
 *   component ([MorphSwap]), no sharedElement on text/icons (that nested
 *   overlay-inside-overlay is what made the nav icons readjust);
 * - endpoints pixel-identical: at 0 only the mini copy draws, at 1 only full.
 */

/** Full-content resolve start. Mirrors [PLAYER_CONTENT_RESOLVE_START]. */
const val UNIFIED_CONTENT_RESOLVE_START = 0.65f

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
