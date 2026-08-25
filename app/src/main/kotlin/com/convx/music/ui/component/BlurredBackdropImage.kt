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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.convx.music.ui.utils.stackBlur
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Bounded session cache of baked blurred backdrops, keyed by
 * "source|decodeSize|radiusPx". One entry per track per background style; a
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
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    // Screen-space blur radius projected onto the tiny source: the image is
    // stretched from decodeSize px to the screen width, so blur radii shrink
    // by the same factor.
    val sourceRadius = remember(screenBlurRadiusDp, decodeSize, configuration, density) {
        val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
        val upscale = (screenWidthPx / decodeSize).coerceAtLeast(1f)
        with(density) { (screenBlurRadiusDp.dp.toPx() / upscale).roundToInt() }
    }
    val cacheKey = "$model|$decodeSize|$sourceRadius"
    var baked by remember(cacheKey) { mutableStateOf(blurredBackdropCache.get(cacheKey)) }

    LaunchedEffect(cacheKey) {
        if (baked != null) return@LaunchedEffect
        val source = model ?: return@LaunchedEffect
        val request = ImageRequest.Builder(context)
            .data(source)
            .size(decodeSize)
            .allowHardware(false) // pixel readback for the bake
            .build()
        val bmp = ImageLoader(context).execute(request).image?.toBitmap() ?: return@LaunchedEffect
        // LaunchedEffect runs on AndroidUiDispatcher, i.e. the MAIN thread inside a frame
        // callback -- a pixel copy plus three blur passes there is a dropped frame every
        // time a new backdrop is baked. The bake is pure CPU over a private bitmap, so it
        // belongs on Default; only the state write below has to come back.
        val blurred = withContext(Dispatchers.Default) {
            bmp.copy(Bitmap.Config.ARGB_8888, true).stackBlur(sourceRadius)
        }
        blurredBackdropCache.put(cacheKey, blurred)
        baked = blurred
    }

    baked?.let { bitmap ->
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = contentDescription,
            contentScale = contentScale,
            alignment = alignment,
            colorFilter = colorFilter,
            modifier = modifier,
        )
    }
}
