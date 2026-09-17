/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */
package com.convx.music.ui.component.backdrop

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.IntSize
import com.convx.music.ui.component.backdrop.backdrops.LayerBackdrop
import com.convx.music.ui.component.backdrop.backdrops.rememberLayerBackdrop
import com.convx.music.ui.component.backdrop.internal.recordLayer

/**
 * One blurred copy of the screen, shared by every glass surface on it.
 *
 * Each glass surface used to run the whole chain for itself — capture its own slice of
 * the screen, saturate it, blur it, refract it — which is about five offscreen render
 * passes apiece. That is affordable for the three or four surfaces a plain nav bar has;
 * Home carries eight (bar, hidden tab row, puck, search circle, mini player, and the
 * floating Account/mic/shuffle buttons) and the passes are what put the frame over
 * budget, measured at 39 per frame against a 120Hz target.
 *
 * The blur is the shared part: every surface asks for the same radius over the same
 * screen. Doing it once here and letting the surfaces sample the result leaves them
 * only their own refraction, tint and shape — the parts that actually differ. The
 * pixels are identical either way: blurring the whole screen and cutting a piece out
 * is the same as cutting the piece out and blurring it, save for the edges, which the
 * capture padding already covers.
 */
/** Fraction of screen size the shared blur is computed at; the blur hides the upscale. */
const val GlassPoolScale = 0.5f

@Composable
fun rememberGlassPool(): LayerBackdrop = rememberLayerBackdrop()

/**
 * Records [source] into [pooled], with [effects] applied, at [scale] of screen size.
 * Draws nothing itself: put it on a full-screen Box that sits above the content whose
 * blur it is, and it just keeps [pooled] current.
 */
fun Modifier.glassPool(
    source: Backdrop,
    pooled: LayerBackdrop,
    effects: BackdropEffectScope.() -> Unit,
    scale: Float,
): Modifier = this then GlassPoolElement(source, pooled, effects, scale.coerceIn(0.05f, 1f))

private class GlassPoolElement(
    val source: Backdrop,
    val pooled: LayerBackdrop,
    val effects: BackdropEffectScope.() -> Unit,
    val scale: Float,
) : ModifierNodeElement<GlassPoolNode>() {

    override fun create(): GlassPoolNode = GlassPoolNode(source, pooled, effects, scale)

    override fun update(node: GlassPoolNode) {
        node.source = source
        node.pooled = pooled
        node.effects = effects
        node.scale = scale
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "glassPool"
        properties["scale"] = scale
    }

    override fun equals(other: Any?): Boolean =
        other is GlassPoolElement && source == other.source && pooled == other.pooled &&
            scale == other.scale

    override fun hashCode(): Int = 31 * source.hashCode() + pooled.hashCode()
}

private class GlassPoolNode(
    var source: Backdrop,
    var pooled: LayerBackdrop,
    var effects: BackdropEffectScope.() -> Unit,
    var scale: Float,
) : DrawModifierNode, GlobalPositionAwareModifierNode, Modifier.Node() {

    private val effectScope = object : BackdropEffectScopeImpl() {
        override val shape get() = androidx.compose.ui.graphics.RectangleShape
    }

    /** Holds the capture; the render effect lives here so it runs once, on the flatten below. */
    private var captureLayer: GraphicsLayer? = null

    private var coordinates: LayoutCoordinates? = null
    private var recordedSize: IntSize = IntSize.Zero

    override fun ContentDrawScope.draw() {
        // Nothing of this node is visible; it exists to keep [pooled] current.
        val capture = captureLayer ?: return
        val coordinates = coordinates ?: return
        if (size.width <= 0f || size.height <= 0f) return

        val recordSize = IntSize(
            (size.width * scale).toInt().coerceAtLeast(1),
            (size.height * scale).toInt().coerceAtLeast(1),
        )
        // Re-recorded every frame, deliberately: the surfaces sampling this show what is
        // behind them live, and holding a copy while the page scrolls is exactly the
        // stale-glass look this is meant to avoid. Three passes buys it for all of them.

        effectScope.update(this, scale)
        effectScope.apply(effects)
        capture.renderEffect = effectScope.renderEffect
        capture.colorFilter = effectScope.compositeColorFilter

        recordLayer(this@GlassPoolNode, capture, size = recordSize) {
            scale(scale, scale, androidx.compose.ui.geometry.Offset.Zero) {
                with(source) {
                    drawBackdrop(
                        density = effectScope,
                        coordinates = coordinates,
                        layerBlock = null,
                    )
                }
            }
        }
        // Flatten: drawing the capture evaluates its effect once, into plain pixels the
        // sampling surfaces can replay without re-running the blur for each of them.
        recordLayer(this@GlassPoolNode, pooled.graphicsLayer, size = recordSize) {
            drawLayer(capture)
        }
        pooled.sourceScale = scale
        pooled.layerCoordinates = coordinates
        pooled.contentRecorded()

        recordedSize = recordSize
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        if (coordinates.isAttached) {
            this.coordinates = coordinates
        }
    }

    override fun onAttach() {
        captureLayer = requireGraphicsContext().createGraphicsLayer()
    }

    override fun onDetach() {
        captureLayer?.let { requireGraphicsContext().releaseGraphicsLayer(it) }
        captureLayer = null
        coordinates = null
        recordedSize = IntSize.Zero
        effectScope.reset()
    }
}
