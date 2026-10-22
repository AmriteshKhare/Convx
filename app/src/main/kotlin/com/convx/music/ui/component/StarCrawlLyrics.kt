package com.convx.music.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.convx.music.lyrics.LyricsEntry
import com.convx.music.lyrics.primaryLyricLineIndex
import kotlin.random.Random

private val CrawlGold = Color(0xFFFFE81F)
private const val TILT_DEGREES = 52f
private const val CAMERA_DISTANCE_DP = 14f
private const val STAR_COUNT = 90

/** Lyrics laid on a tilted plane that recedes into a starfield, like an opening crawl. */
@Composable
fun StarCrawlLyrics(
    lines: List<LyricsEntry>,
    fontFamily: FontFamily,
    fontSize: Float,
    positionMs: () -> Long,
    isSynced: Boolean,
    autoScrollEnabled: Boolean,
    onSeek: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val listState = rememberLazyListState()
    val current = remember(lines, isSynced) { mutableIntStateOf(-1) }
    var height by remember { mutableIntStateOf(0) }

    LaunchedEffect(lines, isSynced) {
        if (!isSynced) return@LaunchedEffect
        while (true) {
            withFrameNanos { }
            current.intValue = primaryLyricLineIndex(lines, positionMs())
        }
    }
    LaunchedEffect(current.intValue, autoScrollEnabled, isSynced, height) {
        if (autoScrollEnabled && isSynced && current.intValue >= 0) listState.followLyricLine(current.intValue)
    }

    Box(
        modifier
            .background(Color.Black)
            .drawWithContent {
                drawContent()
                drawRect(
                    Brush.verticalGradient(
                        0f to Color.Black,
                        0.45f to Color.Transparent,
                        startY = 0f,
                        endY = size.height,
                    ),
                )
            }
            .onSizeChanged { height = it.height },
    ) {
        StarField(Modifier.fillMaxSize())
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(vertical = with(density) { (height / 2f).toDp() }),
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationX = TILT_DEGREES
                    cameraDistance = CAMERA_DISTANCE_DP * this.density
                    transformOrigin = TransformOrigin(0.5f, 0.75f)
                },
        ) {
            itemsIndexed(lines, key = { index, line -> "$index-${line.time}" }) { index, line ->
                val alpha by animateFloatAsState(
                    targetValue = when {
                        !isSynced || index == current.intValue -> 1f
                        index < current.intValue -> 0.35f
                        else -> 0.6f
                    },
                    animationSpec = tween(400),
                    label = "crawl_line_alpha",
                )
                BasicText(
                    text = line.text,
                    style = TextStyle(
                        fontFamily = fontFamily,
                        fontSize = (fontSize * 1.15f).sp,
                        lineHeight = (fontSize * 1.5f).sp,
                        fontWeight = FontWeight.Bold,
                        color = CrawlGold,
                        textAlign = TextAlign.Center,
                        shadow = Shadow(CrawlGold.copy(alpha = 0.6f), blurRadius = if (index == current.intValue) 24f else 0f),
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { this.alpha = alpha }
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                            onSeek(line.time.toInt())
                        }
                        .padding(horizontal = 36.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun StarField(modifier: Modifier) {
    val stars = remember {
        val random = Random(7)
        List(STAR_COUNT) { Triple(random.nextFloat(), random.nextFloat(), 0.4f + random.nextFloat() * 1.4f) }
    }
    Canvas(modifier) {
        stars.forEach { (x, y, radius) ->
            drawCircle(Color.White.copy(alpha = 0.35f + radius / 3f), radius, Offset(x * size.width, y * size.height))
        }
    }
}
