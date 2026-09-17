/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */
package com.convx.music.ui.player

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import kotlin.jvm.JvmField
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Size as CoilSize
import com.convx.music.constants.PlayerArtworkStyle
import com.convx.music.constants.PlayerArtworkStyleKey
import com.convx.music.constants.PlayerLook
import com.convx.music.constants.PlayerLookKey
import com.convx.music.ui.component.PLAYER_MINI_FADE_END
import com.convx.music.utils.rememberEnumPreference
import com.convx.music.ui.utils.resize

/**
 * Registered bounds of the mini player's own artwork and the full player's own artwork,
 * in root/window coordinates -- updated from `onGloballyPositioned` on each, read once
 * by [PlayerArtworkMorphOverlay].
 *
 * Plain module-level state, not a hoisted parameter, for the same reason
 * `SharedArtwork.kt`'s `placedArtwork` map is plain state: the two artworks live in
 * composables that are far apart in the tree (`MiniPlayer.kt`'s collapsed content,
 * `Thumbnail.kt`'s expanded content, orchestrated from `BottomSheet.kt` in between) and
 * neither needs to compose off the other's position -- only the overlay, drawn once,
 * reads both.
 */
private val miniArtworkRect = mutableStateOf<Rect?>(null)
private val fullArtworkRect = mutableStateOf<Rect?>(null)

/**
 * Call on the mini player's own artwork box. Purely observational: records where the
 * artwork is on screen and changes nothing about how the mini player itself renders.
 */
/**
 * Corner radius of each end's cover, in px.
 *
 * The flight used to clip to [CircleShape] the whole way, so a rounded-square pill cover
 * snapped to a circle the instant it left the bar and snapped back to the player's own
 * shape on arrival. The radius has to travel with the rect.
 *
 * A vinyl or clover player artwork registers half its size here, i.e. a circle; the
 * clover's petals are not reproduced mid-flight, which reads as a circle growing into a
 * clover at the very end.
 */
private val miniArtworkRadius = androidx.compose.runtime.mutableFloatStateOf(0f)
private val fullArtworkRadius = androidx.compose.runtime.mutableFloatStateOf(0f)

/**
 * Q6 lock: V2's pause-shrink (0.729x) frozen while the morph owns the frame.
 *
 * Plain field, draw-phase only (same contract as [PlayerMorph]'s own fields):
 * written from the artwork's graphicsLayer when at rest, read by the overlay
 * as part of its measured target. The flight therefore lands on the live
 * shrunk size instead of snapping full→shrunk at the handoff.
 */
@JvmField var fullArtworkRestScale: Float = 1f

fun Modifier.registerMiniArtworkRect(cornerRadiusPx: Float = 0f): Modifier = onGloballyPositioned {
    miniArtworkRadius.floatValue = cornerRadiusPx
    if (it.isAttached) miniArtworkRect.value = it.boundsInRoot()
}

/** Call on the full player's own artwork container. Same contract as
 *  [registerMiniArtworkRect]. */
fun Modifier.registerFullArtworkRect(cornerRadiusPx: Float = 0f): Modifier = onGloballyPositioned {
    // The player's artwork lives in a pager, so every neighbouring page reports bounds
    // too -- and the last one to report would win. Only the page actually on screen may
    // claim the target rect; an off-screen page's window bounds are empty.
    if (it.boundsInWindow().isEmpty) return@onGloballyPositioned
    fullArtworkRadius.floatValue = cornerRadiusPx
    if (it.isAttached) fullArtworkRect.value = it.boundsInRoot()
}

/**
 * Draws ONE artwork image growing from the mini player's registered rect to the full
 * player's, as [progress] runs 0..1.
 *
 * Sized to the FULL (target) rect, not the mini one -- scaling DOWN from there to the
 * mini size at fraction 0, then back up to 1:1 (its native size) at the handoff. This
 * is the opposite of the first version, which sized to the small mini rect and scaled
 * UP: scaling up stretches whatever resolution the underlying image happens to be,
 * which read as soft mid-growth even after requesting a larger decode, because the
 * COMPOSE LAYOUT SIZE (what a bitmap gets stretched across) was still the tiny mini
 * box the whole time. Sizing to full and scaling down means the on-screen image is
 * never asked to be bigger than the pixels it actually has -- downsampling stays
 * sharp at any fraction, which is exactly the technique Melox's own
 * `PlayerSheetArtworkOverlay` uses (see the scratchpad clone read for this fix).
 *
 * `transformOrigin` pinned to the top-left (not the layer's default center) so a
 * plain `translationX/Y = rect.left/top` places the SCALED result exactly at
 * [sharedArtworkRect]'s interpolated rect, with no extra center-offset math needed.
 *
 * A fresh [AsyncImage] of the same URL, not a captured GraphicsLayer of the mini
 * player's own rendering (Melox's technique) -- both artworks show identical content,
 * so a pixel capture buys nothing a positioned image doesn't, and Coil's cache is
 * already warm from the mini player's own load.
 *
 Its corner radius travels with it, from the pill cover's to whatever the player's own
 * artwork uses (a card's radius, or half its size for the round vinyl), so the cover
 * grows into the real shape instead of snapping to a circle on the way.
 */
@Composable
fun PlayerArtworkMorphOverlay(
    thumbnailUrl: String?,
    progress: () -> Float,
) {
    // Q5 lock: only a squared card travels. Hero/bleed/vinyl/clover shapes never
    // lerp rects between mismatched geometry -- they shrink+fade in place.
    val (playerLook) = rememberEnumPreference(PlayerLookKey, defaultValue = PlayerLook.SQUARED)
    val (artworkStyle) = rememberEnumPreference(PlayerArtworkStyleKey, defaultValue = PlayerArtworkStyle.CARD)
    val travelEnabled = playerLook == PlayerLook.SQUARED && artworkStyle == PlayerArtworkStyle.CARD
    val mini = miniArtworkRect.value
    val full = fullArtworkRect.value
    if (mini == null || full == null || thumbnailUrl == null) return
    // Tied to the container morph, not to raw progress: on a layout with no pill to fly
    // out of (classic nav bar, rail, tab view) the sheet still just slides, and a cover
    // flying across it would be flying out of nowhere.
    if (!PlayerMorph.active) return
    // Both rects are registered from onGloballyPositioned, which can fire once with
    // a degenerate (zero or negative) size before the real layout pass settles --
    // measured a crash on a Samsung device: coil3.size.Dimension throws on any px
    // <= 0, and full.width/height fed CoilSize directly with no floor.
    if (mini.width <= 0f || mini.height <= 0f || full.width <= 0f || full.height <= 0f) return

    if (!travelEnabled) {
        FancyArtworkFadeOverlay(
            thumbnailUrl = thumbnailUrl,
            progress = progress,
        )
        return
    }

    // Q6: shrink the measured target about its own center when paused-shrunk.
    // fullArtworkRestScale only moves at rest, so the flight target is stable.
    val shrink = fullArtworkRestScale.coerceIn(0.1f, 1f)
    val target = if (shrink >= 1f) full else Rect(
        left = full.center.x - full.width * shrink / 2f,
        top = full.center.y - full.height * shrink / 2f,
        right = full.center.x + full.width * shrink / 2f,
        bottom = full.center.y + full.height * shrink / 2f,
    )

    val density = LocalDensity.current
    val boxWidthDp = with(density) { target.width.toDp() }
    val boxHeightDp = with(density) { target.height.toDp() }

    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    target.left.roundToInt(),
                    target.top.roundToInt(),
                )
            }
            .size(boxWidthDp, boxHeightDp)
            .graphicsLayer {
                val fraction = progress().coerceIn(0f, 1f)
                if (fraction <= 0f || fraction >= 1f) {
                    alpha = 0f
                    return@graphicsLayer
                }
                alpha = 1f
                val rect = sharedArtworkRect(mini, target, fraction)
                transformOrigin = TransformOrigin(0f, 0f)
                translationX = rect.left - target.left
                translationY = rect.top - target.top
                val artworkScaleX = rect.width / target.width.coerceAtLeast(1f)
                val artworkScaleY = rect.height / target.height.coerceAtLeast(1f)
                scaleX = artworkScaleX
                scaleY = artworkScaleY
                val radius = androidx.compose.ui.util.lerp(
                    miniArtworkRadius.floatValue,
                    fullArtworkRadius.floatValue * shrink,
                    fraction,
                )
                val localRadius = if (artworkScaleX > 0f) (radius / artworkScaleX).toDp() else radius.toDp()
                shape = RoundedCornerShape(localRadius)
                clip = true
            }
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                // Same URL AND same decode size as every real player artwork
                // (Thumbnail.kt, Player.kt's APPLE_MUSIC layer, the mini pill
                // below) -- Coil then serves the identical cached bitmap at the
                // handoff instead of decoding a second one sized to this box,
                // which was the one-frame pop once the flight ended.
                .data(thumbnailUrl.resize(1200, 1200))
                .size(CoilSize.ORIGINAL)
                .crossfade(false)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(boxWidthDp, boxHeightDp),
        )
    }
}

/**
 * Q5 fancy path: hero/bleed/vinyl/clover never travel. The mini-anchored copy
 * shrinks (1 -> 0.85) and fades across the first quarter; the real full artwork
 * reveals late via [hideFullArtworkWhileMorphing]. Two layers, no rect lerp,
 * no jitter between mismatched shapes.
 */
@Composable
private fun FancyArtworkFadeOverlay(
    thumbnailUrl: String,
    progress: () -> Float,
) {
    val mini = miniArtworkRect.value ?: return
    if (mini.width <= 0f || mini.height <= 0f) return
    val density = LocalDensity.current
    val boxWidthDp = with(density) { mini.width.toDp() }
    val boxHeightDp = with(density) { mini.height.toDp() }
    val miniRadiusDp = with(density) { miniArtworkRadius.floatValue.toDp() }
    Box(
        modifier = Modifier
            .offset { IntOffset(mini.left.roundToInt(), mini.top.roundToInt()) }
            .size(boxWidthDp, boxHeightDp)
            .graphicsLayer {
                val fraction = progress().coerceIn(0f, 1f)
                if (fraction <= 0f || fraction >= PLAYER_MINI_FADE_END) {
                    alpha = 0f
                    return@graphicsLayer
                }
                val fade = easeOutCubic((fraction / PLAYER_MINI_FADE_END).coerceIn(0f, 1f))
                alpha = 1f - fade
                val shrinkScale = 1f - 0.15f * fade
                scaleX = shrinkScale
                scaleY = shrinkScale
                transformOrigin = TransformOrigin.Center
                shape = RoundedCornerShape(miniRadiusDp)
                clip = true
            }
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(thumbnailUrl.resize(1200, 1200))
                .size(CoilSize.ORIGINAL)
                .crossfade(false)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(boxWidthDp, boxHeightDp),
        )
    }
}

/**
 * Full-artwork visibility while the morph owns the frame.
 *
 * Travel mode: overlay carries the cover the whole flight, real copy stays
 * hidden until the handoff. Fancy mode: real copy resolves late
 * (0.65 -> 1, ghost to crisp) behind the mini shrink+fade.
 * Draw-phase reads only -- see [hideWhileArtworkMorphing].
 */
fun Modifier.hideFullArtworkWhileMorphing(travelEnabled: Boolean): Modifier = graphicsLayer {
    val fraction = PlayerMorph.progress
    alpha = if (fraction <= 0f || fraction >= 1f) {
        1f
    } else if (travelEnabled) {
        0f
    } else {
        easeOutCubic(((fraction - PLAYER_CONTENT_RESOLVE_START) / (1f - PLAYER_CONTENT_RESOLVE_START)).coerceIn(0f, 1f))
    }
}
fun Modifier.hideWhileArtworkMorphing(progress: () -> Float): Modifier = graphicsLayer {
    // Lambda, not a Float: this modifier sits deep inside the player's content (under
    // AnimatedContent and a shared-element LookaheadScope on the V2 player). Taking the
    // value meant the caller read the sheet's progress in COMPOSITION, so that whole
    // subtree recomposed on every frame of every open/close drag -- which is what put
    // measure requests on nodes the same frame was deactivating ("measure is called on
    // a deactivated node"). Reading it here keeps it to the draw phase.
    val fraction = progress()
    alpha = if (fraction > 0f && fraction < 1f) 0f else 1f
}
