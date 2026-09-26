/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.viewmodels

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.innertube.YouTube
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.Artist
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import kotlinx.coroutines.flow.combine
import com.music.innertube.models.WatchEndpoint
import com.music.innertube.models.BrowseEndpoint
import com.music.innertube.models.YTItem
import com.music.innertube.models.filterExplicit
import com.music.innertube.models.filterVideoSongs
import com.music.innertube.models.filterYoutubeShorts
import com.music.innertube.pages.ExplorePage
import com.music.innertube.pages.HomePage
import com.music.innertube.utils.completed
import com.convx.music.constants.HideExplicitKey
import com.convx.music.constants.HideVideoSongsKey
import com.convx.music.constants.DataSaverEnabledKey
import com.convx.music.constants.HideYoutubeShortsKey
import com.convx.music.constants.InnerTubeCookieKey
import com.convx.music.constants.LocalAlbumsByYearKey
import com.convx.music.constants.LocalOnlyModeKey
import com.convx.music.constants.LocalSongSortDescendingKey
import com.convx.music.constants.LocalSongSortTypeKey
import com.convx.music.constants.PlaylistSortType
import com.convx.music.constants.SongSortType
import com.convx.music.constants.QuickPicks
import com.convx.music.constants.QuickPicksKey
import com.convx.music.constants.ShowWrappedCardKey
import com.convx.music.constants.WrappedSeenKey
import com.convx.music.db.MusicDatabase
import com.convx.music.db.entities.Album
import com.convx.music.db.entities.LocalItem
import com.convx.music.db.entities.Playlist
import com.convx.music.db.entities.Song
import com.convx.music.db.entities.SpeedDialItem
import com.convx.music.extensions.filterVideoSongs
import com.convx.music.extensions.toEnum
import com.convx.music.models.SimilarRecommendation
import com.convx.music.ui.screens.wrapped.WrappedAudioService
import com.convx.music.ui.screens.wrapped.WrappedManager
import com.convx.music.utils.LocalAudioScanner
import com.convx.music.utils.LocalFolderIndex
import com.convx.music.utils.SyncUtils
import com.convx.music.utils.dataStore
import com.convx.music.utils.get
import com.convx.music.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject
import kotlin.random.Random

data class DailyDiscoverItem(
    val seed: Song,
    val recommendation: YTItem,
    val relatedEndpoint: BrowseEndpoint?
)

data class CommunityPlaylistItem(
    val playlist: PlaylistItem,
    val songs: List<SongItem>
)

/** Single immutable snapshot Home's screen observes — collapses the multi-flow fan-out into one State. */
@Immutable
data class HomeUiState(
    val quickPicks: List<Song>? = null,
    val forgottenFavorites: List<Song>? = null,
    val keepListening: List<LocalItem>? = null,
    val topSongs: List<Song>? = null,
    val similarRecommendations: List<SimilarRecommendation>? = null,
    val accountPlaylists: List<PlaylistItem>? = null,
    val homePage: HomePage? = null,
    val explorePage: ExplorePage? = null,
    val dailyDiscover: List<DailyDiscoverItem>? = null,
    val communityPlaylists: List<CommunityPlaylistItem>? = null,
    val allLocalItems: List<LocalItem> = emptyList(),
    val allYtItems: List<YTItem> = emptyList(),
    val localOnlyMode: Boolean = false,
    val localSongs: List<Song> = emptyList(),
    val localAlbums: List<Album> = emptyList(),
    val localArtists: List<com.convx.music.db.entities.Artist> = emptyList(),
    val localPlaylists: List<Playlist> = emptyList(),
    val localFolders: List<LocalFolderIndex.Folder> = emptyList(),
    val speedDialItems: List<YTItem> = emptyList(),
    val selectedChip: HomePage.Chip? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isRandomizing: Boolean = false,
    val accountName: String = "Guest",
    val accountImageUrl: String? = null,
    val showWrappedCard: Boolean = false,
)

private data class HomeUiFeedA(
    val quickPicks: List<Song>?,
    val forgottenFavorites: List<Song>?,
    val keepListening: List<LocalItem>?,
    val topSongs: List<Song>?,
    val similarRecommendations: List<SimilarRecommendation>?,
)

private data class HomeUiFeedB(
    val accountPlaylists: List<PlaylistItem>?,
    val homePage: HomePage?,
    val explorePage: ExplorePage?,
    val dailyDiscover: List<DailyDiscoverItem>?,
    val communityPlaylists: List<CommunityPlaylistItem>?,
)

private data class HomeUiFeedC(
    val allLocalItems: List<LocalItem>,
    val allYtItems: List<YTItem>,
    val localOnlyMode: Boolean,
    val localSongs: List<Song>,
    val localAlbums: List<Album>,
)

private data class HomeUiFeedD(
    val localArtists: List<com.convx.music.db.entities.Artist>,
    val localPlaylists: List<Playlist>,
    val localFolders: List<LocalFolderIndex.Folder>,
    val speedDialItems: List<YTItem>,
    val selectedChip: HomePage.Chip?,
)

private data class HomeUiFlags(
    val isLoading: Boolean,
    val isRefreshing: Boolean,
    val isRandomizing: Boolean,
    val accountName: String,
    val accountImageUrl: String?,
)

/** Second shelf list the home feed is padded from — see [HomeViewModel.loadNetworkDataPhase]. */
private const val NEW_RELEASES_BROWSE_ID = "FEmusic_new_releases"
private const val RECENT_EVENT_WINDOW = 150
private const val RECENT_CARD_COUNT = 12

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext val context: Context,
    val database: MusicDatabase,
    val syncUtils: SyncUtils,
    val wrappedManager: WrappedManager,
    private val wrappedAudioService: WrappedAudioService,
) : ViewModel() {
    val isRefreshing = MutableStateFlow(false)
    val isLoading = MutableStateFlow(false)
    val isRandomizing = MutableStateFlow(false)

    private val quickPicksEnum = context.dataStore.data.map {
        it[QuickPicksKey].toEnum(QuickPicks.QUICK_PICKS)
    }.distinctUntilChanged()

    val quickPicks = MutableStateFlow<List<Song>?>(null)
    val dailyDiscover = MutableStateFlow<List<DailyDiscoverItem>?>(null)
    val forgottenFavorites = MutableStateFlow<List<Song>?>(null)
    val keepListening = MutableStateFlow<List<LocalItem>?>(null)

    // The song playing right now, fed by Home from the player. A play only becomes a history
    // event when ExoPlayer reports its stats (song ended or skipped), so without this the row
    // would lag a whole song behind what the user just started.
    private val nowPlayingId = MutableStateFlow<String?>(null)

    fun onNowPlaying(mediaId: String?) {
        nowPlayingId.value = mediaId
    }

    // Home's Recently Played: the current song, then the newest distinct plays straight from
    // the event table, so it re-emits whenever the user plays something (online or
    // downloaded), unlike the one-shot most-played snapshot in keepListening. 150 events is a
    // window, not a cap: replays of the same song collapse to one card, so a smaller window
    // could leave the row nearly empty.
    val recentlyPlayed: StateFlow<List<LocalItem>?> =
        combine(
            database.recentEvents(RECENT_EVENT_WINDOW),
            nowPlayingId.flatMapLatest { id -> if (id == null) flowOf(null) else database.song(id) },
        ) { events, current ->
            val hideVideos = context.dataStore.get(HideVideoSongsKey, false) ||
                context.dataStore.get(DataSaverEnabledKey, false)
            (listOfNotNull(current) + events.map { it.song })
                .filterNot { it.song.isLocal }
                .distinctBy { it.id }
                .take(RECENT_CARD_COUNT)
                .filterVideoSongs(hideVideos)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val topSongs = MutableStateFlow<List<Song>?>(null)
    val similarRecommendations = MutableStateFlow<List<SimilarRecommendation>?>(null)
    val accountPlaylists = MutableStateFlow<List<PlaylistItem>?>(null)
    val homePage = MutableStateFlow<HomePage?>(null)
    val explorePage = MutableStateFlow<ExplorePage?>(null)
    val communityPlaylists = MutableStateFlow<List<CommunityPlaylistItem>?>(null)
    val selectedChip = MutableStateFlow<HomePage.Chip?>(null)
    private val previousHomePage = MutableStateFlow<HomePage?>(null)

    val allLocalItems = MutableStateFlow<List<LocalItem>>(emptyList())
    val allYtItems = MutableStateFlow<List<YTItem>>(emptyList())

    /** One switch decides whether Home talks to YouTube at all. */
    val localOnlyMode: StateFlow<Boolean> = context.dataStore.data
        .map { it[LocalOnlyModeKey] ?: false }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // Local-only Home rows. Room-backed, so a rescan updates them without a reload.
    val localSongs: StateFlow<List<Song>> = context.dataStore.data
        .map { prefs ->
            (prefs[LocalSongSortTypeKey].toEnum(SongSortType.NAME)) to (prefs[LocalSongSortDescendingKey] ?: false)
        }
        .distinctUntilChanged()
        .flatMapLatest { (sortType, descending) -> database.localSongs(sortType, descending) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    // Newest release first by default, not A-Z: an on-device library is something the
    // user assembled over time, so release order says more about it than the alphabet
    // does. Files with no year tag sort last -- SQLite puts NULLs at the end on DESC.
    val localAlbums: StateFlow<List<Album>> = context.dataStore.data
        .map { it[LocalAlbumsByYearKey] ?: true }
        .distinctUntilChanged()
        .flatMapLatest { byYear ->
            if (byYear) database.albumsLocalByYearDesc() else database.albumsLocalByNameAsc()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val localArtists: StateFlow<List<com.convx.music.db.entities.Artist>> = database.artistsLocalByNameAsc()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    // Playlists the user made here — a synced YouTube playlist has a browseId.
    val localPlaylists: StateFlow<List<Playlist>> =
        database.playlists(PlaylistSortType.CREATE_DATE, true)
            .map { playlists -> playlists.filter { it.playlist.browseId == null } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val localFolders = MutableStateFlow<List<LocalFolderIndex.Folder>>(emptyList())

    val speedDialItems: StateFlow<List<YTItem>> =
        combine(
            database.speedDialDao.getAll(),
            keepListening,
            quickPicks
        ) { pinned, keepListening, quick ->
            val pinnedItems = pinned.map { it.toYTItem() }
            val filled = pinnedItems.toMutableList()
            val targetSize = 27

            if (filled.size < targetSize) {
                // Keep Listening (History/Heavy Rotation)
                keepListening?.let { k ->
                    val needed = targetSize - filled.size
                    val available = k.filter { item ->
                        filled.none { p -> p.id == item.id }
                    }.mapNotNull { item ->
                        when (item) {
                            is Song -> SongItem(
                                id = item.id,
                                title = item.title,
                                artists = item.artists.map { Artist(name = it.name, id = it.id) },
                                thumbnail = item.thumbnailUrl ?: "",
                                explicit = false
                            )
                            is Album -> AlbumItem(
                                browseId = item.id,
                                playlistId = item.album.playlistId ?: "",
                                title = item.title,
                                artists = item.artists.map { Artist(name = it.name, id = it.id) },
                                year = item.album.year,
                                thumbnail = item.thumbnailUrl ?: ""
                            )
                            else -> null
                        }
                    }
                    filled.addAll(available.take(needed))
                }
            }

            if (filled.size < targetSize) {
                // Quick Picks
                quick?.let { q ->
                    val needed = targetSize - filled.size
                    val available = q.filter { song ->
                        filled.none { p -> p.id == song.id }
                    }.map { song ->
                        SongItem(
                            id = song.id,
                            title = song.title,
                            artists = song.artists.map { Artist(name = it.name, id = it.id) },
                            thumbnail = song.thumbnailUrl ?: "",
                            explicit = false
                        )
                    }
                    filled.addAll(available.take(needed))
                }
            }

            filled.take(targetSize)
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    suspend fun getRandomItem(): YTItem? {
        try {
            isRandomizing.value = true
            // Visual feedback for the animation
            kotlinx.coroutines.delay(1000)

            val userSongs = mutableListOf<YTItem>()
            val otherSources = mutableListOf<YTItem>()

            quickPicks.value?.let { songs ->
                userSongs.addAll(songs.map { song ->
                    SongItem(
                        id = song.id,
                        title = song.title,
                        artists = song.artists.map { Artist(name = it.name, id = it.id) },
                        thumbnail = song.thumbnailUrl ?: "",
                        explicit = false
                    )
                })
            }

            keepListening.value?.let { items ->
                items.forEach { item ->
                    when (item) {
                        is Song -> userSongs.add(SongItem(
                            id = item.id,
                            title = item.title,
                            artists = item.artists.map { Artist(name = it.name, id = it.id) },
                            thumbnail = item.thumbnailUrl ?: "",
                            explicit = false
                        ))
                        is Album -> otherSources.add(AlbumItem(
                            browseId = item.id,
                            playlistId = item.album.playlistId ?: "",
                            title = item.title,
                            artists = item.artists.map { Artist(name = it.name, id = it.id) },
                            year = item.album.year,
                            thumbnail = item.thumbnailUrl ?: ""
                        ))
                        else -> {}
                    }
                }
            }

            otherSources.addAll(allYtItems.value)

            // Probability: 80% User Songs, 20% Other Sources
            val item = if (userSongs.isNotEmpty() && (otherSources.isEmpty() || Random.nextFloat() < 0.8f)) {
                userSongs.distinctBy { it.id }.shuffled().firstOrNull()
            } else {
                otherSources.distinctBy { it.id }.shuffled().firstOrNull()
            } ?: userSongs.firstOrNull() ?: otherSources.firstOrNull()

            return item
        } finally {
            isRandomizing.value = false
        }
    }

    val accountName = MutableStateFlow("Guest")
    val accountImageUrl = MutableStateFlow<String?>(null)

    val showWrappedCard: StateFlow<Boolean> = context.dataStore.data.map { prefs ->
        val showWrappedPref = prefs[ShowWrappedCardKey] ?: false
        val seen = prefs[WrappedSeenKey] ?: false
        val isBeforeDate = LocalDate.now().isBefore(LocalDate.of(2026, 2, 1))

        isBeforeDate && (!seen || showWrappedPref)
    }.stateIn(viewModelScope, SharingStarted.Lazily, false)

    val wrappedSeen: StateFlow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[WrappedSeenKey] ?: false
    }.stateIn(viewModelScope, SharingStarted.Lazily, false)

    /** One StateFlow for Home — one collector, one snapshot, one remember key instead of ~26 collectors. */
    val uiState: StateFlow<HomeUiState> = combine(
        combine(quickPicks, forgottenFavorites, recentlyPlayed, topSongs, similarRecommendations) {
                quickPicks, forgottenFavorites, keepListening, topSongs, similarRecommendations ->
            HomeUiFeedA(quickPicks, forgottenFavorites, keepListening, topSongs, similarRecommendations)
        },
        combine(accountPlaylists, homePage, explorePage, dailyDiscover, communityPlaylists) {
                accountPlaylists, homePage, explorePage, dailyDiscover, communityPlaylists ->
            HomeUiFeedB(accountPlaylists, homePage, explorePage, dailyDiscover, communityPlaylists)
        },
        combine(allLocalItems, allYtItems, localOnlyMode, localSongs, localAlbums) {
                allLocalItems, allYtItems, localOnlyMode, localSongs, localAlbums ->
            HomeUiFeedC(allLocalItems, allYtItems, localOnlyMode, localSongs, localAlbums)
        },
        combine(localArtists, localPlaylists, localFolders, speedDialItems, selectedChip) {
                localArtists, localPlaylists, localFolders, speedDialItems, selectedChip ->
            HomeUiFeedD(localArtists, localPlaylists, localFolders, speedDialItems, selectedChip)
        },
        combine(
            combine(isLoading, isRefreshing, isRandomizing, accountName, accountImageUrl) {
                    isLoading, isRefreshing, isRandomizing, accountName, accountImageUrl ->
                HomeUiFlags(isLoading, isRefreshing, isRandomizing, accountName, accountImageUrl)
            },
            showWrappedCard,
        ) { flags, showWrappedCard -> flags to showWrappedCard },
    ) { a, b, c, d, flagsPair ->
        HomeUiState(
            quickPicks = a.quickPicks,
            forgottenFavorites = a.forgottenFavorites,
            keepListening = a.keepListening,
            topSongs = a.topSongs,
            similarRecommendations = a.similarRecommendations,
            accountPlaylists = b.accountPlaylists,
            homePage = b.homePage,
            explorePage = b.explorePage,
            dailyDiscover = b.dailyDiscover,
            communityPlaylists = b.communityPlaylists,
            allLocalItems = c.allLocalItems,
            allYtItems = c.allYtItems,
            localOnlyMode = c.localOnlyMode,
            localSongs = c.localSongs,
            localAlbums = c.localAlbums,
            localArtists = d.localArtists,
            localPlaylists = d.localPlaylists,
            localFolders = d.localFolders,
            speedDialItems = d.speedDialItems,
            selectedChip = d.selectedChip,
            isLoading = flagsPair.first.isLoading,
            isRefreshing = flagsPair.first.isRefreshing,
            isRandomizing = flagsPair.first.isRandomizing,
            accountName = flagsPair.first.accountName,
            accountImageUrl = flagsPair.first.accountImageUrl,
            showWrappedCard = flagsPair.second,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    fun togglePin(item: YTItem) {
        viewModelScope.launch(Dispatchers.IO) {
            val speedDialItem = SpeedDialItem.fromYTItem(item)
            val isPinned = database.speedDialDao.isPinned(speedDialItem.id).first()
            if (isPinned) {
                database.speedDialDao.delete(speedDialItem.id)
            } else {
                database.speedDialDao.insert(speedDialItem)
            }
        }
    }

    fun markWrappedAsSeen() {
        viewModelScope.launch(Dispatchers.IO) {
            context.dataStore.edit {
                it[WrappedSeenKey] = true
            }
        }
    }
    // Track last processed cookie to avoid unnecessary updates
    private var lastProcessedCookie: String? = null
    // Track if we're currently processing account data
    private var isProcessingAccountData = false

    private suspend fun getDailyDiscover() {
        val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false) || context.dataStore.get(DataSaverEnabledKey, false)
        val likedSongs = database.likedSongsByCreateDateAsc().first()
        if (likedSongs.isEmpty()) return

        val seeds = likedSongs.shuffled().distinctBy { it.id }.take(5)

        // Use a synchronized list to collect results safely from concurrent coroutines
        val items = java.util.Collections.synchronizedList(mutableListOf<DailyDiscoverItem>())

        kotlinx.coroutines.coroutineScope {
            seeds.map { seed ->
                launch(Dispatchers.IO) {
                    val endpoint = YouTube.next(WatchEndpoint(videoId = seed.id)).getOrNull()?.relatedEndpoint
                    if (endpoint != null) {
                        YouTube.related(endpoint).onSuccess { page ->
                            val recommendations = page.songs
                                .filter { item ->
                                    if (hideVideoSongs && item.isVideoSong) return@filter false
                                    if (item.explicit) return@filter false
                                    true
                                }
                                .shuffled()

                            // Simple check to avoid immediate duplicate of seed
                            val recommendation = recommendations.firstOrNull { rec ->
                                rec.id != seed.id
                            }

                            if (recommendation != null) {
                                items.add(
                                    DailyDiscoverItem(
                                        seed = seed,
                                        recommendation = recommendation,
                                        relatedEndpoint = endpoint
                                    )
                                )
                            }
                        }
                    }
                }
            }.forEach { it.join() }
        }

        // Final deduplication just in case multiple seeds recommended the same song
        dailyDiscover.value = items.toList().distinctBy { it.recommendation.id }.shuffled()
    }

    private suspend fun getQuickPicks() {
        val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false) || context.dataStore.get(DataSaverEnabledKey, false)
        when (quickPicksEnum.first()) {
            QuickPicks.QUICK_PICKS -> {
                val relatedSongs = database.quickPicks().first().filterVideoSongs(hideVideoSongs)
                val forgotten = database.forgottenFavorites().first().filterVideoSongs(hideVideoSongs).take(8)

                // Get similar songs from YouTube based on recent listening
                val recentSong = database.latestEvent().first()?.song
                val ytSimilarSongs = mutableListOf<Song>()

                if (recentSong != null) {
                    val endpoint = YouTube.next(WatchEndpoint(videoId = recentSong.id)).getOrNull()?.relatedEndpoint
                    if (endpoint != null) {
                        YouTube.related(endpoint).onSuccess { page ->
                            // Convert YouTube songs to local Song format if they exist in database
                            page.songs.take(10).forEach { ytSong ->
                                database.song(ytSong.id).first()?.let { localSong ->
                                    if (!hideVideoSongs || !localSong.song.isVideo) {
                                        ytSimilarSongs.add(localSong)
                                    }
                                }
                            }
                        }
                    }
                }

                // Combine all sources and remove duplicates
                val combined = (relatedSongs + forgotten + ytSimilarSongs)
                    .distinctBy { it.id }
                    .shuffled()
                    .take(20)

                quickPicks.value = combined.ifEmpty { relatedSongs.shuffled().take(20) }
            }
            QuickPicks.LAST_LISTEN -> {
                val song = database.latestEvent().first()?.song
                if (song != null && database.hasRelatedSongs(song.id)) {
                    quickPicks.value = database.getRelatedSongs(song.id).first().filterVideoSongs(hideVideoSongs).shuffled().take(20)
                }
            }
        }
    }

    private suspend fun getCommunityPlaylists() {
        val fromTimeStamp = System.currentTimeMillis() - 86400000L * 7 * 4
        val artistSeeds = database.mostPlayedArtists(fromTimeStamp, limit = 10).first()
            .filter { it.artist.isYouTubeArtist }
            .shuffled().take(3)
        val songSeeds = database.mostPlayedSongs(fromTimeStamp, limit = 5).first()
            .shuffled().take(2)

        val candidatePlaylists = java.util.Collections.synchronizedList(mutableListOf<PlaylistItem>())

        kotlinx.coroutines.coroutineScope {
            artistSeeds.map { seed ->
                launch(Dispatchers.IO) {
                    YouTube.artist(seed.id).onSuccess { page ->
                        page.sections.forEach { section ->
                            section.items.filterIsInstance<PlaylistItem>().forEach { playlist ->
                                if (playlist.author?.name != "YouTube Music" &&
                                    playlist.author?.name != "YouTube" &&
                                    playlist.author?.name != "Playlist" &&
                                    playlist.author?.name != seed.artist.name &&
                                    !playlist.id.startsWith("RD") &&
                                    !playlist.id.startsWith("OLAK")
                                ) {
                                    candidatePlaylists.add(playlist)
                                }
                            }
                        }
                    }
                }
            }

            songSeeds.map { seed ->
                launch(Dispatchers.IO) {
                    val endpoint = YouTube.next(WatchEndpoint(videoId = seed.id)).getOrNull()?.relatedEndpoint
                    if (endpoint != null) {
                        YouTube.related(endpoint).onSuccess { page ->
                            page.playlists.forEach { playlist ->
                                if (playlist.author?.name != "YouTube Music" &&
                                    playlist.author?.name != "YouTube" &&
                                    playlist.author?.name != "Playlist" &&
                                    !playlist.id.startsWith("RD") &&
                                    !playlist.id.startsWith("OLAK")
                                ) {
                                    candidatePlaylists.add(playlist)
                                }
                            }
                        }
                    }
                }
            }
        }

        val uniqueCandidates = candidatePlaylists.distinctBy { it.id }.shuffled().take(5)

        val playlists = java.util.Collections.synchronizedList(mutableListOf<CommunityPlaylistItem>())

        kotlinx.coroutines.coroutineScope {
            uniqueCandidates.map { playlist ->
                launch(Dispatchers.IO) {
                    YouTube.playlist(playlist.id).onSuccess { page ->
                        val songs = page.songs.take(10)
                        if (songs.isNotEmpty()) {
                            // Use song count from the playlist page if available, otherwise use original
                            val songCountText = page.playlist.songCountText ?: playlist.songCountText
                            val updatedPlaylist = playlist.copy(songCountText = songCountText)
                            playlists.add(CommunityPlaylistItem(updatedPlaylist, songs))
                        }
                    }
                }
            }.forEach { it.join() }
        }

        communityPlaylists.value = playlists.shuffled()
    }

    /**
     * Phase 1: Reads all local DB data and immediately drops the loading indicator.
     * Guarantees the UI shows real content before any network call is made.
     */
    private suspend fun loadLocalDataPhase() {
        val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false) || context.dataStore.get(DataSaverEnabledKey, false)

        forgottenFavorites.value = database.forgottenFavorites().first()
            .filterVideoSongs(hideVideoSongs).filterNot { it.song.isLocal }.shuffled().take(20)

        val fromTimeStamp = System.currentTimeMillis() - 86400000L * 7 * 2
        val keepListeningSongs = database.mostPlayedSongs(fromTimeStamp, limit = 15, offset = 5).first()
            .filterVideoSongs(hideVideoSongs).filterNot { it.song.isLocal }.shuffled().take(10)
        val keepListeningAlbums = database.mostPlayedAlbums(fromTimeStamp, limit = 8, offset = 2).first()
            .filter { !it.album.isLocal && it.album.thumbnailUrl != null }.shuffled().take(5)
        val keepListeningArtists = database.mostPlayedArtists(fromTimeStamp).first()
            .filter { it.artist.isYouTubeArtist && it.artist.thumbnailUrl != null }.shuffled().take(5)
        keepListening.value = (keepListeningSongs + keepListeningAlbums + keepListeningArtists).shuffled()

        topSongs.value = database.mostPlayedSongs(fromTimeStamp, limit = 10, offset = 0).first()
            .filterVideoSongs(hideVideoSongs).filterNot { it.song.isLocal }

        publishAllLocalItems()
    }

    /**
     * Quick picks waits on a YouTube round-trip, so [load] runs this beside the other phases
     * instead of making Home's spinner and every network section wait for it.
     */
    private suspend fun loadQuickPicks() {
        getQuickPicks()
        // On-device tracks are the whole of local-only mode, which is a separate Home
        // (see localHomeContent). Leaking them into the online feed put files with no
        // artwork and no metadata beside YouTube rows that have both, so every shelf
        // they landed in looked broken.
        quickPicks.value = quickPicks.value?.filterNot { it.song.isLocal }
        publishAllLocalItems()
    }

    /** Called by both phases; whichever finishes last includes everything. */
    private fun publishAllLocalItems() {
        allLocalItems.value = (quickPicks.value.orEmpty() + forgottenFavorites.value.orEmpty() + keepListening.value.orEmpty())
            .filter { it is Song || it is Album }
    }

    /**
     * Fetches all three recommendation sources (artists, songs, albums) concurrently
     * using async/awaitAll, replacing the previous sequential mapNotNull chains.
     */
    private suspend fun loadSimilarRecommendations() {
        val hideExplicit = context.dataStore.get(HideExplicitKey, false)
        val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false) || context.dataStore.get(DataSaverEnabledKey, false)
        val fromTimeStamp = System.currentTimeMillis() - 86400000L * 7 * 2

        coroutineScope {
            val artistDeferreds = database.mostPlayedArtists(fromTimeStamp, limit = 15).first()
                .filter { it.artist.isYouTubeArtist }
                .shuffled().take(4)
                .map { artist ->
                    async(Dispatchers.IO) {
                        val items = mutableListOf<YTItem>()
                        YouTube.artist(artist.id).onSuccess { page ->
                            page.sections.takeLast(3).forEach { section -> items += section.items }
                        }
                        SimilarRecommendation(
                            title = artist,
                            items = items
                                .distinctBy { item -> item.id }
                                .filterExplicit(hideExplicit)
                                .filterVideoSongs(hideVideoSongs)
                                .shuffled()
                                .take(12)
                                .ifEmpty { return@async null }
                        )
                    }
                }

            val songDeferreds = database.mostPlayedSongs(fromTimeStamp, limit = 15).first()
                .filter { it.album != null }
                .shuffled().take(3)
                .map { song ->
                    async(Dispatchers.IO) {
                        val endpoint = YouTube.next(WatchEndpoint(videoId = song.id)).getOrNull()?.relatedEndpoint
                            ?: return@async null
                        val page = YouTube.related(endpoint).getOrNull() ?: return@async null
                        SimilarRecommendation(
                            title = song,
                            items = (page.songs.shuffled().take(10) +
                                    page.albums.shuffled().take(5) +
                                    page.artists.shuffled().take(3) +
                                    page.playlists.shuffled().take(3))
                                .distinctBy { it.id }
                                .filterExplicit(hideExplicit)
                                .filterVideoSongs(hideVideoSongs)
                                .shuffled()
                                .ifEmpty { return@async null }
                        )
                    }
                }

            val albumDeferreds = database.mostPlayedAlbums(fromTimeStamp, limit = 10).first()
                .filter { it.album.thumbnailUrl != null }
                .shuffled().take(2)
                .map { album ->
                    async(Dispatchers.IO) {
                        val items = mutableListOf<YTItem>()
                        YouTube.album(album.id).onSuccess { page ->
                            page.otherVersions.let { items += it }
                        }
                        album.artists.firstOrNull()?.id?.let { artistId ->
                            YouTube.artist(artistId).onSuccess { page ->
                                page.sections.lastOrNull()?.items?.let { items += it }
                            }
                        }
                        SimilarRecommendation(
                            title = album,
                            items = items
                                .distinctBy { it.id }
                                .filterExplicit(hideExplicit)
                                .filterVideoSongs(hideVideoSongs)
                                .shuffled()
                                .take(10)
                                .ifEmpty { return@async null }
                        )
                    }
                }

            val results = (artistDeferreds + songDeferreds + albumDeferreds).awaitAll()
            similarRecommendations.value = results.filterNotNull().shuffled()
        }
    }

    /**
     * Phase 2: Fires all network sections concurrently.
     * Because isLoading is already false, each section streams into the UI
     * as its data arrives â€” no spinner blocking the user.
     */
    private suspend fun loadNetworkDataPhase() {
        val hideExplicit = context.dataStore.get(HideExplicitKey, false)
        val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false) || context.dataStore.get(DataSaverEnabledKey, false)
        val hideYoutubeShorts = context.dataStore.get(HideYoutubeShortsKey, false)

        coroutineScope {
            launch(Dispatchers.IO) { getDailyDiscover() }
            launch(Dispatchers.IO) { getCommunityPlaylists() }
            launch(Dispatchers.IO) { loadSimilarRecommendations() }
            launch(Dispatchers.IO) {
                // BitChord's trick: FEmusic_home alone is thin (three shelves signed out),
                // so the feed is padded with FEmusic_new_releases, which carries genuinely
                // different rows. Fetched alongside rather than after, and de-duped by
                // title in case home already surfaced the same shelf. Only home's own
                // continuation is kept — new releases is a one-shot list.
                val newReleases = async(Dispatchers.IO) {
                    YouTube.home(browseId = NEW_RELEASES_BROWSE_ID).getOrNull()?.sections.orEmpty()
                }
                YouTube.home().onSuccess { page ->
                    val seenTitles = mutableSetOf<String>()
                    homePage.value = page.copy(
                        sections = (page.sections + newReleases.await()).mapNotNull { section ->
                            if (!seenTitles.add(section.title.lowercase())) return@mapNotNull null
                            val filteredItems = section.items
                                .filterExplicit(hideExplicit)
                                .filterVideoSongs(hideVideoSongs)
                                .filterYoutubeShorts(hideYoutubeShorts)
                            if (filteredItems.isEmpty()) null else section.copy(items = filteredItems)
                        }
                    )
                }.onFailure {
                    newReleases.cancel()
                    reportException(it)
                }
            }
            launch(Dispatchers.IO) {
                YouTube.explore().onSuccess { page ->
                    explorePage.value = page.copy(
                        newReleaseAlbums = page.newReleaseAlbums.filterExplicit(hideExplicit)
                    )
                }.onFailure { reportException(it) }
            }
            if (YouTube.cookie != null) {
                launch(Dispatchers.IO) { loadAccountPlaylists() }
            }
        }

        // Update combined YT items once all network data has settled
        allYtItems.value = similarRecommendations.value?.flatMap { it.items }.orEmpty() +
                homePage.value?.sections?.flatMap { it.items }.orEmpty()
    }

    private suspend fun load() {
        isLoading.value = true

        // Local-only mode never touches the network — the Room flows above already
        // feed Home, so the only thing left to fetch is the folder index.
        if (localOnlyMode.value) {
            localFolders.value = runCatching { LocalFolderIndex.load(context) }.getOrDefault(emptyList())
            isLoading.value = false
            return
        }

        // Phase 1: Local DB only â€” UI renders immediately after this
        kotlinx.coroutines.coroutineScope {
            launch { loadQuickPicks() }
            loadLocalDataPhase()
            isLoading.value = false

            // Phase 2: All network sections in parallel â€” streams in progressively
            loadNetworkDataPhase()
        }
    }

    /** Pull-to-refresh in local-only mode rescans the device instead of the network. */
    private suspend fun rescanLocal() {
        runCatching { LocalAudioScanner.scanAndInsert(context, database) }
        localFolders.value = runCatching { LocalFolderIndex.load(context) }.getOrDefault(emptyList())
    }

    private val _isLoadingMore = MutableStateFlow(false)
    fun loadMoreYouTubeItems(continuation: String?) {
        if (continuation == null || _isLoadingMore.value) return
        val hideExplicit = context.dataStore.get(HideExplicitKey, false)
        val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false) || context.dataStore.get(DataSaverEnabledKey, false)
        val hideYoutubeShorts = context.dataStore.get(HideYoutubeShortsKey, false)

        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingMore.value = true
            val nextSections = YouTube.home(continuation).getOrNull() ?: run {
                _isLoadingMore.value = false
                return@launch
            }

            homePage.value = nextSections.copy(
                chips = homePage.value?.chips,
                sections = (homePage.value?.sections.orEmpty() + nextSections.sections).mapNotNull { section ->
                    val filteredItems = section.items.filterExplicit(hideExplicit).filterVideoSongs(hideVideoSongs).filterYoutubeShorts(hideYoutubeShorts)
                    if (filteredItems.isEmpty()) null else section.copy(items = filteredItems)
                }
            )
            _isLoadingMore.value = false
        }
    }

    fun toggleChip(chip: HomePage.Chip?) {
        if (chip == null || chip == selectedChip.value && previousHomePage.value != null) {
            homePage.value = previousHomePage.value
            previousHomePage.value = null
            selectedChip.value = null
            return
        }

        if (selectedChip.value == null) {
            previousHomePage.value = homePage.value
        }

        viewModelScope.launch(Dispatchers.IO) {
            val hideExplicit = context.dataStore.get(HideExplicitKey, false)
            val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false) || context.dataStore.get(DataSaverEnabledKey, false)
            val hideYoutubeShorts = context.dataStore.get(HideYoutubeShortsKey, false)
            val nextSections = YouTube.home(params = chip.endpoint?.params).getOrNull() ?: return@launch

            homePage.value = nextSections.copy(
                chips = homePage.value?.chips,
                sections = nextSections.sections.map { section ->
                    section.copy(items = section.items.filterExplicit(hideExplicit).filterVideoSongs(hideVideoSongs).filterYoutubeShorts(hideYoutubeShorts))
                }
            )
            selectedChip.value = chip
        }
    }

    private suspend fun loadAccountPlaylists() {
        val hideYoutubeShorts = context.dataStore.get(HideYoutubeShortsKey, false)
        YouTube.library("FEmusic_liked_playlists").completed().onSuccess {
            accountPlaylists.value = it.items.filterIsInstance<PlaylistItem>()
                .filterNot { it.id == "SE" }
                .filterYoutubeShorts(hideYoutubeShorts)
        }.onFailure {
            reportException(it)
        }
    }

    fun refresh() {
        if (isRefreshing.value) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                isRefreshing.value = true
                if (localOnlyMode.value) rescanLocal() else load()
            } finally {
                isRefreshing.value = false
            }
        }
        if (localOnlyMode.value) return
        // Run sync when user manually refreshes
        viewModelScope.launch(Dispatchers.IO) {
            syncUtils.tryAutoSync()
        }
    }

    init {

        // Load home data. Re-runs when local-only mode is toggled, so Home swaps
        // between the YouTube feed and the on-device library without a restart.
        viewModelScope.launch(Dispatchers.IO) {
            context.dataStore.data
                .map { it[InnerTubeCookieKey] }
                .distinctUntilChanged()
                .first()

            localOnlyMode.collect { load() }
        }

        // Run sync in separate coroutine with cooldown to avoid blocking UI
        viewModelScope.launch(Dispatchers.IO) {
            if (!localOnlyMode.first()) syncUtils.tryAutoSync()
        }

        // Prepare wrapped data in background
        viewModelScope.launch(Dispatchers.IO) {
            showWrappedCard.collect { shouldShow ->
                if (shouldShow && !wrappedManager.state.value.isDataReady) {
                    try {
                        wrappedManager.prepare()
                        val state = wrappedManager.state.first { it.isDataReady }
                        val trackMap = state.trackMap
                        if (trackMap.isNotEmpty()) {
                            val firstTrackId = trackMap.entries.first().value
                            wrappedAudioService.prepareTrack(firstTrackId)
                        }
                    } catch (e: Exception) {
                        reportException(e)
                    }
                }
            }
        }

        // Listen for cookie changes and reload account data
        viewModelScope.launch(Dispatchers.IO) {
            context.dataStore.data
                .map { it[InnerTubeCookieKey] }
                .collect { cookie ->
                    // Avoid processing if already processing
                    if (isProcessingAccountData) return@collect

                    // Always process cookie changes, even if same value (for logout/login scenarios)
                    lastProcessedCookie = cookie
                    isProcessingAccountData = true

                    try {
                        if (cookie != null && cookie.isNotEmpty()) {

                            // Update YouTube.cookie manually to ensure it's set
                            YouTube.cookie = cookie

                            // Fetch new account data
                            YouTube.accountInfo().onSuccess { info ->
                                accountName.value = info.name
                                accountImageUrl.value = info.thumbnailUrl
                            }.onFailure {
                                if (it.message != "Active account info not found in header") {
                                    reportException(it)
                                }
                            }
                        } else {
                            accountName.value = "Guest"
                            accountImageUrl.value = null
                            accountPlaylists.value = null
                        }
                    } finally {
                        isProcessingAccountData = false
                    }
                }
        }

        // Listen for HideYoutubeShorts preference changes and reload account playlists instantly
        viewModelScope.launch(Dispatchers.IO) {
            context.dataStore.data
                .map { it[HideYoutubeShortsKey] ?: false }
                .distinctUntilChanged()
                .collect {
                    if (YouTube.cookie != null && accountPlaylists.value != null) {
                        loadAccountPlaylists()
                    }
                }
        }
    }
}
