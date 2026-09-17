/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import com.convx.music.ui.ios.compat.Icon
import com.convx.music.ui.ios.compat.IconButton
import com.convx.music.ui.ios.compat.AppTheme
import com.convx.music.ui.ios.compat.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.height
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.convx.music.LocalAmbientBackgroundColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.painterResource
import com.composables.sfsymbols.SfSymbols
import com.composables.sfsymbols.monochrome.SFBackwardFill
import com.composables.sfsymbols.monochrome.SFForwardFill
import com.composables.sfsymbols.monochrome.SFListBullet
import com.composables.sfsymbols.monochrome.SFQuoteBubble
import com.composables.sfsymbols.monochrome.SFRepeat
import com.composables.sfsymbols.monochrome.SFRepeat1
import com.composables.sfsymbols.monochrome.SFShuffle
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Size as CoilSize
import androidx.compose.ui.platform.LocalContext
import com.convx.music.ui.component.rememberNoArtworkPainter
import com.convx.music.LocalListenTogetherManager
import com.convx.music.LocalPlayerConnection
import com.convx.music.R
import com.convx.music.ui.player.customize.PlayerGlyph
import com.convx.music.ui.player.customize.PlayerIconSlot
import com.convx.music.constants.MiniBarTabStyleKey
import com.convx.music.constants.MiniPlayerWaveformKey
import com.convx.music.constants.SwipeSensitivityKey
import com.convx.music.constants.SwipeThumbnailKey
import androidx.media3.common.Player
import com.convx.music.extensions.togglePlayPause
import com.convx.music.extensions.toggleRepeatMode
import com.convx.music.ui.component.AnimatedPlayPauseIcon
import com.convx.music.ui.component.ScrollingWaveformSeekBar
import com.convx.music.ui.component.floatingtabbar.SearchBarRowHeight
import com.convx.music.ui.component.rememberPlaybackFraction
import androidx.compose.ui.graphics.isSpecified
import com.convx.music.LocalAmbientBackgroundColor
import com.convx.music.ui.component.asSoftIconTint
import com.convx.music.ui.component.elasticStretchLayerBlock
import com.convx.music.ui.component.backdrop.catalog.utils.InteractiveHighlight
import com.convx.music.ui.utils.additiveChrome
import com.convx.music.ui.utils.resize
import com.convx.music.utils.rememberPreference
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Optical size correction for the lyrics glyph beside the queue one.
 *
 * `lyrics` is drawn in a 28dp viewport with real padding around the artwork;
 * `queue_music` is drawn in a 24dp viewport with its strokes running nearly edge
 * to edge. Rendered at the same box size the lyrics mark therefore reads visibly
 * smaller, so it gets a slightly larger box to match by eye rather than by number.
 */
private const val LyricsIconScale = 1.18f

/**
 * Compact now playing controls docked inside the floating tab bar, mirroring the
 * iOS 26 Apple Music accessory: a wide pill above the tabs when the bar is
 * expanded, and a slim strip between the tab pill and the search tab when inline.
 *
 * Swiping the artwork/title area horizontally changes the song, honoring the same
 * swipe preferences and thresholds as [MiniPlayer]. Tapping the row invokes
 * [onClick] (which opens the full player).
 */
@Composable
fun FloatingMiniPlayer(
    isInline: Boolean,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    // Gates additiveChrome on every icon here the same way the nav bar's own tab
    // icons are gated -- additive only reads right over glass, not a flat surface.
    useGlass: Boolean = true,
    onLyricsClick: (() -> Unit)? = null,
    onQueueClick: (() -> Unit)? = null,
    // Fires on release after a real drag (never a tap) so the host bar can
    // answer the snap with its own motion.
    onSnapRelease: () -> Unit = {},
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val ambientColor = LocalAmbientBackgroundColor.current.value
    val isDark = AppTheme.colorScheme.surface.luminance() <= 0.5f
    val playTint = com.convx.music.ui.theme.GlassTintTokens.glassContentColor(
        isDark = isDark,
        activeTint = ambientColor,
        isSelected = false,
    ).copy(alpha = 1.0f)
    val transportTint = com.convx.music.ui.theme.GlassTintTokens.glassContentColor(
        isDark = isDark,
        activeTint = ambientColor,
        isSelected = false,
    )
    val context = LocalContext.current
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val shuffleModeEnabled by playerConnection.shuffleModeEnabled.collectAsStateWithLifecycle()
    val repeatMode by playerConnection.repeatMode.collectAsStateWithLifecycle()
    val canSkipPrevious by playerConnection.canSkipPrevious.collectAsStateWithLifecycle()
    val canSkipNext by playerConnection.canSkipNext.collectAsStateWithLifecycle()

    val swipeSensitivity by rememberPreference(SwipeSensitivityKey, 0.73f)
    val swipeThumbnailPref by rememberPreference(SwipeThumbnailKey, true)
    val (tabStyle) = rememberPreference(MiniBarTabStyleKey, defaultValue = false)
    val miniPlayerWaveform by rememberPreference(MiniPlayerWaveformKey, defaultValue = true)
    val listenTogetherManager = LocalListenTogetherManager.current
    val isListenTogetherGuest = listenTogetherManager?.let { it.isInRoom && !it.isHost } ?: false
    val swipeEnabled = swipeThumbnailPref && !isListenTogetherGuest

    val layoutDirection = LocalLayoutDirection.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    // Swipe thresholds below were tuned as raw pixel counts, which is only
    // ~14dp on a dense (~3.5x) screen — well within a tap's incidental finger
    // drift, so plain taps were misread as deliberate swipes and skipped the
    // track. Scaling by density.density makes them density-independent.
    val densityScale = density.density

    val offsetXAnimatable = remember { Animatable(0f) }
    // Stretch visual comes from the shared search-pill elastic formula
    // (elasticStretchLayerBlock + glow); skip slide stays on offsetX below.
    // Center drag stretches only, edge flick skips -- see pointerInput.
    val glowScope = rememberCoroutineScope()
    val glow = remember(glowScope) { InteractiveHighlight(animationScope = glowScope) }
    var dragStartTime by remember { mutableLongStateOf(0L) }
    var totalDragDistance by remember { mutableFloatStateOf(0f) }
    val viewConfig = LocalViewConfiguration.current
    // Natural iOS rubber-band return: settles with subtle micro-bounce past center.
    val animationSpec = remember {
        spring<Float>(dampingRatio = 0.73f, stiffness = 620f)
    }
    // Commit distance for the swipe. The logistic curve this replaces produced
    // ~400dp — about 1200px at 3x, on a screen 1080px wide — so releasing the pill
    // never changed track no matter how far it was dragged. Expressed as a fraction
    // of screen width instead: sensitivity 0 needs 45%, sensitivity 1 needs 12%.
    val configuration = LocalConfiguration.current
    val autoSwipeThreshold = remember(swipeSensitivity, configuration.screenWidthDp, densityScale) {
        val screenWidthPx = configuration.screenWidthDp * densityScale
        (screenWidthPx * (0.45f - 0.33f * swipeSensitivity.coerceIn(0f, 1f))).roundToInt()
    }

    // Same structure as MiniPlayer: the drag detector sits on the outermost
    // container so the whole accessory is swipeable, and the entire content row
    // slides with the drag. A plain Box (not BoxWithConstraints) — this sits
    // inside FloatingTabBar's Row, which sizes itself via
    // .height(IntrinsicSize.Max); BoxWithConstraints is a SubcomposeLayout,
    // and SubcomposeLayout can't answer intrinsic-measurement queries, which
    // crashes the whole bar. onSizeChanged gets the same measured height for
    // the !isInline tablet pill's icon/thumbnail scaling without that problem.
    //
    // Seeded at the pill's real resting height (64dp, matching
    // FloatingSideBar's FloatingMiniPlayerMinHeight/MaxHeight) rather than 0:
    // every scroll-triggered mount of this branch used to render its very
    // first frame with measuredHeightPx still at 0, which the size formulas
    // below floor-clamp to 36dp regardless of the pill's real height — then
    // the true measurement lands a frame later and every icon visibly pops
    // to its real size. Starting from the known resting height means the
    // first frame is already correct (or within a pixel of it) instead of
    // popping from the clamp floor on every single expand.
    var measuredHeightPx by remember { mutableIntStateOf(with(density) { 64.dp.roundToPx() }) }
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier
            // Same tanh-damped stretch as the search pill, draw-only.
            // Wide pills (expanded accessory, tab-view pill) run 2x so the
            // travel reads at pill size -- same as bar pills elsewhere.
            .graphicsLayer(elasticStretchLayerBlock(glow, magnitude = if (isInline) 1f else 2f))
            .then(modifier)
            // Source end of the shared container morph: measures the pill as the
            // nav bar places it for the live card to grow out of. Live tree --
            // the pill draws in place; the travelling card covers it.
            .registerMiniContainerRect()
            .onSizeChanged { measuredHeightPx = it.height }
            // Finger-following glow, same as the search pill.
            .then(glow.gestureModifier)
            .then(glow.modifier)
            // Edge-flick skip only: center drag stretches (via glow above)
            // but never slides or commits. Snaps hop to the card scope.
            .pointerInput(swipeEnabled) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val pid = down.id
                    val start = down.position
                    val edgePx = with(density) { 64.dp.toPx() }
                    val verticalCapPx = with(density) { 36.dp.toPx() }
                    val isEdgeZone = start.x < edgePx || start.x > size.width - edgePx
                    val allowSwipe = swipeEnabled && isEdgeZone
                    dragStartTime = System.currentTimeMillis()
                    totalDragDistance = 0f
                    var lastAdjustedX = 0f
                    var maxAbsY = 0f
                    // Local slide accumulator: the async snap below can't be
                    // read back for the commit gate without racing it.
                    var slideX = offsetXAnimatable.value
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == pid }
                        if (change == null || !change.pressed) break
                        val raw = change.position - start
                        maxAbsY = maxOf(maxAbsY, abs(raw.y))
                        val adjustedX =
                            if (layoutDirection == LayoutDirection.Rtl) -raw.x else raw.x
                        val delta = adjustedX - lastAdjustedX
                        lastAdjustedX = adjustedX
                        totalDragDistance += abs(delta)
                        if (allowSwipe) {
                            val canSkipPrevious =
                                playerConnection.player.previousMediaItemIndex != -1
                            val canSkipNext =
                                playerConnection.player.nextMediaItemIndex != -1
                            val allowLeft = delta < 0 && canSkipNext
                            val allowRight = delta > 0 && canSkipPrevious
                            val canReturnToCenter =
                                (delta > 0 && !canSkipPrevious && slideX < 0) ||
                                    (delta < 0 && !canSkipNext && slideX > 0)
                            if (allowLeft || allowRight || canReturnToCenter) {
                                if (abs(slideX) > viewConfig.touchSlop) {
                                    change.consume()
                                }
                                slideX += delta
                                coroutineScope.launch { offsetXAnimatable.snapTo(slideX) }
                            }
                        } else if (totalDragDistance > viewConfig.touchSlop) {
                            // Center drag (stretch only): swallow the move so the
                            // tap-to-open below can't fire on release after a drag.
                            change.consume()
                        }
                    }
                    val duration = System.currentTimeMillis() - dragStartTime
                    // Mean velocity over the gesture, px/ms.
                    val velocity = if (duration > 0) totalDragDistance / duration else 0f
                    val currentOffset = slideX
                    val dragged = abs(currentOffset)
                    val canSkipPrevious =
                        playerConnection.player.previousMediaItemIndex != -1
                    val canSkipNext =
                        playerConnection.player.nextMediaItemIndex != -1
                    // Quick edge flick or dragged past threshold
                    val shouldChangeSong = allowSwipe &&
                        maxAbsY < verticalCapPx &&
                        (dragged > autoSwipeThreshold ||
                            (velocity > 0.55f && dragged > autoSwipeThreshold * 0.25f))
                    if (shouldChangeSong) {
                        // Through the connection, not the raw player: the wrapper
                        // routes to Cast while casting, re-prepares an ENDED
                        // player, and fires the skip callbacks.
                        if (currentOffset > 0 && canSkipPrevious) {
                            playerConnection.seekToPrevious()
                        } else if (currentOffset < 0 && canSkipNext) {
                            playerConnection.seekToNext()
                        }
                    }
                    if (totalDragDistance > viewConfig.touchSlop) onSnapRelease()
                    coroutineScope.launch {
                        offsetXAnimatable.animateTo(0f, animationSpec)
                    }
                }
            }
            // Single tap anywhere on the pill (except the transport controls and
            // the seek waveform, which consume the tap themselves) opens the
            // full player. Sits after pointerInput so a drag that consumes the
            // move above cancels this tap instead of opening on release.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
    ) {
        // Fixed sizes for both phone accessory strips (isInline and the default
        // !tabStyle wave bar) — neither's resting height ever changes, so there is
        // nothing to scale against. Only the tablet pill's height actually varies
        // (FloatingMiniPlayerMinHeight..MaxHeight in FloatingSideBar), so only it
        // scales its icons/thumbnail off the measured height.
        val animatedHeightPx by animateIntAsState(
            targetValue = if (tabStyle) measuredHeightPx else with(density) { 64.dp.roundToPx() },
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            label = "miniPlayerMeasuredHeight",
        )
        val measuredHeight = with(density) { animatedHeightPx.toDp() }
        val artSize = if (tabStyle) (measuredHeight * 0.6f).coerceIn(36.dp, 60.dp) else if (isInline) 36.dp else 38.dp
        val artCornerRadius = if (isInline) 9.dp else artSize * 0.22f
        val controlSize = if (tabStyle) (measuredHeight * 0.55f).coerceIn(36.dp, 52.dp) else 36.dp

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                // Shrunk height pinned to the search pill's row height, so the
                // mini and search pills always sit at the same height.
                .then(if (isInline) Modifier.height(SearchBarRowHeight) else Modifier)
                .offset { IntOffset(offsetXAnimatable.value.roundToInt(), 0) }
                .padding(
                    horizontal = if (isInline) 10.dp else 12.dp,
                    vertical = if (isInline) 6.dp else 8.dp,
                ),
        ) {
            if (isInline) {
                // Left-side tap consumer: tapping thumbnail or song info exposes player
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onClick,
                        ),
                ) {
                    AsyncImage(
                        // Same URL AND decode size as PlayerArtworkMorphOverlay and the
                        // full player's own artwork -- one shared Coil cache entry for
                        // all three, so the pill, the flying cover, and the handoff
                        // target are pixel-identical (no re-decode pop between them).
                        model = ImageRequest.Builder(context)
                            .data(mediaMetadata?.thumbnailUrl?.resize(1200, 1200))
                            .size(CoilSize.ORIGINAL)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        placeholder = rememberNoArtworkPainter(),
                        error = rememberNoArtworkPainter(),
                        modifier = Modifier
                            .size(artSize)
                            .registerMiniArtworkRect(with(density) { artCornerRadius.toPx() })
                            .hideWhileMorphing()
                            .clip(RoundedCornerShape(artCornerRadius)),
                    )

                    Spacer(Modifier.width(8.dp))

                    Column(
                        // One Plus layer for both lines rather than one each: the glyphs never
                        // overlap, so adding them together is the same pixels at half the layers.
                        modifier = Modifier.weight(1f).additiveChrome(useGlass && isDark),
                    ) {
                        Text(
                            text = mediaMetadata?.title.orEmpty(),
                            style = AppTheme.typography.bodySmall,
                            color = contentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = mediaMetadata?.artists?.joinToString { it.name }.orEmpty(),
                            style = AppTheme.typography.bodySmall.copy(fontSize = 10.sp),
                            color = contentColor.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                IconButton(
                    onClick = { playerConnection.player.togglePlayPause() },
                    modifier = Modifier.size(controlSize),
                ) {
                    // No additiveChrome here: its saveLayer clips to the Box's measured
                    // size, and this content cross-fades between two different sizes
                    // (SizeTransform(clip = false) -- play/pause deliberately overlap
                    // uncropped during the swap), so the layer bounds cut the icon off
                    // mid-transition.
                    AnimatedPlayPauseIcon(
                        isPlaying = isPlaying,
                        tint = playTint,
                        size = 20.dp,
                    )
                }
            } else if (!tabStyle) {
                // iOS-style mini bar (default): thumbnail, title/artist, a wave
                // seek bar, play/pause, and forward (skip next).
                val iconSize = controlSize * 0.5f
                val playIconSize = controlSize * 0.6f
                // Keep the State, don't unwrap it: rememberPlaybackFraction samples
                // withFrameMillis, so a `by` delegate read here recomposes the whole
                // mini player every frame while playing. Only the seek bar's progress
                // lambda below needs the value, and that reads it in the draw phase.
                val playbackFraction = rememberPlaybackFraction(playerConnection.player, isPlaying)
                val waveformSeed = remember(mediaMetadata?.id) { mediaMetadata?.id?.hashCode() ?: 0 }

                // Left-side tap consumer: tapping thumbnail or song info exposes player
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onClick,
                        ),
                ) {
                    AsyncImage(
                        // Same URL AND decode size as PlayerArtworkMorphOverlay and the
                        // full player's own artwork -- one shared Coil cache entry for
                        // all three, so the pill, the flying cover, and the handoff
                        // target are pixel-identical (no re-decode pop between them).
                        model = ImageRequest.Builder(context)
                            .data(mediaMetadata?.thumbnailUrl?.resize(1200, 1200))
                            .size(CoilSize.ORIGINAL)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        placeholder = rememberNoArtworkPainter(),
                        error = rememberNoArtworkPainter(),
                        modifier = Modifier
                            .size(artSize)
                            .registerMiniArtworkRect(with(density) { artCornerRadius.toPx() })
                            .hideWhileMorphing()
                            .clip(RoundedCornerShape(artCornerRadius)),
                    )

                    Spacer(Modifier.width(10.dp))

                    Column(
                        // One Plus layer for both lines rather than one each: the glyphs never
                        // overlap, so adding them together is the same pixels at half the layers.
                        modifier = Modifier.weight(1f).additiveChrome(useGlass && isDark),
                    ) {
                        Text(
                            text = mediaMetadata?.title.orEmpty(),
                            style = AppTheme.typography.bodyMedium,
                            color = contentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = mediaMetadata?.artists?.joinToString { it.name }.orEmpty(),
                            style = AppTheme.typography.bodySmall,
                            color = contentColor.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                if (miniPlayerWaveform) {
                    Spacer(Modifier.width(8.dp))
                    ScrollingWaveformSeekBar(
                        progress = { playbackFraction.value },
                        onSeek = { frac ->
                            val duration = playerConnection.player.duration
                            if (duration > 0) {
                                playerConnection.player.seekTo((frac * duration).toLong())
                            }
                        },
                        playedColor = contentColor,
                        trackColor = contentColor.copy(alpha = 0.3f),
                        seed = waveformSeed,
                        visibleBars = 14,
                        modifier = Modifier
                            .width(64.dp)
                            .height(22.dp)
                            .additiveChrome(useGlass && isDark),
                    )
                }

                Spacer(Modifier.width(10.dp))

                IconButton(
                    onClick = { playerConnection.player.togglePlayPause() },
                    modifier = Modifier.size(controlSize),
                ) {
                    AnimatedPlayPauseIcon(
                        isPlaying = isPlaying,
                        tint = playTint,
                        size = playIconSize,
                    )
                }
                IconButton(
                    onClick = { playerConnection.seekToNext() },
                    enabled = canSkipNext,
                    modifier = Modifier.size(controlSize),
                ) {
                    PlayerGlyph(
                        slot = PlayerIconSlot.NEXT,
                        fallbackVector = SfSymbols.Monochrome.SFForwardFill,
                        tint = if (canSkipNext) transportTint else transportTint.copy(alpha = 0.4f),
                        modifier = Modifier.size(playIconSize).additiveChrome(useGlass && isDark),
                    )
                }
            } else {
                // Tablet floating pill: matches the Apple Music iPad now-playing
                // bar exactly — shuffle, prev, play/pause, next, repeat, then
                // artwork+title/artist, then lyrics and queue on the far end.
                // Icons scale with controlSize — IconButton's own size modifier
                // only resizes the tap target, the Icon inside stays at a fixed
                // default unless given an explicit size too.
                val iconSize = controlSize * 0.5f
                val playIconSize = controlSize * 0.6f
                IconButton(
                    onClick = { playerConnection.player.shuffleModeEnabled = !shuffleModeEnabled },
                    modifier = Modifier.size(controlSize),
                ) {
                    Icon(
                        imageVector = SfSymbols.Monochrome.SFShuffle,
                        contentDescription = stringResource(R.string.shuffle_label),
                        tint = transportTint,
                        modifier = Modifier.size(iconSize).additiveChrome(useGlass && isDark),
                    )
                }
                IconButton(
                    onClick = { playerConnection.seekToPrevious() },
                    enabled = canSkipPrevious,
                    modifier = Modifier.size(controlSize),
                ) {
                    PlayerGlyph(
                        slot = PlayerIconSlot.PREVIOUS,
                        fallbackVector = SfSymbols.Monochrome.SFBackwardFill,
                        tint = if (canSkipPrevious) transportTint else transportTint.copy(alpha = 0.4f),
                        modifier = Modifier.size(iconSize).additiveChrome(useGlass && isDark),
                    )
                }
                IconButton(
                    onClick = { playerConnection.player.togglePlayPause() },
                    modifier = Modifier.size(controlSize),
                ) {
                    AnimatedPlayPauseIcon(
                        isPlaying = isPlaying,
                        tint = playTint,
                        size = playIconSize,
                    )
                }
                IconButton(
                    onClick = { playerConnection.seekToNext() },
                    enabled = canSkipNext,
                    modifier = Modifier.size(controlSize),
                ) {
                    PlayerGlyph(
                        slot = PlayerIconSlot.NEXT,
                        fallbackVector = SfSymbols.Monochrome.SFForwardFill,
                        tint = if (canSkipNext) transportTint else transportTint.copy(alpha = 0.4f),
                        modifier = Modifier.size(playIconSize).additiveChrome(useGlass && isDark),
                    )
                }
                IconButton(
                    onClick = { playerConnection.player.toggleRepeatMode() },
                    modifier = Modifier.size(controlSize),
                ) {
                    Icon(
                        imageVector = if (repeatMode == Player.REPEAT_MODE_ONE) SfSymbols.Monochrome.SFRepeat1 else SfSymbols.Monochrome.SFRepeat,
                        contentDescription = stringResource(R.string.repeat_mode_all),
                        tint = if (repeatMode != Player.REPEAT_MODE_OFF) transportTint else transportTint.copy(alpha = 0.4f),
                        modifier = Modifier.size(iconSize).additiveChrome(useGlass && isDark),
                    )
                }

                Spacer(Modifier.width(8.dp))

                // Tap consumer for thumbnail + song info
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onClick,
                        ),
                ) {
                    AsyncImage(
                        // Same URL AND decode size as PlayerArtworkMorphOverlay and the
                        // full player's own artwork -- one shared Coil cache entry for
                        // all three, so the pill, the flying cover, and the handoff
                        // target are pixel-identical (no re-decode pop between them).
                        model = ImageRequest.Builder(context)
                            .data(mediaMetadata?.thumbnailUrl?.resize(1200, 1200))
                            .size(CoilSize.ORIGINAL)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        placeholder = rememberNoArtworkPainter(),
                        error = rememberNoArtworkPainter(),
                        modifier = Modifier
                            .size(artSize)
                            .registerMiniArtworkRect(with(density) { artCornerRadius.toPx() })
                            .hideWhileMorphing()
                            .clip(RoundedCornerShape(artCornerRadius)),
                    )

                    Spacer(Modifier.width(12.dp))

                    Column(
                        // One Plus layer for both lines rather than one each: the glyphs never
                        // overlap, so adding them together is the same pixels at half the layers.
                        modifier = Modifier.weight(1f).additiveChrome(useGlass && isDark),
                    ) {
                        Text(
                            text = mediaMetadata?.title.orEmpty(),
                            style = AppTheme.typography.bodyMedium,
                            color = contentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = mediaMetadata?.artists?.joinToString { it.name }.orEmpty(),
                            style = AppTheme.typography.bodySmall,
                            color = contentColor.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                IconButton(
                    onClick = onLyricsClick ?: onClick,
                    modifier = Modifier.size(controlSize),
                ) {
                    Icon(
                        imageVector = SfSymbols.Monochrome.SFQuoteBubble,
                        contentDescription = stringResource(R.string.lyrics),
                        tint = transportTint,
                        modifier = Modifier.size(iconSize * LyricsIconScale).additiveChrome(useGlass && isDark),
                    )
                }
                IconButton(
                    onClick = onQueueClick ?: onClick,
                    modifier = Modifier.size(controlSize),
                ) {
                    Icon(
                        imageVector = SfSymbols.Monochrome.SFListBullet,
                        contentDescription = stringResource(R.string.queue),
                        tint = transportTint,
                        modifier = Modifier.size(iconSize).additiveChrome(useGlass && isDark),
                    )
                }
            }
        }
    }
}
