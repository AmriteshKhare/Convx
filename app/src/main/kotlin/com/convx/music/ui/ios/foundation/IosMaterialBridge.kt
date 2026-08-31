/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.ui.ios.foundation

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Stable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Migration bridge: an M3 [ColorScheme] and [Typography] built entirely out of the iOS
 * palette and text styles.
 *
 * No screen reads this any more — they all go through `AppTheme` / `IosTheme`. It is
 * still needed because the iOS compat layer delegates to real Material machinery for
 * things that carry no Material styling of their own but do read `MaterialTheme` for
 * internal defaults: `Scaffold`'s layout, `ModalBottomSheet`'s drag/anchor behaviour,
 * `SnackbarHost`'s queueing, `TooltipBox`, `SwipeToDismissBox`, and the wavy slider that
 * backs the user-selectable `SliderStyle.WAVY`.
 *
 * Without this, those internals fall back to Material's stock purple. It is invisible on
 * screen; its whole job is keeping the parts we did not reimplement on the iOS palette.
 *
 * ponytail: delete only if every one of those delegations is reimplemented natively.
 * Removing it while they exist does not remove Material — it just makes it ugly.
 */
@Stable
fun iosColorScheme(colors: IosColors): ColorScheme {
    val base = if (colors.isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = colors.accent,
        onPrimary = colors.onAccent,
        primaryContainer = colors.accent.copy(alpha = 0.18f),
        onPrimaryContainer = colors.accent,
        inversePrimary = colors.accent,

        secondary = colors.accent,
        onSecondary = colors.onAccent,
        secondaryContainer = colors.secondarySystemFill,
        onSecondaryContainer = colors.label,

        tertiary = colors.systemIndigo,
        onTertiary = colors.onAccent,
        tertiaryContainer = colors.tertiarySystemFill,
        onTertiaryContainer = colors.label,

        background = colors.systemGroupedBackground,
        onBackground = colors.label,

        // iOS has no elevation tint ladder: a "surface" is a card, and every level of
        // it is one of the three flat system greys.
        surface = colors.systemBackground,
        onSurface = colors.label,
        surfaceVariant = colors.secondarySystemGroupedBackground,
        onSurfaceVariant = colors.secondaryLabel,
        surfaceTint = colors.accent,
        inverseSurface = colors.label,
        inverseOnSurface = colors.systemBackground,

        surfaceDim = colors.systemGroupedBackground,
        surfaceBright = colors.systemBackground,
        surfaceContainerLowest = colors.systemBackground,
        surfaceContainerLow = colors.secondarySystemGroupedBackground,
        surfaceContainer = colors.secondarySystemGroupedBackground,
        surfaceContainerHigh = colors.tertiarySystemGroupedBackground,
        surfaceContainerHighest = colors.tertiarySystemGroupedBackground,

        error = colors.systemRed,
        onError = colors.onAccent,
        errorContainer = colors.systemRed.copy(alpha = 0.18f),
        onErrorContainer = colors.systemRed,

        outline = colors.separator,
        outlineVariant = colors.opaqueSeparator,
        scrim = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.32f),
    )
}

/**
 * M3 [Typography] carrying the iOS text styles, so a stray `Text()` inside an
 * unconverted screen still measures and tracks like SF Pro rather than Roboto.
 *
 * The *display* tiers are Apple Music's, not plain iOS's. SF's own large title is
 * W400 at positive tracking, which is right for a settings header and wrong for a
 * screen whose whole job is one album name over one picture: at that weight a title
 * and the credit line under it read as the same rank, and the page has no hierarchy
 * left. Apple Music sets those tiers heavy (W700/W800) and tightens the tracking as
 * the size grows, which is the look BitChord is built on and what makes its headers
 * carry. Supporting copy drops the other way — 14sp body against a 30sp title — so
 * the gap between the two ranks does the work instead of colour.
 *
 * Only the Material scale changes. `IosTheme.typography` keeps SF's real metrics, so
 * every iOS-styled row, label and menu item is untouched; this moves headers, titles
 * and subtitles, which is where the hierarchy lives.
 */
@Stable
fun iosMaterialTypography(fontFamily: FontFamily = FontFamily.Default): Typography {
    val t = iosTypography(fontFamily)

    // Line height is set at ~1.2x rather than left to the font: a two-line album title
    // at -0.8 tracking and W800 sets very tight by default, and the second line ends up
    // touching the first.
    fun display(size: Int, weight: FontWeight, tracking: Float) = TextStyle(
        fontFamily = fontFamily,
        fontSize = size.sp,
        lineHeight = (size * 1.2f).sp,
        fontWeight = weight,
        letterSpacing = tracking.sp,
    )

    return Typography(
        displayLarge = display(34, FontWeight.W800, -0.8f),
        displayMedium = display(30, FontWeight.W800, -0.7f),
        displaySmall = display(26, FontWeight.W700, -0.5f),
        headlineLarge = display(30, FontWeight.W800, -0.7f),
        headlineMedium = display(22, FontWeight.W700, -0.4f),
        headlineSmall = display(20, FontWeight.W700, -0.3f),
        titleLarge = display(20, FontWeight.W700, -0.3f),
        titleMedium = display(16, FontWeight.W600, -0.2f),
        titleSmall = t.subheadline,
        bodyLarge = display(16, FontWeight.W400, 0f),
        bodyMedium = display(14, FontWeight.W400, 0f),
        bodySmall = t.footnote,
        labelLarge = t.headline,
        labelMedium = display(12, FontWeight.W600, 0f),
        labelSmall = display(11, FontWeight.W600, 0f),
    )
}
