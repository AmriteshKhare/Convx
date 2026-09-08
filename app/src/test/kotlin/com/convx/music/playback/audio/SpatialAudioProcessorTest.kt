package com.convx.music.playback.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SpatialAudioProcessorTest {

    @Test
    fun `non-stereo and non-16-bit formats bow out instead of throwing`() {
        val mono = SpatialAudioProcessor()
        mono.configure(AudioProcessor.AudioFormat(44_100, 1, C.ENCODING_PCM_16BIT))
        assertFalse(mono.isActive)

        val float = SpatialAudioProcessor()
        float.configure(AudioProcessor.AudioFormat(44_100, 2, C.ENCODING_PCM_FLOAT))
        assertFalse(float.isActive)
    }

    @Test
    fun `disabled is a byte-exact passthrough`() {
        val processor = configured(enabled = false)
        val input = frames(listOf(1000 to -1000, 500 to 250, -3000 to 900))
        processor.queueInput(input)

        val output = processor.output.order(ByteOrder.nativeOrder()).asShortBuffer()
        assertEquals(6, output.remaining())
        listOf<Short>(1000, -1000, 500, 250, -3000, 900).forEach {
            assertEquals(it, output.get())
        }
    }

    @Test
    fun `enabled widens the side signal past the input level`() {
        val processor = configured(enabled = true)
        // Pure side content (L = -R): widening amplifies it, and the delay line is
        // still zeroed on the first frames so no crossfeed muddies the comparison.
        processor.queueInput(frames(listOf(1000 to -1000)))

        val output = processor.output.order(ByteOrder.nativeOrder()).asShortBuffer()
        val left = output.get().toInt()
        val right = output.get().toInt()
        assertTrue("expected widening, got $left", left > 1000)
        assertEquals(-left, right)
    }

    private fun configured(enabled: Boolean) = SpatialAudioProcessor().apply {
        val out = configure(AudioProcessor.AudioFormat(44_100, 2, C.ENCODING_PCM_16BIT))
        assertEquals(2, out.channelCount)
        flush()
        this.enabled = enabled
    }

    private fun frames(pairs: List<Pair<Int, Int>>): ByteBuffer =
        ByteBuffer.allocateDirect(pairs.size * 4).order(ByteOrder.nativeOrder()).apply {
            pairs.forEach { (l, r) ->
                putShort(l.toShort())
                putShort(r.toShort())
            }
            flip()
        }
}
