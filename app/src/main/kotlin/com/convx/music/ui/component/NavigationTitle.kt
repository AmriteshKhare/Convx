/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import com.convx.music.ui.ios.compat.AppTheme
import com.convx.music.ui.ios.compat.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.convx.music.R

import com.convx.music.LocalAmbientBackgroundColor
import com.convx.music.ui.ios.compat.LocalContentColor
import com.convx.music.ui.theme.AppleTokens
import com.convx.music.ui.theme.LocalPageMetrics
import com.convx.music.ui.theme.LocalAccentTextColor
import com.convx.music.ui.theme.isHueless
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified

@Composable
fun NavigationTitle(
    title: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    thumbnail: (@Composable () -> Unit)? = null,
    color: Color? = null,
    onClick: (() -> Unit)? = null,
    onPlayAllClick: (() -> Unit)? = null,
) {
    // Headings stay neutral per theme; only hero screens keep the
    // artwork-derived tint (provided into this local), so a section title
    // matches the screen it is on. The See All/Play All link beside it
    // always keeps the accent — links are in accent scope.
    val ambient = LocalAmbientBackgroundColor.current.value
    val contentColor = color ?: if (ambient.isSpecified && !ambient.isHueless()) {
        LocalAccentTextColor.current
    } else {
        AppTheme.colorScheme.onSurface
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
            .clickable(enabled = onClick != null) {
                onClick?.invoke()
            }
            .padding(
                start = LocalPageMetrics.current.gutter,
                end = LocalPageMetrics.current.gutter,
                top = LocalPageMetrics.current.headerTop,
                bottom = LocalPageMetrics.current.headerBottom,
            )
    ) {
        thumbnail?.invoke()

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Column(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.weight(1f, fill = false)
            ) {
                label?.let { label ->
                    Text(
                        text = label,
                        style = AppTheme.typography.labelLarge,
                        color = contentColor.copy(alpha = 0.6f),
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Text(
                    text = title,
                    fontSize = AppleTokens.SectionHeader,
                    lineHeight = AppleTokens.SectionHeaderLineHeight,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.01).em,
                    color = contentColor,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                )
            }
        }

        // "See All" / "Play all" as a plain accent text action rather than an
        // outlined button or a chevron: the design keeps every section header to
        // one weight of chrome, so the action reads as a link beside the title
        // instead of as a control stacked on it. Links stay accent even though
        // the title beside them is neutral.
        val action = onPlayAllClick ?: onClick
        if (action != null) {
            Text(
                text = stringResource(
                    if (onPlayAllClick != null) R.string.play_all else R.string.see_all
                ),
                fontSize = AppleTokens.ItemTitle,
                lineHeight = AppleTokens.ItemTitleLineHeight,
                fontWeight = FontWeight.Normal,
                color = LocalAccentTextColor.current,
                maxLines = 1,
                modifier = Modifier.clickable(onClick = action),
            )
        }
    }
}
