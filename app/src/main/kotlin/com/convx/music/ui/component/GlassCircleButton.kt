/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import com.convx.music.ui.ios.compat.LocalContentColor
import com.convx.music.ui.ios.compat.AppTheme
import com.convx.music.ui.ios.compat.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.util.lerp
import com.convx.music.ui.component.backdrop.catalog.utils.InteractiveHighlight
import com.convx.music.ui.component.shapes.ContinuousRoundedRectangle
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

// Kyant0/Capsule's continuous (superellipse) circle instead of CircleShape's
// circular-arc corners — same reasoning as the floating nav bar/search pill:
// smoother, and it's what liquidGlass's lens effect is tuned against.
private val GlassCircleShape = ContinuousRoundedRectangle(percent = 50)

/**
 * Same tanh-damped press/drag-out scale+translate distortion as
 * [com.convx.music.ui.component.backdrop.catalog.components.LiquidButton], reused
 * (not duplicated) here so every small floating glass button — top bar back/share/
 * search, selection-mode exit — gets the same "elastic stretch" liquid feel on
 * press. Cheap: a per-frame graphicsLayer transform, no extra backdrop re-capture.
 */
internal fun elasticStretchLayerBlock(interactiveHighlight: InteractiveHighlight): GraphicsLayerScope.() -> Unit = {
    val width = size.width
    val height = size.height

    val progress = interactiveHighlight.pressProgress
    val scale = lerp(1f, 1f + 4f.dp.toPx() / size.height, progress)

    val maxOffset = size.minDimension
    val initialDerivative = 0.05f
    val offset = interactiveHighlight.offset
    translationX = maxOffset * tanh(initialDerivative * offset.x / maxOffset)
    translationY = maxOffset * tanh(initialDerivative * offset.y / maxOffset)

    val maxDragScale = 4f.dp.toPx() / size.height
    val offsetAngle = atan2(offset.y, offset.x)
    scaleX = scale +
        maxDragScale * abs(cos(offsetAngle) * offset.x / size.maxDimension) *
        (width / height).fastCoerceAtMost(1f)
    scaleY = scale +
        maxDragScale * abs(sin(offsetAngle) * offset.y / size.maxDimension) *
        (height / width).fastCoerceAtMost(1f)
}

@Composable
internal fun rememberElasticStretch(): InteractiveHighlight {
    val animationScope = rememberCoroutineScope()
    return remember(animationScope) { InteractiveHighlight(animationScope) }
}

/**
 * A floating circular liquid-glass button — the back/share/favorite buttons on
 * the Artist/Album/Playlist detail screens, replacing their Material TopAppBar
 * and flat-colored circular Surface/IconButton. Falls back to a translucent
 * flat circle when glass is disabled or unsupported, same as
 * [com.convx.music.ui.component.FloatingNavBar]'s own useGlass fallback.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GlassCircleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    size: Dp = 44.dp,
    // Same idea as the nav bar's own rim tint: the caller's current artwork/background
    // colour, already boosted (asRimPop). Unspecified keeps the plain default rim.
    rimTintColor: Color = Color.Unspecified,
    content: @Composable () -> Unit,
) {
    val glassConfig = LocalGlassEffectConfig.current
    // These float over content the same way the nav bar does and are meant to
    // read as the same material, so they follow the nav bar's own on/off switch
    // and its effect values rather than only the global one.
    val useGlass = glassConfig.isEnabledFor(GlassComponent.NAV_BAR) && isGlassAllowed()

    val backgroundModifier = if (useGlass) {
        Modifier.liquidGlass(
            config = glassConfig,
            shape = GlassCircleShape,
            highlightAlpha = 0.3f,
            rimTintColor = rimTintColor,
            // backdropScale is left at liquidGlass's default
            // (glassResolutionScale of the blur radius) so these record their
            // backdrop at the same reduced resolution the nav bar uses instead
            // of full res — the blur hides the upscale.
        )
    } else {
        Modifier
            .clip(GlassCircleShape)
            .background(AppTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f))
    }
    val contentColor = if (useGlass) glassConfig.textColor else AppTheme.colorScheme.onSurface

    val interactionSource = remember { MutableInteractionSource() }
    val elasticStretch = rememberElasticStretch()

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .graphicsLayer(elasticStretchLayerBlock(elasticStretch))
            .size(size)
            .clip(GlassCircleShape)
            .then(backgroundModifier)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                role = Role.Button,
                indication = ripple(bounded = false, radius = size / 2),
                interactionSource = interactionSource,
            )
            .then(elasticStretch.modifier)
            .then(elasticStretch.gestureModifier)
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            content()
        }
    }
}

/**
 * A flat circular button whose fill and content colour both come from the
 * screen's own artwork tint, not from glass or a fixed Material color — the
 * back/share/favorite buttons on an artwork-tinted screen (Artist, Album)
 * that wants its whole chrome to read as *that record's* color rather than
 * floating glass over it.
 *
 * [onTint] should be the same contrast color the rest of the screen already
 * derives from its tint (e.g. `AppleTokens.onColor(tint)`), so a light-tinted
 * cover gets dark buttons/icons and a dark-tinted cover gets light ones.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContrastCircleButton(
    onTint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    size: Dp = 44.dp,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val elasticStretch = rememberElasticStretch()

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .graphicsLayer(elasticStretchLayerBlock(elasticStretch))
            .size(size)
            .clip(GlassCircleShape)
            .background(onTint.copy(alpha = 0.16f))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                role = Role.Button,
                indication = ripple(bounded = false, radius = size / 2),
                interactionSource = interactionSource,
            )
            .then(elasticStretch.modifier)
            .then(elasticStretch.gestureModifier)
    ) {
        CompositionLocalProvider(LocalContentColor provides onTint) {
            content()
        }
    }
}
