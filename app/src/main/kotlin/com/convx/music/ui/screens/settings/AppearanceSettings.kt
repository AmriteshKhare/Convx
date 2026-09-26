/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.ui.screens.settings

import androidx.compose.foundation.layout.Box
import com.convx.music.ui.ios.foundation.IosTheme
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.utils.appTopBarWindowInsets
import android.app.Activity
import com.convx.music.ui.utils.appTopBarWindowInsets
import android.os.Build
import com.convx.music.ui.utils.appTopBarWindowInsets
import android.content.Intent
import com.convx.music.ui.utils.appTopBarWindowInsets
import android.provider.Settings
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.core.net.toUri
import com.convx.music.ui.utils.appTopBarWindowInsets
import android.content.Context
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.core.content.edit
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.utils.bounceClick
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.foundation.layout.Arrangement
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.foundation.layout.Column
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.foundation.layout.Row
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.foundation.layout.Spacer
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.convx.music.ui.ios.compat.AppTheme
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.material3.SnackbarHostState
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
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.runtime.getValue
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.runtime.mutableFloatStateOf
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.runtime.mutableStateOf
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.runtime.remember
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.runtime.saveable.rememberSaveable
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.runtime.setValue
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.ui.Alignment
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.ui.Modifier
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import com.composables.sfsymbols.SfSymbols
import com.composables.sfsymbols.monochrome.SFGaugeWithNeedle
import com.composables.sfsymbols.monochrome.SFIcloudAndArrowDownFill
import com.composables.sfsymbols.monochrome.SFSliderHorizontal3
import com.composables.sfsymbols.monochrome.SFStar
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
import com.convx.music.constants.CanvasSource
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.constants.CanvasSourceKey
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.constants.CanvasThumbnailAnimationKey
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.constants.CropAlbumArtKey
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.constants.DefaultOpenTabKey
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.constants.DynamicThemeKey
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.constants.EnableHighRefreshRateKey
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.constants.OneTapFullscreenLyricsKey
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.constants.HomeHeroCardEnabledKey
import com.convx.music.constants.PureBlackHeroBackgroundKey
import com.convx.music.constants.LiquidGlassGlobalEnabledKey
import com.convx.music.constants.AlbumCanvasEnabledKey
import com.convx.music.constants.AppLanguageKey
import com.convx.music.constants.LanguageCodeToName
import com.convx.music.constants.SYSTEM_DEFAULT
import com.convx.music.ui.ios.compat.Slider
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.constants.ListenTogetherInTopBarKey
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.constants.LyricsAnimationStyle
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.constants.LyricsAnimationStyleKey
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.constants.LyricsTextPositionKey
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.constants.LyricsTextSizeKey
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.constants.ShowCachedPlaylistKey
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.constants.ShowDownloadedPlaylistKey
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.constants.ShowLikedPlaylistKey
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.constants.ShowTopPlaylistKey
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.constants.ShowUploadedPlaylistKey
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.constants.SlimNavBarKey
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.constants.SwipeSensitivityKey
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.constants.SwipeThumbnailKey
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.constants.SwipeLyricsKey
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.constants.SwipeToRemoveSongKey
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.constants.SwipeToSongKey
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.constants.UseNewMiniPlayerDesignKey
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.constants.UseNewPlayerDesignKey
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.utils.appTopBarWindowInsets
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
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.constants.LyricsClickKey
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.constants.LyricsLineSpacingKey
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.constants.LyricsScrollKey
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.constants.MiniPlayerWaveformKey
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.constants.AppFont
import com.convx.music.constants.SelectedFontKey

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    activity: Activity,
    snackbarHostState: SnackbarHostState,
) {
    val (_, _) = rememberPreference(UseNewMiniPlayerDesignKey, defaultValue = true)
    val (_, _) = rememberPreference(DynamicThemeKey, defaultValue = true)
    val (useNewPlayerDesign, _) = rememberPreference(UseNewPlayerDesignKey, defaultValue = false)
    val (miniPlayerWaveform, onMiniPlayerWaveformChange) = rememberPreference(
        MiniPlayerWaveformKey,
        defaultValue = false
    )
    val (selectedFontValue) = rememberPreference(
        SelectedFontKey,
        defaultValue = AppFont.SYSTEM.value
    )

    val (enableHighRefreshRate, onEnableHighRefreshRateChange) = rememberPreference(
        EnableHighRefreshRateKey,
        defaultValue = true
    )
    val (cropAlbumArt, onCropAlbumArtChange) = rememberPreference(
        CropAlbumArtKey,
        defaultValue = false
    )
    val (defaultOpenTab, onDefaultOpenTabChange) = rememberEnumPreference(
        DefaultOpenTabKey,
        defaultValue = NavigationTab.HOME
    )
    val (lyricsPosition, onLyricsPositionChange) = rememberEnumPreference(
        LyricsTextPositionKey,
        defaultValue = LyricsPosition.LEFT
    )
    val (lyricsClick, onLyricsClickChange) = rememberPreference(LyricsClickKey, defaultValue = true)
    val (lyricsScroll, onLyricsScrollChange) = rememberPreference(
        LyricsScrollKey,
        defaultValue = true
    )
    val (lyricsAnimationStyle, onLyricsAnimationStyleChange) = rememberEnumPreference(
        LyricsAnimationStyleKey,
        defaultValue = LyricsAnimationStyle.VIVIMUSIC_1
    )
    val (lyricsTextSize, onLyricsTextSizeChange) = rememberPreference(LyricsTextSizeKey, defaultValue = 30f)
    val (lyricsLineSpacing, onLyricsLineSpacingChange) = rememberPreference(LyricsLineSpacingKey, defaultValue = 1.3f)
    val (swipeLyrics, onSwipeLyricsChange) = rememberPreference(SwipeLyricsKey, defaultValue = false)
    val (oneTapFullscreenLyrics, onOneTapFullscreenLyricsChange) = rememberPreference(OneTapFullscreenLyricsKey, defaultValue = false)

    val (swipeThumbnail, onSwipeThumbnailChange) = rememberPreference(
        SwipeThumbnailKey,
        defaultValue = true
    )
    val (swipeSensitivity, onSwipeSensitivityChange) = rememberPreference(
        SwipeSensitivityKey,
        defaultValue = 0.73f
    )
    val (canvasThumbnailAnimation, onCanvasThumbnailAnimationChange) = rememberPreference(
        CanvasThumbnailAnimationKey,
        defaultValue = true
    )
    val (canvasSource) = rememberEnumPreference(
        CanvasSourceKey,
        defaultValue = CanvasSource.AUTO
    )
    val (pureBlackHeroBackground, onPureBlackHeroBackgroundChange) = rememberPreference(PureBlackHeroBackgroundKey, false)
    val (liquidGlassEnabled, onLiquidGlassEnabledChange) = rememberPreference(
        LiquidGlassGlobalEnabledKey,
        defaultValue = true
    )
    // Moved from Content settings (task 011): this is an album-display option,
    // so it belongs next to the other canvas/player-display toggles here.
    val (albumCanvasEnabled, onAlbumCanvasEnabledChange) = rememberPreference(
        AlbumCanvasEnabledKey,
        defaultValue = false
    )
    // Moved from Content settings (task 011): app language is a general/system
    // setting, not content filtering -- there's no dedicated "General" screen,
    // so it lives in this screen's existing catch-all "misc" group instead.
    val (appLanguage, onAppLanguageChange) = rememberPreference(key = AppLanguageKey, defaultValue = SYSTEM_DEFAULT)
    var showAppLanguageDialog by rememberSaveable { mutableStateOf(false) }
    val (homeHeroCardEnabled, onHomeHeroCardEnabledChange) = rememberPreference(HomeHeroCardEnabledKey, true)

    val (listenTogetherInTopBar, onListenTogetherInTopBarChange) = rememberPreference(
        ListenTogetherInTopBarKey,
        defaultValue = true
    )

    val (swipeToSong, onSwipeToSongChange) = rememberPreference(
        SwipeToSongKey,
        defaultValue = false
    )

    val (swipeToRemoveSong, onSwipeToRemoveSongChange) = rememberPreference(
        SwipeToRemoveSongKey,
        defaultValue = false
    )

    val (showLikedPlaylist, onShowLikedPlaylistChange) = rememberPreference(
        ShowLikedPlaylistKey,
        defaultValue = true
    )
    val (showDownloadedPlaylist, onShowDownloadedPlaylistChange) = rememberPreference(
        ShowDownloadedPlaylistKey,
        defaultValue = true
    )
    val (showTopPlaylist, onShowTopPlaylistChange) = rememberPreference(
        ShowTopPlaylistKey,
        defaultValue = true
    )
    val (showCachedPlaylist, onShowCachedPlaylistChange) = rememberPreference(
        ShowCachedPlaylistKey,
        defaultValue = true
    )
    val (showUploadedPlaylist, onShowUploadedPlaylistChange) = rememberPreference(
        ShowUploadedPlaylistKey,
        defaultValue = true
    )
    var showLyricsPositionDialog by rememberSaveable {
        mutableStateOf(false)
    }

    var showLyricsAnimationStyleDialog by rememberSaveable {
        mutableStateOf(false)
    }

    var showLyricsTextSizeDialog by rememberSaveable {
        mutableStateOf(false)
    }

    var showLyricsLineSpacingDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showLyricsPositionDialog) {
        EnumDialog(
            onDismiss = { showLyricsPositionDialog = false },
            onSelect = {
                onLyricsPositionChange(it)
                showLyricsPositionDialog = false
            },
            title = stringResource(R.string.lyrics_text_position),
            current = lyricsPosition,
            values = LyricsPosition.entries,
            valueText = {
                when (it) {
                    LyricsPosition.LEFT -> stringResource(R.string.left)
                    LyricsPosition.CENTER -> stringResource(R.string.center)
                    LyricsPosition.RIGHT -> stringResource(R.string.right)
                }
            }
        )
    }

    if (showLyricsAnimationStyleDialog) {
        EnumDialog(
            onDismiss = { showLyricsAnimationStyleDialog = false },
            onSelect = {
                onLyricsAnimationStyleChange(it)
                showLyricsAnimationStyleDialog = false
            },
            title = stringResource(R.string.lyrics_animation_style),
            current = lyricsAnimationStyle,
            values = LyricsAnimationStyle.entries,
            valueText = {
                when (it) {
                    LyricsAnimationStyle.NONE -> stringResource(R.string.none)
                    LyricsAnimationStyle.FADE -> stringResource(R.string.fade)
                    LyricsAnimationStyle.GLOW -> stringResource(R.string.glow)
                    LyricsAnimationStyle.SLIDE -> stringResource(R.string.slide)
                    LyricsAnimationStyle.KARAOKE -> stringResource(R.string.karaoke)
                    LyricsAnimationStyle.APPLE -> stringResource(R.string.apple_music_style)
                    LyricsAnimationStyle.APPLE_V2 -> stringResource(R.string.apple_music_style_letter)
                    LyricsAnimationStyle.VIVIMUSIC_1 -> stringResource(R.string.vivimusic_1)
                    LyricsAnimationStyle.LYRICS_V2 -> stringResource(R.string.lyrics_v2_fluid)
                    LyricsAnimationStyle.METRO_LYRICS -> stringResource(R.string.lyrics_animation_metro)
                    LyricsAnimationStyle.ARCHIVE_TUNE -> stringResource(R.string.lyrics_animation_karaoke_apple)
                }
            }
        )
    }

    var showDefaultOpenTabDialog by rememberSaveable { mutableStateOf(false) }
    if (showDefaultOpenTabDialog) {
        EnumDialog(
            onDismiss = { showDefaultOpenTabDialog = false },
            onSelect = {
                onDefaultOpenTabChange(it)
                showDefaultOpenTabDialog = false
            },
            title = stringResource(R.string.default_open_tab),
            current = defaultOpenTab,
            values = NavigationTab.entries,
            valueText = {
                when (it) {
                    NavigationTab.HOME -> stringResource(R.string.home)
                    NavigationTab.SEARCH -> stringResource(R.string.search)
                    NavigationTab.LIBRARY -> stringResource(R.string.filter_library)
                }
            }
        )
    }

    if (showAppLanguageDialog) {
        EnumDialog(
            onDismiss = { showAppLanguageDialog = false },
            onSelect = {
                onAppLanguageChange(it)
                showAppLanguageDialog = false
            },
            title = stringResource(R.string.app_language),
            current = appLanguage,
            values = (listOf(SYSTEM_DEFAULT) + LanguageCodeToName.keys.toList()),
            valueText = {
                LanguageCodeToName.getOrElse(it) { stringResource(R.string.system_default) }
            }
        )
    }

    SettingsLazyPage(
        navController = navController,
        title = stringResource(R.string.appearance),
        pageColor = IosTheme.colors.systemGroupedBackground,
        horizontalPadding = 16.dp,
    ) {
        item(key = "all_appearance_settings") {
            Column {
        IosSettingsGroup(
            title = stringResource(R.string.theme_colors),
            items = listOfNotNull(
                IosSettingsItem(
                    icon = painterResource(R.drawable.palette),
                    title = { Text(stringResource(R.string.theme_colors)) },
                    onClick = { navController.navigate("settings/appearance/theme") }
                ),
                IosSettingsItem(
                    icon = painterResource(R.drawable.alphabet_cyrillic),
                    title = { Text(stringResource(R.string.app_font)) },
                    description = {
                        val fontLabel = when (AppFont.fromValue(selectedFontValue)) {
                            AppFont.SYSTEM -> stringResource(R.string.font_system)
                            AppFont.GOOGLE_SANS -> stringResource(R.string.font_google_sans)
                            AppFont.SANS_FLEX -> stringResource(R.string.font_sans_flex)
                            AppFont.OUTFIT -> stringResource(R.string.font_outfit)
                            AppFont.PLUS_JAKARTA_SANS -> stringResource(R.string.font_plus_jakarta_sans)
                        }
                        Text(fontLabel)
                    },
                    onClick = { navController.navigate("settings/appearance/font") }
                ),
                IosSettingsItem(
                    icon = painterResource(R.drawable.convx_logo),
                    title = { Text(stringResource(R.string.app_icon)) },
                    description = { Text(stringResource(R.string.app_icon_desc)) },
                    onClick = { navController.navigate("settings/appearance/appicon") }
                ),
                IosSettingsItem(
                    icon = painterResource(R.drawable.palette),
                    title = { Text(stringResource(R.string.presets)) },
                    description = { Text(stringResource(R.string.presets_desc)) },
                    onClick = { navController.navigate("settings/appearance/presets") }
                ),
                IosSettingsItem(
                    icon = painterResource(R.drawable.tune),
                    title = { Text(stringResource(R.string.player_icons)) },
                    description = { Text(stringResource(R.string.player_icons_desc)) },
                    onClick = { navController.navigate("settings/appearance/playericons") }
                ),
                // Used to hide behind !useAppleMusicPlayer: DIY only drew its stickers on the
                // classic player. Now that Player_v2 (V17) renders the same DiyStickerLayer
                // too (with a real orientation read and the same behind/in-front z split),
                // the editor applies to both and has no reason to be V17-only hidden.
                IosSettingsItem(
                    icon = painterResource(R.drawable.edit),
                    title = { Text(stringResource(R.string.diy)) },
                    description = { Text(stringResource(R.string.diy_desc)) },
                    onClick = { navController.navigate("settings/appearance/diy") }
                )
            )
        )

        Spacer(modifier = Modifier.height(27.dp))

        IosSettingsGroup(
            title = stringResource(R.string.liquid_glass),
            items = listOf(
                IosSettingsItem(
                    icon = painterResource(R.drawable.tune),
                    title = { Text(stringResource(R.string.liquid_glass)) },
                    description = { Text(stringResource(R.string.liquid_glass_simple_desc)) },
                    trailingContent = {
                        Switch(
                            checked = liquidGlassEnabled,
                            onCheckedChange = onLiquidGlassEnabledChange
                        )
                    },
                    onClick = { onLiquidGlassEnabledChange(!liquidGlassEnabled) }
                ),
                IosSettingsItem(
                    icon = rememberVectorPainter(SfSymbols.Monochrome.SFSliderHorizontal3),
                    title = { Text(stringResource(R.string.advanced)) },
                    description = { Text(stringResource(R.string.advanced_settings_desc)) },
                    onClick = { navController.navigate("settings/appearance/liquidglass") }
                )
            )
        )

        Spacer(modifier = Modifier.height(27.dp))

        IosSettingsGroup(
            title = stringResource(R.string.appearance),
            items = buildList {
                add(
                    IosSettingsItem(
                        icon = rememberVectorPainter(SfSymbols.Monochrome.SFGaugeWithNeedle),
                        title = { Text(stringResource(R.string.enable_high_refresh_rate)) },
                        description = { Text(stringResource(R.string.enable_high_refresh_rate_desc)) },
                        trailingContent = {
                            Switch(
                                checked = enableHighRefreshRate,
                                onCheckedChange = onEnableHighRefreshRateChange
                            )
                        },
                        onClick = { onEnableHighRefreshRateChange(!enableHighRefreshRate) }
                    )
                )
            }
        )

        Spacer(modifier = Modifier.height(27.dp))

        IosSettingsGroup(
            title = stringResource(R.string.player),
            items = listOfNotNull(
                IosSettingsItem(
                    icon = painterResource(R.drawable.gradient),
                    title = { Text(stringResource(R.string.player_theme)) },
                    description = { Text(stringResource(R.string.player_theme_desc)) },
                    onClick = { navController.navigate("settings/appearance/playertheme") }
                ),
                IosSettingsItem(
                    icon = painterResource(R.drawable.crop),
                    title = { Text(stringResource(R.string.crop_album_art)) },
                    description = { Text(stringResource(R.string.crop_album_art_desc)) },
                    trailingContent = {
                        Switch(
                            checked = cropAlbumArt,
                            onCheckedChange = onCropAlbumArtChange
                        )
                    },
                    onClick = { onCropAlbumArtChange(!cropAlbumArt) }
                ),
                IosSettingsItem(
                    icon = painterResource(R.drawable.tune),
                    title = { Text(stringResource(R.string.mini_player_waveform)) },
                    trailingContent = {
                        Switch(
                            checked = miniPlayerWaveform,
                            onCheckedChange = onMiniPlayerWaveformChange
                        )
                    },
                    onClick = { onMiniPlayerWaveformChange(!miniPlayerWaveform) }
                ),
                    IosSettingsItem(
                        icon = painterResource(R.drawable.canvas_art),
                        title = { Text(stringResource(R.string.vivimusic_canvas)) },
                    description = {
                        val summary = if (!canvasThumbnailAnimation) {
                            stringResource(R.string.disable)
                        } else {
                            when (canvasSource) {
                                CanvasSource.AUTO -> stringResource(R.string.canvas_source_auto)
                                CanvasSource.ECHO_MUSIC -> stringResource(R.string.canvas_source_echo_music)
                                CanvasSource.APPLE_MUSIC -> stringResource(R.string.canvas_source_apple_music)
                                CanvasSource.VIVIMUSIC -> stringResource(R.string.canvas_source_vivimusic)
                                CanvasSource.TIDAL -> stringResource(R.string.canvas_source_tidal)
                            }
                        }
                        Text(summary)
                    },
                    onClick = { navController.navigate("settings/appearance/canvas") }
                ),
                IosSettingsItem(
                    icon = painterResource(R.drawable.slow_motion_video),
                    title = { Text(stringResource(R.string.show_album_canvas)) },
                    description = { Text(stringResource(R.string.show_album_canvas_desc)) },
                    trailingContent = {
                        Switch(
                            checked = albumCanvasEnabled,
                            onCheckedChange = onAlbumCanvasEnabledChange
                        )
                    },
                    onClick = { onAlbumCanvasEnabledChange(!albumCanvasEnabled) }
                )
            )
        )

        Spacer(modifier = Modifier.height(27.dp))

        IosSettingsGroup(
            title = stringResource(R.string.lyrics),
            items = listOfNotNull(
                IosSettingsItem(
                    icon = painterResource(R.drawable.lyrics),
                    title = { Text(stringResource(R.string.lyrics_text_position)) },
                    description = {
                        Text(
                            when (lyricsPosition) {
                                LyricsPosition.LEFT -> stringResource(R.string.left)
                                LyricsPosition.CENTER -> stringResource(R.string.center)
                                LyricsPosition.RIGHT -> stringResource(R.string.right)
                            }
                        )
                    },
                    onClick = { showLyricsPositionDialog = true }
                ),
                IosSettingsItem(
                    icon = painterResource(R.drawable.lyrics),
                    title = { Text(stringResource(R.string.lyrics_animation_style)) },
                    description = {
                        Text(
                            when (lyricsAnimationStyle) {
                                LyricsAnimationStyle.NONE -> stringResource(R.string.none)
                                LyricsAnimationStyle.FADE -> stringResource(R.string.fade)
                                LyricsAnimationStyle.GLOW -> stringResource(R.string.glow)
                                LyricsAnimationStyle.SLIDE -> stringResource(R.string.slide)
                                LyricsAnimationStyle.KARAOKE -> stringResource(R.string.karaoke)
                                LyricsAnimationStyle.VIVIMUSIC_1 -> stringResource(R.string.vivimusic_1)
                                LyricsAnimationStyle.APPLE -> stringResource(R.string.apple_music_style)
                                LyricsAnimationStyle.APPLE_V2 -> stringResource(R.string.apple_music_style_letter)
                                LyricsAnimationStyle.LYRICS_V2 -> stringResource(R.string.lyrics_v2_fluid)
                                LyricsAnimationStyle.METRO_LYRICS -> stringResource(R.string.lyrics_animation_metro)
                                LyricsAnimationStyle.ARCHIVE_TUNE -> stringResource(R.string.lyrics_animation_karaoke_apple)
                            }
                        )
                    },
                    onClick = { showLyricsAnimationStyleDialog = true }
                ),
                IosSettingsItem(
                    icon = painterResource(R.drawable.lyrics),
                    title = { Text(stringResource(R.string.lyrics_auto_scroll)) },
                    trailingContent = {
                        Switch(
                            checked = lyricsScroll,
                            onCheckedChange = onLyricsScrollChange
                        )
                    },
                    onClick = { onLyricsScrollChange(!lyricsScroll) }
                ),
                IosSettingsItem(
                    icon = painterResource(R.drawable.swipe),
                    title = { Text(stringResource(R.string.lyrics_swipe_to_change_song)) },
                    description = { Text(stringResource(R.string.lyrics_swipe_to_change_song_desc)) },
                    trailingContent = {
                        Switch(
                            checked = swipeLyrics,
                            onCheckedChange = onSwipeLyricsChange
                        )
                    },
                    onClick = { onSwipeLyricsChange(!swipeLyrics) }
                ),
                IosSettingsItem(
                    icon = painterResource(R.drawable.fullscreen),
                    title = { Text(stringResource(R.string.one_tap_fullscreen_lyrics)) },
                    description = { Text(stringResource(R.string.one_tap_fullscreen_lyrics_desc)) },
                    trailingContent = {
                        Switch(
                            checked = oneTapFullscreenLyrics,
                            onCheckedChange = onOneTapFullscreenLyricsChange
                        )
                    },
                    onClick = { onOneTapFullscreenLyricsChange(!oneTapFullscreenLyrics) }
                )
            )
        )

        Spacer(modifier = Modifier.height(27.dp))

        IosSettingsGroup(
            title = stringResource(R.string.misc),
            items = listOf(
                IosSettingsItem(
                    icon = painterResource(R.drawable.nav_bar),
                    title = { Text(stringResource(R.string.default_open_tab)) },
                    description = {
                        Text(
                            when (defaultOpenTab) {
                                NavigationTab.HOME -> stringResource(R.string.home)
                                NavigationTab.SEARCH -> stringResource(R.string.search)
                                NavigationTab.LIBRARY -> stringResource(R.string.filter_library)
                            }
                        )
                    },
                    onClick = { showDefaultOpenTabDialog = true }
                ),
                IosSettingsItem(
                    icon = painterResource(R.drawable.swipe),
                    title = { Text(stringResource(R.string.swipe_song_to_add)) },
                    trailingContent = {
                        Switch(
                            checked = swipeToSong,
                            onCheckedChange = onSwipeToSongChange
                        )
                    },
                    onClick = { onSwipeToSongChange(!swipeToSong) }
                ),
                IosSettingsItem(
                    icon = painterResource(R.drawable.swipe),
                    title = { Text(stringResource(R.string.swipe_song_to_remove)) },
                    trailingContent = {
                        Switch(
                            checked = swipeToRemoveSong,
                            onCheckedChange = onSwipeToRemoveSongChange
                        )
                    },
                    onClick = { onSwipeToRemoveSongChange(!swipeToRemoveSong) }
                ),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    IosSettingsItem(
                        icon = painterResource(R.drawable.language),
                        title = { Text(stringResource(R.string.app_language)) },
                        onClick = {
                            activity.startActivity(
                                Intent(
                                    Settings.ACTION_APP_LOCALE_SETTINGS,
                                    "package:${activity.packageName}".toUri()
                                )
                            )
                        }
                    )
                } else {
                    IosSettingsItem(
                        icon = painterResource(R.drawable.language),
                        title = { Text(stringResource(R.string.app_language)) },
                        description = {
                            Text(
                                LanguageCodeToName.getOrElse(appLanguage) { stringResource(R.string.system_default) }
                            )
                        },
                        onClick = { showAppLanguageDialog = true }
                    )
                }
            )
        )

        Spacer(modifier = Modifier.height(27.dp))

        IosSettingsGroup(
            title = stringResource(R.string.grid_and_cards),
            items = listOf(
                IosSettingsItem(
                    icon = painterResource(R.drawable.grid_view),
                    title = { Text(stringResource(R.string.pure_black_hero_background)) },
                    description = { Text(stringResource(R.string.pure_black_hero_background_desc)) },
                    trailingContent = {
                        Switch(
                            checked = pureBlackHeroBackground,
                            onCheckedChange = onPureBlackHeroBackgroundChange
                        )
                    },
                    onClick = { onPureBlackHeroBackgroundChange(!pureBlackHeroBackground) }
                ),
                IosSettingsItem(
                    icon = painterResource(R.drawable.grid_view),
                    title = { Text(stringResource(R.string.home_hero_card)) },
                    description = { Text(stringResource(R.string.home_hero_card_desc)) },
                    trailingContent = {
                        Switch(
                            checked = homeHeroCardEnabled,
                            onCheckedChange = onHomeHeroCardEnabledChange,
                        )
                    },
                    onClick = { onHomeHeroCardEnabledChange(!homeHeroCardEnabled) },
                ),
            )
        )

        Spacer(modifier = Modifier.height(27.dp))

        IosSettingsGroup(
            title = stringResource(R.string.auto_playlists),
            items = listOf(
                IosSettingsItem(
                    icon = rememberVectorPainter(SfSymbols.Monochrome.SFStar),
                    title = { Text(stringResource(R.string.show_liked_playlist)) },
                    trailingContent = {
                        Switch(
                            checked = showLikedPlaylist,
                            onCheckedChange = onShowLikedPlaylistChange
                        )
                    },
                    onClick = { onShowLikedPlaylistChange(!showLikedPlaylist) }
                ),
                IosSettingsItem(
                    icon = rememberVectorPainter(SfSymbols.Monochrome.SFIcloudAndArrowDownFill),
                    title = { Text(stringResource(R.string.show_downloaded_playlist)) },
                    trailingContent = {
                        Switch(
                            checked = showDownloadedPlaylist,
                            onCheckedChange = onShowDownloadedPlaylistChange
                        )
                    },
                    onClick = { onShowDownloadedPlaylistChange(!showDownloadedPlaylist) }
                ),
                IosSettingsItem(
                    icon = painterResource(R.drawable.trending_up),
                    title = { Text(stringResource(R.string.show_top_playlist)) },
                    trailingContent = {
                        Switch(
                            checked = showTopPlaylist,
                            onCheckedChange = onShowTopPlaylistChange
                        )
                    },
                    onClick = { onShowTopPlaylistChange(!showTopPlaylist) }
                ),
                IosSettingsItem(
                    icon = painterResource(R.drawable.cached),
                    title = { Text(stringResource(R.string.show_cached_playlist)) },
                    trailingContent = {
                        Switch(
                            checked = showCachedPlaylist,
                            onCheckedChange = onShowCachedPlaylistChange
                        )
                    },
                    onClick = { onShowCachedPlaylistChange(!showCachedPlaylist) }
                ),
                IosSettingsItem(
                    icon = painterResource(R.drawable.backup),
                    title = { Text(stringResource(R.string.show_uploaded_playlist)) },
                    trailingContent = {
                        Switch(
                            checked = showUploadedPlaylist,
                            onCheckedChange = onShowUploadedPlaylistChange
                        )
                    },
                    onClick = { onShowUploadedPlaylistChange(!showUploadedPlaylist) }
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
                    onClick = { navController.navigate("settings/appearance/advanced") }
                )
            )
        )
        Spacer(modifier = Modifier.height(16.dp))
            }
        }
    
    }
}

enum class DarkMode {
    ON,
    OFF,
    AUTO,
}

enum class NavigationTab {
    HOME,
    SEARCH,
    LIBRARY,
}

enum class LyricsPosition {
    LEFT,
    CENTER,
    RIGHT,
}

enum class PlayerTextAlignment {
    SIDED,
    CENTER,
}
