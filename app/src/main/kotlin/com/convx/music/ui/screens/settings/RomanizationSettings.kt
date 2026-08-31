/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.ui.screens.settings

import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.foundation.layout.Column
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.foundation.layout.Spacer
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.foundation.layout.height
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
import com.convx.music.ui.ios.compat.Switch
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.material3.SwitchDefaults
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.ios.compat.Text
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.ios.compat.TopAppBar
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.material3.TopAppBarScrollBehavior
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.runtime.Composable
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.runtime.mutableStateOf
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.runtime.remember
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.ui.Modifier
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.ui.platform.LocalContext
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.ui.res.painterResource
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.ui.res.stringResource
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.ui.unit.dp
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.navigation.NavController
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.LocalPlayerAwareWindowInsets
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.R
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.constants.LyricsRomanizeAsMainKey
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.constants.LyricsRomanizeBelarusianKey
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.constants.LyricsRomanizeBulgarianKey
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.constants.LyricsRomanizeChineseKey
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.constants.LyricsRomanizeHindiKey
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.constants.LyricsRomanizePunjabiKey
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.constants.LyricsRomanizeCyrillicByLineKey
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.constants.LyricsRomanizeJapaneseKey
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.constants.LyricsRomanizeKoreanKey
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.constants.LyricsRomanizeKyrgyzKey
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.constants.LyricsRomanizeMacedonianKey
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.constants.LyricsRomanizeRussianKey
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.constants.LyricsRomanizeSerbianKey
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.constants.LyricsRomanizeUkrainianKey
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.component.ActionPromptDialog
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.component.IconButton
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.component.IosSettingsGroup
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.component.IosSettingsItem
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.utils.backToMain
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.utils.rememberPreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RomanizationSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val context = LocalContext.current

    val (lyricsRomanizeAsMain, onLyricsRomanizeAsMainChange) = rememberPreference(LyricsRomanizeAsMainKey, defaultValue = false)
    val (lyricsRomanizeJapanese, onLyricsRomanizeJapaneseChange) = rememberPreference(LyricsRomanizeJapaneseKey, defaultValue = true)
    val (lyricsRomanizeKorean, onLyricsRomanizeKoreanChange) = rememberPreference(LyricsRomanizeKoreanKey, defaultValue = true)
    val (lyricsRomanizeChinese, onLyricsRomanizeChineseChange) = rememberPreference(LyricsRomanizeChineseKey, defaultValue = true)
    val (lyricsRomanizeHindi, onLyricsRomanizeHindiChange) = rememberPreference(LyricsRomanizeHindiKey, defaultValue = true)
    val (lyricsRomanizePunjabi, onLyricsRomanizePunjabiChange) = rememberPreference(LyricsRomanizePunjabiKey, defaultValue = true)
    val (lyricsRomanizeRussian, onLyricsRomanizeRussianChange) = rememberPreference(LyricsRomanizeRussianKey, defaultValue = true)
    val (lyricsRomanizeUkrainian, onLyricsRomanizeUkrainianChange) = rememberPreference(LyricsRomanizeUkrainianKey, defaultValue = true)
    val (lyricsRomanizeSerbian, onLyricsRomanizeSerbianChange) = rememberPreference(LyricsRomanizeSerbianKey, defaultValue = true)
    val (lyricsRomanizeBulgarian, onLyricsRomanizeBulgarianChange) = rememberPreference(LyricsRomanizeBulgarianKey, defaultValue = true)
    val (lyricsRomanizeBelarusian, onLyricsRomanizeBelarusianChange) = rememberPreference(LyricsRomanizeBelarusianKey, defaultValue = true)
    val (lyricsRomanizeKyrgyz, onLyricsRomanizeKyrgyzChange) = rememberPreference(LyricsRomanizeKyrgyzKey, defaultValue = true)
    val (lyricsRomanizeMacedonian, onLyricsRomanizeMacedonianChange) = rememberPreference(LyricsRomanizeMacedonianKey, defaultValue = true)
    val (lyricsRomanizeCyrillicByLine, onLyricsRomanizeCyrillicByLineChange) = rememberPreference(LyricsRomanizeCyrillicByLineKey, defaultValue = false)
    val (showDialog, setShowDialog) = remember { mutableStateOf(false) }

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        IosSettingsGroup(
            title = stringResource(R.string.general),
            items = listOf(
                IosSettingsItem(
                    icon = painterResource(R.drawable.lyrics),
                    title = { Text(stringResource(R.string.lyrics_romanize_as_main)) },
                    trailingContent = {
                        Switch(
                            checked = lyricsRomanizeAsMain,
                            onCheckedChange = onLyricsRomanizeAsMainChange
                        )
                    },
                    onClick = { onLyricsRomanizeAsMainChange(!lyricsRomanizeAsMain) }
                ),
                IosSettingsItem(
                    icon = painterResource(R.drawable.language_japanese_latin),
                    title = { Text(stringResource(R.string.lyrics_romanize_japanese)) },
                    trailingContent = {
                        Switch(
                            checked = lyricsRomanizeJapanese,
                            onCheckedChange = onLyricsRomanizeJapaneseChange
                        )
                    },
                    onClick = { onLyricsRomanizeJapaneseChange(!lyricsRomanizeJapanese) }
                ),
                IosSettingsItem(
                    icon = painterResource(R.drawable.language_korean_latin),
                    title = { Text(stringResource(R.string.lyrics_romanize_korean)) },
                    trailingContent = {
                        Switch(
                            checked = lyricsRomanizeKorean,
                            onCheckedChange = onLyricsRomanizeKoreanChange
                        )
                    },
                    onClick = { onLyricsRomanizeKoreanChange(!lyricsRomanizeKorean) }
                ),
                IosSettingsItem(
                    icon = painterResource(R.drawable.language),
                    title = { Text(stringResource(R.string.lyrics_romanize_chinese)) },
                    trailingContent = {
                        Switch(
                            checked = lyricsRomanizeChinese,
                            onCheckedChange = onLyricsRomanizeChineseChange
                        )
                    },
                    onClick = { onLyricsRomanizeChineseChange(!lyricsRomanizeChinese) }
                ),
                IosSettingsItem(
                    icon = painterResource(R.drawable.language),
                    title = { Text(stringResource(R.string.lyrics_romanize_hindi)) },
                    trailingContent = {
                        Switch(
                            checked = lyricsRomanizeHindi,
                            onCheckedChange = onLyricsRomanizeHindiChange
                        )
                    },
                    onClick = { onLyricsRomanizeHindiChange(!lyricsRomanizeHindi) }
                ),
                IosSettingsItem(
                    icon = painterResource(R.drawable.language),
                    title = { Text(stringResource(R.string.lyrics_romanize_punjabi)) },
                    trailingContent = {
                        Switch(
                            checked = lyricsRomanizePunjabi,
                            onCheckedChange = onLyricsRomanizePunjabiChange
                        )
                    },
                    onClick = { onLyricsRomanizePunjabiChange(!lyricsRomanizePunjabi) }
                )
            )
        )

        Spacer(modifier = Modifier.height(27.dp))

        IosSettingsGroup(
            title = stringResource(R.string.lyrics_romanization_cyrillic),
            items = listOf(
                IosSettingsItem(
                    icon = painterResource(R.drawable.alphabet_cyrillic),
                    title = { Text(stringResource(R.string.lyrics_romanize_russian)) },
                    trailingContent = {
                        Switch(
                            checked = lyricsRomanizeRussian,
                            onCheckedChange = onLyricsRomanizeRussianChange
                        )
                    },
                    onClick = { onLyricsRomanizeRussianChange(!lyricsRomanizeRussian) }
                ),
                IosSettingsItem(
                    icon = painterResource(R.drawable.alphabet_cyrillic),
                    title = { Text(stringResource(R.string.lyrics_romanize_ukrainian)) },
                    trailingContent = {
                        Switch(
                            checked = lyricsRomanizeUkrainian,
                            onCheckedChange = onLyricsRomanizeUkrainianChange
                        )
                    },
                    onClick = { onLyricsRomanizeUkrainianChange(!lyricsRomanizeUkrainian) }
                ),
                IosSettingsItem(
                    icon = painterResource(R.drawable.alphabet_cyrillic),
                    title = { Text(stringResource(R.string.lyrics_romanize_serbian)) },
                    trailingContent = {
                        Switch(
                            checked = lyricsRomanizeSerbian,
                            onCheckedChange = onLyricsRomanizeSerbianChange
                        )
                    },
                    onClick = { onLyricsRomanizeSerbianChange(!lyricsRomanizeSerbian) }
                ),
                IosSettingsItem(
                    icon = painterResource(R.drawable.alphabet_cyrillic),
                    title = { Text(stringResource(R.string.lyrics_romanize_bulgarian)) },
                    trailingContent = {
                        Switch(
                            checked = lyricsRomanizeBulgarian,
                            onCheckedChange = onLyricsRomanizeBulgarianChange
                        )
                    },
                    onClick = { onLyricsRomanizeBulgarianChange(!lyricsRomanizeBulgarian) }
                ),
                IosSettingsItem(
                    icon = painterResource(R.drawable.alphabet_cyrillic),
                    title = { Text(stringResource(R.string.lyrics_romanize_belarusian)) },
                    trailingContent = {
                        Switch(
                            checked = lyricsRomanizeBelarusian,
                            onCheckedChange = onLyricsRomanizeBelarusianChange
                        )
                    },
                    onClick = { onLyricsRomanizeBelarusianChange(!lyricsRomanizeBelarusian) }
                ),
                IosSettingsItem(
                    icon = painterResource(R.drawable.alphabet_cyrillic),
                    title = { Text(stringResource(R.string.lyrics_romanize_kyrgyz)) },
                    trailingContent = {
                        Switch(
                            checked = lyricsRomanizeKyrgyz,
                            onCheckedChange = onLyricsRomanizeKyrgyzChange
                        )
                    },
                    onClick = { onLyricsRomanizeKyrgyzChange(!lyricsRomanizeKyrgyz) }
                ),
                IosSettingsItem(
                    icon = painterResource(R.drawable.alphabet_cyrillic),
                    title = { Text(stringResource(R.string.lyrics_romanize_macedonian)) },
                    trailingContent = {
                        Switch(
                            checked = lyricsRomanizeMacedonian,
                            onCheckedChange = onLyricsRomanizeMacedonianChange
                        )
                    },
                    onClick = { onLyricsRomanizeMacedonianChange(!lyricsRomanizeMacedonian) }
                ),
                IosSettingsItem(
                    icon = painterResource(R.drawable.warning),
                    title = { Text(stringResource(R.string.line_by_line_option_title)) },
                    description = { Text(stringResource(R.string.line_by_line_option_desc)) },
                    trailingContent = {
                        Switch(
                            checked = lyricsRomanizeCyrillicByLine,
                            onCheckedChange = {
                                if (it) {
                                    setShowDialog(true)
                                } else {
                                    onLyricsRomanizeCyrillicByLineChange(false)
                                }
                            }
                        )
                    },
                    onClick = {
                        if (!lyricsRomanizeCyrillicByLine) {
                            setShowDialog(true)
                        } else {
                            onLyricsRomanizeCyrillicByLineChange(false)
                        }
                    }
                )
            )
        )
        if (showDialog) {
            ActionPromptDialog(
                title = stringResource(R.string.line_by_line_dialog_title),
                onDismiss = { setShowDialog(false) },
                onConfirm = {
                    onLyricsRomanizeCyrillicByLineChange(true)
                    setShowDialog(false)
                },
                onCancel = { setShowDialog(false) },
                content = {
                    Text(stringResource(R.string.line_by_line_dialog_desc))
                }
            )
        }
    }

    TopAppBar(
            windowInsets = appTopBarWindowInsets(),
        title = { Text(stringResource(R.string.lyrics_romanize_title)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null,
                )
            }
        }
    )
}
