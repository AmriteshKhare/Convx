package com.convx.music.playback.karaoke

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin

/**
 * The STFT path must be transparent when nothing is ducked: sing on, no mask, output is the input
 * delayed by exactly 2048 samples. Catches a broken window, hop, FFT sign or overlap-add FIFO.
 */
class KaraokeAudioProcessorTest {

    @Test
    fun `enabled with no mask reconstructs the input delayed by N`() {
        val p = KaraokeAudioProcessor()
        p.configure(AudioProcessor.AudioFormat(44_100, 2, C.ENCODING_PCM_16BIT))
        p.flush()
        p.enabled = true
        p.level = 0f

        val frames = 12_000
        val signal = ShortArray(frames) { (sin(it * 0.031) * 9000 + sin(it * 0.0071) * 6000).toInt().toShort() }
        val input = ByteBuffer.allocateDirect(frames * 4).order(ByteOrder.nativeOrder())
        signal.forEach { input.putShort(it); input.putShort((-it).toShort()) }
        input.flip()
        p.queueInput(input)
        val out = p.output.order(ByteOrder.nativeOrder())

        val delay = 2048
        for (i in delay until frames step 97) {
            val l = out.getShort((i) * 4).toInt()
            val r = out.getShort((i) * 4 + 2).toInt()
            assertEquals("L @$i", signal[i - delay].toDouble(), l.toDouble(), 2.0)
            assertEquals("R @$i", -signal[i - delay].toDouble(), r.toDouble(), 2.0)
        }
    }
}
