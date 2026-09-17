/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import com.convx.music.ui.utils.rememberEdgeAwareFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import com.convx.music.ui.ios.compat.Icon
import androidx.compose.material3.MaterialShapes
import com.convx.music.ui.ios.compat.AppTheme
import com.convx.music.ui.ios.compat.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.drop
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.graphics.Bitmap
import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.core.graphics.createBitmap
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.size.Size as CoilSize
import coil3.toBitmap
import com.convx.music.LocalListenTogetherManager
import com.convx.music.LocalPlayerConnection
import com.convx.music.R
import com.convx.music.constants.CropAlbumArtKey
import com.convx.music.constants.HidePlayerThumbnailKey
import com.convx.music.constants.PlayerBackgroundStyle
import com.convx.music.constants.PlayerBackgroundStyleKey
import com.convx.music.constants.PlayerHorizontalPadding
import com.convx.music.ui.component.shapes.ContinuousRoundedRectangle
import com.convx.music.constants.PlayerArtworkStyle
import com.convx.music.constants.PlayerArtworkStyleKey
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import com.convx.music.constants.SeekExtraSeconds
import com.convx.music.constants.SwipeThumbnailKey
import com.convx.music.constants.ThumbnailCornerRadiusKey
import com.convx.music.constants.ThumbnailCornerRadius
import com.convx.music.listentogether.RoomRole
import com.convx.music.ui.component.CastButton
import com.convx.music.utils.rememberEnumPreference
import com.convx.music.constants.CanvasSource
import com.convx.music.constants.CanvasSourceKey
import com.convx.music.constants.CanvasThumbnailAnimationKey
import com.convx.music.constants.DataSaverEnabledKey
import com.convx.music.canvas.TidalCanvasProvider
import com.convx.music.canvas.CanvasArtwork
import com.convx.music.extensions.metadata
import com.convx.music.ui.utils.resize
import com.convx.music.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import com.convx.music.applecanvas.AppleMusicCanvasProvider
import com.convx.music.vivimusiccanvas.ViviMusicCanvasProvider
import com.convx.music.vivimusiccanvas.EchoMusicCanvasProvider
import java.util.Locale

/**
 * Pre-calculated thumbnail dimensions to avoid repeated calculations during recomposition.
 * All values are computed once and cached.
 */
@Immutable
data class ThumbnailDimensions(
    val itemWidth: Dp,
    val containerSize: Dp,
    val thumbnailSize: Dp,
    val cornerRadius: Dp
)

/**
 * Cached media items data to prevent recalculation on every recomposition.
 */
@Immutable
data class MediaItemsData(
    val items: List<MediaItem>,
    val currentIndex: Int
)

/**
 * Calculate thumbnail dimensions once based on container size.
 * This function is marked as @Stable to indicate it produces stable results.
 * In landscape mode, uses the smaller dimension (height) to ensure square thumbnail fits.
 */
@Stable
private fun calculateThumbnailDimensions(
    containerWidth: Dp,
    containerHeight: Dp = containerWidth,
    horizontalPadding: Dp = PlayerHorizontalPadding,
    cornerRadius: Dp = ThumbnailCornerRadius,
    isLandscape: Boolean = false
): ThumbnailDimensions {
    // In landscape, use height as the constraining dimension for a square thumbnail
    val effectiveSize = if (isLandscape) {
        minOf(containerWidth, containerHeight) - (horizontalPadding * 2)
    } else {
        containerWidth - (horizontalPadding * 2)
    }
    return ThumbnailDimensions(
        itemWidth = containerWidth,
        containerSize = containerWidth,
        thumbnailSize = effectiveSize,
        cornerRadius = cornerRadius * 2
    )
}

/**
 * Get media items for the thumbnail carousel.
 * Calculates previous, current, and next items based on shuffle mode.
 */
@Stable
private fun getMediaItems(
    player: Player,
    swipeThumbnail: Boolean
): MediaItemsData {
    val timeline = player.currentTimeline
    val currentIndex = player.currentMediaItemIndex
    val shuffleModeEnabled = player.shuffleModeEnabled
    
    val currentMediaItem = try {
        player.currentMediaItem
    } catch (e: Exception) { null }
    
    val previousMediaItem = if (swipeThumbnail && !timeline.isEmpty) {
        val previousIndex = timeline.getPreviousWindowIndex(
            currentIndex,
            Player.REPEAT_MODE_OFF,
            shuffleModeEnabled
        )
        if (previousIndex != C.INDEX_UNSET) {
            try { player.getMediaItemAt(previousIndex) } catch (e: Exception) { null }
        } else null
    } else null

    val nextMediaItem = if (swipeThumbnail && !timeline.isEmpty) {
        val nextIndex = timeline.getNextWindowIndex(
            currentIndex,
            Player.REPEAT_MODE_OFF,
            shuffleModeEnabled
        )
        if (nextIndex != C.INDEX_UNSET) {
            try { player.getMediaItemAt(nextIndex) } catch (e: Exception) { null }
        } else null
    } else null

    val items = listOfNotNull(previousMediaItem, currentMediaItem, nextMediaItem)
    val currentMediaIndex = items.indexOf(currentMediaItem)
    
    return MediaItemsData(items, currentMediaIndex)
}

/**
 * Get text color based on player background style.
 * Computed once per background style change.
 */
@Stable
@Composable
private fun getTextColor(playerBackground: PlayerBackgroundStyle): Color {
    return when (playerBackground) {
        PlayerBackgroundStyle.DEFAULT -> AppTheme.colorScheme.onBackground
        PlayerBackgroundStyle.BLUR, PlayerBackgroundStyle.GRADIENT, PlayerBackgroundStyle.GLOW_ANIMATED, PlayerBackgroundStyle.APPLE_MUSIC, PlayerBackgroundStyle.LIVE_MESH, PlayerBackgroundStyle.BITCHORD_MESH, PlayerBackgroundStyle.STATIC, PlayerBackgroundStyle.CUSTOM_GRADIENT, PlayerBackgroundStyle.GLASS -> Color.White
    }
}


object CanvasArtworkPlaybackCache {
    private const val defaultMaxSize = 256
    private val map = LinkedHashMap<String, CanvasArtwork>(defaultMaxSize, 0.75f, true)
    @Volatile private var maxSize = defaultMaxSize

    @Synchronized
    fun get(mediaId: String): CanvasArtwork? {
        if (maxSize <= 0) return null
        return map[mediaId]
    }

    @Synchronized
    fun put(mediaId: String, artwork: CanvasArtwork) {
        val limit = maxSize
        if (limit <= 0) return
        if (mediaId.isBlank()) return
        map[mediaId] = artwork
        while (map.size > limit) {
            val it = map.entries.iterator()
            if (it.hasNext()) {
                it.next()
                it.remove()
            }
        }
    }

    @Synchronized
    fun clear() {
        map.clear()
    }

    @Synchronized
    fun setMaxSize(value: Int) {
        maxSize = value.coerceAtLeast(0)
        if (maxSize == 0) {
            map.clear()
            return
        }
        while (map.size > maxSize) {
            val it = map.entries.iterator()
            if (it.hasNext()) {
                it.next()
                it.remove()
            } else {
                break
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Thumbnail(
    sliderPositionProvider: () -> Long?,
    modifier: Modifier = Modifier,
    isPlayerExpanded: () -> Boolean = { true },
    isLandscape: Boolean = false,
    isListenTogetherGuest: Boolean = false,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val context = LocalContext.current
    val layoutDirection = LocalLayoutDirection.current

    // Collect states
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val error by playerConnection.error.collectAsState()
    val queueTitle by playerConnection.queueTitle.collectAsState()
    val canSkipPrevious by playerConnection.canSkipPrevious.collectAsState()
    val canSkipNext by playerConnection.canSkipNext.collectAsState()

    // Preferences - computed once
    // Disable swipe for Listen Together guests
    val swipeThumbnailPref by rememberPreference(SwipeThumbnailKey, true)
    val swipeThumbnail = swipeThumbnailPref && !isListenTogetherGuest
    val hidePlayerThumbnail by rememberPreference(HidePlayerThumbnailKey, false)
    val cropAlbumArt by rememberPreference(CropAlbumArtKey, false)
    val playerBackground by rememberEnumPreference(
        key = PlayerBackgroundStyleKey,
        defaultValue = PlayerBackgroundStyle.APPLE_MUSIC
    )
    val thumbnailCornerRadius by rememberPreference(ThumbnailCornerRadiusKey, defaultValue = 3f)
    
    // Pre-calculate text color based on background style
    val textBackgroundColor = getTextColor(playerBackground)
    
    // Grid state
    val thumbnailLazyGridState = rememberLazyGridState()
    
    // Calculate media items data - memoized
    val mediaItemsData by remember(
        playerConnection.player.currentMediaItemIndex,
        playerConnection.player.shuffleModeEnabled,
        swipeThumbnail,
        mediaMetadata
    ) {
        derivedStateOf {
            getMediaItems(playerConnection.player, swipeThumbnail)
        }
    }
    
    val mediaItems = mediaItemsData.items
    val currentMediaIndex = mediaItemsData.currentIndex

    // Snap behavior - created once per grid state
    val thumbnailSnapLayoutInfoProvider = remember(thumbnailLazyGridState) {
        ThumbnailSnapLayoutInfoProvider(
            lazyGridState = thumbnailLazyGridState,
            positionInLayout = { layoutSize, itemSize ->
                (layoutSize / 2f - itemSize / 2f)
            },
            velocityThreshold = 500f
        )
    }

    // Guards the swipe detector below from mistaking the position-resync
    // scroll (also below) for a user swipe — that scroll also flips
    // isScrollInProgress, which is otherwise indistinguishable from a drag.
    var isSyncingPosition by remember { mutableStateOf(false) }

    // Handle swipe to change song. Watch the scroll end (snapshotFlow) instead of
    // keying on the scroll offset, so per-pixel scroll changes don't recompose
    // the whole thumbnail carousel.
    LaunchedEffect(swipeThumbnail, currentMediaIndex, canSkipNext, canSkipPrevious) {
        if (!swipeThumbnail || currentMediaIndex < 0) return@LaunchedEffect
        // drop(1): this effect restarts every time currentMediaIndex changes,
        // and snapshotFlow replays the CURRENT isScrollInProgress value
        // immediately on collection start — which is stale grid state from
        // before the index changed (the resync scroll below hasn't started
        // yet). Comparing that stale position against the new index misread
        // as a user swipe and fired a spurious seek, which changed the index
        // again, restarting this effect again — an infinite ping-pong at
        // queue boundaries (index 0/1) where the carousel window is 2 items
        // instead of 3. Only react to an actual transition, not the replay.
        snapshotFlow { thumbnailLazyGridState.isScrollInProgress }
            .drop(1)
            .collect { scrolling ->
                if (!scrolling && !isSyncingPosition && thumbnailLazyGridState.firstVisibleItemScrollOffset == 0) {
                    val index = thumbnailLazyGridState.firstVisibleItemIndex
                    if (index > currentMediaIndex && canSkipNext) {
                        playerConnection.player.seekToNext()
                    } else if (index < currentMediaIndex && canSkipPrevious) {
                        playerConnection.player.seekToPreviousMediaItem()
                    }
                }
            }
    }

    // Update position when song changes
    LaunchedEffect(mediaMetadata, canSkipPrevious, canSkipNext) {
        val index = maxOf(0, currentMediaIndex)
        if (index >= 0 && index < mediaItems.size) {
            isSyncingPosition = true
            try {
                thumbnailLazyGridState.animateScrollToItem(index)
            } catch (e: Exception) {
                thumbnailLazyGridState.scrollToItem(index)
            } finally {
                isSyncingPosition = false
            }
        }
    }

    // Prefetch high-quality thumbnails for the carousel items (previous, current, and next)
    LaunchedEffect(mediaItems) {
        mediaItems.forEach { item ->
            val artworkUri = item.mediaMetadata.artworkUri?.toString()?.resize(1200, 1200) ?: return@forEach
            val request = ImageRequest.Builder(context)
                .data(artworkUri)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .build()
            SingletonImageLoader.get(context).enqueue(request)
        }
    }

    // Seek effect state
    var showSeekEffect by remember { mutableStateOf(false) }
    var seekDirection by remember { mutableStateOf("") }

    Box(
        modifier = modifier
    ) {
        // Error view
        AnimatedVisibility(
            visible = error != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .padding(32.dp)
                .align(Alignment.Center),
        ) {
            error?.let { playbackError ->
                PlaybackError(
                    error = playbackError,
                    retry = playerConnection.player::prepare,
                )
            }
        }

        // Main thumbnail view
        AnimatedVisibility(
            visible = error == null && !(playerBackground == PlayerBackgroundStyle.APPLE_MUSIC && !isLandscape),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .fillMaxSize()
                .then(if (!isLandscape) Modifier.statusBarsPadding() else Modifier),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = if (isLandscape) Arrangement.Center else Arrangement.Top
            ) {
                // Now Playing header - hide in landscape mode
                if (!isLandscape) {
                    ThumbnailHeader(
                        queueTitle = queueTitle,
                        albumTitle = mediaMetadata?.album?.title,
                        textColor = textBackgroundColor
                    )
                }
                
                // Thumbnail content
                BoxWithConstraints(
                    contentAlignment = Alignment.Center,
                    modifier = if (isLandscape) {
                        Modifier.weight(1f, false)
                    } else {
                        Modifier.fillMaxSize()
                    }
                ) {
                    // Calculate dimensions once per size change, considering landscape mode
                    val dimensions = remember(maxWidth, maxHeight, isLandscape, thumbnailCornerRadius) {
                        calculateThumbnailDimensions(
                            containerWidth = maxWidth,
                            containerHeight = maxHeight,
                            cornerRadius = thumbnailCornerRadius.dp,
                            isLandscape = isLandscape
                        )
                    }

                    // Remember the onSeek callback to prevent recomposition
                    val onSeekCallback = remember {
                        { direction: String, showEffect: Boolean ->
                            seekDirection = direction
                            showSeekEffect = showEffect
                        }
                    }
                    
                    // Derive scroll enabled state to prevent unnecessary recomposition
                    val isScrollEnabled by remember(swipeThumbnail) {
                        derivedStateOf { swipeThumbnail && isPlayerExpanded() }
                    }
                    
                    LazyHorizontalGrid(
                        state = thumbnailLazyGridState,
                        rows = GridCells.Fixed(1),
                        flingBehavior = rememberEdgeAwareFlingBehavior(
                            thumbnailLazyGridState,
                            rememberSnapFlingBehavior(thumbnailSnapLayoutInfoProvider),
                        ),
                        userScrollEnabled = isScrollEnabled,
                        modifier = if (isLandscape) {
                            Modifier.size(dimensions.thumbnailSize + (PlayerHorizontalPadding * 2))
                        } else {
                            Modifier.fillMaxSize()
                        }
                    ) {
                        items(
                            items = mediaItems,
                            key = { item -> 
                                item.mediaId.ifEmpty { "unknown_${item.hashCode()}" }
                            }
                        ) { item ->
                            ThumbnailItem(
                                item = item,
                                dimensions = dimensions,
                                hidePlayerThumbnail = hidePlayerThumbnail,
                                cropAlbumArt = cropAlbumArt,
                                textBackgroundColor = textBackgroundColor,
                                layoutDirection = layoutDirection,
                                onSeek = onSeekCallback,
                                playerConnection = playerConnection,
                                context = context,
                                isLandscape = isLandscape,
                                isListenTogetherGuest = isListenTogetherGuest,
                                currentMediaId = mediaMetadata?.id,
                                currentMediaThumbnail = mediaMetadata?.thumbnailUrl,
                                playerBackground = playerBackground
                            )
                }
            }
        }

    }
}

        // Seek effect
        LaunchedEffect(showSeekEffect) {
            if (showSeekEffect) {
                delay(1000)
                showSeekEffect = false
            }
        }

        AnimatedVisibility(
            visible = showSeekEffect,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            SeekEffectOverlay(seekDirection = seekDirection)
        }
    }
}

/**
 * Header component showing "Now Playing" and queue/album title.
 */
@Composable
private fun ThumbnailHeader(
    queueTitle: String?,
    albumTitle: String?,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    val listenTogetherManager = LocalListenTogetherManager.current
    val listenTogetherRoleState = listenTogetherManager?.role?.collectAsState(initial = RoomRole.NONE)
    // See Player.kt: gated on control mode, not on role.
    val canControlTogether = listenTogetherManager?.canControl?.collectAsState(initial = true)
    val isListenTogetherGuest = canControlTogether?.value == false
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 48.dp)
        ) {
            // Listen Together indicator
            if (listenTogetherRoleState?.value != RoomRole.NONE) {
                Text(
                    text = if (listenTogetherRoleState?.value == RoomRole.HOST) "Hosting Listen Together" else "Listening Together",
                    style = AppTheme.typography.titleMedium,
                    color = textColor
                )
            } else {
                Text(
                    text = stringResource(R.string.now_playing),
                    style = AppTheme.typography.titleMedium,
                    color = textColor
                )
            }
            val playingFrom = albumTitle ?: queueTitle // Prioritize album title
            androidx.compose.animation.AnimatedContent(
                targetState = playingFrom,
                transitionSpec = { androidx.compose.animation.fadeIn() togetherWith androidx.compose.animation.fadeOut() },
                label = "NowPlayingAnimation"
            ) { text ->
                if (!text.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = text,
                        style = AppTheme.typography.titleMedium,
                        color = textColor.copy(alpha = 0.8f),
                        maxLines = 1,
                        modifier = Modifier.basicMarquee()
                    )
                }
            }
        }

        CastButton(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(24.dp),
            tintColor = textColor
        )
    }
}

/**
 * Individual thumbnail item in the carousel.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ThumbnailItem(
    item: MediaItem,
    dimensions: ThumbnailDimensions,
    hidePlayerThumbnail: Boolean,
    cropAlbumArt: Boolean,
    textBackgroundColor: Color,
    layoutDirection: LayoutDirection,
    onSeek: (String, Boolean) -> Unit,
    playerConnection: com.convx.music.playback.PlayerConnection,
    context: android.content.Context,
    isLandscape: Boolean = false,
    isListenTogetherGuest: Boolean = false,
    currentMediaId: String? = null,
    currentMediaThumbnail: String? = null,
    playerBackground: PlayerBackgroundStyle = PlayerBackgroundStyle.DEFAULT,
    modifier: Modifier = Modifier,
) {
    val (artworkStyle) = rememberEnumPreference(PlayerArtworkStyleKey, defaultValue = PlayerArtworkStyle.CARD)
    val rotatingThumbnail = artworkStyle != PlayerArtworkStyle.CARD
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val isCurrentItem = item.mediaId == currentMediaId

    // An infiniteRepeatable never finishes, so pinning targetValue to 0f did not
    // stop this — the transition kept awaiting a frame forever and redrew the
    // whole app every vsync whenever the player was on screen. Only create it
    // while the artwork is actually spinning.
    val rotation: State<Float> = if (isPlaying && rotatingThumbnail && isCurrentItem) {
        val infiniteTransition = rememberInfiniteTransition(label = "ThumbnailRotation")
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(20000, easing = LinearEasing),
                repeatMode = androidx.compose.animation.core.RepeatMode.Restart
            ),
            label = "Rotation"
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    val incrementalSeekSkipEnabled by rememberPreference(SeekExtraSeconds, defaultValue = false)
    var skipMultiplier by remember { mutableIntStateOf(1) }
    var lastTapTime by remember { mutableLongStateOf(0L) }

    val dataSaverEnabled by rememberPreference(DataSaverEnabledKey, defaultValue = false)
    val canvasThumbnailAnimationPref by rememberPreference(CanvasThumbnailAnimationKey, defaultValue = true)
    val canvasThumbnailAnimation = if (dataSaverEnabled) false else canvasThumbnailAnimationPref

    Box(
        modifier = modifier
            .then(
                if (isLandscape) {
                    Modifier.size(dimensions.thumbnailSize + (PlayerHorizontalPadding * 2))
                } else {
                    Modifier
                        .width(dimensions.itemWidth)
                        .fillMaxSize()
                }
            )
            .padding(horizontal = PlayerHorizontalPadding)
            .graphicsLayer {
                // Render entire thumbnail item on separate hardware layer for smooth animations
                // Offscreen compositing removed to prevent interference with AndroidView
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { offset ->
                        if (isListenTogetherGuest) return@detectTapGestures

                        val currentPosition = playerConnection.player.currentPosition
                        val duration = playerConnection.player.duration

                        val now = System.currentTimeMillis()
                        if (incrementalSeekSkipEnabled && now - lastTapTime < 1000) {
                            skipMultiplier++
                        } else {
                            skipMultiplier = 1
                        }
                        lastTapTime = now

                        val skipAmount = 5000 * skipMultiplier

                        val isLeftSide = (layoutDirection == LayoutDirection.Ltr && offset.x < size.width / 2) ||
                                (layoutDirection == LayoutDirection.Rtl && offset.x > size.width / 2)

                        if (isLeftSide) {
                            playerConnection.player.seekTo((currentPosition - skipAmount).coerceAtLeast(0))
                            onSeek(context.getString(R.string.seek_backward_dynamic, skipAmount / 1000), true)
                        } else {
                            playerConnection.player.seekTo((currentPosition + skipAmount).coerceAtMost(duration))
                            onSeek(context.getString(R.string.seek_forward_dynamic, skipAmount / 1000), true)
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        val artworkDensity = androidx.compose.ui.platform.LocalDensity.current
        Box(
            modifier = Modifier
                .size(dimensions.thumbnailSize)
                // Target end of the mini-to-full cover morph, registered on the node that
                // KNOWS the shape: a vinyl or clover is a circle for morph purposes (half
                // its size), a card is its own corner radius. Also the copy that has to be
                // hidden while the travelling one is in flight, or the cover is on screen
                // twice from the handoff onward.
                .registerFullArtworkRect(
                    with(artworkDensity) {
                        when (artworkStyle) {
                            PlayerArtworkStyle.CARD -> dimensions.cornerRadius.toPx()
                            else -> dimensions.thumbnailSize.toPx() / 2f
                        }
                    }
                )
                // Q5: squared card travels (hidden all flight); vinyl/clover
                // resolve late in place behind the mini shrink+fade.
                .hideFullArtworkWhileMorphing(
                    travelEnabled = artworkStyle == PlayerArtworkStyle.CARD,
                )
                .graphicsLayer {
                    rotationZ = rotation.value
                }
                .clip(
                    when (artworkStyle) {
                        PlayerArtworkStyle.VINYL -> CircleShape
                        PlayerArtworkStyle.CLOVER -> MaterialShapes.Clover8Leaf.toShape()
                        // Continuous (squircle) corners, matching the rest of the chrome.
                        PlayerArtworkStyle.CARD -> ContinuousRoundedRectangle(dimensions.cornerRadius)
                    }
                )
                // A vinyl spins with its label, so only the clover shape counter-rotates.
                .graphicsLayer {
                    rotationZ = if (artworkStyle == PlayerArtworkStyle.VINYL) 0f else -rotation.value
                }
        ) {
            if (hidePlayerThumbnail) {
                HiddenThumbnailPlaceholder(textBackgroundColor = textBackgroundColor)
            } else {
                val artworkUriToUse = if (item.mediaId == currentMediaId && !currentMediaThumbnail.isNullOrBlank()) {
                    currentMediaThumbnail
                } else {
                    item.mediaMetadata.artworkUri?.toString()
                }

                val crossfadeDurationMs by playerConnection.crossfadeDurationMs.collectAsState()
                ThumbnailImage(
                    artworkUri = artworkUriToUse?.resize(1200, 1200),
                    cropArtwork = cropAlbumArt,
                    crossfadeMillis = crossfadeDurationMs.takeIf { isCurrentItem && it > 0 }?.toInt() ?: 200,
                )
            }
            
            if (artworkStyle == PlayerArtworkStyle.VINYL) {
                Canvas(Modifier.fillMaxSize()) {
                    val r = size.minDimension / 2f
                    val c = Offset(size.width / 2f, size.height / 2f)
                    // Black record body drawn as a ring, so the art stays visible
                    // in the middle as the vinyl's paper label.
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.82f),
                        radius = r * 0.67f,
                        center = c,
                        style = Stroke(width = r * 0.66f),
                    )
                    for (i in 1..7) {
                        drawCircle(
                            color = Color.White.copy(alpha = 0.05f),
                            radius = r * (0.40f + i * 0.083f),
                            center = c,
                            style = Stroke(width = 1.dp.toPx()),
                        )
                    }
                    drawCircle(Color.Black, radius = r * 0.055f, center = c)
                }
            }

            if (canvasThumbnailAnimation && item.mediaId == currentMediaId && !rotatingThumbnail && playerBackground != PlayerBackgroundStyle.APPLE_MUSIC) {
                val (canvasSource) = rememberEnumPreference(CanvasSourceKey, defaultValue = CanvasSource.AUTO)
                var canvasArtwork by remember(item.mediaId) { mutableStateOf<CanvasArtwork?>(null) }
                var canvasFetchInFlight by remember(item.mediaId) { mutableStateOf(false) }
                val storefront = remember {
                    val country = Locale.getDefault().country
                    if (country.length == 2) country.lowercase(Locale.ROOT) else "us"
                }

                LaunchedEffect(item.mediaId, canvasSource) {
                    val cacheKey = "${item.mediaId}:${canvasSource.name}"
                    CanvasArtworkPlaybackCache.get(cacheKey)?.let { cached ->
                        canvasArtwork = cached
                        return@LaunchedEffect
                    }

                    if (canvasFetchInFlight) return@LaunchedEffect
                    canvasFetchInFlight = true

                    val fetched = withContext(Dispatchers.IO) {
                        val metadata = item.metadata
                        val albumName = (metadata?.album?.title ?: item.mediaMetadata.albumTitle)?.toString()
                        val duration = metadata?.duration
                        
                        val songTitleRaw = item.mediaMetadata.title?.toString() ?: ""
                        val artistNameRaw = item.mediaMetadata.artist?.toString() ?: ""
                        
                        val songTitle = normalizeCanvasSongTitle(songTitleRaw)
                        val artistName = normalizeCanvasArtistName(artistNameRaw)
                        
                        println("CanvasFetch: Song='$songTitle' (raw='$songTitleRaw'), Artist='$artistName' (raw='$artistNameRaw'), Album='$albumName'")
                        
                        val searchTasks = listOf(
                            songTitleRaw to artistNameRaw
                        ).filter { (s, a) -> s.isNotBlank() && a.isNotBlank() }

                        when (canvasSource) {
                            CanvasSource.AUTO -> {
                                searchTasks.firstNotNullOfOrNull { (s, a) ->
                                    EchoMusicCanvasProvider.getBySongArtist(
                                        song = s,
                                        artist = a
                                    )?.takeIf { !it.preferredAnimationUrl.isNullOrBlank() }
                                        ?: AppleMusicCanvasProvider.getBySongArtist(
                                        song = s,
                                        artist = a,
                                        album = albumName,
                                        storefront = storefront
                                    )?.takeIf { !it.preferredAnimationUrl.isNullOrBlank() }
                                        ?: ViviMusicCanvasProvider.getBySongArtist(
                                            song = s,
                                            artist = a
                                        )?.takeIf { !it.preferredAnimationUrl.isNullOrBlank() }
                                        ?: TidalCanvasProvider.getBySongArtist(
                                            song = s,
                                            artist = a,
                                            album = albumName
                                        )?.takeIf { !it.preferredAnimationUrl.isNullOrBlank() }
                                }
                            }
                            CanvasSource.ECHO_MUSIC -> {
                                searchTasks.firstNotNullOfOrNull { (s, a) ->
                                    EchoMusicCanvasProvider.getBySongArtist(
                                        song = s,
                                        artist = a
                                    )?.takeIf { !it.preferredAnimationUrl.isNullOrBlank() }
                                }
                            }
                            CanvasSource.APPLE_MUSIC -> {
                                searchTasks.firstNotNullOfOrNull { (s, a) ->
                                    AppleMusicCanvasProvider.getBySongArtist(
                                        song = s,
                                        artist = a,
                                        album = albumName,
                                        storefront = storefront
                                    )?.takeIf { !it.preferredAnimationUrl.isNullOrBlank() }
                                }
                            }
                            CanvasSource.VIVIMUSIC -> {
                                searchTasks.firstNotNullOfOrNull { (s, a) ->
                                    ViviMusicCanvasProvider.getBySongArtist(
                                        song = s,
                                        artist = a
                                    )?.takeIf { !it.preferredAnimationUrl.isNullOrBlank() }
                                }
                            }
                            CanvasSource.TIDAL -> {
                                searchTasks.firstNotNullOfOrNull { (s, a) ->
                                    TidalCanvasProvider.getBySongArtist(
                                        song = s,
                                        artist = a,
                                        album = albumName
                                    )?.takeIf { !it.preferredAnimationUrl.isNullOrBlank() }
                                }
                            }
                        }
                    }
                    
                    // Client-side safety check: ensure the fetched canvas matches the requested song
                    // This prevents "wrong canvas" if a provider returned a generic or incorrect match
                    val requestedArtist = item.mediaMetadata.artist?.toString() ?: ""
                    val requestedTitle = item.mediaMetadata.title?.toString() ?: ""
                    
                    val validated = fetched?.let { artwork ->
                        val resultArtist = artwork.artist
                        val resultName = artwork.name
                        
                        // Check artist list overlaps
                        val artistMatches = if (resultArtist != null && requestedArtist.isNotBlank()) {
                            val requestedList = splitAndNormalizeArtists(requestedArtist)
                            val resultList = splitAndNormalizeArtists(resultArtist)
                            requestedList.any { req -> resultList.any { res -> res.contains(req) || req.contains(res) } }
                        } else true

                        // Check song title (raw comparison)
                        val songMatches = if (resultName != null && requestedTitle.isNotBlank()) {
                            resultName.trim().equals(requestedTitle.trim(), ignoreCase = true)
                        } else true
                        // Check album name (raw comparison)
                        val requestedAlbum = item.mediaMetadata.albumTitle?.toString() ?: ""
                        val canvasAlbumName = artwork.albumName

                        val albumMatches = if (canvasAlbumName != null && requestedAlbum.isNotBlank()) {
                            canvasAlbumName.trim().equals(requestedAlbum.trim(), ignoreCase = true)
                        } else false

                        val titleMatches = songMatches && albumMatches

                        println("CanvasValidation: artistMatches=$artistMatches, songMatches=$songMatches, albumMatches=$albumMatches")
                        println("  Requested: Title='$requestedTitle', Album='$requestedAlbum', Artists='$requestedArtist'")
                        println("  Returned: Title='$resultName', Album='$canvasAlbumName', Artists='$resultArtist'")

                        if (artistMatches && titleMatches) {
                            println("CanvasValidation: Match SUCCESS for '${artwork.name}'")
                            artwork
                        } else {
                            println("CanvasValidation: Match FAILED for '${artwork.name}' by '${artwork.artist}'")
                            null
                        }
                    }
                    
                    canvasArtwork = validated
                    if (validated != null) {
                        CanvasArtworkPlaybackCache.put("${item.mediaId}:${canvasSource.name}", validated)
                    }
                    canvasFetchInFlight = false
                }

                canvasArtwork?.let { artwork ->
                    CanvasArtworkPlayer(
                        primaryUrl = artwork.animated,
                        fallbackUrl = artwork.videoUrl,
                        isPlaying = isPlaying,
                        mediaId = item.mediaId,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

/**
 * Placeholder shown when thumbnail is hidden.
 */
@Composable
private fun HiddenThumbnailPlaceholder(
    textBackgroundColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.convx_logo),
            contentDescription = stringResource(R.string.hide_player_thumbnail),
            tint = textBackgroundColor.copy(alpha = 0.7f),
            modifier = Modifier.size(120.dp)
        )
    }
}

/** Mesh resolution for the API<33 ripple-warp fallback. */
private const val MESH_COLS = 8
private const val MESH_ROWS = 8

/** How far a mesh vertex moves at the peak of the ripple (fallback path). */
private val RIPPLE_MAX_DISPLACEMENT = 14.dp

/** How many ripple rings fit across the artwork's radius (fallback path). */
private const val RIPPLE_RINGS = 3.0

/** How many covers' worth of bitmaps this keeps around at once — just the
 *  outgoing and current cover, never recycled (letting GC reclaim them once
 *  the composable drops the reference is far safer than manually recycling a
 *  Bitmap a not-yet-recomposed frame might still be drawing). */
private const val MAX_CACHED_COVERS = 2

/**
 * Actual thumbnail image with caching and hardware layer rendering.
 *
 * A plain Coil crossfade is only an opacity dissolve — one cover fading over
 * the other, which reads as two flat images stacked rather than one turning
 * into the next. On API 33+ this instead runs an AGSL fragment shader
 * ([MELT_SHADER_SOURCE], ported from React Bits' MorphSlider "melt"
 * transition) that displaces both covers through a shared noise field and
 * reveals the incoming one where that same field crosses the transition
 * progress — an organic, uneven melt rather than a uniform fade — plus a
 * chromatic-aberration flicker that peaks mid-transition. Below API 33
 * ([RuntimeShader] doesn't exist yet) it falls back to a simpler geometric
 * ripple warp via [android.graphics.Canvas.drawBitmapMesh].
 *
 * ponytail: the noise field is content-agnostic, not a feature-correspondence
 * morph (à la face-morphing tools) — two unrelated album covers share no
 * structure for a correspondence-based warp to align to, so a shape-preserving
 * morph between them would mostly look like arbitrary distortion anyway.
 */
@Composable
internal fun ThumbnailImage(
    artworkUri: String?,
    cropArtwork: Boolean,
    modifier: Modifier = Modifier,
    // Matches the audio crossfade's own length right after a DJ handoff (see
    // PlayerConnection.crossfadeDurationMs); a short default the rest of the
    // time, so a manual skip/seek still feels snappy.
    crossfadeMillis: Int = 200,
) {
    val context = LocalContext.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val shaderMorphSupported = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU

    var boxSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    var currentUrl by remember { mutableStateOf(artworkUri) }
    var outgoingUrl by remember { mutableStateOf<String?>(null) }
    val morph = remember { Animatable(1f) }
    // Driven only while a transition is in flight — an always-on tick for the
    // shader's idle drift would be the exact "redraws every vsync forever"
    // mistake called out on this file's own rotation animation above.
    var timeSeconds by remember { mutableFloatStateOf(0f) }

    // Raw decoded covers, keyed by resolved URL (a fallback rung gets its own
    // slot rather than overwriting the URL that failed).
    val rawBitmaps = remember { androidx.compose.runtime.mutableStateMapOf<String, Bitmap>() }
    // Covers pre-scaled to exactly the box's pixel size, needed only by the
    // API<33 fallback: drawBitmapMesh has no notion of ContentScale, it
    // divides the bitmap's own dimensions evenly across the mesh, so the
    // source must already match the destination. The shader path needs no
    // such step — its coverUV does that math itself, against the raw bitmap.
    val scaledBitmaps = remember { androidx.compose.runtime.mutableStateMapOf<String, Bitmap>() }

    suspend fun ensureLoaded(url: String?) {
        var candidate = url
        while (candidate != null && !rawBitmaps.containsKey(candidate)) {
            val bitmap = loadArtworkBitmap(context, candidate)
            if (bitmap != null) {
                rawBitmaps[candidate] = bitmap
                return
            }
            candidate = nextThumbnailFallback(candidate)
        }
    }

    LaunchedEffect(artworkUri) {
        if (artworkUri != currentUrl) {
            ensureLoaded(artworkUri)
            outgoingUrl = currentUrl
            currentUrl = artworkUri
            morph.snapTo(0f)
            coroutineScope {
                val timeJob = if (shaderMorphSupported) {
                    launch {
                        val start = withFrameNanos { it }
                        while (isActive) {
                            timeSeconds = (withFrameNanos { it } - start) / 1_000_000_000f
                        }
                    }
                } else {
                    null
                }
                morph.animateTo(1f, animationSpec = tween(crossfadeMillis, easing = LinearEasing))
                timeJob?.cancel()
            }
            outgoingUrl = null
            // Only the just-landed cover matters from here — this is a
            // per-composable cache for morph-ready bitmaps, not a general
            // image cache (Coil already owns that).
            if (rawBitmaps.size > MAX_CACHED_COVERS) {
                rawBitmaps.keys.filterNot { it == currentUrl }.forEach { rawBitmaps.remove(it) }
            }
            val keep = currentUrl
            scaledBitmaps.keys.filterNot { keep != null && it.startsWith("$keep@") }
                .forEach { scaledBitmaps.remove(it) }
        }
    }
    LaunchedEffect(Unit) { ensureLoaded(currentUrl) }

    if (!shaderMorphSupported) {
        fun scaledKeyFor(url: String) = "$url@${boxSize.width}x${boxSize.height}x$cropArtwork"
        LaunchedEffect(rawBitmaps.keys.toSet(), boxSize, cropArtwork) {
            if (boxSize.width <= 0 || boxSize.height <= 0) return@LaunchedEffect
            for ((url, bitmap) in rawBitmaps) {
                val key = scaledKeyFor(url)
                if (!scaledBitmaps.containsKey(key)) {
                    scaledBitmaps[key] = scaleForMesh(bitmap, boxSize.width, boxSize.height, cropArtwork)
                }
            }
        }

        Box(
            modifier = modifier
                .fillMaxSize()
                .background(AppTheme.colorScheme.surfaceVariant)
                .onSizeChanged { boxSize = it }
        ) {
            val outgoingBitmap = outgoingUrl?.let { scaledBitmaps[scaledKeyFor(it)] }
            val currentBitmap = currentUrl?.let { scaledBitmaps[scaledKeyFor(it)] }
            if (currentBitmap != null || outgoingBitmap != null) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val maxDisplacementPx = with(density) { RIPPLE_MAX_DISPLACEMENT.toPx() }
                    val warpAmount = kotlin.math.sin((morph.value * Math.PI).toFloat())
                    drawIntoCanvas { canvas ->
                        val nativeCanvas = canvas.nativeCanvas
                        outgoingBitmap?.let { bitmap ->
                            val verts = rippleMesh(size.width, size.height, warpAmount, maxDisplacementPx)
                            val paint = android.graphics.Paint().apply {
                                isAntiAlias = true
                                alpha = ((1f - morph.value) * 255).toInt().coerceIn(0, 255)
                            }
                            nativeCanvas.drawBitmapMesh(bitmap, MESH_COLS, MESH_ROWS, verts, 0, null, 0, paint)
                        }
                        currentBitmap?.let { bitmap ->
                            val verts = rippleMesh(size.width, size.height, warpAmount, maxDisplacementPx)
                            val paint = android.graphics.Paint().apply {
                                isAntiAlias = true
                                alpha = if (outgoingBitmap != null) {
                                    (morph.value * 255).toInt().coerceIn(0, 255)
                                } else {
                                    255
                                }
                            }
                            nativeCanvas.drawBitmapMesh(bitmap, MESH_COLS, MESH_ROWS, verts, 0, null, 0, paint)
                        }
                    }
                }
            }
        }
        return
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppTheme.colorScheme.surfaceVariant)
    ) {
        // The resting frame: a plain image respecting cropArtwork exactly like
        // before. The shader only ever draws on top of this during the brief
        // transition window below, so the steady state pays nothing extra.
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(currentUrl)
                .size(CoilSize.ORIGINAL)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .networkCachePolicy(CachePolicy.ENABLED)
                .build(),
            onError = onError@{
                val fallback = nextThumbnailFallback(currentUrl) ?: return@onError
                currentUrl = fallback
            },
            contentDescription = null,
            contentScale = if (cropArtwork) ContentScale.Crop else ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )

        val outgoingBitmap = outgoingUrl?.let { rawBitmaps[it] }
        val currentBitmap = currentUrl?.let { rawBitmaps[it] }
        if (outgoingBitmap != null && currentBitmap != null) {
            MeltMorphOverlay(
                outgoing = outgoingBitmap,
                incoming = currentBitmap,
                progress = morph.value,
                timeSeconds = timeSeconds,
            )
        }
    }
}

/**
 * The morph itself: one shader draw call blending [outgoing] into [incoming]
 * as [progress] runs 0→1. See [MELT_SHADER_SOURCE] for the actual displacement
 * and reveal math — this composable only binds the two bitmaps and uniforms.
 */
@androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.TIRAMISU)
@Composable
private fun MeltMorphOverlay(outgoing: Bitmap, incoming: Bitmap, progress: Float, timeSeconds: Float) {
    val shader = remember { android.graphics.RuntimeShader(MELT_SHADER_SOURCE) }
    val outgoingShader = remember(outgoing) {
        android.graphics.BitmapShader(outgoing, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP)
    }
    val incomingShader = remember(incoming) {
        android.graphics.BitmapShader(incoming, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP)
    }
    Canvas(modifier = Modifier.fillMaxSize()) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        shader.setInputShader("tCurrent", outgoingShader)
        shader.setInputShader("tNext", incomingShader)
        shader.setFloatUniform("uResolution", size.width, size.height)
        shader.setFloatUniform("uCurrentSize", outgoing.width.toFloat(), outgoing.height.toFloat())
        shader.setFloatUniform("uNextSize", incoming.width.toFloat(), incoming.height.toFloat())
        shader.setFloatUniform("uProgress", progress)
        shader.setFloatUniform("uIntensity", MeltIntensity)
        shader.setFloatUniform("uScale", MeltScale)
        shader.setFloatUniform("uAberration", MeltAberration)
        shader.setFloatUniform("uTime", timeSeconds)
        drawIntoCanvas { canvas ->
            val paint = android.graphics.Paint().apply { this.shader = shader }
            canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint)
        }
    }
}

/** Displacement strength — how far the melt pushes pixels around. */
private const val MeltIntensity = 0.55f

/** Noise-field frequency — higher reads as finer, more turbulent melt cells. */
private const val MeltScale = 2.4f

/** RGB-split strength at the midpoint of the transition, fading to none at
 *  both ends. */
private const val MeltAberration = 0.35f

/**
 * AGSL port of React Bits' MorphSlider "melt" fragment shader
 * (github.com/DavidHDev/react-bits): a shared fbm noise field displaces both
 * the outgoing and incoming cover, and the SAME noise value gates a
 * per-pixel reveal threshold against [progress] — so different regions of the
 * frame cross over at different moments instead of a uniform fade, which is
 * what actually reads as one image melting into the next rather than two
 * images cross-faded on top of each other. Chromatic aberration rides
 * alongside, peaking at the transition's midpoint.
 */
private const val MELT_SHADER_SOURCE = """
uniform shader tCurrent;
uniform shader tNext;
uniform float2 uResolution;
uniform float2 uCurrentSize;
uniform float2 uNextSize;
uniform float uProgress;
uniform float uIntensity;
uniform float uScale;
uniform float uAberration;
uniform float uTime;

float hash21(float2 p) {
    float3 p3 = fract(p.xyx * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float noise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float2 u = f * f * (3.0 - 2.0 * f);
    float a = hash21(i);
    float b = hash21(i + float2(1.0, 0.0));
    float c = hash21(i + float2(0.0, 1.0));
    float d = hash21(i + float2(1.0, 1.0));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float fbm(float2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 5; i++) {
        v += a * noise(p);
        p *= 2.0;
        a *= 0.5;
    }
    return v;
}

float2 coverUV(float2 uv, float2 res, float2 img) {
    float rA = res.x / max(res.y, 1.0);
    float iA = img.x / max(img.y, 1.0);
    float2 s = float2(1.0, 1.0);
    float ratio = rA / max(iA, 0.0001);
    if (ratio > 1.0) {
        s.y = 1.0 / ratio;
    } else {
        s.x = ratio;
    }
    return (uv - 0.5) * s + 0.5;
}

half4 main(float2 fragCoord) {
    float2 uv = fragCoord / uResolution;
    float p = clamp(uProgress, 0.0, 1.0);
    float env = sin(p * 3.14159265359);

    float nn = fbm(uv * uScale + uTime * 0.03);
    float warp = fbm(uv * uScale * 1.7 - uTime * 0.02);
    float2 g = float2(nn, warp) - 0.5;

    float2 uvC = uv + g * uIntensity * 0.5 * p;
    float2 uvN = uv - g * uIntensity * 0.5 * (1.0 - p);
    float m = smoothstep(nn - 0.15, nn + 0.15, p);

    float2 sC = coverUV(uvC, uResolution, uCurrentSize);
    float2 sN = coverUV(uvN, uResolution, uNextSize);

    float ca = uAberration * env * 0.03;

    float3 colC = float3(
        tCurrent.eval((sC + float2(ca, 0.0)) * uCurrentSize).r,
        tCurrent.eval(sC * uCurrentSize).g,
        tCurrent.eval((sC - float2(ca, 0.0)) * uCurrentSize).b
    );
    float3 colN = float3(
        tNext.eval((sN + float2(ca, 0.0)) * uNextSize).r,
        tNext.eval(sN * uNextSize).g,
        tNext.eval((sN - float2(ca, 0.0)) * uNextSize).b
    );

    float3 col = mix(colC, colN, m);
    return half4(half3(col), 1.0);
}
"""

/** Destination vertices for an (MESH_COLS+1) x (MESH_ROWS+1) grid, each pushed
 *  radially from center by a ring pattern scaled by [warpAmount] (0 = flat). */
private fun rippleMesh(widthPx: Float, heightPx: Float, warpAmount: Float, maxDisplacementPx: Float): FloatArray {
    val verts = FloatArray((MESH_COLS + 1) * (MESH_ROWS + 1) * 2)
    var index = 0
    for (row in 0..MESH_ROWS) {
        val v = row.toFloat() / MESH_ROWS
        for (col in 0..MESH_COLS) {
            val u = col.toFloat() / MESH_COLS
            val dx = u - 0.5f
            val dy = v - 0.5f
            val radius = kotlin.math.sqrt(dx * dx + dy * dy)
            val ripple = kotlin.math.sin((radius * RIPPLE_RINGS * 2 * Math.PI).toFloat()) * warpAmount
            val displacement = ripple * maxDisplacementPx
            val nx = if (radius < 0.0001f) 0f else dx / radius
            val ny = if (radius < 0.0001f) 0f else dy / radius
            verts[index++] = u * widthPx + nx * displacement
            verts[index++] = v * heightPx + ny * displacement
        }
    }
    return verts
}

/** Scales+center-crops (or center-pads, for [crop] == false) [bitmap] onto an
 *  exact [targetWidth] x [targetHeight] canvas — drawBitmapMesh needs the
 *  source bitmap's own dimensions to already match the destination 1:1.
 *
 *  ponytail: always center-crops/pads rather than replicating ContentScale's
 *  full Fit/Crop/FillBounds matrix math — album art is overwhelmingly square,
 *  so the two rarely differ in practice; upgrade path is Compose's own
 *  ContentScale math if a non-square source becomes common. */
private fun scaleForMesh(bitmap: Bitmap, targetWidth: Int, targetHeight: Int, crop: Boolean): Bitmap {
    val srcW = bitmap.width
    val srcH = bitmap.height
    if (srcW <= 0 || srcH <= 0) return bitmap
    val scale = if (crop) {
        kotlin.math.max(targetWidth.toFloat() / srcW, targetHeight.toFloat() / srcH)
    } else {
        kotlin.math.min(targetWidth.toFloat() / srcW, targetHeight.toFloat() / srcH)
    }
    val scaledW = (srcW * scale).toInt().coerceAtLeast(1)
    val scaledH = (srcH * scale).toInt().coerceAtLeast(1)
    val scaled = Bitmap.createScaledBitmap(bitmap, scaledW, scaledH, true)
    val output = createBitmap(targetWidth, targetHeight)
    val canvas = android.graphics.Canvas(output)
    canvas.drawBitmap(scaled, (targetWidth - scaledW) / 2f, (targetHeight - scaledH) / 2f, null)
    if (scaled !== bitmap) scaled.recycle()
    return output
}

private suspend fun loadArtworkBitmap(context: android.content.Context, url: String): Bitmap? {
    val request = ImageRequest.Builder(context)
        .data(url)
        .allowHardware(false)
        .build()
    return when (val result = context.imageLoader.execute(request)) {
        is coil3.request.SuccessResult -> runCatching { result.image.toBitmap() }.getOrNull()
        else -> null
    }
}

/**
 * Seek effect overlay showing seek direction.
 */
@Composable
private fun SeekEffectOverlay(
    seekDirection: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = seekDirection,
        color = Color.White,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
            .padding(8.dp)
    )
}

internal fun normalizeCanvasSongTitle(raw: String): String {
    val stripped =
        raw
            .replace(Regex("\\s*\\[[^]]*]"), "")
            .replace(
                Regex(
                    "\\s*\\((?:feat\\.?|ft\\.?|featuring|with)\\b[^)]*\\)",
                    RegexOption.IGNORE_CASE,
                ),
                "",
            )
            .replace(
                Regex(
                    "\\s*\\((?:official\\s*)?(?:music\\s*)?(?:video|mv|lyrics?|audio|visualizer|live|remaster(?:ed)?|version|edit|mix|remix)[^)]*\\)",
                    RegexOption.IGNORE_CASE,
                ),
                "",
            )
            .replace(
                Regex(
                    "\\s*-\\s*(?:official\\s*)?(?:music\\s*)?(?:video|mv|lyrics?|audio|visualizer|live|remaster(?:ed)?|version|edit|mix|remix)\\b.*$",
                    RegexOption.IGNORE_CASE,
                ),
                "",
            )
            .replace(Regex("\\s+"), " ")
            .trim()

    return stripped
        .trim('-')
        .replace(Regex("\\s+"), " ")
        .trim()
}

internal fun normalizeCanvasArtistName(raw: String): String {
    val first =
        raw
            .split(
                Regex(
                    "(?:\\s*,\\s*|\\s*&\\s*|\\s+×\\s+|\\s+x\\s+|\\bfeat\\.?\\b|\\bft\\.?\\b|\\bfeaturing\\b|\\bwith\\b)",
                    RegexOption.IGNORE_CASE,
                ),
                limit = 2,
                ).firstOrNull().orEmpty()

    return first.replace(Regex("\\s+"), " ").trim()
}

internal fun splitAndNormalizeArtists(raw: String): List<String> {
    return raw.split(
        Regex(
            "(?:\\s*,\\s*|\\s*&\\s*|\\s+×\\s+|\\s+x\\s+|\\bfeat\\.?\\b|\\bft\\.?\\b|\\bfeaturing\\b|\\bwith\\b)",
            RegexOption.IGNORE_CASE,
        )
    ).map { it.replace(Regex("\\s+"), " ").trim().lowercase(Locale.ROOT) }
        .filter { it.isNotBlank() }
}

/**
 * Next-lower YouTube thumbnail filename, or null once there is nothing left to try.
 *
 * i.ytimg.com serves a fixed ladder of filenames rather than arbitrary sizes, and which
 * rungs exist varies per video: maxresdefault is frequently absent, sddefault usually is
 * not. Walking down one rung per failure keeps the best available image instead of
 * collapsing to the smallest on the first miss.
 */
internal fun nextThumbnailFallback(url: String?): String? = when {
    url == null -> null
    url.contains("maxresdefault.jpg") -> url.replace("maxresdefault.jpg", "sddefault.jpg")
    url.contains("sddefault.jpg") -> url.replace("sddefault.jpg", "hqdefault.jpg")
    url.contains("hqdefault.jpg") -> url.replace("hqdefault.jpg", "mqdefault.jpg")
    // Either already at the bottom rung, or not an i.ytimg.com URL at all -- a
    // googleusercontent size parameter has no ladder to walk.
    else -> null
}
