/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */
package com.convx.music.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
     * Master switch. [active] short-circuits on this so every caller's own
     * `morphEnabled && PlayerMorph.active` check goes false too; off, the sheet just slides.
     */
    @JvmField var enabled: Boolean = true
    @JvmField var miniLayer: GraphicsLayer? = null
    /** The pill's own surface (glass, shadow, fill), recorded apart from its content: the card
     *  starts as this surface stretched to the card rect (Melox draws its mini surface on the
     *  growing card), so mini and player read as ONE object, not a pill swapped for a card. */
    @JvmField var miniSurfaceLayer: GraphicsLayer? = null
    @JvmField var fullBackgroundLayer: GraphicsLayer? = null
    @JvmField var fullContentLayer: GraphicsLayer? = null
    /** The pill's own drop-shadow elevation (px). The card casts it while it is still the
     *  pill, so the shadow is there all the way down instead of popping in on landing. */
    @JvmField var miniShadowElevationPx: Float = 0f

    /**
     * 0..1 multiplier on the docked pill's drop shadow. Snaps to 0 when the morph takes the
     * pill, eases back to 1 after it lands ([PILL_SHADOW_RETURN_MS]): the returning shadow
     * fades in instead of popping on the landing frame. Read in the draw phase only.
     */
    val pillShadow = androidx.compose.animation.core.Animatable(1f)

    /**
     * The close's landing, in sheet-progress units (<= 0 while it runs, 0 at rest).
     *
     * The sheet's lower bound IS the collapsed anchor, so the close spring hits it at full
     * speed and stops dead. The docked pill used to take over there and start its own dip
     * spring: two motions with the speed cut between them -- the "lands, snaps, THEN
     * overshoots". Now the card itself carries on past the slot on this spring, seeded with
     * the exact arrival speed, and springs back; the pill only reappears once it settles.
     */
    val landing = androidx.compose.animation.core.Animatable(0f)

    /** True while [landing] runs: the morph card, not the docked pill, owns the drawing. */
    var landingActive: Boolean by androidx.compose.runtime.mutableStateOf(false)

    /** Card centre travel per unit of progress at the pill end (px); set when a landing starts. */
    @JvmField var landingTravelPx: Float = 0f

    /** Ceiling of the landing dip (px); the dip eases into it rather than clipping. */
    @JvmField var landingMaxDipPx: Float = 0f

    /**
     * How far below its slot the landing card's centre is right now, px (0 at rest). Linear in
     * [landing] near the slot -- so the card keeps exactly the speed it arrived with -- and
     * soft-saturating toward [landingMaxDipPx] for hard flings. Snapshot read: callers in the
     * draw phase redraw every landing frame.
     */
    fun landingDipPx(): Float {
        val overshoot = landing.value
        if (overshoot >= 0f || landingMaxDipPx <= 0f) return 0f
        return landingMaxDipPx * kotlin.math.tanh(-overshoot * landingTravelPx / landingMaxDipPx)
    }

    /**
     * Confirmed-fresh flags (Melox PlayerSheetTransitionState.miniFrameReady /
     * fullBackgroundFrameReady / fullContentFrameReady).
     *
     * [active] used to trust a recording the instant its [GraphicsLayer] ref was
     * non-null and the pill had a measured size -- true from the very first frame of
     * a fast tap-open, before the full player's content had actually been composed,
     * measured, and drawn even once. For that frame the source was already hidden
     * (`drawInPlace = !active`) but the overlay's own zero-size guard on
     * `fullContentLayer` also skipped drawing -- nothing painted, then the next frame
     * caught up all at once. That read as a static mini pill with the real player
     * just fading in, not one shape growing, because the overlay's very first visible
     * frame showed a stale or empty recording instead of the actual content.
     *
     * Set true only after [Modifier.recordPlayerLayer] has actually recorded that
     * layer at a usable size. Never reset: a resize always redraws, and so re-records, the
     * source, whereas a reset with no redraw after it left the morph off for good.
     */
    @JvmField var miniFrameReady: Boolean = false
    @JvmField var fullBackgroundFrameReady: Boolean = false
    @JvmField var fullContentFrameReady: Boolean = false

    /** Set by the player; the sheet's own 0..1 drag/settle progress. Snapshot-backed so
     *  [activeState] re-subscribes when the player installs its provider -- as a plain field
     *  the derived state first ran against the `{ 0f }` placeholder, recorded no
     *  dependencies, and stayed false for good (the morph silently never ran). */
    var progressProvider: () -> Float by androidx.compose.runtime.mutableStateOf({ 0f })

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
    val active: Boolean get() = activeState.value

    /**
     * [active] as a derived snapshot: a draw-phase reader (the recordPlayerLayer `drawInPlace`
     * lambdas, hideWhileMorphing) is invalidated only when the boolean flips, not on every
     * progress tick. Reading raw progress there re-recorded the whole player every frame, and
     * since the overlay animates that recording's alpha HWUI keeps it as a full-screen
     * offscreen layer -- so every frame re-rendered the entire player offscreen (measured
     * 25-110ms RenderThread frames during open/close on the SM-M346B).
     */
    private val activeState = androidx.compose.runtime.derivedStateOf {
        val fraction = progress
        fraction > 0f && fraction < 1f && sharedLayersReady
    }

    /**
     * Glass under the card may skip re-sampling: the card covers it until 0.75, then the
     * capture goes live so the glass is warm when it resolves. Derived, so draw-phase
     * readers (backdrop `frozen` providers) invalidate on the flip only -- reading raw
     * progress there re-invalidated every glass surface on every morph frame.
     */
    val glassFrozen: Boolean get() = glassFrozenState.value

    private val glassFrozenState = androidx.compose.runtime.derivedStateOf {
        activeState.value && progress < com.convx.music.ui.component.PLAYER_GLASS_UNFREEZE_PROGRESS
    }

    /**
     * All three recordings present, confirmed freshly recorded, with a usable pill:
     * the overlay may draw and the sources may hide. Melox gates this on its own
     * per-layer frame-ready flags (see their doc above); a non-null layer with a
     * non-zero pill is necessary but not sufficient -- see [miniFrameReady].
     */
    val sharedLayersReady: Boolean
        get() {
            if (!enabled) return false
            if (miniLayer == null || fullBackgroundLayer == null || fullContentLayer == null) return false
            if (!miniFrameReady || !fullBackgroundFrameReady || !fullContentFrameReady) return false
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
    val miniSurface = rememberGraphicsLayer()
    val fullBackground = rememberGraphicsLayer()
    val fullContent = rememberGraphicsLayer()
    DisposableEffect(mini, miniSurface, fullBackground, fullContent) {
        PlayerMorph.miniLayer = mini
        PlayerMorph.miniSurfaceLayer = miniSurface
        PlayerMorph.fullBackgroundLayer = fullBackground
        PlayerMorph.fullContentLayer = fullContent
        onDispose {
            PlayerMorph.miniLayer = null
            PlayerMorph.miniSurfaceLayer = null
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
 *
 * [markReady] fires after each successful non-zero-size recording (Melox
 * markXFrameRecorded) -- see [PlayerMorph.miniFrameReady]'s doc for why the overlay
 * must not trust a layer before this has fired at least once at the current size.
 */
fun Modifier.recordPlayerLayer(
    layer: () -> GraphicsLayer?,
    drawInPlace: () -> Boolean,
    markReady: () -> Unit = {},
): Modifier = drawWithContent {
    val target = layer()
    if (target == null) {
        drawContent()
        return@drawWithContent
    }
    target.record { this@drawWithContent.drawContent() }
    if (target.size.width > 0 && target.size.height > 0) markReady()
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
