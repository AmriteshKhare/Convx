/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */
// Apple Music player UI ported from vivizzz007/vivi-music (https://github.com/vivizzz007/vivi-music), GPL-3.0.

package com.convx.music.ui.player

import com.convx.music.ui.ios.compat.AppTheme

import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import androidx.media3.common.C
import com.convx.music.LocalPlayerConnection
import com.convx.music.playback.PlayerConnection
import com.convx.music.constants.PlayerBackgroundStyle
import com.convx.music.constants.PlayerBackgroundStyleKey
import com.convx.music.constants.PlayerLook
import com.convx.music.constants.PlayerLookKey
import com.convx.music.constants.PlayerStaticColorKey
import com.convx.music.constants.PlayerGradientStopsKey
import com.convx.music.ui.theme.decodeGradientStops
import androidx.compose.ui.graphics.luminance
import com.convx.music.constants.ShowPlayerThumbnailShadowKey
import com.convx.music.constants.PlayerThumbnailShadowElevationKey
import com.convx.music.constants.EnableGoogleCastKey
import com.convx.music.models.MediaMetadata
import com.convx.music.ui.component.BottomSheetState
import com.convx.music.ui.component.DjReadout
import com.convx.music.ui.component.BakedFootBlur
import com.convx.music.ui.component.ElasticVolumeSlider
import com.convx.music.ui.component.PlayerSliderTrack
import com.convx.music.ui.theme.PlayerSliderColors
import com.convx.music.utils.makeTimeString
import com.convx.music.utils.rememberEnumPreference
import com.convx.music.extensions.togglePlayPause
import com.convx.music.extensions.metadata
import com.convx.music.ui.component.Lyrics
import com.convx.music.ui.component.rememberBottomSheetState
import com.convx.music.ui.component.expandedAnchor
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import com.convx.music.constants.SwipeSensitivityKey
import com.convx.music.constants.SwipeThumbnailKey
import kotlin.math.roundToInt
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import com.convx.music.ui.component.LocalBottomSheetPageState
import com.convx.music.ui.component.LocalMenuState
import com.convx.music.ui.menu.LyricsMenu
import com.convx.music.ui.menu.PlayerMenu
import com.convx.music.ui.utils.ShowMediaInfo
import com.convx.music.ui.utils.ShowOffsetDialog
import com.convx.music.ui.utils.resize
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Size as CoilSize
import com.convx.music.vivimusic.AudioDeviceBottomSheet
import com.convx.music.vivimusic.getConnectedBluetoothDeviceName
import com.convx.music.ui.component.CastButton
import com.convx.music.BuildConfig
import com.convx.music.R
import com.convx.music.utils.rememberPreference
import com.convx.music.constants.CompactPlayerInTabViewKey
import com.convx.music.LocalTabView
import com.convx.music.constants.ShowUpNextKey
import com.convx.music.constants.HideVolumeBarKey
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.convx.music.ui.player.customize.AbsoluteFrameDiyStickers
import com.convx.music.ui.player.customize.PlayerGlyph
import androidx.compose.ui.graphics.graphicsLayer
import com.convx.music.ui.utils.additiveChrome
import com.convx.music.ui.player.customize.AnimatedPlayerGlyph
import com.convx.music.ui.player.customize.PlayerIconSlot
import com.convx.music.ui.player.customize.rememberDiyLayout
import com.convx.music.ui.player.customize.DiyOrientation
import com.convx.music.LocalPlayerConnection
import kotlinx.coroutines.launch
import com.convx.music.ui.utils.Motion

enum class PlayerInternalState { COVER, LYRICS, QUEUE }

/** Mirrors Player.kt's PARENT_SHEET_SETTLE_TIMEOUT_MS (private to that file). */
private const val V2_PARENT_SHEET_SETTLE_TIMEOUT_MS = 1_000L

private fun Modifier.customSoftShadow(elevation: androidx.compose.ui.unit.Dp, cornerRadius: androidx.compose.ui.unit.Dp, enabled: Boolean): Modifier =
    if (enabled) shadow(elevation, RoundedCornerShape(cornerRadius)) else this

@OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
@Composable
fun PlayerV2(
    state: BottomSheetState,
    navController: NavController,
    modifier: Modifier = Modifier,
    showDiyStickers: Boolean = true,
    forcedOrientation: DiyOrientation? = null,
    isPreviewOnly: Boolean = false,
    /** See AbsoluteFrameDiyStickers' doc -- shared with classic Player.kt's own z < 0
     *  sticker band so both skins resolve stickers against the same frame. */
    diyRootCoordinates: androidx.compose.ui.layout.LayoutCoordinates? = null,
    /** Reports whether [playerState] is COVER, so Player.kt's z >= 0 sticker band
     *  (rendered from its own overlayContent, outside this composable entirely) knows
     *  to hide while V17 is showing Lyrics or Queue -- it used to get that for free by
     *  living inside the same AnimatedContent branch as the cover. */
    onCoverShowingChange: ((Boolean) -> Unit)? = null,
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    val playerConnection = LocalPlayerConnection.current ?: return
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    // For the hero artwork's live swipe peek below: the neighbour thumbnail slides in from
    // whichever side the finger is dragging toward, tracking the drag itself rather than
    // only appearing once the gesture commits.
    val queueWindows by playerConnection.queueWindows.collectAsState()
    val currentQueueIndex by playerConnection.currentMediaItemIndex.collectAsState()
    val nextMediaMetadata = queueWindows.getOrNull(currentQueueIndex + 1)?.mediaItem?.metadata
    val previousMediaMetadata = queueWindows.getOrNull(currentQueueIndex - 1)?.mediaItem?.metadata
    val currentSong by playerConnection.currentSong.collectAsState(initial = null)
    val currentLyrics by playerConnection.currentLyrics.collectAsState(initial = null)
    val menuState = LocalMenuState.current
    val bottomSheetPageState = LocalBottomSheetPageState.current
    var playerState by remember { mutableStateOf(PlayerInternalState.COVER) }

    LaunchedEffect(playerState) {
        onCoverShowingChange?.invoke(playerState == PlayerInternalState.COVER)
    }

    // Hoisted out of rememberSwipeCardModifier so the live value is readable here too, for
    // the neighbour-thumbnail peek.
    val heroDragOffset = remember { Animatable(0f) }

    // The floating mini bar's lyrics/queue icons live outside this composable
    // (FloatingSideBar/FloatingNavBar), so they signal here through
    // PlayerConnection instead of calling local state directly -- same
    // requestShowLyrics/requestShowQueue contract the classic player observes.
    // V17 never collected these at all, so tapping those icons while V17 was
    // active expanded the player sheet but never switched panes -- a dead tap.
    LaunchedEffect(Unit) {
        playerConnection.requestShowLyrics.collect { requested ->
            if (requested) {
                if (!state.isExpanded) {
                    withTimeoutOrNull(V2_PARENT_SHEET_SETTLE_TIMEOUT_MS) {
                        snapshotFlow { state.isExpanded }.first { it }
                    }
                }
                playerState = PlayerInternalState.LYRICS
                playerConnection.requestShowLyrics.value = false
            }
        }
    }
    LaunchedEffect(Unit) {
        playerConnection.requestShowQueue.collect { requested ->
            if (requested) {
                if (!state.isExpanded) {
                    withTimeoutOrNull(V2_PARENT_SHEET_SETTLE_TIMEOUT_MS) {
                        snapshotFlow { state.isExpanded }.first { it }
                    }
                }
                playerState = PlayerInternalState.QUEUE
                playerConnection.requestShowQueue.value = false
            }
        }
    }

    val listenTogetherManager = com.convx.music.LocalListenTogetherManager.current
    val listenTogetherRoleState = listenTogetherManager?.role?.collectAsState(initial = com.convx.music.listentogether.RoomRole.NONE)
    val isListenTogetherGuest = listenTogetherRoleState?.value == com.convx.music.listentogether.RoomRole.GUEST
    val isMuted by playerConnection.isMuted.collectAsState()
    val canSkipPrevious by playerConnection.canSkipPrevious.collectAsState()
    val canSkipNext by playerConnection.canSkipNext.collectAsState()
    
    androidx.activity.compose.BackHandler(enabled = playerState != PlayerInternalState.COVER) {
        playerState = PlayerInternalState.COVER
    }
    
    var controlsVisible by remember { mutableStateOf(true) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(playerState, lastInteractionTime) {
        if (playerState == PlayerInternalState.LYRICS) {
            delay(3000)
            controlsVisible = false
        } else if (playerState != PlayerInternalState.QUEUE) {
            controlsVisible = true
        }
    }
    // Queue: the transport controls follow the list's scroll direction, like Apple Music
    // (60fps.design apple-music-player-controls-collapse) -- scrolling the queue up tucks
    // them away to give the list the room, scrolling back down brings them straight back.
    // A touch alone no longer re-shows them there, or every drag would flash them on.
    LaunchedEffect(playerState) {
        if (playerState == PlayerInternalState.QUEUE) controlsVisible = true
    }
    // Needs a real run of travel in one direction before it flips. Reacting to every
    // 1px delta flipped the controls back and forth on finger jitter, and each flip
    // restarted their enter/exit animation -- the reported jittery, too-fast hide.
    val queueControlsFlipPx = with(androidx.compose.ui.platform.LocalDensity.current) { 48.dp.toPx() }
    val queueControlsScroll = remember(queueControlsFlipPx) {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            var travel = 0f
            override fun onPostScroll(
                consumed: androidx.compose.ui.geometry.Offset,
                available: androidx.compose.ui.geometry.Offset,
                source: androidx.compose.ui.input.nestedscroll.NestedScrollSource,
            ): androidx.compose.ui.geometry.Offset {
                if (source == androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput) {
                    val dy = consumed.y + available.y
                    // Direction change restarts the run.
                    if (dy != 0f && (travel == 0f || (dy < 0f) == (travel < 0f))) travel += dy else travel = dy
                    if (travel <= -queueControlsFlipPx) {
                        if (controlsVisible) controlsVisible = false
                        travel = 0f
                    } else if (travel >= queueControlsFlipPx) {
                        if (!controlsVisible) controlsVisible = true
                        travel = 0f
                    }
                }
                return androidx.compose.ui.geometry.Offset.Zero
            }
        }
    }
    
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var sliderPosition by remember { mutableStateOf<Long?>(null) }

    val audioManager = remember { context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager }
    val maxSystemVolume = remember { audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC).toFloat() }
    
    // Custom volume state implementation since produceState awaitDispose can be tricky with imports
    var systemVolume by remember { mutableFloatStateOf(audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC).toFloat() / maxSystemVolume) }
    val animatedVolume by androidx.compose.animation.core.animateFloatAsState(
        targetValue = systemVolume,
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = 150,
            easing = androidx.compose.animation.core.FastOutLinearInEasing
        ),
        label = "volumeAnimation"
    )
    // Same staleness fix as Player.kt: VOLUME_CHANGED_ACTION is undocumented and is not
    // reliably delivered while backgrounded, so the slider could sit on a number the
    // system had long since moved off. Re-read on every resume as well.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(context, lifecycleOwner) {
        fun refresh() {
            systemVolume = audioManager.getStreamVolume(
                android.media.AudioManager.STREAM_MUSIC
            ).toFloat() / maxSystemVolume
        }
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: android.content.Context?, intent: android.content.Intent?) {
                if (intent?.action == "android.media.VOLUME_CHANGED_ACTION") refresh()
            }
        }
        androidx.core.content.ContextCompat.registerReceiver(
            context,
            receiver,
            android.content.IntentFilter("android.media.VOLUME_CHANGED_ACTION"),
            androidx.core.content.ContextCompat.RECEIVER_EXPORTED,
        )
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        refresh()
        onDispose {
            context.unregisterReceiver(receiver)
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val storedPlayerBackground by rememberEnumPreference(
        key = PlayerBackgroundStyleKey,
        defaultValue = PlayerBackgroundStyle.DEFAULT
    )
    val playerBackground = if (storedPlayerBackground == PlayerBackgroundStyle.APPLE_MUSIC) {
        PlayerBackgroundStyle.DEFAULT
    } else {
        storedPlayerBackground
    }
    
    val playerLook by rememberEnumPreference(key = PlayerLookKey, defaultValue = PlayerLook.SQUARED)
    val isHeroLook = playerLook == PlayerLook.HERO
    val showPlayerThumbnailShadow by rememberPreference(ShowPlayerThumbnailShadowKey, defaultValue = false)
    val playerThumbnailShadowElevation by rememberPreference(PlayerThumbnailShadowElevationKey, defaultValue = 8f)
    val enableGoogleCast by rememberPreference(EnableGoogleCastKey, defaultValue = true)
    val (showUpNext) = rememberPreference(ShowUpNextKey, defaultValue = false)
    val (hideVolumeBar) = rememberPreference(HideVolumeBarKey, defaultValue = false)
    val diyLayout = rememberDiyLayout()
    // Was hardcoded to PORTRAIT below, so rotating into landscape kept using the
    // portrait sticker placement -- same real-orientation read the classic player
    // uses for its own diyOrientation. forcedOrientation overrides it for the DIY
    // editor's preview mount, same as the classic player's isLandscapeLayout.
    val diyOrientation = forcedOrientation ?: if (
        LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    ) DiyOrientation.LANDSCAPE else DiyOrientation.PORTRAIT

    var showAudioDeviceBottomSheet by remember { mutableStateOf(false) }
    
    val bluetoothDeviceName by produceState<String?>(initialValue = getConnectedBluetoothDeviceName(context)) {
        val am = context.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager
        val scope = this
        val updateDeviceState: () -> Unit = {
            scope.value = getConnectedBluetoothDeviceName(context)
            scope.launch {
                kotlinx.coroutines.delay(500)
                scope.value = getConnectedBluetoothDeviceName(context)
            }
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: android.content.Context, intent: Intent) {
                updateDeviceState()
            }
        }
        val callback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            object : android.media.AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<out android.media.AudioDeviceInfo>?) {
                    updateDeviceState()
                }
                override fun onAudioDevicesRemoved(removedDevices: Array<out android.media.AudioDeviceInfo>?) {
                    updateDeviceState()
                }
            }
        } else null

        val filter = IntentFilter().apply {
            addAction(AudioManager.ACTION_HEADSET_PLUG)
            addAction("android.bluetooth.adapter.action.STATE_CHANGED")
            addAction("android.bluetooth.device.action.ACL_CONNECTED")
            addAction("android.bluetooth.device.action.ACL_DISCONNECTED")
            addAction("android.media.AUDIO_BECOMING_NOISY")
        }
        
        context.registerReceiver(receiver, filter)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && callback != null) {
            am.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        }
        
        awaitDispose {
            context.unregisterReceiver(receiver)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && callback != null) {
                am.unregisterAudioDeviceCallback(callback)
            }
        }
    }
    
    // Only this sheet's own slider and lyrics read position/duration, and both are off-screen
    // while collapsed -- the mini player has its own state. Polling unconditionally kept a
    // 20Hz recomposition running for as long as a track was loaded, paused or not.
    LaunchedEffect(isPlaying, state.isCollapsed, mediaMetadata?.id) {
        if (state.isCollapsed) return@LaunchedEffect
        while (isActive) {
            val rawDuration = playerConnection.player.duration
            position = playerConnection.player.currentPosition.coerceAtLeast(0L)
            duration = if (rawDuration == C.TIME_UNSET || rawDuration < 0) 0L else rawDuration
            delay(if (isPlaying) 50L else 500L)
        }
    }

    val staticColorInt by rememberPreference(PlayerStaticColorKey, defaultValue = 0xFF1A1A1A.toInt())
    val gradientStopsRaw by rememberPreference(PlayerGradientStopsKey, defaultValue = "")
    val gradientStops = remember(gradientStopsRaw) { decodeGradientStops(gradientStopsRaw) }
    val flatOnColor = remember(playerBackground, staticColorInt, gradientStops) {
        val luminance = when (playerBackground) {
            PlayerBackgroundStyle.CUSTOM_GRADIENT ->
                gradientStops.map { it.luminance() }.average().toFloat()
            else -> Color(staticColorInt).luminance()
        }
        if (luminance > 0.5f) Color.Black else Color.White
    }

    // Back to the original per-background pick -- no mesh blend, no global custom colour
    // override, just what it looked like before either of those existed.
    val adaptivePrimary by animateColorAsState(
        targetValue = when (playerBackground) {
            PlayerBackgroundStyle.DEFAULT -> AppTheme.colorScheme.onSurface
            PlayerBackgroundStyle.BLUR,
            PlayerBackgroundStyle.GRADIENT,
            PlayerBackgroundStyle.GLOW_ANIMATED,
            PlayerBackgroundStyle.APPLE_MUSIC,
            PlayerBackgroundStyle.LIVE_MESH,
            PlayerBackgroundStyle.BITCHORD_MESH,
            PlayerBackgroundStyle.GLASS -> Color.White
            PlayerBackgroundStyle.STATIC,
            PlayerBackgroundStyle.CUSTOM_GRADIENT -> flatOnColor
        },
        label = "adaptivePrimary"
    )
    // Back on, per user request: player chrome is composited additively onto the
    // background/mesh again.
    val additiveChrome = true
    val adaptiveSecondary by animateColorAsState(
        targetValue = when (playerBackground) {
            PlayerBackgroundStyle.DEFAULT -> AppTheme.colorScheme.onSurfaceVariant
            PlayerBackgroundStyle.BLUR -> Color.White.copy(alpha = FlamingoSecondaryAlpha)
            PlayerBackgroundStyle.GRADIENT -> Color.White.copy(alpha = FlamingoSecondaryAlpha)
            PlayerBackgroundStyle.GLOW_ANIMATED -> Color.White.copy(alpha = FlamingoSecondaryAlpha)
            PlayerBackgroundStyle.APPLE_MUSIC -> Color.White.copy(alpha = FlamingoSecondaryAlpha)
            PlayerBackgroundStyle.LIVE_MESH -> Color.White.copy(alpha = FlamingoSecondaryAlpha)
            PlayerBackgroundStyle.BITCHORD_MESH -> Color.White.copy(alpha = FlamingoSecondaryAlpha)
            PlayerBackgroundStyle.GLASS -> Color.White.copy(alpha = FlamingoSecondaryAlpha)
            PlayerBackgroundStyle.STATIC,
            PlayerBackgroundStyle.CUSTOM_GRADIENT -> flatOnColor.copy(alpha = FlamingoSecondaryAlpha)
        },
        label = "adaptiveSecondary"
    )
    // Flamingo runs a third step below the artist line for the two time labels, which are
    // reference text rather than content — reading them should never cost a glance.
    val adaptiveTime = if (additiveChrome) {
        Color.White.copy(alpha = FlamingoTimeAlpha)
    } else {
        adaptiveSecondary
    }
    val adaptiveSurface by animateColorAsState(
        targetValue = when (playerBackground) {
            PlayerBackgroundStyle.DEFAULT -> AppTheme.colorScheme.surfaceVariant
            PlayerBackgroundStyle.BLUR,
            PlayerBackgroundStyle.GRADIENT,
            PlayerBackgroundStyle.GLOW_ANIMATED,
            PlayerBackgroundStyle.APPLE_MUSIC,
            PlayerBackgroundStyle.LIVE_MESH,
            PlayerBackgroundStyle.BITCHORD_MESH,
            PlayerBackgroundStyle.GLASS -> Color.White.copy(alpha = 0.2f)
            PlayerBackgroundStyle.STATIC,
            PlayerBackgroundStyle.CUSTOM_GRADIENT -> flatOnColor.copy(alpha = 0.2f)
        },
        label = "adaptiveSurface"
    )

    // Matches the classic player's own rule (Player.kt): side-by-side on a wide/
    // landscape screen, unless the user asked tab view to keep the compact,
    // phone-shaped player. V17 had no wide branch at all before this -- in landscape
    // it stacked a squashed cover over the controls and ran out of height.
    val (compactPlayerInTabView) = rememberPreference(CompactPlayerInTabViewKey, false)
    val isWidePlayerLayout =
        LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE &&
            !(LocalTabView.current && compactPlayerInTabView)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                        if (event.changes.any { it.pressed }) {
                            lastInteractionTime = System.currentTimeMillis()
                            if (playerState != PlayerInternalState.QUEUE) controlsVisible = true
                        }
                    }
                }
            }
    ) {
        val systemBarsTopPadding = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()
        PlayerV2Frame(
            isWide = isWidePlayerLayout,
            // Hero Blend's artwork bleeds all the way to the true top of the screen, like the
            // classic player's own APPLE_MUSIC background -- so the frame doesn't reserve top
            // inset space here; the handle claims it itself instead, floating over the artwork.
            overlayHandle = isHeroLook,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = if (isHeroLook) 0.dp else systemBarsTopPadding)
                .padding(bottom = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding())
                // Landscape: keep content out of side gesture bars/cutouts,
                // like the classic player's landscape branch does.
                .then(
                    if (isWidePlayerLayout) Modifier.windowInsetsPadding(
                        WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)
                    ) else Modifier
                ),
            handle = {
            // Minimal drag handle header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = if (isHeroLook) systemBarsTopPadding + 16.dp else 16.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(48.dp)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(adaptivePrimary.copy(alpha = 0.3f))
                        .clickable { state.collapseSoft() }
                )
            }
            },
            pane = { paneModifier ->
            // Morphing Content Area (Cover vs Lyrics vs Queue)
            androidx.compose.animation.SharedTransitionLayout(
                    modifier = paneModifier
                ) {
                    AnimatedContent(
                        targetState = playerState,
                        transitionSpec = {
                            val enter = fadeIn(animationSpec = spring(dampingRatio = 0.82f, stiffness = 550f)) +
                                scaleIn(initialScale = 0.96f, animationSpec = spring(dampingRatio = 0.78f, stiffness = 550f))
                            val exit = fadeOut(animationSpec = spring(dampingRatio = 1.0f, stiffness = 650f)) +
                                scaleOut(targetScale = 0.96f, animationSpec = spring(dampingRatio = 1.0f, stiffness = 650f))
                            enter.togetherWith(exit).using(
                                // Ensure the Lyrics pane is drawn OVER the Cover pane during transition
                                androidx.compose.animation.SizeTransform(clip = false)
                            ).apply {
                                targetContentZIndex = if (targetState == PlayerInternalState.LYRICS || targetState == PlayerInternalState.QUEUE) 1f else 0f
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        label = "InternalWindow"
                    ) { targetState ->
                        if (targetState == PlayerInternalState.COVER) {
                            // DIY stickers with a negative z sit here, below the artwork. Anchored
                            // to diyRootCoordinates (captured in Player.kt's overlayContent, since
                            // that is a sibling of BOTH player skins) rather than this Box's own
                            // fillMaxSize() -- this Box only gets `pane`'s space, which
                            // PlayerV2Frame shrinks by its own handle and controls rows, a
                            // different amount than classic Player.kt's chrome does. A sticker
                            // placed correctly in the editor (which always previews via classic)
                            // was landing at the wrong size and place here for exactly that
                            // reason. See AbsoluteFrameDiyStickers' doc for the full story.
                            Box(modifier = Modifier.fillMaxSize()) {
                            if (showDiyStickers) {
                                AbsoluteFrameDiyStickers(
                                    rootCoordinates = diyRootCoordinates,
                                    orientation = diyOrientation,
                                    layout = diyLayout,
                                    zFilter = { it < 0 },
                                )
                            }
                            Column(
                                modifier = Modifier.fillMaxSize()
                            ) {
                                // Hero Blend starts flush from the top of the pane; Squared
                                // centres its card with a spacer above and below.
                                if (!isHeroLook) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }

                                // Massive Apple Music Style Artwork -- Squared keeps the classic
                                // centred square card; Hero Blend runs the artwork edge-to-edge
                                // and fades its bottom into the controls below. Frame, blend
                                // stops and foot are BYTE-identical to the classic APPLE_MUSIC
                                // clear-art box: full width, 0.65 of the SCREEN height (not the
                                // pane — the pane loses the bottom inset, which put this hero
                                // ~20dp higher than classic with an earlier blend start), knee
                                // 0.78, so both heroes read as the same image.
                                val heroScreenHeight = LocalConfiguration.current.screenHeightDp.dp
                                Box(
                                    modifier = if (isHeroLook) {
                                        Modifier
                                            .fillMaxWidth()
                                            .height(heroScreenHeight * 0.65f)
                                            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                                            .drawWithContent {
                                                drawContent()
                                                drawRect(
                                                    brush = com.convx.music.ui.component.heroFadeOutBrush(),
                                                    blendMode = BlendMode.DstIn
                                                )
                                            }
                                    } else if (isWidePlayerLayout) {
                                        // Landscape/tablet side-by-side: height is the
                                        // limiting dimension, a fillMaxWidth square
                                        // overflows the short pane. Constrain by
                                        // height like the classic player's landscape
                                        // Thumbnail does.
                                        Modifier
                                            .fillMaxHeight(0.65f)
                                            .aspectRatio(1f)
                                            .align(Alignment.CenterHorizontally)
                                    } else {
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 24.dp)
                                            .aspectRatio(1f)
                                    }
                                ) {
                                    // Live swipe peek: the neighbour song's own card, revealed from
                                    // whichever side the finger is dragging toward, tracking the drag
                                    // itself rather than only appearing once the gesture commits.
                                    val peekMetadata = if (heroDragOffset.value > 0f) previousMediaMetadata else nextMediaMetadata
                                    if (peekMetadata != null && heroDragOffset.value != 0f) {
                                        val peekDirection = if (heroDragOffset.value > 0f) 1f else -1f
                                        val screenWidthPx = LocalConfiguration.current.screenWidthDp *
                                            androidx.compose.ui.platform.LocalDensity.current.density
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .offset {
                                                    IntOffset(
                                                        (heroDragOffset.value - screenWidthPx * peekDirection).roundToInt(),
                                                        0,
                                                    )
                                                }
                                                .customSoftShadow(
                                                    elevation = playerThumbnailShadowElevation.dp,
                                                    cornerRadius = if (isHeroLook) 0.dp else 12.dp,
                                                    enabled = showPlayerThumbnailShadow && !isHeroLook
                                                )
                                                .background(adaptiveSurface, RoundedCornerShape(if (isHeroLook) 0.dp else 12.dp))
                                                .clip(RoundedCornerShape(if (isHeroLook) 0.dp else 12.dp))
                                        ) {
                                            AsyncImage(
                                                model = ImageRequest.Builder(context)
                                                    .data(peekMetadata.thumbnailUrl?.resize(1200, 1200))
                                                    .size(CoilSize.ORIGINAL)
                                                    .build(),
                                                contentDescription = "Cover Art",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        }
                                    }

                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .sharedElement(
                                                rememberSharedContentState(key = "coverArt"),
                                                animatedVisibilityScope = this@AnimatedContent
                                            )
                                            // Target end of the mini-to-full artwork morph.
                                            // Without this the overlay has no full rect to
                                            // grow into and the morph silently does nothing
                                            // on this player -- the classic player registers
                                            // its own artwork the same way.
                                            .registerFullArtworkRect(
                                                with(androidx.compose.ui.platform.LocalDensity.current) { 12.dp.toPx() }
                                            )
                                            // The overlay owns the cover for the whole
                                            // flight; without this both are on screen from
                                            // the handoff onward.
                                            .hideWhileMorphing()
                                            // Deliberately no background/clip/shadow here any more, and no
                                            // scale either -- this Box is only the morph's stable measurement
                                            // anchor now. Everything that actually paints (shadow, rounded
                                            // background, the image itself) lives one level in, inside the
                                            // AnimatedContent below, so the whole styled card -- not just the
                                            // image inside a fixed frame -- scales on pause and slides fully
                                            // off/on during a skip, with nothing here clipping it partway.
                                            .then(
                                                rememberSwipeCardModifier(
                                                    enabled = (playerState == PlayerInternalState.COVER && !isListenTogetherGuest),
                                                    playerConnection = playerConnection,
                                                    offsetXAnimatable = heroDragOffset,
                                                )
                                            )
                                            .then(
                                                rememberDragToQueueModifier(
                                                    enabled = (playerState == PlayerInternalState.COVER && !isListenTogetherGuest),
                                                    onOpenQueue = { playerState = PlayerInternalState.QUEUE },
                                                )
                                            )
                                    ) {
                                    // Hero Blend is edge-to-edge and full-screen from the top --
                                    // shrinking it on pause would tear it away from that fixed
                                    // frame, so only Squared's floating card does the pause-shrink.
                                    val heroScale by animateFloatAsState(
                                        // Measured off 60fps.design apple-music-play-pause (60fps):
                                        // artwork 667px playing, 486px paused (0.729). Play overshoots
                                        // ~11% of the travel then settles; pause glides in with no
                                        // undershoot. Both fit a 0.5s-response spring (stiffness 160),
                                        // damping 0.6 growing and 0.95 shrinking.
                                        targetValue = if (isPlaying || isHeroLook) 1f else 0.729f,
                                        animationSpec = spring(
                                            dampingRatio = if (isPlaying || isHeroLook) 0.6f else 0.95f,
                                            stiffness = 160f,
                                        ),
                                        label = "heroScale",
                                    )
                                    // Same morph-on-crossfade artwork as the classic player screen
                                    // (see Thumbnail.kt's ThumbnailImage) rather than a plain
                                    // AnimatedContent fade+scale swap: it plays the melt shader
                                    // during a real DJ crossfade (PlayerConnection.crossfadeDurationMs)
                                    // and a quick default dissolve otherwise, in one persistent
                                    // composable instead of tearing down/rebuilding per track id.
                                    val heroCrossfadeMs by playerConnection.crossfadeDurationMs.collectAsState()
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .graphicsLayer { scaleX = heroScale; scaleY = heroScale }
                                            .customSoftShadow(
                                                elevation = playerThumbnailShadowElevation.dp,
                                                cornerRadius = if (isHeroLook) 0.dp else 12.dp,
                                                enabled = showPlayerThumbnailShadow && !isHeroLook
                                            )
                                            .background(adaptiveSurface, RoundedCornerShape(if (isHeroLook) 0.dp else 12.dp))
                                            .clip(RoundedCornerShape(if (isHeroLook) 0.dp else 12.dp))
                                    ) {
                                        ThumbnailImage(
                                            artworkUri = mediaMetadata?.thumbnailUrl?.resize(1200, 1200),
                                            cropArtwork = true,
                                            crossfadeMillis = heroCrossfadeMs.takeIf { it > 0 }?.toInt() ?: 200,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                        // Same baked progressive foot as artist/classic player
                                        // (byte-identical stops): stills grow blur down the band;
                                        // under canvas video it is the poster until the first
                                        // frame. No Haze — Haze can't sample TextureView video.
                                        // Hero Blend only: the squared card is a plain cover, no foot blur.
                                        if (isHeroLook) {
                                            BakedFootBlur(
                                                artworkUrl = mediaMetadata?.thumbnailUrl,
                                                modifier = Modifier.fillMaxSize(),
                                            )
                                        }
                                        PlayerV2Canvas(
                                            mediaMetadata = mediaMetadata,
                                            // Collapsed sheet stays composed: pause the canvas decoder.
                                            isPlaying = isPlaying && !state.isCollapsed,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    }
                                }

                                Spacer(modifier = Modifier.weight(1f)) // Allows the content to lock down

                                // Track Meta Info (Left Aligned)
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        FadeSwapText(
                                            mediaMetadata?.title ?: "Unknown",
                                            style = AppTheme.typography.headlineSmall, 
                                            color = adaptivePrimary,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.sharedBounds(
                                                rememberSharedContentState(key = "title"),
                                                animatedVisibilityScope = this@AnimatedContent
                                            ).clickable {
                                                state.collapseSoft()
                                                mediaMetadata?.album?.id?.let { navController.navigate("album/$it") }
                                            }
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        FadeSwapText(
                                            mediaMetadata?.artists?.firstOrNull()?.name ?: "Unknown",
                                            style = AppTheme.typography.titleMedium,
                                            // Same extra dimming UpNextSong applies to its own
                                            // artist line (subtitleColor.copy(alpha = 0.7f)),
                                            // so both artist-name reads land at the same weight.
                                            color = adaptiveSecondary.copy(alpha = 0.7f),
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.sharedBounds(
                                                rememberSharedContentState(key = "artist"),
                                                animatedVisibilityScope = this@AnimatedContent
                                            ).clickable {
                                                state.collapseSoft()
                                                mediaMetadata?.artists?.firstOrNull()?.id?.let { navController.navigate("artist/$it") }
                                            }
                                                .additiveChrome(additiveChrome)
                                                .graphicsLayer { if (additiveChrome) alpha = AdditiveChromeContentAlpha }
                                        )
                                        DjReadout(
                                            modifier = Modifier.padding(top = 2.dp)
                                                .additiveChrome(additiveChrome)
                                                .graphicsLayer { if (additiveChrome) alpha = AdditiveChromeContentAlpha },
                                            color = adaptiveSecondary,
                                        )
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.sharedBounds(
                                            rememberSharedContentState(key = "actionButtons"),
                                            animatedVisibilityScope = this@AnimatedContent
                                        )
                                            .additiveChrome(additiveChrome)
                                            .graphicsLayer { if (additiveChrome) alpha = AdditiveChromeContentAlpha }
                                    ) {
                                        val isLiked = currentSong?.song?.liked == true
        
                                        // Flamingo's favourite/more glyphs draw their own
                                        // 0.1-alpha disc; the Box that used to sit here drew a
                                        // second one behind it.
                                        IconButton(
                                            onClick = playerConnection::toggleLike,
                                            modifier = Modifier.size(40.dp),
                                        ) {
                                            PlayerGlyph(
                                                slot = if (isLiked) PlayerIconSlot.V2_LIKED else PlayerIconSlot.V2_LIKE,
                                                fallback = if (isLiked) R.drawable.flamingo_favorited else R.drawable.flamingo_favorite,
                                                // Like sits in the primary row already at full-strength
                                                // adaptivePrimary either way -- filled vs outline glyph is
                                                // what signals liked, not a colour swap.
                                                tint = adaptivePrimary,
                                                modifier = Modifier.size(28.dp).likePop(isLiked, currentSong?.song?.id),
                                                contentDescription = "Like",
                                            )
                                        }
                                        
                                        Spacer(modifier = Modifier.width(8.dp))
                                        
                                        IconButton(
                                            modifier = Modifier.size(40.dp),
                                            onClick = {
                                                menuState.show {
                                                    PlayerMenu(
                                                        mediaMetadata = mediaMetadata,
                                                        navController = navController,
                                                        playerBottomSheetState = state,
                                                        onShowDetailsDialog = {
                                                            mediaMetadata?.id?.let {
                                                                bottomSheetPageState.show {
                                                                    ShowMediaInfo(it)
                                                                }
                                                            }
                                                        },
                                                        onDismiss = menuState::dismiss
                                                    )
                                                }
                                            }
                                        ) {
                                            PlayerGlyph(
                                                slot = PlayerIconSlot.V2_MORE,
                                                fallback = R.drawable.flamingo_more,
                                                tint = adaptivePrimary,
                                                modifier = Modifier.size(28.dp),
                                                contentDescription = "Options",
                                            )
                                        }
                                    }
                                    }
                                }
                            // The z >= 0 sticker band renders from Player.kt's overlayContent
                            // slot instead, a sibling of both player skins -- see that call
                            // site's comment.
                            }
                        } else if (targetState == PlayerInternalState.LYRICS || targetState == PlayerInternalState.QUEUE) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                // Apple Music Morphing Mini Header
                                //
                                // Hero Blend skips the pane's own systemBarsTopPadding (its art
                                // wants to bleed to the true top, under the floating handle) --
                                // but this header, unlike the art, isn't meant to sit under
                                // anything. Without its own top inset here it landed flush at y=0,
                                // straight under the status bar and the handle floating over it.
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            top = if (isHeroLook) {
                                                systemBarsTopPadding + 32.dp
                                            } else {
                                                8.dp
                                            },
                                            start = 24.dp,
                                            end = 24.dp,
                                            bottom = 8.dp,
                                        ),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .sharedElement(
                                                rememberSharedContentState(key = "coverArt"),
                                                animatedVisibilityScope = this@AnimatedContent
                                            )
                                            .size(64.dp)
                                            .customSoftShadow(
                                                elevation = playerThumbnailShadowElevation.dp / 2f,
                                                cornerRadius = 8.dp,
                                                enabled = showPlayerThumbnailShadow
                                            )
                                            .background(adaptiveSurface, RoundedCornerShape(8.dp))
                                            .clip(RoundedCornerShape(8.dp))
                                            // BitChord touch: the shrunk thumbnail is the way
                                            // back -- tapping it during the queue puts the cover
                                            // back full-size the same way opening it took it away.
                                            .then(
                                                if (targetState == PlayerInternalState.QUEUE) {
                                                    Modifier.clickable { playerState = PlayerInternalState.COVER }
                                                } else {
                                                    Modifier
                                                }
                                            )
                                    ) {
                                        AsyncImage(
                                            model = mediaMetadata?.thumbnailUrl?.resize(1200, 1200),
                                            contentDescription = "Cover Art",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                        PlayerV2Canvas(
                                            mediaMetadata = mediaMetadata,
                                            // Collapsed sheet stays composed: pause the canvas decoder.
                                            isPlaying = isPlaying && !state.isCollapsed,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            mediaMetadata?.title ?: "Unknown",
                                            style = AppTheme.typography.titleMedium, 
                                            color = adaptivePrimary,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.sharedBounds(
                                                rememberSharedContentState(key = "title"),
                                                animatedVisibilityScope = this@AnimatedContent
                                            )
                                        )
                                        Text(
                                            mediaMetadata?.artists?.firstOrNull()?.name ?: "Unknown",
                                            style = AppTheme.typography.titleSmall,
                                            // Same extra dimming as the portrait artist line above.
                                            color = adaptiveSecondary.copy(alpha = 0.7f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.sharedBounds(
                                                rememberSharedContentState(key = "artist"),
                                                animatedVisibilityScope = this@AnimatedContent
                                            )
                                                .additiveChrome(additiveChrome)
                                                .graphicsLayer { if (additiveChrome) alpha = AdditiveChromeContentAlpha }
                                        )
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.sharedBounds(
                                            rememberSharedContentState(key = "actionButtons"),
                                            animatedVisibilityScope = this@AnimatedContent
                                        )
                                            .additiveChrome(additiveChrome)
                                            .graphicsLayer { if (additiveChrome) alpha = AdditiveChromeContentAlpha }
                                    ) {
                                        val isLiked = currentSong?.song?.liked == true

                                        IconButton(
                                            onClick = playerConnection::toggleLike
                                        ) {
                                            Icon(
                                                painter = painterResource(if (isLiked) R.drawable.flamingo_favorited else R.drawable.flamingo_favorite),
                                                contentDescription = "Like",
                                                tint = adaptivePrimary,
                                                modifier = Modifier.size(24.dp).likePop(isLiked, currentSong?.song?.id)
                                            )
                                        }
                                        
                                        IconButton(
                                            onClick = {
                                                if (targetState == PlayerInternalState.QUEUE) {
                                                    menuState.show {
                                                        PlayerMenu(
                                                            mediaMetadata = mediaMetadata,
                                                            navController = navController,
                                                            playerBottomSheetState = state,
                                                            onShowDetailsDialog = {
                                                                mediaMetadata?.id?.let {
                                                                    bottomSheetPageState.show {
                                                                        ShowMediaInfo(it)
                                                                    }
                                                                }
                                                            },
                                                            onDismiss = menuState::dismiss
                                                        )
                                                    }
                                                } else {
                                                    menuState.show {
                                                        LyricsMenu(
                                                            lyricsProvider = { currentLyrics },
                                                            songProvider = { currentSong?.song },
                                                            mediaMetadataProvider = { mediaMetadata!! },
                                                            onDismiss = menuState::dismiss,
                                                            onShowOffsetDialog = {
                                                                bottomSheetPageState.show {
                                                                    ShowOffsetDialog(
                                                                        songProvider = { currentSong?.song }
                                                                    )
                                                                }
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.MoreVert,
                                                contentDescription = "Options",
                                                tint = adaptivePrimary,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                }
                                
                                // Dynamic Engine Block
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .animateEnterExit(
                                            enter = slideInVertically(animationSpec = Motion.appear()) { it } + fadeIn(Motion.appear()),
                                            exit = fadeOut(animationSpec = Motion.appearExit()) + slideOutVertically(animationSpec = Motion.appearExit()) { it }
                                        )
                                        .then(
                                            if (targetState == PlayerInternalState.QUEUE) {
                                                Modifier.nestedScroll(queueControlsScroll)
                                            } else {
                                                Modifier
                                            }
                                        )
                                ) {
                                    if (targetState == PlayerInternalState.LYRICS) {
                                        Lyrics(
                                            sliderPositionProvider = { sliderPosition ?: position },
                                            showLyrics = true,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        SingControl(
                                            engine = playerConnection.service.karaoke,
                                            tint = adaptivePrimary,
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(end = 20.dp, bottom = 12.dp),
                                        )
                                    } else {
                                        val queueSheetState = rememberBottomSheetState(
                                            dismissedBound = 0.dp,
                                            expandedBound = LocalConfiguration.current.screenHeightDp.dp,
                                            initialAnchor = expandedAnchor,
                                        )
                                        // Queue's own inner sheet drags independently of playerState,
                                        // the outer enum that actually decides whether this whole
                                        // block is composed. Drag it to dismissedBound and Queue()
                                        // (BottomSheet.kt:242,282) stops drawing BOTH its expanded
                                        // and collapsed content -- nothing left on screen -- while
                                        // playerState is still QUEUE, so nothing ever remounts a
                                        // fresh, expanded queueSheetState. Tying the two together
                                        // is what makes a drag-to-dismiss here behave like actually
                                        // leaving the queue.
                                        //
                                        // But `initialAnchor` only ever applies ONCE:
                                        // rememberBottomSheetState keeps the last anchor in a
                                        // rememberSaveable, so after the queue has been dismissed
                                        // even once, every later open remounts this sheet ALREADY
                                        // dismissed. The effect below then fired on the very first
                                        // frame and put playerState straight back to COVER -- which
                                        // is exactly the reported dead queue button: it works once,
                                        // then never again for the rest of the process. So re-open
                                        // the sheet on mount, and only let a dismissal mean "leave
                                        // the queue" once it has genuinely been open.
                                        var queueOpened by remember { mutableStateOf(false) }
                                        LaunchedEffect(Unit) {
                                            queueSheetState.expandSoft()
                                        }
                                        LaunchedEffect(queueSheetState.isExpanded) {
                                            if (queueSheetState.isExpanded) queueOpened = true
                                        }
                                        LaunchedEffect(queueSheetState.isDismissed) {
                                            if (queueOpened && queueSheetState.isDismissed) {
                                                playerState = PlayerInternalState.COVER
                                            }
                                        }
                                        // Cover art fading in behind the queue list from fully
                                        // transparent, anchored to the top like Hero Blend's own
                                        // artwork -- Queue's own background stays transparent so
                                        // the list sits on top of this rather than replacing it.
                                        val queueBackdropAlpha = remember { Animatable(0f) }
                                        LaunchedEffect(Unit) {
                                            queueBackdropAlpha.animateTo(1f, animationSpec = Motion.appear())
                                        }
                                        Box(modifier = Modifier.fillMaxSize()) {
                                            Queue(
                                                state = queueSheetState,
                                                playerBottomSheetState = state,
                                                navController = navController,
                                                background = Color.Transparent,
                                                onBackgroundColor = adaptivePrimary,
                                                TextBackgroundColor = adaptivePrimary,
                                                textButtonColor = adaptivePrimary,
                                                iconButtonColor = adaptivePrimary,
                                                pureBlack = false,
                                                showInlineLyrics = false,
                                                playerBackground = playerBackground,
                                                // V17's own controls behind this sheet already show
                                                // the thumbnail/title/like — the shared header row
                                                // would just repeat them.
                                                showHeaderRow = false,
                                                // Drag-to-dismiss on this sheet was getting stuck —
                                                // the queue toggle icon and back gesture still close it.
                                                sheetDraggable = false,
                                                modifier = Modifier.fillMaxSize().padding(top = 16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                }
            }
            },
            controls = {
            // Persistent Controls Array (Always at the bottom)
            AnimatedVisibility(
                    visible = controlsVisible,
                    enter = fadeIn(animationSpec = ControlsShowSpec()) +
                            slideInVertically(animationSpec = ControlsShowSpec()) { it / 2 } +
                            androidx.compose.animation.expandVertically(animationSpec = ControlsShowSpec()),
                    exit = fadeOut(animationSpec = ControlsHideSpec()) +
                           slideOutVertically(animationSpec = ControlsHideSpec()) { it / 2 } +
                           androidx.compose.animation.shrinkVertically(animationSpec = ControlsHideSpec())
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {}
                            )
                            .padding(horizontal = 24.dp)
                    ) {
                        // Apple Music Timeline Slider
                    val currentPos = sliderPosition ?: position
                    
                    val trackInteractionSource = remember { MutableInteractionSource() }
                    val isTrackDragged by trackInteractionSource.collectIsDraggedAsState()
                    val isTrackPressed by trackInteractionSource.collectIsPressedAsState()
                    val isTrackActive = isTrackDragged || isTrackPressed
                    
                    val trackHeight by animateDpAsState(
                        targetValue = if (isTrackActive) 12.dp else 6.dp,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                        label = "trackScale"
                    )
                    
                    Slider(
                        value = currentPos.toFloat(),
                        valueRange = 0f..(if (duration == androidx.media3.common.C.TIME_UNSET) 0f else duration.toFloat()),
                        onValueChange = { value ->
                            if (!isListenTogetherGuest) {
                                sliderPosition = value.toLong()
                            }
                        },
                        onValueChangeFinished = {
                            if (!isListenTogetherGuest) {
                                sliderPosition?.let { pos ->
                                    playerConnection.player.seekTo(pos)
                                    position = pos
                                    sliderPosition = null
                                }
                            }
                        },
                        enabled = !isListenTogetherGuest,
                        interactionSource = trackInteractionSource,
                        thumb = { Spacer(modifier = Modifier.size(0.dp)) },
                        track = { sliderState ->
                            PlayerSliderTrack(
                                sliderState = sliderState,
                                trackHeight = trackHeight,
                                colors = PlayerSliderColors.getSliderColors(
                                    activeColor = if (additiveChrome) adaptivePrimary else adaptivePrimary.copy(alpha = 0.8f),
                                    // Flamingo's bar: the unplayed half is a hairline, not a
                                    // grey rail. It reads at 0.05 only because the whole thing
                                    // is added onto the artwork rather than laid over it.
                                    inactiveAlpha = if (additiveChrome) 0.05f else 0.3f,
                                )
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .additiveChrome(additiveChrome)
                            // After additiveChrome, never before: alpha applied first is
                            // folded into the saved layer and the add then runs at full strength.
                            .graphicsLayer { if (additiveChrome) alpha = AdditiveChromeContentAlpha }
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp) // Align with internal slider padding
                            .additiveChrome(additiveChrome)
                            .graphicsLayer { if (additiveChrome) alpha = AdditiveChromeContentAlpha },
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(makeTimeString(currentPos), color = adaptiveTime, style = AppTheme.typography.labelSmall, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp)
                        // Collapsed-gated too: this sheet stays composed behind the mini player,
                        // and the badge's infinite spin kept the whole app drawing every vsync.
                        AudioQualityBadge(playerConnection, adaptivePrimary, isPlaying && !state.isCollapsed)
                        Text("-" + makeTimeString(maxOf(0L, duration - currentPos)), color = adaptiveTime, style = AppTheme.typography.labelSmall, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp)
                    }

                    if (showUpNext) {
                        UpNextSong(
                            playerConnection = playerConnection,
                            titleColor = adaptivePrimary,
                            subtitleColor = adaptiveSecondary,
                            modifier = Modifier.padding(vertical = 4.dp),
                            additiveChrome = additiveChrome,
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Naked Transparent Playback Controls
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .additiveChrome(additiveChrome)
                            .graphicsLayer { if (additiveChrome) alpha = AdditiveChromeContentAlpha },
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val previousInteraction = remember { MutableInteractionSource() }
                        IconButton(
                            onClick = { if (!isListenTogetherGuest && canSkipPrevious) playerConnection.player.seekToPrevious() },
                            enabled = !isListenTogetherGuest && canSkipPrevious,
                            interactionSource = previousInteraction,
                            modifier = Modifier
                                .size(61.dp)
                                .transportPressDisc(previousInteraction, adaptivePrimary.copy(alpha = 0.16f))
                                .alpha(if (isListenTogetherGuest || !canSkipPrevious) 0.4f else 1f)
                        ) {
                            PlayerGlyph(
                                slot = PlayerIconSlot.V2_PREVIOUS,
                                fallback = R.drawable.flamingo_rewind,
                                tint = if (additiveChrome) adaptivePrimary else adaptivePrimary.copy(alpha = 0.8f),
                                modifier = Modifier.size(41.dp).skipGlyphPress(previousInteraction, direction = -1f),
                                contentDescription = "Previous",
                            )
                        }

                        Spacer(modifier = Modifier.width(43.dp))

                        val playInteraction = remember { MutableInteractionSource() }
                        IconButton(
                            onClick = {
                                if (isListenTogetherGuest) {
                                    playerConnection.toggleMute()
                                } else {
                                    playerConnection.player.togglePlayPause()
                                }
                            },
                            interactionSource = playInteraction,
                            modifier = Modifier
                                .size(58.5.dp)
                                .transportPressDisc(playInteraction, adaptivePrimary.copy(alpha = 0.16f))
                        ) {
                            if (isListenTogetherGuest) {
                                Icon(
                                    imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                    contentDescription = if (isMuted) "Unmute" else "Mute",
                                    modifier = Modifier.size(38.5.dp),
                                    tint = if (additiveChrome) adaptivePrimary else adaptivePrimary.copy(alpha = 0.8f)
                                )
                            } else {
                                AnimatedPlayerGlyph(
                                    modifier = Modifier.playGlyphPress(playInteraction),
                                    isPlaying = isPlaying,
                                    playSlot = PlayerIconSlot.V2_PLAY,
                                    pauseSlot = PlayerIconSlot.V2_PAUSE,
                                    playFallback = R.drawable.flamingo_play,
                                    pauseFallback = R.drawable.flamingo_pause,
                                    tint = if (additiveChrome) adaptivePrimary else adaptivePrimary.copy(alpha = 0.8f),
                                    playSize = 40.5.dp,
                                    pauseSize = 38.5.dp,
                                    playDescription = "Play",
                                    pauseDescription = "Pause",
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(43.dp))

                        val nextInteraction = remember { MutableInteractionSource() }
                        IconButton(
                            onClick = { if (!isListenTogetherGuest && canSkipNext) playerConnection.player.seekToNext() },
                            enabled = !isListenTogetherGuest && canSkipNext,
                            interactionSource = nextInteraction,
                            modifier = Modifier
                                .size(61.dp)
                                .transportPressDisc(nextInteraction, adaptivePrimary.copy(alpha = 0.16f))
                                .alpha(if (isListenTogetherGuest || !canSkipNext) 0.4f else 1f)
                        ) {
                            PlayerGlyph(
                                slot = PlayerIconSlot.V2_NEXT,
                                fallback = R.drawable.flamingo_fforward,
                                tint = if (additiveChrome) adaptivePrimary else adaptivePrimary.copy(alpha = 0.8f),
                                modifier = Modifier.size(41.dp).skipGlyphPress(nextInteraction, direction = 1f),
                                contentDescription = "Next",
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Audio Volume Component — hidden while the player menu or a
                    // settings-style bottom sheet page is covering the controls, so
                    // it isn't sitting there fighting for attention behind the sheet.
                    androidx.compose.animation.AnimatedVisibility(
                        visible = !menuState.isVisible && !bottomSheetPageState.isVisible,
                        enter = androidx.compose.animation.fadeIn(),
                        exit = androidx.compose.animation.fadeOut(),
                    ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .additiveChrome(additiveChrome)
                            // Alpha + cleared semantics, not removed from the tree: same as the
                            // classic player's HideVolumeBarKey handling -- the row still reserves
                            // its layout space so a DIY sticker placed relative to it doesn't jump.
                            .alpha(if (hideVolumeBar) 0f else 1f)
                            .then(if (hideVolumeBar) Modifier.clearAndSetSemantics {} else Modifier),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ElasticVolumeSlider(
                            value = animatedVolume,
                            onValueChange = { newValue ->
                                systemVolume = newValue
                                val targetVolume = (newValue * maxSystemVolume).toInt()
                                audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, targetVolume, 0)
                            },
                            enabled = !hideVolumeBar,
                            activeColor = adaptivePrimary.copy(alpha = 0.8f),
                            inactiveColor = adaptivePrimary.copy(alpha = 0.3f),
                            modifier = Modifier.fillMaxWidth(),
                            leftIcon = {
                                Icon(Icons.Default.VolumeMute, contentDescription = "Volume Down", tint = adaptiveSecondary, modifier = Modifier.size(20.dp))
                            },
                            rightIcon = {
                                Icon(Icons.Default.VolumeUp, contentDescription = "Volume Up", tint = adaptiveSecondary, modifier = Modifier.size(20.dp))
                            },
                        )
                        }
                    }
                    }
                }
            // (Controls AnimatedVisibility closed above)

            // Bottom Utility Action Bar
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(animationSpec = ControlsShowSpec()) +
                        slideInVertically(animationSpec = ControlsShowSpec()) { it / 2 } +
                        androidx.compose.animation.expandVertically(animationSpec = ControlsShowSpec()),
                exit = fadeOut(animationSpec = ControlsHideSpec()) +
                       slideOutVertically(animationSpec = ControlsHideSpec()) { it / 2 } +
                       androidx.compose.animation.shrinkVertically(animationSpec = ControlsHideSpec())
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {}
                        )
                        .additiveChrome(additiveChrome)
                        .graphicsLayer { if (additiveChrome) alpha = AdditiveChromeContentAlpha }
                        .padding(horizontal = 16.dp, vertical = 24.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                val isLyricsActive = playerState == PlayerInternalState.LYRICS
                IconButton(
                    onClick = { playerState = if (isLyricsActive) PlayerInternalState.COVER else PlayerInternalState.LYRICS }
                ) {
                    PlayerGlyph(
                        slot = PlayerIconSlot.V2_LYRICS,
                        fallback = if (isLyricsActive) R.drawable.flamingo_lyricson else R.drawable.flamingo_lyrics,
                        // Active = full-strength adaptivePrimary instead of the dim
                        // adaptiveSecondary every other bottom-bar icon sits at -- same
                        // white tint, just boosted to full brightness/opacity so it reads
                        // as lit up, no separate hue involved.
                        tint = if (isLyricsActive) adaptivePrimary else adaptiveSecondary,
                        modifier = Modifier.size(32.dp),
                        contentDescription = "Lyrics",
                    )
                }

                Box(contentAlignment = Alignment.Center) {
                    if (bluetoothDeviceName != null) {
                        IconButton(
                            onClick = { showAudioDeviceBottomSheet = true },
                            modifier = Modifier.background(Color.Transparent, RoundedCornerShape(12.dp))
                        ) {
                            Icon(
                                Icons.Default.Bluetooth,
                                contentDescription = "Audio Device",
                                tint = adaptiveSecondary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Text(
                            text = bluetoothDeviceName!!,
                            style = AppTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = adaptiveSecondary.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier
                                .absoluteOffset(y = 30.dp)
                                .widthIn(max = 84.dp)
                        )
                    } else if (BuildConfig.CAST_AVAILABLE && enableGoogleCast) {
                        CastButton(
                            modifier = Modifier.size(48.dp),
                            tintColor = adaptiveSecondary,
                        )
                    } else {
                        IconButton(
                            onClick = { showAudioDeviceBottomSheet = true },
                            modifier = Modifier.background(Color.Transparent, RoundedCornerShape(12.dp))
                        ) {
                            PlayerGlyph(
                                slot = PlayerIconSlot.V2_VOLUME_DOWN,
                                fallback = R.drawable.flamingo_volume,
                                tint = adaptiveSecondary,
                                modifier = Modifier.size(32.dp),
                                contentDescription = "Speaker",
                            )
                        }
                    }
                }
                
                val isQueueActive = playerState == PlayerInternalState.QUEUE
                IconButton(
                    onClick = { playerState = if (isQueueActive) PlayerInternalState.COVER else PlayerInternalState.QUEUE }
                ) {
                    PlayerGlyph(
                        slot = PlayerIconSlot.V2_QUEUE,
                        fallback = if (isQueueActive) R.drawable.flamingo_queueon else R.drawable.flamingo_queue,
                        tint = if (isQueueActive) adaptivePrimary else adaptiveSecondary,
                        modifier = Modifier.size(32.dp),
                        contentDescription = "Queue",
                    )
                }
                }
            }
            },
        )

        if (showAudioDeviceBottomSheet) {
            AudioDeviceBottomSheet(onDismiss = { showAudioDeviceBottomSheet = false })
        }
    }
}

/**
 * The V17 player's outer frame, in one of two arrangements.
 *
 * Portrait stacks the pane over the controls, exactly as before. Landscape (and tab
 * view, unless it was set to stay compact) puts them side by side, which is what the
 * classic player already does at `Player.kt`'s `isLandscapeLayout` branch: artwork or
 * lyrics on one half, the whole control stack centred on the other. Stacking them on a
 * short, wide screen left the cover a letterbox strip and pushed the transport row off
 * the bottom.
 *
 * [pane] is handed the modifier that gives it its share of the space, because that is
 * `weight` in one arrangement and `fillMaxSize` inside a weighted Box in the other.
 */
@Composable
private fun PlayerV2Frame(
    isWide: Boolean,
    modifier: Modifier,
    handle: @Composable ColumnScope.() -> Unit,
    pane: @Composable (Modifier) -> Unit,
    controls: @Composable ColumnScope.() -> Unit,
    // Hero Blend: the handle floats over the pane's own top edge instead of pushing it
    // down, so the artwork can bleed all the way to the true top of the screen.
    overlayHandle: Boolean = false,
) {
    if (overlayHandle) {
        Box(modifier = modifier) {
            if (isWide) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        contentAlignment = Alignment.Center,
                    ) {
                        pane(Modifier.fillMaxSize())
                    }
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                        content = controls,
                    )
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    pane(Modifier.weight(1f))
                    controls()
                }
            }
            Column(modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(), content = handle)
        }
    } else {
        Column(modifier = modifier) {
            handle()
            if (isWide) {
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        contentAlignment = Alignment.Center,
                    ) {
                        pane(Modifier.fillMaxSize())
                    }
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                        content = controls,
                    )
                }
            } else {
                pane(Modifier.weight(1f))
                controls()
            }
        }
    }
}

/**
 * The classic mini player's swipe-to-skip card drag, ported onto V17's own cover
 * artwork: the whole artwork follows the finger and springs back (or commits a skip
 * past the sensitivity-scaled threshold, or on a fast flick) instead of the plain
 * gesture-only swipe detector this replaces having no visual feedback at all.
 */
@Composable
private fun rememberSwipeCardModifier(
    enabled: Boolean,
    playerConnection: PlayerConnection,
    // Hoisted so the caller can read the live drag position too, to slide the next/previous
    // song's own thumbnail in as a preview while the finger is still moving -- not just after
    // the gesture commits.
    offsetXAnimatable: Animatable<Float, AnimationVector1D>,
): Modifier {
    val (swipeThumbnail) = rememberPreference(SwipeThumbnailKey, true)
    val (swipeSensitivity) = rememberPreference(SwipeSensitivityKey, 0.73f)
    val coroutineScope = rememberCoroutineScope()
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val configuration = LocalConfiguration.current
    val layoutDirection = androidx.compose.ui.platform.LocalLayoutDirection.current

    var dragStartTime by remember { mutableLongStateOf(0L) }
    var totalDragDistance by remember { mutableFloatStateOf(0f) }
    val animationSpec = remember {
        spring<Float>(dampingRatio = 0.74f, stiffness = 650f)
    }
    val autoSwipeThreshold = remember(swipeSensitivity, configuration.screenWidthDp, density) {
        val screenWidthPx = configuration.screenWidthDp * density
        (screenWidthPx * (0.45f - 0.33f * swipeSensitivity.coerceIn(0f, 1f))).roundToInt()
    }

    return Modifier
        .offset { IntOffset(offsetXAnimatable.value.roundToInt(), 0) }
        .then(
            if (enabled && swipeThumbnail) {
                Modifier.pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            dragStartTime = System.currentTimeMillis()
                            totalDragDistance = 0f
                        },
                        onDragCancel = {
                            coroutineScope.launch { offsetXAnimatable.animateTo(0f, animationSpec) }
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            val adjustedDragAmount =
                                if (layoutDirection == LayoutDirection.Rtl) -dragAmount else dragAmount
                            val canSkipPrevious = playerConnection.player.previousMediaItemIndex != -1
                            val canSkipNext = playerConnection.player.nextMediaItemIndex != -1
                            val tryingToSwipeRight = adjustedDragAmount > 0
                            val tryingToSwipeLeft = adjustedDragAmount < 0
                            val allowLeft = tryingToSwipeLeft && canSkipNext
                            val allowRight = tryingToSwipeRight && canSkipPrevious
                            val canReturnToCenter =
                                (tryingToSwipeRight && !canSkipPrevious && offsetXAnimatable.value < 0) ||
                                    (tryingToSwipeLeft && !canSkipNext && offsetXAnimatable.value > 0)
                            if (allowLeft || allowRight || canReturnToCenter) {
                                totalDragDistance += kotlin.math.abs(adjustedDragAmount)
                                coroutineScope.launch {
                                    offsetXAnimatable.snapTo(offsetXAnimatable.value + adjustedDragAmount)
                                }
                            }
                        },
                        onDragEnd = {
                            val dragDuration = System.currentTimeMillis() - dragStartTime
                            val velocity = if (dragDuration > 0) totalDragDistance / dragDuration else 0f
                            val currentOffset = offsetXAnimatable.value
                            val dragged = kotlin.math.abs(currentOffset)
                            val canSkipPrevious = playerConnection.player.previousMediaItemIndex != -1
                            val canSkipNext = playerConnection.player.nextMediaItemIndex != -1
                            val shouldChangeSong = dragged > autoSwipeThreshold ||
                                (velocity > 0.55f && dragged > autoSwipeThreshold * 0.25f)
                            if (shouldChangeSong) {
                                if (currentOffset > 0 && canSkipPrevious) {
                                    playerConnection.seekToPrevious()
                                } else if (currentOffset <= 0 && canSkipNext) {
                                    playerConnection.seekToNext()
                                }
                            }
                            // Either way, spring the same offset back to 0 -- committed or
                            // cancelled, this is what carries the live peek the rest of the way
                            // in (or back out). AnimatedContent below only fades the content
                            // identity in underneath once mediaMetadata changes; it doesn't also
                            // slide, so there's only ever one thing moving the card horizontally.
                            coroutineScope.launch { offsetXAnimatable.animateTo(0f, animationSpec) }
                        },
                    )
                }
            } else {
                Modifier
            }
        )
}

/**
 * BitChord touch, ported: dragging the cover artwork upward opens the queue --
 * the shared-element transition (see the "coverArt" key above) then does the
 * actual shrink-and-rise-to-the-header morph, same as the button-triggered
 * open already did. This only decides *when* that morph fires.
 *
 * Coexists with [rememberSwipeCardModifier]'s horizontal drag as a second,
 * independent `pointerInput` on the same node: `detectVerticalDragGestures`
 * and `detectHorizontalDragGestures` each wait out their own axis's touch
 * slop before consuming, so a horizontal swipe never trips this and a
 * vertical drag never trips the skip gesture.
 */
@Composable
private fun rememberDragToQueueModifier(
    enabled: Boolean,
    onOpenQueue: () -> Unit,
): Modifier {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val openThresholdPx = remember(density) { with(density) { 80.dp.toPx() } }
    var dragged by remember { mutableFloatStateOf(0f) }
    return if (enabled) {
        Modifier.pointerInput(Unit) {
            detectVerticalDragGestures(
                onDragStart = { dragged = 0f },
                onDragCancel = { dragged = 0f },
                onVerticalDrag = { change, dragAmount ->
                    // Only upward drags are this gesture's; downward is left
                    // unconsumed for the outer sheet's own drag-to-dismiss.
                    if (dragAmount < 0f || dragged < 0f) {
                        dragged += dragAmount
                        change.consume()
                    }
                },
                onDragEnd = {
                    if (dragged <= -openThresholdPx) onOpenQueue()
                    dragged = 0f
                },
            )
        }
    } else {
        Modifier
    }
}

/** Title stays white; the artist line sits well back. Flamingo's own value. */
private const val FlamingoSecondaryAlpha = 0.35f

/** Elapsed/remaining under the seek bar — quieter again than the artist line. */
private const val FlamingoTimeAlpha = 0.3f

/**
 * Strength of white content added onto the background under [additiveChrome]. Below full so
 * the blend doesn't clip to plain white -- the background's own colour and saturation stay
 * visible through the tint instead of getting washed out.
 */
private const val AdditiveChromeContentAlpha = 0.45f

/**
 * Flamingo's transport press: the button dips under the finger and springs back rather than
 * flashing a ripple. Needs the same [interactionSource] the button is given, so the scale
 * tracks the real press rather than a second gesture detector layered over it.
 */
/**
 * Apple Music's transport press, read off designRefrence/GVRecorder_1788117512105~3.mp4 (a
 * real device capture): a translucent disc snaps in under the finger as the press lands,
 * growing from ~0.85x, holds while the finger is down, and fades ~150ms after release.
 */
@Composable
private fun Modifier.transportPressDisc(
    interactionSource: MutableInteractionSource,
    color: androidx.compose.ui.graphics.Color,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val disc by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = if (pressed) tween(60) else tween(150, delayMillis = 60),
        label = "transportDisc",
    )
    return drawBehind {
        if (disc > 0f) {
            drawCircle(
                color = color,
                radius = size.minDimension / 2f * (0.85f + 0.15f * disc),
                alpha = disc,
            )
        }
    }
}

/**
 * Play/pause glyph under the finger: shrinks toward 0.2x over ~200ms while held; on release
 * the swapped glyph springs back up (AnimatedPlayerGlyph's enter). Same capture as above.
 */
@Composable
private fun Modifier.playGlyphPress(interactionSource: MutableInteractionSource): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.2f else 1f,
        animationSpec = if (pressed) {
            tween(220, easing = FastOutSlowInEasing)
        } else {
            spring(dampingRatio = 0.75f, stiffness = 500f)
        },
        label = "playGlyphPress",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Skip glyph: dips slightly under the finger, and on release nudges in the skip direction
 * and back -- the reference's arrows step forward rather than scale.
 */
@Composable
private fun Modifier.skipGlyphPress(interactionSource: MutableInteractionSource, direction: Float): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = spring(dampingRatio = 1f, stiffness = 900f),
        label = "skipGlyphScale",
    )
    val nudge = remember { androidx.compose.animation.core.Animatable(0f) }
    var wasPressed by remember { mutableStateOf(false) }
    LaunchedEffect(pressed) {
        if (wasPressed && !pressed) {
            nudge.animateTo(0f, keyframes {
                durationMillis = 260
                0f at 0
                1f at 110 using FastOutSlowInEasing
                0f at 260 using FastOutSlowInEasing
            })
        }
        wasPressed = pressed
    }
    val nudgePx = with(androidx.compose.ui.platform.LocalDensity.current) { 8.dp.toPx() }
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
        translationX = nudge.value * nudgePx * direction
    }
}

/**
 * Apple Music's favourite pop, keyframed off 60fps.design apple-music-animated-favourite-icon
 * (60fps, glyph bbox per frame, outline star = 25px):
 *  - 0..100ms: glyph crossfades outline -> filled, nearly invisible at ~60ms, 0.88x -> 1.08x
 *  - 100..283ms: swells to 1.56x, ease-out (27,31,32,34,35,36,36,38,38,39px)
 *  - 283..517ms: holds
 *  - 517..700ms: settles back to 1x
 * The same timeline plays on unliking (user ask), onto the outline glyph. The first
 * composition, and a song change, stay still.
 */
@Composable
private fun Modifier.likePop(isLiked: Boolean, songId: String?): Modifier {
    val scale = remember { androidx.compose.animation.core.Animatable(1f) }
    val alpha = remember { androidx.compose.animation.core.Animatable(1f) }
    // Re-keyed per song: skipping to a song with a different liked state is not a tap.
    var previous by remember(songId) { mutableStateOf(isLiked) }
    LaunchedEffect(isLiked, songId) {
        val changed = previous != isLiked
        previous = isLiked
        if (!changed) return@LaunchedEffect
        launch {
            scale.animateTo(1f, keyframes {
                durationMillis = 700
                0.88f at 0
                1.08f at 100 using LinearEasing
                1.56f at 283 using CubicBezierEasing(0.2f, 0.8f, 0.3f, 1f)
                1.56f at 517 using LinearEasing
                1f at 700 using FastOutSlowInEasing
            })
        }
        launch {
            alpha.animateTo(1f, keyframes {
                durationMillis = 110
                1f at 0
                0.08f at 60 using LinearEasing
                1f at 110 using LinearEasing
            })
        }
    }
    return graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
        this.alpha = alpha.value
    }
}

// Transport controls leaving / returning (queue scroll, lyrics idle), timed off
// 60fps.design apple-music-player-controls-collapse (60fps): hide runs ~217ms, show ~383ms.
private fun <T> ControlsHideSpec(): FiniteAnimationSpec<T> = tween(220, easing = FastOutSlowInEasing)
private fun <T> ControlsShowSpec(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 250f)

/**
 * Now Playing title/artist as Apple Music draws them (designRefrence/GVRecorder_1788117512105~3.mp4):
 * on a track change the old line fades out, a blank beat, then the new one fades in; a line
 * too long for its row scrolls instead of ellipsizing.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun FadeSwapText(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    color: androidx.compose.ui.graphics.Color,
    fontWeight: FontWeight,
    modifier: Modifier = Modifier,
) {
    androidx.compose.animation.AnimatedContent(
        targetState = text,
        transitionSpec = {
            fadeIn(tween(220, delayMillis = 90)) togetherWith fadeOut(tween(90))
        },
        contentAlignment = Alignment.CenterStart,
        label = "fadeSwapText",
        modifier = modifier,
    ) { value ->
        Text(
            value,
            style = style,
            color = color,
            fontWeight = fontWeight,
            maxLines = 1,
            modifier = Modifier.basicMarquee(
                iterations = Int.MAX_VALUE,
                initialDelayMillis = 1200,
            ),
        )
    }
}

/**
 * Apple Music Sing's control (60fps.design apple-music-sing, 60fps): a small rounded mic button
 * in the lyrics view that springs up into a tall capsule slider while held or dragged, the fill
 * rising from the bottom with the vocal level, the glyph turning dark once the fill covers it,
 * then sinks back into the button about a second after release.
 *
 * Tap toggles Sing (tapping it on also opens the slider once, so the level is discoverable);
 * press-and-drag adjusts the level. Nothing runs until the first tap -- see KaraokeEngine.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun SingControl(
    engine: com.convx.music.playback.karaoke.KaraokeEngine,
    tint: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    val state by engine.state.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    val expansion = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(expanded) {
        if (expanded) {
            // Overshoots ~10% of its height on the way up, like the capture.
            expansion.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 380f))
        } else {
            expansion.animateTo(0f, spring(dampingRatio = 1f, stiffness = 90f))
        }
    }
    LaunchedEffect(expanded, dragging) {
        if (expanded && !dragging) {
            delay(1400)
            expanded = false
        }
    }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val collapsedW = 36.dp; val collapsedH = 36.dp
    val expandedW = 58.dp; val expandedH = 150.dp
    val e = expansion.value
    val w = androidx.compose.ui.unit.lerp(collapsedW, expandedW, e.coerceAtLeast(0f))
    val h = androidx.compose.ui.unit.lerp(collapsedH, expandedH, e)
    val fill = if (state.enabled) state.level else 0f
    val animatedFill by animateFloatAsState(fill, spring(dampingRatio = 1f, stiffness = 900f), label = "singFill")
    val corner = androidx.compose.ui.unit.lerp(10.dp, 22.dp, e.coerceIn(0f, 1f))
    val shape = RoundedCornerShape(corner)
    val iconDark = state.enabled && e > 0.3f && animatedFill > 0.22f

    Box(
        modifier = modifier
            .size(width = w, height = h)
            .clip(shape)
            .background(tint.copy(alpha = if (state.enabled) 0.22f else 0.12f))
            .drawBehind {
                if (e > 0.02f && animatedFill > 0f) {
                    val fh = size.height * animatedFill
                    drawRect(tint, topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - fh),
                        size = androidx.compose.ui.geometry.Size(size.width, fh))
                }
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = {
                        if (!engine.state.value.enabled) engine.setEnabled(true)
                        dragging = true; expanded = true
                    },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                ) { change, dragAmount ->
                    change.consume()
                    val travel = with(density) { expandedH.toPx() }
                    engine.setLevel(engine.state.value.level - dragAmount / travel)
                }
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                val on = !engine.state.value.enabled
                engine.setEnabled(on)
                expanded = on
            },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Icon(
            painter = painterResource(R.drawable.mic),
            contentDescription = "Sing",
            tint = if (iconDark) androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.8f) else tint,
            modifier = Modifier
                .padding(bottom = androidx.compose.ui.unit.lerp(8.dp, 14.dp, e.coerceIn(0f, 1f)))
                .size(androidx.compose.ui.unit.lerp(20.dp, 26.dp, e.coerceIn(0f, 1f))),
        )
        // Analysis still running for this track: a thin ring of progress under the button edge.
        if (state.enabled && state.progress < 1f && e < 0.5f) {
            androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
                drawArc(
                    color = tint,
                    startAngle = -90f,
                    sweepAngle = 360f * state.progress,
                    useCenter = false,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()),
                )
            }
        }
    }
}
