package com.convx.music.ui.component

import com.convx.music.lyrics.LyricsEntry
import com.convx.music.lyrics.WordTimestamp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstrumentalNoteTest {
    private fun line(time: Long, endSeconds: Double? = null) = LyricsEntry(
        time = time,
        text = "x",
        words = endSeconds?.let { listOf(WordTimestamp("x", time / 1000.0, it)) },
    )

    @Test
    fun `a long intro counts as a gap`() {
        assertTrue(isInstrumentalGap(listOf(line(12_000)), 3_000))
    }

    @Test
    fun `a short intro does not`() {
        assertFalse(isInstrumentalGap(listOf(line(4_000)), 1_000))
    }

    @Test
    fun `the gap starts when the sung line ends, not when it starts`() {
        val lines = listOf(line(10_000, endSeconds = 14.0), line(30_000))
        assertFalse(isInstrumentalGap(lines, 12_000))
        assertTrue(isInstrumentalGap(lines, 15_000))
    }

    @Test
    fun `lines without word timings are assumed to last four seconds`() {
        val lines = listOf(line(10_000), line(30_000))
        assertFalse(isInstrumentalGap(lines, 13_000))
        assertTrue(isInstrumentalGap(lines, 15_000))
    }

    @Test
    fun `nothing after the last line is a gap`() {
        assertFalse(isInstrumentalGap(listOf(line(1_000)), 60_000))
    }

    @Test
    fun `closely spaced lines never gap`() {
        val lines = listOf(line(10_000, endSeconds = 12.0), line(14_000))
        assertFalse(isInstrumentalGap(lines, 12_500))
    }
}
