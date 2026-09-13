/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.ui.screens.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.convx.music.LocalDatabase
import com.convx.music.R
import com.convx.music.ui.ios.compat.AppTheme
import com.convx.music.ui.ios.compat.Button
import com.convx.music.ui.ios.compat.CircularProgressIndicator
import com.convx.music.ui.ios.compat.Icon
import com.convx.music.ui.ios.compat.IconButton
import com.convx.music.ui.ios.compat.OutlinedTextField
import com.convx.music.ui.ios.compat.Text
import com.convx.music.ui.ios.compat.TextButton
import com.convx.music.ui.screens.settings.SettingsPage
import com.convx.music.ui.utils.localPlaylistRoute
import com.convx.music.viewmodels.AiDjSetViewModel

private const val MIN_SONG_COUNT = 4
private const val MAX_SONG_COUNT = 30
private const val DEFAULT_SONG_COUNT = 12

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiDjSetScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    seedSongId: String?,
    viewModel: AiDjSetViewModel = hiltViewModel(),
) {
    val database = LocalDatabase.current
    val state by viewModel.state.collectAsStateWithLifecycle()

    var prompt by rememberSaveable { mutableStateOf("") }
    var songCount by rememberSaveable { mutableIntStateOf(DEFAULT_SONG_COUNT) }

    val seedSong = if (!seedSongId.isNullOrBlank()) {
        database.song(seedSongId).collectAsStateWithLifecycle(initialValue = null).value
    } else {
        null
    }

    SettingsPage(
        navController = navController,
        title = stringResource(R.string.ai_dj_mix),
        pageColor = AppTheme.colorScheme.background,
        horizontalPadding = 16.dp,
    ) {
        when {
            state.result != null -> {
                val result = state.result!!
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = result.playlistName,
                        style = AppTheme.typography.headlineSmall,
                        color = AppTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.ai_dj_mix_song_count, result.orderedTitles.size),
                        style = AppTheme.typography.bodyMedium,
                        color = AppTheme.colorScheme.onSurfaceVariant,
                    )

                    if (result.unmatchedTitles.isNotEmpty()) {
                        Text(
                            text = stringResource(
                                R.string.ai_dj_mix_unmatched_count,
                                result.unmatchedTitles.size,
                                result.unmatchedTitles.joinToString(", "),
                            ),
                            style = AppTheme.typography.bodySmall,
                            color = AppTheme.colorScheme.error,
                        )
                    }

                    HorizontalDivider()

                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(result.orderedTitles) { title ->
                            Text(
                                text = title,
                                style = AppTheme.typography.bodyMedium,
                                color = AppTheme.colorScheme.onSurface,
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { viewModel.reset() }) {
                            Text(stringResource(R.string.ai_dj_mix_start_over))
                        }
                        Button(
                            onClick = {
                                navController.navigate(localPlaylistRoute(result.playlistId, null))
                            },
                        ) {
                            Text(stringResource(R.string.ai_dj_mix_open_playlist))
                        }
                    }
                }
            }

            state.isRunning -> {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Spacer(modifier = Modifier.height(24.dp))
                    CircularProgressIndicator(color = AppTheme.colorScheme.primary)
                    val latestLog = state.logs.lastOrNull() ?: stringResource(R.string.ai_dj_mix_starting)
                    Text(
                        text = latestLog,
                        style = AppTheme.typography.bodyMedium,
                        color = AppTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    if (state.logs.size > 1) {
                        val scrollState = rememberScrollState()
                        Text(
                            text = state.logs.dropLast(1).asReversed().joinToString("\n"),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 160.dp)
                                .verticalScroll(scrollState),
                            style = AppTheme.typography.bodySmall,
                            color = AppTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            else -> {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (state.failed) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = state.logs.lastOrNull() ?: stringResource(R.string.ai_dj_mix_failed),
                                style = AppTheme.typography.bodyMedium,
                                color = AppTheme.colorScheme.error,
                            )
                            // The last line is usually a generic outcome ("No songs
                            // found") — the actual reason (missing key, network
                            // error, parse failure) is often an earlier line that
                            // would otherwise be silently lost.
                            if (state.logs.size > 1) {
                                Text(
                                    text = state.logs.dropLast(1).joinToString("\n"),
                                    style = AppTheme.typography.bodySmall,
                                    color = AppTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    if (seedSong != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(painterResource(R.drawable.sparks), contentDescription = null)
                            Text(
                                text = stringResource(R.string.ai_dj_mix_based_on, seedSong.song.title),
                                style = AppTheme.typography.bodyMedium,
                                color = AppTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    OutlinedTextField(
                        value = prompt,
                        onValueChange = { prompt = it },
                        label = { Text(stringResource(R.string.ai_dj_mix_prompt_label)) },
                        placeholder = {
                            Text(
                                if (seedSong != null) {
                                    stringResource(R.string.ai_dj_mix_prompt_hint_seeded)
                                } else {
                                    stringResource(R.string.ai_dj_mix_prompt_hint)
                                }
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        minLines = 2,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.ai_dj_mix_song_count_label),
                            style = AppTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = { songCount = (songCount - 1).coerceAtLeast(MIN_SONG_COUNT) },
                        ) {
                            Icon(painterResource(R.drawable.remove), contentDescription = null)
                        }
                        Text(text = "$songCount", style = AppTheme.typography.bodyLarge)
                        IconButton(
                            onClick = { songCount = (songCount + 1).coerceAtMost(MAX_SONG_COUNT) },
                        ) {
                            Icon(painterResource(R.drawable.add), contentDescription = null)
                        }
                    }

                    val canGenerate = prompt.isNotBlank() || seedSong != null
                    if (!canGenerate) {
                        Text(
                            text = stringResource(R.string.ai_dj_mix_need_prompt_or_seed),
                            style = AppTheme.typography.bodySmall,
                            color = AppTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Button(
                        onClick = {
                            viewModel.generate(
                                prompt = prompt.takeIf { it.isNotBlank() },
                                seedSongId = seedSongId,
                                songCount = songCount,
                            )
                        },
                        enabled = canGenerate,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.ai_dj_mix_generate))
                    }
                }
            }
        }
    }
}
