/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.convx.music.R
import com.convx.music.constants.AutoDjMixingEnabledKey
import com.convx.music.constants.CreativeTransitionsEnabledKey
import com.convx.music.constants.CrossfadeDurationKey
import com.convx.music.constants.CrossfadeEnabledKey
import com.convx.music.ui.component.IosSettingsGroup
import com.convx.music.ui.component.IosSettingsItem
import com.convx.music.ui.ios.compat.AppTheme
import com.convx.music.ui.ios.compat.Icon
import com.convx.music.ui.ios.compat.Slider
import com.convx.music.ui.ios.compat.Switch
import com.convx.music.ui.ios.compat.Text
import com.convx.music.utils.rememberPreference

/** Apple's slider runs 1s to 12s; older installs may have stored up to 15s, so clamp on read. */
private const val MinFadeSeconds = 1f
private const val MaxFadeSeconds = 12f

/**
 * Song Transitions, laid out like the iOS Music setting it mirrors: one master switch, then a
 * choice between AutoMix and Crossfade, then the fade length (Crossfade only).
 *
 * It edits the same three preferences the queue's crossfade pill and the old Player settings
 * rows did, which MusicService turns into the controller's behaviour: master = crossfade on,
 * AutoMix = Auto-DJ mixing on, Crossfade = Auto-DJ mixing off.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongTransitionsScreen(navController: NavController) {
    val (enabled, onEnabledChange) = rememberPreference(CrossfadeEnabledKey, defaultValue = false)
    val (autoMix, onAutoMixChange) = rememberPreference(AutoDjMixingEnabledKey, defaultValue = false)
    val (duration, onDurationChange) = rememberPreference(CrossfadeDurationKey, defaultValue = 5f)
    val (creative, onCreativeChange) = rememberPreference(CreativeTransitionsEnabledKey, defaultValue = false)

    SettingsLazyPage(
        navController = navController,
        title = stringResource(R.string.song_transitions),
        horizontalPadding = 16.dp,
    ) {
        item(key = "song_transitions_content") {
            Column {
                IosSettingsGroup(
                    items = listOf(
                        IosSettingsItem(
                            icon = painterResource(R.drawable.ic_queue_crossfade),
                            title = { Text(stringResource(R.string.song_transitions)) },
                            trailingContent = { Switch(checked = enabled, onCheckedChange = onEnabledChange) },
                            onClick = { onEnabledChange(!enabled) },
                        ),
                    ),
                )
                Footnote(stringResource(R.string.song_transitions_desc))

                Spacer(Modifier.height(8.dp))
                IosSettingsGroup(
                    title = stringResource(R.string.transition_style),
                    items = listOf(
                        IosSettingsItem(
                            icon = painterResource(R.drawable.equalizer),
                            title = { Text(stringResource(R.string.transition_automix)) },
                            description = { Text(stringResource(R.string.transition_automix_desc)) },
                            enabled = enabled,
                            // An empty trailing slot, not null: null makes a clickable row draw a chevron,
                            // which reads as "opens a sub-screen" rather than "choose this".
                            trailingContent = { if (enabled && autoMix) CheckMark() else Spacer(Modifier.size(24.dp)) },
                            onClick = if (enabled) {
                                { onAutoMixChange(true) }
                            } else null,
                        ),
                        IosSettingsItem(
                            icon = painterResource(R.drawable.ic_queue_crossfade),
                            title = { Text(stringResource(R.string.crossfade)) },
                            description = { Text(stringResource(R.string.transition_crossfade_desc)) },
                            enabled = enabled,
                            // An empty trailing slot, not null: null makes a clickable row draw a chevron,
                            // which reads as "opens a sub-screen" rather than "choose this".
                            trailingContent = { if (enabled && !autoMix) CheckMark() else Spacer(Modifier.size(24.dp)) },
                            onClick = if (enabled) {
                                { onAutoMixChange(false) }
                            } else null,
                        ),
                    ),
                )

                if (enabled && autoMix) {
                    Spacer(Modifier.height(16.dp))
                    IosSettingsGroup(
                        items = listOf(
                            IosSettingsItem(
                                icon = painterResource(R.drawable.equalizer),
                                title = { Text(stringResource(R.string.creative_transitions)) },
                                description = { Text(stringResource(R.string.creative_transitions_desc)) },
                                trailingContent = { Switch(checked = creative, onCheckedChange = onCreativeChange) },
                                onClick = { onCreativeChange(!creative) },
                            ),
                        ),
                    )
                }

                if (enabled && !autoMix) {
                    Spacer(Modifier.height(16.dp))
                    val seconds = duration.coerceIn(MinFadeSeconds, MaxFadeSeconds)
                    IosSettingsGroup(
                        title = stringResource(R.string.crossfade_duration),
                        items = listOf(
                            IosSettingsItem(
                                title = {
                                    Text(pluralStringResource(R.plurals.seconds, seconds.toInt(), seconds.toInt()))
                                },
                                description = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text("${MinFadeSeconds.toInt()}s", color = AppTheme.colorScheme.onSurfaceVariant)
                                        Slider(
                                            value = seconds,
                                            onValueChange = onDurationChange,
                                            valueRange = MinFadeSeconds..MaxFadeSeconds,
                                            steps = (MaxFadeSeconds - MinFadeSeconds).toInt() - 1,
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 8.dp),
                                        )
                                        Text("${MaxFadeSeconds.toInt()}s", color = AppTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                            ),
                        ),
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun CheckMark() {
    Icon(
        painter = painterResource(R.drawable.check),
        contentDescription = null,
        tint = AppTheme.colorScheme.primary,
    )
}

@Composable
private fun Footnote(text: String) {
    Text(
        text = text,
        color = AppTheme.colorScheme.onSurfaceVariant,
        style = AppTheme.typography.bodySmall,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
