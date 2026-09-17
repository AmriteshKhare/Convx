/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */
package com.convx.music.ui.player

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

/**
 * Q2 lock: the single host owning the mini <-> full morph flight.
 *
 * One composition driven by one [progress] source, composing (in z order):
 * the interpolated container ([PlayerContainerMorphOverlay]) with the mini
 * recording fading out early and the full recording resolving late as a
 * blurred ghost to crisp, then the artwork ([PlayerArtworkMorphOverlay]) --
 * travelling rect for squared cards, shrink+fade in place for hero / bleed /
 * vinyl / clover.
 *
 * Mini and full UI becoming states of one tree (rather than two recorded
 * roots) lands here next, behind [PlayerMorph.enabled]; the overlay contract
 * -- progress in, draw-phase reads only -- stays identical.
 */
@Composable
fun BoxScope.PlayerMorphHost(
    progress: () -> Float,
    thumbnailUrl: String?,
    containerColor: Color,
    expandedCornerRadius: Dp,
) {
    // Container first, artwork on top of it: the artwork travels across the
    // same window and has to ride above the surface it is growing out of.
    PlayerContainerMorphOverlay(
        progress = progress,
        color = containerColor,
        expandedCornerRadius = expandedCornerRadius,
    )
    PlayerArtworkMorphOverlay(
        thumbnailUrl = thumbnailUrl,
        progress = progress,
    )
}
