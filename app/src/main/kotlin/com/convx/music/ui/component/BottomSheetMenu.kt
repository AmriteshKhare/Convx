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
 * Outer radius for every menu sheet — continuous (superellipse) corners are the iOS
 * sheet silhouette; Material's smaller circular-arc default is why sheets didn't match.
 */
val MenuSheetCornerRadius = 28.dp

/** Horizontal inset the sheet's content sits at, and the basis for [MenuSheetItemRadius]. */
val MenuSheetContentInset = 16.dp

/** Card/row radius inside a sheet, kept concentric with the sheet's own curve. */
val MenuSheetItemRadius: Dp = concentricRadius(MenuSheetCornerRadius, MenuSheetContentInset)

/** Cupertino's action-sheet timing (from its source): 400ms in on this cubic-bezier, 150ms of [EaseIn] out. */
private val CupertinoSheetEasing = CubicBezierEasing(0.2833f, 0.99f, 0.31833f, 0.99f)
private const val CupertinoSheetEnterMillis = 400
private const val CupertinoSheetExitMillis = 150

/** Range the rubber-band resist eases across — pulling past this barely moves it further. */
private val MenuDragResistRange = 320.dp

@Composable
fun BottomSheetMenu(
    modifier: Modifier = Modifier,
    state: MenuState,
    // Sheet is the grouped background — darker/greyer than the cards laid on it.
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

    // hide() plays the real slide-down settle; isVisible only flips once it finishes,
    // so the sheet stays composed until then.
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
                // hig provides its own LocalContentColor; provide the compat one this app's screens read too.
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
 * iOS sheet grabber: a 36x5 capsule under the sheet's top edge. [dragOffsetPx] is the
 * caller's drag-to-dismiss offset — the grabber stretches wider/thinner as the sheet is
 * pulled, same tanh-damped feel as [GlassCircleButton]. Not used by [BottomSheetMenu]'s
 * hig-based sheet, which draws its own grabber.
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
