/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.ui.component

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.dp
import com.convx.music.ui.component.shapes.ContinuousRoundedRectangle
import com.convx.music.ui.ios.compat.ProvideTextStyle
import com.convx.music.ui.ios.foundation.IosMetrics
import com.convx.music.ui.ios.foundation.IosTheme
import com.convx.music.ui.ios.foundation.iosRowPress
import com.convx.music.ui.ios.foundation.LocalIosContentColor
import com.convx.music.ui.ios.foundation.LocalIosTextStyle

/**
 * An iOS inset-grouped action list: one continuous-cornered card whose rows are split
 * by hairline separators inset to the text column.
 *
 * This replaced a stack of individually rounded Material cards — iOS never breaks a
 * group into separate cards, and the per-row rounding was the loudest Material tell in
 * every menu sheet.
 */
@Composable
fun IosMenuGroup(
    items: List<IosMenuItemData>,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    val colors = IosTheme.colors

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(ContinuousRoundedRectangle(IosMetrics.GroupedCornerRadius))
            .background(colors.secondarySystemGroupedBackground)
            .animateContentSize()
    ) {
        items.forEachIndexed { index, item ->
            if (index > 0) {
                Box(
                    Modifier
                        .padding(start = if (item.icon != null) 60.dp else 16.dp)
                        .fillMaxWidth()
                        .height(IosMetrics.SeparatorThickness)
                        .background(colors.separator)
                )
            }
            IosMenuItemRow(item)
        }
    }
}

@Composable
private fun IosMenuItemRow(item: IosMenuItemData) {
    val colors = IosTheme.colors
    // A destructive row colours its label as well as its icon — that, not a red fill,
    // is how iOS marks "Delete" apart from the rows above it.
    val contentColor = if (item.tint.isSpecified) item.tint else colors.label

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .iosRowPress(onClick = item.onClick)
            .defaultMinSize(minHeight = IosMetrics.ListRowHeight)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item.icon?.let { icon ->
            Box(Modifier.width(28.dp), contentAlignment = Alignment.CenterStart) {
                ProvideIosContent(contentColor, IosTheme.typography.body) { icon() }
            }
            Spacer(Modifier.width(16.dp))
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            ProvideIosContent(contentColor, IosTheme.typography.body) { item.title() }
            item.description?.let { desc ->
                ProvideIosContent(colors.secondaryLabel, IosTheme.typography.footnote) { desc() }
            }
        }

        item.trailingContent?.let { trailing ->
            Spacer(Modifier.width(8.dp))
            ProvideIosContent(colors.secondaryLabel, IosTheme.typography.body) { trailing() }
        }
    }
}

/**
 * Pushes a colour and text style into both the iOS locals and the Material ones, so a
 * row's slot content lands the same whether it was written with [com.convx.music.ui.ios.component.IosText]
 * or a still-Material `Text`.
 */
@Composable
private fun ProvideIosContent(
    color: Color,
    style: androidx.compose.ui.text.TextStyle,
    content: @Composable () -> Unit,
) {
    androidx.compose.runtime.CompositionLocalProvider(
        LocalIosContentColor provides color,
        LocalIosTextStyle provides style,
        com.convx.music.ui.ios.compat.LocalContentColor provides color,
    ) {
        ProvideTextStyle(style, content)
    }
}

/** One row of an [IosMenuGroup]. */
data class IosMenuItemData(
    val icon: (@Composable () -> Unit)? = null,
    val title: @Composable () -> Unit,
    val description: (@Composable () -> Unit)? = null,
    val onClick: (() -> Unit)? = null,
    /** Label + icon colour. Unspecified reads as the normal label colour. */
    val tint: Color = Color.Unspecified,
    val trailingContent: (@Composable () -> Unit)? = null,
)
