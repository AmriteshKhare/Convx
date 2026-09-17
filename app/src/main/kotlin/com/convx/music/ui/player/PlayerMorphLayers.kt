/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */
package com.convx.music.ui.player

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.jvm.JvmField

/**
 * The two ends of the mini-to-full morph, live -- not recorded.
 *
 * The morph is NOT a sheet sliding up over a mini player. One progress value
 * ([progressProvider], the sheet's own 0..1 drag/settle progress) drives the
 * sheet's own nodes travelling inside the interpolated card ([morphLiveCard]),
 * the artwork flight ([PlayerArtworkMorphOverlay]) and the late content
 * resolve ([unifiedContentResolve]). No GraphicsLayer recordings anywhere, so
 * there is no stale capture and no 0/1 handoff gap class.
 *
 * Module-level for the same reason the rects are: the pill lives in the
 * floating nav bar, the sheet lives in the player, the flight reads both.
 *
 * Plain fields, not snapshot state -- everything here is read in the DRAW phase, and a
 * snapshot read there would make every write re-invalidate the frame (the trap that
 * `hideWhileArtworkMorphing` was fixed for).
 */
object PlayerMorph {
    /**
     * Master switch. [active] short-circuits on this so every caller's own
     * `morphEnabled && PlayerMorph.active` check goes false too, without each of them
     * needing their own flag.
     */
    @JvmField var enabled: Boolean = true

    /** Set by the player; the sheet's own 0..1 drag/settle progress. */
    @JvmField var progressProvider: () -> Float = { 0f }

    /**
     * The sheet's drag entry points, published for the mini player.
     *
     * The pill lives in the nav bar and has no way to reach the sheet's state through the
     * tree, but the mini player can be dragged open, not only tapped -- and a drag is
     * the only way to see the morph at a progress you choose.
     */
    @JvmField var onDragDelta: (Float) -> Unit = {}
    @JvmField var onDragEnd: (Float) -> Unit = {}
    @JvmField var onDragCancel: () -> Unit = {}

    val progress: Float get() = progressProvider().coerceIn(0f, 1f)

    /**
     * True while the live card owns the drawing.
     *
     * The pill has to have real bounds; without them the card has nothing to
     * grow out of, so both ends keep drawing themselves.
     */
    val active: Boolean
        get() {
            if (!enabled) return false
            val fraction = progress
            if (fraction <= 0f || fraction >= 1f) return false
            val mini = miniPlayerContainerRect ?: return false
            return mini.width > 0f && mini.height > 0f
        }
}

/**
 * Hides an element for exactly as long as the live card is travelling.
 *
 * Used on the mini player's own artwork: the cover is flown separately by
 * [PlayerArtworkMorphOverlay], so the in-place copy has to go, or the two are
 * on screen together for the first part of every open.
 */
fun Modifier.hideWhileMorphing(): Modifier = graphicsLayer {
    alpha = if (PlayerMorph.active) 0f else 1f
}
