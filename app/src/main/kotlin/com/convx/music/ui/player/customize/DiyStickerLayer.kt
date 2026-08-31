/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.ui.player.customize

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.convx.music.ui.ios.compat.Text
import androidx.compose.foundation.shape.RoundedCornerShape
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.convx.music.constants.DiyLayoutKey
import com.convx.music.utils.rememberPreference
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Draws the DIY stickers over whatever is beneath them.
 *
 * The layer is purely decorative: it never takes a touch. Stickers sit between the artwork and
 * the transport controls, so a sticker can cover album art but can never swallow a tap meant for
 * the play button — which is why the whole thing is drawn with no pointer input at all rather
 * than with per-sticker hit-testing.
 *
 * @param zFilter which slice of the stack to draw. Callers render the layer twice — once behind
 *   the artwork for negative z, once in front for the rest — so ordering works without the
 *   player having to restructure itself.
 */
@Composable
fun DiyStickerLayer(
    layout: DiyLayout,
    orientation: DiyOrientation,
    modifier: Modifier = Modifier,
    zFilter: (Int) -> Boolean = { true },
) {
    if (layout.isEmpty) return
    val visible = remember(layout, zFilter) {
        layout.stickers.filter { zFilter(it.z) }.sortedBy { it.z }
    }
    if (visible.isEmpty()) return

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val bounds = DiyBounds(maxWidth, maxHeight)
        visible.forEach { sticker ->
            DiyStickerContent(sticker = sticker, orientation = orientation, bounds = bounds)
        }
    }
}

/** Player-relative geometry a sticker's normalised transform resolves against. */
data class DiyBounds(val width: Dp, val height: Dp) {
    /** Scale is expressed against the shorter edge so a sticker keeps its size across rotations. */
    val referenceEdge: Dp get() = Dp(min(width.value, height.value))
}

/**
 * One sticker, positioned and transformed.
 *
 * Position and rotation live in a `graphicsLayer` so moving a sticker is a draw-time transform:
 * the bitmap is decoded once and never re-rasterised, which is what keeps a ten-sticker player
 * from costing anything per frame.
 */
@Composable
fun DiyStickerContent(
    sticker: DiySticker,
    orientation: DiyOrientation,
    bounds: DiyBounds,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val transform = sticker.transformFor(orientation)
    val sizeDp = bounds.referenceEdge * transform.scale
    val density = LocalDensity.current

    val offsetX = with(density) { (bounds.width * transform.x - sizeDp / 2).toPx() }
    val offsetY = with(density) { (bounds.height * transform.y - sizeDp / 2).toPx() }

    val base = modifier
        .size(sizeDp)
        .graphicsLayer {
            translationX = offsetX
            translationY = offsetY
            rotationZ = transform.rotation
            alpha = sticker.opacity
            if (sticker.flipHorizontal) scaleX = -1f
        }

    when (sticker.kind) {
        DiyStickerKind.EMOJI -> Box(base) {
            Text(
                text = sticker.source,
                textAlign = TextAlign.Center,
                style = TextStyle(fontSize = with(density) { sizeDp.toPx().toSp() * 0.82f }),
                modifier = Modifier.fillMaxSize(),
            )
        }

        DiyStickerKind.IMAGE -> {
            val file = remember(sticker.source) { DiyStore.stickerFile(context, sticker) }
            if (file?.isFile != true) return
            val shape = RoundedCornerShape(percent = (sticker.cornerRadius * 100).toInt())
            AsyncImage(
                model = ImageRequest.Builder(context).data(file).build(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = base
                    .then(if (sticker.shadow) Modifier.shadow(8.dp, shape) else Modifier)
                    .clip(shape),
            )
        }
    }
}

/** Reads the saved layout and keeps it live as the user edits it. */
@Composable
fun rememberDiyLayout(): DiyLayout {
    val (json) = rememberPreference(DiyLayoutKey, defaultValue = "{}")
    return remember(json) { DiyLayout.fromJson(json) }
}

/** True when the player should bother rendering a sticker layer at all. */
@Composable
fun rememberHasDiyStickers(): Boolean = !rememberDiyLayout().isEmpty

/**
 * A sticker layer that resolves against [rootCoordinates]' absolute frame instead of
 * whatever space its own immediate parent hands it.
 *
 * Exists for the z < 0 sticker band specifically: it has to stay nested between a
 * player skin's background and artwork to keep that z-order, so it can't simply move to
 * a topmost overlay slot the way the z >= 0 band did. But classic Player.kt and V17
 * (Player_v2.kt) give that nesting point a different amount of space -- V17 draws its
 * own drag handle and a separate controls row inside the same content area, shrinking
 * what its sticker canvas sees below what classic's (and the DIY editor's, which always
 * previews via classic) does. [rootCoordinates] -- captured once from a plain sibling
 * Box the whole player content shares -- is the one frame both skins can agree on.
 *
 * [requiredSize] forces this layer to [rootCoordinates]' size regardless of its actual
 * (possibly smaller) parent, and the [offset] cancels out wherever that parent placed it
 * so the layer paints at [rootCoordinates]' exact position. One frame of lag while
 * [selfCoordinates] settles on first composition is expected and invisible here -- this
 * is a static overlay, not something read every frame.
 */
@Composable
fun AbsoluteFrameDiyStickers(
    rootCoordinates: LayoutCoordinates?,
    orientation: DiyOrientation,
    layout: DiyLayout,
    zFilter: (Int) -> Boolean,
    modifier: Modifier = Modifier,
) {
    val root = rootCoordinates ?: return
    var selfCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val density = LocalDensity.current
    val rootSizeDp = remember(root.size, density) {
        with(density) { DpSize(root.size.width.toDp(), root.size.height.toDp()) }
    }

    Box(
        modifier = modifier
            .requiredSize(rootSizeDp)
            .onGloballyPositioned { selfCoordinates = it }
            .offset {
                val self = selfCoordinates
                // Both sides can go stale mid-teardown (sheet dismissing, screen rotating) --
                // localPositionOf throws on a detached LayoutCoordinates rather than returning
                // a default, so this has to check first.
                if (self == null || !root.isAttached || !self.isAttached) return@offset IntOffset.Zero
                val delta = root.localPositionOf(self, Offset.Zero)
                IntOffset(-delta.x.roundToInt(), -delta.y.roundToInt())
            },
    ) {
        DiyDesignCanvas(orientation = orientation, modifier = Modifier.fillMaxSize()) {
            DiyStickerLayer(layout = layout, orientation = orientation, zFilter = zFilter)
        }
    }
}
