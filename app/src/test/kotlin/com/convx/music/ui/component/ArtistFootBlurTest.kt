package com.convx.music.ui.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The hero foot blur curve fitted on the iOS 27 artist page references. */
class ArtistFootBlurTest {
    @Test
    fun `blur leaves zero smoothly, hits the measured points, caps`() {
        assertEquals(0f, artistFootBlurPt(0.5f), 0f)
        assertEquals(0f, artistFootBlurPt(0.78f), 0f)
        // Just past the knee the blur is still invisible: no step for the eye to catch.
        assertTrue(artistFootBlurPt(0.80f) < 0.2f)
        assertEquals(12f, artistFootBlurPt(0.995f), 1f)
        assertEquals(48f, artistFootBlurPt(1.3f), 0f)
        var previous = 0f
        var f = 0.70f
        while (f <= 1.3f) {
            val v = artistFootBlurPt(f)
            assertTrue("blur step at $f", v - previous < 0.4f)
            previous = v
            f += 0.001f
        }
    }

    @Test
    fun `wash is continuous and smooth end to end`() {
        assertEquals(0f, artistWash(0.3f), 0f)
        assertEquals(0.9f, artistWash(1.2f), 0.001f)
        assertEquals(1f, artistWash(ArtistHeroExtent), 0f)
        // No jumps anywhere, and no slope break bigger than a fine sampling step can show.
        var previous = artistWash(0f)
        var previousSlope = 0f
        var f = 0.001f
        while (f <= ArtistHeroExtent) {
            val v = artistWash(f)
            val slope = (v - previous) / 0.001f
            assertTrue("jump at $f", kotlin.math.abs(v - previous) < 0.003f)
            assertTrue("slope break at $f", kotlin.math.abs(slope - previousSlope) < 0.2f)
            previous = v
            previousSlope = slope
            f += 0.001f
        }
    }
}
