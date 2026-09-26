/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */
// Apple Music player UI ported from vivizzz007/vivi-music (https://github.com/vivizzz007/vivi-music), GPL-3.0.

package com.convx.music.ui.player

import android.graphics.Bitmap
import android.view.TextureView
import android.view.ViewGroup
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import com.convx.music.applecanvas.AppleMusicCanvasProvider
import com.convx.music.canvas.CanvasArtwork
import com.convx.music.canvas.TidalCanvasProvider
import com.convx.music.constants.CanvasSource
import com.convx.music.constants.CanvasSourceKey
import com.convx.music.constants.CanvasThumbnailAnimationKey
import com.convx.music.models.MediaMetadata
import com.convx.music.utils.rememberEnumPreference
import com.convx.music.utils.rememberPreference
import com.convx.music.vivimusiccanvas.ViviMusicCanvasProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/*
 * V2's canvas (motion artwork) video: ONE decoder per player, shared by every surface that
 * shows it (the cover, the lyrics/queue header), instead of one ExoPlayer per surface.
 *
 * Before, the header mounted its own player on every cover -> lyrics switch: a second
 * network fetch and decoder, and the header's video popped in ~300ms after the pane had
 * already landed. Now the most recently attached surface takes the output, the texture it
 * leaves keeps its last frame, and each surface dissolves in on its own first frame.
 *
 * Track changes dissolve the old loop OUT before the next one loads (while the cover melts
 * underneath), then the new loop dissolves IN on its first frame -- nothing cuts.
 */

/** A canvas video's colour identity: a few small frames sampled once, stitched. */
@Immutable
class CanvasVideoLook(
    val mediaId: String,
    /** 2x2 grid of sampled frames -- palette extraction reads the whole clip's colours. */
    val stitched: Bitmap,
    /** One representative frame, for the blurred-artwork backgrounds. */
    val representative: Bitmap,
)

/**
 * Per-track video looks. Colours are extracted ONCE per video from [SAMPLE_COUNT] tiny
 * readbacks spread over its first seconds -- never per frame -- and cached, so a replay or
 * a revisit is instant and the background eases between two fixed palettes.
 */
object CanvasVideoLooks {
    private val cache = android.util.LruCache<String, CanvasVideoLook>(24)
    private val latest = mutableStateOf<CanvasVideoLook?>(null)

    /** Composition-safe: re-reads when a new look lands, never per frame. */
    fun forMedia(mediaId: String?): CanvasVideoLook? {
        if (mediaId == null) return null
        val newest = latest.value
        return if (newest?.mediaId == mediaId) newest else cache.get(mediaId)
    }

    internal fun has(mediaId: String): Boolean = cache.get(mediaId) != null

    internal fun publish(look: CanvasVideoLook) {
        cache.put(look.mediaId, look)
        latest.value = look
    }
}

private const val SAMPLE_COUNT = 4
private const val SAMPLE_SIZE_PX = 96
private const val SAMPLE_START_MS = 450L
private const val SAMPLE_GAP_MS = 1_100L

/**
 * Samples [mediaId]'s look off [texture]. Main thread (TextureView.getBitmap), but only
 * four 96px readbacks across ~4s; the stitch runs on Default.
 */
internal suspend fun sampleCanvasVideoLook(mediaId: String, texture: () -> TextureView?) {
    if (CanvasVideoLooks.has(mediaId)) return
    val frames = ArrayList<Bitmap>(SAMPLE_COUNT)
    delay(SAMPLE_START_MS)
    for (i in 0 until SAMPLE_COUNT) {
        if (i > 0) delay(SAMPLE_GAP_MS)
        val view = texture()?.takeIf { it.isAvailable } ?: break
        view.getBitmap(SAMPLE_SIZE_PX, SAMPLE_SIZE_PX)?.let(frames::add)
    }
    if (frames.isEmpty()) return
    val stitched = withContext(Dispatchers.Default) {
        val out = Bitmap.createBitmap(SAMPLE_SIZE_PX * 2, SAMPLE_SIZE_PX * 2, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(out)
        // Short clips or an early stop repeat frames rather than leave black quadrants,
        // which would drag every palette toward black.
        for (slot in 0 until 4) {
            canvas.drawBitmap(
                frames[slot % frames.size],
                (slot % 2) * SAMPLE_SIZE_PX.toFloat(),
                (slot / 2) * SAMPLE_SIZE_PX.toFloat(),
                null,
            )
        }
        out
    }
    CanvasVideoLooks.publish(CanvasVideoLook(mediaId, stitched, frames[frames.size / 2]))
}

/**
 * The morph's hook into the live video: at flight start the overlay grabs one poster of
 * the frame on screen (paused while collapsed, so it is also the frame the open lands on)
 * and dissolves the travelling cover into it, so mini -> player lands ON the video.
 */
internal object CanvasPosterSource {
    @JvmField var controller: CanvasVideoController? = null
    // Called from composition at flight start; reads nothing into the caller's snapshot
    // scope, so the overlay does not recompose along with the video's fades.
    fun capture(mediaId: String?): Bitmap? =
        androidx.compose.runtime.snapshots.Snapshot.withoutReadObservation { controller?.capturePoster(mediaId) }
}

@Stable
class CanvasVideoController internal constructor(
    internal val exoPlayer: ExoPlayer,
    private val scope: CoroutineScope,
) {
    /** Loop-level dissolve: out on a track change, in on the first frame. */
    internal val fade = Animatable(0f)
    internal var videoAspect by mutableFloatStateOf(1f)
        private set
    /** The texture that has received a frame since it was bound; surfaces dissolve on it. */
    internal var surfaceWithFrame by mutableStateOf<TextureView?>(null)
        private set

    private var loadingMediaId: String? = null
    private var loadingUrl: String? = null
    private var primary: String? = null
    private var fallback: String? = null
    private var frameMediaId: String? = null
    private var boundTexture: TextureView? = null
    private val attached = ArrayList<Pair<Any, TextureView>>()
    private var fadeInJob: Job? = null

    internal fun attach(token: Any, view: TextureView) {
        attached.removeAll { it.first === token }
        attached.add(token to view)
        bind(view)
    }

    internal fun detach(token: Any) {
        val wasTop = attached.lastOrNull()?.first === token
        attached.removeAll { it.first === token }
        if (wasTop) bind(attached.lastOrNull()?.second)
    }

    private fun bind(view: TextureView?) {
        if (boundTexture === view) return
        boundTexture = view
        surfaceWithFrame = null
        if (view == null) exoPlayer.clearVideoSurface() else exoPlayer.setVideoTextureView(view)
    }

    internal fun onFirstFrame() {
        frameMediaId = loadingMediaId
        surfaceWithFrame = boundTexture
        fadeInJob?.cancel()
        fadeInJob = scope.launch { fade.animateTo(1f, tween(VIDEO_FADE_IN_MS, easing = SeamlessEasing)) }
        val id = loadingMediaId ?: return
        scope.launch { sampleCanvasVideoLook(id) { boundTexture } }
    }

    internal fun onVideoSize(size: VideoSize) {
        if (size.width > 0 && size.height > 0) videoAspect = size.width.toFloat() / size.height
    }

    internal fun onError() {
        val next = if (loadingUrl == primary) fallback else null
        val id = loadingMediaId
        if (id != null && !next.isNullOrBlank() && next != loadingUrl) {
            loadingUrl = next
            exoPlayer.setMediaItem(mediaItem(id, next))
            exoPlayer.prepare()
        }
    }

    internal suspend fun load(mediaId: String?, primary: String?, fallback: String?, fadeOutMillis: Int) {
        val url = primary ?: fallback
        if (mediaId == loadingMediaId && url == loadingUrl) return
        fadeInJob?.cancel()
        if (fade.value > 0f) fade.animateTo(0f, tween(fadeOutMillis, easing = SeamlessEasing))
        loadingMediaId = mediaId
        loadingUrl = url
        this.primary = primary
        this.fallback = fallback
        frameMediaId = null
        surfaceWithFrame = null
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        if (mediaId == null || url == null) return
        exoPlayer.setMediaItem(mediaItem(mediaId, url))
        exoPlayer.prepare()
    }

    private fun mediaItem(mediaId: String, url: String): MediaItem {
        val normalized = url.trim()
        return MediaItem.Builder()
            .setUri(normalized)
            .setMimeType(canvasMimeType(normalized, primary, fallback))
            // Same key DownloadUtil pre-caches canvas loops under.
            .setCustomCacheKey("$mediaId#canvas")
            .build()
    }

    /** A still of the frame on screen, <= 720px, or null when no video is showing. */
    internal fun capturePoster(mediaId: String?): Bitmap? {
        if (mediaId == null || frameMediaId != mediaId || fade.value < 0.99f) return null
        val view = boundTexture?.takeIf { it.isAvailable && it === surfaceWithFrame } ?: return null
        val w = view.width
        val h = view.height
        if (w <= 0 || h <= 0) return null
        val scale = minOf(1f, POSTER_MAX_PX / maxOf(w, h))
        return runCatching {
            view.getBitmap((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
        }.getOrNull()
    }
}

private const val VIDEO_FADE_IN_MS = 750
private const val VIDEO_FADE_OUT_MAX_MS = 1_400
private const val POSTER_MAX_PX = 720f

/**
 * V2's single canvas decoder. [isPlaying] should already fold in "sheet not collapsed";
 * [trackFadeMillis] is the ambient transition length (the DJ crossfade while one runs).
 */
@Composable
fun rememberCanvasVideoController(
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    trackFadeMillis: Int,
): CanvasVideoController {
    val context = LocalContext.current
    val mediaSourceFactory = rememberCanvasMediaSourceFactory()
    val scope = rememberCoroutineScope()
    val controller = remember {
        val player = ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                trackSelectionParameters = trackSelectionParameters
                    .buildUpon()
                    .setMaxVideoFrameRate(30)
                    .setMaxVideoBitrate(2_500_000)
                    .build()
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .build(),
                    false,
                )
                volume = 0f
                repeatMode = Player.REPEAT_MODE_ONE
                playWhenReady = false
            }
        CanvasVideoController(player, scope)
    }
    DisposableEffect(controller) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() = controller.onFirstFrame()
            override fun onVideoSizeChanged(videoSize: VideoSize) = controller.onVideoSize(videoSize)
            override fun onPlayerError(error: PlaybackException) = controller.onError()
        }
        controller.exoPlayer.addListener(listener)
        CanvasPosterSource.controller = controller
        onDispose {
            if (CanvasPosterSource.controller === controller) CanvasPosterSource.controller = null
            controller.exoPlayer.removeListener(listener)
            controller.exoPlayer.release()
        }
    }

    // Decorative: never decode with the app in the background.
    val lifecycleOwner = LocalLifecycleOwner.current
    var inForeground by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> inForeground = true
                Lifecycle.Event.ON_STOP -> inForeground = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val shouldPlay = isPlaying && inForeground
    LaunchedEffect(shouldPlay) { controller.exoPlayer.playWhenReady = shouldPlay }

    val enableCanvas by rememberPreference(CanvasThumbnailAnimationKey, defaultValue = true)
    val (canvasSource) = rememberEnumPreference(CanvasSourceKey, defaultValue = CanvasSource.AUTO)
    val mediaId = mediaMetadata?.id
    val latestMetadata by rememberUpdatedState(mediaMetadata)
    // Tagged with its track: produceState keeps its last value across a key change, so an
    // untagged result let the NEW track load and fade in the OLD track's loop until the
    // fetch returned.
    val fetched by produceState<Pair<String?, CanvasArtwork?>?>(null, mediaId, canvasSource, enableCanvas) {
        val metadata = latestMetadata
        value = mediaId to if (!enableCanvas || metadata == null) null else fetchCanvasArtwork(metadata, canvasSource)
    }
    val artwork = fetched?.takeIf { it.first == mediaId }?.second
    val fadeOut = trackFadeMillis.coerceAtMost(VIDEO_FADE_OUT_MAX_MS)
    val primary = artwork?.preferredAnimationUrl?.takeIf { it.isNotBlank() }
    val fallback = artwork?.animated?.takeIf { it.isNotBlank() }
    LaunchedEffect(mediaId, primary, fallback) {
        controller.load(mediaId, primary, fallback, fadeOut)
    }
    return controller
}

/**
 * A surface showing [controller]'s video. Crop-fills its bounds (the poster the morph
 * captures is drawn the same way), and dissolves in on its own first frame.
 */
@Composable
fun CanvasVideoSurface(controller: CanvasVideoController?, modifier: Modifier = Modifier) {
    if (controller == null) return
    val token = remember { Any() }
    var texture by remember { mutableStateOf<TextureView?>(null) }
    val surfaceAlpha = remember { Animatable(0f) }
    val hasFrame = texture != null && controller.surfaceWithFrame === texture
    LaunchedEffect(hasFrame) {
        // Only ever dissolves IN: a surface that loses the output keeps its last frame
        // (TextureView retains it), and loop-level fades belong to controller.fade.
        if (hasFrame) surfaceAlpha.animateTo(1f, tween(SURFACE_FADE_IN_MS, easing = SeamlessEasing))
    }
    DisposableEffect(token) { onDispose { controller.detach(token) } }
    val aspect = controller.videoAspect
    AndroidView(
        factory = { viewContext ->
            AspectRatioFrameLayout(viewContext).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                val view = TextureView(viewContext).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                }
                addView(view)
                texture = view
                controller.attach(token, view)
            }
        },
        update = { it.setAspectRatio(aspect) },
        modifier = modifier.graphicsLayer {
            clip = true
            alpha = controller.fade.value * surfaceAlpha.value
        },
    )
}

private const val SURFACE_FADE_IN_MS = 420

private suspend fun fetchCanvasArtwork(metadata: MediaMetadata, canvasSource: CanvasSource): CanvasArtwork? {
    val cacheKey = "${metadata.id}:${canvasSource.name}"
    CanvasArtworkPlaybackCache.get(cacheKey)?.let { return it }
    val storefront = Locale.getDefault().country.let { if (it.length == 2) it.lowercase(Locale.ROOT) else "us" }
    val fetched = withContext(Dispatchers.IO) {
        runCatching {
            val songTitle = metadata.title
            val artistName = metadata.artists.firstOrNull()?.name ?: ""
            val albumName = metadata.album?.title ?: ""
            when (canvasSource) {
                CanvasSource.AUTO ->
                    AppleMusicCanvasProvider.getBySongArtist(songTitle, artistName, albumName, storefront)
                        ?.takeIf { !it.animated.isNullOrBlank() }
                        ?: TidalCanvasProvider.getBySongArtist(songTitle, artistName, albumName)
                            ?.takeIf { !it.animated.isNullOrBlank() }
                        ?: ViviMusicCanvasProvider.getBySongArtist(songTitle, artistName)
                            ?.takeIf { !it.animated.isNullOrBlank() }
                CanvasSource.APPLE_MUSIC ->
                    AppleMusicCanvasProvider.getBySongArtist(songTitle, artistName, albumName, storefront)
                        ?.takeIf { !it.animated.isNullOrBlank() }
                CanvasSource.VIVIMUSIC ->
                    ViviMusicCanvasProvider.getBySongArtist(songTitle, artistName)
                        ?.takeIf { !it.animated.isNullOrBlank() }
                CanvasSource.TIDAL ->
                    TidalCanvasProvider.getBySongArtist(songTitle, artistName, albumName)
                        ?.takeIf { !it.animated.isNullOrBlank() }
                else -> null
            }
        }.getOrNull()
    }
    if (fetched != null) CanvasArtworkPlaybackCache.put(cacheKey, fetched)
    return fetched
}
