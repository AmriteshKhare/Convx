/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.ui.component

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.convx.music.constants.SheetCornerRadiusKey
import com.convx.music.ui.ios.foundation.IosTheme
import com.convx.music.ui.component.shapes.ContinuousRoundedRectangle
import com.convx.music.ui.utils.concentricRadius
import com.convx.music.ui.utils.rimLight
import com.convx.music.utils.rememberPreference
import kotlinx.coroutines.launch
import kotlin.math.tanh

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

/** Matches [io.github.alexzhirkevich.cupertino.CupertinoDialogsDefaults.ScrimColor]. */
@Composable
private fun cupertinoScrimColor(): Color = Color.Black.copy(alpha = if (isSystemInDarkTheme()) 0.4f else 0.2f)

/** How far the sheet must be dragged down before release commits a dismiss. */
private val MenuDragCommitDistance = 120.dp

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
    val sheetBackground = if (background.isSpecified) background else iosColors.systemGroupedBackground
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val (sheetCornerRadius) = rememberPreference(SheetCornerRadiusKey, MenuSheetCornerRadius.value)
    val sheetShape = remember(sheetCornerRadius) { ContinuousRoundedRectangle(sheetCornerRadius.dp) }

    fun dismiss() {
        focusManager.clearFocus()
        state.isVisible = false
    }

    // Own drag offset on top of the enter/exit slide below — dragging never fights the
    // open/close animation, it just adds further translation while a finger is down.
    val dragOffsetPx = remember { Animatable(0f) }
    LaunchedEffect(state.isVisible) {
        if (state.isVisible) dragOffsetPx.snapTo(0f)
    }

    AnimatedVisibility(
        visible = state.isVisible,
        enter = fadeIn(tween(CupertinoSheetEnterMillis, easing = CupertinoSheetEasing)),
        exit = fadeOut(tween(CupertinoSheetExitMillis, easing = EaseIn)),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(cupertinoScrimColor())
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = ::dismiss,
                ),
            contentAlignment = Alignment.BottomCenter,
        ) {
            BackHandler(onBack = ::dismiss)

            AnimatedVisibility(
                visible = state.isVisible,
                // A morph-style grow rather than a plain slide: the sheet scales up from
                // the bottom edge it's anchored to (not just translating in at full size),
                // so it reads as unfolding into place. Not a true origin-tracking morph off
                // the button that opened it (that needs every one of the ~30 call sites
                // wired with a position, like PlayerContainerMorph's mini-player pill) —
                // this is the cheap version: one fixed anchor, same effect from any opener.
                enter = slideInVertically(
                    animationSpec = tween(CupertinoSheetEnterMillis, easing = CupertinoSheetEasing),
                    initialOffsetY = { it },
                ) + scaleIn(
                    animationSpec = tween(CupertinoSheetEnterMillis, easing = CupertinoSheetEasing),
                    initialScale = 0.85f,
                    transformOrigin = TransformOrigin(0.5f, 1f),
                ),
                exit = slideOutVertically(
                    animationSpec = tween(CupertinoSheetExitMillis, easing = EaseIn),
                    targetOffsetY = { it },
                ) + scaleOut(
                    animationSpec = tween(CupertinoSheetExitMillis, easing = EaseIn),
                    targetScale = 0.85f,
                    transformOrigin = TransformOrigin(0.5f, 1f),
                ),
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 12.dp)
                        .widthIn(max = 500.dp)
                        .fillMaxWidth()
                        .graphicsLayer { translationY = dragOffsetPx.value }
                        .pointerInput(Unit) {
                            val resistRangePx = MenuDragResistRange.toPx()
                            val commitPx = MenuDragCommitDistance.toPx()
                            detectVerticalDragGestures(
                                onVerticalDrag = { change, dragAmount ->
                                    change.consume()
                                    // Rubber-band: the further already pulled, the less an
                                    // additional pixel of drag moves it — never lets go slack.
                                    val resisted = if (dragAmount > 0f) {
                                        val eased = (1f - (dragOffsetPx.value / resistRangePx).coerceIn(0f, 1f))
                                            .coerceAtLeast(0.15f)
                                        dragAmount * eased
                                    } else {
                                        dragAmount
                                    }
                                    coroutineScope.launch {
                                        dragOffsetPx.snapTo((dragOffsetPx.value + resisted).coerceAtLeast(0f))
                                    }
                                },
                                onDragEnd = {
                                    if (dragOffsetPx.value > commitPx) {
                                        dismiss()
                                    } else {
                                        coroutineScope.launch {
                                            dragOffsetPx.animateTo(
                                                0f,
                                                spring(
                                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                                    stiffness = Spring.StiffnessMedium,
                                                ),
                                            )
                                        }
                                    }
                                },
                                onDragCancel = {
                                    coroutineScope.launch {
                                        dragOffsetPx.animateTo(0f, spring(stiffness = Spring.StiffnessMedium))
                                    }
                                },
                            )
                        }
                        .clip(sheetShape)
                        .background(sheetBackground, sheetShape)
                        .rimLight(shape = sheetShape)
                        // Swallows taps so a press on the menu itself does not reach the
                        // scrim's dismiss handler underneath.
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    IosGrabber(dragOffsetPx = dragOffsetPx.value)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = MenuSheetContentInset)
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
 * [dragOffsetPx] is the sheet's own drag-to-dismiss offset (see [BottomSheetMenu]'s
 * `dragOffsetPx`) — the grabber stretches into a wider, thinner capsule as the sheet
 * is pulled, same tanh-damped "elastic" feel as [GlassCircleButton]'s press/drag
 * distortion, just driven by the sheet's existing drag state instead of its own
 * gesture (the whole sheet already owns vertical drag; a second detector on the
 * grabber alone would fight it).
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
