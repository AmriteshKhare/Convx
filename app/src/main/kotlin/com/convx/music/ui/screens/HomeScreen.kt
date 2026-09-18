/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.ui.screens

import com.convx.music.ui.utils.sharedArtworkSource
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.em
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.convx.music.ui.ios.compat.Button
import androidx.compose.material3.ButtonDefaults
import com.convx.music.ui.ios.compat.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import com.convx.music.ui.ios.compat.Icon
import com.convx.music.ui.ios.compat.LocalContentColor
import com.convx.music.ui.ios.compat.AppTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.surfaceColorAtElevation
import com.convx.music.ui.ios.compat.Text
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.crossfade
import coil3.request.ImageRequest
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import com.music.innertube.models.YTItem
import com.music.innertube.pages.ExplorePage
import com.music.innertube.pages.HomePage
import com.music.innertube.pages.MoodAndGenres
import com.music.innertube.utils.parseCookieString
import com.music.innertube.YouTube
import com.convx.music.constants.InnerTubeCookieKey
import com.convx.music.constants.ListThumbnailSize
import com.convx.music.constants.LocalSongSortDescendingKey
import com.convx.music.constants.HideHomeFavoriteIconKey
import com.convx.music.constants.LocalSongSortTypeKey
import com.convx.music.constants.RandomizeHomeOrderKey
import com.convx.music.constants.SongSortType
import com.convx.music.constants.ShowHomeFabKey
import com.convx.music.ui.component.homeCardRim
import com.convx.music.ui.component.rememberHomeCardShape
import com.convx.music.constants.HomeGridColumnsOverrideKey
import com.convx.music.constants.HomeHeroCardEnabledKey
import com.convx.music.constants.HomeSectionHiddenKey
import com.convx.music.constants.HomeSectionOrderKey
import com.convx.music.constants.ShowHomeMoodFiltersKey
import com.convx.music.constants.ThumbnailCornerRadius
import com.convx.music.db.MusicDatabase
import com.convx.music.ui.component.SpotlightCard
import com.convx.music.ui.component.SpotlightCardArtworkSize
import com.convx.music.ui.component.SpotlightCardHeight
import com.convx.music.ui.component.SpotlightCardInfoHeight
import com.convx.music.ui.component.SpotlightCardSpacing
import com.convx.music.db.entities.Album
import com.convx.music.db.entities.Artist
import com.convx.music.db.entities.LocalItem
import com.convx.music.db.entities.Playlist
import com.convx.music.db.entities.PlaylistEntity
import com.convx.music.db.entities.Song
import com.convx.music.extensions.toMediaItem
import com.convx.music.ui.utils.bleedStart
import com.convx.music.ui.utils.plusStart
import com.convx.music.ui.utils.bounceClick
import com.convx.music.utils.LocalFolderIndex
import com.convx.music.ui.utils.combinedBounceClick
import com.convx.music.ui.utils.albumRoute
import com.convx.music.ui.utils.artistRoute
import com.convx.music.ui.utils.localPlaylistRoute
import com.convx.music.ui.utils.onlinePlaylistRoute
import com.convx.music.LocalDatabase
import com.convx.music.LocalPlayerAwareWindowInsets
import com.convx.music.LocalPlayerConnection
import com.convx.music.LocalTabView
import com.convx.music.models.MediaMetadata
import com.convx.music.models.SimilarRecommendation
import com.convx.music.models.toMediaMetadata
import com.convx.music.playback.PlayerConnection
import com.convx.music.playback.queues.ListQueue
import com.convx.music.playback.queues.LocalAlbumRadio
import com.convx.music.playback.queues.YouTubeAlbumRadio
import com.convx.music.playback.queues.YouTubeQueue
import com.convx.music.R
import com.composables.sfsymbols.SfSymbols
import com.composables.sfsymbols.monochrome.SFPersonCropCircle
import com.convx.music.ui.component.AlbumGridItem
import com.convx.music.ui.component.ChipsRow
import com.convx.music.ui.component.ListItem
import com.convx.music.ui.component.PlaylistListItem
import com.convx.music.ui.component.ArtistGridItem
import com.convx.music.ui.component.HideOnScrollFAB
import com.convx.music.ui.component.HomeHeroCard
import com.convx.music.ui.component.HomeImageBackground
import com.convx.music.ui.component.LocalBottomSheetPageState
import com.convx.music.ui.component.LocalHomeTopBarLogoProgress
import com.convx.music.ui.component.LocalMenuState
import com.convx.music.ui.component.MenuState
import com.convx.music.ui.component.NavigationTitle
import com.convx.music.ui.component.rememberAppBackgroundColor
import com.convx.music.ui.component.rememberTitleCollapseProgress
import com.convx.music.ui.component.rememberTitleCollapseProgressState
import com.convx.music.ui.component.shimmer.GridItemPlaceHolder
import com.convx.music.ui.component.shimmer.ShimmerHost
import com.convx.music.ui.component.shimmer.TextPlaceholder
import com.convx.music.ui.component.SongGridItem
import com.convx.music.ui.component.SortHeader
import com.convx.music.ui.menu.AlbumMenu
import com.convx.music.ui.menu.ArtistMenu
import com.convx.music.ui.menu.SongMenu
import com.convx.music.ui.menu.YouTubeAlbumMenu
import com.convx.music.ui.menu.YouTubeArtistMenu
import com.convx.music.ui.menu.YouTubePlaylistMenu
import com.convx.music.ui.menu.YouTubeSongMenu
import com.convx.music.ui.theme.AppleTokens
import com.convx.music.ui.theme.BrandName
import com.convx.music.ui.theme.rememberBrandFontFamily
import com.convx.music.ui.theme.rememberGlobalAccentColors
import com.convx.music.utils.listItemShape
import com.convx.music.utils.rememberPreference
import com.convx.music.viewmodels.CommunityPlaylistItem
import com.convx.music.viewmodels.HomeViewModel
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.convx.music.viewmodels.DailyDiscoverItem


/**
 * Home item budget. Content alone (glass bypassed) measured 14ms per frame against an
 * 8.33ms/120Hz budget, driven by how many rich rows are composed at once. The two song
 * grids used to stack 4 rows each, so ~40 rows could be live simultaneously. Two rows
 * matches the survey's "2x2 grid" preference for recent activity and halves that.
 *
 * Raised from 2/3 for a fuller feed (BitChord renders every shelf its browse call
 * returns) — doubled rather than uncapped, since the number above is measured against a
 * real frame budget and this hasn't been re-measured against the new cap. Each shelf is
 * one lazy `item {}`, so raising the cap only means more rows exist to scroll to, not
 * more composed at once.
 */
private const val MaxSimilarSections = 4
private const val MaxHomePageSections = 6

/** Rows of album/artist tiles a local shelf shows before the full list takes over. */
private const val LocalShelfRows = 2

/** Playlist and folder rows on Home. The rest are one tap away in Library. */
private const val LocalListPreview = 6

sealed class HomeSection(val id: String, val baseWeight: Int) {
    /** The "star of the day" card. Always first, never shuffled. */
    data object Hero : HomeSection("hero", 110)
    data object SpeedDial : HomeSection("speed_dial", 100)
    data object QuickPicks : HomeSection("quick_picks", 90)
    data object DailyDiscover : HomeSection("daily_discover", 80)
    data object KeepListening : HomeSection("keep_listening", 50)
    data object TopSongs : HomeSection("top_songs", 55)
    data object AccountPlaylists : HomeSection("account_playlists", 40)
    data object ForgottenFavorites : HomeSection("forgotten_favorites", 30)
    data object FromTheCommunity : HomeSection("from_the_community", 20)
    data class SimilarRecommendation(val index: Int) : HomeSection("similar_recommendation_$index", 10)
    data class HomePageSection(val index: Int) : HomeSection("home_page_section_$index", 10)
    data object MoodAndGenres : HomeSection("mood_and_genres", 5)
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    navController: NavController,
    snackbarHostState: SnackbarHostState,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val bottomSheetPageState = LocalBottomSheetPageState.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val haptic = LocalHapticFeedback.current

    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()

    val quickPicks by viewModel.quickPicks.collectAsStateWithLifecycle()
    val forgottenFavorites by viewModel.forgottenFavorites.collectAsStateWithLifecycle()
    val keepListening by viewModel.keepListening.collectAsStateWithLifecycle()
    val topSongs by viewModel.topSongs.collectAsStateWithLifecycle()
    val similarRecommendations by viewModel.similarRecommendations.collectAsStateWithLifecycle()
    val accountPlaylists by viewModel.accountPlaylists.collectAsStateWithLifecycle()
    val homePage by viewModel.homePage.collectAsStateWithLifecycle()
    val explorePage by viewModel.explorePage.collectAsStateWithLifecycle()
    val dailyDiscover by viewModel.dailyDiscover.collectAsStateWithLifecycle()
    val communityPlaylists by viewModel.communityPlaylists.collectAsStateWithLifecycle()

    val allLocalItems by viewModel.allLocalItems.collectAsStateWithLifecycle()
    val allYtItems by viewModel.allYtItems.collectAsStateWithLifecycle()

    // Local-only mode: same route, same chrome, on-device library instead of the feed.
    val localOnly by viewModel.localOnlyMode.collectAsStateWithLifecycle()
    val localSongs by viewModel.localSongs.collectAsStateWithLifecycle()
    val localAlbums by viewModel.localAlbums.collectAsStateWithLifecycle()
    val localArtists by viewModel.localArtists.collectAsStateWithLifecycle()
    val localPlaylists by viewModel.localPlaylists.collectAsStateWithLifecycle()
    val localFolders by viewModel.localFolders.collectAsStateWithLifecycle()

    // Wide layout: Home's recently-played row grows a hero tile (see keepListeningSection).
    val tabView = LocalTabView.current
    val speedDialItems by viewModel.speedDialItems.collectAsStateWithLifecycle()
    val selectedChip by viewModel.selectedChip.collectAsStateWithLifecycle()

    val isLoading: Boolean by viewModel.isLoading.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val isRandomizing by viewModel.isRandomizing.collectAsStateWithLifecycle()
    val pullRefreshState = rememberPullToRefreshState()


    val accountName by viewModel.accountName.collectAsStateWithLifecycle()
    val accountImageUrl by viewModel.accountImageUrl.collectAsStateWithLifecycle()
    val innerTubeCookie by rememberPreference(InnerTubeCookieKey, "")
    // Default flipped to false: 2 of 3 survey responses asked for a fixed order
    // ("fixed order, I can find things faster"); the third wanted only the top section
    // pinned. Users who explicitly enabled shuffling keep it.
    val (randomizeHomeOrder) = rememberPreference(RandomizeHomeOrderKey, false)

    val shouldShowWrappedCard by viewModel.showWrappedCard.collectAsStateWithLifecycle()
    val wrappedState by viewModel.wrappedManager.state.collectAsStateWithLifecycle()
    val isWrappedDataReady = wrappedState.isDataReady

    val isLoggedIn = remember(innerTubeCookie) {
        "SAPISID" in parseCookieString(innerTubeCookie)
    }
    val url = if (isLoggedIn) accountImageUrl else null

    val scope = rememberCoroutineScope()
    // Track randomization job. MutableState<Job?> so SpeedDial's section composable
    // (a LazyListScope extension, not a @Composable) can swap the job in and out.
    val randomizeJob = remember { mutableStateOf<Job?>(null) }

    val lazylistState = rememberLazyListState()
    // Drives the crossfade between this screen's own big logo item and the
    // small one MainActivity's top bar shows once it's scrolled away — see
    // LocalHomeTopBarLogoProgress for why the hand-off is a shared float
    // rather than something threaded through the Scaffold.
    val homeTopBarLogoProgress = LocalHomeTopBarLogoProgress.current
    val logoCollapseProgress = rememberTitleCollapseProgressState(lazylistState)
    LaunchedEffect(logoCollapseProgress, homeTopBarLogoProgress) {
        snapshotFlow { logoCollapseProgress.value }
            .collect { homeTopBarLogoProgress.floatValue = it }
    }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val scrollToTop =
        backStackEntry?.savedStateHandle?.getStateFlow("scrollToTop", false)?.collectAsStateWithLifecycle()

    val wrappedDismissed by backStackEntry?.savedStateHandle?.getStateFlow("wrapped_seen", false)
        ?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(false) }

    var randomSeed by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            randomSeed = System.currentTimeMillis()
        }
    }

    val foundInSettings = stringResource(R.string.found_in_settings_content)
    LaunchedEffect(wrappedDismissed) {
        if (wrappedDismissed) {
            viewModel.markWrappedAsSeen()
            scope.launch {
                snackbarHostState.showSnackbar(foundInSettings)
            }
            backStackEntry?.savedStateHandle?.set("wrapped_seen", false) // Reset the value
        }
    }

    LaunchedEffect(scrollToTop?.value) {
        if (scrollToTop?.value == true) {
            lazylistState.animateScrollToItem(0)
            backStackEntry?.savedStateHandle?.set("scrollToTop", false)
        }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { lazylistState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastVisibleIndex ->
                val len = lazylistState.layoutInfo.totalItemsCount
                if (!localOnly && lastVisibleIndex != null && lastVisibleIndex >= len - 3) {
                    viewModel.loadMoreYouTubeItems(homePage?.continuation)
                }
            }
    }

    NetworkReload(
        onReload = viewModel::refresh
    )

    if (selectedChip != null) {
        BackHandler {
            // if a chip is selected, go back to the normal homepage first
            viewModel.toggleChip(selectedChip)
        }
    }

    val (hideHomeFavoriteIcon) = rememberPreference(HideHomeFavoriteIconKey, false)

    // The tap behaviour of localGridItem below, reachable without rendering a tile —
    // the spotlight row draws its own card but wants the same navigation.
    val onLocalItemClick: (LocalItem) -> Unit = { localItem ->
        when (localItem) {
            is Song -> if (localItem.id == mediaMetadata?.id) {
                playerConnection.togglePlayPause()
            } else {
                playerConnection.playQueue(YouTubeQueue.radio(localItem.toMediaMetadata()))
            }

            is Album -> navController.navigate(albumRoute(localItem.id, localItem.thumbnailUrl))
            is Artist -> navController.navigate(artistRoute(localItem.id, localItem.thumbnailUrl))
            // Matches localGridItem, which also renders no tap target for a playlist
            // here: this shelf is built from songs and albums only.
            is Playlist -> Unit
        }
    }

    val localGridItem: @Composable (LocalItem) -> Unit = {
        when (it) {
            is Song -> SongGridItem(
                song = it,
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedBounceClick(
                        onClick = {
                            if (it.id == mediaMetadata?.id) {
                                playerConnection.togglePlayPause()
                            } else {
                                playerConnection.playQueue(
                                    YouTubeQueue.radio(it.toMediaMetadata()),
                                )
                            }
                        },
                        onLongClick = {
                            haptic.performHapticFeedback(
                                HapticFeedbackType.LongPress,
                            )
                            menuState.show {
                                SongMenu(
                                    originalSong = it,
                                    navController = navController,
                                    onDismiss = menuState::dismiss,
                                )
                            }
                        },
                    ),
                isActive = it.id == mediaMetadata?.id,
                isPlaying = isPlaying,
                showLikedIcon = !hideHomeFavoriteIcon,
                // GridItem's own fixed-width sizing otherwise wins over this
                // Modifier.fillMaxWidth() (it's applied after, in GridItem's
                // internal chain) — barely visible in a phone's ~164dp-wide
                // weighted slot, but leaves a large gap in tab view's much
                // wider one. This is the actual switch that makes the tile
                // stretch instead of the outer modifier alone.
                fillMaxWidth = true,
            )

            is Album -> AlbumGridItem(
                album = it,
                isActive = it.id == mediaMetadata?.album?.id,
                isPlaying = isPlaying,
                coroutineScope = scope,
                showLikedIcon = !hideHomeFavoriteIcon,
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedBounceClick(
                        onClick = {
                            navController.navigate(albumRoute(it.id, it.thumbnailUrl))
                        },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuState.show {
                                AlbumMenu(
                                    originalAlbum = it,
                                    navController = navController,
                                    onDismiss = menuState::dismiss
                                )
                            }
                        }
                    ),
                fillMaxWidth = true,
            )

            is Artist -> ArtistGridItem(
                artist = it,
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedBounceClick(
                        onClick = {
                            navController.navigate(artistRoute(it.id, it.thumbnailUrl))
                        },
                        onLongClick = {
                            haptic.performHapticFeedback(
                                HapticFeedbackType.LongPress,
                            )
                            menuState.show {
                                ArtistMenu(
                                    originalArtist = it,
                                    coroutineScope = scope,
                                    onDismiss = menuState::dismiss,
                                )
                            }
                        },
                    ),
                fillMaxWidth = true,
            )

            is Playlist -> {}
        }
    }

    val (heroCardEnabled) = rememberPreference(HomeHeroCardEnabledKey, true)
    val (savedSectionOrder) = rememberPreference(HomeSectionOrderKey, "")
    val (hiddenSectionIds) = rememberPreference(HomeSectionHiddenKey, "")
    val (showMoodFilters) = rememberPreference(ShowHomeMoodFiltersKey, true)
    val (homeGridColumnsOverride) = rememberPreference(HomeGridColumnsOverrideKey, 0)
    val keepListeningColumns = if (homeGridColumnsOverride > 0) homeGridColumnsOverride else 2

    val homeSections = remember(
        heroCardEnabled,
        randomizeHomeOrder,
        // Without this key a reorder saved in settings would not reach Home until
        // something else invalidated the list.
        savedSectionOrder,
        hiddenSectionIds,
        randomSeed,
        speedDialItems,
        quickPicks,
        dailyDiscover,
        keepListening,
        accountPlaylists,
        forgottenFavorites,
        communityPlaylists,
        similarRecommendations,
        homePage?.sections,
        explorePage?.moodAndGenres
    ) {
        val hidden = hiddenSectionIds.lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toSet()
        val list = mutableListOf<HomeSection>()

        if (heroCardEnabled && quickPicks?.isNotEmpty() == true) list.add(HomeSection.Hero)
        // Speed Dial section commented out per request -- gated out here rather than
        // deleting the section's rendering code, so it is one line to bring back.
        // if (speedDialItems.isNotEmpty()) list.add(HomeSection.SpeedDial)
        if (quickPicks?.isNotEmpty() == true) list.add(HomeSection.QuickPicks)
        if (communityPlaylists?.isNotEmpty() == true) list.add(HomeSection.FromTheCommunity)
        if (dailyDiscover?.isNotEmpty() == true) list.add(HomeSection.DailyDiscover)
        if (keepListening?.isNotEmpty() == true) list.add(HomeSection.KeepListening)
        if (topSongs?.isNotEmpty() == true) list.add(HomeSection.TopSongs)
        if (accountPlaylists?.isNotEmpty() == true) list.add(HomeSection.AccountPlaylists)
        if (forgottenFavorites?.isNotEmpty() == true) list.add(HomeSection.ForgottenFavorites)

        // Capped. The survey's most concrete complaint was "I have to see 4 to 6
        // similar-to sections that I don't want", and each extra section is ~12 more
        // simultaneously composed rows — the single largest driver of Home's frame cost
        // (content alone measured 14ms/frame against an 8.33ms budget).
        similarRecommendations?.indices?.take(MaxSimilarSections)?.forEach { i ->
            list.add(HomeSection.SimilarRecommendation(i))
        }

        homePage?.sections?.indices?.take(MaxHomePageSections)?.forEach { i ->
            list.add(HomeSection.HomePageSection(i))
        }

        if (explorePage?.moodAndGenres != null) list.add(HomeSection.MoodAndGenres)

        // Filtered once here rather than at each `if` above: the section list is built
        // from a dozen separate conditions, and a hidden check on every one of them is a
        // dozen places for a new section to be added without it.
        list.retainAll { it.id !in hidden }

        val customOrder = savedSectionOrder.lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toList()

        if (customOrder.isNotEmpty()) {
            // An explicit arrangement beats both the shuffle and the default weights --
            // the user has said what they want to see first.
            //
            // Sections the saved order does not mention keep their relative default order
            // and go after the ones it does. That is what lets a release add a section
            // without scrambling an arrangement saved by an older version: the new one
            // simply appears at the end instead of landing somewhere arbitrary.
            list.sortedWith(
                compareBy(
                    { section ->
                        val at = customOrder.indexOf(section.id)
                        if (at >= 0) at else Int.MAX_VALUE
                    },
                    { section -> -section.baseWeight },
                ),
            )
        } else if (randomizeHomeOrder) {
            list.sortedByDescending { section ->
                // Use a stable seed for each section based on the session seed + section ID hash
                // This ensures the weight for a specific section remains constant during a session (until refresh)
                // even if other sections appear/disappear, preventing jumping.
                val sectionRandom = Random(randomSeed + section.id.hashCode())

                // Flatten the base values to allow for more overlap and variation
                // All "main" sections start closer together
                val base = when (section) {
                    // Pinned above everything, even with shuffling on — the one survey
                    // response that wanted shuffling still asked to keep the top fixed.
                    HomeSection.Hero -> 10_000

                    HomeSection.SpeedDial,
                    HomeSection.QuickPicks,
                    HomeSection.DailyDiscover -> 500 // Top tier starts equal

                    HomeSection.KeepListening,
                    HomeSection.AccountPlaylists,
                    HomeSection.ForgottenFavorites,
                    HomeSection.FromTheCommunity -> 300 // Middle tier starts equal

                    else -> 100 // Bottom tier
                }

                val modifier = when (section) {
                    // Top tier: High variance to allow shuffling among themselves
                    // Range: [500-200, 500+400] = [300, 900]
                    HomeSection.SpeedDial,
                    HomeSection.QuickPicks,
                    HomeSection.DailyDiscover -> sectionRandom.nextInt(-200, 400)

                    // Middle tier: Can jump up to challenge top tier, or drop lower
                    // Range: [300-100, 300+400] = [200, 700]
                    // This allows them to occasionally appear above a "bad roll" top tier item
                    HomeSection.KeepListening,
                    HomeSection.AccountPlaylists,
                    HomeSection.ForgottenFavorites,
                    HomeSection.FromTheCommunity -> sectionRandom.nextInt(-100, 400)

                    // Bottom tier: Standard variance
                    else -> sectionRandom.nextInt(-50, 50)
                }
                base + modifier
            }
        } else {
            // Recently played leads: it is the grid the whole screen is built around,
            // and the one thing a user opening the app most often wants. Speed Dial
            // drops below Quick Picks — as the first thing on screen its wall of
            // tiles buried everything else.
            val defaultOrder = mapOf(
                HomeSection.Hero to 110,
                HomeSection.KeepListening to 100,
                HomeSection.QuickPicks to 90,
                HomeSection.SpeedDial to 80,
                HomeSection.FromTheCommunity to 70,
                HomeSection.DailyDiscover to 60,
                HomeSection.AccountPlaylists to 50,
                HomeSection.ForgottenFavorites to 40,
                HomeSection.MoodAndGenres to 10
            )

            list.sortedByDescending { section ->
                when(section) {
                    is HomeSection.SimilarRecommendation -> 30 - section.index
                    is HomeSection.HomePageSection -> 20 - section.index
                    else -> defaultOrder[section] ?: 0
                }
            }
        }
    }

    // One batched query per list instead of a per-row database.song() Flow. The rows
    // below were each opening their own Room observation for the same ~10 ids, and
    // every visible tile subscribed for the whole time it stayed composed. A single
    // songsByIds flow still re-emits when any of those rows change, so the live-update
    // behaviour is unchanged — just one observer instead of N.
    val quickPicksSongMap = rememberSongsById(
        database,
        remember(quickPicks) { quickPicks.orEmpty().map { it.id } },
    )
    val forgottenFavoritesSongMap = rememberSongsById(
        database,
        remember(forgottenFavorites) { forgottenFavorites.orEmpty().map { it.id } },
    )

    PullToRefreshBox(
        state = pullRefreshState,
        // Local mode has nothing to pull for: there is no feed to re-fetch, and the
        // rescan it used to trigger already runs on entering the library and from the
        // scan screen. Both the action and its indicator are gated, so the gesture does
        // nothing and shows nothing.
        isRefreshing = isRefreshing && !localOnly,
        onRefresh = { if (!localOnly) viewModel.refresh() },
        indicator = {
            // Material3's expressive LoadingIndicator morphs its shape on an
            // infinite animation for as long as it is composed — it does not
            // gate on isRefreshing. Left in the tree it awaits a frame forever,
            // so the whole app recomposed and redrew every vsync at idle (~50
            // draws/s on a motionless Home) for an indicator scaled to nothing.
            // Compose it only while it can actually be seen.
            if (!localOnly && (isRefreshing || pullRefreshState.distanceFraction > 0f)) {
                PullToRefreshDefaults.LoadingIndicator(
                    state = pullRefreshState,
                    isRefreshing = isRefreshing,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(LocalPlayerAwareWindowInsets.current.asPaddingValues()),
                )
            }
        }
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopStart
        ) {
            // Flat fill behind everything — a no-op (matches the default
            // Scaffold background) unless the user picked a specific custom
            // theme color, in which case that becomes Home's actual background.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(rememberAppBackgroundColor(AppTheme.colorScheme.background))
            )
            HomeImageBackground(contentLoaded = homePage != null)

            // Space the floating side bar occupies. The vertical list keeps it as
            // content padding so rows stay clear of the panel; the carousels below
            // give it back with bleedStart so their cards run underneath the glass
            // instead of stopping at its edge.
            val sideInset = LocalPlayerAwareWindowInsets.current
                .asPaddingValues()
                .calculateStartPadding(LocalLayoutDirection.current)

            val deps = HomeSectionDeps(
                viewModel = viewModel,
                navController = navController,
                playerConnection = playerConnection,
                menuState = menuState,
                haptic = haptic,
                scope = scope,
                sideInset = sideInset,
                localGridItem = localGridItem,
                onLocalItemClick = onLocalItemClick,
            )

            LazyColumn(
                state = lazylistState,
                modifier = Modifier,
                // Sections had nothing but NavigationTitle's own 12dp padding
                // between them — ran into each other, felt cramped.
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
            ) {
                // The big logo, scrolled with the content rather than pinned in the
                // app bar: the bar is transparent chrome here, and this is what
                // gives the first screenful its weight. Fades out as the small
                // wordmark in MainActivity's top bar fades in — see
                // logoCollapseProgress above.
                item(key = "listen_now_title", contentType = "section_title") {
                    Text(
                        text = stringResource(R.string.home),
                        style = AppTheme.typography.displayLarge.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.02).em,
                        ),
                        color = LocalContentColor.current,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(
                                WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)
                            )
                            .padding(
                                start = AppleTokens.Gutter,
                                end = AppleTokens.Gutter,
                                top = AppleTokens.ItemGap,
                                bottom = AppleTokens.TextGap,
                            )
                            .graphicsLayer { alpha = 1f - logoCollapseProgress.value }
                            .animateItem(),
                    )
                }

                // YouTube's own mood/genre filter chips (Energize, Relax, Feel good...),
                // pinned right under the title rather than folded into the reorderable
                // "Mood and Genres" shelf below — a filter control belongs where it's
                // always reachable, not wherever that shelf happens to sort to.
                val moodChips = homePage?.chips
                if (showMoodFilters && !localOnly && !moodChips.isNullOrEmpty()) {
                    item(key = "mood_filters", contentType = "mood_filters") {
                        ChipsRow(
                            // null is a real option, not a placeholder: it is how the user
                            // clears the filter and returns to the unfiltered feed.
                            chips = listOf<Pair<HomePage.Chip?, String>>(
                                null to stringResource(R.string.filter_all),
                            ) + moodChips.map { chip -> chip to chip.title },
                            currentValue = selectedChip,
                            onValueUpdate = { chip -> viewModel.toggleChip(chip) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }

                if (selectedChip == null && !localOnly) {
                    item(key = "wrapped_card", contentType = "wrapped_card") {
                        AnimatedVisibility(visible = shouldShowWrappedCard) {
                            val wrappedCardShape = rememberHomeCardShape()
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                                    .homeCardRim(wrappedCardShape),
                                colors = CardDefaults.cardColors(
                                    containerColor = LocalContentColor.current.copy(alpha = 0.1f),
                                ),
                                shape = wrappedCardShape
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isWrappedDataReady) {
                                        val bbhFont = try {
                                            FontFamily(Font(R.font.bbh_bartle_regular))
                                        } catch (e: Exception) {
                                            FontFamily.Default
                                        }
                                        Column(
                                            modifier = Modifier.padding(16.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                                        ) {
                                            Text(
                                                text = stringResource(R.string.wrapped_ready_title),
                                                style = AppTheme.typography.headlineLarge.copy(
                                                    fontFamily = bbhFont,
                                                    textAlign = TextAlign.Center
                                                )
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = stringResource(R.string.wrapped_ready_subtitle),
                                                style = AppTheme.typography.bodyLarge.copy(
                                                    textAlign = TextAlign.Center
                                                )
                                            )
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Button(onClick = {
                                                navController.navigate("wrapped")
                                            }) {
                                                Text(stringResource(R.string.open))
                                            }
                                        }
                                    } else {
                                        com.convx.music.ui.component.ShimmerBox(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp)
                                                .height(160.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (localOnly) {
                    localHomeContent(
                        deps = deps,
                        songs = localSongs,
                        albums = localAlbums,
                        artists = localArtists,
                        playlists = localPlaylists,
                        folders = localFolders,
                        columns = keepListeningColumns,
                    )
                } else homeSectionsContent(
                    sections = homeSections,
                    deps = deps,
                    speedDialItems = speedDialItems,
                    quickPicks = quickPicks,
                    forgottenFavorites = forgottenFavorites,
                    dailyDiscover = dailyDiscover,
                    keepListening = keepListening,
                    topSongs = topSongs,
                    accountPlaylists = accountPlaylists,
                    communityPlaylists = communityPlaylists,
                    similarRecommendations = similarRecommendations,
                    homePage = homePage,
                    explorePage = explorePage,
                    mediaMetadata = mediaMetadata,
                    isPlaying = isPlaying,
                    isRandomizing = isRandomizing,
                    randomizeJob = randomizeJob,
                    quickPicksSongMap = quickPicksSongMap,
                    forgottenFavoritesSongMap = forgottenFavoritesSongMap,
                    accountName = accountName,
                    url = url,
                    keepListeningColumns = keepListeningColumns,
                    tabView = tabView,
                )
                // Only while a fetch is actually in flight. The old condition also kept this
                // composed whenever a continuation merely EXISTED, so parking at the bottom of
                // Home left the shimmer mounted forever — and its infinite transition drove
                // ~65 recompositions/s with nothing on screen moving (328 Recomposer:animation
                // sections in a 5s trace). Reaching the bottom triggers loadMoreYouTubeItems
                // immediately, so the placeholder still appears for every real load.
                if (isLoading) {
                    item(key = "loading_shimmer", contentType = "shimmer") {
                        ShimmerHost(
                            modifier = Modifier.animateItem()
                        ) {
                            repeat(2) {
                                TextPlaceholder(
                                    height = 36.dp,
                                    modifier = Modifier
                                        .padding(AppleTokens.Gutter)
                                        .width(250.dp),
                                )
                                LazyRow(
                                    modifier = Modifier.bleedStart(sideInset),
                                    contentPadding = PaddingValues(horizontal = AppleTokens.ItemGap / 2)
                                        .plusStart(sideInset),
                                ) {
                                    items(4) {
                                        GridItemPlaceHolder()
                                    }
                                }
                            }

                            TextPlaceholder(
                                height = 36.dp,
                                modifier = Modifier
                                    .padding(AppleTokens.Gutter)
                                    .width(250.dp),
                            )
                            repeat(4) {
                                Row {
                                    repeat(2) {
                                        TextPlaceholder(
                                            height = MoodAndGenresButtonHeight,
                                            shape = RoundedCornerShape(AppleTokens.Control),
                                            modifier = Modifier
                                                .padding(AppleTokens.ItemGap / 2)
                                                .width(200.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item(key = "bottom_spacer", contentType = "spacer") {
                    Spacer(modifier = Modifier.height(30.dp))
                }
            }

            val (showHomeFab) = rememberPreference(ShowHomeFabKey, true)
            if (showHomeFab) {
                HideOnScrollFAB(
                    visible = if (localOnly) localSongs.isNotEmpty()
                    else allLocalItems.isNotEmpty() || allYtItems.isNotEmpty(),
                    lazyListState = lazylistState,
                    icon = R.drawable.shuffle,
                    onClick = {
                        if (localOnly) {
                            // Nothing to pick a radio from offline — shuffle the library itself.
                            playerConnection.playQueue(
                                ListQueue(items = localSongs.shuffled().map { it.toMediaItem() }, startIndex = 0),
                            )
                            return@HideOnScrollFAB
                        }
                        val local = when {
                            allLocalItems.isNotEmpty() && allYtItems.isNotEmpty() -> Random.nextFloat() < 0.5
                            allLocalItems.isNotEmpty() -> true
                            else -> false
                        }
                        scope.launch(Dispatchers.Main) {
                            if (local) {
                                when (val luckyItem = allLocalItems.random()) {
                                    is Song -> playerConnection.playQueue(YouTubeQueue.radio(luckyItem.toMediaMetadata()))
                                    is Album -> {
                                        val albumWithSongs = withContext(Dispatchers.IO) {
                                            database.albumWithSongs(luckyItem.id).first()
                                        }
                                        albumWithSongs?.let {
                                            playerConnection.playQueue(LocalAlbumRadio(it))
                                        }
                                    }
                                    is Artist -> {}
                                    is Playlist -> {}
                                }
                            } else {
                                when (val luckyItem = allYtItems.random()) {
                                    is SongItem -> playerConnection.playQueue(YouTubeQueue.radio(luckyItem.toMediaMetadata()))
                                    is AlbumItem -> playerConnection.playQueue(YouTubeAlbumRadio(luckyItem.playlistId))
                                    is ArtistItem -> luckyItem.radioEndpoint?.let {
                                        playerConnection.playQueue(YouTubeQueue(it))
                                    }
                                    is PlaylistItem -> luckyItem.playEndpoint?.let {
                                        playerConnection.playQueue(YouTubeQueue(it))
                                    }
                                }
                            }
                        }
                    },
                    onRecognitionClick = {
                        navController.navigate("recognition")
                    }
                )
            }
        }
    }
}

/**
 * Stable cross-section plumbing. Cheap plain-data re-created on recomposition; holds
 * the non-UI references and per-row composables each section needs. Volatile data
 * (media metadata, song maps, lists) travels as explicit parameters instead so the
 * sections stay free of composition-local reads.
 */
@OptIn(ExperimentalFoundationApi::class)
private data class HomeSectionDeps(
    val viewModel: HomeViewModel,
    val navController: NavController,
    val playerConnection: PlayerConnection,
    val menuState: MenuState,
    val haptic: HapticFeedback,
    val scope: CoroutineScope,
    val sideInset: Dp,
    val localGridItem: @Composable (LocalItem) -> Unit,
    val onLocalItemClick: (LocalItem) -> Unit,
)

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
private fun LazyListScope.homeSectionsContent(
    sections: List<HomeSection>,
    deps: HomeSectionDeps,
    speedDialItems: List<YTItem>,
    quickPicks: List<Song>?,
    forgottenFavorites: List<Song>?,
    dailyDiscover: List<DailyDiscoverItem>?,
    keepListening: List<LocalItem>?,
    topSongs: List<Song>?,
    accountPlaylists: List<PlaylistItem>?,
    communityPlaylists: List<CommunityPlaylistItem>?,
    similarRecommendations: List<SimilarRecommendation>?,
    homePage: HomePage?,
    explorePage: ExplorePage?,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    isRandomizing: Boolean,
    randomizeJob: MutableState<Job?>,
    quickPicksSongMap: Map<String, Song>,
    forgottenFavoritesSongMap: Map<String, Song>,
    accountName: String,
    url: String?,
    keepListeningColumns: Int,
    tabView: Boolean,
) {
    sections.forEach { section ->
        when (section) {
            HomeSection.Hero -> heroSection(deps, quickPicks)
            HomeSection.SpeedDial -> speedDialSection(
                deps = deps,
                items = speedDialItems,
                isRandomizing = isRandomizing,
                randomizeJob = randomizeJob,
            )
            HomeSection.QuickPicks -> quickPicksSection(deps, quickPicks, mediaMetadata, quickPicksSongMap)
            HomeSection.FromTheCommunity -> communityPlaylistsSection(deps, communityPlaylists)
            HomeSection.DailyDiscover -> dailyDiscoverSection(deps, dailyDiscover)
            HomeSection.KeepListening ->
                keepListeningSection(deps, keepListening, mediaMetadata, isPlaying, keepListeningColumns, tabView)
            HomeSection.TopSongs ->
                topSongsSection(deps, topSongs, mediaMetadata)
            HomeSection.AccountPlaylists -> accountPlaylistsSection(
                deps = deps,
                accountPlaylists = accountPlaylists,
                accountName = accountName,
                url = url,
            )
            HomeSection.ForgottenFavorites -> forgottenFavoritesSection(
                deps, forgottenFavorites, mediaMetadata, forgottenFavoritesSongMap,
            )
            is HomeSection.SimilarRecommendation -> similarRecommendationsSection(
                section, deps, similarRecommendations,
            )
            is HomeSection.HomePageSection -> homePageSection(section, deps, homePage)
            HomeSection.MoodAndGenres -> moodAndGenresSection(deps, explorePage)
        }
    }
}

/**
 * Home while local-only mode is on: the browse hub a local-first player shows —
 * songs, albums, artists, playlists, folders — sharing this screen's list, chrome
 * and pull-to-refresh rather than living on a route of its own.
 */
@OptIn(ExperimentalFoundationApi::class)
private fun LazyListScope.localHomeContent(
    deps: HomeSectionDeps,
    songs: List<Song>,
    albums: List<Album>,
    artists: List<Artist>,
    playlists: List<Playlist>,
    folders: List<LocalFolderIndex.Folder>,
    columns: Int,
) {
    // Local mode used to open on a chip row over one flat list: correct, but it showed a
    // single category at a time and nothing to look at. It is now the same shelf
    // architecture the online Home uses, sourced entirely from the device.
    //
    // Every shelf is derived in memory from lists the view model already holds -- no
    // extra queries. That is the point of keeping the local library as a list.
    if (songs.isEmpty()) {
        item(key = "local_empty", contentType = "empty") {
            Text(
                text = stringResource(R.string.no_local_files),
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AppleTokens.Gutter),
            )
        }
        return
    }

    item(key = "local_actions", contentType = "shuffle_row") {
        val (accentColor, onAccentColor) = rememberGlobalAccentColors()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppleTokens.Gutter)
                .animateItem(),
            horizontalArrangement = Arrangement.spacedBy(AppleTokens.ItemGap),
        ) {
            Button(
                onClick = {
                    deps.playerConnection.playQueue(
                        ListQueue(items = songs.map { it.toMediaItem() }, startIndex = 0),
                    )
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor,
                    contentColor = onAccentColor,
                ),
            ) { Text(stringResource(R.string.play)) }
            Button(
                onClick = {
                    deps.playerConnection.playQueue(
                        ListQueue(items = songs.shuffled().map { it.toMediaItem() }, startIndex = 0),
                    )
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor,
                    contentColor = onAccentColor,
                ),
            ) { Text(stringResource(R.string.shuffle)) }
        }
    }

    localSongShelf(
        deps = deps,
        key = "local_recently_added",
        titleRes = R.string.recently_added,
        songs = songs.sortedByDescending { it.song.inLibrary ?: it.song.dateModified }
            .take(LocalSpotlightCount),
    )

    // Only once something has actually been played -- a shelf of zero-play songs is
    // "recently added" again in a different order.
    val mostPlayed = songs.filter { it.song.totalPlayTime > 0 }
        .sortedByDescending { it.song.totalPlayTime }
        .take(LocalSpotlightCount)
    localSongShelf(
        deps = deps,
        key = "local_most_played",
        titleRes = R.string.most_played_songs,
        songs = mostPlayed,
    )

    if (albums.isNotEmpty()) {
        item(key = "local_albums_title", contentType = "section_title") {
            NavigationTitle(
                title = stringResource(R.string.albums),
                modifier = Modifier.animateItem(),
            )
        }
        localItemRows(deps, "local_album", albums.take(LocalShelfRows * columns), columns)
    }

    if (artists.isNotEmpty()) {
        item(key = "local_artists_title", contentType = "section_title") {
            NavigationTitle(
                title = stringResource(R.string.artists),
                modifier = Modifier.animateItem(),
            )
        }
        localItemRows(deps, "local_artist", artists.take(LocalShelfRows * columns), columns)
    }

    if (playlists.isNotEmpty()) {
        item(key = "local_playlists_title", contentType = "section_title") {
            NavigationTitle(
                title = stringResource(R.string.playlists),
                modifier = Modifier.animateItem(),
            )
        }
        val shownPlaylists = playlists.take(LocalListPreview)
        itemsIndexed(
            items = shownPlaylists,
            key = { _, playlist -> "local_playlist_" + playlist.id },
            contentType = { _, _ -> "local_playlist" },
        ) { index, playlist ->
            PlaylistListItem(
                playlist = playlist,
                shape = listItemShape(index, shownPlaylists.size),
                modifier = Modifier
                    .fillMaxWidth()
                    .bounceClick { deps.navController.navigate(localPlaylistRoute(playlist.id, playlist.thumbnailUrl)) },
            )
        }
    }

    if (folders.isNotEmpty()) {
        item(key = "local_folders_title", contentType = "section_title") {
            NavigationTitle(
                title = stringResource(R.string.folders),
                modifier = Modifier.animateItem(),
            )
        }
        // Built once for the whole section, not per row -- a per-row scan over every
        // local song to find a folder thumbnail is the same "expensive work in a
        // LazyColumn row" mistake as a per-row DB query.
        //
        // No `remember` here: this runs in the function body, not inside an item{}
        // lambda, so remember is not callable at this scope. It is still built once per
        // call rather than once per row, which is what actually mattered.
        val songById = songs.associateBy { it.id }
        val shownFolders = folders.take(LocalListPreview)

        itemsIndexed(
            items = shownFolders,
            key = { _, folder -> "local_folder_" + folder.path },
            contentType = { _, _ -> "local_folder" },
        ) { index, folder ->
            val thumbnailUrl = remember(folder, songById) {
                folder.songIds.firstNotNullOfOrNull { songById[it]?.song?.thumbnailUrl }
            }
            ListItem(
                title = folder.name,
                subtitle = pluralStringResource(
                    R.plurals.n_song,
                    folder.songIds.size,
                    folder.songIds.size,
                ),
                thumbnailContent = {
                    AsyncImage(
                        model = thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        placeholder = painterResource(R.drawable.thumbnail_fallback),
                        error = painterResource(R.drawable.thumbnail_fallback),
                        modifier = Modifier
                            .size(ListThumbnailSize)
                            .clip(RoundedCornerShape(AppleTokens.Control)),
                    )
                },
                shape = listItemShape(index, shownFolders.size),
                modifier = Modifier
                    .fillMaxWidth()
                    .bounceClick {
                        deps.navController.navigate("local_folder/" + Uri.encode(folder.path))
                    },
            )
        }
    }
}

/**
 * One horizontal shelf of songs, drawn with the card the online Home leads with.
 *
 * LazyRow, not HorizontalPager: a pager consumes the whole fling to advance exactly one
 * page, so a hard flick and a nudge travel the same distance -- which was the "no physics
 * on the horizontal shelves" report. A lazy row keeps the platform decay, so velocity
 * decides how far the shelf goes.
 */
private fun LazyListScope.localSongShelf(
    deps: HomeSectionDeps,
    key: String,
    @StringRes titleRes: Int,
    songs: List<Song>,
) {
    if (songs.isEmpty()) return

    item(key = key + "_title", contentType = "section_title") {
        NavigationTitle(
            title = stringResource(titleRes),
            modifier = Modifier.animateItem(),
            onPlayAllClick = {
                deps.playerConnection.playQueue(
                    ListQueue(items = songs.map { it.toMediaItem() }, startIndex = 0),
                )
            },
        )
    }
    item(key = key, contentType = "spotlight_row") {
        LazyRow(
            contentPadding = PaddingValues(horizontal = AppleTokens.Gutter)
                .plusStart(deps.sideInset),
            horizontalArrangement = Arrangement.spacedBy(SpotlightCardSpacing),
            modifier = Modifier
                .fillMaxWidth()
                .height(SpotlightCardHeight)
                .animateItem()
                .bleedStart(deps.sideInset),
        ) {
            items(songs, key = { it.id }, contentType = { "spotlight_card" }) { song ->
                SpotlightCard(
                    thumbnailUrl = song.thumbnailUrl,
                    title = song.title,
                    subtitle = song.artists.joinToString(", ") { it.name },
                    onClick = { deps.onLocalItemClick(song) },
                )
            }
        }
    }
}

/**
 * Grid rows of [LocalItem]s laid out as rows of the outer list, the same shape
 * (and for the same reason) as the recently-played section above.
 */
private fun LazyListScope.localItemRows(
    deps: HomeSectionDeps,
    keyPrefix: String,
    items: List<LocalItem>,
    columns: Int,
) {
    items.chunked(columns).forEachIndexed { index, rowItems ->
        item(key = "${keyPrefix}_row_$index", contentType = "local_row") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppleTokens.Gutter - AppleTokens.ItemGap / 2),
            ) {
                rowItems.forEach { localItem ->
                    Box(modifier = Modifier.weight(1f)) {
                        deps.localGridItem(localItem)
                    }
                }
                repeat(columns - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
/**
 * One entry on a Home shelf. Sections keep their own data and their own click
 * handling; only the row and the tile are shared.
 */
private data class ShelfEntry(
    val id: String,
    val thumbnailUrl: String?,
    val title: String,
    val subtitle: String,
    val onClick: () -> Unit,
    val onLongClick: (() -> Unit)? = null,
)

/**
 * Every Home section's row, drawn one way: BitChord's shelf — a header, then a
 * sideways row of cards — with the recently-played [SpotlightCard] as the tile.
 *
 * Home used to draw a different shape per section: a paging grid for speed dial, a
 * two-row list grid for quick picks, a multi-browse carousel for daily discover, a
 * numbered text list for top songs, plain grid tiles elsewhere. Five card vocabularies
 * on one screen, each with its own height, corner and text scale. This is the one.
 */
private fun LazyListScope.shelfRow(
    key: String,
    deps: HomeSectionDeps,
    entries: List<ShelfEntry>,
    artworkSize: Dp = SpotlightCardArtworkSize,
    // Recent (keepListeningSection's own inline row) and Discover keep the tall
    // reflection SpotlightCard treatment — everything else on Home shrinks to
    // BitChord's flatter, square shelf card, so one screen doesn't read as nine
    // sections all shouting at the same volume.
    compact: Boolean = false,
) {
    if (entries.isEmpty()) return
    item(key = key, contentType = if (compact) "shelf_row" else "spotlight_row") {
        LazyRow(
            contentPadding = PaddingValues(horizontal = AppleTokens.Gutter).plusStart(deps.sideInset),
            horizontalArrangement = Arrangement.spacedBy(if (compact) HomeShelfCardSpacing else SpotlightCardSpacing),
            modifier = Modifier
                .fillMaxWidth()
                .height(if (compact) HomeShelfCardHeight else artworkSize + SpotlightCardInfoHeight)
                .animateItem()
                .bleedStart(deps.sideInset),
        ) {
            items(entries, key = { it.id }, contentType = { if (compact) "shelf_card" else "spotlight_card" }) { entry ->
                if (compact) {
                    HomeShelfCard(
                        thumbnailUrl = entry.thumbnailUrl,
                        title = entry.title,
                        subtitle = entry.subtitle,
                        onClick = entry.onClick,
                        onLongClick = entry.onLongClick,
                    )
                } else {
                    SpotlightCard(
                        thumbnailUrl = entry.thumbnailUrl,
                        title = entry.title,
                        subtitle = entry.subtitle,
                        onClick = entry.onClick,
                        onLongClick = entry.onLongClick,
                        artworkSize = artworkSize,
                    )
                }
            }
        }
    }
}

/** Square artwork edge for [HomeShelfCard] — BitChord's own shelf card width. */
private val HomeShelfCardWidth = 150.dp

/** Two text lines' worth of room below the artwork. */
private val HomeShelfCardInfoHeight = 46.dp
private val HomeShelfCardHeight = HomeShelfCardWidth + HomeShelfCardInfoHeight
private val HomeShelfCardSpacing = 12.dp

/**
 * BitChord's shelf card: flat square artwork, title and subtitle directly below —
 * no reflection, no tinted card body. The plain, small-scale card every Home section
 * other than Recent/Discover uses, so those two stay the only "loud" rows on the page.
 */
@Composable
private fun HomeShelfCard(
    thumbnailUrl: String?,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val cardShape = rememberHomeCardShape()
    Column(
        modifier = Modifier
            .width(HomeShelfCardWidth)
            .combinedBounceClick(onClick = onClick, onLongClick = onLongClick),
    ) {
        AsyncImage(
            model = thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(HomeShelfCardWidth)
                .clip(cardShape)
                .homeCardRim(cardShape),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = title,
            style = AppTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = LocalContentColor.current,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = subtitle,
            style = AppTheme.typography.bodySmall,
            color = LocalContentColor.current.copy(alpha = 0.6f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Where a [YTItem] goes when tapped. Shared by every shelf that carries remote items. */
private fun HomeSectionDeps.openYtItem(item: YTItem) {
    when (item) {
        is SongItem -> playerConnection.playQueue(
            YouTubeQueue(item.endpoint ?: WatchEndpoint(videoId = item.id), item.toMediaMetadata())
        )
        is AlbumItem -> navController.navigate(albumRoute(item.id, item.thumbnail))
        is ArtistItem -> navController.navigate(artistRoute(item.id, item.thumbnail))
        // A locally-created playlist (id prefixed "LP", see PlaylistEntity.generatePlaylistId)
        // is not a real YouTube playlist — online_playlist/ cannot resolve it.
        is PlaylistItem -> navController.navigate(
            if (item.id.startsWith("LP")) localPlaylistRoute(item.id, item.thumbnail)
            else onlinePlaylistRoute(item.id, item.title, item.thumbnail)
        )
    }
}

private fun HomeSectionDeps.showYtItemMenu(item: YTItem) {
    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    menuState.show {
        when (item) {
            is SongItem -> YouTubeSongMenu(song = item, navController = navController, onDismiss = menuState::dismiss)
            is AlbumItem -> YouTubeAlbumMenu(albumItem = item, navController = navController, onDismiss = menuState::dismiss)
            is ArtistItem -> YouTubeArtistMenu(artist = item, onDismiss = menuState::dismiss)
            is PlaylistItem -> YouTubePlaylistMenu(playlist = item, coroutineScope = scope, onDismiss = menuState::dismiss)
        }
    }
}

/** Second line under a remote tile. Empty rather than invented where there is nothing to say. */
private fun YTItem.shelfSubtitle(): String = when (this) {
    is SongItem -> artists.joinToString { it.name }
    is AlbumItem -> artists?.joinToString { it.name }.orEmpty()
    is PlaylistItem -> author?.name ?: songCountText.orEmpty()
    is ArtistItem -> ""
}

private fun YTItem.toShelfEntry(deps: HomeSectionDeps): ShelfEntry {
    val item = this
    return ShelfEntry(
        id = item.id,
        thumbnailUrl = item.thumbnail,
        title = item.title,
        subtitle = item.shelfSubtitle(),
        onClick = { deps.openYtItem(item) },
        onLongClick = { deps.showYtItemMenu(item) },
    )
}

/** A library song on a shelf: play it (or toggle, if it is the one already playing). */
private fun Song.toShelfEntry(deps: HomeSectionDeps, mediaMetadata: MediaMetadata?): ShelfEntry {
    val entry = this
    return ShelfEntry(
        id = entry.id,
        thumbnailUrl = entry.song.thumbnailUrl,
        title = entry.song.title,
        subtitle = entry.artists.joinToString { it.name },
        onClick = {
            if (entry.id == mediaMetadata?.id) {
                deps.playerConnection.togglePlayPause()
            } else {
                deps.playerConnection.playQueue(YouTubeQueue.radio(entry.toMediaMetadata()))
            }
        },
        onLongClick = {
            deps.haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            deps.menuState.show {
                SongMenu(originalSong = entry, navController = deps.navController, onDismiss = deps.menuState::dismiss)
            }
        },
    )
}

private fun LazyListScope.heroSection(
    deps: HomeSectionDeps,
    quickPicks: List<Song>?,
) {
    quickPicks?.firstOrNull()?.let { top ->
        item(key = "hero_card", contentType = "hero_card") {
            val heroSubtitle = remember(top.id) {
                top.artists.joinToString { it.name }
            }
            HomeHeroCard(
                title = top.song.title,
                subtitle = heroSubtitle,
                thumbnailUrl = top.song.thumbnailUrl,
                onPlay = {
                    deps.playerConnection.playQueue(
                        ListQueue(
                            title = top.song.title,
                            items = quickPicks.orEmpty().map { it.toMediaItem() },
                        )
                    )
                },
                onShuffle = {
                    deps.playerConnection.playQueue(
                        ListQueue(
                            title = top.song.title,
                            items = quickPicks.orEmpty().shuffled().map { it.toMediaItem() },
                        )
                    )
                },
                onClick = {
                    deps.playerConnection.playQueue(
                        ListQueue(
                            title = top.song.title,
                            items = listOf(top.toMediaItem()),
                        )
                    )
                },
                modifier = Modifier
                    .padding(horizontal = AppleTokens.Gutter)
                    .animateItem(),
            )
        }
    }
}

private fun LazyListScope.speedDialSection(
    deps: HomeSectionDeps,
    items: List<YTItem>,
    isRandomizing: Boolean,
    randomizeJob: MutableState<Job?>,
) {
    items.takeIf { it.isNotEmpty() }?.let { items ->
        item(key = "speed_dial_title", contentType = "section_title") {
            NavigationTitle(
                title = stringResource(R.string.speed_dial),
                modifier = Modifier.animateItem(),
            )
        }
        // The randomize tile rides at the tail of the shelf rather than in a reserved
        // slot of a paging grid: with one row there is no grid to reserve a cell in,
        // and the tile is a "if nothing here appeals" action, which belongs at the end.
        val entries = items.map { it.toShelfEntry(deps) } + ShelfEntry(
            id = "speed_dial_randomize",
            thumbnailUrl = null,
            title = "Surprise me",
            subtitle = if (isRandomizing) "Picking..." else "Play something at random",
            onClick = {
                if (isRandomizing) {
                    randomizeJob.value?.cancel()
                } else {
                    randomizeJob.value = deps.scope.launch {
                        deps.viewModel.getRandomItem()?.let { deps.openYtItem(it) }
                    }
                }
            },
        )
        shelfRow(key = "speed_dial_list", deps = deps, entries = entries, compact = true)
    }
}

private fun LazyListScope.quickPicksSection(
    deps: HomeSectionDeps,
    quickPicks: List<Song>?,
    mediaMetadata: MediaMetadata?,
    songMap: Map<String, Song>,
) {
    quickPicks?.takeIf { it.isNotEmpty() }?.let { quickPicks ->
        item(key = "quick_picks_title", contentType = "section_title") {
            val quickPicksTitle = stringResource(R.string.quick_picks)
            NavigationTitle(
                title = quickPicksTitle,
                modifier = Modifier.animateItem(),
                onPlayAllClick = {
                    deps.playerConnection.playQueue(
                        ListQueue(
                            title = quickPicksTitle,
                            items = quickPicks.distinctBy { it.id }.map { it.toMediaItem() }
                        )
                    )
                },
            )
        }
        shelfRow(
            key = "quick_picks_list",
            deps = deps,
            entries = quickPicks.distinctBy { it.id }
                .map { (songMap[it.id] ?: it).toShelfEntry(deps, mediaMetadata) },
            compact = true,
        )
    }
}

private fun LazyListScope.communityPlaylistsSection(
    deps: HomeSectionDeps,
    communityPlaylists: List<CommunityPlaylistItem>?,
) {
    communityPlaylists?.takeIf { it.isNotEmpty() }?.let { playlists ->
        item(key = "community_playlists_title", contentType = "section_title") {
            NavigationTitle(
                title = stringResource(R.string.from_the_community),
                modifier = Modifier.animateItem(),
            )
        }
        shelfRow(
            key = "community_playlists_content",
            deps = deps,
            entries = playlists.map { item ->
                ShelfEntry(
                    id = item.playlist.id,
                    thumbnailUrl = item.playlist.thumbnail,
                    title = item.playlist.title,
                    subtitle = item.playlist.author?.name ?: item.playlist.songCountText.orEmpty(),
                    onClick = {
                        deps.navController.navigate(
                            onlinePlaylistRoute(
                                item.playlist.id.removePrefix("VL"),
                                item.playlist.title,
                                item.playlist.thumbnail,
                            )
                        )
                    },
                )
            },
            compact = true,
        )
    }
}

private fun LazyListScope.dailyDiscoverSection(
    deps: HomeSectionDeps,
    dailyDiscover: List<DailyDiscoverItem>?,
) {
    dailyDiscover?.takeIf { it.isNotEmpty() }?.let { discoverList ->
        item(key = "daily_discover_title", contentType = "section_title") {
            val title = stringResource(R.string.your_daily_discover)
            NavigationTitle(
                title = title,
                onPlayAllClick = {
                    val queueItems = discoverList.mapNotNull {
                        (it.recommendation as? SongItem)?.toMediaMetadata()
                    }

                    if (queueItems.isNotEmpty()) {
                        deps.playerConnection.playQueue(
                            ListQueue(
                                title = title,
                                items = queueItems.map { it.toMediaItem() }
                            )
                        )
                    }
                },
            )
        }
        shelfRow(
            key = "daily_discover_content",
            deps = deps,
            // Distinct: the same recommendation can be reached from two different
            // seeds, and a repeated key is a hard crash in a lazy row.
            entries = discoverList.map { it.recommendation }
                .distinctBy { it.id }
                .map { it.toShelfEntry(deps) },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun LazyListScope.keepListeningSection(
    deps: HomeSectionDeps,
    keepListening: List<LocalItem>?,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    keepListeningColumns: Int,
    tabView: Boolean = false,
) {
    keepListening?.takeIf { it.isNotEmpty() }?.let { keepListening ->
        item(key = "keep_listening_title", contentType = "section_title") {
            NavigationTitle(
                title = stringResource(R.string.recently_played),
                modifier = Modifier.animateItem(),
            )
        }

        // Wide layout: the most recent item becomes a hero tile with the next four
        // stacked beside it in a 2x2, which is the shape of the reference design and
        // the only way this section fills a tablet's width instead of running a
        // two-tile row across it.
        val heroCount = if (tabView && keepListening.size >= 3) 5 else 0
        if (heroCount > 0) {
            val hero = keepListening.first()
            val companions = keepListening.drop(1).take(heroCount - 1)
            item(key = "keep_listening_hero", contentType = "keep_listening_hero") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppleTokens.Gutter)
                        .animateItem(),
                    horizontalArrangement = Arrangement.spacedBy(AppleTokens.ItemGap),
                ) {
                    Box(Modifier.weight(1f)) { deps.localGridItem(hero) }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(AppleTokens.ItemGap),
                    ) {
                        companions.chunked(2).forEach { rowItems ->
                            Row(horizontalArrangement = Arrangement.spacedBy(AppleTokens.ItemGap)) {
                                rowItems.forEach { item ->
                                    Box(Modifier.weight(1f)) { deps.localGridItem(item) }
                                }
                                repeat(2 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
        }

        // One horizontal row of tall spotlight cards, snapping a card at a time.
        //
        // This section used to be rows of the outer LazyColumn, on the reasoning that a
        // horizontal row hides most of the list off-screen while vertical rows keep
        // everything reachable by scrolling the page you are already scrolling. That is
        // still true, and it is the cost of this layout: the tail of recently-played is
        // now behind a sideways swipe. Taken deliberately -- the card carries artwork,
        // reflection and title at a size a 2-up grid tile cannot, and recently-played is
        // a "pick up where I left off" shelf, where the first few entries are the point.
        val spotlightItems = keepListening.drop(heroCount)
        if (spotlightItems.isNotEmpty()) {
            item(key = "keep_listening_spotlight", contentType = "spotlight_row") {
                // See the local shelf above: a pager spends the whole fling on one page,
                // so this row felt inert next to the vertical list it sits in.
                LazyRow(
                    contentPadding = PaddingValues(horizontal = AppleTokens.Gutter)
                        .plusStart(deps.sideInset),
                    horizontalArrangement = Arrangement.spacedBy(SpotlightCardSpacing),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(SpotlightCardHeight)
                        .animateItem()
                        .bleedStart(deps.sideInset),
                ) {
                    items(
                        spotlightItems,
                        key = { it.id },
                        contentType = { "spotlight_card" },
                    ) { localItem ->
                        SpotlightCard(
                            thumbnailUrl = localItem.thumbnailUrl,
                            title = localItem.title,
                            subtitle = localItem.spotlightSubtitle(),
                            onClick = { deps.onLocalItemClick(localItem) },
                            // The card is the artwork here, so it is the morph source.
                            modifier = Modifier.sharedArtworkSource(localItem.id, localItem.thumbnailUrl),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Numbered top-songs list without thumbnails — BitChord-style compact ranking.
 * Just number, title, artist — no album art, no extra weight.
 */
private fun LazyListScope.topSongsSection(
    deps: HomeSectionDeps,
    topSongs: List<Song>?,
    mediaMetadata: MediaMetadata?,
) {
    topSongs?.takeIf { it.isNotEmpty() }?.let { songs ->
        item(key = "top_songs_title", contentType = "section_title") {
            NavigationTitle(
                title = stringResource(R.string.most_played_songs),
                modifier = Modifier.animateItem(),
            )
        }
        shelfRow(
            key = "top_songs_list",
            deps = deps,
            entries = songs.take(10).map { it.toShelfEntry(deps, mediaMetadata) },
            compact = true,
        )
    }
}

/**
 * Secondary line for a spotlight card. [LocalItem] only guarantees a title, so the
 * artist has to come off the concrete type.
 */
private fun LocalItem.spotlightSubtitle(): String = when (this) {
    is Song -> artists.joinToString(", ") { it.name }
    is Album -> artists.joinToString(", ") { it.name }
    // Neither carries a second line worth showing: an artist's name is already the
    // title, and Playlist.title is playlist.name.
    is Artist, is Playlist -> ""
}

private fun LazyListScope.accountPlaylistsSection(
    deps: HomeSectionDeps,
    accountPlaylists: List<PlaylistItem>?,
    accountName: String,
    url: String?,
) {
    accountPlaylists?.takeIf { it.isNotEmpty() }?.let { accountPlaylists ->
        item(key = "account_playlists_title", contentType = "section_title") {
            NavigationTitle(
                label = stringResource(R.string.your_youtube_playlists),
                title = accountName,
                thumbnail = {
                    if (url != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(url)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .diskCacheKey(url)
                                .crossfade(false)
                                .build(),
                            placeholder = painterResource(id = R.drawable.person),
                            error = painterResource(id = R.drawable.person),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(ListThumbnailSize)
                                .clip(CircleShape)
                        )
                    } else {
                        Icon(
                            imageVector = SfSymbols.Monochrome.SFPersonCropCircle,
                            contentDescription = null,
                            modifier = Modifier.size(ListThumbnailSize)
                        )
                    }
                },
                onClick = {
                    deps.navController.navigate("account")
                },
                modifier = Modifier.animateItem(),
            )
        }
        shelfRow(
            key = "account_playlists_list",
            deps = deps,
            entries = accountPlaylists.distinctBy { it.id }.map { it.toShelfEntry(deps) },
            compact = true,
        )
    }
}

private fun LazyListScope.forgottenFavoritesSection(
    deps: HomeSectionDeps,
    forgottenFavorites: List<Song>?,
    mediaMetadata: MediaMetadata?,
    songMap: Map<String, Song>,
) {
    forgottenFavorites?.takeIf { it.isNotEmpty() }?.let { forgottenFavorites ->
        item(key = "forgotten_favorites_title", contentType = "section_title") {
            val forgottenFavoritesTitle = stringResource(R.string.forgotten_favorites)
            NavigationTitle(
                title = forgottenFavoritesTitle,
                modifier = Modifier.animateItem(),
                onPlayAllClick = {
                    deps.playerConnection.playQueue(
                        ListQueue(
                            title = forgottenFavoritesTitle,
                            items = forgottenFavorites.distinctBy { it.id }.map { it.toMediaItem() }
                        )
                    )
                },
            )
        }
        shelfRow(
            key = "forgotten_favorites_list",
            deps = deps,
            entries = forgottenFavorites.distinctBy { it.id }
                .map { (songMap[it.id] ?: it).toShelfEntry(deps, mediaMetadata) },
            compact = true,
        )
    }
}

private fun LazyListScope.similarRecommendationsSection(
    section: HomeSection.SimilarRecommendation,
    deps: HomeSectionDeps,
    similarRecommendations: List<SimilarRecommendation>?,
) {
    val recommendation = similarRecommendations?.getOrNull(section.index)
    recommendation?.let {
        item(key = "similar_to_title_${section.index}", contentType = "section_title") {
            NavigationTitle(
                label = stringResource(R.string.similar_to),
                title = recommendation.title.title,
                thumbnail = recommendation.title.thumbnailUrl?.let { thumbnailUrl ->
                    {
                        val shape =
                            if (recommendation.title is Artist) CircleShape else RoundedCornerShape(
                                ThumbnailCornerRadius
                            )
                        AsyncImage(
                            model = thumbnailUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(ListThumbnailSize)
                                .clip(shape)
                        )
                    }
                },
                onClick = {
                    when (recommendation.title) {
                        is Song -> deps.navController.navigate(albumRoute(recommendation.title.album!!.id, recommendation.title.thumbnailUrl))
                        is Album -> deps.navController.navigate(albumRoute(recommendation.title.id, recommendation.title.thumbnailUrl))
                        is Artist -> deps.navController.navigate(artistRoute(recommendation.title.id, recommendation.title.thumbnailUrl))
                        is Playlist -> {}
                    }
                },
                modifier = Modifier.animateItem(),
            )
        }
        shelfRow(
            key = "similar_to_list_${section.index}",
            deps = deps,
            entries = recommendation.items.distinctBy { it.id }.map { it.toShelfEntry(deps) },
            compact = true,
        )
    }
}

private fun LazyListScope.homePageSection(
    section: HomeSection.HomePageSection,
    deps: HomeSectionDeps,
    homePage: HomePage?,
) {
    val sectionData = homePage?.sections?.getOrNull(section.index)
    sectionData?.let {
        val sectionSongs = sectionData.items.filterIsInstance<SongItem>()

        item(key = "home_section_title_${section.index}", contentType = "section_title") {
            NavigationTitle(
                title = sectionData.title,
                label = sectionData.label,
                thumbnail = sectionData.thumbnail?.let { thumbnailUrl ->
                    {
                        val shape =
                            if (sectionData.endpoint?.isArtistEndpoint == true) CircleShape else RoundedCornerShape(
                                ThumbnailCornerRadius
                            )
                        AsyncImage(
                            model = thumbnailUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(ListThumbnailSize)
                                .clip(shape)
                        )
                    }
                },
                onClick = sectionData.endpoint?.let { endpoint ->
                    {
                        when {
                            endpoint.browseId == "FEmusic_moods_and_genres" ->
                                deps.navController.navigate("mood_and_genres")
                            endpoint.params != null ->
                                deps.navController.navigate("youtube_browse/${endpoint.browseId}?params=${endpoint.params}")
                            else ->
                                deps.navController.navigate("browse/${endpoint.browseId}")
                        }
                    }
                },
                onPlayAllClick = if (sectionSongs.isNotEmpty()) {
                    {
                        deps.playerConnection.playQueue(
                            ListQueue(
                                title = sectionData.title,
                                items = sectionSongs.map { it.toMediaMetadata().toMediaItem() }
                            )
                        )
                    }
                } else null,
                modifier = Modifier.animateItem(),
            )
        }
        shelfRow(
            key = "home_section_list_${section.index}",
            deps = deps,
            entries = sectionData.items.distinctBy { it.id }.map { it.toShelfEntry(deps) },
            compact = true,
        )
    }
}

private fun LazyListScope.moodAndGenresSection(
    deps: HomeSectionDeps,
    explorePage: ExplorePage?,
) {
    // The filter chips themselves (Energize, Relax, Feel good...) are pinned right under
    // Home's title now — see homeMoodFilterChips — not folded into this reorderable
    // section any more. This is just the "Mood and Genres" tile shelf below them, which
    // stays a normal section: arrangeable/hideable in Home Feed Order like any other.

    // Deduplicated by title: YouTube can return the same
    // genre twice (seen with "Desi Hip-Hop"), and the grid
    // below keys on title — a repeat crashed Home outright
    // with IllegalArgumentException from the lazy list.
    // Rendering it twice would be wrong regardless, so this
    // fixes the display and the crash in one place.
    explorePage?.moodAndGenres?.distinctBy { it.title }?.let { moodAndGenres ->
        item(key = "mood_and_genres_title", contentType = "section_title") {
            NavigationTitle(
                title = stringResource(R.string.mood_and_genres),
                onClick = {
                    deps.navController.navigate("mood_and_genres")
                },
                modifier = Modifier.animateItem(),
            )
        }
        item(key = "mood_and_genres_list", contentType = "mood_row") {
            // One sideways row on the shelf's rhythm, like every other section. A mood
            // has no artwork, so the tile stays a gradient card rather than a
            // SpotlightCard with nothing in it — same width and corner, no reflection.
            LazyRow(
                contentPadding = PaddingValues(horizontal = AppleTokens.Gutter)
                    .plusStart(deps.sideInset),
                horizontalArrangement = Arrangement.spacedBy(SpotlightCardSpacing),
                modifier = Modifier
                    .fillMaxWidth()
                    .animateItem()
                    .bleedStart(deps.sideInset),
            ) {
                items(items = moodAndGenres, key = { it.title }) {
                    MoodCard(
                        title = it.title,
                        width = SpotlightCardArtworkSize,
                        height = 96.dp,
                        onClick = {
                            deps.navController.navigate("youtube_browse/${it.endpoint.browseId}?params=${it.endpoint.params}")
                        },
                    )
                }
            }
        }
    }
}

/**
 * A mood/genre tile: a two-stop gradient card with the name across the bottom.
 *
 * The gradient comes from the title's own hash rather than from a palette lookup, so
 * a genre keeps the same colour between sessions and devices without shipping (or
 * maintaining) a colour table for a list the server can change at any time.
 */
@Composable
private fun MoodCard(
    title: String,
    width: Dp,
    height: Dp,
    onClick: () -> Unit,
) {
    val colors = remember(title) {
        val hue = ((title.hashCode() % 360) + 360) % 360
        listOf(
            Color.hsl(hue.toFloat(), 0.55f, 0.42f),
            Color.hsl(((hue + 40) % 360).toFloat(), 0.60f, 0.26f),
        )
    }
    Box(
        contentAlignment = Alignment.BottomStart,
        modifier = Modifier
            .size(width = width, height = height)
            .clip(RoundedCornerShape(AppleTokens.Artwork))
            .background(Brush.linearGradient(colors))
            .bounceClick(onClick = onClick)
            .padding(12.dp),
    ) {
        Text(
            text = title,
            fontSize = AppleTokens.ItemTitle,
            lineHeight = AppleTokens.ItemTitleLineHeight,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Fetches one batched songsByIds flow for a whole list's ids and indexes it into a
 * Map<String, Song>. Re-emits whenever any of those rows change, so rows stay as live
 * as the old per-row database.song() subscriptions — with a single observer per list.
 */
@Composable
private fun rememberSongsById(
    database: MusicDatabase,
    songIds: List<String>,
): Map<String, Song> {
    val flow = remember(songIds) {
        if (songIds.isEmpty()) flowOf(emptyList()) else database.songsByIds(songIds)
    }
    val songs by flow.collectAsStateWithLifecycle(initialValue = emptyList())
    return remember(songs) { songs.associateBy { it.id } }
}

/** Cards on local mode's spotlight shelf. A shelf, not the library. */
private const val LocalSpotlightCount = 15
