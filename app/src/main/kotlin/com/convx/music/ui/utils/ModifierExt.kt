package com.convx.music.ui.utils

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Indication
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

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
        // Recorded once and composited twice, rather than one saveLayer per pass.
        // The result is the same pixels (dst + L + L), at half the render targets --
        // and they leave the window's own render pass, which a saveLayer splits in
        // two. Home was spending ~1.6ms/frame on 15 such splits, each drawing one
        // icon-sized rect, because every chrome glyph here wraps itself in two.
        val layer = obtainGraphicsLayer().apply { blendMode = BlendMode.Plus }
        val layerSize = IntSize(
            size.width.toInt().coerceAtLeast(1),
            size.height.toInt().coerceAtLeast(1),
        )
        onDrawWithContent {
            layer.record(layerSize) { this@onDrawWithContent.drawContent() }
            repeat(AdditiveChromePasses) { drawLayer(layer) }
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

private const val BouncePressedScale = 0.94f

private object BounceIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        BounceIndicationNode(interactionSource)

    override fun equals(other: Any?): Boolean = other === this
    override fun hashCode(): Int = 0x426F756E
}

private class BounceIndicationNode(
    private val interactionSource: InteractionSource,
) : Modifier.Node(), DrawModifierNode {
    private val scaleAnimation = Animatable(1f, 0.001f)
    private val activePresses = mutableSetOf<PressInteraction.Press>()
    private var interactionJob: Job? = null
    private var animationJob: Job? = null

    private fun animatePressed(pressed: Boolean) {
        animationJob?.cancel()
        animationJob = coroutineScope.launch {
            scaleAnimation.animateTo(
                targetValue = if (pressed) BouncePressedScale else 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                    visibilityThreshold = 0.001f,
                ),
            )
        }
    }

    override fun onAttach() {
        interactionJob = coroutineScope.launch {
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> {
                        activePresses += interaction
                        animatePressed(true)
                    }
                    is PressInteraction.Release -> {
                        activePresses -= interaction.press
                        animatePressed(activePresses.isNotEmpty())
                    }
                    is PressInteraction.Cancel -> {
                        activePresses -= interaction.press
                        animatePressed(activePresses.isNotEmpty())
                    }
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        val scaleFactor = scaleAnimation.value
        if (scaleFactor == 1f) {
            drawContent()
        } else {
            scale(scaleFactor, pivot = center) { this@draw.drawContent() }
        }
    }

    override fun onDetach() {
        interactionJob?.cancel()
        animationJob?.cancel()
        interactionJob = null
        animationJob = null
        activePresses.clear()
    }
}

/** Clickable with no ripple by default. */
fun Modifier.bounceClick(
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    indication: Indication? = null,
    onClick: () -> Unit
): Modifier = clickable(
    interactionSource = interactionSource,
    indication = if (enabled) BounceIndication else indication,
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
    indication = if (enabled) BounceIndication else indication,
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
 * the bounds — the outermost layer, wider than the light rim.
 *
 * Mirrors the light rim's wide-on-edge / slim-at-ends read on TWO sides: the
 * ring is drawn twice, once per diagonal axis, each pass black at both ends
 * fading to nothing at its middle — so every side gets a dark outer edge
 * that swells and slims around the shape instead of a flat uniform line.
 *
 * Normal alpha blend, not Plus: Plus adds zero for black, so a Plus black rim
 * would be a no-op. Applied by [Modifier.liquidGlass] itself; do not stack
 * manually on glass surfaces.
 */
fun Modifier.outerDarkRim(
    shape: Shape,
    width: Dp = 1.6.dp,
    alpha: Float = 0.32f,
    /** Light rim's angle, degrees — the dark axis is derived from it. */
    angle: Float = 45f,
): Modifier = drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val stroke = Stroke(width = width.toPx())
    // One gradient per diagonal axis (canvas coords, y down — same convention
    // as the highlight shader's cos/sin normal). Each is black at both ends
    // fading out at its middle, so the two passes swell on opposite sides.
    val center = Offset(size.width / 2f, size.height / 2f)
    fun flankBrush(axisDeg: Float): Brush {
        val a = axisDeg * (PI / 180f).toFloat()
        val dir = Offset(cos(a), sin(a))
        // Matched so the shape's own corners land on the black ends.
        val reach = dir * (abs(dir.x) * size.width + abs(dir.y) * size.height) / 2f
        return Brush.linearGradient(
            0f to Color.Black.copy(alpha = alpha),
            0.5f to Color.Transparent,
            1f to Color.Black.copy(alpha = alpha),
            start = center - reach,
            end = center + reach,
        )
    }
    val firstBrush = flankBrush(angle)
    val secondBrush = flankBrush(angle + 90f)
    onDrawWithContent {
        drawContent()
        drawOutline(outline = outline, brush = firstBrush, style = stroke)
        drawOutline(outline = outline, brush = secondBrush, style = stroke)
    }
}
/**
 * The radius an inner element needs for its curve to stay concentric with the [outer]
 * one it sits [inset] inside. Apple's rule, and the reason a card inside a sheet looks
 * wrong at the same radius as the sheet: parallel curves must differ by the gap between
 * them. Floors at 4dp so a large inset cannot square the corners off entirely.
 */
fun concentricRadius(outer: Dp, inset: Dp): Dp = (outer - inset).coerceAtLeast(4.dp)
