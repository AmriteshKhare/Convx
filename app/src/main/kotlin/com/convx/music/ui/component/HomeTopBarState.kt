/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.ui.component

import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * How far Home's own big logo has scrolled toward the compact top bar (0..1).
 *
 * Written by `HomeScreen` from its own scroll state and read by
 * `MainActivity`'s top bar to crossfade its small logo in — the two live in
 * different parts of the composition (the Scaffold's `topBar` slot vs the
 * NavHost content) with no shared `LazyListState` between them, so this is
 * the hand-off. A [MutableFloatState] rather than a plain `Float` composition
 * local: the value changes every scroll frame, and re-providing the local on
 * every change would recompose everything under it instead of just the two
 * places that actually read `.floatValue`.
 */
val LocalHomeTopBarLogoProgress = staticCompositionLocalOf<MutableFloatState> {
    mutableFloatStateOf(0f)
}
