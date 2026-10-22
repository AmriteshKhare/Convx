/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.convx.music.R
import com.convx.music.lyrics.LyricsEntry
import kotlinx.coroutines.delay

private const val MIN_GAP_MS = 5_000L
private const val FALLBACK_LINE_MS = 4_000L
private const val POLL_MS = 250L
private const val BAR_COUNT = 4

/**
 * True while [positionMs] sits in a break of at least [MIN_GAP_MS] with no sung line: the intro,
 * or the time between the end of one line and the start of the next. A line's end comes from its
 * word timings when it has them, else a fixed guess, so unsynced lyrics should not call this.
 */
internal fun isInstrumentalGap(lines: List<LyricsEntry>, positionMs: Long): Boolean {
    val next = lines.firstOrNull { it.time > positionMs } ?: return false
    val previous = lines.lastOrNull { it.time <= positionMs }
    val gapStart = previous?.let { lineEnd(it, next.time) } ?: 0L
    return positionMs >= gapStart && next.time - gapStart >= MIN_GAP_MS
}

private fun lineEnd(line: LyricsEntry, nextTime: Long): Long {
    val wordsEnd = line.words?.lastOrNull()?.let { (it.endTime * 1000).toLong() }
    return minOf(wordsEnd ?: (line.time + FALLBACK_LINE_MS), nextTime)
}

/** A small equalizer that breathes while the track plays through an instrumental break. */
@Composable
fun InstrumentalNote(
    lines: List<LyricsEntry>,
    positionMs: () -> Long,
    color: Color,
    modifier: Modifier = Modifier,
) {
    var inGap by remember { mutableStateOf(false) }
    LaunchedEffect(lines) {
        while (true) {
            inGap = isInstrumentalGap(lines, positionMs())
            delay(POLL_MS)
        }
    }
    val description = stringResource(R.string.lyrics_instrumental)
    AnimatedVisibility(
        visible = inGap,
        enter = fadeIn() + scaleIn(initialScale = 0.85f),
        exit = fadeOut() + scaleOut(targetScale = 0.85f),
        modifier = modifier,
    ) {
        Row(
            Modifier
                .clip(CircleShape)
                .background(color.copy(alpha = 0.14f))
                .padding(horizontal = 18.dp, vertical = 12.dp)
                .semantics { contentDescription = description },
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val transition = rememberInfiniteTransition(label = "instrumentalBars")
            repeat(BAR_COUNT) { index ->
                val level by transition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 420 + index * 90, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "bar$index",
                )
                Box(
                    Modifier
                        .width(4.dp)
                        .height(22.dp)
                        .graphicsLayer {
                            scaleY = level
                            transformOrigin = TransformOrigin(0.5f, 0.5f)
                        }
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.85f)),
                )
            }
        }
    }
}
