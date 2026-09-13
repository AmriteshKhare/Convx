package com.convx.music.playback

import android.media.MediaDataSource
import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheKeyFactory
import androidx.media3.datasource.cache.SimpleCache
import com.convx.music.playback.smart.TrackAnalyzer
import java.io.IOException

/**
 * Adapter that gives [TrackAnalyzer] access to ExoPlayer's [SimpleCache],
 * translating BitChord's rendition-based API over vivi-music's existing
 * player/download cache chain.
 *
 * In this initial port there is a single rendition per track — the live
 * stream the player cached — so `renditionsOf` returns zero or one entry.
 * Multi-rendition support (quality upgrades, alternative sources) can be
 * layered in later by adding sibling cache keys.
 */
@UnstableApi
object AudioCache {

    private const val TAG = "ViviAudioCache"

    private lateinit var cache: SimpleCache

    // ponytail: matches the real player's cache key only when it's the bare
    // media id (the common case — no lossless/Spine namespace suffix active).
    // A namespaced key ("$mediaId#suffix") won't be found by this factory;
    // upgrade path is reading the active namespace the same way
    // MusicService.effectiveCacheKey does, if that toggle turns out to matter
    // for analysis too.
    /** Standard key factory: builds from the URI's `v` query parameter. */
    val keyFactory = CacheKeyFactory { spec ->
        spec.uri.getQueryParameter("v") ?: spec.uri.toString()
    }

    /**
     * [playerCache] must be the SAME [SimpleCache] instance the real player's
     * [CacheDataSource] writes into (MusicService's `@PlayerCache`-qualified
     * one) — not a second, independent cache. A separate cache here would
     * never see a single byte the player actually downloads: [renditionsOf]
     * would stay permanently empty and every track would read as
     * unanalysable forever, which is exactly what happened before this was
     * wired to the real thing.
     */
    fun init(playerCache: SimpleCache) {
        cache = playerCache
    }

    //  Rendition API — the surface TrackAnalyzer reads

    data class Rendition(
        val key: String,
        val isComplete: Boolean,
        val cachedPrefix: Long,
        val contentLength: Long = 0L,
    )

    /**
     * All cached copies of [uri] (including sibling keys like `#alt`).
     */
    fun renditionsOf(uri: Uri): List<Rendition> {
        if (!::cache.isInitialized) return emptyList()
        val videoId = uri.getQueryParameter("v") ?: return emptyList()
        val family = cache.keys.filter { it == videoId || it.startsWith("$videoId#") }
        return family.map { key ->
            val cached = cache.getCachedSpans(key)
            val prefix = cached.maxOfOrNull { it.position + it.length } ?: 0L
            Rendition(
                key = key,
                isComplete = false,
                cachedPrefix = prefix,
            )
        }
    }

    /**
     * Returns a [MediaDataSource] over a specific rendition for the analyzer
     * to read. Falls back to the live-player cache entry when the requested
     * key is not present.
     */
    fun renditionDataSource(uri: Uri, rendition: Rendition): MediaDataSource {
        val cacheSource = CacheDataSource.Factory()
            .setCache(cache)
            .setCacheKeyFactory(keyFactory)
            .createDataSource()
        return CacheMediaDataSource(cacheSource, uri)
    }

    /**
     * The key the player cache uses for this URI — used by the analyzer to
     * distinguish "the copy the player is reading" from sibling renditions.
     */
    fun cacheKeyOf(uri: Uri): String = keyFactory.buildCacheKey(DataSpec(uri))

    /**
     * Installed by [MusicService] to actively fetch a track's opening bytes
     * into [cache], independent of the player's own buffering. Left null
     * only outside a running service (tests, previews).
     */
    var prefetchHead: ((Uri) -> Unit)? = null

    /**
     * Request the opening bytes of a track so the head analysis pass can
     * run before the full download finishes.
     *
     * Waiting on the player's own buffering left this permanently too late
     * for the *incoming* half of a transition: ExoPlayer's DefaultLoadControl
     * only reaches into the next queue item within ~50s of the current one
     * ending, well inside the window a beat-detection pass needs to run and
     * land before the crossfade arms. [prefetchHead] does a real bounded
     * fetch instead.
     */
    fun requestAnalysisHead(uri: Uri) {
        val hook = prefetchHead
        if (hook != null) {
            hook(uri)
        } else {
            Log.d(TAG, "requestAnalysisHead: no prefetch hook installed, ${uri.getQueryParameter("v") ?: uri}")
        }
    }

    /**
     * Discard a rendition whose bytes are corrupt.
     */
    fun discardBadRendition(uri: Uri, key: String): Boolean {
        if (!::cache.isInitialized) return false
        val currentKey = cacheKeyOf(uri)
        if (key == currentKey) {
            Log.d(TAG, "Refusing to discard active rendition $key")
            return false
        }
        return runCatching { cache.removeResource(key) }
            .onSuccess { Log.d(TAG, "Discarded bad rendition $key") }
            .onFailure { Log.d(TAG, "Could not discard $key: ${it.message}") }
            .isSuccess
    }

    //  MediaDataSource bridge — adapts Media3 DataSource to MediaExtractor

    private class CacheMediaDataSource(
        private val delegate: DataSource,
        private val uri: Uri,
    ) : MediaDataSource() {

        override fun readAt(position: Long, buffer: ByteArray?, offset: Int, size: Int): Int {
            if (buffer == null || size == 0) return 0
            if (position < 0) return -1
            val dataSpec = DataSpec.Builder()
                .setUri(uri)
                .setPosition(position)
                .setLength(size.toLong())
                .build()
            try {
                delegate.open(dataSpec)
                val bytesRead = delegate.read(buffer, offset, size)
                // Media3 read returns C.RESULT_END_OF_INPUT (-1) at EOF.
                return bytesRead
            } finally {
                runCatching { delegate.close() }
            }
        }

        override fun getSize(): Long = C.LENGTH_UNSET.toLong()

        @Throws(IOException::class)
        override fun close() {
            delegate.close()
        }
    }
}
