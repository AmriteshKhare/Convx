/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */
package com.convx.music.ui.component

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.convx.music.ui.utils.stackBlur
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Bounded session cache of baked blurred backdrops, keyed by
 * "source|decodeSize|radiusPx|finishKey". One entry per track per background style; a
 * 128-entry ceiling covers hours of listening.
 */
private val blurredBackdropCache = android.util.LruCache<String, Bitmap>(128)

/**
 * A full-bleed blurred artwork backdrop, baked ONCE per track on a tiny decoded
 * source instead of running a screen-size RenderEffect blur every frame.
 *
 * The source decodes at [decodeSize] px square and is then upscaled to fill the
 * screen; at that magnification a blur of [screenBlurRadiusDp] computed on the
 * source is visually identical to `Modifier.blur(screenBlurRadiusDp)` on the
 * full-size image, but costs a few ms of CPU on decode rather than a multi-ms
 * RenderEffect pass on every redraw. Results are cached per track in
 * [blurredBackdropCache].
 *
 * Draws nothing until the first bake completes (callers paint an opaque base
 * behind this anyway).
 */
@Composable
fun BlurredBackdropImage(
    model: Any?,
    screenBlurRadiusDp: Float,
    modifier: Modifier = Modifier,
    decodeSize: Int = 160,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
    alignment: Alignment = Alignment.Center,
    colorFilter: ColorFilter? = null,
    /**
     * Fired once this has pixels to draw — on the bake completing, or immediately on a
     * cache hit. The AsyncImage this replaces reported readiness through `onSuccess`, and
     * callers use that to start their own fade; without an equivalent they would fade in
     * against nothing.
     */
    onReady: (() -> Unit)? = null,
    /**
     * Static finish pass applied once on the CPU inside the existing bake
     * (flip, colour matrix, vertical alpha ramp) — the CPU equivalent of an
     * Offscreen layer + DstIn gradient. Null = plain blur as before.
     */
    finish: ((Bitmap) -> Bitmap)? = null,
    finishKey: String = "",
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    // Screen-space blur radius projected onto the tiny source: the image is
    // stretched from its decoded width to the screen width, so blur radii shrink
    // by the same factor. The projection has to use the width the decode actually
    // came back at, not [decodeSize] -- Coil fits within a decodeSize box, so a
    // non-square source lands narrower than that and would come out over-blurred
    // by the ratio it was short by. Square artwork decodes to exactly decodeSize,
    // so nothing changes for the callers that pass one.
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenRadiusPx = with(density) { screenBlurRadiusDp.dp.toPx() }
    val cacheKey = "$model|$decodeSize|$screenBlurRadiusDp|$finishKey"
    var baked by remember(cacheKey) { mutableStateOf(blurredBackdropCache.get(cacheKey)) }

    LaunchedEffect(cacheKey) {
        if (baked != null) return@LaunchedEffect
        val source = model ?: return@LaunchedEffect
        val request = ImageRequest.Builder(context)
            .data(source)
            .size(decodeSize)
            .allowHardware(false) // pixel readback for the bake
            .build()
        val bmp = context.imageLoader.execute(request).image?.toBitmap() ?: return@LaunchedEffect
        val upscale = (screenWidthPx / bmp.width.coerceAtLeast(1)).coerceAtLeast(1f)
        val sourceRadius = (screenRadiusPx / upscale).roundToInt()
        // LaunchedEffect runs on AndroidUiDispatcher, i.e. the MAIN thread inside a frame
        // callback -- a pixel copy plus three blur passes there is a dropped frame every
        // time a new backdrop is baked. The bake is pure CPU over a private bitmap, so it
        // belongs on Default; only the state write below has to come back.
        val blurred = withContext(Dispatchers.Default) {
            bmp.copy(Bitmap.Config.ARGB_8888, true).stackBlur(sourceRadius)
                .let { finish?.invoke(it) ?: it }
        }
        blurredBackdropCache.put(cacheKey, blurred)
        baked = blurred
    }

    // Covers both paths in one place: the bake finishing above, and the cache hit that
    // seeded `baked` before the effect ever ran.
    val readyCallback by rememberUpdatedState(onReady)
    LaunchedEffect(baked) {
        if (baked != null) readyCallback?.invoke()
    }

    // A cache hit shows at once; a bake that lands after first composition eases in instead
    // of popping -- it usually lands mid-way through a track-change crossfade.
    val reveal = remember(cacheKey) { androidx.compose.animation.core.Animatable(if (baked != null) 1f else 0f) }
    LaunchedEffect(baked) {
        if (baked != null && reveal.value < 1f) {
            reveal.animateTo(1f, androidx.compose.animation.core.tween(600, easing = com.convx.music.ui.player.SeamlessEasing))
        }
    }

    baked?.let { bitmap ->
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = contentDescription,
            contentScale = contentScale,
            alignment = alignment,
            colorFilter = colorFilter,
            modifier = modifier.graphicsLayer { alpha = reveal.value },
        )
    }
}

/** Centre-crop dimensions of a w×h source to [aspect] (w/h), pure for tests. */
internal fun cropSize(w: Int, h: Int, aspect: Float): Pair<Int, Int> =
    if (w.toFloat() / h > aspect) (h * aspect).roundToInt() to h
    else w to (w / aspect).roundToInt()

/**
 * Centre-crops [src] to [aspect] (w/h), optionally flips it vertically, applies [matrix],
 * then multiplies alpha by a vertical ramp ([rampAlphas] at [rampStops], 0..1 of height),
 * i.e. the CPU equivalent of an Offscreen layer + DstIn gradient.
 */
internal fun bakeRamp(
    src: Bitmap,
    aspect: Float,
    flip: Boolean,
    matrix: android.graphics.ColorMatrix?,
    rampStops: FloatArray,
    rampAlphas: FloatArray,
): Bitmap {
    val (cw, ch) = cropSize(src.width, src.height, aspect)
    val out = Bitmap.createBitmap(cw.coerceAtLeast(1), ch.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(out)
    val sx = (src.width - cw) / 2
    val sy = (src.height - ch) / 2
    canvas.save()
    if (flip) canvas.scale(1f, -1f, cw / 2f, ch / 2f)
    canvas.drawBitmap(
        src,
        android.graphics.Rect(sx, sy, sx + cw, sy + ch),
        android.graphics.Rect(0, 0, cw, ch),
        android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG).apply {
            matrix?.let { colorFilter = android.graphics.ColorMatrixColorFilter(it) }
        },
    )
    canvas.restore()
    val colors = IntArray(rampAlphas.size) {
        android.graphics.Color.argb((rampAlphas[it] * 255).roundToInt(), 255, 255, 255)
    }
    canvas.drawRect(
        0f, 0f, cw.toFloat(), ch.toFloat(),
        android.graphics.Paint().apply {
            shader = android.graphics.LinearGradient(
                0f, 0f, 0f, ch.toFloat(), colors, rampStops,
                android.graphics.Shader.TileMode.CLAMP,
            )
            xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN)
        },
    )
    return out
}
