/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */
package com.convx.music.ui.utils

import android.graphics.Bitmap

/**
 * In-memory box blur (three passes) for small bitmaps, approximating a gaussian.
 *
 * Used to bake blurs into tiny decoded artwork sources (48-160px) once per
 * track instead of running a full-screen RenderEffect blur every frame: the
 * source is upscaled 7-20x to fill the screen, so a blur at this size is
 * visually identical to the screen-space blur it replaces while costing a few
 * milliseconds of CPU, once, on decode.
 */
fun Bitmap.stackBlur(radius: Int): Bitmap {
    if (radius < 1) return this
    val w = width
    val h = height
    if (w <= 0 || h <= 0) return this
    val pixels = IntArray(w * h)
    getPixels(pixels, 0, w, 0, 0, w, h)
    stackBlurPixels(pixels, w, h, radius)
    setPixels(pixels, 0, w, 0, 0, w, h)
    return this
}

/**
 * The blur itself, over a plain ARGB pixel array — split out from [stackBlur] so it is
 * reachable from a JVM test, where `android.graphics.Bitmap` is not.
 *
 * Blurs [pixels] in place. Alpha is preserved per pixel; only RGB is averaged.
 */
internal fun stackBlurPixels(pixels: IntArray, w: Int, h: Int, radius: Int) {
    if (radius < 1 || w <= 0 || h <= 0) return
    require(pixels.size >= w * h) { "pixels too small: ${pixels.size} < ${w * h}" }

    val div = 2 * radius + 1
    val r = IntArray(w * h)
    val g = IntArray(w * h)
    val b = IntArray(w * h)
    // Division table: dv[sum] == sum / div, so the inner loops never divide.
    val dv = IntArray(256 * div)
    for (i in dv.indices) dv[i] = i / div

    // Horizontal pass: sliding-window sums into r/g/b planes.
    //
    // `rowStart` is the row's base offset and must stay FIXED across the row -- the two
    // window reads below are `rowStart + <column clamped to this row>`. It used to be a
    // running counter incremented alongside the write cursor, which made both reads
    // `y*w + x + clamp(...)`: a diagonal walk that drifted a column further into the
    // next row on every step, and ran off the end of the array entirely on the last row
    // (ArrayIndexOutOfBoundsException: length=w*h; index=w*h+1 for a square source).
    // So this was both a crash and, on every row it did not crash, the wrong pixels.
    for (y in 0 until h) {
        val rowStart = y * w
        var ar = 0
        var ag = 0
        var ab = 0
        for (i in -radius..radius) {
            val p = pixels[rowStart + i.coerceIn(0, w - 1)]
            ar += (p and 0x00ff0000) shr 16
            ag += (p and 0x0000ff00) shr 8
            ab += p and 0x000000ff
        }
        for (x in 0 until w) {
            val out = rowStart + x
            r[out] = dv[ar]
            g[out] = dv[ag]
            b[out] = dv[ab]
            val p1 = pixels[rowStart + (x - radius).coerceIn(0, w - 1)]
            val p2 = pixels[rowStart + (x + radius + 1).coerceIn(0, w - 1)]
            ar += ((p2 and 0x00ff0000) - (p1 and 0x00ff0000)) shr 16
            ag += ((p2 and 0x0000ff00) - (p1 and 0x0000ff00)) shr 8
            ab += (p2 and 0x000000ff) - (p1 and 0x000000ff)
        }
    }

    // Vertical passes (x2 with the single horizontal pass approximates a
    // gaussian; band-free at these tiny sizes).
    repeat(2) {
        for (x in 0 until w) {
            var ar = 0
            var ag = 0
            var ab = 0
            for (i in -radius..radius) {
                val idx = i.coerceIn(0, h - 1) * w + x
                ar += r[idx]; ag += g[idx]; ab += b[idx]
            }
            var yp = x
            for (y in 0 until h) {
                pixels[yp] = pixels[yp] and 0xff000000.toInt() or
                    (dv[ar] shl 16) or (dv[ag] shl 8) or dv[ab]
                // Clamp BOTH ends of the window, and always move both -- exactly what the
                // horizontal pass does. The old form only added when the leading row was
                // in range and only subtracted when the trailing one was, so at the top
                // edge (where priming had already counted row 0 `radius + 1` times) the
                // window grew past `div` terms. The accumulator then exceeded 255 * div
                // and indexed off the end of the division table: the second half of the
                // same crash, and a brightening band along the top of every baked blur.
                val add = ((y + radius + 1).coerceIn(0, h - 1)) * w + x
                val sub = ((y - radius).coerceIn(0, h - 1)) * w + x
                ar += r[add] - r[sub]
                ag += g[add] - g[sub]
                ab += b[add] - b[sub]
                yp += w
            }
        }
    }
}
