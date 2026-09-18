/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */
package com.convx.music.ui.player

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import com.convx.music.ui.component.PLAYER_CONTENT_APPEAR_END
import com.convx.music.ui.component.PLAYER_CONTENT_APPEAR_START
import com.convx.music.ui.component.PLAYER_LAYER_HANDOFF_PROGRESS
import com.convx.music.ui.component.PLAYER_MINI_FADE_END

/**
 * Where the mini player's own pill sits on screen, in root coordinates.
 *
 * Module-level state for the same reason the artwork rects are: the pill lives in the
 * floating nav bar, the sheet lives in the player, they are far apart in the tree, and
 * only the overlay -- drawn once, in between -- reads it.
 */
private val miniContainerRect = mutableStateOf<Rect?>(null)

internal val miniPlayerContainerRect: Rect? get() = miniContainerRect.value

/**
 * Call on the mini player's outermost pill box. Purely observational: it records the
 * pill's bounds and changes nothing about how the pill renders.
 */
fun Modifier.registerMiniContainerRect(): Modifier = onGloballyPositioned {
    if (!it.isAttached) return@onGloballyPositioned
    val bounds = it.boundsInRoot()
    val previous = miniContainerRect.value
    // A resized pill (layout branch change, rotation) invalidates its last recording --
    // that frame was captured at the old size, not this one. See PlayerMorph.invalidateMiniFrame.
    if (previous != null && previous.size != bounds.size) PlayerMorph.invalidateMiniFrame()
    miniContainerRect.value = bounds
}

/**
 * Mini transport cluster, in root coordinates (Melox miniPlayerControlsBounds).
 *
 * The overlay draws the mini recording twice -- title side riding the
 * travelling corner, controls shifted to keep their right inset -- so both
 * visibly travel instead of crossfading. Controls report per-button and read
 * back the union: variants mount exclusively, so at most one set is live.
 */
private val miniControlsPlayRect = mutableStateOf<Rect?>(null)
private val miniControlsNextRect = mutableStateOf<Rect?>(null)

internal val miniPlayerControlsRect: Rect?
    get() {
        val play = miniControlsPlayRect.value
        val next = miniControlsNextRect.value
        if (play == null) return next
        if (next == null) return play
        return Rect(
            left = minOf(play.left, next.left),
            top = minOf(play.top, next.top),
            right = maxOf(play.right, next.right),
            bottom = maxOf(play.bottom, next.bottom),
        )
    }

/** Clears stale variant bounds when the mini switches layout (tab style, inline). */
internal fun clearMiniContentControlsRects() {
    miniControlsPlayRect.value = null
    miniControlsNextRect.value = null
}

fun Modifier.registerMiniControlsPlayRect(): Modifier = onGloballyPositioned {
    if (it.isAttached) miniControlsPlayRect.value = it.boundsInRoot()
}

fun Modifier.registerMiniControlsNextRect(): Modifier = onGloballyPositioned {
    if (it.isAttached) miniControlsNextRect.value = it.boundsInRoot()
}

/** Mini title/buttons track across the first three tenths, then hand over. */
fun playerSheetMiniContentAlpha(progress: Float): Float =
    1f - easeOutCubic((progress / PLAYER_MINI_FADE_END).coerceIn(0f, 1f))

/** Full-player recording fades in from a tenth to six tenths, ease-in. */
fun playerSheetPageAlpha(progress: Float): Float =
    easeInCubic(
        (
            (progress - PLAYER_CONTENT_APPEAR_START) /
                (PLAYER_CONTENT_APPEAR_END - PLAYER_CONTENT_APPEAR_START)
            ).coerceIn(0f, 1f),
    )

/** Full-player background leads across the first fifth, ease-in. */
fun playerSheetBackgroundAlpha(progress: Float): Float =
    easeInCubic((progress / PLAYER_LAYER_HANDOFF_PROGRESS).coerceIn(0f, 1f))

/**
 * The one surface the whole morph happens inside (Melox PlayerSheetContentOverlay).
 *
 * Positioned at the interpolated container rect -- the pill's bounds at progress 0, the
 * whole screen at 1 -- it draws BOTH recordings:
 *
 * - the mini player's, drawn TWICE with a clip split: the title side pinned to
 *   the source offsets, the controls translated to keep their right inset as
 *   the card grows. Title/artist/buttons visibly TRAVEL instead of fading;
 * - the full player's, scaled by `container width / full width`, appearing
 *   from a tenth to six tenths while the mini hands over.
 *
 * ENTIRELY draw-phase. An earlier version sized a real Box from progress read in
 * composition; it composed once, at progress 0.0004, and never again -- so the container
 * sat at pill size for the whole gesture while the sheet it had replaced was no longer
 * drawing itself either. Reading progress here means the frame that changes it is the
 * frame that redraws this, with no recomposition at all.
 */
@Composable
fun PlayerContainerMorphOverlay(
    progress: () -> Float,
    color: Color,
    expandedCornerRadius: Dp,
) {
    Spacer(
        modifier = Modifier
            .fillMaxSize()
            .drawWithCache {
                val clipPath = Path()
                // This whole block re-runs whenever the overlay's own (screen) size
                // changes -- rotation, window resize -- so it is the natural hook to
                // invalidate the full player's recordings, the same way a mini pill
                // resize invalidates its own (see registerMiniContainerRect).
                PlayerMorph.invalidateFullFrames()
                onDrawBehind {
                    val fraction = progress().coerceIn(0f, 1f)
                    if (fraction <= 0f || fraction >= 1f) return@onDrawBehind
                    if (!PlayerMorph.sharedLayersReady) return@onDrawBehind
                    val mini = miniContainerRect.value ?: return@onDrawBehind
                    // onGloballyPositioned can fire once with a degenerate size before layout
                    // settles.
                    if (mini.width <= 0f || mini.height <= 0f) return@onDrawBehind
                    val miniLayer = PlayerMorph.miniLayer ?: return@onDrawBehind
                    val fullBackgroundLayer = PlayerMorph.fullBackgroundLayer ?: return@onDrawBehind
                    val fullContentLayer = PlayerMorph.fullContentLayer ?: return@onDrawBehind
                    if (fullBackgroundLayer.size.width <= 0 || fullContentLayer.size.width <= 0) return@onDrawBehind

                    // The full player is the recorded screen itself, so its rect IS this
                    // overlay's own size. Nothing to register at that end, nothing to go
                    // stale.
                    val full = Rect(0f, 0f, size.width, size.height)
                    val rect = sharedContainerRect(mini, full, fraction)
                    val radius = sharedContainerCornerRadii(
                        // The nav bar draws the pill as a 50%-rounded capsule, so its radius
                        // is half its height by definition. Derived rather than passed in, so
                        // restyling the pill cannot leave the two out of step.
                        source = SharedContainerCornerRadii.uniform(mini.height / 2f),
                        target = SharedContainerCornerRadii.uniform(expandedCornerRadius.toPx()),
                        progress = fraction,
                    ).topStart

                    clipPath.reset()
                    clipPath.addRoundRect(RoundRect(rect, CornerRadius(radius, radius)))
                    clipPath(clipPath) {
                        // No flat plate (Melox draws no plate either): the mini
                        // recording carries its own live glass for the first
                        // fifth, and the full recording is opaque from a tenth.
                        // A plate would sit behind translucent glass and read dead.
                        translate(left = rect.left, top = rect.top) {
                            if (miniLayer.size.width > 0 && fraction < PLAYER_MINI_FADE_END) {
                                miniLayer.alpha = playerSheetMiniContentAlpha(fraction)
                                miniLayer.renderEffect = null
                                drawTrackedMiniLayer(
                                    miniLayer = miniLayer,
                                    mini = mini,
                                    container = rect,
                                )
                            }
                            // Background leads (first fifth), content follows (tenth
                            // to six tenths): two recordings, staged like Melox.
                            val scale = rect.width / size.width
                            if (fraction > 0f) {
                                fullBackgroundLayer.alpha = playerSheetBackgroundAlpha(fraction)
                                fullBackgroundLayer.renderEffect = null
                                scale(scaleX = scale, scaleY = scale, pivot = Offset.Zero) {
                                    drawLayer(fullBackgroundLayer)
                                }
                            }
                            if (fraction > PLAYER_CONTENT_APPEAR_START) {
                                fullContentLayer.alpha = playerSheetPageAlpha(fraction)
                                fullContentLayer.renderEffect = null
                                scale(scaleX = scale, scaleY = scale, pivot = Offset.Zero) {
                                    drawLayer(fullContentLayer)
                                }
                            }
                        }
                    }
                }
            },
    )
}

/**
 * Mini recording drawn as two tracked halves (Melox clip split, pill-origin
 * layer, inside the caller's translate to the container origin).
 *
 * Title side compensates the corner travel so it stays absolute while the
 * card grows around it; the controls half adds the edge shift, keeping its
 * right inset and breaking away rightward. Falls back to one plain draw
 * until the controls bounds report.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTrackedMiniLayer(
    miniLayer: androidx.compose.ui.graphics.layer.GraphicsLayer,
    mini: Rect,
    container: Rect,
) {
    val controls = miniPlayerControlsRect
    if (controls == null || controls.width <= 0f || controls.height <= 0f) {
        drawLayer(miniLayer)
        return
    }
    // Layer-space x of the split: controls' left edge relative to the pill.
    val split = controls.left - mini.left
    val titleShift = sharedMiniPlayerTitleShift(
        sourcePlayerBounds = mini,
        animatedPlayerBounds = container,
    )
    val edgeShift = sharedMiniPlayerControlsShift(
        sourcePlayerBounds = mini,
        animatedPlayerBounds = container,
        controlsBounds = controls,
    )
    clipRect(right = titleShift + split) {
        withTransform({
            translate(left = titleShift, top = 0f)
        }) {
            drawLayer(miniLayer)
        }
    }
    clipRect(left = titleShift + split + edgeShift) {
        withTransform({
            translate(left = titleShift + edgeShift, top = 0f)
        }) {
            drawLayer(miniLayer)
        }
    }
}
