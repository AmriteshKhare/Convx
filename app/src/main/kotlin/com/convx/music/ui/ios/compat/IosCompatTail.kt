/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

@file:Suppress("FunctionName", "UNUSED_PARAMETER")
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.convx.music.ui.ios.compat

import androidx.compose.foundation.Indication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.convx.music.ui.component.shapes.ContinuousCapsule
import com.convx.music.ui.component.shapes.ContinuousRoundedRectangle
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableIntStateOf
import zone.ien.hig.CupertinoBottomSheetContent
import zone.ien.hig.CupertinoScaffold
import zone.ien.hig.CupertinoSegmentedControl
import zone.ien.hig.CupertinoSegmentedControlTab
import com.convx.music.ui.component.PresentingSheetEffect
import com.convx.music.ui.ios.foundation.IosTheme
import com.convx.music.ui.ios.foundation.LocalIosContentColor
import com.convx.music.ui.ios.foundation.LocalIosTextStyle

/**
 * The Scaffold call shape. The layout is kept — slots, insets and nested scroll are not
 * Material-specific — and only its colours move to the iOS palette.
 */
@Composable
fun Scaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    floatingActionButtonPosition: androidx.compose.material3.FabPosition =
        androidx.compose.material3.FabPosition.End,
    containerColor: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
    contentWindowInsets: WindowInsets = WindowInsets(0, 0, 0, 0),
    content: @Composable (PaddingValues) -> Unit,
) {
    val colors = IosTheme.colors
    CupertinoScaffold(
        modifier = modifier,
        topBar = topBar,
        bottomBar = bottomBar,
        snackbarHost = snackbarHost,
        floatingActionButton = floatingActionButton,
        // Cupertino places the fab itself and takes no position; iOS has no fab to place,
        // and the one caller that asks for Center gets End, which is where the app's
        // floating controls already sit.
        containerColor = if (containerColor.isSpecified) containerColor else colors.systemGroupedBackground,
        contentColor = if (contentColor.isSpecified) contentColor else colors.label,
        contentWindowInsets = contentWindowInsets,
        content = content,
    )
}

/**
 * iOS has no ripple. A press dims or highlights the thing pressed; it never spreads a
 * circle out from the finger, so this resolves to no indication at all.
 */
@Suppress("UnusedReceiverParameter")
fun ripple(
    bounded: Boolean = true,
    radius: Dp = Dp.Unspecified,
    color: Color = Color.Unspecified,
): Indication? = null

/** The iOS sheet chrome over Material's sheet mechanism. */
@Composable
fun ModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: androidx.compose.material3.SheetState =
        androidx.compose.material3.rememberModalBottomSheetState(),
    sheetMaxWidth: Dp = androidx.compose.material3.BottomSheetDefaults.SheetMaxWidth,
    shape: Shape? = null,
    containerColor: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
    scrimColor: Color = Color.Unspecified,
    tonalElevation: Dp = 0.dp,
    dragHandle: (@Composable () -> Unit)? = { com.convx.music.ui.component.IosGrabber() },
    contentWindowInsets: @Composable () -> WindowInsets = { WindowInsets(0, 0, 0, 0) },
    properties: androidx.compose.material3.ModalBottomSheetProperties =
        androidx.compose.material3.ModalBottomSheetDefaults.properties,
    content: @Composable ColumnScope.() -> Unit,
) {
    // The screen behind pushes back for as long as this sheet is composed. Registered
    // here so it holds for every sheet in the app rather than the two that happen to own
    // a MenuState.
    PresentingSheetEffect()

    val colors = IosTheme.colors
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        sheetMaxWidth = sheetMaxWidth,
        shape = shape ?: ContinuousRoundedRectangle(
            topStart = 28.dp,
            topEnd = 28.dp,
        ),
        // systemGroupedBackground is pure black in dark mode (that's correct for the
        // base screen, which sits on the OLED background) — a floating sheet is an
        // elevated surface, so it wants the *secondary* grouped background instead
        // (Apple's #1C1C1E), or it reads as flat AMOLED black.
        containerColor = if (containerColor.isSpecified) containerColor else colors.secondarySystemGroupedBackground,
        contentColor = if (contentColor.isSpecified) contentColor else colors.label,
        // The presenting screen shrinks behind the sheet, so a heavy scrim on top of
        // that just muddies it.
        scrimColor = if (scrimColor.isSpecified) scrimColor else Color.Black.copy(alpha = 0.12f),
        dragHandle = dragHandle,
        contentWindowInsets = contentWindowInsets,
        properties = properties,
    ) {
        // The sheet's *body* is cupertino's; its presentation is not. Cupertino ships no
        // modal presenter — only CupertinoBottomSheetScaffold, which owns the whole screen
        // and would mean restructuring all seven callers around it. This is the seam where
        // that stops: everything inside the sheet is cupertino, the thing that animates it
        // up over the screen is still M3's.
        CupertinoBottomSheetContent(
            containerColor = if (containerColor.isSpecified) containerColor else colors.secondarySystemGroupedBackground,
            contentColor = if (contentColor.isSpecified) contentColor else colors.label,
        ) {
            Column { content() }
        }
    }
}

/** An unstyled dialog container, kept unstyled — the iOS card is the caller's job. */
@Composable
fun BasicAlertDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit,
) = Dialog(onDismissRequest = onDismissRequest, properties = properties) {
    Box(modifier) { content() }
}

/** iOS shows a small dark capsule, not a Material tooltip plate. */
@Composable
fun PlainTooltip(
    modifier: Modifier = Modifier,
    caretSize: androidx.compose.ui.geometry.Size? = null,
    shape: Shape? = null,
    contentColor: Color = Color.Unspecified,
    containerColor: Color = Color.Unspecified,
    content: @Composable () -> Unit,
) {
    val colors = IosTheme.colors
    Box(
        modifier
            .clip(shape ?: ContinuousRoundedRectangle(10.dp))
            .background(
                if (containerColor.isSpecified) containerColor else colors.label.copy(alpha = 0.92f)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        CompositionLocalProvider(
            LocalContentColor provides
                (if (contentColor.isSpecified) contentColor else colors.systemBackground),
            LocalIosTextStyle provides IosTheme.typography.footnote,
        ) { ProvideTextStyle(IosTheme.typography.footnote, content) }
    }
}

// --------------------------------------------------------------------------------
// Segmented control
// --------------------------------------------------------------------------------

/**
 * The iOS segmented control: one recessed capsule track holding equal segments, the
 * selected one lifted out as a lighter capsule. Material draws outlined pills with a
 * check mark on the selected one; iOS draws neither.
 */
/**
 * Cupertino's segmented control, driven from M3's call shape.
 *
 * Cupertino wants the selected index up front to place its sliding indicator; M3's segments
 * each carry their own `selected` flag instead. The row therefore hands its children a slot
 * to report into: each [SegmentedButton] takes the next index as it composes and, if it is
 * the selected one, writes that index back. The write happens in a [SideEffect] because
 * writing state during composition is what a recomposition loop is made of — so the
 * indicator settles one frame after a selection change rather than in the same frame.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun SingleChoiceSegmentedButtonRow(
    modifier: Modifier = Modifier,
    space: Dp = 0.dp,
    content: @Composable RowScope.() -> Unit,
) {
    val selectedIndex = remember { mutableIntStateOf(0) }
    val cursor = remember { SegmentCursor() }
    cursor.next = 0

    CupertinoSegmentedControl(
        selectedTabIndex = selectedIndex.intValue,
        modifier = modifier,
    ) {
        CompositionLocalProvider(
            LocalSegmentCursor provides cursor,
            LocalSelectedSegment provides selectedIndex,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(space),
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }
    }
}

/** Hands each segment its position in the row as the row composes. */
private class SegmentCursor {
    var next: Int = 0
}

private val LocalSegmentCursor = compositionLocalOf<SegmentCursor?> { null }
private val LocalSelectedSegment = compositionLocalOf<MutableIntState?> { null }

@Composable
fun RowScope.SegmentedButton(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape? = null,
    icon: (@Composable () -> Unit)? = null,
    label: (@Composable () -> Unit)? = null,
    content: @Composable RowScope.() -> Unit = {},
) {
    val colors = IosTheme.colors
    val cursor = LocalSegmentCursor.current
    val selectedSlot = LocalSelectedSegment.current
    val index = remember(cursor) { cursor?.next ?: 0 }
    if (cursor != null) cursor.next = index + 1
    if (selected && selectedSlot != null) {
        SideEffect { selectedSlot.intValue = index }
    }

    CupertinoSegmentedControlTab(
        onClick = onClick,
        isSelected = selected,
        modifier = modifier.weight(1f),
    ) {
        Row(
            modifier = Modifier.defaultMinSize(minHeight = 30.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompositionLocalProvider(
                LocalIosContentColor provides colors.label,
                LocalContentColor provides colors.label,
                LocalIosTextStyle provides IosTheme.typography.subheadline,
            ) {
                ProvideTextStyle(IosTheme.typography.subheadline) {
                    icon?.invoke()
                    label?.invoke()
                    content()
                }
            }
        }
    }
}

/** Every segment of an iOS control is the same capsule, so the index does not matter. */
object SegmentedButtonDefaults {
    fun itemShape(index: Int, count: Int, baseShape: Shape? = null): Shape = ContinuousCapsule()
}

// --------------------------------------------------------------------------------
// Odds and ends
// --------------------------------------------------------------------------------

/** iOS has no FAB; the nearest thing is a floating capsule button on the tint. */
@Composable
fun ExtendedFloatingActionButton(
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    text: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean = true,
    containerColor: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
) {
    val fill = if (containerColor.isSpecified) containerColor else IosTheme.tint
    val content = if (contentColor.isSpecified) contentColor else IosTheme.colors.onAccent

    Row(
        modifier = modifier
            .clip(ContinuousCapsule())
            .background(fill)
            .clickable(onClick = onClick)
            .defaultMinSize(minHeight = 50.dp)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CompositionLocalProvider(
            LocalIosContentColor provides content,
            LocalContentColor provides content,
            LocalIosTextStyle provides IosTheme.typography.headline,
        ) {
            icon()
            if (expanded) ProvideTextStyle(IosTheme.typography.headline, text)
        }
    }
}

/** A circular icon toggle: filled with the tint when on, a plain fill when off. */
@Composable
fun FilledIconToggleButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape? = null,
    colors: androidx.compose.material3.IconToggleButtonColors? = null,
    content: @Composable () -> Unit,
) {
    val iosColors = IosTheme.colors
    val fill = if (checked) IosTheme.tint else iosColors.secondarySystemFill
    val tint = if (checked) iosColors.onAccent else IosTheme.tint

    Box(
        modifier = modifier
            .size(44.dp)
            .clip(shape ?: ContinuousCapsule())
            .background(fill)
            .clickable(enabled = enabled) { onCheckedChange(!checked) },
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(
            LocalIosContentColor provides tint,
            LocalContentColor provides tint,
        ) { content() }
    }
}

@Composable
fun LoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
) = CircularProgressIndicator(modifier = modifier, color = color)

@Composable
fun LinearWavyProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    trackColor: Color = Color.Unspecified,
) = LinearProgressIndicator(modifier = modifier, color = color, trackColor = trackColor)

@Composable
fun LinearWavyProgressIndicator(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    trackColor: Color = Color.Unspecified,
    stroke: androidx.compose.ui.graphics.drawscope.Stroke? = null,
    trackStroke: androidx.compose.ui.graphics.drawscope.Stroke? = null,
    gapSize: Dp = 0.dp,
    stopSize: Dp = 0.dp,
    amplitude: ((Float) -> Float)? = null,
    wavelength: Dp = 0.dp,
    waveSpeed: Dp = 0.dp,
) {
    val tint = if (color.isSpecified) color else IosTheme.tint
    val track = if (trackColor.isSpecified) trackColor else IosTheme.colors.systemGray5
    Box(
        modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(ContinuousCapsule())
            .background(track)
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress().coerceIn(0f, 1f))
                .height(4.dp)
                .clip(ContinuousCapsule())
                .background(tint)
        )
    }
}
