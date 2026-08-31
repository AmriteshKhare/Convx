/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.ui.ios.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.convx.music.ui.ios.foundation.IosTheme
import com.convx.music.ui.ios.foundation.LocalIosContentColor
import kotlin.math.abs
import kotlinx.coroutines.launch

/** One button revealed by swiping an [IosSwipeableRow]. */
data class IosSwipeAction(
    val label: String,
    val tint: Color,
    val icon: (@Composable () -> Unit)? = null,
    val onClick: () -> Unit,
)

/** How wide each revealed action button is. */
private val ActionWidth = 76.dp

/**
 * SwiftUI's `swipeActions`: the row slides aside to reveal coloured buttons underneath.
 *
 * Two behaviours make this read as iOS rather than as a generic swipe-to-dismiss:
 * the row settles at exactly the width of the revealed buttons (it does not fly off),
 * and a swipe past [fullSwipeFraction] of the row commits the *first* trailing action
 * outright — which is how Mail deletes a message without you lifting a finger.
 *
 * @param onFullSwipe runs when the row is swiped all the way. Defaults to the first
 *   trailing action, and passing null disables the full-swipe shortcut.
 */
@Composable
fun IosSwipeableRow(
    modifier: Modifier = Modifier,
    leadingActions: List<IosSwipeAction> = emptyList(),
    trailingActions: List<IosSwipeAction> = emptyList(),
    fullSwipeFraction: Float = 0.55f,
    onFullSwipe: (() -> Unit)? = trailingActions.firstOrNull()?.onClick,
    content: @Composable () -> Unit,
) {
    if (leadingActions.isEmpty() && trailingActions.isEmpty()) {
        Box(modifier) { content() }
        return
    }

    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }

    val trailingWidthPx = with(density) { (ActionWidth * trailingActions.size).toPx() }
    val leadingWidthPx = with(density) { (ActionWidth * leadingActions.size).toPx() }
    val rowWidth = remember { androidx.compose.runtime.mutableFloatStateOf(0f) }

    fun settle(target: Float) {
        scope.launch { offset.animateTo(target, spring(dampingRatio = 1f, stiffness = 500f)) }
    }

    fun close() = settle(0f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { rowWidth.floatValue = it.width.toFloat() }
    ) {
        // The buttons live behind the row, pinned to the edge they belong to, so the
        // row sliding away is what uncovers them.
        Row(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight(),
            horizontalArrangement = Arrangement.Start,
        ) {
            leadingActions.forEach { ActionButton(it, ::close) }
        }
        Row(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight(),
            horizontalArrangement = Arrangement.End,
        ) {
            trailingActions.forEach { ActionButton(it, ::close) }
        }

        Box(
            Modifier
                .graphicsLayer { translationX = offset.value }
                .background(IosTheme.colors.secondarySystemGroupedBackground)
                .pointerInput(trailingActions.size, leadingActions.size) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val x = offset.value
                            val full = rowWidth.floatValue * fullSwipeFraction
                            when {
                                onFullSwipe != null && -x > full -> {
                                    onFullSwipe()
                                    close()
                                }
                                // Past halfway to the buttons, they stay open.
                                -x > trailingWidthPx / 2f -> settle(-trailingWidthPx)
                                x > leadingWidthPx / 2f -> settle(leadingWidthPx)
                                else -> close()
                            }
                        },
                        onDragCancel = { close() },
                    ) { change, drag ->
                        change.consume()
                        val next = (offset.value + drag)
                            .coerceIn(-rowWidth.floatValue, leadingWidthPx)
                        scope.launch { offset.snapTo(next) }
                    }
                }
        ) {
            content()
        }
    }

    // A row that has been swiped open closes itself when its actions disappear.
    LaunchedEffect(trailingActions.size, leadingActions.size) {
        if (abs(offset.value) > 0f) offset.animateTo(0f)
    }
}

@Composable
private fun ActionButton(action: IosSwipeAction, onDone: () -> Unit) {
    Box(
        modifier = Modifier
            .width(ActionWidth)
            .fillMaxHeight()
            .background(action.tint)
            .clickable {
                action.onClick()
                onDone()
            }
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            LocalIosContentColor provides Color.White
        ) {
            if (action.icon != null) action.icon.invoke()
            else IosText(
                text = action.label,
                style = IosTheme.typography.footnote,
                color = Color.White,
            )
        }
    }
}
