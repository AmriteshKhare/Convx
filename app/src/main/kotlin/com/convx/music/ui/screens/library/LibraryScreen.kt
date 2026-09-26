/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.ui.screens.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.convx.music.LocalPlayerAwareWindowInsets
import com.convx.music.R
import com.convx.music.constants.ChipSortTypeKey
import com.convx.music.constants.LibraryFilter
import com.convx.music.ui.component.ChipsRow
import com.convx.music.ui.component.HomeImageBackground
import com.convx.music.ui.component.IosMenuGroup
import com.convx.music.ui.component.IosMenuItemData
import com.convx.music.ui.ios.compat.Icon
import com.convx.music.ui.ios.compat.Text
import com.convx.music.ui.theme.AppleTokens
import com.convx.music.utils.rememberEnumPreference

/**
 * Figma-identical Library main: 8 category rows in the menu-row style,
 * each opening its existing screen. Light and dark follow the menu tokens.
 */
@Composable
private fun FigmaLibraryMain(
    navController: NavController,
    onSelectFilter: (LibraryFilter) -> Unit,
) {
    fun row(
        label: String,
        iconRes: Int,
        onClick: () -> Unit,
    ) = IosMenuItemData(
        title = { Text(text = label) },
        icon = {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
            )
        },
        onClick = onClick,
    )

    LazyColumn(
        contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
    ) {
        item(key = "library_categories", contentType = "menu_group") {
            IosMenuGroup(
                items = listOf(
                    row(stringResource(R.string.filter_playlists), R.drawable.queue_music) {
                        onSelectFilter(LibraryFilter.PLAYLISTS)
                    },
                    row(stringResource(R.string.filter_artists), R.drawable.artist) {
                        onSelectFilter(LibraryFilter.ARTISTS)
                    },
                    row(stringResource(R.string.filter_albums), R.drawable.album) {
                        onSelectFilter(LibraryFilter.ALBUMS)
                    },
                    row(stringResource(R.string.filter_songs), R.drawable.music_note) {
                        onSelectFilter(LibraryFilter.SONGS)
                    },
                    row(stringResource(R.string.filter_downloaded), R.drawable.download) {
                        navController.navigate("auto_playlist/downloaded")
                    },
                    row(stringResource(R.string.history), R.drawable.history) {
                        navController.navigate("history")
                    },
                    row(stringResource(R.string.filter_liked), R.drawable.favorite_border) {
                        navController.navigate("auto_playlist/liked")
                    },
                    row(stringResource(R.string.filter_local), R.drawable.library_music) {
                        navController.navigate("auto_playlist/local")
                    },
                ),
                modifier = Modifier.padding(
                    horizontal = AppleTokens.Gutter,
                    vertical = 12.dp,
                ),
            )
        }
    }
}

@Composable
fun LibraryScreen(navController: NavController) {
    var filterType by rememberEnumPreference(ChipSortTypeKey, LibraryFilter.LIBRARY)

    val filterContent = @Composable {
        Row {
            ChipsRow(
                chips =
                listOf(
                    LibraryFilter.PLAYLISTS to stringResource(R.string.filter_playlists),
                    LibraryFilter.SONGS to stringResource(R.string.filter_songs),
                    LibraryFilter.ALBUMS to stringResource(R.string.filter_albums),
                ),
                currentValue = filterType,
                onValueUpdate = {
                    filterType =
                        if (filterType == it) {
                            LibraryFilter.LIBRARY
                        } else {
                            it
                        }
                },
                modifier = Modifier.weight(1f),
            )
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        // Hoisted here (not per-tab) so switching Songs/Albums/Artists/Playlists
        // doesn't lose the custom background — previously only LibraryMixScreen drew it.
        HomeImageBackground(withGradient = true)

        when (filterType) {
            // Figma-identical Library main (section 489:16464): the 8 category
            // rows. Sub-screens below keep every playlist, song, album and
            // artist — the rows are navigation, nothing is removed.
            LibraryFilter.LIBRARY -> FigmaLibraryMain(
                navController = navController,
                onSelectFilter = { filterType = it },
            )
            LibraryFilter.PLAYLISTS -> LibraryPlaylistsScreen(navController, filterContent)
            LibraryFilter.SONGS -> LibrarySongsScreen(
                navController,
                { filterType = LibraryFilter.LIBRARY })

            LibraryFilter.ALBUMS -> LibraryAlbumsScreen(
                navController,
                { filterType = LibraryFilter.LIBRARY })

            LibraryFilter.ARTISTS -> LibraryArtistsScreen(
                navController,
                { filterType = LibraryFilter.LIBRARY })
        }
    }
}
