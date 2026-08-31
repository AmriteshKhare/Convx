/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.ui.component

import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.convx.music.constants.ReduceDynamicBlurKey
import com.convx.music.utils.rememberPreference
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials

/**
 * The run the fade needs below the bar to get from full blur to none without
 * the eye finding where it got there.
 *
 * Shortened from the original 120dp — a page's first heading sits a fixed
 * distance down the screen, well inside this run, and the tail is what hides
 * the layer's end, so it cannot simply be cut short of this.
 */
private val FADE_RUN = 88.dp

/**
 * How much blur the fade reaches at its outer edge — short of all of it.
 *
 * The last quarter buys almost nothing visually and costs the most: a blur
 * has nothing to sample past the edge of its own layer, so the harder it is
 * pushed there the more of the layer is flat material colour rather than
 * blurred content. Stopping at three quarters keeps the ramp and loses the
 * band.
 */
private const val PEAK = 0.75f

/**
 * How dark the readability scrim starts, at the very top of the strip.
 *
 * Modest on purpose: it gives glyphs a floor on a pale sleeve, not to grey
 * out the artwork underneath.
 */
private const val SCRIM_PEAK = 0.42f

/** Enough stops that the ramp does not band across a near-flat colour. */
private const val SCRIM_STOPS = 12

/**
 * Ported from BitChord's `TopFadeBlur` — the glass behind every top bar: full
 * blur along the top edge, ramping to nothing on the way down.
 *
 * A bar carrying a uniform pane is a rectangle sitting on the page, and its
 * bottom edge is a line drawn across whatever scrolls under it. Fading out
 * instead leaves the title and back arrow something to be legible against and
 * the page nothing to be interrupted by, so [FrostedTopBar] paints no
 * backdrop of its own anywhere — this is the whole of it.
 *
 * Reuses the exact Haze mechanics [HeroTopBlur] already established here
 * (`HazeMaterials.ultraThin`, `HazeProgressive.verticalGradient`,
 * [ReduceDynamicBlurKey] as the escape hatch) — same material, different
 * shape of ramp and with a readability scrim layered on top.
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun TopFadeBlur(
    hazeState: HazeState,
    /**
     * The colour of the page behind this: the theme's background on a feed, a
     * detail page's artwork wash on one of those.
     */
    pageColor: Color,
    modifier: Modifier = Modifier,
    /**
     * A wash laid over the blur to keep the bar's glyphs readable.
     *
     * Laid *over* the blur rather than under it, which is the only order that
     * works: haze samples the content tagged as its source, not whatever
     * sibling sits between that content and itself, so a scrim underneath
     * would be painted over by the blurred content and do nothing.
     */
    scrimColor: Color = pageColor,
    // For a caller that isn't sitting under the status bar — a divider inside
    // the page itself (e.g. under a tab strip) — where [topBarHeight]'s status
    // bar inset would measure the wrong thing entirely.
    heightOverride: Dp? = null,
) {
    val reduceDynamicBlur by rememberPreference(ReduceDynamicBlurKey, false)
    // The bar fills itself solid instead when blur is reduced, so this has
    // nothing left to do.
    if (reduceDynamicBlur) return

    val height = heightOverride ?: (topBarHeight() + FADE_RUN)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .hazeEffect(
                state = hazeState,
                // Keyed to the colour of the page underneath, not the theme's
                // — a blur has nothing to sample past the top of its own
                // layer, so the first blur-radius of this strip is barely
                // covered by blurred content and shows mostly the flat colour
                // of the material instead.
                style = HazeMaterials.ultraThin(pageColor),
            ) {
                // Cubic rather than the default quadratic, and eased out
                // rather than in: the ramp falls away quickly under the bar
                // and then spends the rest of its run near nothing, which is
                // what hides where the layer ends.
                progressive = HazeProgressive.verticalGradient(
                    easing = EaseOutCubic,
                    startIntensity = PEAK,
                    endIntensity = 0f,
                )
                // Uniform across the layer, so it would show as texture over
                // the untouched foot of the ramp — the edge being hidden.
                noiseFactor = 0f
            },
    )

    val scrim = remember(scrimColor) {
        Brush.verticalGradient(
            // The same eased-out shape as the blur above it, so the two
            // arrive at nothing together.
            colorStops = Array(SCRIM_STOPS) { i ->
                val t = i / (SCRIM_STOPS - 1f)
                t to scrimColor.copy(alpha = SCRIM_PEAK * (1f - EaseOutCubic.transform(t)))
            },
        )
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(scrim),
    )
}
