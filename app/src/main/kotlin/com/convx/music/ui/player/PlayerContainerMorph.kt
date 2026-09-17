/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */
package com.convx.music.ui.player

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * Where the mini player's own pill sits on screen, in root coordinates.
 *
 * Module-level state for the same reason the artwork rects are: the pill lives in the
 * floating nav bar, the sheet lives in the player, they are far apart in the tree, and
 * only the live card ([morphLiveCard]) -- drawn once per frame, draw-phase -- reads it.
 */
private val miniContainerRect = mutableStateOf<Rect?>(null)

internal val miniPlayerContainerRect: Rect? get() = miniContainerRect.value

/**
 * Call on the mini player's outermost pill box. Purely observational: it records the
 * pill's bounds and changes nothing about how the pill renders.
 */
fun Modifier.registerMiniContainerRect(): Modifier = onGloballyPositioned {
    if (it.isAttached) miniContainerRect.value = it.boundsInRoot()
}
