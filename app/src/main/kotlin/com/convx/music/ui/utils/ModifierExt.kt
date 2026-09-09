package com.convx.music.ui.utils

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Indication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Flamingo's player-chrome compositing: draw the content into a single layer over what is
 * behind it, blended with `BlendMode.Plus`.
 *
 * This is how a player takes its chrome colour off the artwork without extracting a palette
 * at all. The seek bar, the transport glyphs and the time text are drawn plain white at a
 * low alpha; the Plus pass adds them on top so the chrome gets *brighter* exactly where the
 * background is dark, letting the artwork's own vivid hue glow through. A palette pass
 * answers the same question with a decode, a quantiser and a frame of lag; this answers it
 * per-pixel, for free, and never disagrees with the pixels it is sitting on.
 *
 * Additive only makes sense over something dark and photographic. Over a flat light surface
 * every white it touches clips, so [enabled] is false on the plain themed background and the
 * chrome falls back to ordinary tinting.
 *
 * Set alpha *after* this modifier (or inside a `graphicsLayer`), never before: alpha applied
 * first is folded into the layer this saves, and the add then happens at full strength.
 */
// A saturation/brightness colorFilter on the Plus paint is a no-op here: content
// drawn under additiveChrome is a flat white/gray icon tint (R == G == B), and a
// saturation matrix leaves any true-gray pixel unchanged by definition -- there is
// no hue in the source layer to boost. What's actually visible after Plus is the
// *backdrop's* hue, brightened; the only way to make that read stronger without
// touching the backdrop itself is to add it again. Two Plus passes accumulate like
// a second exposure: already-saturated (bright) pixels are already clipped and
// don't move, but everything short of that ceiling gets pushed further, widening
// the glow before it clips to white.
private const val AdditiveChromePasses = 2

fun Modifier.additiveChrome(enabled: Boolean = true): Modifier =
    if (!enabled) this else drawWithCache {
        val plusPaint = Paint().apply { blendMode = BlendMode.Plus }
        val bounds = Rect(0f, 0f, size.width, size.height)
        onDrawWithContent {
            val canvas = drawContext.canvas
            repeat(AdditiveChromePasses) {
                canvas.saveLayer(bounds, plusPaint)
                drawContent()
                canvas.restore()
            }
        }
    }

/**
 * Springy "wobble" on press for an existing [interactionSource]: the target scales down
 * while held and overshoots back on release (low damping = bouncy). Use on components that
 * already own an interaction source — nav bar items, glass buttons/pills — where
 * [bounceClick] can't wrap the click. Icon/label only; doesn't affect layout.
 */
fun Modifier.pressWobble(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.86f,
) = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = 0.38f,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "pressWobble",
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/*
 * The two click modifiers below used to add a spring scale-down on press. That is
 * gone. It was built on `composed {}`, which the Compose compiler cannot skip and
 * which defeats modifier reuse, and each instance additionally allocated a
 * MutableInteractionSource, started a collectIsPressedAsState flow collector,
 * started an animateFloatAsState, and added a graphicsLayer — per item, rebuilt
 * every time a row recycled. With ~200 call sites, nearly all of them inside lazy
 * lists, that was the app's largest per-frame scroll cost. The reference app this
 * one is compared against uses plain combinedClickable and has no `composed {}`
 * anywhere.
 *
 * These stay as named wrappers rather than being deleted so the ~200 call sites,
 * and the option of reintroducing the press feedback, both stay in one place. To
 * bring the bounce back, implement it as a Modifier.Node — not with `composed`.
 */

/** Clickable with no ripple by default. */
fun Modifier.bounceClick(
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    indication: Indication? = null,
    onClick: () -> Unit
): Modifier = clickable(
    interactionSource = interactionSource,
    indication = indication,
    enabled = enabled,
    onClick = onClick
)

/** [combinedClickable] with no ripple by default. */
fun Modifier.combinedBounceClick(
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    indication: Indication? = null,
    onLongClick: (() -> Unit)? = null,
    onDoubleClick: (() -> Unit)? = null,
    onClick: () -> Unit
): Modifier = combinedClickable(
    interactionSource = interactionSource,
    indication = indication,
    enabled = enabled,
    onLongClick = onLongClick,
    onDoubleClick = onDoubleClick,
    onClick = onClick
)

/**
 * Apple's specular edge: a hairline that catches light along the top of a surface and
 * has faded out by its equator, so a sheet, pill or button reads as a raised material
 * instead of a flat fill.
 *
 * Traced from [shape]'s own outline rather than a `border()`, so it follows continuous
 * (superellipse) corners exactly instead of approximating them with circular arcs. Drawn
 * over the content, which is what makes it read as light on the surface rather than a
 * line under it.
 *
 * This is the *plain* rim. Surfaces that already sample the backdrop through
 * `Modifier.liquidGlass` get theirs from the glass system's own `Highlight` instead —
 * do not stack both.
 */
fun Modifier.rimLight(
    shape: Shape,
    color: Color = Color.White,
    /** Alpha at the very top edge. Fades to nothing by halfway down. */
    strength: Float = 0.55f,
    width: Dp = 1.dp,
): Modifier = drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val stroke = Stroke(width = width.toPx())
    val brush = Brush.verticalGradient(
        0f to color.copy(alpha = strength),
        0.5f to color.copy(alpha = strength * 0.12f),
        1f to Color.Transparent,
    )
    onDrawWithContent {
        drawContent()
        // Plus rather than the default alpha blend: same additiveChrome boost as the
        // nav bar's icons, so the hairline takes its glow off the surface it sits on
        // instead of just laying flat white over it.
        drawOutline(
            outline = outline,
            brush = brush,
            style = stroke,
            blendMode = BlendMode.Plus,
        )
    }
}

/**
 * Dark counterpart to the specular rim: a thin black outline traced on the
 * surface's own edge, drawn WITHOUT clipping so half the stroke sits outside
 * the bounds — the outermost layer, wider than the light rim, at 180° to it
 * in spirit (dark where the light isn't). Reads as an outline that keeps
 * glass legible on any surface, light or photographic.
 *
 * Normal alpha blend, not Plus: Plus adds zero for black, so a Plus black rim
 * would be a no-op. Applied by [Modifier.liquidGlass] itself; do not stack
 * manually on glass surfaces.
 */
fun Modifier.outerDarkRim(
    shape: Shape,
    width: Dp = 1.6.dp,
    alpha: Float = 0.32f,
): Modifier = drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val stroke = Stroke(width = width.toPx())
    val color = Color.Black.copy(alpha = alpha)
    onDrawWithContent {
        drawContent()
        drawOutline(
            outline = outline,
            color = color,
            style = stroke,
        )
    }
}
/**
 * The radius an inner element needs for its curve to stay concentric with the [outer]
 * one it sits [inset] inside. Apple's rule, and the reason a card inside a sheet looks
 * wrong at the same radius as the sheet: parallel curves must differ by the gap between
 * them. Floors at 4dp so a large inset cannot square the corners off entirely.
 */
fun concentricRadius(outer: Dp, inset: Dp): Dp = (outer - inset).coerceAtLeast(4.dp)
