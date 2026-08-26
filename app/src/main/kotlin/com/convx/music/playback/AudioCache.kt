package com.convx.music.playback

import android.content.Context
import android.media.MediaDataSource
import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheKeyFactory
import androidx.media3.datasource.cache.SimpleCache
import com.convx.music.data.settings.AppSettings
import com.convx.music.playback.smart.TrackAnalyzer
import java.io.File
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

    /** Standard key factory: builds from the URI's `v` query parameter. */
    val keyFactory = CacheKeyFactory { spec ->
        spec.uri.getQueryParameter("v") ?: spec.uri.toString()
    }

    fun init(context: Context) {
        cache = SimpleCache(
            File(context.cacheDir, "audio"),
            com.convx.music.playback.smart.DynamicLruCacheEvictor(AppSettings.DEFAULT_CACHE_LIMIT_BYTES),
            StandaloneDatabaseProvider(context),
        )
    }

    // ------------------------------------------------------------------
    //  Rendition API — the surface TrackAnalyzer reads
    // ------------------------------------------------------------------

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
     * Request the opening bytes of a track so the head analysis pass can
     * run before the full download finishes.
     *
     * Currently a no-op: vivi-music's player cache fills on playback, and
     * the analyzer's own progressive head-pass handles partial entries.
     * Can be wired to a targeted range request later.
     */
    fun requestAnalysisHead(uri: Uri) {
        Log.d(TAG, "requestAnalysisHead: ${uri.getQueryParameter("v") ?: uri}")
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

    // ------------------------------------------------------------------
    //  MediaDataSource bridge — adapts Media3 DataSource to MediaExtractor
    // ------------------------------------------------------------------

    private class CacheMediaDataSource(
        private val delegate: DataSource,
        private val uri: Uri,
    ) : MediaDataSource() {

        override fun readAt(position: Long, buffer: ByteArray?, offset: Int, size: Int): Int {
            if (buffer == null || size == 0) return -1
            val dataSpec = DataSpec.Builder()
                .setUri(uri)
                .setPosition(position)
                .setLength(size.toLong())
                .build()
            return delegate.open(dataSpec).let { bytesread ->
                delegate.read(buffer, offset, size)
            }.toInt().coerceAtLeast(0)
        }

        override fun getSize(): Long = C.LENGTH_UNSET.toLong()

        @Throws(IOException::class)
        override fun close() {
            delegate.close()
        }
    }
}
