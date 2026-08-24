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

    val div = 2 * radius + 1
    val r = IntArray(w * h)
    val g = IntArray(w * h)
    val b = IntArray(w * h)
    val dv = IntArray(256 * div)
    for (i in dv.indices) dv[i] = i / div

    // Horizontal pass: sliding-window sums into r/g/b planes.
    var yw = 0
    var yi = 0
    for (y in 0 until h) {
        var ar = 0; var ag = 0; var ab = 0
        for (i in -radius..radius) {
            val p = pixels[yi + i.coerceIn(0, w - 1)]
            ar += (p and 0xff0000.toInt()) shr 16
            ag += (p and 0x00ff00) shr 8
            ab += p and 0x0000ff
        }
        for (x in 0 until w) {
            r[yi] = dv[ar]; g[yi] = dv[ag]; b[yi] = dv[ab]
            val p1 = pixels[yw + (x - radius).coerceIn(0, w - 1)]
            val p2 = pixels[yw + (x + radius + 1).coerceIn(0, w - 1)]
            ar += ((p2 and 0xff0000.toInt()) - (p1 and 0xff0000.toInt())) shr 16
            ag += ((p2 and 0x00ff00) - (p1 and 0x00ff00)) shr 8
            ab += (p2 and 0x0000ff) - (p1 and 0x0000ff)
            yi++; yw++
        }
    }

    // Vertical passes (x2 with the single horizontal pass approximates a
    // gaussian; band-free at these tiny sizes).
    repeat(2) {
        for (x in 0 until w) {
            var ar = 0; var ag = 0; var ab = 0
            for (i in -radius..radius) {
                val idx = i.coerceIn(0, h - 1) * w + x
                ar += r[idx]; ag += g[idx]; ab += b[idx]
            }
            var yp = x
            for (y in 0 until h) {
                pixels[yp] = pixels[yp] and 0xff000000.toInt() or
                    (dv[ar] shl 16) or (dv[ag] shl 8) or dv[ab]
                val i1 = y + radius + 1
                val i2 = y - radius
                if (i1 < h) {
                    val idx = x + i1 * w
                    ar += r[idx]; ag += g[idx]; ab += b[idx]
                }
                if (i2 >= 0) {
                    val idx = x + i2 * w
                    ar -= r[idx]; ag -= g[idx]; ab -= b[idx]
                }
                yp += w
            }
        }
    }
    setPixels(pixels, 0, w, 0, 0, w, h)
    return this
}
