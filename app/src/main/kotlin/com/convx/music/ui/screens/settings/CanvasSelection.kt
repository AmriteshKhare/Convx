/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.ui.screens.settings

import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.animation.animateColorAsState
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.utils.bounceClick
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.utils.combinedBounceClick
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.foundation.layout.Arrangement
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.foundation.layout.Box
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.foundation.layout.Column
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.foundation.layout.Row
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.foundation.layout.Spacer
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.foundation.layout.height
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.foundation.layout.only
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.foundation.layout.padding
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.foundation.rememberScrollState
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.foundation.shape.RoundedCornerShape
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.foundation.verticalScroll
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.ios.compat.Card
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.material3.CardDefaults
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.material3.ExperimentalMaterial3Api
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.ios.compat.Icon
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.ios.compat.AppTheme
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.ios.compat.RadioButton
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.ios.compat.Switch
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.ios.compat.Text
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.material3.TopAppBarDefaults
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.material3.TopAppBarScrollBehavior
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.ui.graphics.Color
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.runtime.getValue
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.ui.Alignment
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.ui.Modifier
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
import com.convx.music.constants.CanvasSource
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.constants.CanvasSourceKey
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.constants.CanvasThumbnailAnimationKey
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.component.ExpressiveIconButton
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.component.IconButton
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.component.IosSettingsGroup
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.component.IosSettingsItem
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.component.ModernSwitch
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.ui.utils.backToMain
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.utils.rememberEnumPreference
import com.convx.music.ui.utils.appTopBarWindowInsets
import com.convx.music.utils.rememberPreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CanvasSelection(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val (canvasThumbnailAnimation, onCanvasThumbnailAnimationChange) = rememberPreference(
        CanvasThumbnailAnimationKey,
        defaultValue = true
    )
    val (canvasSource, onCanvasSourceChange) = rememberEnumPreference(
        CanvasSourceKey,
        defaultValue = CanvasSource.AUTO
    )

    SettingsPage(
        navController = navController,
        title = stringResource(R.string.vivimusic_canvas),
        pageColor = AppTheme.colorScheme.background,
        horizontalPadding = 24.dp,
    ) {
        // Description text
        Text(
            text = stringResource(R.string.vivimusic_canvas_desc),
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 24.dp, top = 16.dp)
        )

        // Large capsule banner for main toggle
        val containerColor by animateColorAsState(
            targetValue = if (canvasThumbnailAnimation) {
                AppTheme.colorScheme.primaryContainer
            } else {
                AppTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            },
            label = "containerColor"
        )

        val contentColor = if (canvasThumbnailAnimation) {
            AppTheme.colorScheme.onPrimaryContainer
        } else {
            AppTheme.colorScheme.onSurfaceVariant
        }

        Card(
            onClick = { onCanvasThumbnailAnimationChange(!canvasThumbnailAnimation) },
            shape = RoundedCornerShape(50),
            colors = CardDefaults.cardColors(containerColor = containerColor),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.use_canvas),
                    style = AppTheme.typography.titleMedium,
                    color = contentColor
                )
                ModernSwitch(
                    checked = canvasThumbnailAnimation,
                    onCheckedChange = onCanvasThumbnailAnimationChange
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Options settings group
        IosSettingsGroup(
            title = stringResource(R.string.canvas_source),
            items = listOf(
                IosSettingsItem(
                    leadingContent = {
                        RadioButton(
                            selected = canvasSource == CanvasSource.AUTO,
                            onClick = null,
                            enabled = canvasThumbnailAnimation
                        )
                    },
                    title = { Text(stringResource(R.string.canvas_source_auto)) },
                    description = { Text(stringResource(R.string.canvas_source_auto_desc)) },
                    enabled = canvasThumbnailAnimation,
                    onClick = { onCanvasSourceChange(CanvasSource.AUTO) }
                ),
                IosSettingsItem(
                    leadingContent = {
                        RadioButton(
                            selected = canvasSource == CanvasSource.ECHO_MUSIC,
                            onClick = null,
                            enabled = canvasThumbnailAnimation
                        )
                    },
                    title = { Text(stringResource(R.string.canvas_source_echo_music)) },
                    description = { Text(stringResource(R.string.canvas_source_echo_music_desc)) },
                    enabled = canvasThumbnailAnimation,
                    onClick = { onCanvasSourceChange(CanvasSource.ECHO_MUSIC) }
                ),
                IosSettingsItem(
                    leadingContent = {
                        RadioButton(
                            selected = canvasSource == CanvasSource.APPLE_MUSIC,
                            onClick = null,
                            enabled = canvasThumbnailAnimation
                        )
                    },
                    title = { Text(stringResource(R.string.canvas_source_apple_music)) },
                    description = { Text(stringResource(R.string.canvas_source_apple_music_desc)) },
                    enabled = canvasThumbnailAnimation,
                    onClick = { onCanvasSourceChange(CanvasSource.APPLE_MUSIC) }
                ),
                IosSettingsItem(
                    leadingContent = {
                        RadioButton(
                            selected = canvasSource == CanvasSource.VIVIMUSIC,
                            onClick = null,
                            enabled = canvasThumbnailAnimation
                        )
                    },
                    title = { Text(stringResource(R.string.canvas_source_vivimusic)) },
                    description = { Text(stringResource(R.string.canvas_source_vivimusic_desc)) },
                    enabled = canvasThumbnailAnimation,
                    onClick = { onCanvasSourceChange(CanvasSource.VIVIMUSIC) }
                ),
                IosSettingsItem(
                    leadingContent = {
                        RadioButton(
                            selected = canvasSource == CanvasSource.TIDAL,
                            onClick = null,
                            enabled = canvasThumbnailAnimation
                        )
                    },
                    title = { Text(stringResource(R.string.canvas_source_tidal)) },
                    description = { Text(stringResource(R.string.canvas_source_tidal_desc)) },
                    enabled = canvasThumbnailAnimation,
                    onClick = { onCanvasSourceChange(CanvasSource.TIDAL) }
                )
            )
        )
        Spacer(modifier = Modifier.height(36.dp))
    
    }
}
