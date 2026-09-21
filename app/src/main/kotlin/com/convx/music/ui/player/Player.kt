/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.ui.player

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.view.WindowManager
import android.widget.Toast
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import com.convx.music.ui.ios.compat.AlertDialog
import com.convx.music.ui.ios.compat.CircularWavyProgressIndicator
import com.convx.music.ui.ios.compat.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import com.convx.music.ui.ios.compat.Icon
import androidx.compose.material3.IconButtonDefaults
import com.convx.music.ui.ios.compat.LocalContentColor
import com.convx.music.ui.ios.compat.AppTheme
import androidx.compose.material3.OutlinedIconButton
import com.convx.music.ui.ios.compat.ProvideTextStyle
import androidx.compose.runtime.produceState
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import com.convx.music.ui.ios.compat.Text
import com.convx.music.ui.ios.compat.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.toArgb
import coil3.size.Size as CoilSize
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Player.STATE_ENDED
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.navigation.NavController
import androidx.palette.graphics.Palette
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.toBitmap
import com.convx.music.LocalDatabase
import com.convx.music.LocalDownloadUtil
import com.convx.music.LocalListenTogetherManager
import com.convx.music.LocalPlayerConnection
import com.convx.music.LocalTabView
import com.convx.music.R
import com.convx.music.constants.AudioQuality
import com.convx.music.constants.AudioQualityKey
import com.convx.music.constants.AppTextColorKey
import com.convx.music.constants.CropAlbumArtKey
import com.convx.music.constants.DarkModeKey
import com.convx.music.constants.CompactPlayerInTabViewKey
import com.convx.music.constants.CompactPlayerMaxWidth
import com.convx.music.constants.HidePlayerThumbnailKey
import com.convx.music.constants.HideVolumeBarKey
import com.convx.music.constants.EnableLyricsThumbnailPlayPauseKey
import com.convx.music.constants.KeepScreenOn
import com.convx.music.constants.PlayerBackgroundStyle
import com.convx.music.constants.PlayerBackgroundStyleKey
import com.convx.music.constants.PlayerGradientAngleKey
import com.convx.music.constants.PlayerGradientStopsKey
import com.convx.music.ui.theme.decodeGradientStops
import com.convx.music.ui.theme.tiltedGradient
import com.convx.music.constants.PlayerStaticColorKey
import com.convx.music.constants.PlayerButtonsStyle
import com.convx.music.constants.OneTapFullscreenLyricsKey
import com.convx.music.constants.FullscreenLyricsCollapseTopKey
import com.convx.music.constants.PlayerButtonsStyleKey
import com.convx.music.constants.PlayerHorizontalPadding
import com.convx.music.constants.QueuePeekHeight
import com.convx.music.constants.ShowUpNextKey
import com.convx.music.constants.SliderStyle
import com.convx.music.constants.SliderStyleKey
import com.convx.music.constants.SwipeLyricsKey
import com.convx.music.constants.ThumbnailCornerRadius
import com.convx.music.constants.ThumbnailRoundedShape
import com.convx.music.constants.UseAppleMusicPlayerKey
import com.convx.music.constants.UseNewPlayerDesignKey
import com.convx.music.constants.ShowAudioQualityBadgeKey
import com.convx.music.db.entities.LyricsEntity
import com.convx.music.extensions.SwipeGesture
import com.convx.music.extensions.togglePlayPause
import com.convx.music.extensions.toggleRepeatMode
import com.convx.music.listentogether.RoomRole
import com.convx.music.models.MediaMetadata
import com.convx.music.playback.ExoDownloadService
import com.convx.music.vivimusic.getConnectedBluetoothDeviceName
import com.convx.music.vivimusic.isBuds
import com.convx.music.vivimusic.isSpeaker
import com.convx.music.vivimusic.AudioDeviceBottomSheet
import com.convx.music.ui.component.DjReadout
import com.convx.music.ui.component.BakedFootBlur
import com.convx.music.ui.component.BlurredBackdropImage
import com.convx.music.ui.component.BottomSheet
import com.convx.music.ui.component.PLAYER_LAYER_HANDOFF_PROGRESS
import com.convx.music.ui.component.BottomSheetState
import com.convx.music.ui.component.LocalBottomSheetPageState
import com.convx.music.ui.component.LocalMenuState
import com.convx.music.ui.component.Lyrics
import com.convx.music.ui.component.PlayerSliderTrack
import com.convx.music.ui.component.ResizableIconButton
import com.convx.music.ui.utils.resize
import com.convx.music.ui.utils.Motion
import com.convx.music.ui.player.customize.DiyDesignCanvas
import com.convx.music.ui.player.customize.DiyOrientation
import com.convx.music.ui.player.customize.DiyStickerLayer
import com.convx.music.ui.utils.additiveChrome
import com.convx.music.ui.player.customize.AnimatedPlayerGlyph
import com.convx.music.ui.player.customize.PlayerGlyph
import com.convx.music.ui.player.customize.PlayerIconSlot
import com.convx.music.ui.player.customize.rememberDiyLayout
import com.convx.music.ui.player.customize.AbsoluteFrameDiyStickers
import com.convx.music.ui.player.customize.rememberPlayerIcon
import com.convx.music.ui.component.GlassComponent
import com.convx.music.ui.component.LocalGlassEffectConfig
import com.convx.music.ui.component.PLAYER_BLUR_MULTIPLIER
import com.convx.music.ui.component.isGlassAllowed
import com.convx.music.ui.component.liquidGlass
import com.convx.music.ui.component.ScrollingWaveformSeekBar
import com.convx.music.ui.component.rememberPlaybackFraction
import com.convx.music.ui.component.WavySlider
import com.convx.music.ui.component.GlassCircleButton
import com.convx.music.ui.component.LocalAppBackdrop
import com.convx.music.ui.component.LocalBackdropLoopBucket
import com.convx.music.ui.component.backdrop.backdrops.backdropStaticRegion
import com.convx.music.ui.component.backdrop.backdrops.layerBackdrop
import com.convx.music.ui.component.backdrop.backdrops.rememberLayerBackdrop
import com.convx.music.ui.component.rememberBottomSheetState
import com.convx.music.ui.component.rememberHeroTint
import com.convx.music.ui.menu.OldPlayerMenu
import com.convx.music.ui.menu.PlayerMenu
import com.convx.music.ui.component.VolumeSlider
import com.convx.music.ui.screens.settings.DarkMode
import com.convx.music.ui.theme.AppleTokens
import com.convx.music.ui.theme.PlayerColorExtractor
import com.convx.music.ui.utils.ShowMediaInfo
import com.convx.music.ui.utils.ShowOffsetDialog
import com.convx.music.utils.makeTimeString
import com.convx.music.utils.rememberEnumPreference
import com.convx.music.utils.rememberPreference
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.max
import kotlin.math.roundToInt
import com.convx.music.ui.component.Icon as MIcon
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.DefaultDataSource
import android.view.TextureView
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.convx.music.applecanvas.AppleMusicCanvasProvider
import com.convx.music.canvas.CanvasArtwork
import com.convx.music.canvas.TidalCanvasProvider
import com.convx.music.constants.CanvasSource
import com.convx.music.constants.CanvasSourceKey
import com.convx.music.constants.CanvasThumbnailAnimationKey
import com.convx.music.constants.DataSaverEnabledKey
import com.convx.music.extensions.metadata
import com.convx.music.ui.player.CanvasArtworkPlaybackCache
import com.convx.music.ui.player.normalizeCanvasArtistName
import com.convx.music.ui.player.normalizeCanvasSongTitle
import com.convx.music.ui.theme.PlayerSliderColors
import com.convx.music.vivimusiccanvas.ViviMusicCanvasProvider
import com.convx.music.vivimusiccanvas.EchoMusicCanvasProvider
import java.util.Locale

/** Poll interval for VideoLoopClock and the bucket width glass surfaces cache
 *  a looping canvas video's blur/lens output at (see LocalBackdropLoopBucket). */
private const val VideoLoopBucketMs = 100L

/** Upper bound on waiting for the player sheet before opening the queue inside it. */
private const val PARENT_SHEET_SETTLE_TIMEOUT_MS = 1_000L

/** Strength of white content added onto the background under `additiveChrome`. Below full so
 *  the blend doesn't clip to plain white and wash out the background's colour. */
private const val AdditiveChromeContentAlpha = 0.45f

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BottomSheetPlayer(
    state: BottomSheetState,
    navController: NavController,
    modifier: Modifier = Modifier,
    pureBlack: Boolean,
    /** Editor-only: the DIY editor mounts this composable as its live canvas and draws its own
     *  sticker layer — off there so the saved layout isn't double-rendered underneath. */
    showDiyStickers: Boolean = true,
    /** Editor-only: previews the orientation the user is toggling, not the device's real one. */
    forcedOrientation: DiyOrientation? = null,
    /** Editor-only: mounted there a second time alongside the real instance, so window-level
     *  effects (status bar icon color, keep-screen-on) are suppressed to keep the two copies
     *  from fighting over that global state. */
    isPreviewOnly: Boolean = false,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val menuState = LocalMenuState.current
    val bottomSheetPageState = LocalBottomSheetPageState.current
    val playerConnection = LocalPlayerConnection.current ?: return

    // Alternate player style, ported from vivizzz007/vivi-music. Off by default.
    // Swapped in below as the sheet's collapsed/expanded content slots so it still goes
    // through the real drag/expand/collapse mechanics.
    val (useAppleMusicPlayer) = rememberPreference(UseAppleMusicPlayerKey, defaultValue = false)

    // Shared between the background slot (BackgroundVideoView polls into it) and the
    // control pills below — see VideoLoopClock's doc for why this is a plain clock.
    val videoLoopClock = rememberVideoLoopClock()

    val (useNewPlayerDesign, onUseNewPlayerDesignChange) = rememberPreference(
        UseNewPlayerDesignKey,
        defaultValue = false
    )
    val (showAudioQualityBadge) = rememberPreference(
        ShowAudioQualityBadgeKey,
        defaultValue = true
    )
    val (hidePlayerThumbnail, onHidePlayerThumbnailChange) = rememberPreference(HidePlayerThumbnailKey, false)
    val cropAlbumArt by rememberPreference(CropAlbumArtKey, false)
    val (compactPlayerInTabView) = rememberPreference(CompactPlayerInTabViewKey, false)
    // Mini player is unaffected — only the expanded content gets width-capped, and only
    // when the side rail is actually showing (tab view / wide screen).
    val playerContentMaxWidth = if (LocalTabView.current && compactPlayerInTabView) {
        CompactPlayerMaxWidth
    } else {
        Dp.Unspecified
    }
    val playerBackground by rememberEnumPreference(
        key = PlayerBackgroundStyleKey,
        defaultValue = PlayerBackgroundStyle.APPLE_MUSIC
    )
    val playerButtonsStyle by rememberEnumPreference(
        key = PlayerButtonsStyleKey,
        defaultValue = PlayerButtonsStyle.DEFAULT
    )

    val isSystemInDarkTheme = isSystemInDarkTheme()
    val darkTheme by rememberEnumPreference(DarkModeKey, defaultValue = DarkMode.AUTO)
    val useDarkTheme = remember(darkTheme, isSystemInDarkTheme) {
        if (darkTheme == DarkMode.AUTO) isSystemInDarkTheme else darkTheme == DarkMode.ON
    }

    val dataSaverEnabled by rememberPreference(DataSaverEnabledKey, false)
    val enableCanvasPref by rememberPreference(CanvasThumbnailAnimationKey, true)
    // Canvas videos are the app's single largest data consumer; DataSaver and
    // battery saver both get them disabled.
    val powerSaverActive = remember {
        context.getSystemService(android.os.PowerManager::class.java)?.isPowerSaveMode ?: false
    }
    val enableCanvas = if (dataSaverEnabled || powerSaverActive) false else enableCanvasPref
    val (canvasSource) = rememberEnumPreference(CanvasSourceKey, defaultValue = CanvasSource.AUTO)

    val shouldUseDarkButtonColors = remember(playerBackground, useDarkTheme) {
        when (playerBackground) {
            PlayerBackgroundStyle.BLUR, PlayerBackgroundStyle.GRADIENT, PlayerBackgroundStyle.GLOW_ANIMATED, PlayerBackgroundStyle.APPLE_MUSIC, PlayerBackgroundStyle.LIVE_MESH, PlayerBackgroundStyle.BITCHORD_MESH, PlayerBackgroundStyle.STATIC, PlayerBackgroundStyle.CUSTOM_GRADIENT, PlayerBackgroundStyle.GLASS -> true
            PlayerBackgroundStyle.DEFAULT -> useDarkTheme
        }
    }

    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val swipeLyrics by rememberPreference(SwipeLyricsKey, false)
    val enableLyricsThumbnailPlayPause by rememberPreference(EnableLyricsThumbnailPlayPauseKey, false)
    val isKeepScreenOn by rememberPreference(KeepScreenOn, false)
    val keepScreenOn = isPlaying && isKeepScreenOn

    DisposableEffect(playerBackground, state.isExpanded, useDarkTheme, keepScreenOn, isPreviewOnly) {
        // The editor mounts a second copy; without this guard both would fight over
        // the same real window's status bar icon color / keep-screen-on flag.
        if (isPreviewOnly) return@DisposableEffect onDispose {}
        val window = (context as? android.app.Activity)?.window
        if (window != null && state.isExpanded) {
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            
            when (playerBackground) {
                PlayerBackgroundStyle.BLUR, PlayerBackgroundStyle.GRADIENT, PlayerBackgroundStyle.GLOW_ANIMATED, PlayerBackgroundStyle.APPLE_MUSIC, PlayerBackgroundStyle.LIVE_MESH, PlayerBackgroundStyle.BITCHORD_MESH, PlayerBackgroundStyle.STATIC, PlayerBackgroundStyle.CUSTOM_GRADIENT, PlayerBackgroundStyle.GLASS -> {
                    insetsController.isAppearanceLightStatusBars = false
                }
                PlayerBackgroundStyle.DEFAULT -> {
                    insetsController.isAppearanceLightStatusBars = !useDarkTheme
                }
            }

            if (keepScreenOn && state.isExpanded)
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        
        onDispose {
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.isAppearanceLightStatusBars = !useDarkTheme
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }
    val staticColor by rememberPreference(PlayerStaticColorKey, defaultValue = 0xFF1A1A1A.toInt())
    val gradientStopsRaw by rememberPreference(PlayerGradientStopsKey, defaultValue = "")
    val gradientStops = remember(gradientStopsRaw) { decodeGradientStops(gradientStopsRaw) }
    val gradientAngle by rememberPreference(PlayerGradientAngleKey, defaultValue = 90f)

    val onBackgroundColor = when (playerBackground) {
        PlayerBackgroundStyle.DEFAULT -> AppTheme.colorScheme.secondary
        else -> AppTheme.colorScheme.onSurface
    }
    val useBlackBackground =
        remember(isSystemInDarkTheme, darkTheme, pureBlack) {
            val useDarkTheme =
                if (darkTheme == DarkMode.AUTO) isSystemInDarkTheme else darkTheme == DarkMode.ON
            useDarkTheme && pureBlack
        }

    val playbackState by playerConnection.playbackState.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val currentSong by playerConnection.currentSong.collectAsStateWithLifecycle(initialValue =null)
    val automix by playerConnection.service.automixItems.collectAsStateWithLifecycle()
    val repeatMode by playerConnection.repeatMode.collectAsStateWithLifecycle()
    val canSkipPrevious by playerConnection.canSkipPrevious.collectAsStateWithLifecycle()
    val canSkipNext by playerConnection.canSkipNext.collectAsStateWithLifecycle()
    val isMuted by playerConnection.isMuted.collectAsStateWithLifecycle()
    val playerVolume by playerConnection.service.playerVolume.collectAsStateWithLifecycle()

    val (audioQuality) = rememberEnumPreference(
        AudioQualityKey,
        defaultValue = AudioQuality.AUTO
    )
    val currentFormat by playerConnection.currentFormat.collectAsStateWithLifecycle(initialValue =null)
    
    // Crossfade swaps playerConnection.player to a different ExoPlayer instance
    // mid-session — collecting the underlying flow (rather than reading
    // playerConnection.player once) is what makes this re-subscribe the
    // listener to the new instance instead of silently tracking the old,
    // fading-out one forever.
    val activePlayer by playerConnection.service.playerFlow.collectAsStateWithLifecycle(
        initialValue = playerConnection.player
    )
    val playerFormat by produceState(initialValue = activePlayer?.audioFormat, activePlayer) {
        val target = activePlayer ?: return@produceState
        value = target.audioFormat
        val listener = object : Player.Listener {
            override fun onEvents(player: androidx.media3.common.Player, events: androidx.media3.common.Player.Events) {
                // A lossless-fallback retry (and other same-item error retries)
                // re-prepare the SAME media item in place via seekTo()+prepare()
                // rather than transitioning to a new one — ExoPlayer doesn't
                // reliably fire EVENT_TRACKS_CHANGED for that even though the
                // actual resolved format changed underneath (FLAC -> AAC/Opus),
                // so the badge kept showing the old format while the DB-backed
                // song-info menu (which re-reads fresh on every resolve) already
                // had the new one. EVENT_POSITION_DISCONTINUITY reliably fires
                // for that seekTo(), giving a second, more certain trigger to
                // re-read the live format.
                if (events.contains(androidx.media3.common.Player.EVENT_TRACKS_CHANGED) ||
                    events.contains(androidx.media3.common.Player.EVENT_POSITION_DISCONTINUITY)
                ) {
                    value = (player as? ExoPlayer)?.audioFormat
                }
            }
        }
        target.addListener(listener)
        awaitDispose {
            target.removeListener(listener)
        }
    }

    val isLosslessStream = currentFormat?.mimeType?.contains("flac", ignoreCase = true) == true || 
                          playerFormat?.sampleMimeType?.contains("flac", ignoreCase = true) == true
    val sliderStyle by rememberEnumPreference(SliderStyleKey, SliderStyle.SLIM)

    // Listen Together state (reactive)
    val listenTogetherManager = LocalListenTogetherManager.current
    val listenTogetherRoleState = listenTogetherManager?.role?.collectAsStateWithLifecycle(initialValue =RoomRole.NONE)
    // "Guest" here means "may not drive playback", not merely "is not host":
    // with the room set to everyone-control a member drives playback too, so
    // keying the UI off the role alone left their controls greyed out.
    val canControlTogether = listenTogetherManager?.canControl?.collectAsStateWithLifecycle(initialValue = true)
    val isListenTogetherGuest = canControlTogether?.value == false
    
    // Cast state - safely access castConnectionHandler to prevent crashes during service lifecycle changes
    val castHandler = remember(playerConnection) {
        try {
            playerConnection.service.castConnectionHandler
        } catch (e: Exception) {
            null
        }
    }
    val isCasting by castHandler?.isCasting?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(false) }
    val castPosition by castHandler?.castPosition?.collectAsStateWithLifecycle() ?: remember { mutableLongStateOf(0L) }
    val castDuration by castHandler?.castDuration?.collectAsStateWithLifecycle() ?: remember { mutableLongStateOf(0L) }
    val castIsPlaying by castHandler?.castIsPlaying?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(false) }
    val castVolume by castHandler?.castVolume?.collectAsStateWithLifecycle() ?: remember { mutableFloatStateOf(1f) }
    
    // Use Cast state when casting, otherwise local player
    val effectiveIsPlaying = if (isCasting) castIsPlaying else isPlaying

    // Use State objects for position/duration to pass to MiniPlayer without causing recomposition
    // These states persist across playback state changes to ensure continuous progress updates
    val positionState = remember { mutableLongStateOf(0L) }
    val durationState = remember { mutableLongStateOf(0L) }
    
    // Convenience accessors for local use
    var position by positionState
    var duration by durationState
    
    val effectivePosition by remember {
        derivedStateOf {
            if (isCasting) {
                castPosition
            } else {
                position
            }
        }
    }
    
    var sliderPosition by remember {
        mutableStateOf<Long?>(null)
    }
    // Track when we last manually set position to avoid Cast overwriting it
    var lastManualSeekTime by remember { mutableLongStateOf(0L) }
    
    var gradientColors by remember {
        mutableStateOf<List<Color>>(emptyList())
    }
    val gradientColorsCache = remember { mutableMapOf<String, List<Color>>() }

    // Keep the queue topped up from automix without mutating the player mid-composition.
    LaunchedEffect(canSkipNext, automix) {
        if (!canSkipNext && automix.isNotEmpty()) {
            playerConnection.service.addToQueueAutomix(automix[0], 0)
        }
    }

    val bluetoothDeviceName by produceState<String?>(initialValue = getConnectedBluetoothDeviceName(context)) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                value = getConnectedBluetoothDeviceName(context)
            }
        }

        val callback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            object : android.media.AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<out android.media.AudioDeviceInfo>?) {
                    value = getConnectedBluetoothDeviceName(context)
                }
                override fun onAudioDevicesRemoved(removedDevices: Array<out android.media.AudioDeviceInfo>?) {
                    value = getConnectedBluetoothDeviceName(context)
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
        
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && callback != null) {
            audioManager.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        }
        
        awaitDispose {
            context.unregisterReceiver(receiver)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && callback != null) {
                audioManager.unregisterAudioDeviceCallback(callback)
            }
        }
    }

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxSystemVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat() }
    // Seeded once and then refreshed ONLY by VOLUME_CHANGED_ACTION, this used to drift:
    // that broadcast is undocumented and is not reliably delivered while the app is
    // backgrounded, so changing the volume with the hardware keys and coming back left
    // the slider showing whatever it last saw — 23% while the system was really at 54%.
    // Re-reading on every resume means the slider can't sit on a stale number, whether or
    // not the broadcast arrives.
    val lifecycleOwner = LocalLifecycleOwner.current
    var systemVolume by remember {
        mutableFloatStateOf(
            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxSystemVolume
        )
    }
    DisposableEffect(lifecycleOwner, maxSystemVolume) {
        fun refresh() {
            systemVolume =
                audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxSystemVolume
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == "android.media.VOLUME_CHANGED_ACTION") refresh()
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter("android.media.VOLUME_CHANGED_ACTION"),
            ContextCompat.RECEIVER_EXPORTED,
        )
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        refresh()
        onDispose {
            context.unregisterReceiver(receiver)
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val defaultGradientColors = listOf(AppTheme.colorScheme.surface, AppTheme.colorScheme.surfaceVariant)
    val fallbackColor = AppTheme.colorScheme.surface.toArgb()

    LaunchedEffect(mediaMetadata?.id, playerBackground) {
        if (playerBackground == PlayerBackgroundStyle.GRADIENT || playerBackground == PlayerBackgroundStyle.GLOW_ANIMATED) {
            val currentMetadata = mediaMetadata
            if (currentMetadata != null && currentMetadata.thumbnailUrl != null) {
                val cachedColors = gradientColorsCache[currentMetadata.id]
                if (cachedColors != null) {
                    gradientColors = cachedColors
                    return@LaunchedEffect
                }
                withContext(Dispatchers.IO) {
                    val request = ImageRequest.Builder(context)
                        .data(currentMetadata.thumbnailUrl)
                        .size(200, 200)
                        .allowHardware(false)
                        .memoryCacheKey("gradient_${currentMetadata.id}")
                        .build()

                    val result = runCatching { context.imageLoader.execute(request) }.getOrNull()
                    if (result != null) {
                        val bitmap = result.image?.toBitmap()
                        if (bitmap != null) {
                            val palette = withContext(Dispatchers.Default) {
                                Palette.from(bitmap)
                                    .maximumColorCount(8)
                                    .resizeBitmapArea(100 * 100)
                                    .generate()
                            }
                            val extractedColors = if (playerBackground == PlayerBackgroundStyle.GLOW_ANIMATED) {
                                listOfNotNull(
                                    palette.getVibrantColor(fallbackColor).let { Color(it) },
                                    palette.getLightVibrantColor(fallbackColor).let { Color(it) },
                                    palette.getDarkVibrantColor(fallbackColor).let { Color(it) },
                                    palette.getMutedColor(fallbackColor).let { Color(it) },
                                    palette.getLightMutedColor(fallbackColor).let { Color(it) },
                                    palette.getDarkMutedColor(fallbackColor).let { Color(it) }
                                ).distinct()
                            } else {
                                PlayerColorExtractor.extractGradientColors(
                                    palette = palette,
                                    fallbackColor = fallbackColor
                                )
                            }
                            gradientColorsCache[currentMetadata.id] = extractedColors
                            withContext(Dispatchers.Main) { gradientColors = extractedColors }
                        }
                    }
                }
            }
        } else {
            gradientColors = emptyList()
        }
    }

    val (showUpNext) = rememberPreference(ShowUpNextKey, defaultValue = false)
    // User's global button/text color choice (AppTextColorKey) — DEFAULT alone
    // follows the per-background/theme pick below; any custom color wins outright.
    val (appTextColorInt) = rememberPreference(AppTextColorKey, defaultValue = 0)
    val TextBackgroundColor by animateColorAsState(
        targetValue = if (appTextColorInt != 0) Color(appTextColorInt) else when (playerBackground) {
            PlayerBackgroundStyle.DEFAULT -> AppTheme.colorScheme.onBackground
            PlayerBackgroundStyle.BLUR -> Color.White
            PlayerBackgroundStyle.GRADIENT -> Color.White
            PlayerBackgroundStyle.GLOW_ANIMATED -> Color.White
            PlayerBackgroundStyle.APPLE_MUSIC -> Color.White
            PlayerBackgroundStyle.LIVE_MESH -> Color.White
            PlayerBackgroundStyle.BITCHORD_MESH -> Color.White
            PlayerBackgroundStyle.GLASS -> Color.White
            PlayerBackgroundStyle.STATIC, PlayerBackgroundStyle.CUSTOM_GRADIENT -> Color.White
        },
        label = "TextBackgroundColor"
    )

    val icBackgroundColor by animateColorAsState(
        targetValue = when (playerBackground) {
            PlayerBackgroundStyle.DEFAULT -> AppTheme.colorScheme.surface
            PlayerBackgroundStyle.BLUR -> Color.Black
            PlayerBackgroundStyle.GRADIENT -> Color.Black
            PlayerBackgroundStyle.GLOW_ANIMATED -> Color.Black
            PlayerBackgroundStyle.APPLE_MUSIC -> Color.Black
            PlayerBackgroundStyle.LIVE_MESH -> Color.Black
            PlayerBackgroundStyle.BITCHORD_MESH -> Color.Black
            PlayerBackgroundStyle.GLASS -> Color.Black
            PlayerBackgroundStyle.STATIC, PlayerBackgroundStyle.CUSTOM_GRADIENT -> Color.Black
        },
        label = "icBackgroundColor"
    )

    // All valid matches in priority order, not just the first one — a source
    // can return a real match whose video then fails to actually play (e.g.
    // dead CDN), and unlike a fetch failure that's only discovered later, at
    // playback time. canvasCandidateIndex lets BackgroundVideoView's onError
    // advance to the next candidate instead of the screen just going blank.
    var canvasCandidates by remember(mediaMetadata?.id) { mutableStateOf<List<CanvasArtwork>>(emptyList()) }
    var canvasCandidateIndex by remember(mediaMetadata?.id) { mutableIntStateOf(0) }
    var canvasFetchInFlight by remember(mediaMetadata?.id) { mutableStateOf(false) }
    val canvasArtwork = canvasCandidates.getOrNull(canvasCandidateIndex)

    LaunchedEffect(mediaMetadata?.id, playerBackground, canvasSource) {
        if (playerBackground != PlayerBackgroundStyle.APPLE_MUSIC || !enableCanvas) {
            canvasCandidates = emptyList()
            return@LaunchedEffect
        }
        val item = mediaMetadata ?: return@LaunchedEffect

        // Use cached artwork if available — it already proved it plays.
        CanvasArtworkPlaybackCache.get("${item.id}:${canvasSource.name}")?.let { cached ->
            canvasCandidates = listOf(cached)
            canvasCandidateIndex = 0
            return@LaunchedEffect
        }

        if (canvasFetchInFlight) return@LaunchedEffect
        canvasFetchInFlight = true

        withContext(Dispatchers.IO) {
            val storefront = Locale.getDefault().country.lowercase(Locale.ROOT).takeIf { it.length == 2 } ?: "us"
            val requestedTitle = item.title
            val requestedArtist = item.artists.joinToString { it.name }
            val requestedAlbum = item.album?.title ?: ""

            val s = normalizeCanvasSongTitle(requestedTitle)
            val a = normalizeCanvasArtistName(requestedArtist)

            val candidates = when (canvasSource) {
                CanvasSource.AUTO -> {
                    val echo = EchoMusicCanvasProvider.getBySongArtist(s, a)
                        ?.takeIf { !it.preferredAnimationUrl.isNullOrBlank() }
                    val appleAlbum = if (requestedAlbum.isNotBlank()) {
                        AppleMusicCanvasProvider.getByAlbumArtist(
                            album = requestedAlbum,
                            artist = a,
                            storefront = storefront
                        )?.takeIf { !it.preferredAnimationUrl.isNullOrBlank() }
                    } else null
                    val appleSong = AppleMusicCanvasProvider.getBySongArtist(s, a, requestedAlbum, storefront)
                        ?.takeIf { !it.preferredAnimationUrl.isNullOrBlank() }
                    val vivi = ViviMusicCanvasProvider.getBySongArtist(s, a)
                        ?.takeIf { !it.preferredAnimationUrl.isNullOrBlank() }
                    val tidal = TidalCanvasProvider.getBySongArtist(s, a, requestedAlbum)
                        ?.takeIf { !it.preferredAnimationUrl.isNullOrBlank() }
                    listOfNotNull(echo, appleAlbum, appleSong, vivi, tidal)
                }
                CanvasSource.ECHO_MUSIC -> listOfNotNull(
                    EchoMusicCanvasProvider.getBySongArtist(s, a)
                        ?.takeIf { !it.preferredAnimationUrl.isNullOrBlank() }
                )
                CanvasSource.APPLE_MUSIC -> listOfNotNull(
                    AppleMusicCanvasProvider.getBySongArtist(s, a, requestedAlbum, storefront)
                        ?.takeIf { !it.preferredAnimationUrl.isNullOrBlank() }
                )
                CanvasSource.VIVIMUSIC -> listOfNotNull(
                    ViviMusicCanvasProvider.getBySongArtist(s, a)
                        ?.takeIf { !it.preferredAnimationUrl.isNullOrBlank() }
                )
                CanvasSource.TIDAL -> listOfNotNull(
                    TidalCanvasProvider.getBySongArtist(s, a, requestedAlbum)
                        ?.takeIf { !it.preferredAnimationUrl.isNullOrBlank() }
                )
            }

            val validated = candidates.filter { artwork ->
                val resultArtist = artwork.artist
                if (resultArtist != null && requestedArtist.isNotBlank()) {
                    resultArtist.contains(requestedArtist, ignoreCase = true) ||
                    requestedArtist.contains(resultArtist, ignoreCase = true)
                } else true
            }

            withContext(Dispatchers.Main) {
                canvasCandidates = validated
                canvasCandidateIndex = 0
                canvasFetchInFlight = false
            }
        }
    }

    // Back to the original branching: artwork-ish backgrounds are always white/black,
    // DEFAULT/STATIC/CUSTOM_GRADIENT flip with the theme so DEFAULT-in-light-mode stays
    // legible instead of white-on-white.
    val (textButtonColor, iconButtonColor) = when {
        playerBackground == PlayerBackgroundStyle.BLUR ||
        playerBackground == PlayerBackgroundStyle.GRADIENT ||
        playerBackground == PlayerBackgroundStyle.GLOW_ANIMATED ||
        playerBackground == PlayerBackgroundStyle.APPLE_MUSIC ||
        playerBackground == PlayerBackgroundStyle.LIVE_MESH ||
        playerBackground == PlayerBackgroundStyle.BITCHORD_MESH ||
        playerBackground == PlayerBackgroundStyle.GLASS -> {
            when (playerButtonsStyle) {
                PlayerButtonsStyle.DEFAULT -> Pair(Color.White, Color.Black)
                PlayerButtonsStyle.PRIMARY -> Pair(
                    AppTheme.colorScheme.primary,
                    AppTheme.colorScheme.onPrimary
                )
                PlayerButtonsStyle.TERTIARY -> Pair(
                    AppTheme.colorScheme.tertiary,
                    AppTheme.colorScheme.onTertiary
                )
            }
        }
        else -> {
            when (playerButtonsStyle) {
                PlayerButtonsStyle.DEFAULT ->
                    if (useDarkTheme) Pair(Color.White, Color.Black)
                    else Pair(Color.Black, Color.White)
                PlayerButtonsStyle.PRIMARY -> Pair(
                    AppTheme.colorScheme.primary,
                    AppTheme.colorScheme.onPrimary
                )
                PlayerButtonsStyle.TERTIARY -> Pair(
                    AppTheme.colorScheme.tertiary,
                    AppTheme.colorScheme.onTertiary
                )
            }
        }
    }

    // Seek bar's loaded (active) color: the user's global color when set, else the
    // same textButtonColor every other player control uses. Every slider style's
    // inactive track is a faded version of this same color (see
    // PlayerSliderColors), so both halves of the seek bar read as one color, not two.
    val seekBarActiveColor = if (appTextColorInt != 0) Color(appTextColorInt) else textButtonColor
    // Back on, per user request: player chrome is composited additively onto the
    // background/mesh again.
    val additiveChrome = true

    // Separate colors for Previous/Next buttons in PRIMARY/TERTIARY modes -- back to the
    // original per-background split.
    val (sideButtonContainerColor, sideButtonContentColor) = when {
        playerBackground == PlayerBackgroundStyle.BLUR ||
        playerBackground == PlayerBackgroundStyle.GRADIENT ||
        playerBackground == PlayerBackgroundStyle.GLASS -> {
            when (playerButtonsStyle) {
                PlayerButtonsStyle.DEFAULT -> Pair(
                    Color.White.copy(alpha = 0.2f),
                    Color.White
                )
                PlayerButtonsStyle.PRIMARY -> Pair(
                    AppTheme.colorScheme.primaryContainer,
                    AppTheme.colorScheme.onPrimaryContainer
                )
                PlayerButtonsStyle.TERTIARY -> Pair(
                    AppTheme.colorScheme.tertiaryContainer,
                    AppTheme.colorScheme.onTertiaryContainer
                )
            }
        }
        playerBackground == PlayerBackgroundStyle.GLOW_ANIMATED -> {
            when (playerButtonsStyle) {
                PlayerButtonsStyle.DEFAULT -> Pair(
                    Color.White.copy(alpha = 0.2f),
                    Color.White
                )
                PlayerButtonsStyle.PRIMARY -> Pair(
                    AppTheme.colorScheme.primaryContainer,
                    AppTheme.colorScheme.onPrimaryContainer
                )
                PlayerButtonsStyle.TERTIARY -> Pair(
                    AppTheme.colorScheme.tertiaryContainer,
                    AppTheme.colorScheme.onTertiaryContainer
                )
            }
        }
        else -> {
            when (playerButtonsStyle) {
                PlayerButtonsStyle.DEFAULT -> Pair(
                    AppTheme.colorScheme.surfaceContainerHighest,
                    AppTheme.colorScheme.onSurface
                )
                PlayerButtonsStyle.PRIMARY -> Pair(
                    AppTheme.colorScheme.primaryContainer,
                    AppTheme.colorScheme.onPrimaryContainer
                )
                PlayerButtonsStyle.TERTIARY -> Pair(
                    AppTheme.colorScheme.tertiaryContainer,
                    AppTheme.colorScheme.onTertiaryContainer
                )
            }
        }
    }

    val download by LocalDownloadUtil.current.getDownload(mediaMetadata?.id ?: "")
        .collectAsStateWithLifecycle(initialValue =null)

    val sleepTimerEnabled =
        remember(
            playerConnection.service.sleepTimer.triggerTime,
            playerConnection.service.sleepTimer.pauseWhenSongEnd
        ) {
            playerConnection.service.sleepTimer.isActive
        }

    var sleepTimerTimeLeft by remember {
        mutableLongStateOf(0L)
    }

    LaunchedEffect(sleepTimerEnabled) {
        if (sleepTimerEnabled) {
            while (isActive) {
                sleepTimerTimeLeft =
                    if (playerConnection.service.sleepTimer.pauseWhenSongEnd) {
                        playerConnection.player.duration - playerConnection.player.currentPosition
                    } else {
                        playerConnection.service.sleepTimer.triggerTime - System.currentTimeMillis()
                    }
                delay(1000L)
            }
        }
    }

    var showSleepTimerDialog by remember {
        mutableStateOf(false)
    }

    var sleepTimerValue by remember {
        mutableFloatStateOf(30f)
    }
    if (showSleepTimerDialog) {
        AlertDialog(
            properties = DialogProperties(usePlatformDefaultWidth = false),
            onDismissRequest = { showSleepTimerDialog = false },
            icon = {
                Icon(
                    painter = painterResource(R.drawable.bedtime),
                    contentDescription = null
                )
            },
            title = { Text(stringResource(R.string.sleep_timer)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSleepTimerDialog = false
                        playerConnection.service.sleepTimer.start(sleepTimerValue.roundToInt())
                    },
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showSleepTimerDialog = false },
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = pluralStringResource(
                            R.plurals.minute,
                            sleepTimerValue.roundToInt(),
                            sleepTimerValue.roundToInt()
                        ),
                        style = AppTheme.typography.bodyLarge,
                    )

                    Slider(
                        value = sleepTimerValue,
                        onValueChange = { sleepTimerValue = it },
                        valueRange = 5f..120f,
                        steps = (120 - 5) / 5 - 1,
                    )

                    OutlinedIconButton(
                        onClick = {
                            showSleepTimerDialog = false
                            playerConnection.service.sleepTimer.start(-1)
                        },
                    ) {
                        Text(stringResource(R.string.end_of_song))
                    }
                }
            },
        )
    }

    var showChoosePlaylistDialog by rememberSaveable {
        mutableStateOf(false)
    }

    var showInlineLyrics by rememberSaveable {
        mutableStateOf(false)
    }

    var isFullScreen by rememberSaveable {
        mutableStateOf(false)
    }
    val (oneTapFullscreenLyrics) = rememberPreference(OneTapFullscreenLyricsKey, defaultValue = false)
    val (fullscreenLyricsCollapseTop) = rememberPreference(FullscreenLyricsCollapseTopKey, defaultValue = true)
    val (hideVolumeBar) = rememberPreference(HideVolumeBarKey, defaultValue = false)
    // Position update - only for local playback when player is visible
    // When casting, we use castPosition directly to avoid sync issues
    // Pauses polling when the full sheet is collapsed to prevent background recompositions
    LaunchedEffect(isPlaying, isCasting, state.isCollapsed) {
        if (!isCasting && isPlaying && !state.isCollapsed) {
            while (isActive) {
                delay(100) // Update for smooth progress bar while player is open
                if (sliderPosition == null) { // Only update if user isn't dragging
                    position = playerConnection.player.currentPosition
                    duration = playerConnection.player.duration
                }
            }
        }
    }
    
    // Also update position when playback state changes (e.g., song change, seek)
    LaunchedEffect(playbackState, mediaMetadata?.id) {
        if (!isCasting) {
            position = playerConnection.player.currentPosition
            duration = playerConnection.player.duration
        }
    }
    
    // When casting, use Cast position/duration directly
    // But wait a bit after manual seeks to let Cast catch up
    LaunchedEffect(isCasting, castPosition, castDuration) {
        if (isCasting && sliderPosition == null) {
            val timeSinceManualSeek = System.currentTimeMillis() - lastManualSeekTime
            if (timeSinceManualSeek > 1500) {
                // Only update from Cast if we haven't manually seeked recently
                position = castPosition
                if (castDuration > 0) duration = castDuration
            }
        }
    }

    val dismissedBound = QueuePeekHeight + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()

    val queueSheetState = rememberBottomSheetState(
        dismissedBound = dismissedBound,
        expandedBound = state.expandedBound,
        collapsedBound = dismissedBound + 1.dp,
        initialAnchor = 1
    )

    // The floating mini bar's lyrics/queue icons live outside this composable
    // (FloatingSideBar/FloatingNavBar), so they signal here through
    // PlayerConnection instead of calling local state directly.
    LaunchedEffect(Unit) {
        playerConnection.requestShowLyrics.collect { requested ->
            if (requested) {
                showInlineLyrics = true
                isFullScreen = true
                playerConnection.requestShowLyrics.value = false
            }
        }
    }
    LaunchedEffect(Unit) {
        playerConnection.requestShowQueue.collect { requested ->
            if (requested) {
                showInlineLyrics = false
                // Queue's own visibility below is gated on !isFullScreen, same as
                // the lyrics path sets isFullScreen = true above. Without clearing
                // it here, a prior fullscreen session (isFullScreen is
                // rememberSaveable, so it survives the sheet collapsing back to
                // the mini pill) leaves this expandSoft() a dead click — the queue
                // sheet state expands but stays hidden behind the fullscreen gate.
                isFullScreen = false
                // Both callers (the floating nav bar's accessory and the now-playing
                // pill) start the PLAYER sheet expanding and set this flag in the same
                // frame, so this ran against a parent that was still animating — the
                // nested queue sheet expanded into bounds that were still moving and
                // settled straight back, which is the dead tap. Wait for the parent to
                // arrive, but never block forever: if it isn't going to expand (already
                // open, or the animation was interrupted by a drag), just go.
                if (!state.isExpanded) {
                    withTimeoutOrNull(PARENT_SHEET_SETTLE_TIMEOUT_MS) {
                        snapshotFlow { state.isExpanded }.first { it }
                    }
                }
                queueSheetState.expandSoft()
                playerConnection.requestShowQueue.value = false
            }
        }
    }

    val bottomSheetBackgroundColor = when (playerBackground) {
        PlayerBackgroundStyle.BLUR, PlayerBackgroundStyle.GRADIENT, PlayerBackgroundStyle.GLOW_ANIMATED, PlayerBackgroundStyle.APPLE_MUSIC, PlayerBackgroundStyle.GLASS ->
            AppTheme.colorScheme.surfaceContainer
        PlayerBackgroundStyle.LIVE_MESH, PlayerBackgroundStyle.BITCHORD_MESH ->
            Color.Black
        else ->
            if (useBlackBackground) Color.Black
            else AppTheme.colorScheme.surfaceContainer
    }

    // state.progress changes on every frame of a sheet drag or expand/collapse
    // animation. Reading it here — the top-level body of a ~3000-line composable
    // — recomposed the entire player once per frame. Every consumer below is a
    // graphicsLayer lambda, so hand them a getter and let the read happen in the
    // draw phase. BottomSheet.kt already does it this way.
    val backgroundAlpha = { state.progress.coerceIn(0f, 1f) }
    // The canvas-video branch gates composition, so it does need a composition
    // read — but a derived boolean only invalidates when it crosses the
    // threshold, not on every frame.
    val backgroundVisible by remember(state) { derivedStateOf { state.progress > 0.01f } }

    // Captures the player's own rendered background (blurred appBackdrop +
    // artwork/mesh/canvas layers below) so the heart/more circular buttons in
    // the foreground sample THAT, not the raw global appBackdrop directly —
    // otherwise they'd show whatever NavHost screen is behind the player
    // sheet (e.g. the Artist screen you opened it from) instead of the
    // player's own look. Safe: the background composable and the foreground
    // content are sibling slots of BottomSheet, not ancestor/descendant.
    val playerBackdrop = rememberLayerBackdrop()

    val diyLayout = rememberDiyLayout()
    // Shared by the sticker canvas below AND the thumbnail/controls layout branch
    // further down, so the editor's orientation toggle (forcedOrientation) previews
    // both consistently instead of stickers repositioning while the background
    // layout stays pinned to the device's real, physical orientation.
    val isLandscapeLayout = forcedOrientation?.let { it == DiyOrientation.LANDSCAPE }
        ?: (LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE)
    val diyOrientation = if (isLandscapeLayout) DiyOrientation.LANDSCAPE else DiyOrientation.PORTRAIT

    // Captured from a plain sibling Box in `overlayContent` below and read by the z < 0
    // sticker band (here and in Player_v2.kt) so both player skins resolve stickers
    // against the same frame -- see AbsoluteFrameDiyStickers' doc for why.
    var diyRootCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    // V17's own Cover/Lyrics/Queue state, reported up from PlayerV2 (its own internal
    // AnimatedContent state, invisible from here otherwise). The z >= 0 sticker band
    // used to live inside that same AnimatedContent's Cover branch, so switching to
    // Lyrics or Queue hid it as a side effect of the branch not being composed. Moving
    // it to overlayContent (this file's own sibling slot, shared with classic) severed
    // that: without this, V17's Queue/Lyrics buttons stopped hiding the stickers.
    var v17ShowingCover by remember { mutableStateOf(true) }

    // Publishes this sheet's progress to the shared container morph. Read only in the
    // draw phase (see PlayerMorph), so nothing recomposes per frame because of it.
    DisposableEffect(state) {
        PlayerMorph.progressProvider = { state.progress }
        PlayerMorph.onDragStart = { fromMini -> state.onDragStart(fromMini) }
        PlayerMorph.onDragDelta = { state.dispatchRawDelta(it) }
        PlayerMorph.onDragEnd = { state.performFling(it, null) }
        PlayerMorph.onDragCancel = { state.snapTo(state.collapsedBound) }
        onDispose {
            PlayerMorph.progressProvider = { 0f }
            PlayerMorph.onDragStart = {}
            PlayerMorph.onDragDelta = {}
            PlayerMorph.onDragEnd = {}
            PlayerMorph.onDragCancel = {}
        }
    }

    BottomSheet(
        state = state,
        modifier = modifier,
        // iOS 27 container morph: the player grows out of the mini player as one card.
        morphEnabled = PlayerMorph.enabled,
        // Resident content: opening from pause used to cold-compose the whole player
        // on its first frame (thumbnail recalc pop, controls fading in after the
        // morph). Once opened it stays composed, measured-but-unplaced while
        // collapsed: animation-time state is already there when the morph starts.
        keepContentComposed = true,
        // The DIY editor mounts this same BottomSheetPlayer as a forced-open preview
        // (editorPlayerState) sitting behind its own sticker-editing overlay. Without
        // this, a drag that isn't fully consumed by the overlay (e.g. panning empty
        // canvas with nothing selected) reaches this sheet's own swipe-to-collapse
        // detector -- which read as the whole preview flickering/collapsing, and at
        // dismissedBound could trigger onDismiss below, which stops playback and
        // clears the real queue. The editor's own forced initialAnchor is the only
        // state this preview should ever be in.
        isExpandable = !isPreviewOnly,
        contentMaxWidth = playerContentMaxWidth,
        // The sheet rounds to the device's real screen radius as it reaches full size, so
        // its corners sit on the glass rather than near it, then opens out to square once
        // settled. 0.dp on a device that reports no rounded corners, or in split screen.
        expandedCornerRadius = rememberScreenCornerRadius(),
        // Mini-to-full morph flight (see PlayerMorphHost): container owns the
        // card, artwork rides above it.
        overlayContent = {
            PlayerMorphHost(
                progress = { state.progress },
                thumbnailUrl = mediaMetadata?.thumbnailUrl,
                containerColor = AppTheme.colorScheme.surfaceContainer,
                expandedCornerRadius = rememberScreenCornerRadius(),
            )

            // A plain, unshifted sibling of the sheet's own draggable content (see this
            // param's own doc above) -- the one frame both classic and V17 (Player_v2.kt)
            // can anchor the z < 0 sticker band to regardless of which skin's own chrome
            // is active. Captured here, read in this file's z < 0 call site and threaded
            // into PlayerV2 for its own.
            Box(modifier = Modifier.fillMaxSize().onGloballyPositioned { diyRootCoordinates = it })

            // The z >= 0 sticker band: everything drawn over the artwork and the
            // transport controls. This slot is already the topmost sibling of the
            // sheet's content, on top of whichever skin is active, so it needs no
            // frame-matching trick the way the z < 0 band (still nested inside each
            // skin's own content, to sit behind its artwork) does.
            //
            // ponytail: only two z bands, not three -- everything here draws over the
            // transport controls too, so a sticker parked on the play button hides it
            // (the button still responds). A real "above artwork, below controls" band
            // needs each skin's controlsContent hoisted into its own overlay slot.
            //
            // Hidden on Lyrics/Queue -- classic's own showInlineLyrics, or V17's
            // reported-up v17ShowingCover, whichever skin is active. Wrapped in
            // AnimatedVisibility (Motion.appear(), the app's "arriving/leaving in
            // place" spring) so that switch crossfades instead of cutting -- it used
            // to ride for free on V17's own AnimatedContent before this band moved
            // here to share a frame with classic; now it needs its own transition.
            val stickersHidden = if (useAppleMusicPlayer) !v17ShowingCover else showInlineLyrics
            AnimatedVisibility(
                visible = showDiyStickers && !stickersHidden && !state.isCollapsed,
                enter = fadeIn(Motion.appear()),
                exit = fadeOut(Motion.appear()),
            ) {
                val revealAlpha = ((state.progress - PLAYER_LAYER_HANDOFF_PROGRESS) / 0.2f).coerceIn(0f, 1f)
                DiyDesignCanvas(
                    orientation = diyOrientation,
                    modifier = Modifier
                        .fillMaxSize()
                        // Fades out with the queue's own drag instead of vanishing the
                        // instant the sheet reports itself expanded, same reason as the
                        // original call site's comment (see git history).
                        .graphicsLayer {
                            alpha = revealAlpha * (1f - queueSheetState.progress.coerceIn(0f, 1f))
                        },
                ) {
                    DiyStickerLayer(layout = diyLayout, orientation = diyOrientation, zFilter = { it >= 0 })
                }
            }
        },
        background = {
            val glassConfig = LocalGlassEffectConfig.current
            // Glass is its own style now, not a base under every style: only GLASS
            // pays the full-screen capture + blur chain. All other styles render
            // their own layers over the solid fallback below.
            val glassActive = playerBackground == PlayerBackgroundStyle.GLASS && isGlassAllowed()
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // OUTER layer, before layerBackdrop: without it every sibling tick
                    // in the sheet (progress, waveform, the position poll) re-records
                    // this whole background subtree instead of just redrawing itself.
                    // Pairs with the inner graphicsLayer the same way MainActivity's
                    // app backdrop does.
                    .graphicsLayer()
                    .layerBackdrop(playerBackdrop, frozen = state.backdropFrozen)
                    .graphicsLayer()
                    .then(
                        if (glassActive) {
                            // GLASS style only: samples appBackdrop (root content)
                            // behind the sheet. Higher blur than pills to feel
                            // like heavy material.
                            Modifier.liquidGlass(
                                config = glassConfig,
                                applyEdgeEffects = false,
                                blurRadiusDp = (glassConfig.blurRadius * PLAYER_BLUR_MULTIPLIER)
                                    .coerceAtMost(100f),
                            )
                        } else {
                            Modifier.background(bottomSheetBackgroundColor)
                        }
                    )
            ) {
                // Secondary dynamic layers (Artwork Blur, Mesh, Canvas) render ON TOP
                // of the base fill — except GLASS, whose liquid-glass base above
                // IS the look, so it adds nothing here.
                when (playerBackground) {
                    PlayerBackgroundStyle.GLASS -> {}
                    PlayerBackgroundStyle.BLUR -> {
                        AnimatedContent(
                            targetState = mediaMetadata?.thumbnailUrl,
                            transitionSpec = {
                                fadeIn(tween(800)).togetherWith(fadeOut(tween(800)))
                            },
                            label = "blurBackground"
                        ) { thumbnailUrl ->
                            if (thumbnailUrl != null) {
                                // The blurred artwork is provably static between track
                                // changes, but it lives inside the playerBackdrop source
                                // subtree — without a static declaration, EVERY seekbar
                                // position poll (100ms) or badge tick bumps the backdrop
                                // version and re-captures + re-blurs the full screen for
                                // each glass pill sampling this backdrop. Declared static,
                                // pill captures whose rects fall inside this region reuse
                                // their last capture; the only cost is that pill frost
                                // lags the 800ms track-change crossfade by up to that
                                // fade's duration (imperceptible tint shift).
                                Box(
                                    modifier = Modifier
                                        .graphicsLayer { alpha = backgroundAlpha() }
                                        .backdropStaticRegion(playerBackdrop)
                                ) {
                                    // Baked blur: the 160px source is upscaled ~7x to fill
                                    // the screen, so a gaussian baked into the decode is
                                    // visually identical to Modifier.blur(100-150dp) on the
                                    // full-size image — but runs once per track on decode
                                    // (cached) instead of a multi-ms RenderEffect pass on
                                    // every redraw. StackBlur works on any API level, so
                                    // the old below-S solid-black fallback goes away too.
                                    BlurredBackdropImage(
                                        model = thumbnailUrl,
                                        screenBlurRadiusDp = if (useDarkTheme) 150f else 100f,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.3f))
                                    )
                                }
                            }
                        }
                    }
                    PlayerBackgroundStyle.GRADIENT -> {
                        AnimatedContent(
                            targetState = gradientColors,
                            transitionSpec = {
                                fadeIn(tween(800)).togetherWith(fadeOut(tween(800)))
                            },
                            label = "gradientBackground"
                        ) { colors ->
                            if (colors.isNotEmpty()) {
                                val gradientColorStops = if (colors.size >= 3) {
                                    arrayOf(
                                        0.0f to colors[0],
                                        0.5f to colors[1],
                                        1.0f to colors[2]
                                    )
                                } else {
                                    arrayOf(
                                        0.0f to colors[0],
                                        0.6f to colors[0].copy(alpha = 0.7f),
                                        1.0f to Color.Black
                                    )
                                }
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .graphicsLayer { alpha = backgroundAlpha() }
                                        .backdropStaticRegion(playerBackdrop)
                                        .background(Brush.verticalGradient(colorStops = gradientColorStops))
                                        .background(Color.Black.copy(alpha = 0.2f))
                                )
                            }
                        }
                    }
                    PlayerBackgroundStyle.GLOW_ANIMATED -> {
                        AnimatedContent(
                            targetState = gradientColors,
                            transitionSpec = {
                                fadeIn(tween(1200)) togetherWith fadeOut(tween(1200))
                            },
                            label = "GlowAnimatedContent"
                        ) { colors ->
                            if (colors.isNotEmpty()) {
                                val progress: androidx.compose.runtime.State<Float> = if (isPlaying && !state.isCollapsed) {
                                    val infiniteTransition =
                                        rememberInfiniteTransition(label = "GlowAnimation")

                                    infiniteTransition.animateFloat(
                                        initialValue = 0f,
                                        targetValue = 1f,
                                        animationSpec = infiniteRepeatable(
                                            animation = tween(20000, easing = LinearEasing),
                                            repeatMode = RepeatMode.Restart
                                        ),
                                        label = "glowProgress"
                                    )
                                } else {
                                    remember { mutableFloatStateOf(0f) }
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer { alpha = backgroundAlpha() }
                                        .drawBehind {
                                            val p = progress.value
                                            val width = size.width
                                            val height = size.height
                                            val baseColor = Color(0xFF050505)

                                            fun rotatedColorAt(index: Int): Color {
                                                val size = colors.size
                                                val idx = index.toFloat() + p * size
                                                val a = kotlin.math.floor(idx).toInt() % size
                                                val b = (a + 1) % size
                                                val frac = idx - kotlin.math.floor(idx)
                                                return androidx.compose.ui.graphics.lerp(
                                                    colors.getOrElse(a) { Color.DarkGray },
                                                    colors.getOrElse(b) { Color.DarkGray },
                                                    frac
                                                )
                                            }

                                            fun oscillate(
                                                min: Float,
                                                max: Float,
                                                phase: Float,
                                                speed: Float = 1f
                                            ): Float {
                                                val v = kotlin.math.sin(
                                                    2f * kotlin.math.PI.toFloat() * (p * speed + phase)
                                                )
                                                return min + (max - min) * ((v + 1f) * 0.5f)
                                            }

                                            val color1 = rotatedColorAt(0)
                                            val color2 = rotatedColorAt(1)
                                            val color3 = rotatedColorAt(2)
                                            val color4 = rotatedColorAt(3)
                                            val color5 = rotatedColorAt(4)
                                            val color6 = rotatedColorAt(5)

                                            val o1x = oscillate(0.0f, 1.0f, 0.00f, 1.0f)
                                            val o1y = oscillate(0.0f, 0.5f, 0.07f, 1.0f)
                                            val r1 = oscillate(0.8f, 1.6f, 0.12f, 1.0f)

                                            val o2x = oscillate(1.0f, 0.0f, 0.2f, 1.0f)
                                            val o2y = oscillate(0.5f, 1.0f, 0.25f, 1.0f)
                                            val r2 = oscillate(0.7f, 1.5f, 0.18f, 1.0f)

                                            val o3x = oscillate(0.2f, 0.8f, 0.33f, 1.0f)
                                            val o3y = oscillate(0.8f, 0.2f, 0.36f, 1.0f)
                                            val r3 = oscillate(0.6f, 1.4f, 0.29f, 1.0f)

                                            val o4x = oscillate(0.3f, 0.7f, 0.44f, 1.0f)
                                            val o4y = oscillate(0.2f, 0.8f, 0.41f, 1.0f)
                                            val r4 = oscillate(0.9f, 1.7f, 0.47f, 1.0f)

                                            val o5x = oscillate(0.4f, 0.6f, 0.55f, 1.0f)
                                            val o5y = oscillate(0.0f, 1.0f, 0.51f, 1.0f)
                                            val r5 = oscillate(0.7f, 1.5f, 0.58f, 1.0f)

                                            val o6x = oscillate(0.0f, 1.0f, 0.66f, 1.0f)
                                            val o6y = oscillate(0.5f, 0.7f, 0.62f, 1.0f)
                                            val r6 = oscillate(0.8f, 1.8f, 0.69f, 1.0f)

                                            val brush1 = Brush.radialGradient(
                                                colors = listOf(
                                                    color1.copy(alpha = 0.85f),
                                                    color1.copy(alpha = 0.5f),
                                                    Color.Transparent
                                                ),
                                                center = Offset(width * o1x, height * o1y),
                                                radius = width * r1
                                            )
                                            val brush2 = Brush.radialGradient(
                                                colors = listOf(
                                                    color2.copy(alpha = 0.8f),
                                                    color2.copy(alpha = 0.45f),
                                                    Color.Transparent
                                                ),
                                                center = Offset(width * o2x, height * o2y),
                                                radius = width * r2
                                            )
                                            val brush3 = Brush.radialGradient(
                                                colors = listOf(
                                                    color3.copy(alpha = 0.75f),
                                                    color3.copy(alpha = 0.4f),
                                                    Color.Transparent
                                                ),
                                                center = Offset(width * o3x, height * o3y),
                                                radius = width * r3
                                            )
                                            val brush4 = Brush.radialGradient(
                                                colors = listOf(
                                                    color4.copy(alpha = 0.7f),
                                                    color4.copy(alpha = 0.35f),
                                                    Color.Transparent
                                                ),
                                                center = Offset(width * o4x, height * o4y),
                                                radius = width * r4
                                            )
                                            val brush5 = Brush.radialGradient(
                                                colors = listOf(
                                                    color5.copy(alpha = 0.65f),
                                                    color5.copy(alpha = 0.3f),
                                                    Color.Transparent
                                                ),
                                                center = Offset(width * o5x, height * o5y),
                                                radius = width * r5
                                            )
                                            val brush6 = Brush.radialGradient(
                                                colors = listOf(
                                                    color6.copy(alpha = 0.6f),
                                                    color6.copy(alpha = 0.25f),
                                                    Color.Transparent
                                                ),
                                                center = Offset(width * o6x, height * o6y),
                                                radius = width * r6
                                            )

                                            drawRect(color = baseColor)
                                            drawRect(brush = brush1)
                                            drawRect(brush = brush2)
                                            drawRect(brush = brush3)
                                            drawRect(brush = brush4)
                                            drawRect(brush = brush5)
                                            drawRect(brush = brush6)
                                        }
                                )
                            }
                        }
                    }
                    PlayerBackgroundStyle.APPLE_MUSIC -> {
                        AnimatedContent(
                            targetState = mediaMetadata?.thumbnailUrl,
                            transitionSpec = {
                                fadeIn(tween(1200)).togetherWith(fadeOut(tween(1200)))
                            },
                            label = "appleMusicBackground"
                        ) { thumbnailUrl ->
                            if (thumbnailUrl != null) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer { alpha = backgroundAlpha() }
                                        .backdropStaticRegion(playerBackdrop)
                                ) {
                                    // Layer 1: Full-Screen Blurred Background — baked still only. The
                                    // title below the clear box reads off this frost + the foot wash;
                                    // no extra scrim (artist-page treatment).
                                    BlurredBackdropImage(
                                        model = thumbnailUrl,
                                        screenBlurRadiusDp = 150f,
                                        decodeSize = 48,
                                        modifier = Modifier.fillMaxSize(),
                                    )

                                    // Layer 2: Clear Artwork (top 65%) with the shared foot knee 0.78:
                                    // opaque to 0.78, eased out by 1.0 — same seam as artist/V2 heroes.
                                    val clearArtworkAlpha by animateFloatAsState(
                                        targetValue = if (showInlineLyrics) 0f else 1f,
                                        animationSpec = tween(500),
                                        label = "clearArtworkAlpha"
                                    )

                                    // Identical hero frame to V2 HERO: full width, 65% of the
                                    // screen height, blend starts at 0.78 of the box in both.
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .fillMaxHeight(0.65f) // Occupies top 65%
                                            .graphicsLayer {
                                                alpha = clearArtworkAlpha
                                                compositingStrategy = CompositingStrategy.Offscreen
                                            }
                                            .drawWithContent {
                                                drawContent()
                                                drawRect(
                                                    brush = com.convx.music.ui.component.heroFadeOutBrush(),
                                                    blendMode = BlendMode.DstIn
                                                )
                                            }
                                    ) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data(thumbnailUrl.resize(1200, 1200))
                                                .size(CoilSize.ORIGINAL)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )

                                        // Baked progressive foot (knee 0.78, same bake as artist):
                                        // stills dissolve with real blur growth, not a sharp cut;
                                        // under video it is the poster until the first frame.
                                        BakedFootBlur(
                                            artworkUrl = thumbnailUrl,
                                            modifier = Modifier.fillMaxSize(),
                                        )

                                        if (enableCanvas && canvasArtwork != null && backgroundVisible) {
                                            BackgroundVideoView(
                                                mediaId = mediaMetadata?.id ?: "",
                                                videoUrl = canvasArtwork?.animated ?: canvasArtwork?.videoUrl ?: "",
                                                isPlaying = isPlaying,
                                                onError = {
                                                    println("CanvasFallback: onError fired at index=$canvasCandidateIndex of ${canvasCandidates.size} candidates")
                                                    if (canvasCandidateIndex < canvasCandidates.lastIndex) {
                                                        canvasCandidateIndex++
                                                    } else {
                                                        canvasCandidateIndex = canvasCandidates.size
                                                    }
                                                    println("CanvasFallback: advanced to index=$canvasCandidateIndex, next url=${canvasCandidates.getOrNull(canvasCandidateIndex)?.preferredAnimationUrl}")
                                                },
                                                onReady = {
                                                    canvasArtwork?.let {
                                                        CanvasArtworkPlaybackCache.put(
                                                            "${mediaMetadata?.id}:${canvasSource.name}",
                                                            it
                                                        )
                                                    }
                                                },
                                                modifier = Modifier.fillMaxSize(),
                                                loopClock = videoLoopClock,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    PlayerBackgroundStyle.LIVE_MESH -> {
                        val shouldAnimateLiveMesh = isPlaying && !state.isCollapsed
                        val anchorRotation: androidx.compose.runtime.State<Float>
                        val fastRotation: androidx.compose.runtime.State<Float>
                        val slowRotation: androidx.compose.runtime.State<Float>

                        if (shouldAnimateLiveMesh) {
                            val infiniteTransition = rememberInfiniteTransition(label = "liveMeshRotation")
                            
                            anchorRotation = infiniteTransition.animateFloat(
                                initialValue = 0f,
                                targetValue = -360f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(80000, easing = LinearEasing),
                                    repeatMode = RepeatMode.Restart
                                ),
                                label = "anchorRotation"
                            )
                            
                            fastRotation = infiniteTransition.animateFloat(
                                initialValue = 0f,
                                targetValue = 360f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(40000, easing = LinearEasing),
                                    repeatMode = RepeatMode.Restart
                                ),
                                label = "fastRotation"
                            )
                            
                            slowRotation = infiniteTransition.animateFloat(
                                initialValue = 0f,
                                targetValue = 360f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(60000, easing = LinearEasing),
                                    repeatMode = RepeatMode.Restart
                                ),
                                label = "slowRotation"
                            )
                        } else {
                            val zero = remember { mutableFloatStateOf(0f) }
                            anchorRotation = zero
                            fastRotation = zero
                            slowRotation = zero
                        }

                        AnimatedContent(
                            targetState = mediaMetadata?.thumbnailUrl,
                            transitionSpec = {
                                fadeIn(tween(1500)).togetherWith(fadeOut(tween(1500)))
                            },
                            label = "liveMeshBackground"
                        ) { thumbnailUrl ->
                            if (thumbnailUrl != null) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer { alpha = backgroundAlpha() }
                                        .graphicsLayer {
                                            // Scale up to avoid showing edges during rotation
                                            scaleX = 1.7f
                                            scaleY = 1.7f
                                        }
                                        // Single melt pass over the three baked-blur layers so the
                                        // rotating shapes read as mesh blobs. One pass on the
                                        // container, not per layer. No-op below API 31, where the
                                        // baked blur underneath still holds.
                                        .blur(32.dp)
                                ) {
                                    val matrix = remember { 
                                        val m = ColorMatrix()
                                        m.setToSaturation(1.8f) // Reduced to avoid neon look
                                        m
                                    }
                                    val colorFilter = remember(matrix) {
                                        ColorFilter.colorMatrix(matrix)
                                    }

                                    // All 3 layers bake the blur into the 48px decode
                                    // (see BLUR branch note) — the RenderEffect versions
                                    // re-blurred three full-screen layers on every frame
                                    // while rotating. Rotation stays as a plain
                                    // graphicsLayer transform on the baked bitmap.
                                    // Layer 1: The Anchor (Full Image, Counter-Clockwise)
                                    BlurredBackdropImage(
                                        model = thumbnailUrl,
                                        screenBlurRadiusDp = 100f,
                                        decodeSize = 160,
                                        colorFilter = colorFilter,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .graphicsLayer { rotationZ = anchorRotation.value },
                                    )

                                    // Layer 2: Fast Rotating Crop (Top-Left)
                                    BlurredBackdropImage(
                                        model = thumbnailUrl,
                                        screenBlurRadiusDp = 120f,
                                        decodeSize = 160,
                                        alignment = Alignment.TopStart,
                                        colorFilter = colorFilter,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .graphicsLayer {
                                                rotationZ = fastRotation.value
                                                alpha = 0.6f
                                            },
                                    )

                                    // Layer 3: Slow Rotating Crop (Bottom-Right)
                                    BlurredBackdropImage(
                                        model = thumbnailUrl,
                                        screenBlurRadiusDp = 120f,
                                        decodeSize = 160,
                                        alignment = Alignment.BottomEnd,
                                        colorFilter = colorFilter,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .graphicsLayer {
                                                rotationZ = slowRotation.value
                                                alpha = 0.5f
                                            },
                                    )

                                    // Global dark tint to prevent neon look + vertical gradient for depth
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.2f))
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                Brush.verticalGradient(
                                                    listOf(
                                                        Color.Transparent,
                                                        Color.Black.copy(alpha = 0.25f)
                                                    )
                                                )
                                            )
                                    )
                                }
                            }
                        }
                    }
                    PlayerBackgroundStyle.BITCHORD_MESH -> {
                        // Four colour blobs off the sleeve rather than the sleeve itself.
                        // No AnimatedContent wrapper: the palette crossfades its own
                        // colours over 1.4s, so fading two whole meshes past each other
                        // would be the same transition drawn twice at double the cost.
                        val meshPalette = rememberArtworkMeshPalette(mediaMetadata?.thumbnailUrl)
                        MeshGradientBackground(
                            palette = meshPalette,
                            trackKey = mediaMetadata?.id,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { alpha = backgroundAlpha() },
                        )
                    }
                    PlayerBackgroundStyle.STATIC -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { alpha = backgroundAlpha() }
                                .backdropStaticRegion(playerBackdrop)
                                .background(Color(staticColor))
                        )
                    }
                    PlayerBackgroundStyle.CUSTOM_GRADIENT -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { alpha = backgroundAlpha() }
                                .backdropStaticRegion(playerBackdrop)
                                .tiltedGradient(gradientStops, gradientAngle)
                        )
                    }
                    PlayerBackgroundStyle.DEFAULT -> {
                        // Nothing
                    }
                }

                // No artwork for this song: BLUR/GRADIENT/GLOW_ANIMATED/APPLE_MUSIC/
                // LIVE_MESH all gate on thumbnailUrl above and render nothing in that
                // case, leaving the player fully blank. Center the app's own mark
                // instead of a void. STATIC/CUSTOM_GRADIENT/DEFAULT/GLASS never depend
                // on artwork in the first place, so they're excluded here.
                if (mediaMetadata?.thumbnailUrl == null &&
                    playerBackground !in setOf(
                        PlayerBackgroundStyle.STATIC,
                        PlayerBackgroundStyle.CUSTOM_GRADIENT,
                        PlayerBackgroundStyle.DEFAULT,
                        PlayerBackgroundStyle.GLASS,
                    )
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Image(
                            painter = painterResource(R.drawable.convx_logo),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(if (useDarkTheme) Color.White else Color.Black),
                            modifier = Modifier.size(96.dp).alpha(0.4f),
                        )
                    }
                }

                // Status-bar scrim: black tint ramping from 0 at its own bottom edge
                // up to fully dark at the top, so the status bar icons stay legible
                // over bright artwork — same treatment as the app's own top bar.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .windowInsetsTopHeight(WindowInsets.statusBars)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.45f),
                                    Color.Transparent,
                                ),
                            )
                        )
                )

                // DIY stickers with a negative z sit here: above the artwork blur and the Canvas
                // video, below everything the player actually draws. The Canvas keeps the very
                // bottom of the stack — nothing is allowed behind it.
                //
                // Anchored to diyRootCoordinates (captured in overlayContent, above) rather than
                // this Box's own fillMaxSize() -- see AbsoluteFrameDiyStickers' doc for why: V17
                // (Player_v2.kt) uses this same call with a smaller local frame otherwise.
                //
                // Hidden on lyrics: this sits in the same artwork Box the inline lyrics
                // swap into (AnimatedContent below), so with no gate a sticker bleeds over
                // the lyrics text the same way it bled over the expanded queue sheet.
                // AnimatedVisibility crossfades that switch instead of cutting it.
                AnimatedVisibility(
                    visible = !showInlineLyrics && showDiyStickers,
                    enter = fadeIn(Motion.appear()),
                    exit = fadeOut(Motion.appear()),
                ) {
                    AbsoluteFrameDiyStickers(
                        rootCoordinates = diyRootCoordinates,
                        orientation = diyOrientation,
                        layout = diyLayout,
                        zFilter = { it < 0 },
                    )
                }
            }
        },
        onDismiss = {
            playerConnection.service.clearAutomix()
            playerConnection.player.stop()
            playerConnection.player.clearMediaItems()
        },
        collapsedContent = {
            if (useAppleMusicPlayer) {
                AppleMiniPlayer(
                    progressState = remember(positionState, durationState) {
                        ProgressState(positionState, durationState)
                    }
                )
            } else {
                MiniPlayer(
                    positionState = positionState,
                    durationState = durationState
                )
            }
        },
    ) {
        if (useAppleMusicPlayer) {
            PlayerV2(
                state = state,
                navController = navController,
                modifier = Modifier,
                showDiyStickers = showDiyStickers,
                forcedOrientation = forcedOrientation,
                isPreviewOnly = isPreviewOnly,
                diyRootCoordinates = diyRootCoordinates,
                onCoverShowingChange = { v17ShowingCover = it },
            )
            return@BottomSheet
        }
        val controlsContent: @Composable ColumnScope.(MediaMetadata) -> Unit = { mediaMetadata ->
            val playPauseRoundness by animateDpAsState(
                targetValue = if (isPlaying) 24.dp else 36.dp,
                animationSpec = spring(dampingRatio = 0.75f, stiffness = 1100f),
                label = "playPauseRoundness",
            )

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PlayerHorizontalPadding),
            ) {
                AnimatedContent(
                    targetState = showInlineLyrics,
                    label = "ThumbnailAnimation"
                ) { showLyrics ->
                    if (showLyrics) {
                        Row {
                            if (hidePlayerThumbnail) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(ThumbnailRoundedShape)
                                        .background(AppTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.music_note),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(32.dp),
                                        tint = textButtonColor.copy(alpha = 0.7f)
                                    )
                                }
                            } else {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(ThumbnailRoundedShape)
                                        .clickable(enabled = isFullScreen && enableLyricsThumbnailPlayPause) {
                                            playerConnection.togglePlayPause()
                                        }
                                ) {
                                    AsyncImage(
                                        model = mediaMetadata.thumbnailUrl,
                                        contentDescription = null,
                                        contentScale = if (cropAlbumArt) ContentScale.Crop else ContentScale.Fit,
                                        placeholder = painterResource(R.drawable.thumbnail_fallback),
                                        error = painterResource(R.drawable.thumbnail_fallback),
                                        modifier = Modifier.fillMaxSize()
                                    )

                                    if (isFullScreen && enableLyricsThumbnailPlayPause && !showInlineLyrics) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color.Black.copy(alpha = if (isPlaying) 0f else 0.4f))
                                        )

                                        androidx.compose.animation.AnimatedVisibility(
                                            visible = !isPlaying,
                                            enter = fadeIn(),
                                            exit = fadeOut()
                                        ) {
                                            PlayerGlyph(
                                                slot = if (playbackState == Player.STATE_ENDED)
                                                    PlayerIconSlot.REPLAY else PlayerIconSlot.PLAY,
                                                fallback = if (playbackState == Player.STATE_ENDED)
                                                    R.drawable.replay else R.drawable.play_applemusic,
                                                tint = Color.White,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                    } else {
                        Spacer(modifier = Modifier.width(0.dp))
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .SwipeGesture(
                            enabled = isFullScreen && swipeLyrics,
                            onSwipeRight = { playerConnection.seekToPrevious() },
                            onSwipeLeft = { playerConnection.seekToNext() }
                        )
                ) {
                    AnimatedContent(
                        targetState = mediaMetadata.title,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "",
                    ) { title ->
                        Text(
                            text = title,
                            style = AppTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = TextBackgroundColor,
                            modifier =
                            Modifier
                                .basicMarquee(iterations = 1, initialDelayMillis = 3000, velocity = 30.dp)
                                .combinedClickable(
                                    enabled = true,
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() },
                                    onClick = {
                                        if (mediaMetadata.album != null) {
                                            navController.navigate("album/${mediaMetadata.album.id}") { launchSingleTop = true }
                                            state.collapseSoft()
                                        }
                                    },
                                    onLongClick = {
                                        val clip = ClipData.newPlainText(context.getString(R.string.copied_title), title)
                                        clipboardManager.setPrimaryClip(clip)
                                        Toast
                                            .makeText(context, context.getString(R.string.copied_title), Toast.LENGTH_SHORT)
                                            .show()
                                    }
                                )
                            ,
                        )
                    }

                    Spacer(Modifier.height(4.dp))

                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (mediaMetadata.explicit) MIcon.Explicit()

                        if (mediaMetadata.artists.any { it.name.isNotBlank() }) {
                            val annotatedString = buildAnnotatedString {
                                mediaMetadata.artists.forEachIndexed { index, artist ->
                                    val tag = "artist_${artist.id.orEmpty()}"
                                    pushStringAnnotation(tag = tag, annotation = artist.id.orEmpty())
                                    withStyle(SpanStyle(color = TextBackgroundColor, fontSize = 16.sp)) {
                                        append(artist.name)
                                    }
                                    pop()
                                    if (index != mediaMetadata.artists.lastIndex) append(", ")
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .basicMarquee(iterations = 1, initialDelayMillis = 3000, velocity = 30.dp)
                                    .padding(end = 12.dp)
                                    .additiveChrome(additiveChrome)
                                    .graphicsLayer { if (additiveChrome) alpha = AdditiveChromeContentAlpha }
                            ) {
                                var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
                                var clickOffset by remember { mutableStateOf<Offset?>(null) }
                                Text(
                                    text = annotatedString,
                                    style = AppTheme.typography.titleMedium.copy(color = TextBackgroundColor),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    onTextLayout = { layoutResult = it },
                                    modifier = Modifier
                                        .pointerInput(Unit) {
                                            awaitPointerEventScope {
                                                while (true) {
                                                    val event = awaitPointerEvent()
                                                    val tapPosition = event.changes.firstOrNull()?.position
                                                    if (tapPosition != null) {
                                                        clickOffset = tapPosition
                                                    }
                                                }
                                            }
                                        }
                                        .combinedClickable(
                                            enabled = true,
                                            indication = null,
                                            interactionSource = remember { MutableInteractionSource() },
                                            onClick = {
                                                val tapPosition = clickOffset
                                                val layout = layoutResult
                                                if (tapPosition != null && layout != null) {
                                                    val offset = layout.getOffsetForPosition(tapPosition)
                                                    annotatedString
                                                        .getStringAnnotations(offset, offset)
                                                        .firstOrNull()
                                                        ?.let { ann ->
                                                            val artistId = ann.item
                                                            if (artistId.isNotBlank()) {
                                                                navController.navigate("artist/$artistId") { launchSingleTop = true }
                                                                state.collapseSoft()
                                                            }
                                                        }
                                                }
                                            },
                                            onLongClick = {
                                                val clip =
                                                    ClipData.newPlainText(
                                                        context.getString(R.string.copied_artist),
                                                        annotatedString
                                                    )
                                                clipboardManager.setPrimaryClip(clip)
                                                Toast
                                                    .makeText(
                                                        context,
                                                        context.getString(R.string.copied_artist),
                                                        Toast.LENGTH_SHORT
                                                    )
                                                    .show()
                                            }
                                        )
                                )
                            }
                        }
                    }

                    DjReadout(
                        modifier = Modifier.padding(top = 2.dp)
                            .additiveChrome(additiveChrome)
                            .graphicsLayer { if (additiveChrome) alpha = AdditiveChromeContentAlpha },
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                if (useNewPlayerDesign) {
                    val shareShape = RoundedCornerShape(
                        topStart = 50.dp, bottomStart = 50.dp,
                        topEnd = 3.dp, bottomEnd = 3.dp
                    )

                    val favShape = RoundedCornerShape(
                        topStart = 3.dp, bottomStart = 3.dp,
                        topEnd = 50.dp, bottomEnd = 50.dp
                    )

                    val middleShape = RoundedCornerShape(3.dp)

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AnimatedContent(targetState = showInlineLyrics, label = "DownloadButton") { showLyrics ->
                            if (showLyrics) {
                                FilledIconButton(
                                    onClick = {
                                        isFullScreen = !isFullScreen
                                        // One-tap mode: collapsing out of fullscreen from this
                                        // button exits lyrics entirely (symmetric with the Queue
                                        // lyrics button opening straight to fullscreen), instead of
                                        // stopping at half. Classic two-tap mode is unaffected.
                                        if (oneTapFullscreenLyrics && !isFullScreen) showInlineLyrics = false
                                    },
                                    shape = shareShape,
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = textButtonColor,
                                        contentColor = iconButtonColor,
                                    ),
                                    modifier = Modifier.size(42.dp)
                                        .additiveChrome(additiveChrome)
                                        .graphicsLayer { if (additiveChrome) alpha = AdditiveChromeContentAlpha },
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.fullscreen),
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            } else {
                                FilledIconButton(
                                    onClick = {
                                        mediaMetadata?.let { meta ->
                                            when (download?.state) {
                                                Download.STATE_COMPLETED, Download.STATE_QUEUED, Download.STATE_DOWNLOADING -> {
                                                    DownloadService.sendRemoveDownload(
                                                        context,
                                                        ExoDownloadService::class.java,
                                                        meta.id,
                                                        false,
                                                    )
                                                }
                                                else -> {
                                                    database.transaction {
                                                        insert(meta)
                                                    }
                                                    val downloadRequest =
                                                        DownloadRequest
                                                            .Builder(meta.id, meta.id.toUri())
                                                            .setCustomCacheKey(meta.id)
                                                            .setData(meta.title.toByteArray())
                                                            .build()
                                                    DownloadService.sendAddDownload(
                                                        context,
                                                        ExoDownloadService::class.java,
                                                        downloadRequest,
                                                        false,
                                                    )
                                                }
                                            }
                                        }
                                    },
                                    shape = shareShape,
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = textButtonColor,
                                        contentColor = iconButtonColor,
                                    ),
                                    modifier = Modifier.size(42.dp)
                                        .additiveChrome(additiveChrome)
                                        .graphicsLayer { if (additiveChrome) alpha = AdditiveChromeContentAlpha },
                                ) {
                                    when (download?.state) {
                                        Download.STATE_COMPLETED -> {
                                            Icon(
                                                painter = painterResource(R.drawable.offline),
                                                contentDescription = null,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                        Download.STATE_QUEUED, Download.STATE_DOWNLOADING -> {
                                            CircularWavyProgressIndicator(
                                                modifier = Modifier.size(24.dp),
                                            )
                                        }
                                        else -> {
                                            Icon(
                                                painter = painterResource(R.drawable.download),
                                                contentDescription = null,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        AnimatedContent(targetState = showInlineLyrics, label = "LikeButton") { showLyrics ->
                            if (showLyrics) {
                                val currentLyrics by playerConnection.currentLyrics.collectAsStateWithLifecycle(initialValue =null)
                                FilledIconButton(
                                    onClick = {
                                        menuState.show {
                                            com.convx.music.ui.menu.LyricsMenu(
                                                lyricsProvider = { currentLyrics },
                                                songProvider = { currentSong?.song },
                                                mediaMetadataProvider = { mediaMetadata },
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
                                    },
                                    shape = favShape,
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = textButtonColor,
                                        contentColor = iconButtonColor,
                                    ),
                                    modifier = Modifier.size(42.dp)
                                        .additiveChrome(additiveChrome)
                                        .graphicsLayer { if (additiveChrome) alpha = AdditiveChromeContentAlpha },
                                ) {
                                    PlayerGlyph(
                                        slot = PlayerIconSlot.MORE,
                                        fallback = R.drawable.more_horiz,
                                        tint = LocalContentColor.current,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            } else {
                                FilledIconButton(
                                    onClick = playerConnection::toggleLike,
                                    shape = favShape,
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = textButtonColor,
                                        contentColor = iconButtonColor,
                                    ),
                                    modifier = Modifier.size(42.dp)
                                        .additiveChrome(additiveChrome)
                                        .graphicsLayer { if (additiveChrome) alpha = AdditiveChromeContentAlpha },
                                ) {
                                    PlayerGlyph(
                                        slot = if (currentSong?.song?.liked == true)
                                            PlayerIconSlot.LIKED else PlayerIconSlot.LIKE,
                                        fallback = if (currentSong?.song?.liked == true)
                                            R.drawable.favorite else R.drawable.favorite_border,
                                        tint = LocalContentColor.current,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Sample the player's own background (its blurred artwork/mesh
                    // layer), not whatever NavHost screen happens to be behind the
                    // player sheet — see playerBackdrop declaration above.
                    // Stable across recompositions (remember'd) so DrawBackdropNode's
                    // loopBucket-identity check doesn't clear the pool every time this
                    // scope recomposes — only when video canvas actually toggles.
                    val loopBucketProvider = remember(videoLoopClock) {
                        { videoLoopClock.bucket(VideoLoopBucketMs) }
                    }
                    val videoCanvasActive = enableCanvas && canvasArtwork != null && backgroundVisible
                    CompositionLocalProvider(
                        LocalAppBackdrop provides playerBackdrop,
                        LocalBackdropLoopBucket provides if (videoCanvasActive) loopBucketProvider else null,
                    ) {
                    AnimatedContent(targetState = showInlineLyrics, label = "DownloadButton") { showLyrics ->
                        if (showLyrics) {
                            GlassCircleButton(
                                onClick = {
                                    isFullScreen = !isFullScreen
                                    if (oneTapFullscreenLyrics && !isFullScreen) showInlineLyrics = false
                                },
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.fullscreen),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        } else {
                            GlassCircleButton(
                                onClick = {
                                    menuState.show {
                                        OldPlayerMenu(
                                            mediaMetadata = mediaMetadata,
                                            navController = navController,
                                            playerBottomSheetState = state,
                                            onShowDetailsDialog = {
                                                mediaMetadata.id.let {
                                                    bottomSheetPageState.show {
                                                       ShowMediaInfo(it)
                                                    }
                                                }
                                            },
                                            onDismiss = menuState::dismiss
                                        )
                                    }
                                },
                            ) {
                                PlayerGlyph(
                                    slot = PlayerIconSlot.MORE,
                                    fallback = R.drawable.more_horiz,
                                    tint = LocalContentColor.current,
                                    modifier = Modifier.size(24.dp)
                                        .additiveChrome(additiveChrome)
                                        .graphicsLayer { if (additiveChrome) alpha = AdditiveChromeContentAlpha }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.size(12.dp))

                    AnimatedContent(targetState = showInlineLyrics, label = "LikeButton") { showLyrics ->
                        if (showLyrics) {
                            val currentLyrics by playerConnection.currentLyrics.collectAsStateWithLifecycle(initialValue =null)
                            GlassCircleButton(
                                onClick = {
                                    menuState.show {
                                        com.convx.music.ui.menu.LyricsMenu(
                                            lyricsProvider = { currentLyrics },
                                            songProvider = { currentSong?.song },
                                            mediaMetadataProvider = { mediaMetadata },
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
                                },
                            ) {
                                PlayerGlyph(
                                    slot = PlayerIconSlot.MORE,
                                    fallback = R.drawable.more_horiz,
                                    tint = LocalContentColor.current,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        } else {
                            GlassCircleButton(
                                onClick = playerConnection::toggleLike,
                            ) {
                                PlayerGlyph(
                                    slot = if (currentSong?.song?.liked == true)
                                        PlayerIconSlot.LIKED else PlayerIconSlot.LIKE,
                                    fallback = if (currentSong?.song?.liked == true)
                                        R.drawable.favorite else R.drawable.favorite_border,
                                    tint = if (currentSong?.song?.liked == true) AppTheme.colorScheme.error else LocalContentColor.current,
                                    modifier = Modifier.size(24.dp)
                                        .additiveChrome(additiveChrome)
                                        .graphicsLayer { if (additiveChrome) alpha = AdditiveChromeContentAlpha },
                                )
                            }
                        }
                    }
                    }
                }
            }

            Spacer(Modifier.height(if (useNewPlayerDesign) 24.dp else 8.dp))

            // Isolate the per-tick position/duration reads into their own recomposition
            // scope so the 100ms progress polling no longer recomposes the whole
            // controlsContent subtree (slider + time labels only).
            val seekControls: @Composable () -> Unit = {
            when (sliderStyle) {
                SliderStyle.DEFAULT -> {
                    Slider(
                        value = (sliderPosition ?: effectivePosition).toFloat(),
                        valueRange = 0f..(if (duration == C.TIME_UNSET) 0f else duration.toFloat()),
                        onValueChange = {
                            if (!isListenTogetherGuest) {
                                sliderPosition = it.toLong()
                            }
                        },
                        onValueChangeFinished = {
                            if (!isListenTogetherGuest) {
                                sliderPosition?.let {
                                    if (isCasting) {
                                        castHandler?.seekTo(it)
                                        lastManualSeekTime = System.currentTimeMillis()
                                    } else {
                                        playerConnection.player.seekTo(it)
                                    }
                                    position = it
                                }
                                sliderPosition = null
                            }
                        },
                        enabled = !isListenTogetherGuest,
                        colors = PlayerSliderColors.getSliderColors(
                            activeColor = if (useNewPlayerDesign) seekBarActiveColor else seekBarActiveColor.copy(alpha = 0.7f),
                            // A hairline, not a grey rail: it only reads this faint because
                            // the bar is added onto the artwork rather than laid over it.
                            inactiveAlpha = if (additiveChrome) 0.05f else 0.3f,
                        ),
                        modifier = Modifier
                            .padding(horizontal = PlayerHorizontalPadding)
                            .additiveChrome(additiveChrome)
                            .graphicsLayer { if (additiveChrome) alpha = AdditiveChromeContentAlpha },
                    )
                }

                SliderStyle.WAVY -> {
                    run {
                        WavySlider(
                            value = (sliderPosition ?: effectivePosition).toFloat(),
                            valueRange = 0f..(if (duration == C.TIME_UNSET) 0f else duration.toFloat()),
                            enabled = !isListenTogetherGuest,
                            onValueChange = {
                                if (!isListenTogetherGuest) sliderPosition = it.toLong()
                            },
                            onValueChangeFinished = {
                                if (!isListenTogetherGuest) {
                                    sliderPosition?.let {
                                        if (isCasting) {
                                            castHandler?.seekTo(it)
                                            lastManualSeekTime = System.currentTimeMillis()
                                        } else {
                                            playerConnection.player.seekTo(it)
                                        }
                                        position = it
                                    }
                                    sliderPosition = null
                                }
                            },
                            colors = PlayerSliderColors.getSliderColors(
                                activeColor = if (useNewPlayerDesign) seekBarActiveColor else seekBarActiveColor.copy(alpha = 0.7f),
                            // A hairline, not a grey rail: it only reads this faint because
                            // the bar is added onto the artwork rather than laid over it.
                            inactiveAlpha = if (additiveChrome) 0.05f else 0.3f,
                            ),
                            modifier = Modifier.padding(horizontal = PlayerHorizontalPadding),
                            isPlaying = effectiveIsPlaying
                        )
                    }
                }

                SliderStyle.SLIM -> {
                    val trackInteractionSource = remember { MutableInteractionSource() }
                    val isTrackDragged by trackInteractionSource.collectIsDraggedAsState()
                    val isTrackPressed by trackInteractionSource.collectIsPressedAsState()
                    val isTrackActive = (isTrackDragged || isTrackPressed) && !useNewPlayerDesign

                    val trackHeight by animateDpAsState(
                        targetValue = if (isTrackActive) 16.dp else 10.dp,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "trackHeight"
                    )

                    Slider(
                        value = (sliderPosition ?: effectivePosition).toFloat(),
                        valueRange = 0f..(if (duration == C.TIME_UNSET) 0f else duration.toFloat()),
                        onValueChange = {
                            if (!isListenTogetherGuest) {
                                sliderPosition = it.toLong()
                            }
                        },
                        onValueChangeFinished = {
                            if (!isListenTogetherGuest) {
                                sliderPosition?.let {
                                    if (isCasting) {
                                        castHandler?.seekTo(it)
                                        lastManualSeekTime = System.currentTimeMillis()
                                    } else {
                                        playerConnection.player.seekTo(it)
                                    }
                                    position = it
                                }
                                sliderPosition = null
                            }
                        },
                        enabled = !isListenTogetherGuest,
                        interactionSource = trackInteractionSource,
                        // The stock design has no visible handle; a custom one only appears
                        // once the user actually supplies an image for the slot.
                        thumb = {
                            val seekThumb = rememberPlayerIcon(PlayerIconSlot.SEEK_THUMB)
                            if (seekThumb.isCustom) {
                                Image(
                                    painter = seekThumb.painter,
                                    contentDescription = null,
                                    colorFilter = seekThumb.colorFilterFor(textButtonColor),
                                    modifier = Modifier.size(18.dp),
                                )
                            } else {
                                Spacer(modifier = Modifier.size(0.dp))
                            }
                        },
                        track = { sliderState ->
                            PlayerSliderTrack(
                                sliderState = sliderState,
                                trackHeight = trackHeight,
                                colors = PlayerSliderColors.getSliderColors(
                                    activeColor = if (useNewPlayerDesign) seekBarActiveColor else seekBarActiveColor.copy(alpha = 0.7f),
                            // A hairline, not a grey rail: it only reads this faint because
                            // the bar is added onto the artwork rather than laid over it.
                            inactiveAlpha = if (additiveChrome) 0.05f else 0.3f,
                                )
                            )
                        },
                        modifier = Modifier
                            .padding(horizontal = PlayerHorizontalPadding)
                            .additiveChrome(additiveChrome)
                            // After additiveChrome, never before: alpha applied first
                            // is folded into the saved layer and the add then runs at
                            // full strength.
                            .graphicsLayer { if (additiveChrome) alpha = AdditiveChromeContentAlpha }
                    )
                }

                SliderStyle.WAVEFORM -> {
                    val waveColors = PlayerSliderColors.getSliderColors(
                        activeColor = if (useNewPlayerDesign) seekBarActiveColor else seekBarActiveColor.copy(alpha = 0.7f),
                        // A hairline, not a grey rail: it only reads this faint because the
                        // bar is added onto the artwork rather than laid over it.
                        inactiveAlpha = if (additiveChrome) 0.05f else 0.3f,
                    )
                    // Per-frame off the player clock, so the waveform glides rather
                    // than stepping with the once-a-second position state.
                    val waveFraction = rememberPlaybackFraction(playerConnection.player, effectiveIsPlaying)
                    ScrollingWaveformSeekBar(
                        progress = {
                            val dragged = sliderPosition
                            if (dragged != null && duration > 0L && duration != C.TIME_UNSET) {
                                dragged.toFloat() / duration
                            } else {
                                waveFraction.value
                            }
                        },
                        onSeek = { f ->
                            if (!isListenTogetherGuest && duration > 0L && duration != C.TIME_UNSET) {
                                val target = (f * duration).toLong()
                                if (isCasting) {
                                    castHandler?.seekTo(target)
                                    lastManualSeekTime = System.currentTimeMillis()
                                } else {
                                    playerConnection.player.seekTo(target)
                                }
                                position = target
                            }
                        },
                        playedColor = waveColors.activeTrackColor,
                        trackColor = waveColors.inactiveTrackColor,
                        seed = mediaMetadata?.id?.hashCode() ?: 0,
                        totalBars = 160,
                        visibleBars = 44,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = PlayerHorizontalPadding)
                            .height(40.dp),
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PlayerHorizontalPadding + 4.dp)
                    .additiveChrome(additiveChrome)
                    .graphicsLayer { if (additiveChrome) alpha = AdditiveChromeContentAlpha },
            ) {
                Text(
                    text = makeTimeString(sliderPosition ?: effectivePosition),
                    style = AppTheme.typography.labelMedium,
                    color = TextBackgroundColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                if (showAudioQualityBadge || sleepTimerEnabled) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(TextBackgroundColor.copy(alpha = 0.08f))
                            .border(
                                width = 0.5.dp,
                                color = TextBackgroundColor.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .clickable {
                                if (sleepTimerEnabled) {
                                    showSleepTimerDialog = true
                                } else {
                                    mediaMetadata.id.let {
                                        bottomSheetPageState.show {
                                            ShowMediaInfo(it)
                                        }
                                    }
                                }
                            }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        AnimatedContent(
                            targetState = sleepTimerEnabled,
                            transitionSpec = {
                                fadeIn(animationSpec = tween(300)) togetherWith
                                        fadeOut(animationSpec = tween(300))
                            },
                            label = "QualityTimerSwitcher"
                        ) { isTimerActive ->
                            if (isTimerActive) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.timer),
                                        contentDescription = null,
                                        tint = TextBackgroundColor.copy(alpha = 0.8f),
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = makeTimeString(sleepTimerTimeLeft.coerceAtLeast(0)),
                                        style = AppTheme.typography.labelSmall.copy(
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.5.sp
                                        ),
                                        color = TextBackgroundColor.copy(alpha = 0.8f),
                                        maxLines = 1,
                                    )
                                }
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    // An infiniteRepeatable never finishes, so this decorative
                                    // sweep kept awaiting a frame for as long as the player was
                                    // on screen — the whole app redrew every vsync, paused
                                    // included. Spin it only while something is actually playing.
                                    val animatedRotation = if (isPlaying && !state.isCollapsed) {
                                        val infiniteTransition =
                                            rememberInfiniteTransition(label = "QualityIconTransition")
                                        infiniteTransition.animateFloat(
                                            initialValue = 0f,
                                            targetValue = 360f,
                                            animationSpec = infiniteRepeatable(
                                                animation = tween(2000, easing = LinearEasing),
                                                repeatMode = RepeatMode.Restart
                                            ),
                                            label = "QualityIconRotation"
                                        )
                                    } else {
                                        remember { mutableFloatStateOf(0f) }
                                    }

                                    val iconBrush = Brush.sweepGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            TextBackgroundColor.copy(alpha = 1.0f),
                                            Color.Transparent
                                        )
                                    )

                                    Icon(
                                        painter = painterResource(R.drawable.stream_old_player),
                                        contentDescription = null,
                                        tint = Color.Unspecified,
                                        modifier = Modifier
                                            .size(12.dp)
                                            .graphicsLayer(alpha = 0.99f)
                                            .drawWithCache {
                                                onDrawWithContent {
                                                    drawContent()
                                                    rotate(animatedRotation.value) {
                                                        drawRect(iconBrush, blendMode = BlendMode.SrcIn)
                                                    }
                                                }
                                            }
                                    )
                                    Text(
                                        text = run {
                                            val format = playerFormat ?: currentFormat?.let {
                                                // Fallback to DB info if live format not yet available
                                                androidx.media3.common.Format.Builder()
                                                    .setSampleMimeType(it.mimeType)
                                                    .setCodecs(it.codecs)
                                                    .setAverageBitrate(it.bitrate ?: 0)
                                                    .setSampleRate(it.sampleRate ?: 0)
                                                    .build()
                                            }
                                            
                                            val codecLabel = format?.let { f ->
                                                val mime = f.sampleMimeType?.lowercase() ?: ""
                                                val codecs = f.codecs?.lowercase() ?: ""
                                                when {
                                                    mime.contains("flac") || codecs.contains("flac") -> "FLAC"
                                                    mime.contains("opus") || codecs.contains("opus") -> "OPUS"
                                                    mime.contains("mp4a") || mime.contains("aac") || codecs.contains("mp4a") || codecs.contains("aac") -> "AAC"
                                                    mime.contains("mp3") || mime.contains("mpeg") || codecs.contains("mp3") || codecs.contains("mpeg") -> "MP3"
                                                    mime.contains("vorbis") || codecs.contains("vorbis") -> "OGG"
                                                    mime.contains("webm") || codecs.contains("webm") -> "WEBM"
                                                    else -> null
                                                }
                                            }
                                            
                                            val bitrate = format?.bitrate?.takeIf { it > 0 } ?: format?.averageBitrate?.takeIf { it > 0 }
                                            val bitrateLabel = bitrate?.let { "${it / 1000}kbps" }
                                            
                                            val sampleRate = format?.sampleRate?.takeIf { it > 0 }
                                            val sampleRateLabel = sampleRate?.let { "${it / 1000}.${(it % 1000) / 100}kHz" }

                                            buildString {
                                                append(codecLabel ?: if (isLosslessStream) "LOSSLESS" else "AUTO")
                                                if (bitrateLabel != null) append(" • $bitrateLabel")
                                                if (sampleRateLabel != null) append(" • $sampleRateLabel")
                                            }
                                        },
                                        style = AppTheme.typography.labelSmall.copy(
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.0.sp
                                        ),
                                        color = TextBackgroundColor.copy(alpha = 0.8f),
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }

                Text(
                    text = if (duration != C.TIME_UNSET) makeTimeString(duration) else "",
                    style = AppTheme.typography.labelMedium,
                    color = TextBackgroundColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            }
            seekControls()

            Spacer(Modifier.height(if (useNewPlayerDesign) 24.dp else 8.dp))

            // Below the seek bar, not the title row: the title area reflows with the
            // marquee/artist tap targets, which used to shove this up and down with it.
            // Padding matches seekControls' own PlayerHorizontalPadding exactly rather
            // than inheriting an ambient Row padding, so it lines up with the seek bar
            // regardless of what the title row above does with its own inset.
            if (showUpNext) {
                UpNextSong(
                    playerConnection = playerConnection,
                    titleColor = TextBackgroundColor,
                    subtitleColor = TextBackgroundColor.copy(alpha = 0.7f),
                    modifier = Modifier.padding(horizontal = PlayerHorizontalPadding),
                    additiveChrome = additiveChrome,
                )
                Spacer(Modifier.height(8.dp))
            }

            AnimatedVisibility(
                // fullscreenLyricsCollapseTop repurposed: on, the controls stay
                // visible (and get reordered above the lyrics — see the two
                // controlsContent call sites below) instead of hiding entirely.
                visible = !isFullScreen || (fullscreenLyricsCollapseTop && !showInlineLyrics),
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.Top) +
                    slideOutVertically(targetOffsetY = { -it }) + fadeOut()
            ) {
                Column {
                    // The transport controls below are already inert for a guest, but
                    // greyed-out buttons alone read as a bug. Say why, once, above both
                    // control layouts.
                    if (isListenTogetherGuest) {
                        Text(
                            text = stringResource(R.string.listen_together_locked),
                            style = AppTheme.typography.labelMedium,
                            color = textButtonColor.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = PlayerHorizontalPadding, vertical = 4.dp),
                        )
                    }
                    if (useNewPlayerDesign) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = PlayerHorizontalPadding)
                        ) {
                            val backInteractionSource = remember { MutableInteractionSource() }
                            val nextInteractionSource = remember { MutableInteractionSource() }
                            val playPauseInteractionSource = remember { MutableInteractionSource() }

                            val isPlayPausePressed by playPauseInteractionSource.collectIsPressedAsState()
                            val isBackPressed by backInteractionSource.collectIsPressedAsState()
                            val isNextPressed by nextInteractionSource.collectIsPressedAsState()

                            val playPauseWeight by animateFloatAsState(
                                targetValue = if (isPlayPausePressed) 1.9f else if (isBackPressed || isNextPressed) 1.1f else 1.3f,
                                animationSpec = spring(
                                    dampingRatio = 0.6f,
                                    stiffness = 500f
                                ),
                                label = "playPauseWeight"
                            )

                            val backButtonWeight by animateFloatAsState(
                                targetValue = if (isBackPressed) 0.65f else if (isPlayPausePressed) 0.35f else 0.45f,
                                animationSpec = spring(
                                    dampingRatio = 0.6f,
                                    stiffness = 500f
                                ),
                                label = "backButtonWeight"
                            )

                            val nextButtonWeight by animateFloatAsState(
                                targetValue = if (isNextPressed) 0.65f else if (isPlayPausePressed) 0.35f else 0.45f,
                                animationSpec = spring(
                                    dampingRatio = 0.6f,
                                    stiffness = 500f
                                ),
                                label = "nextButtonWeight"
                            )

                            FilledIconButton(
                                onClick = playerConnection::seekToPrevious,
                                enabled = canSkipPrevious && !isListenTogetherGuest,
                                shape = RoundedCornerShape(50),
                                interactionSource = backInteractionSource,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = sideButtonContainerColor,
                                    contentColor = sideButtonContentColor,
                                ),
                                modifier = Modifier
                                    .height(68.dp)
                                    .weight(backButtonWeight)
                                    .widthIn(min = 44.dp)
                                    .additiveChrome(additiveChrome)
                                    .graphicsLayer { if (additiveChrome) alpha = AdditiveChromeContentAlpha }
                            ) {
                                PlayerGlyph(
                                    slot = PlayerIconSlot.PREVIOUS,
                                    fallback = R.drawable.skip_previous_legacy,
                                    tint = LocalContentColor.current,
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            FilledIconButton(
                                onClick = {
                                    if (isListenTogetherGuest) {
                                        playerConnection.toggleMute()
                                        return@FilledIconButton
                                    }
                                    if (isCasting) {
                                        if (castIsPlaying) {
                                            castHandler?.pause()
                                        } else {
                                            castHandler?.play()
                                        }
                                    } else if (playbackState == STATE_ENDED) {
                                        playerConnection.player.seekTo(0, 0)
                                        playerConnection.player.playWhenReady = true
                                    } else {
                                        playerConnection.togglePlayPause()
                                    }
                                },
                                shape = RoundedCornerShape(50),
                                interactionSource = playPauseInteractionSource,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = textButtonColor,
                                    contentColor = iconButtonColor,
                                ),
                                modifier = Modifier
                                    .height(68.dp)
                                    .weight(playPauseWeight)
                                    .widthIn(min = 44.dp)
                                    .additiveChrome(additiveChrome)
                                    .graphicsLayer { if (additiveChrome) alpha = AdditiveChromeContentAlpha }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    if (isListenTogetherGuest) {
                                        // Guests get a mute toggle in this position rather than
                                        // transport, and that button is not a customisable slot
                                        // (nor a play/pause pair to animate between).
                                        PlayerGlyph(
                                            slot = null,
                                            fallback = if (isMuted) R.drawable.volume_off else R.drawable.volume_down,
                                            tint = LocalContentColor.current,
                                            contentDescription = if (isMuted) stringResource(R.string.unmute) else stringResource(R.string.mute),
                                            modifier = Modifier.size(32.dp)
                                        )
                                    } else {
                                        AnimatedPlayerGlyph(
                                            isPlaying = effectiveIsPlaying,
                                            playSlot = PlayerIconSlot.PLAY,
                                            pauseSlot = PlayerIconSlot.PAUSE,
                                            playFallback = R.drawable.play_applemusic,
                                            pauseFallback = R.drawable.pause_applemusic,
                                            tint = LocalContentColor.current,
                                            playDescription = stringResource(R.string.play),
                                            pauseDescription = stringResource(R.string.pause),
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (isListenTogetherGuest) {
                                            if (isMuted) stringResource(R.string.unmute) else stringResource(R.string.mute)
                                        } else {
                                            if (effectiveIsPlaying) stringResource(R.string.pause) else stringResource(R.string.play)
                                        },
                                        style = AppTheme.typography.titleMedium
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            FilledIconButton(
                                onClick = playerConnection::seekToNext,
                                enabled = canSkipNext && !isListenTogetherGuest,
                                shape = RoundedCornerShape(50),
                                interactionSource = nextInteractionSource,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = sideButtonContainerColor,
                                    contentColor = sideButtonContentColor,
                                ),
                                modifier = Modifier
                                    .height(68.dp)
                                    .weight(nextButtonWeight)
                                    .widthIn(min = 44.dp)
                                    .additiveChrome(additiveChrome)
                                    .graphicsLayer { if (additiveChrome) alpha = AdditiveChromeContentAlpha }
                            ) {
                                PlayerGlyph(
                                    slot = PlayerIconSlot.NEXT,
                                    fallback = R.drawable.fast_forward,
                                    tint = LocalContentColor.current,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = PlayerHorizontalPadding),
                        ) {
//                            Box(modifier = Modifier.weight(1f)) {
//                                ResizableIconButton(
//                                    icon = when (repeatMode) {
//                                        Player.REPEAT_MODE_OFF, Player.REPEAT_MODE_ALL -> R.drawable.repeat
//                                        Player.REPEAT_MODE_ONE -> R.drawable.repeat_one
//                                        else -> throw IllegalStateException()
//                                    },
//                                    color = TextBackgroundColor,
//                                    modifier = Modifier
//                                        .size(32.dp)
//                                        .padding(4.dp)
//                                        .align(Alignment.Center)
//                                        .alpha(if (isListenTogetherGuest) 0.5f else 1f),
//                                    enabled = !isListenTogetherGuest,
//                                    onClick = {
//                                        playerConnection.player.toggleRepeatMode()
//                                    }
//                                )
//                            }

                            Box(modifier = Modifier.weight(1f)) {
                                ResizableIconButton(
                                    slot = PlayerIconSlot.PREVIOUS,
                                    icon = R.drawable.skip_previous_legacy,
                                    enabled = canSkipPrevious && !isListenTogetherGuest,
                                    color = TextBackgroundColor,
                                    modifier =
                                Modifier
                                    .size(48.dp)
                                    .align(Alignment.Center)
                                    .alpha(if (isListenTogetherGuest) 0.5f else 1f)
                                    .additiveChrome(additiveChrome)
                                    .graphicsLayer { if (additiveChrome) alpha = AdditiveChromeContentAlpha },
                                    onClick = playerConnection::seekToPrevious,
                                )
                            }

                            Spacer(Modifier.width(8.dp))

                            Box(
                                modifier =
                                Modifier
                                    .size(100.dp) //100
                                    .clip(RoundedCornerShape(playPauseRoundness))
                                    .additiveChrome(additiveChrome)
                                    .graphicsLayer { if (additiveChrome) alpha = AdditiveChromeContentAlpha }
                                    .clickable {
                                        if (isListenTogetherGuest) {
                                            playerConnection.toggleMute()
                                            return@clickable
                                        }
                                        if (isCasting) {
                                            if (castIsPlaying) {
                                                castHandler?.pause()
                                            } else {
                                                castHandler?.play()
                                            }
                                        } else if (playbackState == STATE_ENDED) {
                                            playerConnection.player.seekTo(0, 0)
                                            playerConnection.player.playWhenReady = true
                                        } else {
                                            playerConnection.player.togglePlayPause()
                                        }
                                    },
                            ) {
                                // Mute (guest) and replay (ended) are single states with no
                                // partner to animate between, so only the transport pair gets
                                // the pop.
                                if (isListenTogetherGuest || playbackState == STATE_ENDED) {
                                    PlayerGlyph(
                                        slot = if (isListenTogetherGuest) null else PlayerIconSlot.REPLAY,
                                        fallback = if (isListenTogetherGuest) {
                                            if (isMuted) R.drawable.volume_mute else R.drawable.volume_down
                                        } else {
                                            R.drawable.replay
                                        },
                                        tint = TextBackgroundColor,
                                        modifier =
                                        Modifier
                                            .align(Alignment.Center)
                                            .size(72.dp),
                                    )
                                } else {
                                    AnimatedPlayerGlyph(
                                        isPlaying = effectiveIsPlaying,
                                        playSlot = PlayerIconSlot.PLAY,
                                        pauseSlot = PlayerIconSlot.PAUSE,
                                        playFallback = R.drawable.play_applemusic,
                                        pauseFallback = R.drawable.pause_applemusic,
                                        tint = TextBackgroundColor,
                                        playDescription = stringResource(R.string.play),
                                        pauseDescription = stringResource(R.string.pause),
                                        playSize = 72.dp,
                                        pauseSize = 72.dp,
                                        modifier =
                                        Modifier
                                            .align(Alignment.Center),
                                    )
                                }
                            }

                            Spacer(Modifier.width(8.dp))

                            Box(modifier = Modifier.weight(1f)) {
                                ResizableIconButton(
                                    slot = PlayerIconSlot.NEXT,
                                    icon = R.drawable.fast_forward,
                                    enabled = canSkipNext && !isListenTogetherGuest,
                                    color = TextBackgroundColor,
                                    modifier =
                                Modifier
                                    .size(48.dp)
                                    .align(Alignment.Center)
                                    .alpha(if (isListenTogetherGuest) 0.5f else 1f)
                                    .additiveChrome(additiveChrome)
                                    .graphicsLayer { if (additiveChrome) alpha = AdditiveChromeContentAlpha },
                                    onClick = playerConnection::seekToNext,
                                )
                            }

//                            Box(modifier = Modifier.weight(1f)) {
//                                ResizableIconButton(
//                                    icon = if (currentSong?.song?.liked == true) R.drawable.favorite else R.drawable.favorite_border,
//                                    color = if (currentSong?.song?.liked == true) AppTheme.colorScheme.error else TextBackgroundColor,
//                                    modifier =
//                                    Modifier
//                                        .size(32.dp)
//                                        .padding(4.dp)
//                                        .align(Alignment.Center),
//                                    onClick = playerConnection::toggleLike,
//                                )
//                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp)) //space between play and audio

                        // Hidden means invisible + non-interactive, not removed — the row still
                        // reserves its layout space so a DIY sticker can be placed over it.
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = PlayerHorizontalPadding)
                                .alpha(if (hideVolumeBar) 0f else 1f)
                                .then(if (hideVolumeBar) Modifier.clearAndSetSemantics {} else Modifier)
                                .additiveChrome(additiveChrome)
                                .graphicsLayer { if (additiveChrome) alpha = AdditiveChromeContentAlpha }
                        ) {
                            val volumeInteractionSource = remember { MutableInteractionSource() }
                            val isVolumeDragged by volumeInteractionSource.collectIsDraggedAsState()
                            val isVolumePressed by volumeInteractionSource.collectIsPressedAsState()
                            val isVolumeActive = isVolumeDragged || isVolumePressed

                            // Internal state to track drag value and avoid system feedback lag
                            var dragVolume by remember { mutableFloatStateOf(systemVolume) }
                            
                            // Use a coroutine to update system volume to avoid UI blocking on fast swipes
                            val scope = rememberCoroutineScope()
                            
                            LaunchedEffect(systemVolume) {
                                if (!isVolumeActive) dragVolume = systemVolume
                            }

                            // Smoothly animate the volume position when changed via buttons
                            val animatedSystemVolume by animateFloatAsState(
                                targetValue = systemVolume,
                                animationSpec = tween(150, easing = LinearOutSlowInEasing),
                                label = "animatedSystemVolume"
                            )
                            
                            val volume = if (isCasting) castVolume else {
                                if (isVolumeActive) dragVolume else animatedSystemVolume
                            }
                            
                            val volumeTrackHeight by animateDpAsState(
                                targetValue = if (isVolumeActive) 16.dp else 10.dp,
                                animationSpec = spring(
                                    dampingRatio = 0.7f, // Slightly more stable damping
                                    stiffness = 600f // Balanced stiffness for high-speed stability
                                ),
                                label = "volumeTrackHeight"
                            )

                            val volumeIconScale by animateFloatAsState(
                                targetValue = if (isVolumeActive) 1.15f else 1f,
                                animationSpec = spring(
                                    dampingRatio = 0.7f,
                                    stiffness = 600f
                                ),
                                label = "volumeIconScale"
                            )

                            Icon(
                                painter = painterResource(R.drawable.volume_mute),
                                contentDescription = null,
                                tint = textButtonColor,
                                modifier = Modifier
                                    .size(20.dp)
                                    .graphicsLayer(scaleX = volumeIconScale, scaleY = volumeIconScale)
                            )

                            Spacer(Modifier.width(12.dp))

                            Slider(
                                value = volume,
                                onValueChange = { newVolume ->
                                    dragVolume = newVolume
                                    if (isCasting) {
                                        castHandler?.setVolume(newVolume)
                                    } else {
                                        // Non-blocking update to prevent "fast swipe" lag
                                        scope.launch(Dispatchers.Default) {
                                            val newStep = (newVolume * maxSystemVolume).roundToInt()
                                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newStep, 0)
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                enabled = !hideVolumeBar,
                                interactionSource = volumeInteractionSource,
                                thumb = {},
                                track = { sliderState ->
                                    PlayerSliderTrack(
                                        sliderState = sliderState,
                                        colors = SliderDefaults.colors(
                                            activeTrackColor = seekBarActiveColor.copy(alpha = 0.7f),
                                            inactiveTrackColor = seekBarActiveColor.copy(alpha = 0.15f)
                                        ),
                                        trackHeight = volumeTrackHeight,
                                        // Volume, not position: the mix window means nothing here.
                                        showAutomix = false
                                    )
                                }
                            )

                            Spacer(Modifier.width(12.dp))

                            Icon(
                                painter = painterResource(R.drawable.volume_down),
                                contentDescription = null,
                                tint = textButtonColor,
                                modifier = Modifier
                                    .size(20.dp)
                                    .graphicsLayer(scaleX = volumeIconScale, scaleY = volumeIconScale)
                            )
                        }

                        val displayBluetoothName = remember(bluetoothDeviceName) {
                            if (bluetoothDeviceName != null) bluetoothDeviceName else bluetoothDeviceName
                        }
                        // Use a persistent state to keep the name during exit animation
                        var lastNonNullName by remember { mutableStateOf<String?>(null) }
                        LaunchedEffect(bluetoothDeviceName) {
                            if (bluetoothDeviceName != null) lastNonNullName = bluetoothDeviceName
                        }

                        AnimatedVisibility(
                            visible = !useNewPlayerDesign && bluetoothDeviceName != null,
                            enter = fadeIn(tween(400)) + expandVertically(tween(400)),
                            exit = fadeOut(tween(400)) + shrinkVertically(tween(400)),
                            label = "BluetoothInfoVisibility"
                        ) {
                            val nameToShow = bluetoothDeviceName ?: lastNonNullName
                            Column {
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                        .additiveChrome(additiveChrome)
                                        .graphicsLayer { if (additiveChrome) alpha = AdditiveChromeContentAlpha }
                                ) {
                                    Icon(
                                        painter = painterResource(
                                            when {
                                                isSpeaker(nameToShow) -> R.drawable.speaker_applemusic
                                                isBuds(nameToShow) -> R.drawable.apple_airpods
                                                else -> R.drawable.apple_headset
                                            }
                                        ),
                                        contentDescription = null,
                                        tint = textButtonColor.copy(alpha = 0.7f),
                                        modifier = Modifier.size(
                                            when {
                                                isSpeaker(nameToShow) -> 18.dp
                                                isBuds(nameToShow) -> 20.dp
                                                else -> 16.dp
                                            }
                                        )
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = nameToShow ?: "",
                                        style = AppTheme.typography.labelSmall,
                                        color = textButtonColor.copy(alpha = 0.7f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Compact tab view forces the stacked (mobile-style) branch below even on a
        // physically-landscape/wide screen — that's the whole point of the setting:
        // no side-by-side thumbnail, controls centered underneath like on phone.
        val forceStackedPlayerLayout = LocalTabView.current && compactPlayerInTabView
        when {
            !forceStackedPlayerLayout && isLandscapeLayout -> {
                // Calculate vertical padding like OuterTune
                val density = LocalDensity.current
                val verticalPadding = max(
                    WindowInsets.systemBars.getTop(density),
                    WindowInsets.systemBars.getBottom(density)
                )
                val verticalPaddingDp = with(density) { verticalPadding.toDp() }
                val verticalWindowInsets = WindowInsets(left = 0.dp, top = verticalPaddingDp, right = 0.dp, bottom = verticalPaddingDp)
                
                Row(
                    modifier = Modifier
                        .windowInsetsPadding(
                            WindowInsets.systemBars.only(WindowInsetsSides.Horizontal).add(verticalWindowInsets)
                        )
                        .padding(bottom = 24.dp)
                        .fillMaxSize()
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .nestedScroll(state.preUpPostDownNestedScrollConnection)
                    ) {
                        // Remember lambdas to prevent unnecessary recomposition
                        val currentSliderPosition by rememberUpdatedState(sliderPosition)
                        val sliderPositionProvider = remember { { currentSliderPosition } }
                        val isExpandedProvider = remember(state) { { state.isExpanded } }
                        AnimatedContent(
                            targetState = showInlineLyrics,
                            label = "Lyrics",
                            transitionSpec = {
                                (fadeIn(spring(dampingRatio = 0.82f, stiffness = 550f)) +
                                    scaleIn(initialScale = 0.96f, animationSpec = spring(dampingRatio = 0.78f, stiffness = 550f)))
                                    .togetherWith(
                                        fadeOut(spring(dampingRatio = 1.0f, stiffness = 650f)) +
                                        scaleOut(targetScale = 0.96f, animationSpec = spring(dampingRatio = 1.0f, stiffness = 650f))
                                    )
                            }
                        ) { showLyrics ->
                            if (showLyrics) {
                                InlineLyricsView(
                                    mediaMetadata = mediaMetadata,
                                    showLyrics = showLyrics,
                                    positionProvider = { effectivePosition }
                                )
                            } else {
                                Thumbnail(
                                    sliderPositionProvider = sliderPositionProvider,
                                    modifier = Modifier.animateContentSize(),
                                    isPlayerExpanded = isExpandedProvider,
                                    isLandscape = true,
                                    isListenTogetherGuest = isListenTogetherGuest
                                )
                            }
                        }
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(if (showInlineLyrics) 0.65f else 1f, false)
                            .animateContentSize()
                            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                    ) {
                        Spacer(Modifier.weight(1f))

                        mediaMetadata?.let {
                            controlsContent(it)
                        }

                        Spacer(Modifier.weight(1f))
                    }
                }
            }

            else -> {
                val bottomPadding by animateDpAsState(
                    targetValue = if (isFullScreen) 0.dp else queueSheetState.collapsedBound,
                    label = "bottomPadding"
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier =
                    Modifier
                        .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top))
                        .padding(bottom = bottomPadding)
                        .animateContentSize(),
                ) {
                    // Controls normally render below the lyrics/thumbnail area.
                    // While fullscreen lyrics are showing with the "collapse
                    // top" setting on, they move above it instead of hiding.
                    val controlsAtTop = isFullScreen && fullscreenLyricsCollapseTop
                    if (controlsAtTop) {
                        mediaMetadata?.let {
                            controlsContent(it)
                        }
                    }

                    Box(
                        contentAlignment = Alignment.Center,
                        // Registers this box's on-screen bounds for
                        // PlayerArtworkMorphOverlay -- the standard (portrait,
                        // non-landscape) player only; see the mini player's own
                        // registerMiniArtworkRect() call for the other end.
                        // The morph's target rect is registered on the artwork ITSELF, down
                        // in Thumbnail -- this column is far taller than the cover, and
                        // flying the cover into these bounds landed it too big and too high.
                        modifier = Modifier.weight(1f),
                    ) {
                        // Remember lambdas to prevent unnecessary recomposition
                        val currentSliderPosition by rememberUpdatedState(sliderPosition)
                        val sliderPositionProvider = remember { { currentSliderPosition } }
                        val isExpandedProvider = remember(state) { { state.isExpanded } }
                        AnimatedContent(
                            targetState = showInlineLyrics,
                            label = "Lyrics",
                            transitionSpec = {
                                (fadeIn(spring(dampingRatio = 0.82f, stiffness = 550f)) +
                                    scaleIn(initialScale = 0.96f, animationSpec = spring(dampingRatio = 0.78f, stiffness = 550f)))
                                    .togetherWith(
                                        fadeOut(spring(dampingRatio = 1.0f, stiffness = 650f)) +
                                        scaleOut(targetScale = 0.96f, animationSpec = spring(dampingRatio = 1.0f, stiffness = 650f))
                                    )
                            }
                        ) { showLyrics ->
                            if (showLyrics) {
                                InlineLyricsView(
                                    mediaMetadata = mediaMetadata,
                                    showLyrics = showLyrics,
                                    positionProvider = { effectivePosition }
                                )
                            } else {
                                Thumbnail(
                                    sliderPositionProvider = sliderPositionProvider,
                                    modifier = Modifier.nestedScroll(state.preUpPostDownNestedScrollConnection),
                                    isPlayerExpanded = isExpandedProvider,
                                    isListenTogetherGuest = isListenTogetherGuest
                                )
                            }
                        }
                    }

                    if (!controlsAtTop) {
                        mediaMetadata?.let {
                            controlsContent(it)
                        }
                    }

                    Spacer(Modifier.height(if (useNewPlayerDesign) 30.dp else 8.dp))
                }
            }
        }

        AnimatedVisibility(
            visible = !isFullScreen,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Top) + slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            // Tinted from the current song's own artwork (same rememberHeroTint
            // used for search-result heroes elsewhere), not a fixed theme color.
            // Always used, even with pureBlack on — that's an app-wide OLED
            // fallback for when there's nothing else to tint from, but here
            // there always is (the current song's own art).
            val queueBackground = rememberHeroTint(mediaMetadata?.thumbnailUrl)
            Queue(
                state = queueSheetState,
                playerBottomSheetState = state,
            navController = navController,
            background = queueBackground,
            onBackgroundColor = onBackgroundColor,
            // Same adaptive-contrast pattern as the playlist screens' onTint —
            // computed from the queue's own song-tinted background, not the
            // player-wide TextBackgroundColor (which tracks playerBackground
            // style instead and can clash with an arbitrary song tint).
            TextBackgroundColor = AppleTokens.onColor(queueBackground),
            textButtonColor = textButtonColor,
            iconButtonColor = iconButtonColor,
            pureBlack = pureBlack,
            showInlineLyrics = showInlineLyrics,
            playerBackground = playerBackground,
            onToggleLyrics = {
                showInlineLyrics = !showInlineLyrics
                // Off (default): classic two-tap — this only opens/closes inline
                // lyrics, a separate tap on the fullscreen button is still needed.
                // On: this same tap opens AND fullscreens lyrics together, and
                // closing lyrics here also exits fullscreen in the same tap.
                if (oneTapFullscreenLyrics) isFullScreen = showInlineLyrics
            },
            )
        }

        // The z >= 0 sticker band now renders from BottomSheet's overlayContent slot
        // instead of here -- see that call site's comment. It doesn't need this Box's
        // own bounds the way the z < 0 band still does.
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun InlineLyricsView(
    mediaMetadata: MediaMetadata?,
    showLyrics: Boolean,
    positionProvider: () -> Long
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val currentLyrics by playerConnection.currentLyrics.collectAsStateWithLifecycle(initialValue =null)
    val lyrics = remember(currentLyrics) { currentLyrics?.lyrics?.trim() }
    val context = LocalContext.current
    val database = LocalDatabase.current
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(mediaMetadata?.id, currentLyrics) {
        if (mediaMetadata != null && currentLyrics == null) {
            delay(500)
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val entryPoint = EntryPointAccessors.fromApplication(
                        context.applicationContext,
                        com.convx.music.di.LyricsHelperEntryPoint::class.java
                    )
                    val lyricsHelper = entryPoint.lyricsHelper()
                    val fetchedLyricsWithProvider = lyricsHelper.getLyrics(mediaMetadata)
                    database.query {
                        upsert(LyricsEntity(mediaMetadata.id, fetchedLyricsWithProvider.lyrics, fetchedLyricsWithProvider.provider))
                    }
                } catch (e: Exception) {
                    // Handle error
                }
            }
        }
    }

    Box (
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        when {
            lyrics == null -> {
                ContainedLoadingIndicator()
            }
            lyrics == LyricsEntity.LYRICS_NOT_FOUND -> {
                Text(
                    text = stringResource(R.string.lyrics_not_found),
                    style = AppTheme.typography.bodyMedium,
                    color = AppTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }
            else -> {
                val lyricsContent: @Composable () -> Unit = {
                    Lyrics(
                        sliderPositionProvider = positionProvider,
                        modifier = Modifier.padding(horizontal = 24.dp),
                        showLyrics = showLyrics
                    )
                }
                ProvideTextStyle(
                    value = AppTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                ) {
                    lyricsContent()
                }
            }
        }
    }
}


@Composable
fun MoreActionsButton(
    mediaMetadata: MediaMetadata,
    navController: NavController,
    state: BottomSheetState,
    textButtonColor: Color,
    iconButtonColor: Color
) {
    val menuState = LocalMenuState.current
    val bottomSheetPageState = LocalBottomSheetPageState.current

    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(textButtonColor)
            .clickable {
                menuState.show {
                    PlayerMenu(
                        mediaMetadata = mediaMetadata,
                        navController = navController,
                        playerBottomSheetState = state,
                        onShowDetailsDialog = {
                            mediaMetadata.id.let {
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
        // Routed through PlayerGlyph like every other control: this one drew the stock
        // glyph straight from resources, so the More slot in player-icon settings did
        // nothing here even though the sibling PlayerMoreMenuButton honoured it.
        PlayerGlyph(
            slot = PlayerIconSlot.MORE,
            fallback = R.drawable.more_horiz,
            tint = iconButtonColor,
        )
    }
}

@Composable
private fun PlayerMoreMenuButton(
    mediaMetadata: MediaMetadata,
    navController: NavController,
    state: BottomSheetState,
    textButtonColor: Color,
    iconButtonColor: Color,
) {
    val menuState = LocalMenuState.current
    val bottomSheetPageState = LocalBottomSheetPageState.current

    Box(
        contentAlignment = Alignment.Center,
        modifier =
        Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(textButtonColor)
            .clickable {
                menuState.show {
                    PlayerMenu(
                        mediaMetadata = mediaMetadata,
                        navController = navController,
                        playerBottomSheetState = state,
                        onShowDetailsDialog = {
                            mediaMetadata.id.let {
                                bottomSheetPageState.show {
                                    ShowMediaInfo(it)
                                }
                            }
                        },
                        onDismiss = menuState::dismiss,
                    )
                }
            },
    ) {
        // PlayerGlyph, not a raw Image with an unconditional tint: a user's own
        // image for this slot was being flattened to a solid colour regardless of
        // the slot's per-icon tint flag, so a photo came out as a coloured blob.
        PlayerGlyph(
            slot = PlayerIconSlot.MORE,
            fallback = R.drawable.more_horiz,
            tint = iconButtonColor,
        )
    }
}

@Composable
private fun BackgroundVideoView(
    mediaId: String,
    videoUrl: String,
    isPlaying: Boolean,
    onError: () -> Unit = {},
    onReady: () -> Unit = {},
    modifier: Modifier = Modifier,
    // Polled into periodically below so glass surfaces sampling this video's
    // backdrop can cache their blur/lens output per loop-position bucket
    // instead of recomputing every frame of every loop — see VideoLoopClock.
    loopClock: VideoLoopClock? = null,
) {
    val context = LocalContext.current
    val downloadUtil = LocalDownloadUtil.current
    var isVideoReady by remember(videoUrl) { mutableStateOf(false) }
    // exoPlayer is remember{}'d without a key (reused across videoUrl changes,
    // swapped via setMediaItem instead of recreation), so the listener set up
    // in DisposableEffect(exoPlayer) below is only installed once — these
    // keep it calling through to the latest onError/onReady instead of the
    // ones captured at that first install.
    val currentOnError by rememberUpdatedState(onError)
    val currentOnReady by rememberUpdatedState(onReady)

    val trackSelector = remember {
        DefaultTrackSelector(context).apply {
            parameters = buildUponParameters()
                .setMaxVideoSize(4096, 4096)
                .setForceHighestSupportedBitrate(true)
                .build()
        }
    }

    val mediaSourceFactory = remember<DefaultMediaSourceFactory>(downloadUtil) {
        DefaultMediaSourceFactory(
            CacheDataSource.Factory()
                .setCache(downloadUtil.downloadCache)
                .setUpstreamDataSourceFactory(DefaultDataSource.Factory(context))
                .setCacheWriteDataSinkFactory(null)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR),
            androidx.media3.extractor.DefaultExtractorsFactory()
        )
    }

    val exoPlayer = remember {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setTrackSelector(trackSelector)
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setTargetBufferBytes(20 * 1024 * 1024) // 20MB buffer for 4K
                    .build()
            )
            .build().apply {
                repeatMode = Player.REPEAT_MODE_ONE
                volume = 0f
                videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
                playWhenReady = isPlaying
            }
    }

    if (loopClock != null) {
        LaunchedEffect(exoPlayer, loopClock) {
            while (isActive) {
                loopClock.update(exoPlayer.currentPosition, exoPlayer.duration)
                delay(VideoLoopBucketMs)
            }
        }
    }

    val aspectRatioFrameLayout = remember {
        AspectRatioFrameLayout(context).apply {
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    aspectRatioFrameLayout.setAspectRatio(videoSize.width.toFloat() / videoSize.height)
                }
            }
            override fun onRenderedFirstFrame() {
                isVideoReady = true
                currentOnReady()
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                println("BackgroundVideoView: E: failed to play '$videoUrl': ${error.errorCodeName} ${error.message}")
                error.printStackTrace()
                currentOnError()
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    LaunchedEffect(videoUrl, mediaId) {
        println("BackgroundVideoView: D: loading '$videoUrl'")
        isVideoReady = false
        val mediaItem = MediaItem.Builder()
            .setUri(videoUrl)
            .setCustomCacheKey("$mediaId#canvas")
            .setMimeType(if (videoUrl.contains("m3u8")) MimeTypes.APPLICATION_M3U8 else MimeTypes.VIDEO_MP4)
            .build()
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
    }

    LaunchedEffect(isPlaying) {
        exoPlayer.playWhenReady = isPlaying
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (isVideoReady) 1f else 0f,
        animationSpec = tween(800),
        label = "videoAlpha"
    )

    AndroidView(
        factory = { _ ->
            aspectRatioFrameLayout.apply {
                // Ensure the view doesn't capture touches intended for other sections
                isEnabled = false
                isClickable = false
                isFocusable = false

                // Ensure TextureView is added only once
                if (childCount == 0) {
                    val textureView = TextureView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                    }
                    addView(textureView)
                    exoPlayer.setVideoTextureView(textureView)
                }
            }
        },
        modifier = modifier.graphicsLayer { this.alpha = alpha }
    )
}




