/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.ui.ios.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.convx.music.ui.component.shapes.ContinuousRoundedRectangle
import com.convx.music.ui.ios.foundation.IosGlassSpec
import com.convx.music.ui.ios.foundation.IosGlassStyle
import com.convx.music.ui.ios.foundation.IosMetrics
import com.convx.music.ui.ios.foundation.IosTheme
import com.convx.music.ui.ios.foundation.LocalIosContentColor
import com.convx.music.ui.ios.foundation.LocalIosTextStyle
import com.convx.music.ui.ios.foundation.iosGlass

/** One entry of an [IosMenu]. */
@Immutable
data class IosMenuItem(
    val title: String,
    /** The second, dimmer line iOS 26 menus can carry under the title. */
    val description: String? = null,
    val icon: (@Composable (Color) -> Unit)? = null,
    val role: IosButtonRole? = null,
    /** Draws a trailing checkmark, the way a Picker inside a Menu marks its selection. */
    val selected: Boolean = false,
    val enabled: Boolean = true,
    val onClick: () -> Unit = {},
)

/** A group of items with a divider above it, and an optional small header. */
@Immutable
data class IosMenuSection(
    val items: List<IosMenuItem>,
    val header: String? = null,
)

/**
 * The iOS 26 menu panel: a glass card of sections, divided by hairlines.
 *
 * [IosMenu] is the panel itself. Anchor it with [IosMenuAnchor], or place it in your
 * own Popup when you need to control the position.
 */
@Composable
fun IosMenu(
    sections: List<IosMenuSection>,
    modifier: Modifier = Modifier,
    onDismissRequest: () -> Unit = {},
) {
    val colors = IosTheme.colors
    Column(
        modifier = modifier
            .widthIn(min = 250.dp, max = 320.dp)
            .iosGlass(
                shape = ContinuousRoundedRectangle(IosMetrics.MenuCornerRadius + 12.dp),
                style = IosGlassStyle.Regular,
                spec = IosGlassSpec.Chrome,
            )
            .padding(vertical = 6.dp),
    ) {
        sections.forEachIndexed { index, section ->
            if (index > 0) {
                Box(
                    Modifier
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .fillMaxWidth()
                        .height(IosMetrics.SeparatorThickness)
                        .background(colors.separator)
                )
            }

            if (section.header != null) {
                Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 2.dp)) {
                    IosText(
                        text = section.header,
                        color = colors.secondaryLabel,
                        style = IosTheme.typography.footnote,
                    )
                }
            }

            // Items line their labels up in one column when any of them carries an icon.
            val hasIcon = section.items.any { it.icon != null }
            section.items.forEach { item ->
                IosMenuRow(item, hasIcon, onDismissRequest)
            }
        }
    }
}

@Composable
private fun IosMenuRow(item: IosMenuItem, reserveIconColumn: Boolean, onDismissRequest: () -> Unit) {
    val colors = IosTheme.colors
    val contentColor = when {
        !item.enabled -> colors.tertiaryLabel
        item.role == IosButtonRole.Destructive -> colors.systemRed
        else -> colors.label
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = item.enabled) {
                item.onClick()
                onDismissRequest()
            }
            .defaultMinSize(minHeight = 44.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (reserveIconColumn) {
            Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                item.icon?.invoke(contentColor)
            }
        }

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            IosText(item.title, color = contentColor)
            if (item.description != null) {
                IosText(
                    text = item.description,
                    color = colors.secondaryLabel,
                    style = IosTheme.typography.subheadline,
                )
            }
        }

        if (item.selected) {
            IosCheckmark(color = IosTheme.tint)
        }
    }
}

/**
 * Anchors a menu to [content], opening it below on tap — SwiftUI's Menu, and with
 * [longPress] set, its contextMenu.
 */
@Composable
fun IosMenuAnchor(
    sections: List<IosMenuSection>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier) {
        Box(Modifier.clickable { onExpandedChange(true) }) { content() }

        if (expanded) {
            Popup(
                onDismissRequest = { onExpandedChange(false) },
                properties = PopupProperties(focusable = true),
            ) {
                CompositionLocalProvider(
                    LocalIosTextStyle provides IosTheme.typography.body,
                    LocalIosContentColor provides IosTheme.colors.label,
                ) {
                    IosMenu(sections, onDismissRequest = { onExpandedChange(false) })
                }
            }
        }
    }
}

/** The checkmark iOS draws next to a selected menu row. */
@Composable
fun IosCheckmark(modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    val stroke = if (color == Color.Unspecified) IosTheme.tint else color
    androidx.compose.foundation.Canvas(modifier.size(width = 16.dp, height = 12.dp)) {
        drawPath(
            path = androidx.compose.ui.graphics.Path().apply {
                moveTo(0f, size.height * 0.55f)
                lineTo(size.width * 0.35f, size.height)
                lineTo(size.width, 0f)
            },
            color = stroke,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = 2.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round,
            ),
        )
    }
}
