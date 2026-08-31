/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.ui.screens.settings

import com.convx.music.R
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import com.convx.music.ui.ios.compat.HorizontalDivider
import com.convx.music.ui.ios.compat.Icon
import com.convx.music.ui.ios.compat.Switch
import androidx.compose.material3.SwitchDefaults
import com.convx.music.ui.ios.compat.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import com.convx.music.ui.component.FrostedTopBar
import com.convx.music.ui.component.TopFadeBlur
import com.convx.music.ui.component.rememberTitleCollapseProgress
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.convx.music.BuildConfig
import com.convx.music.LocalPlayerAwareWindowInsets
import com.convx.music.ui.component.IosSettingsGroup
import com.convx.music.ui.component.shapes.ContinuousRoundedRectangle
import com.convx.music.ui.component.shapes.ContinuousCapsule
import com.convx.music.ui.ios.foundation.IosMetrics
import com.convx.music.ui.ios.foundation.IosTheme
import com.convx.music.ui.ios.foundation.iosRowPress
import com.convx.music.ui.component.IosSettingsItem
import com.convx.music.ui.utils.isScrollingUp
import com.convx.music.vivimusic.updater.getUpdateAvailableState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val isUpdateAvailable = getUpdateAvailableState(context) &&
        com.convx.music.vivimusic.updater.getAutoUpdateCheckSetting(context)

    var searchQuery by rememberSaveable { mutableStateOf("") }
    val searchLower = searchQuery.trim().lowercase()
    val listState = rememberLazyListState()
    // Progressive top blur so the list dissolves under the status bar instead
    // of cutting off hard — same technique as the rest of the app's screens.
    val hazeState = remember { HazeState() }

    Box(
        modifier = Modifier
            // Explicit rather than relying on whatever's painted behind the NavHost:
            // this is an iOS grouped-list page, so it owns the grouped background
            // token directly instead of inheriting MainActivity's flat systemBackground.
            .background(IosTheme.colors.systemGroupedBackground),
    ) {
    androidx.compose.foundation.lazy.LazyColumn(
        state = listState,
        modifier = Modifier
            .hazeSource(hazeState)
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal))
    ) {
        item(key = "top_spacer") {
            Spacer(
                Modifier.windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)
                )
            )
        }

        // Large title (iOS style)
        item(key = "title") {
            Text(
                text = stringResource(R.string.settings),
                style = IosTheme.typography.largeTitle,
                color = IosTheme.colors.label,
                modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 14.dp)
            )
        }

        // Search field — filters the settings catalog and jumps to the matching screen.
        // It collapses as you scroll down and comes back on the way up, the way iOS
        // hides the search bar in Settings and Mail. A query in progress pins it open:
        // hiding the field you are typing into would be a bug, not a flourish.
        item(key = "search_field") {
            AnimatedVisibility(
                visible = listState.isScrollingUp() || searchQuery.isNotEmpty(),
                enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
            ) {
            SettingsSearchField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = stringResource(R.string.search),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            )
            }
        }

        if (searchLower.isEmpty()) {
        // Section: General
        item(key = "general_header") { SettingsSectionHeader("General") }
        item(key = "general_section") {
            SettingsSection {
                SettingsNavItem(
                    icon = painterResource(if (isUpdateAvailable) R.drawable.convx_logo else R.drawable.network_update),
                    iconTint = if (isUpdateAvailable) IosTheme.colors.systemRed else IosTheme.colors.systemBlue,
                    title = stringResource(R.string.system_update),
                    badge = if (isUpdateAvailable) stringResource(R.string.update_available) else null,
                    onClick = { navController.navigate("settings/update") },
                )
                SettingsDivider()
                SettingsNavItem(
                    icon = painterResource(R.drawable.palette),
                    iconTint = IosTheme.colors.systemPurple,
                    title = stringResource(R.string.appearance),
                    onClick = { navController.navigate("settings/appearance") },
                )
                SettingsDivider()
                SettingsNavItem(
                    icon = painterResource(R.drawable.play),
                    iconTint = IosTheme.colors.systemPink,
                    title = stringResource(R.string.player_and_audio),
                    onClick = { navController.navigate("settings/player") },
                )
            }
        }

        item(key = "spacer_1") { Spacer(Modifier.height(24.dp)) }

        // Section: Account
        item(key = "account_header") { SettingsSectionHeader("Account") }
        item(key = "account_section") {
            SettingsSection {
                SettingsNavItem(
                    icon = painterResource(R.drawable.account),
                    iconTint = IosTheme.colors.systemGreen,
                    title = stringResource(R.string.account),
                    onClick = { navController.navigate("settings/account") },
                )
                SettingsDivider()
                SettingsNavItem(
                    icon = painterResource(R.drawable.group),
                    iconTint = IosTheme.colors.systemIndigo,
                    title = stringResource(R.string.listen_together),
                    // Screens.ListenTogether.route ("listen_together") is a
                    // MainTabsPager page now, not its own NavHost destination, and
                    // this screen has no access to the pager state to scroll it.
                    // "listen_together_from_topbar" is the same screen (showTopBar =
                    // true) as a real, always-registered destination -- exactly what
                    // the top bar's own Listen Together entry point already uses.
                    onClick = { navController.navigate("listen_together_from_topbar") },
                )
            }
        }

        item(key = "spacer_activity") { Spacer(Modifier.height(24.dp)) }

        // Section: Activity — history/stats moved here from the top bar, which now
        // only shows the logo and this settings/profile pill.
        item(key = "activity_header") { SettingsSectionHeader("Activity") }
        item(key = "activity_section") {
            SettingsSection {
                SettingsNavItem(
                    icon = painterResource(R.drawable.music_history),
                    iconTint = IosTheme.colors.systemCyan,
                    title = stringResource(R.string.history),
                    onClick = { navController.navigate("history") },
                )
                SettingsDivider()
                SettingsNavItem(
                    icon = painterResource(R.drawable.stats),
                    iconTint = IosTheme.colors.systemYellow,
                    title = stringResource(R.string.stats),
                    onClick = { navController.navigate("stats") },
                )
            }
        }

        item(key = "spacer_2") { Spacer(Modifier.height(24.dp)) }

        // Section: Content
        item(key = "content_header") { SettingsSectionHeader("Content") }
        item(key = "content_section") {
            SettingsSection {
                SettingsNavItem(
                    icon = painterResource(R.drawable.language),
                    iconTint = IosTheme.colors.systemBlue,
                    title = stringResource(R.string.content),
                    onClick = { navController.navigate("settings/content") },
                )
                SettingsDivider()
                SettingsNavItem(
                    icon = painterResource(R.drawable.link),
                    iconTint = IosTheme.colors.systemIndigo,
                    title = stringResource(R.string.modules),
                    onClick = { navController.navigate("settings/modules") },
                )
                SettingsDivider()
                SettingsNavItem(
                    icon = painterResource(R.drawable.translate),
                    iconTint = IosTheme.colors.systemOrange,
                    title = stringResource(R.string.ai_lyrics_translation),
                    onClick = { navController.navigate("settings/ai") },
                )
            }
        }

        item(key = "spacer_3") { Spacer(Modifier.height(24.dp)) }

        // Section: Data & Privacy
        item(key = "privacy_header") { SettingsSectionHeader("Data & Privacy") }
        item(key = "privacy_section") {
            SettingsSection {
                SettingsNavItem(
                    icon = painterResource(R.drawable.security),
                    iconTint = IosTheme.colors.systemBlue,
                    title = stringResource(R.string.privacy),
                    onClick = { navController.navigate("settings/privacy") },
                )
                SettingsDivider()
                SettingsNavItem(
                    icon = painterResource(R.drawable.storage),
                    iconTint = IosTheme.colors.systemGray,
                    title = stringResource(R.string.storage),
                    onClick = { navController.navigate("settings/storage") },
                )
                SettingsDivider()
                SettingsNavItem(
                    icon = painterResource(R.drawable.restore),
                    iconTint = IosTheme.colors.systemGreen,
                    title = stringResource(R.string.backup_restore),
                    onClick = { navController.navigate("settings/backup_restore") },
                )
            }
        }

        item(key = "spacer_4") { Spacer(Modifier.height(24.dp)) }

        // Section: About
        item(key = "about_section") {
            SettingsSection {
                SettingsNavItem(
                    icon = painterResource(R.drawable.info),
                    iconTint = IosTheme.colors.systemBlue,
                    title = stringResource(R.string.about),
                    onClick = { navController.navigate("settings/about") },
                )
            }
        }

        item(key = "bottom_spacer") {
            Spacer(
                Modifier
                    .height(50.dp)
                    .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom))
            )
        }
        } else {
            item(key = "search_results") {
                val results = getAllSearchableSettings()
                    .filter {
                        it.title.lowercase().contains(searchLower) ||
                            it.description?.lowercase()?.contains(searchLower) == true
                    }
                if (results.isEmpty()) {
                    Text(
                        text = "No settings found for \"$searchQuery\"",
                        style = IosTheme.typography.body,
                        color = IosTheme.colors.secondaryLabel,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 32.dp)
                    )
                } else {
                    IosSettingsGroup(
                        items = results.map { setting ->
                            IosSettingsItem(
                                icon = painterResource(R.drawable.search),
                                title = { Text(setting.title) },
                                description = { Text(setting.category) },
                                onClick = { navController.navigate(setting.route) }
                            )
                        }
                    )
                }
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
            title = stringResource(R.string.settings),
            titleAlpha = rememberTitleCollapseProgress(listState),
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = IosTheme.typography.subheadline,
        color = IosTheme.colors.secondaryLabel,
        modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingsSection(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(ContinuousRoundedRectangle(IosMetrics.GroupedCornerRadius))
            .background(IosTheme.colors.secondarySystemGroupedBackground),
        content = content,
    )
}

@Composable
private fun SettingsNavItem(
    icon: Painter,
    iconTint: Color,
    title: String,
    badge: String? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .iosRowPress(onClick = onClick)
            .defaultMinSize(minHeight = IosMetrics.ListRowHeight)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // iOS puts the glyph ON the colour, not in a 15%-alpha wash of it.
        Box(
            modifier = Modifier
                .size(IosMetrics.IconTileSize)
                .clip(ContinuousRoundedRectangle(IosMetrics.IconTileRadius))
                .background(iconTint),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }

        Spacer(Modifier.width(12.dp))

        // Title
        Text(
            text = title,
            style = IosTheme.typography.body,
            color = IosTheme.colors.label,
            modifier = Modifier.weight(1f),
        )

        // Badge or chevron
        if (badge != null) {
            Text(
                text = badge,
                style = IosTheme.typography.footnote,
                color = IosTheme.colors.systemRed,
            )
            Spacer(Modifier.width(8.dp))
        }

        Icon(
            painter = painterResource(R.drawable.chevron_right_px),
            contentDescription = null,
            tint = IosTheme.colors.tertiaryLabel,
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        // Inset to the text column, clearing the glyph tile — the iOS separator rule.
        modifier = Modifier.padding(start = 57.dp),
        color = IosTheme.colors.separator,
        thickness = IosMetrics.SeparatorThickness,
    )
}

/**
 * Settings' own search field: wider and taller than the shared [com.convx.music.ui.ios.compat.IosSearchField]
 * pill, with a Cancel affordance that fades in as a frosted capsule (Apple's blurred-glass
 * chip look) instead of plain text once there's something to cancel.
 */
@Composable
private fun SettingsSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
) {
    val colors = IosTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
                .clip(ContinuousCapsule())
                .background(colors.secondarySystemFill)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.search),
                    contentDescription = null,
                    tint = colors.secondaryLabel,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = IosTheme.typography.body,
                            color = colors.secondaryLabel,
                        )
                    }
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        modifier = Modifier.fillMaxWidth(),
                        interactionSource = interactionSource,
                        textStyle = IosTheme.typography.body.copy(color = colors.label),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(IosTheme.tint),
                        singleLine = true,
                    )
                }
            }
        }

        // Cupertino's own search-field motion: Cancel slides in from the trailing edge
        // as the field shrinks to make room, rather than popping in place — the axis
        // that was wrong before (expand/shrinkVertically animates height, not the
        // horizontal reveal this actually is). Plain text, no chip: Cupertino's real
        // Cancel button has no background either.
        AnimatedVisibility(
            visible = isFocused || value.isNotEmpty(),
            enter = expandHorizontally(expandFrom = Alignment.Start) + fadeIn(),
            exit = shrinkHorizontally(shrinkTowards = Alignment.Start) + fadeOut(),
        ) {
            Text(
                text = stringResource(android.R.string.cancel),
                style = IosTheme.typography.body,
                color = IosTheme.tint,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .clickable {
                        onValueChange("")
                        focusManager.clearFocus()
                    },
            )
        }
    }
}
