/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.lyrics

import android.content.Context
import android.util.LruCache
import com.convx.music.constants.LyricsProviderOrderKey
import com.convx.music.constants.PreferredLyricsProvider
import com.convx.music.constants.PreferredLyricsProviderKey
import com.convx.music.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.convx.music.extensions.toEnum
import com.convx.music.models.MediaMetadata
import com.convx.music.utils.NetworkConnectivityObserver
import com.convx.music.utils.dataStore
import com.convx.music.utils.reportException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

class LyricsHelper
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val networkConnectivity: NetworkConnectivityObserver,
) {
    /**
     * Resolves the ordered list of lyrics providers from the user's saved priority order.
     * Falls back to migrating the legacy [PreferredLyricsProvider] enum if the new order
     * preference has not been written yet, ensuring a smooth upgrade for existing users.
     */
    private suspend fun resolveLyricsProviders(): List<LyricsProvider> {
        val preferences = context.dataStore.data.first()
        val orderString = preferences[LyricsProviderOrderKey].orEmpty()

        if (orderString.isNotBlank()) {
            return LyricsProviderRegistry.getOrderedProviders(orderString)
        }

        // Migration path: honor the legacy single-provider preference, but only
        // when the user actually picked one — the key is written solely by the
        // settings UI. A never-set key used to force YouLyPlus to the front for
        // everyone, which is exactly the slow-chain behavior the new default
        // order (LrcLib first) exists to fix.
        val preferredEnum = preferences[PreferredLyricsProviderKey]
            ?.let { it.toEnum(PreferredLyricsProvider.YOULYPLUS) }
            ?.let { LyricsProviderRegistry.getProviderNameForEnum(it) }
            ?: return LyricsProviderRegistry.getDefaultProviderOrder()
                .mapNotNull { LyricsProviderRegistry.getProviderByName(it) }

        val defaultOrder = LyricsProviderRegistry.getDefaultProviderOrder()
        val migratedOrder = listOf(preferredEnum) + defaultOrder.filter { it != preferredEnum }
        return migratedOrder.mapNotNull { LyricsProviderRegistry.getProviderByName(it) }
    }



    private val cache = LruCache<String, List<LyricsResult>>(MAX_CACHE_SIZE)
    private var currentLyricsJob: Job? = null

    suspend fun getLyrics(mediaMetadata: MediaMetadata): LyricsWithProvider {
        currentLyricsJob?.cancel()

        val cached = cache.get(mediaMetadata.id)?.firstOrNull()
        if (cached != null) {
            return LyricsWithProvider(cached.lyrics, cached.providerName)
        }

        // Check network connectivity before making network requests
        // Use synchronous check as fallback if flow doesn't emit
        val isNetworkAvailable = try {
            networkConnectivity.isCurrentlyConnected()
        } catch (e: Exception) {
            // If network check fails, try to proceed anyway
            true
        }
        
        if (!isNetworkAvailable) {
            // Still proceed but return not found to avoid hanging
            return LyricsWithProvider(LYRICS_NOT_FOUND, "Unknown")
        }

        val providers = resolveLyricsProviders().usableFor(isLocalSong(mediaMetadata.id))
        val scope = CoroutineScope(SupervisorJob())
        val deferred = scope.async {
            for (provider in providers) {
                if (provider.isEnabled(context)) {
                    try {
                        val result = provider.getLyrics(
                            mediaMetadata.id,
                            mediaMetadata.title,
                            mediaMetadata.artists.joinToString { it.name },
                            mediaMetadata.duration,
                            mediaMetadata.album?.title,
                        )
                        result.onSuccess { lyrics ->
                            return@async LyricsWithProvider(lyrics, provider.name)
                        }.onFailure {
                            reportException(it)
                        }
                    } catch (e: Exception) {
                        // Catch network-related exceptions like UnresolvedAddressException
                        reportException(e)
                    }
                }
            }
            return@async LyricsWithProvider(LYRICS_NOT_FOUND, "Unknown")
        }

        val result = deferred.await()
        scope.cancel()
        return result
    }

    suspend fun getAllLyrics(
        mediaId: String,
        songTitle: String,
        songArtists: String,
        duration: Int,
        album: String? = null,
        callback: (LyricsResult) -> Unit,
    ) {
        currentLyricsJob?.cancel()

        val cacheKey = "$songArtists-$songTitle".replace(" ", "")
        cache.get(cacheKey)?.let { results ->
            results.forEach {
                callback(it)
            }
            return
        }

        // Check network connectivity before making network requests
        // Use synchronous check as fallback if flow doesn't emit
        val isNetworkAvailable = try {
            networkConnectivity.isCurrentlyConnected()
        } catch (e: Exception) {
            // If network check fails, try to proceed anyway
            true
        }
        
        if (!isNetworkAvailable) {
            // Still try to proceed in case of false negative
            return
        }

        val allResult = mutableListOf<LyricsResult>()
        val providers = resolveLyricsProviders().usableFor(isLocalSong(mediaId))
        currentLyricsJob = CoroutineScope(SupervisorJob()).launch {
            providers.forEach { provider ->
                if (provider.isEnabled(context)) {
                    try {
                        provider.getAllLyrics(mediaId, songTitle, songArtists, duration, album) { lyrics ->
                            val result = LyricsResult(provider.name, lyrics)
                            allResult += result
                            callback(result)
                        }
                    } catch (e: Exception) {
                        // Catch network-related exceptions like UnresolvedAddressException
                        reportException(e)
                    }
                }
            }
            cache.put(cacheKey, allResult)
        }

        currentLyricsJob?.join()
    }

    fun cancelCurrentLyricsJob() {
        currentLyricsJob?.cancel()
        currentLyricsJob = null
    }

    /**
     * Local songs carry MediaStore content-URI ids, not YouTube videoIds. The
     * transcript/next-based providers can only ever fail for them, so skip the
     * dead round trips — they sit at the end of the default order and are what
     * made local-song lookups crawl before giving up entirely.
     */
    private fun isLocalSong(id: String): Boolean = id.startsWith("content://")

    private fun List<LyricsProvider>.usableFor(isLocalSong: Boolean): List<LyricsProvider> =
        if (!isLocalSong) this else filterNot { it.name in VIDEO_ID_ONLY_PROVIDERS }

    companion object {
        private const val MAX_CACHE_SIZE = 3
        private val VIDEO_ID_ONLY_PROVIDERS = setOf("YouTubeSubtitle", "YouTubeMusic")
    }
}

data class LyricsResult(
    val providerName: String,
    val lyrics: String,
)

data class LyricsWithProvider(
    val lyrics: String,
    val provider: String,
)