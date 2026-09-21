/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.ui.screens

import com.convx.music.ui.utils.sharedArtworkSource
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import com.convx.music.ui.utils.rememberEdgeAwareFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import com.convx.music.ui.ios.compat.Icon
import com.convx.music.ui.ios.compat.IconButton
import com.convx.music.ui.ios.compat.LocalContentColor
import com.convx.music.ui.ios.compat.AppTheme
import com.convx.music.ui.ios.compat.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.music.innertube.YouTube
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import com.music.innertube.models.YTItem
import com.music.innertube.pages.BrowseResult
import com.convx.music.LocalPlayerAwareWindowInsets
import com.convx.music.LocalPlayerConnection
import com.convx.music.R
import com.composables.sfsymbols.SfSymbols
import com.convx.music.constants.ListItemHeight
import com.convx.music.models.toMediaMetadata
import com.convx.music.playback.PlayerConnection
import com.convx.music.playback.queues.YouTubeQueue
import com.convx.music.ui.component.LocalMenuState
import com.convx.music.ui.component.MenuState
import com.convx.music.ui.component.NavigationTitle
import com.convx.music.ui.component.YouTubeGridItem
import com.convx.music.ui.component.YouTubeListItem
import com.convx.music.ui.component.rememberAppBackgroundColor
import com.convx.music.ui.component.SpotlightCard
import com.convx.music.ui.component.SpotlightCardArtworkSize
import com.convx.music.ui.component.SpotlightCardHeight
import com.convx.music.ui.component.SpotlightCardSpacing
import com.convx.music.ui.theme.AppleTokens
import com.convx.music.ui.theme.LocalAccentTextColor
import com.convx.music.ui.component.shimmer.GridItemPlaceHolder
import com.convx.music.ui.component.shimmer.ShimmerHost
import com.convx.music.ui.component.shimmer.TextPlaceholder
import com.convx.music.ui.menu.YouTubeAlbumMenu
import com.convx.music.ui.menu.YouTubeArtistMenu
import com.convx.music.ui.menu.YouTubePlaylistMenu
import com.convx.music.ui.menu.YouTubeSongMenu
import com.convx.music.ui.utils.bounceClick
import com.convx.music.ui.utils.combinedBounceClick
import com.convx.music.ui.utils.albumRoute
import com.convx.music.ui.utils.artistRoute
import com.convx.music.ui.utils.onlinePlaylistRoute
import com.convx.music.ui.utils.SnapLayoutInfoProvider
import com.convx.music.utils.listItemShape
import com.convx.music.utils.reportException
import com.convx.music.viewmodels.ChartsViewModel
import com.convx.music.viewmodels.ExploreViewModel

/**
 * The discovery tab — the Browse counterpart to Home: charts, new releases,
 * top videos and moods/genres on one flat scroll. Same conventions as Home:
 * pull-to-refresh, a large scrolling title, grouped sections whose headers act
 * as their own "See All", and chart continuations appended as you reach the end.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ExploreScreen(
    navController: NavController,
    exploreViewModel: ExploreViewModel = hiltViewModel(),
    chartsViewModel: ChartsViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val explorePage by exploreViewModel.explorePage.collectAsState()
    val recommendedForYou by exploreViewModel.recommendedForYou.collectAsState()
    val chartsPage by chartsViewModel.chartsPage.collectAsState()
    val isChartsLoading by chartsViewModel.isLoading.collectAsState()

    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()
    val pullRefreshState = rememberPullToRefreshState()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val scrollToTop by backStackEntry?.savedStateHandle
        ?.getStateFlow("scrollToTop", false)?.collectAsState() ?: return

    LaunchedEffect(Unit) {
        if (chartsPage == null) {
            chartsViewModel.loadCharts()
        }
    }

    LaunchedEffect(scrollToTop) {
        if (scrollToTop) {
            lazyListState.animateScrollToItem(0)
            backStackEntry?.savedStateHandle?.set("scrollToTop", false)
        }
    }

    // Append the next page of chart sections once the user gets near the end,
    // so the tab keeps growing instead of dead-ending after the first batch.
    val shouldLoadMoreCharts by remember {
        derivedStateOf {
            val info = lazyListState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && lastVisible >= info.totalItemsCount - 6
        }
    }
    LaunchedEffect(shouldLoadMoreCharts, chartsPage?.continuation) {
        if (shouldLoadMoreCharts &&
            chartsPage?.continuation != null &&
            !isChartsLoading
        ) {
            chartsViewModel.loadMore()
        }
    }

    val contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()

    PullToRefreshBox(
        state = pullRefreshState,
        isRefreshing = isChartsLoading,
        onRefresh = {
            chartsViewModel.loadCharts()
            exploreViewModel.refresh()
        },
        indicator = {
            if (isChartsLoading || pullRefreshState.distanceFraction > 0f) {
                PullToRefreshDefaults.LoadingIndicator(
                    state = pullRefreshState,
                    isRefreshing = isChartsLoading,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(contentPadding),
                )
            }
        },
    ) {
        // Flat fill behind everything, same convention as Home: a no-op unless a
        // custom theme colour is picked, in which case it becomes the background.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(rememberAppBackgroundColor(AppTheme.colorScheme.background)),
        ) {
            LazyColumn(
                state = lazyListState,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = contentPadding,
            ) {
                item(key = "explore_title", contentType = "section_title") {
                    // Same title treatment as Home's "Listen Now": large, bold,
                    // scrolling with the content instead of pinned in a bar.
                    Text(
                        text = stringResource(R.string.tab_explore),
                        fontSize = AppleTokens.TitleLarge,
                        lineHeight = AppleTokens.TitleLargeLineHeight,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.02).em,
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
                            ),
                    )
                }

                // Spotlight cards, the recently-played card language from Home:
                // tall artwork cards with a reflection pour, driven from data
                // already on screen.
                // Real chart branding: the top 3-4 songs' own artwork tiled into one
                // collage, not one arbitrary song's cover standing in for the whole chart.
                val trendingCollage =
                    chartsPage?.sections?.firstOrNull()?.items
                        ?.filterIsInstance<SongItem>()
                        ?.mapNotNull { it.thumbnail }
                        ?.take(4)
                        .orEmpty()
                val newReleaseArt =
                    explorePage?.newReleaseAlbums?.firstOrNull()?.thumbnail

                item(key = "discover_header", contentType = "section_title") {
                    NavigationTitle(title = stringResource(R.string.discover))
                }
                item(key = "discover_spotlight", contentType = "spotlight_row") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = AppleTokens.Gutter),
                        horizontalArrangement = Arrangement.spacedBy(SpotlightCardSpacing),
                    ) {
                        item(key = "spotlight_top_global") {
                            SpotlightCard(
                                thumbnailUrl = trendingCollage.firstOrNull(),
                                collageUrls = trendingCollage,
                                title = stringResource(R.string.top_global),
                                subtitle = stringResource(R.string.charts),
                                onClick = { navController.navigate("charts_screen") },
                            )
                        }
                        newReleaseArt?.let { art ->
                            item(key = "spotlight_new_release") {
                                SpotlightCard(
                                    thumbnailUrl = art,
                                    title = stringResource(R.string.new_release_albums),
                                    subtitle = stringResource(R.string.tab_explore),
                                    onClick = { navController.navigate("new_release") },
                                )
                            }
                        }
                        item(key = "spotlight_concert_mode") {
                            SpotlightCard(
                                thumbnailUrl = null,
                                title = stringResource(R.string.ai_dj_mix_concert_mode_label),
                                subtitle = stringResource(R.string.ai_dj_mix_concert_mode_explore_subtitle),
                                onClick = { navController.navigate("ai_dj_set?concert=true") },
                            )
                        }
                    }
                }

                // Both sources failed and nothing is loading — say so instead of
                // spinning shimmer forever. Either source alone is enough to render.
                val loadFailed =
                    !isChartsLoading && chartsPage == null && explorePage == null
                if (loadFailed) {
                    item(key = "explore_error") {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.explore_outlined),
                                contentDescription = null,
                                tint = AppTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                modifier = Modifier.size(40.dp),
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.error_title),
                                style = AppTheme.typography.bodyMedium,
                                color = AppTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.retry_button),
                                style = AppTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = LocalAccentTextColor.current,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .bounceClick {
                                        chartsViewModel.loadCharts()
                                        exploreViewModel.refresh()
                                    }
                                    .padding(horizontal = 20.dp, vertical = 10.dp),
                            )
                        }
                    }
                } else if (chartsPage == null || explorePage == null) {
                    // isChartsLoading deliberately excluded from this branch: it's also
                    // true during loadMore() pagination and pull-to-refresh, and both of
                    // those already have real content on screen. Gating the whole shelf
                    // list on it discarded every rendered section (and re-fetched every
                    // image) on each page-in — the "images reload / a section renders
                    // then disappears" report. Once there's real data, only the footer
                    // spinner below reacts to further loading.
                    item(key = "explore_shimmer") {
                        ShimmerHost {
                            TextPlaceholder(
                                height = 36.dp,
                                modifier = Modifier
                                    .padding(12.dp)
                                    .fillMaxWidth(0.5f),
                            )
                            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                                val horizontalLazyGridItemWidthFactor = if (maxWidth * 0.475f >= 320.dp) 0.475f else 0.9f
                                val horizontalLazyGridItemWidth = maxWidth * horizontalLazyGridItemWidthFactor

                                LazyHorizontalGrid(
                                    rows = GridCells.Fixed(4),
                                    contentPadding = PaddingValues(start = 4.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(ListItemHeight * 4),
                                ) {
                                    items(4) {
                                        Row(
                                            modifier = Modifier
                                                .width(horizontalLazyGridItemWidth)
                                                .padding(8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(ListItemHeight - 16.dp)
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(AppTheme.colorScheme.onSurface),
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column(
                                                modifier = Modifier.fillMaxHeight(),
                                                verticalArrangement = Arrangement.Center,
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .height(16.dp)
                                                        .width(120.dp)
                                                        .background(AppTheme.colorScheme.onSurface),
                                                )
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .height(12.dp)
                                                        .width(80.dp)
                                                        .background(AppTheme.colorScheme.onSurface),
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            TextPlaceholder(
                                height = 36.dp,
                                modifier = Modifier
                                    .padding(vertical = 12.dp, horizontal = 12.dp)
                                    .width(250.dp),
                            )
                            Row {
                                repeat(2) {
                                    GridItemPlaceHolder()
                                }
                            }

                            TextPlaceholder(
                                height = 36.dp,
                                modifier = Modifier
                                    .padding(vertical = 12.dp, horizontal = 12.dp)
                                    .width(250.dp),
                            )
                            repeat(4) {
                                Row {
                                    repeat(2) {
                                        TextPlaceholder(
                                            height = MoodAndGenresButtonHeight,
                                            modifier = Modifier
                                                .padding(horizontal = 6.dp)
                                                .width(200.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    chartsPage?.sections?.forEach { section ->
                        val songs = section.items.filterIsInstance<SongItem>().distinctBy { it.id }
                        val nonSongs = section.items.filter { it !is SongItem }.distinctBy { it.id }

                        if (songs.isNotEmpty()) {
                            item(key = "chart_songs_header_${section.title}", contentType = "section_title") {
                                NavigationTitle(
                                    title = when (section.title) {
                                        "Trending" -> stringResource(R.string.trending)
                                        else -> section.title.ifEmpty { stringResource(R.string.charts) }
                                    },
                                    label = stringResource(R.string.charts),
                                    onClick = {
                                        navController.navigate("charts_screen")
                                    },
                                )
                            }
                            item(key = "chart_songs_grid_${section.title}", contentType = "chart_grid") {
                                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                                    val horizontalLazyGridItemWidthFactor = if (maxWidth * 0.475f >= 320.dp) 0.475f else 0.9f
                                    val horizontalLazyGridItemWidth = maxWidth * horizontalLazyGridItemWidthFactor

                                    val lazyGridState = rememberLazyGridState()
                                    val snapLayoutInfoProvider = remember(lazyGridState) {
                                        SnapLayoutInfoProvider(
                                            lazyGridState = lazyGridState,
                                            positionInLayout = { layoutSize, itemSize ->
                                                (layoutSize * horizontalLazyGridItemWidthFactor / 2f - itemSize / 2f)
                                            },
                                        )
                                    }

                                    LazyHorizontalGrid(
                                        state = lazyGridState,
                                        rows = GridCells.Fixed(4),
                                        flingBehavior = rememberEdgeAwareFlingBehavior(
                                            lazyGridState,
                                            rememberSnapFlingBehavior(snapLayoutInfoProvider),
                                        ),
                                        contentPadding = PaddingValues(horizontal = AppleTokens.Gutter),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(ListItemHeight * 4),
                                    ) {
                                        itemsIndexed(
                                            items = songs,
                                            key = { _, it -> "${section.title}_${it.id}" },
                                        ) { index, song ->
                                            YouTubeListItem(
                                                item = song,
                                                isActive = song.id == mediaMetadata?.id,
                                                isPlaying = isPlaying,
                                                isSwipeable = false,
                                                flat = true,
                                                shape = listItemShape(index % 4, 4),
                                                trailingContent = {
                                                    IconButton(
                                                        onClick = {
                                                            menuState.show {
                                                                YouTubeSongMenu(
                                                                    song = song,
                                                                    navController = navController,
                                                                    onDismiss = menuState::dismiss,
                                                                )
                                                            }
                                                        },
                                                    ) {
                                                        Icon(
                                                            painter = painterResource(R.drawable.more_horiz),
                                                            contentDescription = null,
                                                        )
                                                    }
                                                },
                                                modifier = Modifier
                                                    .width(horizontalLazyGridItemWidth)
                                                    .combinedBounceClick(
                                                        onClick = {
                                                            if (song.id == mediaMetadata?.id) {
                                                                playerConnection.togglePlayPause()
                                                            } else {
                                                                playerConnection.playQueue(
                                                                    YouTubeQueue(
                                                                        endpoint = WatchEndpoint(videoId = song.id),
                                                                        preloadItem = song.toMediaMetadata(),
                                                                    ),
                                                                )
                                                            }
                                                        },
                                                        onLongClick = {
                                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            menuState.show {
                                                                YouTubeSongMenu(
                                                                    song = song,
                                                                    navController = navController,
                                                                    onDismiss = menuState::dismiss,
                                                                )
                                                            }
                                                        },
                                                    ),
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Chart shelves that are NOT songs — albums, artists,
                        // playlists the charts endpoint mixes in (e.g. its own
                        // new-release grid). Rendered as a cover carousel so
                        // they stop being dropped on the floor.
                        if (nonSongs.isNotEmpty()) {
                            item(key = "chart_items_header_${section.title}", contentType = "section_title") {
                                NavigationTitle(title = section.title)
                            }
                            item(key = "chart_items_row_${section.title}", contentType = "grid_row") {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = AppleTokens.Gutter),
                                ) {
                                    items(
                                        items = nonSongs,
                                        key = { "${section.title}_${it.id}" },
                                    ) { item ->
                                        ChartShelfItem(
                                            item = item,
                                            isActive = mediaMetadata?.album?.id == item.id,
                                            isPlaying = isPlaying,
                                            navController = navController,
                                            menuState = menuState,
                                            mediaMetadataId = mediaMetadata?.id,
                                            playerConnection = playerConnection,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // One section per mood/genre, each loading that genre's page
                    // and presenting its albums in the recently-played spotlight
                    // card language. This is what turns the tab from two or three
                    // shelves into a real Browse-style set of lists.
                    explorePage?.moodAndGenres.orEmpty().take(4).forEach { mood ->
                        item(key = "mood_section_${mood.title}", contentType = "mood_section") {
                            MoodAlbumSection(
                                title = mood.title,
                                browseId = mood.endpoint.browseId,
                                params = mood.endpoint.params,
                                onSeeAll = {
                                    navController.navigate("youtube_browse/${mood.endpoint.browseId}?params=${mood.endpoint.params}")
                                },
                                onAlbumClick = { albumId ->
                                    navController.navigate("album/$albumId")
                                },
                            )
                        }
                    }

                    explorePage?.newReleaseAlbums?.let { newReleaseAlbums ->
                        item(key = "new_release_header", contentType = "section_title") {
                            NavigationTitle(
                                title = stringResource(R.string.new_release_albums),
                                onClick = {
                                    navController.navigate("new_release")
                                },
                            )
                        }
                        item(key = "new_release_row", contentType = "grid_row") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = AppleTokens.Gutter),
                            ) {
                                items(
                                    items = newReleaseAlbums.distinctBy { it.id },
                                    key = { it.id },
                                ) { album ->
                                    YouTubeGridItem(
                                        item = album,
                                        isActive = mediaMetadata?.album?.id == album.id,
                                        isPlaying = isPlaying,
                                        coroutineScope = coroutineScope,
                                        modifier = Modifier
                                            .combinedBounceClick(
                                                onClick = {
                                                    navController.navigate(albumRoute(album.id, album.thumbnail))
                                                },
                                                onLongClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    menuState.show {
                                                        YouTubeAlbumMenu(
                                                            albumItem = album,
                                                            navController = navController,
                                                            onDismiss = menuState::dismiss,
                                                        )
                                                    }
                                                },
                                            )
                                            .animateItem(),
                                    )
                                }
                            }
                        }
                    }

                    chartsPage?.sections?.find { it.title == "Top music videos" }?.let { topVideosSection ->
                        item(key = "top_videos_header", contentType = "section_title") {
                            NavigationTitle(
                                title = stringResource(R.string.top_music_videos),
                                onClick = {
                                    navController.navigate("charts_screen")
                                },
                            )
                        }
                        item(key = "top_videos_row", contentType = "grid_row") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = AppleTokens.Gutter),
                            ) {
                                items(
                                    items = topVideosSection.items.filterIsInstance<SongItem>().distinctBy { it.id },
                                    key = { it.id },
                                ) { video ->
                                    YouTubeGridItem(
                                        item = video,
                                        isActive = video.id == mediaMetadata?.id,
                                        isPlaying = isPlaying,
                                        coroutineScope = coroutineScope,
                                        modifier = Modifier
                                            .combinedBounceClick(
                                                onClick = {
                                                    if (video.id == mediaMetadata?.id) {
                                                        playerConnection.togglePlayPause()
                                                    } else {
                                                        playerConnection.playQueue(
                                                            YouTubeQueue(
                                                                endpoint = WatchEndpoint(videoId = video.id),
                                                                preloadItem = video.toMediaMetadata(),
                                                            ),
                                                        )
                                                    }
                                                },
                                                onLongClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    menuState.show {
                                                        YouTubeSongMenu(
                                                            song = video,
                                                            navController = navController,
                                                            onDismiss = menuState::dismiss,
                                                        )
                                                    }
                                                },
                                            )
                                            .animateItem(),
                                    )
                                }
                            }
                        }
                    }

                    // A shelf of items related to the user's own recent listening —
                    // same seed-and-related approach Home's similar-recommendations
                    // shelf uses, so Explore isn't purely YouTube's own charts/releases.
                    recommendedForYou?.takeIf { it.isNotEmpty() }?.let { recommended ->
                        item(key = "recommended_header", contentType = "section_title") {
                            NavigationTitle(title = stringResource(R.string.recommended_for_you))
                        }
                        item(key = "recommended_row", contentType = "grid_row") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = AppleTokens.Gutter),
                            ) {
                                items(items = recommended, key = { it.id }) { recItem ->
                                    YouTubeGridItem(
                                        item = recItem,
                                        isActive = recItem.id == mediaMetadata?.id,
                                        isPlaying = isPlaying,
                                        coroutineScope = coroutineScope,
                                        modifier = Modifier
                                            .combinedBounceClick(
                                                onClick = {
                                                    when (recItem) {
                                                        is SongItem -> if (recItem.id == mediaMetadata?.id) {
                                                            playerConnection.togglePlayPause()
                                                        } else {
                                                            playerConnection.playQueue(
                                                                YouTubeQueue(
                                                                    endpoint = recItem.endpoint ?: WatchEndpoint(videoId = recItem.id),
                                                                    preloadItem = recItem.toMediaMetadata(),
                                                                ),
                                                            )
                                                        }
                                                        is AlbumItem -> navController.navigate(albumRoute(recItem.id, recItem.thumbnail))
                                                        is ArtistItem -> navController.navigate(artistRoute(recItem.id, recItem.thumbnail))
                                                        is PlaylistItem -> navController.navigate(
                                                            onlinePlaylistRoute(recItem.id, recItem.title, recItem.thumbnail)
                                                        )
                                                    }
                                                },
                                                onLongClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    menuState.show {
                                                        when (recItem) {
                                                            is SongItem -> YouTubeSongMenu(song = recItem, navController = navController, onDismiss = menuState::dismiss)
                                                            is AlbumItem -> YouTubeAlbumMenu(albumItem = recItem, navController = navController, onDismiss = menuState::dismiss)
                                                            is ArtistItem -> YouTubeArtistMenu(artist = recItem, onDismiss = menuState::dismiss)
                                                            is PlaylistItem -> YouTubePlaylistMenu(playlist = recItem, coroutineScope = coroutineScope, onDismiss = menuState::dismiss)
                                                        }
                                                    }
                                                },
                                            )
                                            .animateItem(),
                                    )
                                }
                            }
                        }
                    }

                    explorePage?.moodAndGenres?.let { moodAndGenres ->
                        item(key = "mood_genres_header", contentType = "section_title") {
                            NavigationTitle(
                                title = stringResource(R.string.mood_and_genres),
                                onClick = {
                                    navController.navigate("mood_and_genres")
                                },
                            )
                        }
                        item(key = "mood_genres_grid", contentType = "mood_grid") {
                            LazyHorizontalGrid(
                                rows = GridCells.Fixed(4),
                                contentPadding = PaddingValues(6.dp),
                                modifier = Modifier.height((MoodAndGenresButtonHeight + 12.dp) * 4 + 12.dp),
                            ) {
                                items(moodAndGenres) {
                                    MoodAndGenresButton(
                                        title = it.title,
                                        onClick = {
                                            navController.navigate("youtube_browse/${it.endpoint.browseId}?params=${it.endpoint.params}")
                                        },
                                        modifier = Modifier
                                            .padding(6.dp)
                                            .width(180.dp),
                                    )
                                }
                            }
                        }
                    }

                    // Quiet tail feedback while the next chart page appends.
                    if (chartsPage?.continuation != null && isChartsLoading) {
                        item(key = "charts_loading_more", contentType = "loading_more") {
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                            ) {
                                GridItemPlaceHolder()
                                Spacer(Modifier.width(8.dp))
                                GridItemPlaceHolder()
                            }
                        }
                    }
                }

                item(key = "explore_bottom_spacer") {
                    Spacer(Modifier.height(50.dp))
                }
            }
        }
    }
}

/**
 * A non-song item from a chart shelf — album, artist or playlist. Dispatches to
 * the right destination and long-press menu without duplicating each screen's
 * navigation logic inline in the list builder.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ChartShelfItem(
    item: YTItem,
    isActive: Boolean,
    isPlaying: Boolean,
    navController: NavController,
    menuState: MenuState,
    mediaMetadataId: String?,
    playerConnection: PlayerConnection,
) {
    val haptic = LocalHapticFeedback.current
    YouTubeGridItem(
        item = item,
        isActive = isActive,
        isPlaying = isPlaying,
        modifier = Modifier
            .combinedBounceClick(
                onClick = {
                    when (item) {
                        is AlbumItem -> navController.navigate(albumRoute(item.id, item.thumbnail))
                        is ArtistItem -> navController.navigate(artistRoute(item.id, item.thumbnail))
                        is PlaylistItem -> navController.navigate(
                            onlinePlaylistRoute(item.id, item.title, item.thumbnail)
                        )
                        is SongItem -> {
                            if (item.id == mediaMetadataId) {
                                playerConnection.togglePlayPause()
                            } else {
                                playerConnection.playQueue(
                                    YouTubeQueue(
                                        endpoint = WatchEndpoint(videoId = item.id),
                                        preloadItem = item.toMediaMetadata(),
                                    ),
                                )
                            }
                        }
                    }
                },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    menuState.show {
                        when (item) {
                            is AlbumItem -> YouTubeAlbumMenu(
                                albumItem = item,
                                navController = navController,
                                onDismiss = menuState::dismiss,
                            )
                            is SongItem -> YouTubeSongMenu(
                                song = item,
                                navController = navController,
                                onDismiss = menuState::dismiss,
                            )
                            else -> Unit
                        }
                    }
                },
            ),
    )
}

/**
 * One mood/genre as a full section: the genre's page is loaded once, and its
 * albums are presented in the recently-played spotlight card language — tall
 * artwork cards with the reflection pour, artist names as subtitles.
 */
@Composable
private fun MoodAlbumSection(
    title: String,
    browseId: String,
    params: String?,
    onSeeAll: () -> Unit,
    onAlbumClick: (String) -> Unit,
) {
    var result by remember(browseId) { mutableStateOf<BrowseResult?>(null) }

    LaunchedEffect(browseId, params) {
        YouTube.browse(browseId = browseId, params = params)
            .onSuccess { result = it }
            .onFailure { reportException(it) }
    }

    val albums = result?.items
        ?.asSequence()
        ?.flatMap { it.items.asSequence() }
        ?.filterIsInstance<AlbumItem>()
        ?.distinctBy { it.id }
        ?.take(8)
        ?.toList()
        .orEmpty()

    if (result != null && albums.isEmpty()) {
        // The genre page has no album shelves — render nothing rather than an
        // orphaned header.
        return
    }

    Column {
        NavigationTitle(title = title, onClick = onSeeAll)
        LazyRow(
            contentPadding = PaddingValues(horizontal = AppleTokens.Gutter),
            horizontalArrangement = Arrangement.spacedBy(SpotlightCardSpacing),
            modifier = Modifier.height(SpotlightCardHeight),
        ) {
            if (result == null) {
                items(2) {
                    Box(
                        modifier = Modifier
                            .width(SpotlightCardArtworkSize)
                            .height(SpotlightCardHeight)
                            .clip(RoundedCornerShape(16.dp))
                            .background(AppTheme.colorScheme.surfaceContainer),
                    )
                }
            } else {
                items(
                    items = albums,
                    key = { "${browseId}_${it.id}" },
                ) { album ->
                    SpotlightCard(
                        thumbnailUrl = album.thumbnail,
                        title = album.title,
                        subtitle = album.artists.orEmpty().joinToString(", ") { it.name },
                        onClick = { onAlbumClick(album.id) },
                        modifier = Modifier.sharedArtworkSource(album.id, album.thumbnail),
                    )
                }
            }
        }
    }
}
