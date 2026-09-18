@file:OptIn(ExperimentalSharedTransitionApi::class)

/*
 * FloatingTabBar v1.0.1 by Elyes Mansour
 * https://github.com/elyesmansour/compose-floating-tab-bar
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Vendored from the v1.0.1 tag: the published AAR is compiled against
 * Compose 1.8 and crashes with NoSuchMethodError on
 * SharedTransitionScope.sharedElement under the Compose version this app uses.
 * Compiling the source here keeps it binary-compatible.
 *
 * Local modifications on top of v1.0.1:
 * - Package renamed.
 * - InlineBar/ExpandedBar use Row/Column instead of ConstraintLayout, and the
 *   tab pill + standalone tab cluster is centered (iOS 26 style) instead of
 *   being pinned to opposite screen edges.
 * - The inline/expanded AnimatedContent is center-aligned so the collapse
 *   animation morphs around the center instead of the start edge.
 * - ExpandedTabs grew a draggable selection puck ported from Kyant0's
 *   AndroidLiquidGlass catalog (LiquidBottomTabs/LiquidBottomTab): equal-width
 *   tabs, a spring-damped indicator you can press-drag between tabs (release
 *   snaps to and navigates the nearest tab), a finger-tracking glow, and —
 *   when an optional [Backdrop] is supplied — chromatic lens refraction and
 *   an accent-tinted glass sample of the selected tab's icon showing through
 *   the puck. See [DampedDragAnimation] and [InteractiveHighlight].
 */

package com.convx.music.ui.component.floatingtabbar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import com.convx.music.ui.ios.compat.AppTheme
import com.convx.music.ui.theme.LocalAccentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.luminance
import com.convx.music.LocalAmbientBackgroundColor
import com.convx.music.ui.component.asPuckTint
import com.convx.music.constants.NavBarMeltTransitionKey
import com.convx.music.ui.component.LocalGlassEffectConfig
import com.convx.music.ui.component.elasticStretchLayerBlock
import com.convx.music.ui.component.glassResolutionScale
import com.convx.music.utils.rememberPreference
import com.convx.music.ui.component.backdrop.Backdrop
import com.convx.music.ui.component.backdrop.catalog.utils.DampedDragAnimation
import com.convx.music.ui.component.backdrop.catalog.utils.InteractiveHighlight
import com.convx.music.ui.component.backdrop.backdrops.layerBackdrop
import com.convx.music.ui.component.backdrop.backdrops.rememberCombinedBackdrop
import com.convx.music.ui.component.backdrop.backdrops.rememberLayerBackdrop
import com.convx.music.ui.component.backdrop.drawBackdrop
import com.convx.music.ui.component.backdrop.highlight.Highlight
import com.convx.music.ui.component.backdrop.shadow.InnerShadow
import com.convx.music.ui.component.backdrop.effects.blur
import com.convx.music.ui.component.backdrop.effects.lens
import com.convx.music.ui.component.backdrop.effects.vibrancy
import com.convx.music.ui.component.shapes.ContinuousRoundedRectangle
import com.convx.music.ui.player.PlayerMorph
import com.convx.music.ui.theme.isHueless
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign



// ponytail: diagnostic slow-motion for the inline<->expanded transition.
// Was left at 6 after a jitter hunt (gooey crossfade 300ms -> 1800ms, tab
// fades 220/180ms -> ~1.4s): both bars rendered overlapping for seconds —
// the reported "ghosting" and puck-behind-nav-bar — and taps during the
// crawl felt dead. Keep at 1; raise only to re-diagnose.
private const val DIAG_SLOWDOWN = 1

// Tuning for the gooey merge/split pulse across the inline<->expanded
// crossfade — see GooeyTransition.kt. Not derived from fadeIn()/fadeOut()'s
// own duration; just a reasonable match for it.
private val GooeyPeakBlur = 12.dp
private const val GooeyDurationMs = 300 * DIAG_SLOWDOWN

// Shadow the selection puck keeps when it is NOT being pressed. It used to be
// driven straight off pressProgress, i.e. zero at rest, which left the puck
// defined only by its own surface wash — invisible against a dark bar.
// (The matching rim light is gone: the iOS 26 reference bars render the selected
// capsule as a plain fill with no specular edge. Separation at rest is the
// shadow's job now, which is what the reference does too.)
private const val PuckRestShadowAlpha = 0.35f

/** How long after the last scroll event the bar's backdrop stays frozen. Long enough to
 *  cover a fling's frame gaps, short enough that the glass refreshes as soon as the list
 *  actually settles. */
private const val ScrollFreezeTailNs = 120_000_000L

// EMA weight for the scroll-velocity estimate. Plain value, no recomposition.
private const val MomentumVelocityEmaAlpha = 0.35f

// Vendored addition: the three shapes the bar can render. INLINE is the
// scroll-collapsed row (unchanged by search mode); EXPANDED is the normal
// all-tabs pill; SEARCH_EXPANDED replaces EXPANDED while search mode is
// active (see SearchExpandedBar).
private enum class FloatingTabBarVisual { INLINE, EXPANDED, SEARCH_EXPANDED }

// Matches the established inline-row height (InlineStandaloneTab's own
// defaultMinSize floor at the FloatingNavBar call site, and the inline mini
// player's own ~48dp content height) so the search-expanded row doesn't read
// shorter than the bar's own shrunk state. Also the shrunk mini player's
// pinned height (see FloatingMiniPlayer) — one constant, same row height.
internal val SearchBarRowHeight = 48.dp

/**
 * A floating tab bar that transitions between inline and expanded states based on scroll behavior.
 *
 * @param selectedTabKey the key of the currently selected tab
 * @param scrollConnection the scroll connection that handles state transitions
 * @param modifier the modifier to be applied to the tab bar
 * @param colors the colors used by the tab bar components
 * @param shapes the shapes used by the tab bar components
 * @param sizes the sizes and spacing used by the tab bar components
 * @param elevations the elevation values used by the tab bar components
 * @param tabBarContentModifier modifier applied to the tab bar sections containing the grouped tabs and standalone tab.
 * It is applied after the default styling (background, shadow, clip) but before any content padding.
 * @param inlineAccessory the accessory composable that appears in inline state (e.g., compact media player)
 * @param expandedAccessory the accessory composable that appears in expanded state (e.g., full media player)
 * @param content the content defining the tabs
 */
@Composable
fun FloatingTabBar(
    selectedTabKey: Any?,
    scrollConnection: FloatingTabBarScrollConnection,
    modifier: Modifier = Modifier,
    tabBarContentModifier: Modifier = Modifier,
    inlineAccessory: (@Composable SharedTransitionScope.(Modifier, AnimatedVisibilityScope) -> Unit)? = null,
    expandedAccessory: (@Composable SharedTransitionScope.(Modifier, AnimatedVisibilityScope) -> Unit)? = null,
    colors: FloatingTabBarColors = FloatingTabBarDefaults.colors(),
    shapes: FloatingTabBarShapes = FloatingTabBarDefaults.shapes(),
    sizes: FloatingTabBarSizes = FloatingTabBarDefaults.sizes(),
    elevations: FloatingTabBarElevations = FloatingTabBarDefaults.elevations(),
    // Vendored addition: when non-null, the expanded selection puck samples this
    // backdrop for lens refraction and an accent-tinted glass preview of the
    // selected tab's icon. Null falls back to a flat colored puck.
    backdrop: Backdrop? = null,
    accentColor: Color? = null,
    // Vendored addition: when true (and searchBarContent non-null), the
    // expanded state renders SearchExpandedBar instead of ExpandedBar — the
    // tab group shrinks to the current screen's own icon and the standalone
    // slot expands to fill the rest of the row with searchBarContent. The
    // inline (scrolled) state is unaffected — it stays the normal collapsed
    // icon+accessory+standalone row regardless of searchMode.
    // Vendored addition: the puck's live position, when something outside the bar is
    // already animating the selection -- the main tabs pager. Returns the fractional
    // tab index while that driver is moving, and null the rest of the time so the
    // ordinary selectedTabKey settle takes back over.
    //
    // Without this the puck waited for the pager to FINISH: `selectedTabKey` derives
    // from `pagerState.currentPage`, which only flips near the end of
    // `animateScrollToPage`, and only then did the puck start its own spring. Two
    // springs in series, so the puck arrived roughly a full animation after the page
    // it marks -- the "selected icon puck comes after a second" report.
    tabPosition: (() -> Float?)? = null,
    // Whole-bar snap thump, 0 at rest. The docked mini fires it on drag
    // release (see onSnapRelease); read in the draw phase, no recomposition.
    snapPulse: () -> Float = { 0f },
    searchMode: Boolean = false,
    searchBarContent: (@Composable (Modifier) -> Unit)? = null,
    // Vendored addition: caller-held width (from a previous onExpandedWidthChanged
    // report) that SearchExpandedBar holds its row to, so the bar doesn't visibly
    // widen to fill available space when entering search mode. Null/0 falls back
    // to filling available space (e.g. first-ever composition in search mode).
    expandedContentWidthPx: Int? = null,
    onExpandedWidthChanged: ((Int) -> Unit)? = null,
    content: FloatingTabBarScope.() -> Unit
) {
    // Rebuilt every composition, deliberately not remembered. The tabs hold
    // @Composable lambdas that close over selection state and colours; caching
    // them behind a key meant every value they captured had to be listed in that
    // key by hand, and anything forgotten showed up as a stale icon or colour on
    // one navigation path only. Re-running the builder is a handful of
    // allocations for a few tabs, and it makes that whole class of bug
    // unrepresentable.
    val scope = FloatingTabBarScopeImpl().apply { content() }

    val isAccessoryShared = inlineAccessory != null && expandedAccessory != null

    val visual = when {
        scrollConnection.isInline -> FloatingTabBarVisual.INLINE
        searchMode && searchBarContent != null -> FloatingTabBarVisual.SEARCH_EXPANDED
        else -> FloatingTabBarVisual.EXPANDED
    }

    // updateTransition (rather than the sugared top-level AnimatedContent) so
    // the transition object is available here to drive the gooey blur pulse,
    // not just inside the content lambda.
    val transition = updateTransition(targetState = visual, label = "floatingTabBarVisual")
    // User-facing toggle: melt (gooey blur + SizeTransform morph) vs. the plain
    // fade this bar had before — melt costs a real offscreen blur pass on every
    // transition, so lower-end devices or anyone who just doesn't like the look
    // can drop back to the cheap crossfade.
    val meltTransitionEnabled by rememberPreference(NavBarMeltTransitionKey, defaultValue = true)
    val gooeyBlurPx = with(LocalDensity.current) { GooeyPeakBlur.toPx() }
    // Liquid "gooey" merge/split (see GooeyTransition.kt): peaks mid-crossfade
    // and returns to 0 at rest, on every transition regardless of direction —
    // the keyframes spec defines the peak explicitly rather than deriving it
    // from a start/end value, since the steady-state target is always 0.
    val gooeyProgress by transition.animateFloat(
        transitionSpec = {
            keyframes {
                durationMillis = GooeyDurationMs
                0f at 0
                1f atFraction 0.5f using FastOutSlowInEasing
                0f at GooeyDurationMs
            }
        },
        label = "gooeyProgress"
    ) { _ -> 0f }

    SharedTransitionLayout(modifier = modifier) {
        // Wrapping AnimatedContent itself (not SharedTransitionLayout's own
        // modifier) so the offscreen gooey capture stays under
        // SharedTransitionLayout's own overlay mechanism — sharedElement-
        // tracked content (like the search tab's shared "standaloneTab"
        // bounds) renders via that overlay, and capturing it into the gooey
        // layer at the wrong level made it visibly pop in a frame late when
        // re-expanding. This way the overlay draws normally, on top.
        // Held as a lambda rather than a Boolean so flipping it costs no
        // recomposition — the glass surfaces call it during draw. See
        // LocalTabBarBackdropFrozen.
        // Frozen while the bar's own inline/expand transition runs AND while the list
        // under it is scrolling. Only the first half existed before, so the one case that
        // actually re-captures the backdrop every frame -- scrolling content passing under
        // the glass -- was the one case not covered.
        val frozenWhileAnimating = remember(transition, scrollConnection) {
            {
                transition.currentState != transition.targetState || scrollConnection.isScrolling() ||
                    // PLAN 4 + Q3: bar is covered by the morph card, re-sampling
                    // under it is wasted work. Unfreezes at 0.75 so the capture
                    // is live before the glass resolves at the end of flight.
                    (PlayerMorph.active && PlayerMorph.progress < 0.75f)
            }
        }
        val collapsing = transition.targetState == FloatingTabBarVisual.INLINE
        val boundsTransforms = remember(collapsing) {
            if (collapsing) BarBoundsTransforms.Collapse else BarBoundsTransforms.Expand
        }
        CompositionLocalProvider(
            LocalTabBarBackdropFrozen provides frozenWhileAnimating,
            LocalBarBoundsTransforms provides boundsTransforms,
        ) {
            // Shape morph, not a crossfade: SizeTransform makes the container itself
            // grow/shrink between the inline and expanded bounds (clip = false, so
            // per-item elastic stretch can protrude past the animating rect) — that
            // continuous resize is what reads as one pill becoming the other.
            // gooey() then wraps the whole resizing container in one offscreen
            // layer (see GooeyTransition.kt), so the outgoing/incoming edges bleed
            // together through the blur and snap back to a crisp, molten seam
            // instead of a hard-clipped rectangle edge.
            Box(
                Modifier
                    .gooey { if (meltTransitionEnabled) gooeyBlurPx * gooeyProgress else 0f }
                    // Unclipped so per-item elastic stretch can protrude past the
                    // bar bounds instead of slicing at its edge.
                    .graphicsLayer { clip = false }
            ) {
                transition.AnimatedContent(
                    transitionSpec = {
                        // Preserve the inline/expanded size morph, but hand icon
                        // ownership over once: old tree fades out in the first half,
                        // new tree fades in in the second. This removes the overlap/
                        // readjust glitch while keeping the requested animation.
                        (fadeIn(
                            tween(
                                durationMillis = GooeyDurationMs / 2,
                                delayMillis = GooeyDurationMs / 2,
                                easing = FastOutSlowInEasing,
                            )
                        ) togetherWith fadeOut(
                            tween(
                                durationMillis = GooeyDurationMs / 2,
                                easing = FastOutSlowInEasing,
                            )
                        ))
                            .using(
                                SizeTransform(clip = false) { _, _ ->
                                    tween(GooeyDurationMs, easing = FastOutSlowInEasing)
                                }
                            )
                    },
                    contentAlignment = Alignment.BottomCenter
                ) { targetVisual ->
            when (targetVisual) {
                FloatingTabBarVisual.INLINE -> InlineBar(
                    scope = scope,
                    selectedTabKey = selectedTabKey,
                    accessory = inlineAccessory,
                    isAccessoryShared = isAccessoryShared,
                    onInlineTabClick = { scrollConnection.expand() },
                    colors = colors,
                    shapes = shapes,
                    sizes = sizes,
                    elevations = elevations,
                    tabBarContentModifier = tabBarContentModifier,
                    animatedVisibilityScope = this@AnimatedContent
                )
                FloatingTabBarVisual.EXPANDED -> ExpandedBar(
                    scope = scope,
                    selectedTabKey = selectedTabKey,
                    tabPosition = tabPosition,
                    accessory = expandedAccessory,
                    isAccessoryShared = isAccessoryShared,
                    colors = colors,
                    shapes = shapes,
                    sizes = sizes,
                    elevations = elevations,
                    tabBarContentModifier = tabBarContentModifier,
                    animatedVisibilityScope = this@AnimatedContent,
                    backdrop = backdrop,
                    accentColor = accentColor,
                    onWidthMeasured = onExpandedWidthChanged
                )
                FloatingTabBarVisual.SEARCH_EXPANDED -> SearchExpandedBar(
                    scope = scope,
                    selectedTabKey = selectedTabKey,
                    accessory = expandedAccessory,
                    isAccessoryShared = isAccessoryShared,
                    colors = colors,
                    shapes = shapes,
                    sizes = sizes,
                    elevations = elevations,
                    tabBarContentModifier = tabBarContentModifier,
                    animatedVisibilityScope = this@AnimatedContent,
                    searchBarContent = searchBarContent ?: {},
                    targetWidthPx = expandedContentWidthPx
                )
            }
            }
        }
        }
    }
}

/**
 * True for exactly the frames of a tab bar transition (inline <-> expanded <->
 * search). The bar's glass surfaces pass it to `drawBackdrop`'s `frozen`, which
 * makes them reuse their last backdrop capture instead of re-recording the whole
 * screen every frame while their bounds animate.
 *
 * A lambda, not a Boolean, so flipping it triggers no recomposition — the surfaces
 * call it during draw. Static local for the same reason: the lambda's identity is
 * stable, only what it reads changes.
 */
internal val LocalTabBarBackdropFrozen = staticCompositionLocalOf<() -> Boolean> { { false } }

/**
 * A [NestedScrollConnection] that handles scroll events to transition between inline and expanded states.
 *
 * @param initialIsInline Initial state of the tab bar (inline or expanded).
 * @param scrollThresholdPx The minimum scroll distance in pixels required to collapse to inline.
 * @param expandThresholdPx The minimum scroll distance in pixels required to expand back. Kept
 * well below [scrollThresholdPx] so the bar returns as soon as the user scrolls up.
 * @param inlineBehavior Defines when the tab bar should transition to inline state.
 */
class FloatingTabBarScrollConnection(
    initialIsInline: Boolean = false,
    private val scrollThresholdPx: Float,
    private val expandThresholdPx: Float = scrollThresholdPx,
    private val inlineBehavior: FloatingTabBarInlineBehavior = FloatingTabBarInlineBehavior.OnScrollDown
) : NestedScrollConnection {
    var isInline by mutableStateOf(initialIsInline)
        private set

    private var accumulatedScroll = 0f

    // Plain array, NOT snapshot state -- exactly the technique BottomSheetState.dragClockNs
    // uses and for the same reason: the glass surfaces read this during the DRAW phase, and
    // a snapshot read there would register a draw dependency, so every write would
    // re-invalidate the frame forever.
    private val scrollClockNs = longArrayOf(0L)

    // Same plain-array technique for scroll velocity: an EMA'd scroll delta
    // plus the timestamp of the last event. Read only from a graphicsLayer
    // block or transition spec (never composition), so writing it every
    // scroll event costs no recomposition.
    private val smoothedVelocityPx = floatArrayOf(0f)
    private val lastDeltaClockNs = longArrayOf(0L)

    // Magnitude of the current scroll velocity, px per scroll event. Drives
    // only the collapse/expand morph spring stiffness, never any squash.
    fun scrollSpeed(): Float = abs(smoothedVelocityPx[0])

    /**
     * True while the list under the bar is scrolling, and for [ScrollFreezeTailNs] after the
     * last scroll event so a fling stays covered.
     *
     * Pass to a glass surface's `frozen` so the bar stops re-capturing the app backdrop for
     * the duration of a scroll. Measured on a SM-M346B: with the nav bar's glass live, a
     * scroll costs 35.5 ms p50 in issue-draw-commands against 14.7 ms with the glass off --
     * the capture plus its blur/lens chain was being rebuilt for every scrolled frame. The
     * content behind a 20-30dp blur cannot show the difference between a capture from this
     * frame and one from 100 ms ago, so re-capturing at scroll cadence buys nothing.
     */
    fun isScrolling(): Boolean {
        val last = scrollClockNs[0]
        return last != 0L && System.nanoTime() - last < ScrollFreezeTailNs
    }

    private fun markScrolled() {
        scrollClockNs[0] = System.nanoTime()
    }

    fun expand() {
        isInline = false
        accumulatedScroll = 0f
    }

    fun inline() {
        isInline = true
        accumulatedScroll = 0f
    }

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        // If behavior is Never, don't change state. The scroll clock is still stamped:
        // a bar that never collapses still sits over moving content, and freezing its
        // backdrop is worth just as much there.
        if (inlineBehavior == FloatingTabBarInlineBehavior.Never) {
            markScrolled()
            return Offset.Zero
        }

        markScrolled()
        // EMA rather than a raw per-event overwrite: onPreScroll deltas are noisy
        // frame to frame (a real list doesn't move the same px every callback), and
        // an unsmoothed value read straight into the live phase above would jitter
        // the bar's scale instead of tracking scroll speed.
        smoothedVelocityPx[0] = smoothedVelocityPx[0] * (1f - MomentumVelocityEmaAlpha) +
            available.y * MomentumVelocityEmaAlpha
        lastDeltaClockNs[0] = System.nanoTime()

        val scrollDelta = available.y

        // Reset accumulated scroll if changing direction
        if ((accumulatedScroll > 0 && scrollDelta < 0) || (accumulatedScroll < 0 && scrollDelta > 0)) {
            accumulatedScroll = 0f
        }

        // Accumulate scroll
        accumulatedScroll += scrollDelta

        when (inlineBehavior) {
            FloatingTabBarInlineBehavior.OnScrollDown -> {
                // Asymmetric on purpose. Collapsing wants hysteresis so a jittery
                // downward scroll can't flicker the bar, and the distance is free
                // there — the user is already moving away. Expanding is the
                // opposite: the bar is what they are reaching for, so making them
                // drag the same distance back reads as the bar being slow to
                // return. Coming back is near-immediate, with just enough travel
                // required that a shaky finger mid-scroll can't oscillate it.
                if (accumulatedScroll <= -scrollThresholdPx && !isInline) {
                    // Scrolling down enough - transition to inline mode
                    isInline = true
                    accumulatedScroll = 0f // Reset after state change
                } else if (accumulatedScroll >= expandThresholdPx && isInline) {
                    // Scrolling up enough - transition to expanded mode
                    isInline = false
                    accumulatedScroll = 0f // Reset after state change
                }
            }

            FloatingTabBarInlineBehavior.OnScrollUp -> {
                // Check if we've scrolled enough to trigger state change
                if (accumulatedScroll >= scrollThresholdPx && !isInline) {
                    // Scrolling up enough - transition to inline mode
                    isInline = true
                    accumulatedScroll = 0f // Reset after state change
                } else if (accumulatedScroll <= -scrollThresholdPx && isInline) {
                    // Scrolling down enough - transition to expanded mode
                    isInline = false
                    accumulatedScroll = 0f // Reset after state change
                }
            }

            FloatingTabBarInlineBehavior.Never -> {
                // Already handled above, but included for completeness
            }
        }

        return Offset.Zero // Don't consume the scroll, let it pass through
    }
}

/**
 * Creates and remembers a [FloatingTabBarScrollConnection] instance.
 *
 * @param initialIsInline Initial state of the tab bar (inline or expanded). Default is false.
 * @param scrollThreshold The scroll distance required to collapse to inline. Default is 50.dp.
 * @param expandThreshold The scroll distance required to expand back. Default is 8.dp — small
 * enough that the bar returns immediately on scroll up, large enough that a shaky finger
 * mid-scroll cannot oscillate it.
 * @param inlineBehavior Defines when the tab bar should transition to inline state. Default is [FloatingTabBarInlineBehavior.OnScrollDown].
 * @return A remembered [FloatingTabBarScrollConnection] instance.
 */
@Composable
fun rememberFloatingTabBarScrollConnection(
    initialIsInline: Boolean = false,
    scrollThreshold: Dp = 50.dp,
    expandThreshold: Dp = 8.dp,
    inlineBehavior: FloatingTabBarInlineBehavior = FloatingTabBarInlineBehavior.OnScrollDown
): FloatingTabBarScrollConnection = with(LocalDensity.current) {
    val scrollThresholdPx = scrollThreshold.toPx()
    val expandThresholdPx = expandThreshold.toPx()
    remember(scrollThresholdPx, expandThresholdPx, inlineBehavior, initialIsInline) {
        FloatingTabBarScrollConnection(
            initialIsInline,
            scrollThresholdPx,
            expandThresholdPx,
            inlineBehavior,
        )
    }
}

/**
 * Defines when the floating tab bar should transition to inline state.
 */
enum class FloatingTabBarInlineBehavior {
    /** Never transition to inline - it stays in expanded state */
    Never,

    /** Transition to inline when scrolling down */
    OnScrollDown,

    /** Transition to inline when scrolling up */
    OnScrollUp
}

interface FloatingTabBarScope {
    /**
     * Adds a regular tab to the floating tab bar.
     *
     * @param key Unique identifier for the tab
     * @param title Composable content for the tab title
     * @param icon Composable content for the tab icon
     * @param onClick Callback invoked when the tab is clicked
     * @param indication Optional indication provider for touch feedback, defaults to LocalIndication.current
     */
    fun tab(
        key: Any,
        title: @Composable () -> Unit,
        icon: @Composable () -> Unit,
        onClick: () -> Unit,
        indication: (@Composable () -> Indication)? = { LocalIndication.current }
    )

    /**
     * Adds a standalone tab to the floating tab bar.
     *
     * Note: Calling this method more than once will override the previous standalone tab value.
     *
     * @param key Unique identifier for the standalone tab
     * @param icon Composable content for the tab icon
     * @param onClick Callback invoked when the tab is clicked
     * @param indication Optional indication provider for touch feedback, defaults to LocalIndication.current
     * @param title Composable content for the tab title. Never actually shown —
     * the standalone tab is always an icon-only floating circle, in both inline
     * and expanded states (see InlineStandaloneTab / ExpandedStandaloneTab).
     */
    fun standaloneTab(
        key: Any,
        icon: @Composable () -> Unit,
        onClick: () -> Unit,
        indication: (@Composable () -> Indication)? = { LocalIndication.current },
        title: @Composable () -> Unit = {}
    )
}

/**
 * Plain release-triggered [clickable]. Firing on finger-DOWN (the old behaviour)
 * meant any touch that turned into a scroll or a puck drag still navigated first —
 * "opens a screen even when I didn't drop the puck". Release-fire lets Compose's
 * touch-slop arbitration cancel the tap when the gesture becomes a drag/scroll, so
 * only a real tap navigates. Keeps the custom [indication].
 */
@Composable
private fun Modifier.tapClickable(
    indication: Indication?,
    onClick: () -> Unit,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return clickable(
        onClick = onClick,
        indication = indication,
        interactionSource = interactionSource
    )
}

@Composable
private fun SharedTransitionScope.InlineBar(
    scope: FloatingTabBarScopeImpl,
    selectedTabKey: Any?,
    accessory: (@Composable SharedTransitionScope.(Modifier, AnimatedVisibilityScope) -> Unit)?,
    isAccessoryShared: Boolean,
    onInlineTabClick: () -> Unit,
    colors: FloatingTabBarColors,
    shapes: FloatingTabBarShapes,
    sizes: FloatingTabBarSizes,
    elevations: FloatingTabBarElevations,
    tabBarContentModifier: Modifier,
    animatedVisibilityScope: AnimatedVisibilityScope
) {
    val inlineTab = scope.getInlineTab(selectedTabKey)
    val standaloneTab = scope.standaloneTab
    val hasInlineTab = inlineTab != null

    // With an accessory the row spans the full width ([tab][accessory][standalone],
    // iOS 26 Apple Music style); without one, the cluster is packed and centered.
    Row(
        horizontalArrangement = Arrangement.spacedBy(sizes.componentSpacing),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (accessory == null) Modifier.wrapContentWidth() else Modifier)
            .height(SearchBarRowHeight)
    ) {
        if (hasInlineTab) {
            InlineTab(
                inlineTab = inlineTab,
                onInlineTabClick = onInlineTabClick,
                shapes = shapes,
                sizes = sizes,
                colors = colors,
                elevations = elevations,
                animatedVisibilityScope = animatedVisibilityScope,
                tabBarContentModifier = tabBarContentModifier,
                // Pinned to the row height so the shrunk tab pill always
                // matches the mini + search pills at 48dp.
                modifier = Modifier.fillMaxHeight()
            )
        }

        if (accessory != null) {
            InlineAccessory(
                accessory = accessory,
                isAccessoryShared = isAccessoryShared,
                shapes = shapes,
                colors = colors,
                elevations = elevations,
                animatedVisibilityScope = animatedVisibilityScope,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }

        if (standaloneTab != null) {
            InlineStandaloneTab(
                standaloneTab = standaloneTab,
                shapes = shapes,
                sizes = sizes,
                colors = colors,
                elevations = elevations,
                animatedVisibilityScope = animatedVisibilityScope,
                tabBarContentModifier = tabBarContentModifier,
                // fillMaxHeight() + the row's IntrinsicSize.Max is supposed to
                // match this to the docked mini player's height, but intrinsic
                // measurement can under-report the mini player's actual
                // (dynamically-measured) content height — floor it explicitly
                // so the standalone tab pill can't end up shorter.
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .fillMaxHeight()
                    // Height-first: aspectRatio resolves from maxWidth by default,
                    // so on a narrow bar the leftover width — not the row height —
                    // decided the circle's diameter and it came out smaller than the
                    // bar beside it.
                    .aspectRatio(1f, matchHeightConstraintsFirst = true)
            )
        }
    }
}

@Composable
private fun SharedTransitionScope.InlineTab(
    inlineTab: FloatingTabBarTab,
    onInlineTabClick: () -> Unit,
    shapes: FloatingTabBarShapes,
    sizes: FloatingTabBarSizes,
    colors: FloatingTabBarColors,
    elevations: FloatingTabBarElevations,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier,
    tabBarContentModifier: Modifier
) {
    // Same finger-tracking stretch as the standalone circle, so the shrunk
    // group tab distorts under the finger instead of sitting dead.
    val glowScope = rememberCoroutineScope()
    val glow = remember(glowScope) { InteractiveHighlight(animationScope = glowScope) }

    Box(
        modifier = modifier
            // Outside sharedElement: draw-only, so bounds tracking is
            // unaffected, and no overlay clipping can cut the stretch.
            .graphicsLayer(elasticStretchLayerBlock(glow))
            .shadow(
                shape = shapes.tabBarShape,
                elevation = elevations.inlineElevation
            )
            .background(
                color = colors.backgroundColor,
                shape = shapes.tabBarShape
            )
            // No clip: the background above already draws the pill shape, and
            // any clip here would cut the outer elastic stretch back to bounds.
            .then(tabBarContentModifier)
            .then(glow.gestureModifier)
            .then(glow.modifier)
            .tapClickable(
                indication = inlineTab.indication?.invoke(),
                onClick = {
                    onInlineTabClick()
                    inlineTab.onClick()
                }
            )
            .padding(sizes.tabInlineContentPadding)
    ) {
        Tab(
            // Q1 fix: no per-icon sharedElement -- the icon rides the tabGroup's
            // own shared bounds. A nested shared node double-drove the icon
            // (overlay bounds inside overlay bounds) = self-readjust glitch.
            icon = { inlineTab.icon() },
            title = { inlineTab.title() },
            isInline = true
        )
    }
}

@Composable
private fun SharedTransitionScope.InlineStandaloneTab(
    standaloneTab: FloatingTabBarTab,
    shapes: FloatingTabBarShapes,
    sizes: FloatingTabBarSizes,
    colors: FloatingTabBarColors,
    elevations: FloatingTabBarElevations,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier,
    tabBarContentModifier: Modifier
) {
    // Same finger-tracking glow as the keyboard-active search pill
    // (NavBarSearchInputBar) — a soft radial light following the touch point.
    val glowScope = rememberCoroutineScope()
    val glow = remember(glowScope) { InteractiveHighlight(animationScope = glowScope) }

    Tab(
        icon = standaloneTab.icon,
        title = standaloneTab.title,
        isInline = true,
        isStandalone = true,
        modifier = modifier
            // No outer inset: the circle holds the full row height so it is
            // exactly as tall as the nav bar. The elastic stretch below is
            // draw-only and protrudes past bounds momentarily, which the
            // unclipped bar shows on purpose.
            // Outside sharedElement: draw-only, so bounds tracking is
            // unaffected, and no overlay clipping can cut the stretch.
            .graphicsLayer(elasticStretchLayerBlock(glow))
            .sharedElement(
                sharedContentState = rememberSharedContentState("standaloneTab"),
                animatedVisibilityScope = animatedVisibilityScope,
                boundsTransform = LocalBarBoundsTransforms.current.bar,
                zIndexInOverlay = 1f
            )
            .shadow(
                shape = shapes.standaloneTabShape,
                elevation = elevations.inlineElevation
            )
            .background(
                color = colors.backgroundColor,
                shape = shapes.standaloneTabShape
            )
            // No clip: the background above already draws the circle, and
            // any clip here would cut the outer elastic stretch back to bounds.
            .then(tabBarContentModifier)
            .then(glow.gestureModifier)
            .then(glow.modifier)
            .tapClickable(
                indication = standaloneTab.indication?.invoke(),
                onClick = standaloneTab.onClick
            )
            // Unlike the regular tab pill (InlineTab, ExpandedTabs), this circle had
            // no inset at all — the icon sat flush against its edge.
            .padding(sizes.tabInlineContentPadding)
    )
}

@Composable
private fun SharedTransitionScope.InlineAccessory(
    accessory: (@Composable SharedTransitionScope.(Modifier, AnimatedVisibilityScope) -> Unit)?,
    isAccessoryShared: Boolean,
    colors: FloatingTabBarColors,
    shapes: FloatingTabBarShapes,
    elevations: FloatingTabBarElevations,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier
) {
    accessory?.let { accessory ->
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .then(
                    if (isAccessoryShared) {
                        Modifier.sharedElement(
                            sharedContentState = rememberSharedContentState("accessory"),
                            animatedVisibilityScope = animatedVisibilityScope,
                            boundsTransform = LocalBarBoundsTransforms.current.accessory,
                        )
                    } else {
                        Modifier.animateEnterExitAccessory(
                            sharedTransitionScope = this,
                            animatedVisibilityScope = animatedVisibilityScope
                        )
                    }
                )
        ) {
            accessory(
                Modifier
                    .fillMaxSize()
                    .shadow(
                        shape = shapes.accessoryShape,
                        elevation = elevations.inlineElevation
                    )
                    .background(color = colors.accessoryBackgroundColor, shapes.accessoryShape)
                    .clip(shapes.accessoryShape),
                animatedVisibilityScope
            )
        }
    }
}

@Composable
private fun SharedTransitionScope.ExpandedBar(
    scope: FloatingTabBarScopeImpl,
    selectedTabKey: Any?,
    tabPosition: (() -> Float?)?,
    accessory: (@Composable SharedTransitionScope.(Modifier, AnimatedVisibilityScope) -> Unit)?,
    isAccessoryShared: Boolean,
    colors: FloatingTabBarColors,
    shapes: FloatingTabBarShapes,
    sizes: FloatingTabBarSizes,
    elevations: FloatingTabBarElevations,
    tabBarContentModifier: Modifier,
    animatedVisibilityScope: AnimatedVisibilityScope,
    backdrop: Backdrop?,
    accentColor: Color?,
    // Vendored addition: reports this row's own measured width upward so
    // SearchExpandedBar can hold the bar at the exact same overall width
    // instead of expanding to fill all available space in search mode.
    onWidthMeasured: ((Int) -> Unit)? = null,
) {
    val hasTabGroup = scope.tabs.isNotEmpty()
    val standaloneTab = scope.standaloneTab

    // The accessory matches the tab row's own (content-sized, not full-bleed)
    // width, so both pills line up edge to edge. The tab row is measured first
    // (onSizeChanged below); the accessory falls back to fillMaxWidth until
    // that first measurement lands — a one-frame lag, same pattern as
    // FloatingMiniPlayer's measuredHeightPx.
    val density = LocalDensity.current
    var tabRowWidthPx by remember { mutableIntStateOf(0) }

    // The standalone tab (search) is always its own floating circle — same as
    // the inline (collapsed) state — never merged into the tab group pill, so
    // it reads as one consistent circular element through both inline and
    // expanded states (see ExpandedStandaloneTab / InlineStandaloneTab, tied
    // together by the shared "standaloneTab" element).
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(sizes.componentSpacing),
        modifier = Modifier.fillMaxWidth()
    ) {
        if (accessory != null) {
            ExpandedAccessory(
                accessory = accessory,
                isAccessoryShared = isAccessoryShared,
                shapes = shapes,
                colors = colors,
                elevations = elevations,
                animatedVisibilityScope = animatedVisibilityScope,
                modifier = if (tabRowWidthPx > 0) {
                    Modifier.width(with(density) { tabRowWidthPx.toDp() })
                } else {
                    Modifier.fillMaxWidth()
                }
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(sizes.componentSpacing),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .height(IntrinsicSize.Max)
                .onSizeChanged {
                    tabRowWidthPx = it.width
                    onWidthMeasured?.invoke(it.width)
                }
        ) {
            if (hasTabGroup) {
                ExpandedTabs(
                    scope = scope,
                    selectedTabKey = selectedTabKey,
                    tabPosition = tabPosition,
                    shapes = shapes,
                    sizes = sizes,
                    colors = colors,
                    elevations = elevations,
                    animatedVisibilityScope = animatedVisibilityScope,
                    tabBarContentModifier = tabBarContentModifier,
                    backdrop = backdrop,
                    accentColor = accentColor ?: colors.backgroundColor,
                    modifier = Modifier
                )
            }

            if (standaloneTab != null) {
                ExpandedStandaloneTab(
                    standaloneTab = standaloneTab,
                    shapes = shapes,
                    sizes = sizes,
                    colors = colors,
                    elevations = elevations,
                    animatedVisibilityScope = animatedVisibilityScope,
                    tabBarContentModifier = tabBarContentModifier,
                    modifier = Modifier
                        // Same floor the inline row already needed: IntrinsicSize.Max
                        // can under-report the tab group's real height, and without a
                        // floor this circle shrinks to that under-reported value while
                        // the tab pill measures its true (taller) content — which is
                        // the search pill looking smaller than the nav bar.
                        .defaultMinSize(minHeight = 48.dp)
                        .fillMaxHeight()
                        // Height-first, as in the inline row: the default resolves the
                        // square from maxWidth, so leftover row width could decide the
                        // diameter instead of the bar height.
                        .aspectRatio(1f, matchHeightConstraintsFirst = true)
                )
            }
        }
    }
}

/**
 * Vendored addition: the search-active expanded shape. Same accessory-above
 * treatment as [ExpandedBar], but the row below it is the collapsed-style
 * single current-screen icon ([InlineTab], reused as-is) next to
 * [searchBarContent] filling the rest of the row — instead of the full
 * tab-group pill + circular standalone tab.
 */
@Composable
private fun SharedTransitionScope.SearchExpandedBar(
    scope: FloatingTabBarScopeImpl,
    selectedTabKey: Any?,
    accessory: (@Composable SharedTransitionScope.(Modifier, AnimatedVisibilityScope) -> Unit)?,
    isAccessoryShared: Boolean,
    colors: FloatingTabBarColors,
    shapes: FloatingTabBarShapes,
    sizes: FloatingTabBarSizes,
    elevations: FloatingTabBarElevations,
    tabBarContentModifier: Modifier,
    animatedVisibilityScope: AnimatedVisibilityScope,
    searchBarContent: @Composable (Modifier) -> Unit,
    // Vendored addition: the normal-expanded row's own last-measured width
    // (see ExpandedBar's onWidthMeasured) — holding this row to that same
    // width keeps the bar's overall footprint constant across search mode
    // instead of the searchBarContent's weight(1f) filling all available
    // space. Null/0 (nothing measured yet) falls back to filling available
    // width.
    targetWidthPx: Int?,
) {
    val inlineTab = scope.getInlineTab(selectedTabKey)
    val density = LocalDensity.current
    // Stretch tracker for the search pill itself. Only the layer + gesture
    // here, no glow draw — the placeholder inside paints its own light.
    val searchStretchScope = rememberCoroutineScope()
    val searchStretch = remember(searchStretchScope) { InteractiveHighlight(animationScope = searchStretchScope) }
    val targetWidthModifier = if (targetWidthPx != null && targetWidthPx > 0) {
        Modifier.width(with(density) { targetWidthPx.toDp() })
    } else {
        Modifier.fillMaxWidth()
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(sizes.componentSpacing),
        modifier = Modifier.fillMaxWidth()
    ) {
        if (accessory != null) {
            ExpandedAccessory(
                accessory = accessory,
                isAccessoryShared = isAccessoryShared,
                shapes = shapes,
                colors = colors,
                elevations = elevations,
                animatedVisibilityScope = animatedVisibilityScope,
                modifier = targetWidthModifier
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(sizes.componentSpacing),
            verticalAlignment = Alignment.CenterVertically,
            // Fixed rather than IntrinsicSize.Max: matches the mini player's own
            // inline (shrunk) height exactly instead of letting a short
            // searchBarContent under-report its intrinsic height and shrink the row.
            modifier = Modifier.height(SearchBarRowHeight).then(targetWidthModifier)
        ) {
            if (inlineTab != null) {
                InlineTab(
                    inlineTab = inlineTab,
                    // Already expanded — a tap here should just navigate
                    // (inlineTab.onClick, fired inside InlineTab itself), not
                    // trigger the inline scroll-connection's expand().
                    onInlineTabClick = {},
                    shapes = shapes,
                    sizes = sizes,
                    colors = colors,
                    elevations = elevations,
                    animatedVisibilityScope = animatedVisibilityScope,
                    tabBarContentModifier = tabBarContentModifier,
                    modifier = Modifier.fillMaxHeight()
                )
            }

            searchBarContent(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    // Same "standaloneTab" key as ExpandedStandaloneTab/InlineStandaloneTab
                    // (the search circle) — without this the circle just pops out and the
                    // bar pops in as two unrelated fades instead of one smoothly growing
                    // pill; this is what makes the tab-group icon side already morph
                    // smoothly, and lets the search-inline (scrolled) state morph back
                    // down to a circle too.
                    // Elastic sits outside sharedElement: draw-only, so bounds
                    // tracking is unaffected, and no overlay clipping cuts it.
                    .graphicsLayer(elasticStretchLayerBlock(searchStretch))
                    .sharedElement(
                        sharedContentState = rememberSharedContentState("standaloneTab"),
                        animatedVisibilityScope = animatedVisibilityScope,
                        boundsTransform = LocalBarBoundsTransforms.current.bar,
                        zIndexInOverlay = 1f
                    )
                    .then(searchStretch.gestureModifier)
                    .shadow(shape = shapes.tabBarShape, elevation = elevations.expandedElevation)
                    .background(color = colors.backgroundColor, shape = shapes.tabBarShape)
                    // No clip: the background above already draws the pill, and
                    // any clip here would cut the outer elastic stretch to bounds.
                    .then(tabBarContentModifier)
            )
        }
    }
}

@Composable
private fun SharedTransitionScope.ExpandedStandaloneTab(
    standaloneTab: FloatingTabBarTab,
    shapes: FloatingTabBarShapes,
    sizes: FloatingTabBarSizes,
    colors: FloatingTabBarColors,
    elevations: FloatingTabBarElevations,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier,
    tabBarContentModifier: Modifier
) {
    // Same finger-tracking glow as the keyboard-active search pill
    // (NavBarSearchInputBar) — a soft radial light following the touch point.
    val glowScope = rememberCoroutineScope()
    val glow = remember(glowScope) { InteractiveHighlight(animationScope = glowScope) }

    Tab(
        icon = standaloneTab.icon,
        title = standaloneTab.title,
        isInline = true,
        isStandalone = true,
        modifier = modifier
            // No outer inset: the circle holds the full row height so it is
            // exactly as tall as the nav bar (see InlineStandaloneTab).
            // Outside sharedElement: draw-only, so bounds tracking is
            // unaffected, and no overlay clipping can cut the stretch.
            .graphicsLayer(elasticStretchLayerBlock(glow))
            .sharedElement(
                sharedContentState = rememberSharedContentState("standaloneTab"),
                animatedVisibilityScope = animatedVisibilityScope,
                boundsTransform = LocalBarBoundsTransforms.current.bar,
                zIndexInOverlay = 1f
            )
            .shadow(
                shape = shapes.standaloneTabShape,
                elevation = elevations.expandedElevation
            )
            .background(
                color = colors.backgroundColor,
                shape = shapes.standaloneTabShape
            )
            // No clip: the background above already draws the circle, and
            // any clip here would cut the outer elastic stretch back to bounds.
            .then(tabBarContentModifier)
            .then(glow.gestureModifier)
            .then(glow.modifier)
            .tapClickable(
                indication = standaloneTab.indication?.invoke(),
                onClick = standaloneTab.onClick
            )
            // Unlike the regular tab pill (ExpandedTabs), this circle had no inset
            // at all — the icon sat flush against its edge.
            .padding(sizes.tabExpandedContentPadding)
    )
}

@Composable
private fun SharedTransitionScope.ExpandedAccessory(
    accessory: @Composable SharedTransitionScope.(Modifier, AnimatedVisibilityScope) -> Unit,
    isAccessoryShared: Boolean,
    colors: FloatingTabBarColors,
    shapes: FloatingTabBarShapes,
    elevations: FloatingTabBarElevations,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier
) {
    Box(
        modifier = modifier
            .then(
                if (isAccessoryShared) {
                    Modifier.sharedElement(
                        sharedContentState = rememberSharedContentState("accessory"),
                        animatedVisibilityScope = animatedVisibilityScope,
                        boundsTransform = LocalBarBoundsTransforms.current.accessory,
                    )
                } else {
                    Modifier.animateEnterExitAccessory(
                        sharedTransitionScope = this,
                        animatedVisibilityScope = animatedVisibilityScope
                    )
                }
            )
    ) {
        accessory(
            Modifier
                .shadow(
                    shape = shapes.accessoryShape,
                    elevation = elevations.expandedElevation
                )
                .background(color = colors.accessoryBackgroundColor, shapes.accessoryShape)
                .clip(shapes.accessoryShape),
            animatedVisibilityScope
        )
    }
}
@Composable
private fun SharedTransitionScope.ExpandedTabs(
    scope: FloatingTabBarScopeImpl,
    selectedTabKey: Any?,
    tabPosition: (() -> Float?)?,
    shapes: FloatingTabBarShapes,
    sizes: FloatingTabBarSizes,
    colors: FloatingTabBarColors,
    elevations: FloatingTabBarElevations,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier,
    tabBarContentModifier: Modifier,
    backdrop: Backdrop?,
    accentColor: Color
) {
    // The standalone tab (e.g. search) never appears here — it's always its
    // own floating circle, rendered as a sibling by ExpandedBar (see
    // ExpandedStandaloneTab). Only the regular tabs form this pill/puck group.
    val allTabs = scope.tabs
    val tabsCount = allTabs.size
    if (tabsCount == 0) return
    // dampedDragAnimation is remember(...)-ed and its onDragStopped closure would
    // otherwise capture the FIRST composition's allTabs — freezing each tab's
    // onClick (and the isSelected it closed over). That stranded drag-to-Home:
    // Home started selected, so its frozen onClick did scroll-to-top forever
    // instead of navigating. Read the live tabs through this instead.
    val currentTabs = rememberUpdatedState(allTabs)

    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val isLtr = layoutDirection == LayoutDirection.Ltr
    val animationScope = rememberCoroutineScope()
    val glassConfig = LocalGlassEffectConfig.current
    // Puck wash. Explicit user color wins; on hero-art screens the pill takes
    // the mesh hue lighter than the surface with just a hint of tint; unset
    // elsewhere follows the app accent so the pill tracks whatever is playing.
    val ambientPuckColor = LocalAmbientBackgroundColor.current.value
    val themeDark = AppTheme.colorScheme.surface.luminance() <= 0.5f
    val puckWash = if (glassConfig.puckColor.isSpecified) {
        glassConfig.puckColor
    } else if (ambientPuckColor.isSpecified && !ambientPuckColor.isHueless()) {
        ambientPuckColor.asPuckTint(themeDark)
    } else {
        com.convx.music.ui.theme.GlassTintTokens.neutralTintColor(themeDark)
    }
    // Spec Q3/Q4: select pill .80 dark / .67 light at rest; hero keeps .80/.70.
    val isHeroContext = ambientPuckColor.isSpecified && !ambientPuckColor.isHueless()
    val puckRestAlpha = if (!glassConfig.puckColor.isSpecified) {
        if (themeDark) {
            0.80f
        } else {
            if (isHeroContext) 0.70f else 0.67f
        }
    } else {
        glassConfig.puckOpacity.coerceIn(0f, 1f)
    }

    val tabWidthPx = with(density) { sizes.tabWidth.toPx() }
    // The row's own content padding insets the tabs from the pill's edges, so
    // the puck (a sibling, not a row child) has to account for it explicitly —
    // otherwise it drifts out of alignment with where the tabs actually sit.
    val paddingStartPx = with(density) { sizes.tabBarContentPadding.calculateStartPadding(layoutDirection).toPx() }
    val paddingEndPx = with(density) { sizes.tabBarContentPadding.calculateEndPadding(layoutDirection).toPx() }
    val totalWidthPx = tabWidthPx * tabsCount + paddingStartPx + paddingEndPx

    // Ported from LiquidBottomTabs: the whole pill nudges a few dp in the drag
    // direction (eased, clamped) so it feels like it's being tugged, not just
    // the puck sliding inside a static shell.
    val offsetAnimation = remember(tabsCount) { Animatable(0f) }
    val panelOffset by remember(density, totalWidthPx) {
        derivedStateOf {
            val fraction = (offsetAnimation.value / totalWidthPx).fastCoerceIn(-1f, 1f)
            with(density) {
                4f.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction))
            }
        }
    }

    var currentIndex by remember(tabsCount) {
        mutableIntStateOf(
            allTabs.indexOfFirst { it.key == selectedTabKey }.coerceIn(0, tabsCount - 1)
        )
    }
    // Break the construction-time self-reference: canDrag needs the animation's current value.
    class DampedDragHolder {
        var instance: DampedDragAnimation? = null
    }
    val holder = remember { DampedDragHolder() }
    val dampedDragAnimation = remember(animationScope, tabsCount, density, isLtr) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = currentIndex.toFloat(),
            valueRange = 0f..(tabsCount - 1).toFloat(),
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 78f / 56f,
            canDrag = { offset ->
                val anim = holder.instance ?: return@DampedDragAnimation true
                if (tabWidthPx == 0f) return@DampedDragAnimation false
                val indicatorX = anim.value * tabWidthPx
                val pad = with(density) { 4.dp.toPx() }
                val globalTouchX = if (isLtr) {
                    pad + indicatorX + offset.x
                } else {
                    totalWidthPx - pad - tabWidthPx - indicatorX + offset.x
                }
                globalTouchX in 0f..totalWidthPx
            },
            onDragStarted = {},
            onDragStopped = {
                val targetIndex = targetValue.fastRoundToInt().coerceIn(0, tabsCount - 1)
                if (currentIndex != targetIndex) {
                    currentIndex = targetIndex
                } else {
                    animateToValue(targetIndex.toFloat())
                }
                animationScope.launch {
                    offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                }
            },
            onDrag = { _, dragAmount ->
                if (tabWidthPx > 0f) {
                    updateValue(
                        (targetValue + dragAmount.x / tabWidthPx * if (isLtr) 1f else -1f)
                            .fastCoerceIn(0f, (tabsCount - 1).toFloat())
                    )
                    animationScope.launch {
                        offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                    }
                }
            },
        ).also { holder.instance = it }
    }
    // Keep click/drag selection and the moving puck synchronized through one index,
    // exactly as Melox does. The remembered tab list prevents stale callback captures.
    LaunchedEffect(dampedDragAnimation) {
        snapshotFlow { currentIndex }
            .drop(1)
            .collectLatest { index ->
                dampedDragAnimation.animateToValue(index.toFloat())
                currentTabs.value.getOrNull(index)?.onClick?.invoke()
            }
    }

    // While an external pager is moving, follow its fractional position directly.
    if (tabPosition != null) {
        LaunchedEffect(dampedDragAnimation, tabPosition) {
            snapshotFlow { tabPosition() }.collect { position ->
                if (position != null) dampedDragAnimation.snapValue(position)
            }
        }
    }

    // External navigation updates the same local index; the collector above owns
    // the Melox press-grow-and-settle animation and click callback.
    LaunchedEffect(selectedTabKey, tabsCount) {
        val index = allTabs.indexOfFirst { it.key == selectedTabKey }
        if (index != -1 && currentIndex != index) currentIndex = index
    }

    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(
            animationScope = animationScope,
            position = { size, offset ->
                Offset(
                    if (isLtr) paddingStartPx + (dampedDragAnimation.value + 0.5f) * tabWidthPx + panelOffset
                    else size.width - paddingStartPx - (dampedDragAnimation.value + 0.5f) * tabWidthPx + panelOffset,
                    size.height / 2f
                )
            }
        )
    }
    // Press-only glass: see the hidden row below. rememberLayerBackdrop is just a
    // holder; nothing records into it until that row is composed.
    val tabsBackdrop = rememberLayerBackdrop()
    val combinedBackdrop = if (backdrop != null) rememberCombinedBackdrop(backdrop, tabsBackdrop) else null
    val pressed by remember(dampedDragAnimation) {
        derivedStateOf { dampedDragAnimation.pressProgress > 0f }
    }

    Box(modifier.width(with(density) { totalWidthPx.toDp() })) {
        Row(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = panelOffset
                    val width = size.width.coerceAtLeast(1f)
                    val pressScale = lerp(
                        1f,
                        1f + 16.dp.toPx() / width,
                        dampedDragAnimation.pressProgress,
                    )
                    scaleX = pressScale
                    scaleY = pressScale
                }
                .shadow(
                    shape = shapes.tabBarShape,
                    elevation = elevations.expandedElevation
                )
                .background(
                    color = colors.backgroundColor,
                    shape = shapes.tabBarShape
                )
                .clip(shapes.tabBarShape)
                .then(tabBarContentModifier)
                .then(interactiveHighlight.modifier)
                .padding(sizes.tabBarContentPadding),
            horizontalArrangement = Arrangement.spacedBy(sizes.tabSpacing)
        ) {
            allTabs.forEachIndexed { index, tab ->
                Tab(
                    icon = {
                        Box(
                            modifier = Modifier
                                .graphicsLayer {
                                    // Unselected tabs sit back; the selected one is
                                    // never dimmed, so the puck must not darken it.
                                    alpha = if (tab.key == selectedTabKey) 1f else 0.6f
                                    // A tab icon is a single Icon draw, so there is no
                                    // self-overlap for an offscreen buffer to resolve.
                                    // CompositingStrategy.Auto would still allocate one
                                    // per dimmed tab because alpha < 1; modulating the
                                    // alpha into the draw is pixel-identical here and
                                    // costs no render target. Same call this file's
                                    // animateEnterExitAccessory already makes.
                                    compositingStrategy = CompositingStrategy.ModulateAlpha
                                }
                        ) {
                            tab.icon()
                        }
                    },
                    title = {
                        Box {
                            tab.title()
                        }
                    },
                    isInline = false,
                    isStandalone = false,
                    contentScale = if (index == currentIndex) {
                        lerp(1f, 1.12f, dampedDragAnimation.pressProgress)
                    } else {
                        1f
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .skipToLookaheadSize()
                        .clip(shapes.tabShape)
                        .tapClickable(
                            indication = tab.indication?.invoke(),
                            onClick = { currentIndex = index }
                        )
                        .padding(sizes.tabExpandedContentPadding)
                )
            }
        }

        // Press-only glass (Melox's liquid pill). While the puck is held or settling,
        // an invisible accent-tinted copy of the tabs is recorded and the puck lenses
        // it together with the page behind the bar. At rest none of this is composed,
        // so an idle or scrolling screen pays nothing for it -- that per-frame cost is
        // why the always-on version was removed in 03df0494.
        if (backdrop != null && pressed) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clearAndSetSemantics {}
                    .alpha(0f)
                    .layerBackdrop(tabsBackdrop)
                    .graphicsLayer { translationX = panelOffset }
                    // Before drawBackdrop so it insets this row's own bounds, exactly
                    // once, matching the visible row -- or the sampled icons drift.
                    .padding(sizes.tabBarContentPadding)
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { shapes.tabBarShape },
                        effects = {
                            vibrancy()
                            blur(4f.dp.toPx())
                            lens(24f.dp.toPx(), 24f.dp.toPx())
                        },
                        highlight = null,
                        shadow = null,
                        onDrawSurface = { drawRect(colors.backgroundColor) },
                    )
                    .then(interactiveHighlight.modifier)
                    // After drawBackdrop: tints only the icons, not the glass sample.
                    .graphicsLayer(colorFilter = ColorFilter.tint(accentColor)),
                horizontalArrangement = Arrangement.spacedBy(sizes.tabSpacing)
            ) {
                allTabs.forEachIndexed { index, tab ->
                    Tab(
                        icon = tab.icon,
                        title = tab.title,
                        isInline = false,
                        isStandalone = false,
                        // Must zoom in sync with the visible row or the copy ghosts.
                        contentScale = if (index == currentIndex) {
                            lerp(1f, 1.12f, dampedDragAnimation.pressProgress)
                        } else {
                            1f
                        },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .skipToLookaheadSize()
                            .padding(sizes.tabExpandedContentPadding)
                    )
                }
            }
        }

        // The draggable selection puck. Position and press/velocity-driven
        // scale come from dampedDragAnimation; tapping a tab still works via
        // each tab's own clickable — the puck only intercepts drags.
        Box(
            Modifier
                // The pill's own content padding and NOTHING else, so the gap above,
                // below and beside the puck is the same number on all four sides --
                // that even inset is what the reference bars have, and an extra
                // vertical inset here is what made the side gap read as wrong and
                // left the capsule short of the bar's inner height.
                .padding(sizes.tabBarContentPadding)
                .graphicsLayer {
                    translationX =
                        if (isLtr) dampedDragAnimation.value * tabWidthPx + panelOffset
                        else size.width - (dampedDragAnimation.value + 1f) * tabWidthPx + panelOffset
                }
                .then(interactiveHighlight.gestureModifier)
                .then(dampedDragAnimation.modifier)
                .width(sizes.tabWidth)
                .fillMaxHeight()
                .then(
                    if (combinedBackdrop != null && pressed) {
                        // Melox's pressed pill: lens + dispersion over the page and the
                        // tinted tab copy, specular rim and inner shadow, all scaled by
                        // press progress so it grows out of the flat rest state below.
                        Modifier.drawBackdrop(
                            backdrop = combinedBackdrop,
                            shape = { shapes.tabShape },
                            effects = {
                                val progress = dampedDragAnimation.pressProgress
                                lens(
                                    refractionHeight = 10f.dp.toPx() * progress,
                                    refractionAmount = 14f.dp.toPx() * progress,
                                    depthEffect = true,
                                    chromaticAberration = true,
                                    chromaticAberrationAmount = 0.5f,
                                )
                            },
                            highlight = {
                                Highlight(width = 1.dp, alpha = dampedDragAnimation.pressProgress)
                            },
                            shadow = null,
                            innerShadow = {
                                val progress = dampedDragAnimation.pressProgress
                                if (progress > 0f) {
                                    InnerShadow(
                                        radius = 8f.dp * progress,
                                        color = Color.Black.copy(alpha = 0.15f),
                                        alpha = progress,
                                    )
                                } else {
                                    null
                                }
                            },
                            layerBlock = {
                                scaleX = dampedDragAnimation.scaleX
                                scaleY = dampedDragAnimation.scaleY
                                val velocity = dampedDragAnimation.velocity / 10f
                                scaleX /= 1f - (velocity * 0.75f).coerceIn(-0.2f, 0.2f)
                                scaleY *= 1f - (velocity * 0.25f).coerceIn(-0.2f, 0.2f)
                            },
                            onDrawSurface = {
                                val progress = dampedDragAnimation.pressProgress
                                drawRect(
                                    if (themeDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.1f),
                                    alpha = 1f - progress,
                                )
                                drawRect(Color.Black.copy(alpha = 0.03f * progress))
                            },
                        )
                    } else Modifier
                        .graphicsLayer {
                            scaleX = dampedDragAnimation.scaleX
                            scaleY = dampedDragAnimation.scaleY
                            // Velocity-driven stretch/squash: the same asymmetric
                            // deformation the old lens layerBlock applied, now on a
                            // flat shape instead of a sampled one.
                            val velocity = dampedDragAnimation.velocity / 10f
                            scaleX /= 1f - (velocity * 0.75f).coerceIn(-0.2f, 0.2f)
                            scaleY *= 1f - (velocity * 0.25f).coerceIn(-0.2f, 0.2f)
                        }
                        // Melox's pill surface: a 10% neutral wash at rest that fades out
                        // as the press grows, leaving only a 3% dark film while dragged.
                        // The old fill was colors.backgroundColor ramped to full alpha --
                        // Color.Transparent on glass, i.e. black, so a drag painted an
                        // opaque black capsule over the icons. The puck sits above the
                        // tab row, so it has to stay see-through at every press level.
                        .drawWithCache {
                            val outline = shapes.tabShape.createOutline(size, layoutDirection, this)
                            val wash = if (themeDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.1f)
                            onDrawBehind {
                                val progress = dampedDragAnimation.pressProgress
                                drawOutline(outline, color = wash, alpha = 1f - progress)
                                drawOutline(outline, color = Color.Black.copy(alpha = 0.03f * progress))
                            }
                        }
                )
        )
    }
}

@Composable
private fun Tab(
    icon: @Composable () -> Unit,
    title: @Composable () -> Unit,
    isInline: Boolean,
    modifier: Modifier = Modifier,
    isStandalone: Boolean = false,
    // Vendored addition: press-zoom scale for the icon+label, driven by the
    // drag puck's press progress (Kyant's LocalLiquidBottomTabScale trick).
    contentScale: Float = 1f
) {
    val showTitle = !isStandalone && !isInline
    Column(
        // A real gap between icon and title, centered as a group — rather than
        // nudging the icon down with a raw offset(), which shifts its drawn
        // position without growing the Column's measured height and gets it
        // clipped by the tab bar's own bounds.
        verticalArrangement = if (showTitle) {
            Arrangement.spacedBy(2.dp, Alignment.CenterVertically)
        } else {
            Arrangement.Center
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .then(
                if (contentScale != 1f) {
                    Modifier.graphicsLayer {
                        scaleX = contentScale
                        scaleY = contentScale
                    }
                } else {
                    Modifier
                }
            )
    ) {
        icon()
        if (showTitle) {
            title()
        }
    }
}

/**
 * A custom modifier that provides smooth enter/exit animations without clipping shadows or other content.
 * This is an alternative to animateEnterExit that uses renderInSharedTransitionScopeOverlay to prevent clipping.
 */
@Composable
private fun Modifier.animateEnterExitAccessory(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope
): Modifier = with(sharedTransitionScope) {
    with(animatedVisibilityScope) {
        val animatedAlpha by transition.animateFloat { targetState ->
            when (targetState) {
                EnterExitState.Visible -> 1f
                else -> 0f
            }
        }

        this@animateEnterExitAccessory
            .renderInSharedTransitionScopeOverlay()
            .graphicsLayer(
                compositingStrategy = CompositingStrategy.ModulateAlpha,
                alpha = animatedAlpha
            )
    }
}

/**
 * A custom modifier that provides smooth enter/exit animations with fade and blur effects.
 */
@Composable
private fun Modifier.animateEnterExitTab(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope
): Modifier = with(sharedTransitionScope) {
    with(animatedVisibilityScope) {
        val enterStartFraction = 0.5f
        val enterEndFraction = 0.8f
        val durationMs = 150

        val animatedAlpha by transition.animateFloat(
            transitionSpec = {
                keyframes {
                    durationMillis = durationMs
                    if (targetState == EnterExitState.Visible) {
                        0f atFraction enterStartFraction using FastOutSlowInEasing
                        1f atFraction enterEndFraction
                    }
                }
            }
        ) { targetState ->
            when (targetState) {
                EnterExitState.Visible -> 1f
                else -> 0f
            }
        }

        graphicsLayer {
            // The tab group already scales/translates these pixels on the shared
            // 300 ms bounds clock. A second 50 dp blur clock made each icon resolve
            // independently mid-morph (the visible readjust/shimmer) and allocated
            // an extra offscreen layer per icon/title. Fade only: one transform clock,
            // identical settled pixels, and substantially less transition work.
            alpha = animatedAlpha
        }
    }
}

private class FloatingTabBarScopeImpl : FloatingTabBarScope {
    val tabs = mutableStateListOf<FloatingTabBarTab>()
    var standaloneTab: FloatingTabBarTab? by mutableStateOf(null)
        private set
    private var inlineTab: FloatingTabBarTab? = null

    fun getInlineTab(selectedTabKey: Any?): FloatingTabBarTab? {
        // Screen-accurate: only ever report a regular tab as "the inline tab"
        // when it's actually the current screen. selectedTabKey not matching
        // any regular tab (e.g. currently on the standalone tab) falls back to
        // the last regular tab that really was selected — never a hardcoded
        // default like tabs.firstOrNull(), which would misrepresent Home as
        // selected when the user has never actually been there.
        val selectedTab = tabs.find { it.key == selectedTabKey }
        if (selectedTab != null) {
            inlineTab = selectedTab
            return selectedTab
        }
        return inlineTab
    }

    override fun tab(
        key: Any,
        title: @Composable () -> Unit,
        icon: @Composable () -> Unit,
        onClick: () -> Unit,
        indication: (@Composable () -> Indication)?
    ) {
        tabs.add(
            FloatingTabBarTab(
                key = key,
                title = title,
                icon = icon,
                onClick = onClick,
                indication = indication
            )
        )
    }
    
    override fun standaloneTab(
        key: Any,
        icon: @Composable () -> Unit,
        onClick: () -> Unit,
        indication: (@Composable () -> Indication)?,
        title: @Composable () -> Unit
    ) {
        standaloneTab = FloatingTabBarTab(
            key = key,
            title = title,
            icon = icon,
            onClick = onClick,
            indication = indication
        )
    }
}

private data class FloatingTabBarTab(
    val key: Any,
    val title: @Composable () -> Unit,
    val icon: @Composable () -> Unit,
    val onClick: () -> Unit,
    val indication: (@Composable () -> Indication)?
)

/**
 * Represents the colors used in [FloatingTabBar].
 */
@Immutable
data class FloatingTabBarColors(
    val backgroundColor: Color,
    val accessoryBackgroundColor: Color,
)

/**
 * Represents the shapes used in [FloatingTabBar].
 */
@Immutable
data class FloatingTabBarShapes(
    val tabBarShape: Shape,
    val tabShape: Shape,
    val standaloneTabShape: Shape,
    val accessoryShape: Shape,
)

/**
 * Represents the elevations used in [FloatingTabBar].
 */
@Immutable
data class FloatingTabBarElevations(
    val inlineElevation: Dp,
    val expandedElevation: Dp,
)

/**
 * Represents the sizes and spacing used in [FloatingTabBar].
 */
@Immutable
data class FloatingTabBarSizes(
    val tabBarContentPadding: PaddingValues,
    val tabInlineContentPadding: PaddingValues,
    val tabExpandedContentPadding: PaddingValues,
    val componentSpacing: Dp,
    val tabSpacing: Dp,
    // Vendored addition: fixed width of one expanded tab slot. The drag-puck
    // indicator needs a known per-tab width to map drag offset to tab index,
    // so expanded tabs are equal-width instead of sized to their content.
    val tabWidth: Dp = FloatingTabBarDefaults.TabWidth,
)

/**
 * Contains the default values used by [FloatingTabBar].
 */
object FloatingTabBarDefaults {
    /**
     * Width of one expanded tab slot at full size. Callers that have to fit the
     * pill plus the standalone tab into a narrow screen shrink below this — see
     * [com.convx.music.ui.component.AppFloatingNavBar] — so it is the ceiling,
     * not a guarantee.
     */
    val TabWidth: Dp = 88.dp

    /**
     * Creates a [FloatingTabBarColors] that represents the default colors used in a [FloatingTabBar].
     *
     * @param backgroundColor the color used for the tab bar background
     * @param accessoryBackgroundColor the color used for the accessory background
     */
    @Composable
    fun colors(
        backgroundColor: Color = AppTheme.colorScheme.surfaceContainerHigh,
        accessoryBackgroundColor: Color = AppTheme.colorScheme.surfaceContainerHigh,
    ): FloatingTabBarColors = FloatingTabBarColors(
        backgroundColor = backgroundColor,
        accessoryBackgroundColor = accessoryBackgroundColor,
    )

    /**
     * Creates a [FloatingTabBarShapes] that represents the default shapes used in a [FloatingTabBar].
     *
     * @param tabBarShape the shape used to clip the tab bar
     * @param tabShape the shape used to clip individual tabs. Can be useful for example to control the click ripple effect shape
     * @param standaloneTabShape the shape used to clip the standalone tab
     * @param accessoryShape the shape used to clip the accessory container
     */
    @Composable
    fun shapes(
        // ContinuousRoundedRectangle(percent = 50) instead of RoundedCornerShape/
        // CircleShape: same capsule/circle silhouette, but Kyant0/Capsule's
        // continuous (superellipse) corners instead of Android's circular-arc
        // ones — smoother, and lerp-able for the puck's drag-to-search morph.
        tabBarShape: Shape = ContinuousRoundedRectangle(percent = 50),
        tabShape: Shape = ContinuousRoundedRectangle(percent = 50),
        standaloneTabShape: Shape = ContinuousRoundedRectangle(percent = 50),
        accessoryShape: Shape = ContinuousRoundedRectangle(percent = 50),
    ): FloatingTabBarShapes = FloatingTabBarShapes(
        tabBarShape = tabBarShape,
        tabShape = tabShape,
        standaloneTabShape = standaloneTabShape,
        accessoryShape = accessoryShape,
    )

    /**
     * Creates a [FloatingTabBarSizes] that represents the default sizes used in a [FloatingTabBar].
     *
     * @param tabBarContentPadding the padding applied to the tab bar content. This also applies to the standalone tab content.
     * @param tabInlineContentPadding the padding applied to tabs in inline state
     * @param tabExpandedContentPadding the padding applied to tabs in expanded state
     * @param componentSpacing the spacing between components
     * @param tabSpacing the spacing between tabs in expanded state
     */
    @Composable
    fun sizes(
        tabBarContentPadding: PaddingValues = PaddingValues(vertical = 4.dp, horizontal = 4.dp),
        tabInlineContentPadding: PaddingValues = PaddingValues(10.dp),
        // horizontal was 20dp (the source lib's default for content-hugging
        // tabs); with fixed-width tabWidth cells that ate too much of the
        // budget and clipped labels like "Settings" — tightened to fit text.
        tabExpandedContentPadding: PaddingValues = PaddingValues(vertical = 6.dp, horizontal = 6.dp),
        componentSpacing: Dp = 8.dp,
        tabSpacing: Dp = 0.dp,
        tabWidth: Dp = TabWidth,
    ): FloatingTabBarSizes = FloatingTabBarSizes(
        tabBarContentPadding = tabBarContentPadding,
        tabInlineContentPadding = tabInlineContentPadding,
        tabExpandedContentPadding = tabExpandedContentPadding,
        componentSpacing = componentSpacing,
        tabSpacing = tabSpacing,
        tabWidth = tabWidth,
    )

    /**
     * Creates a [FloatingTabBarElevations] that represents the default elevations used in a [FloatingTabBar].
     *
     * @param inlineElevation the elevation used for tabs in inline state
     * @param expandedElevation the elevation used for tabs in expanded state
     */
    @Composable
    fun elevations(
        inlineElevation: Dp = 6.dp,
        expandedElevation: Dp = 12.dp,
    ): FloatingTabBarElevations = FloatingTabBarElevations(
        inlineElevation = inlineElevation,
        expandedElevation = expandedElevation,
    )
}

/**
 * Inline/expand choreography, reverted to the pre-yesterday behaviour: one
 * fixed-duration tween drives the outer SizeTransform, the fades AND every
 * sharedElement bounds, zero delay, both directions. Tweens share one end
 * frame, so the overlay hands back to real layout on the same frame -- no
 * spring settle-threshold drift between the container and the overlay copy,
 * which is what read as the icon pop. Stagger/bounce sacrificed on purpose:
 * each was its own clock on the same pixels.
 */
internal class BarBoundsTransforms(val bar: BoundsTransform, val accessory: BoundsTransform) {
    companion object {
        private fun <T> morphTween() =
            tween<T>(GooeyDurationMs, easing = FastOutSlowInEasing)

        val Collapse = BarBoundsTransforms(
            bar = BoundsTransform { _, _ -> morphTween() },
            accessory = BoundsTransform { _, _ -> morphTween() },
        )
        val Expand = BarBoundsTransforms(
            bar = BoundsTransform { _, _ -> morphTween() },
            accessory = BoundsTransform { _, _ -> morphTween() },
        )
    }
}

internal val LocalBarBoundsTransforms = staticCompositionLocalOf { BarBoundsTransforms.Collapse }
