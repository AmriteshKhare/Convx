package com.convx.music.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import com.convx.music.ui.ios.compat.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.sfsymbols.SfSymbols
import com.composables.sfsymbols.monochrome.SFPauseFill
import com.composables.sfsymbols.monochrome.SFPlayFill

import androidx.compose.foundation.layout.offset

@Composable
fun AnimatedPlayPauseIcon(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    size: Dp = 32.dp,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        AnimatedContent(
            targetState = isPlaying,
            transitionSpec = {
                // 0.3, not a polite 0.85: Flamingo's pop. At 0.85 the scale is small enough
                // to be indistinguishable from a plain cut, and the swap reads as a different
                // button appearing rather than this one changing its mind.
                ((fadeIn(tween(200, easing = FastOutSlowInEasing)) + scaleIn(tween(200, easing = FastOutSlowInEasing), initialScale = 0.3f)) togetherWith
                        (fadeOut(tween(160, easing = FastOutSlowInEasing)) + scaleOut(tween(160, easing = FastOutSlowInEasing), targetScale = 0.3f)))
                    .using(SizeTransform(clip = false))
            },
            contentKey = { it },
            label = "play_pause",
        ) { playing ->
            val iconSize = if (playing) size * 0.88f else size
            val opticalOffset = if (!playing) 2.dp else 0.dp

            Icon(
                imageVector = if (playing) SfSymbols.Monochrome.SFPauseFill else SfSymbols.Monochrome.SFPlayFill,
                contentDescription = null,
                tint = tint,
                modifier = Modifier
                    .size(iconSize)
                    .offset(x = opticalOffset),
            )
        }
    }
}
