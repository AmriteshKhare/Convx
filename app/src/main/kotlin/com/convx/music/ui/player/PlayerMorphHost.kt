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
 * Live tree: the travelling card is the sheet's own nodes ([morphLiveCard]),
 * so this host only carries what cannot live in the sheet -- the artwork
 * flight ([PlayerArtworkMorphOverlay]), which spans the mini's bar slot and
 * the sheet's hero rect. One progress source, draw-phase reads only.
 */
@Composable
fun BoxScope.PlayerMorphHost(
    progress: () -> Float,
    thumbnailUrl: String?,
    containerColor: Color,
    expandedCornerRadius: Dp,
) {
    PlayerArtworkMorphOverlay(
        thumbnailUrl = thumbnailUrl,
        progress = progress,
    )
}
