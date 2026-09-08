/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.ui.component

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import com.convx.music.ui.ios.compat.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetDefaults
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetState
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.convx.music.ui.ios.compat.LocalContentColor
import com.convx.music.ui.ios.foundation.IosTheme
import com.convx.music.ui.utils.concentricRadius
import kotlin.math.tanh
import kotlinx.coroutines.launch
import zone.ien.hig.CupertinoModalBottomSheet
import zone.ien.hig.PresentationDetent
import zone.ien.hig.PresentationStyle
import zone.ien.hig.rememberCupertinoSheetState

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Alignment
import com.convx.music.constants.SheetCornerRadiusKey
import com.convx.music.ui.component.shapes.ContinuousRoundedRectangle
import com.convx.music.utils.rememberPreference

val LocalMenuState = compositionLocalOf { MenuState() }

@Stable
class MenuState(
    isVisible: Boolean = false,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    var isVisible by mutableStateOf(isVisible)
    var content by mutableStateOf(content)

    fun show(content: @Composable ColumnScope.() -> Unit) {
        isVisible = true
        this.content = content
    }

    fun dismiss() {
        isVisible = false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimatedBottomSheet(
    isVisible: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
    sheetMaxWidth: Dp = BottomSheetDefaults.SheetMaxWidth,
    shape: Shape = BottomSheetDefaults.ExpandedShape,
    containerColor: Color = BottomSheetDefaults.ContainerColor,
    contentColor: Color = contentColorFor(containerColor),
    tonalElevation: Dp = 0.dp,
    scrimColor: Color = BottomSheetDefaults.ScrimColor,
    dragHandle: @Composable (() -> Unit)? = { BottomSheetDefaults.DragHandle() },
    contentWindowInsets: @Composable () -> WindowInsets = { BottomSheetDefaults.windowInsets },
    properties: ModalBottomSheetProperties = ModalBottomSheetDefaults.properties,
    content: @Composable ColumnScope.() -> Unit,
) {
    var lastContent by remember { mutableStateOf(content) }

    LaunchedEffect(content) {
        if (isVisible) {
            lastContent = content
        }
    }

    LaunchedEffect(isVisible) {
        if (isVisible) {
            sheetState.show()
        } else {
            sheetState.hide()
        }
    }

    if (!sheetState.isVisible && !isVisible) {
        return
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        sheetMaxWidth = sheetMaxWidth,
        shape = shape,
        containerColor = containerColor,
        contentColor = contentColor,
        tonalElevation = tonalElevation,
        scrimColor = scrimColor,
        dragHandle = dragHandle,
        contentWindowInsets = contentWindowInsets,
        properties = properties,
        content = lastContent,
    )
}

/**
 * Outer corner radius shared by every menu sheet. Continuous (superellipse) corners at
 * this size are the iOS sheet silhouette; Material's default is both smaller and drawn
 * with circular arcs, which is most of why our sheets did not read as the reference.
 */
val MenuSheetCornerRadius = 28.dp

/** Horizontal inset the sheet's content sits at, and the basis for [MenuSheetItemRadius]. */
val MenuSheetContentInset = 16.dp

/**
 * Radius for anything drawn as a card/row inside a menu sheet, so its curve stays
 * concentric with the sheet's own. Use this instead of picking a radius per screen.
 */
val MenuSheetItemRadius: Dp = concentricRadius(MenuSheetCornerRadius, MenuSheetContentInset)

/** Cupertino's own action-sheet timing, read out of its (unexposed) source rather than
 *  guessed: 400ms in on this cubic-bezier, 150ms of [EaseIn] on the way out. Reproduced
 *  here — not called into the library — because [io.github.alexzhirkevich.cupertino.CupertinoActionSheet]
 *  exposes no modifier/shape/margin hook to float, rim-light or drag-dismiss its sheet. */
private val CupertinoSheetEasing = CubicBezierEasing(0.2833f, 0.99f, 0.31833f, 0.99f)
private const val CupertinoSheetEnterMillis = 400
private const val CupertinoSheetExitMillis = 150

/** Range the rubber-band resist eases across — pulling past this barely moves it further. */
private val MenuDragResistRange = 320.dp

@Composable
fun BottomSheetMenu(
    modifier: Modifier = Modifier,
    state: MenuState,
    // The sheet is the *grouped* background: menu content sits on it as cards, which
    // is only legible if the sheet itself is the darker/greyer of the two.
    background: Color = Color.Unspecified,
) {
    val iosColors = IosTheme.colors
    val isDark = isSystemInDarkTheme()
    val (sheetCornerRadius) = rememberPreference(SheetCornerRadiusKey, MenuSheetCornerRadius.value)
    val sheetShape = remember(sheetCornerRadius) {
        ContinuousRoundedRectangle(
            topStart = sheetCornerRadius.dp,
            topEnd = sheetCornerRadius.dp,
            bottomEnd = 0.dp,
            bottomStart = 0.dp,
        )
    }
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberCupertinoSheetState(
        presentationStyle = PresentationStyle.Modal(
            detents = setOf(PresentationDetent.Medium, PresentationDetent.Large),
        ),
    )

    // Routed through hig's own detent state machine (CupertinoSheetState) via
    // CupertinoModalBottomSheet — the real library mechanism, not a hand-rolled
    // stand-in: opens to the Medium detent, drags to Large, dismisses past Medium.
    // hide() plays the real slide-down settle; MenuState.isVisible only flips once
    // that finishes (see onDismissRequest below), so this stays composed for it.
    fun dismiss() {
        focusManager.clearFocus()
        scope.launch { sheetState.hide() }
    }

    AnimatedVisibility(
        visible = state.isVisible,
        enter = fadeIn(tween(CupertinoSheetEnterMillis, easing = CupertinoSheetEasing)),
        exit = fadeOut(tween(CupertinoSheetExitMillis, easing = EaseIn)),
        modifier = modifier,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            BackHandler(onBack = ::dismiss)

            val resolvedContainerColor = when {
                background.isSpecified -> background
                isDark -> Color(0xFF141416)
                else -> iosColors.secondarySystemGroupedBackground
            }

            CupertinoModalBottomSheet(
                onDismissRequest = { state.isVisible = false },
                sheetState = sheetState,
                shape = sheetShape,
                containerColor = resolvedContainerColor,
                contentColor = iosColors.label,
                dragHandle = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        IosGrabber()
                    }
                },
            ) {
                // CupertinoModalBottomSheet provides hig's own LocalContentColor, but
                // this app's screens are built on the ios.compat Icon/Text, which reads
                // a separate compat LocalContentColor — provide that one too, or content
                // here falls back to Compose's global-default black regardless.
                CompositionLocalProvider(LocalContentColor provides iosColors.label) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 500.dp)
                            .fillMaxWidth()
                            .padding(horizontal = MenuSheetContentInset),
                    ) {
                        state.content(this)
                    }
                }
            }
        }
    }
}

/**
 * The iOS sheet grabber: a 36x5 capsule sitting close under the sheet's own top edge,
 * not floating in a tall Material-sized handle area.
 *
 * [dragOffsetPx] is the caller's own drag-to-dismiss offset — the grabber stretches
 * into a wider, thinner capsule as the sheet is pulled, same tanh-damped "elastic"
 * feel as [GlassCircleButton]'s press/drag distortion, just driven by the sheet's
 * existing drag state instead of its own gesture (the whole sheet already owns
 * vertical drag; a second detector on the grabber alone would fight it). Used as the
 * drag handle for Material's [ModalBottomSheet]-backed sheets ([IosCompatTail]'s
 * default, [BottomSheetPage]) — [BottomSheetMenu] drags through hig's
 * [zone.ien.hig.CupertinoModalBottomSheet], which draws its own grabber.
 */
@Composable
fun IosGrabber(modifier: Modifier = Modifier, dragOffsetPx: Float = 0f) {
    Box(
        modifier = modifier
            .padding(top = 5.dp, bottom = 8.dp)
            .size(width = 36.dp, height = 5.dp)
            .graphicsLayer {
                val stretch = tanh(dragOffsetPx / MenuDragResistRange.toPx())
                scaleX = 1f + stretch * 0.7f
                scaleY = 1f - stretch * 0.3f
            }
            .clip(RoundedCornerShape(2.5.dp))
            .background(IosTheme.colors.systemGray3)
    )
}
