/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.ui.screens.artist

import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.activity.compose.BackHandler
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.foundation.ExperimentalFoundationApi
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.foundation.layout.Box
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.foundation.layout.Row
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.foundation.layout.Spacer
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.foundation.layout.padding
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.foundation.lazy.grid.GridCells
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.foundation.lazy.grid.GridItemSpan
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.foundation.lazy.grid.items
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.material3.ExperimentalMaterial3Api
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.ios.compat.Icon
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.ios.compat.AppTheme
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.ios.compat.SnackbarHost
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.material3.SnackbarHostState
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.ios.compat.Text
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.ios.compat.TopAppBar
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.material3.TopAppBarScrollBehavior
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.runtime.Composable
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.runtime.collectAsState
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.runtime.getValue
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.runtime.mutableStateListOf
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.runtime.mutableStateOf
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.runtime.remember
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.runtime.rememberCoroutineScope
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.runtime.saveable.listSaver
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.runtime.saveable.rememberSaveable
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.runtime.setValue
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.runtime.toMutableStateList
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.ui.Alignment
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.ui.Modifier
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.ui.res.painterResource
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.ui.res.pluralStringResource
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.ui.unit.dp
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.navigation.NavController
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.LocalPlayerAwareWindowInsets
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.LocalPlayerConnection
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.R
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.utils.rememberGridColumns
import com.convx.music.constants.CONTENT_TYPE_ALBUM
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.constants.CONTENT_TYPE_HEADER
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.constants.GridItemSize
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.constants.GridItemsSizeKey
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.constants.GridThumbnailHeight
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.component.ListScrollRail
import com.convx.music.ui.component.IconButton
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.component.LibraryAlbumGridItem
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.component.LocalMenuState
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.utils.backToMain
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.utils.rememberEnumPreference
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.viewmodels.ArtistAlbumsViewModel
import com.convx.music.ui.component.ScreenLargeTitle
import com.convx.music.ui.component.ScreenScaffold
import com.convx.music.ui.component.rememberTitleCollapseProgress

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ArtistAlbumsScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: ArtistAlbumsViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val artist by viewModel.artist.collectAsState()
    val albums by viewModel.albums.collectAsState()

    val coroutineScope = rememberCoroutineScope()
    val lazyGridState = rememberLazyGridState()
    val gridItemSize by rememberEnumPreference(GridItemsSizeKey, GridItemSize.BIG)

    var inSelectMode by rememberSaveable { mutableStateOf(false) }
    val selection = rememberSaveable(
        saver = listSaver<MutableList<String>, String>(
            save = { it.toList() },
            restore = { it.toMutableStateList() }
        )
    ) { mutableStateListOf() }
    val onExitSelectionMode = {
        inSelectMode = false
        selection.clear()
    }
    if (inSelectMode) {
        BackHandler(onBack = onExitSelectionMode)
    }

    val snackbarHostState = remember { SnackbarHostState() }

    val visibleAlbums = remember(albums) { albums.distinctBy { it.id } }

    ScreenScaffold(
        title = artist?.artist?.name.orEmpty(),
        // A grid has no title item to measure against, so this rides the grid's
        // own scroll offset — see the LazyGridState overload.
        titleAlpha = rememberTitleCollapseProgress(lazyGridState),
        onBack = navController::navigateUp,
        onBackLongClick = navController::backToMain,
    ) { chrome ->
        LazyVerticalGrid(
            state = lazyGridState,
            columns = rememberGridColumns(),
            modifier = chrome.contentModifier(),
            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()
        ) {
            item(
                key = "title",
                span = { GridItemSpan(maxLineSpan) },
            ) {
                ScreenLargeTitle(artist?.artist?.name.orEmpty())
            }

            item(
                key = "header",
                span = { GridItemSpan(maxLineSpan) },
                contentType = CONTENT_TYPE_HEADER
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Spacer(Modifier.weight(1f))

                    Text(
                        text = pluralStringResource(R.plurals.n_album, albums.size, albums.size),
                        style = AppTheme.typography.titleSmall,
                        color = AppTheme.colorScheme.secondary
                    )
                }
            }

            items(
                items = visibleAlbums,
                key = { it.id },
                contentType = { CONTENT_TYPE_ALBUM }
            ) { album ->
                LibraryAlbumGridItem(
                    navController = navController,
                    menuState = menuState,
                    coroutineScope = coroutineScope,
                    album = album,
                    isActive = album.id == mediaMetadata?.album?.id,
                    isPlaying = isPlaying,
                    modifier = Modifier.animateItem()
                )
            }
        }

        // No sort control on this screen -- albums arrive in release order -- so the rail
        // is a proportional thumb rather than letters.
        ListScrollRail(
            lazyGridState = lazyGridState,
            itemCount = visibleAlbums.size,
            sectionIndexMap = null,
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
                .align(Alignment.BottomCenter)
        )
    }
}
