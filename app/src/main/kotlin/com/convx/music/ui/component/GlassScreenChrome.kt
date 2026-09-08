/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.convx.music.ui.component.backdrop.backdrops.LayerBackdrop
import com.convx.music.ui.component.backdrop.backdrops.layerBackdrop
import com.convx.music.ui.component.backdrop.backdrops.rememberBackdropFreeze
import com.convx.music.ui.component.backdrop.backdrops.rememberLayerBackdrop
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

/**
 * The backdrop + haze wiring every flat (non-hero-artwork) screen needs to give
 * its [FrostedTopBar] real live-refraction glass instead of either a plain tint
 * or a RenderNode self-capture crash.
 *
 * [FrostedTopBar]'s back/action buttons sample `LocalAppBackdrop` — provide
 * [backdrop] there, then apply [contentModifier] to the screen's own scrolling
 * root (LazyColumn/LazyVerticalGrid/etc) so it becomes an ATTACHED, already-drawn
 * capture. The top bar must be a *sibling* of that scrolling root, never a
 * descendant, or the capture includes its own draw pass — see
 * `glass-chrome-rendernode-cycle` for the crash this avoids (hit on Settings'
 * own back button, and the standing pattern already used on
 * Artist/Album/Playlist/History/Stats).
 */
@Stable
class GlassScreenChrome internal constructor(
    val backdrop: LayerBackdrop,
    val hazeState: HazeState,
    private val freeze: com.convx.music.ui.component.backdrop.backdrops.BackdropFreeze,
) {
    // Same freeze the scrolling content's own layerBackdrop uses — a consumer
    // of [backdrop] that doesn't pass this to its own `frozen` param re-runs
    // its whole effect chain every scroll frame
    // instead of reusing the last one, which is where the scroll jank came
    // from measured elsewhere in this codebase (47ms/frame re-recorded vs
    // 1.9ms reused).
    val frozen: () -> Boolean get() = freeze.frozen
    // hazeSource must wrap ONLY the scrollable content — NEVER a container that
    // also holds TopFadeBlur/FrostedTopBar as children/siblings-within-it. Haze
    // captures everything drawn inside the hazeSource'd node; if the blur
    // overlay itself is inside that node, the "source" ends up being the
    // overlay's own draw output, and the blur silently does nothing (no crash,
    // no error — just an empty/no-op capture). MainActivity's proven wiring
    // keeps them structurally separate: topBarHazeState marks the NavHost
    // content Box, while TopFadeBlur/FrostedTopBar live in the Scaffold's
    // topBar slot — a different subtree entirely, connected only by the shared
    // HazeState reference. Mirror that: wrap a Box around just the scrolling
    // content with this modifier, and keep TopFadeBlur/FrostedTopBar as later
    // siblings of THAT wrapping Box, not inside it.
    /** Apply to a Box wrapping ONLY the screen's scrolling content — nothing else. */
    fun screenModifier(): Modifier = Modifier.hazeSource(hazeState)

    /** Apply to the screen's scrolling root (LazyColumn/Column/etc). */
    fun contentModifier(): Modifier = Modifier
        .nestedScroll(freeze.connection)
        // Outer layer must enclose the backdrop node so unrelated siblings
        // (mini player, playing indicator) ticking on their own schedule don't
        // force a re-record; inner layer flattens the list into one RenderNode
        // so the capture records a single drawRenderNode instead of replaying
        // every item — same pairing MainActivity's own appBackdrop uses.
        .graphicsLayer()
        .layerBackdrop(backdrop, frozen = freeze.frozen)
        .graphicsLayer()
}

@Composable
fun rememberGlassScreenChrome(background: Color): GlassScreenChrome {
    val freeze = rememberBackdropFreeze()
    val backdrop = rememberLayerBackdrop(
        // Fills the screen background into the capture BEFORE content, same
        // reason MainActivity's appBackdrop does: without it the list records
        // onto a transparent canvas and the blur reads as part-transparent /
        // ghosted instead of solid frosted content.
        onDraw = remember(background) {
            val bg = background
            { drawRect(bg); drawContent() }
        }
    )
    val hazeState = remember { HazeState() }
    return remember(backdrop, hazeState, freeze) { GlassScreenChrome(backdrop, hazeState, freeze) }
}
