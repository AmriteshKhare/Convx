/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.ui.component

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.convx.music.constants.SheetCornerRadiusKey
import com.convx.music.ui.component.shapes.ContinuousRoundedRectangle
import com.convx.music.ui.ios.foundation.IosTheme
import com.convx.music.utils.rememberPreference

val LocalBottomSheetPageState = compositionLocalOf { BottomSheetPageState() }

@Stable
class BottomSheetPageState(
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
fun BottomSheetPage(
    modifier: Modifier = Modifier,
    state: BottomSheetPageState,
    background: Color = Color.Unspecified,
) {
    val focusManager = LocalFocusManager.current
    val iosColors = IosTheme.colors
    val isDark = isSystemInDarkTheme()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val (sheetCornerRadius) = rememberPreference(SheetCornerRadiusKey, MenuSheetCornerRadius.value)
    val sheetShape = remember(sheetCornerRadius) {
        ContinuousRoundedRectangle(
            topStart = sheetCornerRadius.dp,
            topEnd = sheetCornerRadius.dp,
            bottomEnd = 0.dp,
            bottomStart = 0.dp,
        )
    }

    val resolvedContainerColor = when {
        background.isSpecified -> background
        isDark -> Color(0xFF141416)
        else -> iosColors.systemGroupedBackground
    }

    AnimatedBottomSheet(
        isVisible = state.isVisible,
        onDismissRequest = {
            focusManager.clearFocus()
            state.isVisible = false
        },
        sheetState = sheetState,
        shape = sheetShape,
        containerColor = resolvedContainerColor,
        contentColor = iosColors.label,
        scrimColor = Color.Black.copy(alpha = 0.12f),
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
        modifier = modifier.fillMaxHeight()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
        ) {
            state.content(this)
        }
    }
}
