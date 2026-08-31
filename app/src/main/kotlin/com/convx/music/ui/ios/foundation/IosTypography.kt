/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.ui.ios.foundation

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * The eleven iOS text styles, at their default (Large) Dynamic Type size.
 *
 * SF Pro cannot be redistributed, so [fontFamily] stays [FontFamily.Default] — the
 * metrics, weights and leading are what carry the iOS feel here. Swap in a bundled
 * face by passing a family to [iosTypography].
 */
@Immutable
data class IosTypography(
    val largeTitle: TextStyle,
    val title: TextStyle,
    val title2: TextStyle,
    val title3: TextStyle,
    val headline: TextStyle,
    val subheadline: TextStyle,
    val body: TextStyle,
    val callout: TextStyle,
    val footnote: TextStyle,
    val caption: TextStyle,
    val caption2: TextStyle,
)

fun iosTypography(fontFamily: FontFamily = FontFamily.Default): IosTypography {
    fun style(size: Int, lineHeight: Int, weight: FontWeight, tracking: Float) = TextStyle(
        fontFamily = fontFamily,
        fontSize = size.sp,
        lineHeight = lineHeight.sp,
        fontWeight = weight,
        letterSpacing = tracking.sp,
    )

    // Apple Music tiers, not plain SF's: heavier weight and tracking that
    // tightens (goes negative) as size grows, rather than SF's flat positive
    // tracking. A title over a single record needs to carry the page on its
    // own; SF's W400 reads as the same rank as the copy under it, so there is
    // no hierarchy left. Line height is set at ~1.2x rather than left to the
    // font: a two-line title at tight tracking and heavy weight sets very
    // close by default, and the second line ends up touching the first.
    fun display(size: Int, weight: FontWeight, tracking: Float) = TextStyle(
        fontFamily = fontFamily,
        fontSize = size.sp,
        lineHeight = (size * 1.2f).sp,
        fontWeight = weight,
        letterSpacing = tracking.sp,
    )

    return IosTypography(
        largeTitle = display(34, FontWeight.W800, -0.8f),
        title = display(28, FontWeight.W800, -0.7f),
        title2 = display(22, FontWeight.W700, -0.4f),
        title3 = display(20, FontWeight.W700, -0.3f),
        headline = display(16, FontWeight.W600, -0.2f),
        subheadline = style(15, 20, FontWeight.Normal, -0.24f),
        // Supporting copy drops the other way from the display tiers — flat
        // weight, zero tracking — so the gap between title and body does the
        // hierarchy work instead of colour.
        body = display(16, FontWeight.W400, 0f),
        callout = display(14, FontWeight.W400, 0f),
        footnote = style(13, 18, FontWeight.Normal, -0.08f),
        caption = style(12, 16, FontWeight.Normal, 0f),
        caption2 = style(11, 13, FontWeight.W600, 0f),
    )
}
