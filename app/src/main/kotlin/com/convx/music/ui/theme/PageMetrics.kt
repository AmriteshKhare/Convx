/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Horizontal/vertical rhythm of a page, overridable for one subtree. Shared components
 * (section headers, chip rows) read this instead of [AppleTokens.Gutter] directly, so a
 * screen that follows a different reference can retune them without forking them.
 */
@Immutable
data class PageMetrics(val gutter: Dp, val headerTop: Dp, val headerBottom: Dp)

/** Default: the Apple rhythm every screen used before per-screen metrics existed. */
val LocalPageMetrics = staticCompositionLocalOf { PageMetrics(AppleTokens.Gutter, 12.dp, 12.dp) }

/**
 * BitChord's page rhythm, measured from its source (Common.kt PAGE_GUTTER, HomeScreen
 * shelves): 10dp gutter, 10dp between a header and its row, 26dp under each shelf + the
 * next header's own 10dp above it, 14dp between hero cards, hero card = 70% of the row up
 * to 320dp.
 *
 * Home's list spaces every item by [ItemSpacing], so the header takes 0dp below and
 * (26dp + 0) above: header -> row = 10dp, row -> next header = 10 + 26 = 36dp, as in BitChord.
 */
object BitChordPage {
    val Gutter = 10.dp
    /** Spacing between every item of a page's list. */
    val ItemSpacing = 10.dp
    /** Extra space above a section header, on top of [ItemSpacing]. */
    val SectionGap = 26.dp
    val HeroCardGap = 14.dp
    val Metrics = PageMetrics(gutter = Gutter, headerTop = SectionGap, headerBottom = 0.dp)

    private const val HeroCardFraction = 0.70f
    private val HeroCardMaxWidth = 320.dp

    fun heroCardWidth(available: Dp): Dp = minOf(available * HeroCardFraction, HeroCardMaxWidth)
}
