/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.ui.screens.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import com.convx.music.ui.component.FrostedTopBar
import com.convx.music.ui.component.TopFadeBlur
import com.convx.music.ui.ios.foundation.IosTheme
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.foundation.layout.Column
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.foundation.layout.Spacer
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.foundation.layout.height
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.foundation.layout.only
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.foundation.layout.padding
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.foundation.layout.size
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.foundation.rememberScrollState
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.foundation.verticalScroll
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.material3.ExperimentalMaterial3Api
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.ios.compat.Icon
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.ios.compat.Slider
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.ios.compat.Switch
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.material3.SwitchDefaults
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.ios.compat.Text
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.ios.compat.TextButton
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.ios.compat.TopAppBar
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.material3.TopAppBarScrollBehavior
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.runtime.getValue
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.runtime.mutableStateOf
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.runtime.remember
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.runtime.setValue
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.ui.Modifier
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.ui.res.painterResource
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.ui.res.pluralStringResource
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.ui.res.stringResource
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.ui.unit.dp
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.navigation.NavController
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.BuildConfig
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.LocalPlayerAwareWindowInsets
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.R
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.constants.AudioNormalizationKey
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.constants.AudioQuality
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.constants.AudioQualityKey
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.constants.AutoDownloadOnLikeKey
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.constants.CrossfadeDurationKey
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.constants.AutoDjMixingEnabledKey
import com.convx.music.constants.CreativeTransitionsEnabledKey
import com.convx.music.constants.LocalOnlyModeKey
import com.convx.music.constants.CrossfadeEnabledKey
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.constants.AutoSkipNextOnErrorKey
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.constants.EnableGoogleCastKey
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.constants.KeepScreenOn
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.constants.PauseOnMute
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.constants.AskBeforeReplacingQueueKey
import com.convx.music.constants.PersistentQueueKey
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.constants.RememberShuffleAndRepeatKey
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.constants.ResumeOnBluetoothConnectKey
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.constants.SimilarContent
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.constants.SkipSilenceKey
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.component.DefaultDialog
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.component.EnumDialog
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.component.IconButton
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.component.IosSettingsGroup
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.component.IosSettingsItem
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.utils.backToMain
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.utils.rememberEnumPreference
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.utils.rememberPreference
import com.convx.music.ui.utils.appTopBarWindowInsets
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val (audioQuality, onAudioQualityChange) = rememberEnumPreference(
        AudioQualityKey,
        defaultValue = AudioQuality.AUTO
    )
    val (crossfadeEnabled, onCrossfadeEnabledChange) = rememberPreference(
        CrossfadeEnabledKey,
        defaultValue = false
    )
    val (crossfadeDuration, onCrossfadeDurationChange) = rememberPreference(
        CrossfadeDurationKey,
        defaultValue = 5f
    )
    val (creativeTransitionsEnabled, onCreativeTransitionsEnabledChange) = rememberPreference(
        CreativeTransitionsEnabledKey,
        defaultValue = false
    )
    val (autoDjMixingEnabled, onAutoDjMixingEnabledChange) = rememberPreference(
        AutoDjMixingEnabledKey,
        defaultValue = false
    )
    val (localOnlyMode, onLocalOnlyModeChange) = rememberPreference(
        LocalOnlyModeKey,
        defaultValue = false
    )
    val (persistentQueue, onPersistentQueueChange) = rememberPreference(
        PersistentQueueKey,
        defaultValue = true
    )
    val (skipSilence, onSkipSilenceChange) = rememberPreference(
        SkipSilenceKey,
        defaultValue = false
    )
    val (audioNormalization, onAudioNormalizationChange) = rememberPreference(
        AudioNormalizationKey,
        defaultValue = true
    )

    val (enableGoogleCast, onEnableGoogleCastChange) = rememberPreference(
        key = EnableGoogleCastKey,
        defaultValue = true
    )

    val (autoDownloadOnLike, onAutoDownloadOnLikeChange) = rememberPreference(
        AutoDownloadOnLikeKey,
        defaultValue = false
    )
    val (similarContentEnabled, similarContentEnabledChange) = rememberPreference(
        key = SimilarContent,
        defaultValue = true
    )
    val (autoSkipNextOnError, onAutoSkipNextOnErrorChange) = rememberPreference(
        AutoSkipNextOnErrorKey,
        defaultValue = false
    )
    val (rememberShuffleAndRepeat, onRememberShuffleAndRepeatChange) = rememberPreference(
        RememberShuffleAndRepeatKey,
        defaultValue = true
    )
    val (askBeforeReplacingQueue, onAskBeforeReplacingQueueChange) = rememberPreference(
        AskBeforeReplacingQueueKey,
        defaultValue = false
    )
    val (pauseOnMute, onPauseOnMuteChange) = rememberPreference(
        PauseOnMute,
        defaultValue = false
    )
    val (resumeOnBluetoothConnect, onResumeOnBluetoothConnectChange) = rememberPreference(
        ResumeOnBluetoothConnectKey,
        defaultValue = false
    )
    val (keepScreenOn, onKeepScreenOnChange) = rememberPreference(
        KeepScreenOn,
        defaultValue = false
    )
    var showAudioQualityDialog by remember {
        mutableStateOf(false)
    }

    if (showAudioQualityDialog) {
        EnumDialog(
            onDismiss = { showAudioQualityDialog = false },
            onSelect = {
                onAudioQualityChange(it)
                showAudioQualityDialog = false
            },
            title = stringResource(R.string.audio_quality),
            current = audioQuality,
            values = AudioQuality.values().toList(),
            valueText = {
                when (it) {
                    AudioQuality.AUTO -> stringResource(R.string.audio_quality_auto)
                    AudioQuality.HIGH -> stringResource(R.string.audio_quality_high)
                    AudioQuality.LOW -> stringResource(R.string.audio_quality_low)
                }
            }
        )
    }



    val hazeState = remember { HazeState() }
    Box(modifier = Modifier) {
    androidx.compose.foundation.lazy.LazyColumn(
        Modifier
            .hazeSource(hazeState)
            .windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                )
            )
            .padding(horizontal = 16.dp)
    ) {
        item(key = "player_settings_content") {
            Column {
        var showCrossfadeBetaDialog by remember { mutableStateOf(false) }

        if (showCrossfadeBetaDialog) {
            DefaultDialog(
                onDismiss = { showCrossfadeBetaDialog = false },
                title = { Text(stringResource(R.string.crossfade_beta_title)) },
                buttons = {
                    TextButton(onClick = { showCrossfadeBetaDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                    TextButton(onClick = {
                        showCrossfadeBetaDialog = false
                        onCrossfadeEnabledChange(true)
                    }) {
                        Text(stringResource(R.string.enable))
                    }
                }
            ) {
                Text(stringResource(R.string.crossfade_beta_message))
            }
        }

        Spacer(
            Modifier.windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(
                    WindowInsetsSides.Top
                )
            )
        )

        IosSettingsGroup(
            title = stringResource(R.string.player),
            items = buildList {
                add(IosSettingsItem(
                    icon = painterResource(R.drawable.graphic_eq),
                    title = { Text(stringResource(R.string.audio_quality)) },
                    description = {
                        Text(
                            when (audioQuality) {
                                AudioQuality.AUTO -> stringResource(R.string.audio_quality_auto)
                                AudioQuality.HIGH -> stringResource(R.string.audio_quality_high)
                                AudioQuality.LOW -> stringResource(R.string.audio_quality_low)
                            }
                        )
                    },
                    onClick = { showAudioQualityDialog = true }
                ))
                add(IosSettingsItem(
                    icon = painterResource(R.drawable.linear_scale),
                    title = { Text(stringResource(R.string.crossfade)) },
                    description = { Text(stringResource(R.string.crossfade_desc)) },
                    showBadge = true,
                    trailingContent = {
                        Switch(
                            checked = crossfadeEnabled,
                            onCheckedChange = {
                                if (!crossfadeEnabled) {
                                    showCrossfadeBetaDialog = true
                                } else {
                                    onCrossfadeEnabledChange(false)
                                }
                            }
                        )
                    },
                    onClick = {
                        if (!crossfadeEnabled) {
                            showCrossfadeBetaDialog = true
                        } else {
                            onCrossfadeEnabledChange(false)
                        }
                    }
                ))
                if (crossfadeEnabled) {
                    add(IosSettingsItem(
                        icon = painterResource(R.drawable.timer),
                        title = { Text(stringResource(R.string.crossfade_duration)) },
                        description = {
                            Column {
                                Text(pluralStringResource(R.plurals.seconds, crossfadeDuration.toInt(), crossfadeDuration.toInt()))
                                Slider(
                                    value = crossfadeDuration,
                                    onValueChange = onCrossfadeDurationChange,
                                    valueRange = 1f..15f,
                                    steps = 14
                                )
                            }
                        }
                    ))
                }
                // Auto-DJ Mixing needs crossfade on (it beatmatches into the crossfade
                // window); Creative Transitions needs Auto-DJ Mixing on (its effects run
                // inside a DJ-mixed transition). Turning the dependency off cascades down
                // rather than leaving a switch on that can no longer do anything.
                LaunchedEffect(crossfadeEnabled) {
                    if (!crossfadeEnabled && autoDjMixingEnabled) onAutoDjMixingEnabledChange(false)
                }
                LaunchedEffect(autoDjMixingEnabled) {
                    if (!autoDjMixingEnabled && creativeTransitionsEnabled) onCreativeTransitionsEnabledChange(false)
                }
                add(IosSettingsItem(
                    icon = painterResource(R.drawable.equalizer),
                    title = { Text(stringResource(R.string.auto_dj_mixing)) },
                    description = {
                        Text(
                            stringResource(
                                if (crossfadeEnabled) R.string.auto_dj_mixing_desc
                                else R.string.auto_dj_mixing_needs_crossfade
                            )
                        )
                    },
                    enabled = crossfadeEnabled,
                    trailingContent = {
                        Switch(
                            checked = autoDjMixingEnabled,
                            enabled = crossfadeEnabled,
                            onCheckedChange = onAutoDjMixingEnabledChange
                        )
                    },
                    onClick = if (crossfadeEnabled) {
                        { onAutoDjMixingEnabledChange(!autoDjMixingEnabled) }
                    } else null
                ))
                add(IosSettingsItem(
                    icon = painterResource(R.drawable.equalizer),
                    title = { Text(stringResource(R.string.creative_transitions)) },
                    description = {
                        Text(
                            stringResource(
                                if (autoDjMixingEnabled) R.string.creative_transitions_desc
                                else R.string.creative_transitions_needs_dj
                            )
                        )
                    },
                    enabled = autoDjMixingEnabled,
                    trailingContent = {
                        Switch(
                            checked = creativeTransitionsEnabled,
                            enabled = autoDjMixingEnabled,
                            onCheckedChange = onCreativeTransitionsEnabledChange
                        )
                    },
                    onClick = if (autoDjMixingEnabled) {
                        { onCreativeTransitionsEnabledChange(!creativeTransitionsEnabled) }
                    } else null
                ))
                add(IosSettingsItem(
                    icon = painterResource(R.drawable.music_note),
                    title = { Text(stringResource(R.string.local_only_mode)) },
                    description = { Text(stringResource(R.string.local_only_mode_desc)) },
                    trailingContent = {
                        Switch(
                            checked = localOnlyMode,
                            onCheckedChange = onLocalOnlyModeChange
                        )
                    },
                    onClick = { onLocalOnlyModeChange(!localOnlyMode) }
                ))
                add(IosSettingsItem(
                    icon = painterResource(R.drawable.music_note),
                    title = { Text(stringResource(R.string.scan_local_files)) },
                    description = { Text(stringResource(R.string.scan_local_files_desc)) },
                    onClick = { navController.navigate("settings/scan_music") }
                ))
                add(IosSettingsItem(
                    icon = painterResource(R.drawable.fast_forward),
                    title = { Text(stringResource(R.string.skip_silence)) },
                    description = { Text(stringResource(R.string.skip_silence_desc)) },
                    trailingContent = {
                        Switch(
                            checked = skipSilence,
                            onCheckedChange = onSkipSilenceChange
                        )
                    },
                    onClick = { onSkipSilenceChange(!skipSilence) }
                ))
                add(IosSettingsItem(
                    icon = painterResource(R.drawable.volume_up),
                    title = { Text(stringResource(R.string.audio_normalization)) },
                    trailingContent = {
                        Switch(
                            checked = audioNormalization,
                            onCheckedChange = onAudioNormalizationChange
                        )
                    },
                    onClick = { onAudioNormalizationChange(!audioNormalization) }
                ))
                // Only show Cast setting in GMS builds (not in F-Droid/FOSS)
                if (BuildConfig.CAST_AVAILABLE) {
                    add(IosSettingsItem(
                        icon = painterResource(R.drawable.cast),
                        title = { Text(stringResource(R.string.google_cast)) },
                        description = { Text(stringResource(R.string.google_cast_description)) },
                        trailingContent = {
                            Switch(
                                checked = enableGoogleCast,
                                onCheckedChange = onEnableGoogleCastChange
                            )
                        },
                        onClick = { onEnableGoogleCastChange(!enableGoogleCast) }
                    ))
                }
                add(IosSettingsItem(
                    icon = painterResource(R.drawable.viviequlizer),
                    title = { Text(stringResource(R.string.vivi_equalizer)) },
                    description = { Text(stringResource(R.string.vivi_equalizer_desc)) },
                    onClick = { navController.navigate("settings/equalizer") }
                ))
            }
        )

        Spacer(modifier = Modifier.height(27.dp))

        IosSettingsGroup(
            title = stringResource(R.string.queue),
            items = listOf(
                IosSettingsItem(
                    icon = painterResource(R.drawable.queue_music),
                    title = { Text(stringResource(R.string.persistent_queue)) },
                    description = { Text(stringResource(R.string.persistent_queue_desc)) },
                    trailingContent = {
                        Switch(
                            checked = persistentQueue,
                            onCheckedChange = onPersistentQueueChange
                        )
                    },
                    onClick = { onPersistentQueueChange(!persistentQueue) }
                ),
                IosSettingsItem(
                    icon = painterResource(R.drawable.download),
                    title = { Text(stringResource(R.string.auto_download_on_like)) },
                    description = { Text(stringResource(R.string.auto_download_on_like_desc)) },
                    trailingContent = {
                        Switch(
                            checked = autoDownloadOnLike,
                            onCheckedChange = onAutoDownloadOnLikeChange
                        )
                    },
                    onClick = { onAutoDownloadOnLikeChange(!autoDownloadOnLike) }
                ),
                IosSettingsItem(
                    icon = painterResource(R.drawable.similar),
                    title = { Text(stringResource(R.string.enable_similar_content)) },
                    description = { Text(stringResource(R.string.similar_content_desc)) },
                    trailingContent = {
                        Switch(
                            checked = similarContentEnabled,
                            onCheckedChange = similarContentEnabledChange
                        )
                    },
                    onClick = { similarContentEnabledChange(!similarContentEnabled) }
                ),
                IosSettingsItem(
                    icon = painterResource(R.drawable.shuffle),
                    title = { Text(stringResource(R.string.remember_shuffle_and_repeat)) },
                    description = { Text(stringResource(R.string.remember_shuffle_and_repeat_desc)) },
                    trailingContent = {
                        Switch(
                            checked = rememberShuffleAndRepeat,
                            onCheckedChange = onRememberShuffleAndRepeatChange
                        )
                    },
                    onClick = { onRememberShuffleAndRepeatChange(!rememberShuffleAndRepeat) }
                ),
                IosSettingsItem(
                    icon = painterResource(R.drawable.queue_music),
                    title = { Text(stringResource(R.string.ask_before_replacing_queue)) },
                    description = { Text(stringResource(R.string.ask_before_replacing_queue_desc)) },
                    trailingContent = {
                        Switch(
                            checked = askBeforeReplacingQueue,
                            onCheckedChange = onAskBeforeReplacingQueueChange
                        )
                    },
                    onClick = { onAskBeforeReplacingQueueChange(!askBeforeReplacingQueue) }
                ),
                IosSettingsItem(
                    icon = painterResource(R.drawable.skip_next),
                    title = { Text(stringResource(R.string.auto_skip_next_on_error)) },
                    description = { Text(stringResource(R.string.auto_skip_next_on_error_desc)) },
                    trailingContent = {
                        Switch(
                            checked = autoSkipNextOnError,
                            onCheckedChange = onAutoSkipNextOnErrorChange
                        )
                    },
                    onClick = { onAutoSkipNextOnErrorChange(!autoSkipNextOnError) }
                )
            )
        )

        Spacer(modifier = Modifier.height(27.dp))

        IosSettingsGroup(
            title = stringResource(R.string.misc),
            items = listOf(
                IosSettingsItem(
                    icon = painterResource(R.drawable.volume_off_pause),
                    title = { Text(stringResource(R.string.pause_music_when_media_is_muted)) },
                    trailingContent = {
                        Switch(
                            checked = pauseOnMute,
                            onCheckedChange = onPauseOnMuteChange
                        )
                    },
                    onClick = { onPauseOnMuteChange(!pauseOnMute) }
                ),
                IosSettingsItem(
                    icon = painterResource(R.drawable.bluetooth),
                    title = { Text(stringResource(R.string.resume_on_bluetooth_connect)) },
                    trailingContent = {
                        Switch(
                            checked = resumeOnBluetoothConnect,
                            onCheckedChange = onResumeOnBluetoothConnectChange
                        )
                    },
                    onClick = { onResumeOnBluetoothConnectChange(!resumeOnBluetoothConnect) }
                ),
                IosSettingsItem(
                    icon = painterResource(R.drawable.screenshot),
                    title = { Text(stringResource(R.string.keep_screen_on_when_player_is_expanded)) },
                    trailingContent = {
                        Switch(
                            checked = keepScreenOn,
                            onCheckedChange = onKeepScreenOnChange
                        )
                    },
                    onClick = { onKeepScreenOnChange(!keepScreenOn) }
                )
            )
        )

        Spacer(modifier = Modifier.height(27.dp))

        IosSettingsGroup(
            title = stringResource(R.string.advanced),
            items = listOf(
                IosSettingsItem(
                    icon = painterResource(R.drawable.tune),
                    title = { Text(stringResource(R.string.advanced)) },
                    description = { Text(stringResource(R.string.advanced_settings_desc)) },
                    onClick = { navController.navigate("settings/player/advanced") }
                )
            )
        )
        Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

        TopFadeBlur(
            hazeState = hazeState,
            pageColor = IosTheme.colors.systemGroupedBackground,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        FrostedTopBar(
            onBack = navController::navigateUp,
            title = stringResource(R.string.player_and_audio),
            titleAlpha = 1f,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}
