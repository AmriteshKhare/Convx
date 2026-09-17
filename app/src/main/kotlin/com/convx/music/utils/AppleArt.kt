/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import coil3.request.allowHardware
import coil3.toBitmap
import com.convx.music.ui.utils.resize
import com.convx.music.canvas.AppleArtist
import com.convx.music.canvas.AppleArtwork
import com.convx.music.canvas.AppleMusicCatalog

/** What a piece of artwork belongs to, so the same item can be looked up on Apple Music. */
sealed interface AppleArtQuery {
    data class Song(val title: String, val artist: String?) : AppleArtQuery
    data class Album(val title: String, val artist: String?) : AppleArtQuery
    data class Artist(val name: String) : AppleArtQuery
}

/**
 * Apple Music artwork as the primary image source, YouTube's as the fallback. [enabled]
 * mirrors AppleArtworkKey (off under data saver) and is written by App's preference
 * collector, so list rows read a plain field instead of each subscribing to DataStore.
 */
object AppleArt {
    @Volatile
    var enabled: Boolean = true

    /** Null when not looked up yet this session; otherwise the cached answer (value null = no match). */
    fun peek(query: AppleArtQuery): AppleMusicCatalog.Cached<AppleArtwork>? = when (query) {
        is AppleArtQuery.Song -> AppleMusicCatalog.peekSong(query.title, query.artist)
        is AppleArtQuery.Album -> AppleMusicCatalog.peekAlbum(query.title, query.artist)
        is AppleArtQuery.Artist -> AppleMusicCatalog.peekArtist(query.name)?.let { AppleMusicCatalog.Cached(it.value?.hero) }
    }

    /**
     * How long an image waits for Apple before showing YouTube's. The lookup itself keeps running
     * in the catalog's own scope, so a slow answer still lands in the cache for next time.
     */
    const val WAIT_MS = 10_000L

    suspend fun lookup(query: AppleArtQuery): AppleArtwork? = when (query) {
        is AppleArtQuery.Song -> AppleMusicCatalog.song(query.title, query.artist)
        is AppleArtQuery.Album -> AppleMusicCatalog.album(query.title, query.artist)
        is AppleArtQuery.Artist -> AppleMusicCatalog.artist(query.name)?.hero
    }
}

/** One image's Apple lookup: still waiting (draw a shimmer), or settled on [url] — Apple's, else the fallback. */
sealed interface ArtState {
    data object Loading : ArtState
    data class Ready(val url: String?) : ArtState
}

/**
 * Apple's artwork for [query] at [px]; [ArtState.Loading] until the lookup settles, then
 * Apple's URL, or [fallbackUrl] when Apple has no match, fails, or takes longer than
 * [AppleArt.WAIT_MS]. Local files and disabled lookups settle on [fallbackUrl] immediately.
 */
@Composable
fun rememberAppleArtwork(query: AppleArtQuery?, fallbackUrl: String?, px: Int): ArtState {
    if (query == null || !AppleArt.enabled || (fallbackUrl != null && !fallbackUrl.startsWith("http"))) {
        return ArtState.Ready(fallbackUrl)
    }
    val initial = remember(query, px, fallbackUrl) {
        AppleArt.peek(query)?.let { ArtState.Ready(it.value?.sized(px) ?: fallbackUrl) } ?: ArtState.Loading
    }
    val state by produceState(initial, query, px, fallbackUrl) {
        if (value != ArtState.Loading) return@produceState
        val art = kotlinx.coroutines.withTimeoutOrNull(AppleArt.WAIT_MS) {
            runCatching { AppleArt.lookup(query) }.getOrNull()
        }
        value = ArtState.Ready(art?.sized(px) ?: fallbackUrl)
    }
    return state
}

/** [rememberAppleArtwork] for callers without a loading state of their own: null while waiting. */
@Composable
fun rememberAppleArtworkUrl(query: AppleArtQuery?, fallbackUrl: String?, px: Int): String? =
    (rememberAppleArtwork(query, fallbackUrl, px) as? ArtState.Ready)?.url

/** An artist page's Apple lookup: [loading] until it settles; [artist] null afterwards means no match. */
data class AppleArtistState(val loading: Boolean, val artist: AppleArtist?)

@Composable
fun rememberAppleArtist(name: String?): AppleArtistState {
    if (!AppleArt.enabled) return AppleArtistState(loading = false, artist = null)
    if (name.isNullOrBlank()) return AppleArtistState(loading = true, artist = null)
    val initial = remember(name) {
        AppleMusicCatalog.peekArtist(name)?.let { AppleArtistState(false, it.value) } ?: AppleArtistState(true, null)
    }
    val state by produceState(initial, name) {
        if (!value.loading) return@produceState
        val artist = kotlinx.coroutines.withTimeoutOrNull(AppleArt.WAIT_MS) {
            runCatching { AppleMusicCatalog.artist(name) }.getOrNull()
        }
        value = AppleArtistState(loading = false, artist = artist)
        // Past the wait (a cold token renewal can take ~10s): keep listening, and switch to
        // Apple's profile if it still arrives, rather than leaving YouTube's for good.
        if (artist == null) {
            runCatching { AppleMusicCatalog.artist(name) }.getOrNull()?.let {
                value = AppleArtistState(loading = false, artist = it)
            }
        }
    }
    return state
}

private val estimatedBgCache = android.util.LruCache<String, Int>(64)

/**
 * On-device estimate of Apple's artwork bgColor ([com.convx.music.ui.theme.AppleColors.estimateArtworkBackground])
 * for art Apple has no entry for. Null until the small decode lands.
 */
@Composable
fun rememberEstimatedArtworkBg(url: String?): Int? {
    val context = androidx.compose.ui.platform.LocalContext.current
    val bg by produceState(url?.let { estimatedBgCache.get(it) }, url) {
        if (url == null || value != null) return@produceState
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val request = coil3.request.ImageRequest.Builder(context)
                .data(url.resize(120, 120))
                .size(120)
                .allowHardware(false)
                .build()
            val result = coil3.SingletonImageLoader.get(context).execute(request) as? coil3.request.SuccessResult
            result?.image?.let { image ->
                runCatching {
                    com.convx.music.ui.theme.AppleColors.estimateArtworkBackground(image.toBitmap())
                }.getOrNull()
            }?.also { estimatedBgCache.put(url, it) }
        }
    }
    return bg
}

/** An album page's Apple lookup: [loading] until it settles; [art] null afterwards means no match. */
data class AppleAlbumState(val loading: Boolean, val art: AppleArtwork?)

@Composable
fun rememberAppleAlbum(title: String?, artist: String?): AppleAlbumState {
    if (!AppleArt.enabled) return AppleAlbumState(loading = false, art = null)
    if (title.isNullOrBlank()) return AppleAlbumState(loading = true, art = null)
    val initial = remember(title, artist) {
        AppleMusicCatalog.peekAlbum(title, artist)?.let { AppleAlbumState(false, it.value) } ?: AppleAlbumState(true, null)
    }
    val state by produceState(initial, title, artist) {
        if (!value.loading) return@produceState
        val art = kotlinx.coroutines.withTimeoutOrNull(AppleArt.WAIT_MS) {
            runCatching { AppleMusicCatalog.album(title, artist, priority = true) }.getOrNull()
        }
        value = AppleAlbumState(loading = false, art = art)
        if (art == null) {
            runCatching { AppleMusicCatalog.album(title, artist, priority = true) }.getOrNull()?.let {
                value = AppleAlbumState(loading = false, art = it)
            }
        }
    }
    return state
}
