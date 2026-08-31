/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.convx.music.constants.HomeCardCornerRadiusOverrideKey
import com.convx.music.ui.component.shapes.ContinuousRoundedRectangle
import com.convx.music.ui.theme.AppleTokens
import com.convx.music.ui.utils.rimLight
import com.convx.music.utils.rememberPreference

/**
 * The single card shape Home draws with.
 *
 * Only the hero ("recently played") card honoured [HomeCardCornerRadiusOverrideKey] and drew
 * a continuous (superellipse) corner; every other card on the screen used a plain
 * [androidx.compose.foundation.shape.RoundedCornerShape] at a fixed radius, so the corners in
 * one column visibly disagreed and the corner-radius setting only moved one of them. Route
 * every Home card through here instead.
 */
@Composable
fun rememberHomeCardShape(): Shape {
    val (cornerOverride) = rememberPreference(HomeCardCornerRadiusOverrideKey, 0)
    val corner = if (cornerOverride > 0) cornerOverride.dp else AppleTokens.CardCornerLarge
    return remember(corner) { ContinuousRoundedRectangle(corner) }
}

/**
 * Home's card rim: the same specular top edge the menu sheets use, dialled back because a
 * card sits on the page rather than floating over it. Draws over the content, so it goes
 * last in the modifier chain — after the clip and the background it is meant to edge.
 *
 * Not for a card that already samples the backdrop through `Modifier.liquidGlass`; that one
 * gets its highlight from the glass system and stacking both doubles the edge.
 */
fun Modifier.homeCardRim(shape: Shape): Modifier = rimLight(shape = shape, strength = 0.28f)
