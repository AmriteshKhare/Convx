/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

@file:Suppress("FunctionName")

package com.convx.music.ui.ios.compat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.convx.music.ui.ios.foundation.IosMetrics
import androidx.compose.runtime.compositionLocalOf
import zone.ien.hig.CupertinoDropdownMenu
import zone.ien.hig.CupertinoMenuScope
import zone.ien.hig.MenuAction
import com.convx.music.ui.ios.foundation.IosTheme
import com.convx.music.ui.ios.foundation.iosRowPress
import com.convx.music.ui.ios.foundation.LocalIosContentColor
import com.convx.music.ui.ios.foundation.LocalIosTextStyle

/**
 * The iOS context menu, behind M3's `DropdownMenu` call shape.
 *
 * Only the chrome changes: iOS draws one panel with tight 14pt *continuous* corners and
 * a soft drop shadow, never Material's tonal-elevated surface with circular corners.
 * The anchoring, scroll and dismiss behaviour are Material's and are already right.
 */
/**
 * Cupertino's menu popup, presented behind M3's call shape.
 *
 * Cupertino hands its rows a [CupertinoMenuScope] receiver, while callers here write a
 * plain `ColumnScope` block of [DropdownMenuItem]s. Rather than rewrite the seven call
 * sites, the scope travels down as a composition local and [DropdownMenuItem] picks it up
 * — the receiver is a marker interface with no members, so nothing is lost by carrying it
 * this way.
 *
 * [offset] is accepted and ignored: cupertino anchors its menu itself.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun DropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
    properties: PopupProperties = PopupProperties(focusable = true),
    content: @Composable ColumnScope.() -> Unit,
) = CupertinoDropdownMenu(
    expanded = expanded,
    onDismissRequest = onDismissRequest,
    modifier = modifier,
    containerColor = IosTheme.colors.secondarySystemGroupedBackground,
    properties = properties,
    backdrop = zone.ien.hig.utils.rememberDefaultBackdrop(),
) {
    val menuScope = this
    CompositionLocalProvider(LocalCupertinoMenuScope provides menuScope) {
        Column { content() }
    }
}

/**
 * The enclosing [CupertinoDropdownMenu]'s row scope. Null when a [DropdownMenuItem] is
 * used outside a menu — a few call sites reuse it as a plain list row — in which case the
 * row falls back to drawing itself.
 */
private val LocalCupertinoMenuScope = compositionLocalOf<CupertinoMenuScope?> { null }

/**
 * One row of an iOS context menu: label left, glyph right, hairline above every row but
 * the first. iOS separates menu rows with a line; Material separates them with nothing.
 */
@Composable
fun DropdownMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
) {
    val colors = IosTheme.colors
    val contentColor = if (enabled) colors.label else colors.tertiaryLabel
    val menuScope = LocalCupertinoMenuScope.current

    if (menuScope != null) {
        // iOS puts a menu row's glyph on the trailing edge, so cupertino has one icon slot
        // and it sits there. A caller's leadingIcon lands in it; a trailingIcon already
        // wanted to be there.
        with(menuScope) {
            MenuAction(
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
                leadingIcon = leadingIcon,
                trailingIcon = trailingIcon,
                title = { ProvideTextStyle(IosTheme.typography.body) { text() } },
            )
        }
        return
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .iosRowPress(enabled = enabled, onClick = onClick)
            .defaultMinSize(minHeight = IosMetrics.ListRowHeight)
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(
            LocalIosContentColor provides contentColor,
            LocalIosTextStyle provides IosTheme.typography.body,
            LocalContentColor provides contentColor,
        ) {
            leadingIcon?.let {
                it()
                Spacer(Modifier.width(12.dp))
            }
            Box(Modifier.weight(1f)) {
                ProvideTextStyle(IosTheme.typography.body) { text() }
            }
            trailingIcon?.let {
                Spacer(Modifier.width(12.dp))
                it()
            }
        }
    }
}

/** SwiftUI's `Divider()` inside a menu — a full-bleed hairline. */
@Composable
fun IosMenuDivider() = Box(
    Modifier
        .fillMaxWidth()
        .height(IosMetrics.SeparatorThickness)
        .background(IosTheme.colors.separator)
)

/**
 * M3's ListItem call shape, drawn as an iOS list row: 44pt minimum, body/footnote type,
 * secondary-label supporting text, and no tonal elevation.
 */
@Composable
fun ListItem(
    headlineContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    overlineContent: (@Composable () -> Unit)? = null,
    supportingContent: (@Composable () -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    colors: androidx.compose.material3.ListItemColors? = null,
    tonalElevation: androidx.compose.ui.unit.Dp = 0.dp,
    shadowElevation: androidx.compose.ui.unit.Dp = 0.dp,
) {
    val colors = IosTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = IosMetrics.ListRowHeight)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leadingContent?.let {
            CompositionLocalProvider(
                LocalIosContentColor provides colors.label,
                LocalContentColor provides colors.label,
            ) { it() }
            Spacer(Modifier.width(12.dp))
        }

        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            overlineContent?.let {
                CompositionLocalProvider(
                    LocalIosContentColor provides colors.secondaryLabel,
                    LocalIosTextStyle provides IosTheme.typography.caption,
                    LocalContentColor provides colors.secondaryLabel,
                ) { ProvideTextStyle(IosTheme.typography.caption, it) }
            }
            CompositionLocalProvider(
                LocalIosContentColor provides colors.label,
                LocalIosTextStyle provides IosTheme.typography.body,
                LocalContentColor provides colors.label,
            ) { ProvideTextStyle(IosTheme.typography.body, headlineContent) }
            supportingContent?.let {
                CompositionLocalProvider(
                    LocalIosContentColor provides colors.secondaryLabel,
                    LocalIosTextStyle provides IosTheme.typography.footnote,
                    LocalContentColor provides colors.secondaryLabel,
                ) { ProvideTextStyle(IosTheme.typography.footnote, it) }
            }
        }

        trailingContent?.let {
            Spacer(Modifier.width(8.dp))
            CompositionLocalProvider(
                LocalIosContentColor provides colors.secondaryLabel,
                LocalContentColor provides colors.secondaryLabel,
            ) { it() }
        }
    }
}
