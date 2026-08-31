package com.convx.music.ui.utils

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards the artwork quality ladder. `i.ytimg.com` thumbnails are addressed by filename, so
 * the only lever is which rung is asked for; it used to be binary (maxres above 1200px,
 * hqdefault for everything else), which fed a 544px grid tile a 480x360 image whose square
 * crop leaves 360px of real detail.
 */
class ThumbnailResizeTest {

    private val ytimg = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg"

    @Test
    fun `list and grid art asks above the old hqdefault floor`() {
        // The regression this fixes: 544 used to land on hqdefault.
        assertEquals(
            "https://i.ytimg.com/vi/dQw4w9WgXcQ/sddefault.jpg",
            ytimg.resize(544, 544),
        )
    }

    @Test
    fun `player art still asks for maxres`() {
        assertEquals(
            "https://i.ytimg.com/vi/dQw4w9WgXcQ/maxresdefault.jpg",
            ytimg.resize(1200, 1200),
        )
    }

    @Test
    fun `small art does not over-fetch`() {
        assertEquals("https://i.ytimg.com/vi/dQw4w9WgXcQ/mqdefault.jpg", ytimg.resize(96, 96))
        assertEquals("https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg", ytimg.resize(256, 256))
    }

    @Test
    fun `height alone still picks a rung`() {
        // maxOf(width ?: 0, height ?: 0): a height-only request used to fall through to
        // hqdefault no matter how large it was, because only width was consulted.
        assertEquals(
            "https://i.ytimg.com/vi/dQw4w9WgXcQ/maxresdefault.jpg",
            ytimg.resize(height = 1200),
        )
    }

    @Test
    fun `no dimensions leaves the url alone`() {
        assertEquals(ytimg, ytimg.resize())
    }
}
