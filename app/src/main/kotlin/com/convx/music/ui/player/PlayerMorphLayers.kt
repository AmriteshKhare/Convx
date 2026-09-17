/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */
package com.convx.music.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer

/**
 * The two ends of the mini-to-full morph, recorded rather than drawn.
 *
 * The morph Melox does -- and the one this now does -- is NOT a sheet sliding up over a
 * mini player. Both ends are recorded into a [GraphicsLayer] and neither is drawn where
 * it lives. One overlay, sized to the interpolated container rect, draws the mini
 * player's recording at its own local offsets while it fades, and the WHOLE full player
 * recording scaled down into the same rect. That is why the player appears to shrink into
 * the pill and unfold back out of it, instead of one panel travelling past another.
 *
 * Module-level for the same reason the rects are: the pill lives in the floating nav bar,
 * the sheet lives in the player, the overlay is drawn in between, and none of the three
 * can hand a value to the other two through the tree.
 *
 * Plain fields, not snapshot state -- everything here is read in the DRAW phase, and a
 * snapshot read there would make every write re-invalidate the frame (the trap that
 * `hideWhileArtworkMorphing` was fixed for).
 */
object PlayerMorph {
    /**
     * Master switch, off for now: the shared container/artwork morph read as an ugly,
     * jarring state-change (a black gap at the fraction 0/1 handoff) rather than a smooth
     * transition. [active] short-circuits on this so every caller's own
     * `morphEnabled && PlayerMorph.active` check goes false too, without each of them
     * needing their own flag. Flip back to true to re-enable once the handoff is redone.
     */
    @JvmField var enabled: Boolean = true
    @JvmField var miniLayer: GraphicsLayer? = null
    @JvmField var fullBackgroundLayer: GraphicsLayer? = null
    @JvmField var fullContentLayer: GraphicsLayer? = null

    /** Set by the player; the sheet's own 0..1 drag/settle progress. */
    @JvmField var progressProvider: () -> Float = { 0f }

    /**
     * The sheet's drag entry points, published for the mini player.
     *
     * The pill lives in the nav bar and has no way to reach the sheet's state through the
     * tree, but the mini player can be dragged open, not only tapped -- and a drag is
     * the only way to see the morph at a progress you choose.
     */
    @JvmField var onDragStart: (fromMiniPlayer: Boolean) -> Unit = {}
    @JvmField var onDragDelta: (Float) -> Unit = {}
    @JvmField var onDragEnd: (Float) -> Unit = {}
    @JvmField var onDragCancel: () -> Unit = {}

    val progress: Float get() = progressProvider().coerceIn(0f, 1f)

    /** Mirrors of the sheet state for input ownership (Melox target/isDragging). */
    @JvmField var targetOpen: Boolean = false
    @JvmField var isDragging: Boolean = false
    @JvmField var dragFromMiniPlayer: Boolean = false

    /** Who owns finger input right now; taps elsewhere would fight the spring. */
    internal val inputOwner: PlayerInputOwner
        get() = playerSheetInputOwner(
            targetOpen = targetOpen,
            isDragging = isDragging,
            dragStartedFromMiniPlayer = dragFromMiniPlayer,
            progress = progress,
            sharedLayersReady = sharedLayersReady,
        )

    /** Tap-to-open is only honored while the mini owns input. */
    internal val miniAcceptsInput: Boolean get() = inputOwner == PlayerInputOwner.MINI

    /**
     * True while the shared container owns the drawing.
     *
     * Both ends have to be recorded AND the pill has to have real bounds; without either
     * the overlay cannot draw anything, so the two ends must keep drawing themselves.
     */
    val active: Boolean
        get() {
            if (!enabled) return false
            val fraction = progress
            if (fraction <= 0f || fraction >= 1f) return false
            if (miniLayer == null || fullBackgroundLayer == null || fullContentLayer == null) return false
            val mini = miniPlayerContainerRect ?: return false
            return mini.width > 0f && mini.height > 0f
        }

    /**
     * All three recordings present with a usable pill: the overlay may draw.
     * Melox gates the shared layers on measured-size agreement; the size gate
     * lives at the record sites (degenerate bounds never publish).
     */
    val sharedLayersReady: Boolean
        get() {
            if (!enabled) return false
            if (miniLayer == null || fullBackgroundLayer == null || fullContentLayer == null) return false
            val mini = miniPlayerContainerRect ?: return false
            return mini.width > 0f && mini.height > 0f
        }
}

/**
 * Creates the two recordings and publishes them for the morph's lifetime.
 *
 * Call once, from a composable that outlives both the player and the nav bar --
 * [GraphicsLayer]s are owned by the composition that remembers them and released when it
 * leaves, so creating them any lower would tear them down mid-gesture.
 */
@Composable
fun InstallPlayerMorphLayers() {
    val mini = rememberGraphicsLayer()
    val fullBackground = rememberGraphicsLayer()
    val fullContent = rememberGraphicsLayer()
    DisposableEffect(mini, fullBackground, fullContent) {
        PlayerMorph.miniLayer = mini
        PlayerMorph.fullBackgroundLayer = fullBackground
        PlayerMorph.fullContentLayer = fullContent
        onDispose {
            PlayerMorph.miniLayer = null
            PlayerMorph.fullBackgroundLayer = null
            PlayerMorph.fullContentLayer = null
        }
    }
}

/**
 * Records this subtree into [layer], and draws it in place only when [drawInPlace] says
 * to.
 *
 * While the morph is running both ends answer false: the overlay is drawing their
 * recordings inside the shared container instead, and drawing them here as well would
 * leave a second copy sitting at its own resting position.
 */
fun Modifier.recordPlayerLayer(
    layer: () -> GraphicsLayer?,
    drawInPlace: () -> Boolean,
): Modifier = drawWithContent {
    val target = layer()
    if (target == null) {
        drawContent()
        return@drawWithContent
    }
    target.record { this@drawWithContent.drawContent() }
    if (drawInPlace()) {
        target.alpha = 1f
        target.renderEffect = null
        drawLayer(target)
    }
}

/**
 * Hides an element for exactly as long as the shared container is drawing it.
 *
 * Used on the mini player's own artwork: the cover is flown separately by
 * [PlayerArtworkMorphOverlay], so the copy baked into the mini recording has to go, or
 * the two are on screen together for the first quarter of every open.
 */
fun Modifier.hideWhileMorphing(): Modifier = graphicsLayer {
    alpha = if (PlayerMorph.active) 0f else 1f
}
