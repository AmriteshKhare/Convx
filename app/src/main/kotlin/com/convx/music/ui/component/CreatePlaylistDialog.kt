/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.ui.component

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import com.convx.music.ui.ios.compat.Icon
import com.convx.music.ui.ios.compat.AppTheme
import com.convx.music.ui.ios.compat.Switch
import com.convx.music.ui.ios.compat.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.composables.sfsymbols.SfSymbols
import com.composables.sfsymbols.monochrome.SFPlus
import com.music.innertube.YouTube
import com.convx.music.LocalDatabase
import com.convx.music.R
import com.convx.music.constants.InnerTubeCookieKey
import com.convx.music.db.entities.PlaylistEntity
import com.convx.music.extensions.isSyncEnabled
import com.convx.music.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.util.logging.Logger

@Composable
fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    initialTextFieldValue: String? = null,
    allowSyncing: Boolean = true,
    onPlaylistCreated: ((String) -> Unit)? = null,
) {
    val database = LocalDatabase.current
    val coroutineScope = rememberCoroutineScope()
    var syncedPlaylist by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val innerTubeCookie by rememberPreference(InnerTubeCookieKey, "")
    val isSignedIn = innerTubeCookie.isNotEmpty()

    TextFieldDialog(
        icon = { Icon(imageVector = SfSymbols.Monochrome.SFPlus, contentDescription = null) },
        title = { Text(text = stringResource(R.string.create_playlist)) },
        initialTextFieldValue = TextFieldValue(initialTextFieldValue ?: ""),
        onDismiss = onDismiss,
        // Default autoDismiss tears the composable down before onDone() runs, cancelling
        // this coroutine mid-flight and silently dropping the toast/callback.
        autoDismiss = false,
        onDone = { playlistName ->
            coroutineScope.launch(Dispatchers.IO) {
                // The one innertube call not wrapped in runCatching — a failure throws out
                // of this coroutine and skips the local insert below.
                val wantsSync = syncedPlaylist
                val browseId = if (wantsSync && isSignedIn) {
                    runCatching { YouTube.createPlaylist(playlistName) }
                        .onFailure { Logger.getLogger("CreatePlaylistDialog").warning("Playlist sync failed: $it") }
                        .getOrNull()
                } else {
                    if (wantsSync) Logger.getLogger("CreatePlaylistDialog").warning("Not signed in")
                    null
                }

                val playlistEntity = PlaylistEntity(
                    name = playlistName,
                    browseId = browseId,
                    bookmarkedAt = LocalDateTime.now(),
                    isEditable = true,
                )

                // database.query{} is async; call insert() directly so the row exists
                // before onPlaylistCreated runs.
                database.insert(playlistEntity)

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        context.getString(
                            if (wantsSync && browseId == null) R.string.playlist_created_sync_failed
                            else R.string.playlist_created
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                    onPlaylistCreated?.invoke(playlistEntity.id)
                    onDismiss()
                }
            }
        },
        extraContent = {
            if (allowSyncing) {
                Row(
                    modifier = Modifier.padding(vertical = 16.dp, horizontal = 40.dp)
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.sync_playlist),
                            style = AppTheme.typography.titleLarge,
                        )
                        Text(
                            text = stringResource(R.string.allows_for_sync_witch_youtube),
                            style = AppTheme.typography.bodySmall,
                            modifier = Modifier.fillMaxWidth(0.7f)
                        )
                    }
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Switch(
                            checked = syncedPlaylist,
                            onCheckedChange = {
                                coroutineScope.launch {
                                    val isYtmSyncEnabled = context.isSyncEnabled()
                                    if (!isSignedIn && !syncedPlaylist) {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.not_logged_in_youtube),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else if (!isYtmSyncEnabled) {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.sync_disabled),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    syncedPlaylist = !syncedPlaylist
                                }
                                }
                            }
                        )
                    }
                }
            }
        }
    )
}
