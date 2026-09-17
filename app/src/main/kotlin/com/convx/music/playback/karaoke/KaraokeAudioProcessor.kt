/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */
package com.convx.music.playback.karaoke

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.convx.music.playback.smart.KaraokeBands
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A whole track's vocal mask, filled in window by window as [KaraokeEngine] computes it.
 * `data[frame * KaraokeBands.COUNT + band]` is 0..255; [ready] says which frames hold a real value.
 */
class KaraokeMask(val mediaId: String, val frames: Int) {
    val data = ByteArray(frames * KaraokeBands.COUNT)
    val ready = BooleanArray(frames)

    companion object {
        /** open-unmix's STFT: 1024-sample hop at 44.1 kHz. */
        const val FRAME_RATE = 44_100.0 / 1024.0
    }
}

/**
 * Karaoke ("Sing") vocal reduction, in ExoPlayer's audio chain.
 *
 * Runs a streaming STFT on what is actually playing (2048-sample sqrt-Hann, 75% overlap, which
 * reconstructs exactly when every gain is 1), scales each bin by
 * `1 - (1 - level) * vocalMask(time, band)`, and overlap-adds back. The mask itself is not
 * computed here -- open-unmix needs a fixed 22 s window and far more time than a buffer allows --
 * it is precomputed ahead by [KaraokeEngine] and looked up by track time. Frames whose mask is
 * not ready yet pass through untouched, so the reduction fades in as the analysis catches up.
 *
 * Track time comes from [rebase], which MusicService calls on every seek and track change with
 * the player's position; the processor counts samples from there. Off ([enabled] false) it is a
 * plain copy with no latency; on, output lags input by 2048 samples (~46 ms).
 *
 * Stereo 16-bit PCM or float only, like [com.convx.music.playback.audio.SpatialAudioProcessor]:
 * anything else configures as NOT_SET so the chain routes around it.
 */
@UnstableApi
class KaraokeAudioProcessor : BaseAudioProcessor() {

    @Volatile var enabled: Boolean = false
    /** 1 = original vocals, 0 = vocals removed as far as the mask allows. */
    @Volatile var level: Float = 1f
    @Volatile var mask: KaraokeMask? = null

    @Volatile private var pendingAnchorMs = 0L
    @Volatile private var anchorGeneration = 0
    private var usedGeneration = -1
    private var anchorMs = 0L
    private var samplesSinceAnchor = 0L

    /** The track this processor's player is playing; a mask for any other track is ignored. */
    @Volatile private var mediaId: String? = null

    fun rebase(mediaId: String?, positionMs: Long) {
        this.mediaId = mediaId
        pendingAnchorMs = positionMs
        anchorGeneration++
    }

    private var sampleRate = 44_100
    private var isFloat = false

    private val input = Array(2) { FloatArray(N) }
    /** Overlap-add accumulator aligned with [input]: acc[i] is the output for input[i]. */
    private val overlap = Array(2) { FloatArray(N) }
    /** Finished output, one hop at a time; [outPos] is the next sample to emit. */
    private val outHop = Array(2) { FloatArray(HOP) }
    private var outPos = 0
    private var filled = 0
    private val re = DoubleArray(N)
    private val im = DoubleArray(N)
    private var bandOfBin = IntArray(N / 2 + 1)
    private val bandGain = FloatArray(KaraokeBands.COUNT)
    private var wasEnabled = false

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val ok = inputAudioFormat.channelCount == 2 &&
            (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT || inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT)
        if (!ok) return AudioProcessor.AudioFormat.NOT_SET
        sampleRate = inputAudioFormat.sampleRate
        isFloat = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        bandOfBin = IntArray(N / 2 + 1) { KaraokeBands.bandOf(it.toDouble() * sampleRate / N) }
        resetStft()
        return inputAudioFormat
    }

    override fun onFlush() = resetStft()

    private fun resetStft() {
        input.forEach { it.fill(0f) }
        overlap.forEach { it.fill(0f) }
        outHop.forEach { it.fill(0f) }
        // Primed: N - HOP zero samples in the window and one hop of zeros waiting to go out,
        // so every input sample pops exactly one output sample. Latency is N samples.
        filled = N - HOP
        outPos = 0
        samplesSinceAnchor = 0
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val bytesPerFrame = if (isFloat) 8 else 4
        val frameCount = inputBuffer.remaining() / bytesPerFrame
        if (frameCount == 0) return
        val out = replaceOutputBuffer(frameCount * bytesPerFrame)

        val on = enabled
        if (on != wasEnabled) { resetStft(); wasEnabled = on }
        if (anchorGeneration != usedGeneration) {
            usedGeneration = anchorGeneration
            anchorMs = pendingAnchorMs
            samplesSinceAnchor = 0
        }
        if (!on) {
            out.put(inputBuffer)
            out.flip()
            samplesSinceAnchor += frameCount
            return
        }

        inputBuffer.order(ByteOrder.nativeOrder())
        out.order(ByteOrder.nativeOrder())
        repeat(frameCount) {
            val l: Float
            val r: Float
            if (isFloat) { l = inputBuffer.float; r = inputBuffer.float }
            else { l = inputBuffer.short / 32768f; r = inputBuffer.short / 32768f }
            val ol = outHop[0][outPos]
            val or = outHop[1][outPos]
            outPos++
            input[0][filled] = l
            input[1][filled] = r
            filled++
            if (filled == N) processFrame()
            if (isFloat) { out.putFloat(ol); out.putFloat(or) }
            else {
                out.putShort((ol * 32767f).coerceIn(-32768f, 32767f).toInt().toShort())
                out.putShort((or * 32767f).coerceIn(-32768f, 32767f).toInt().toShort())
            }
            samplesSinceAnchor++
        }
        out.flip()
    }

    /**
     * One STFT hop, when [input] holds N samples: masks the frame, overlap-adds it, moves the now
     * final first HOP samples of the accumulator to [outHop], and slides both buffers by HOP.
     */
    private fun processFrame() {
        computeBandGains()
        for (c in 0..1) {
            val x = input[c]
            for (i in 0 until N) { re[i] = x[i] * WINDOW[i]; im[i] = 0.0 }
            fft(re, im, false)
            for (k in 0..N / 2) {
                val band = bandOfBin[k]
                if (band < 0) continue
                val g = bandGain[band]
                if (g == 1f) continue
                re[k] *= g; im[k] *= g
                if (k != 0 && k != N / 2) { re[N - k] *= g; im[N - k] *= g }
            }
            fft(re, im, true)
            val o = overlap[c]
            // sqrt-Hann twice at 75% overlap sums to 2.
            for (i in 0 until N) o[i] += (re[i] * WINDOW[i] * 0.5).toFloat()
            System.arraycopy(o, 0, outHop[c], 0, HOP)
            System.arraycopy(o, HOP, o, 0, N - HOP)
            java.util.Arrays.fill(o, N - HOP, N, 0f)
            System.arraycopy(x, HOP, x, 0, N - HOP)
        }
        filled = N - HOP
        outPos = 0
    }

    private fun computeBandGains() {
        val m = mask
        val lvl = level
        if (m == null || lvl >= 1f || m.mediaId != mediaId) { bandGain.fill(1f); return }
        // Centre of this frame in track time (samplesSinceAnchor counts the sample just queued).
        val centreSamples = samplesSinceAnchor + 1 - N / 2
        val seconds = anchorMs / 1000.0 + centreSamples.toDouble() / sampleRate
        val frame = (seconds * KaraokeMask.FRAME_RATE).toInt()
        val reduce = 1f - lvl
        for (band in 0 until KaraokeBands.COUNT) {
            // Max over a ±3-frame (~70 ms) neighbourhood: hides the anchor's small timing error
            // and keeps consonant onsets from leaking through.
            var v = -1
            for (f in frame - 3..frame + 3) {
                if (f < 0 || f >= m.frames || !m.ready[f]) continue
                val b = m.data[f * KaraokeBands.COUNT + band].toInt() and 0xFF
                if (b > v) v = b
            }
            bandGain[band] = if (v < 0) 1f else 1f - reduce * (v / 255f)
        }
    }

    companion object {
        private const val N = 2048
        private const val HOP = N / 4
        private val WINDOW = DoubleArray(N) { sqrt(0.5 - 0.5 * cos(2.0 * PI * it / N)) }
        private val COS = DoubleArray(N / 2) { cos(2.0 * PI * it / N) }
        private val SIN = DoubleArray(N / 2) { sin(2.0 * PI * it / N) }
        private val REV = IntArray(N).also { rev ->
            val bits = Integer.numberOfTrailingZeros(N)
            for (i in 0 until N) rev[i] = Integer.reverse(i) ushr (32 - bits)
        }

        /** In-place iterative radix-2 FFT; [inverse] also divides by N. */
        private fun fft(re: DoubleArray, im: DoubleArray, inverse: Boolean) {
            for (i in 0 until N) {
                val j = REV[i]
                if (j > i) {
                    val tr = re[i]; re[i] = re[j]; re[j] = tr
                    val ti = im[i]; im[i] = im[j]; im[j] = ti
                }
            }
            var size = 2
            while (size <= N) {
                val half = size / 2
                val step = N / size
                var start = 0
                while (start < N) {
                    for (k in 0 until half) {
                        val c = COS[k * step]
                        val s = if (inverse) -SIN[k * step] else SIN[k * step]
                        val a = start + k
                        val b = a + half
                        val tr = re[b] * c + im[b] * s
                        val ti = -re[b] * s + im[b] * c
                        re[b] = re[a] - tr; im[b] = im[a] - ti
                        re[a] += tr; im[a] += ti
                    }
                    start += size
                }
                size *= 2
            }
            if (inverse) for (i in 0 until N) { re[i] /= N; im[i] /= N }
        }
    }
}
