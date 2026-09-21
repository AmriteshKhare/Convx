/*
 * Ported from Orchard (https://github.com/SFG5453/Orchard).
 *
 * Copyright (C) 2026 SFG545 (original Orchard implementation)
 * Copyright (C) 2026 Kushagra Singh (BitChord adaptation)
 *
 * Orchard's original source is licensed under the GNU Affero General Public
 * License, version 3 or later. Per AGPLv3 section 13, this file is combined
 * here into BitChord -- a work licensed under the GNU General Public
 * License, version 3 or later -- and remains itself governed by the AGPLv3
 * as part of that combination.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero
 * General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.convx.music.playback.smart

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.channels.FileChannel
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate

/** The linear-frequency STFT front end open-unmix was trained on. */
object VocalSpectrogram {
    val available: Boolean get() = MelSpectrogram.available
    val bins: Int by lazy { if (available) nativeBins() else 2049 }
    val sampleRate: Double by lazy { if (available) nativeSampleRate() else 44_100.0 }
    val hop: Int by lazy { if (available) nativeHop() else 1024 }
    val fftSize: Int by lazy { if (available) nativeFftSize() else 4096 }
    val frameRate: Double get() = sampleRate / hop

    /**
     * Computes the magnitude STFT for planar stereo at [sampleRate].
     *
     * Returns null when the library is missing, the rate is wrong, or the input is shorter than one
     * padded frame, all of which mean "no mask available" rather than an error.
     */
    fun compute(left: FloatArray, right: FloatArray, rate: Double = sampleRate): Spectrogram? {
        if (!available || left.isEmpty() || left.size != right.size) return null
        val values = nativeCompute(left, right, rate)
        if (values.isEmpty()) return null
        return Spectrogram(values, frames = values.size / (CHANNELS * bins), bins = bins)
    }

    /**
     * Bin-major, flattened: channel c, bin b, frame f is at `(c * bins + b) * frames + f`. That
     * ordering is not the natural one for an STFT computed a frame at a time; it is chosen to
     * match the model's `[1, 2, bins, frames]` tensor exactly, so nothing has to transpose.
     */
    data class Spectrogram(val values: FloatArray, val frames: Int, val bins: Int) {
        override fun equals(other: Any?): Boolean =
            this === other || (other is Spectrogram && frames == other.frames &&
                bins == other.bins && values.contentEquals(other.values))

        override fun hashCode(): Int = 31 * (31 * values.contentHashCode() + frames) + bins
    }

    const val CHANNELS = 2

    @JvmStatic private external fun nativeCompute(left: FloatArray, right: FloatArray, rate: Double): FloatArray
    @JvmStatic private external fun nativeBins(): Int
    @JvmStatic private external fun nativeSampleRate(): Double
    @JvmStatic private external fun nativeHop(): Int
    @JvmStatic private external fun nativeFftSize(): Int
}

/**
 * Vocal-presence tracking with KUIELab's MDX-Net "vocals" target (UVR-MDX-NET-9482, MIT weights),
 * ISMIR 2021 MDX 2nd place at 9.00 vocals SDR on MUSDB18 — far above the open-unmix checkpoint
 * this replaces.
 *
 * The transition policy uses this to avoid mixing two vocals over each other: a blend where both
 * tracks are singing is the one case that reliably sounds wrong however well the beats line up.
 * Karaoke uses the same per-band mask to attenuate the vocal stem live.
 *
 * Only the vocals target is used: what matters is how much vocal content is present at an
 * instant, not reconstructing four stems. The model estimates the vocals complex spectrogram
 * directly (not a mask), so the ratio used downstream is `|vocals| / (|mix| + eps)`.
 */
class VocalTracker(private val context: Context) {

    @Volatile private var session: OrtSession? = null
    private val lock = Any()

    private fun session(): OrtSession? {
        session?.let { return it }
        synchronized(lock) {
            session?.let { return it }
            return runCatching {
                val file = File(context.filesDir, MODEL_ASSET)
                if (!file.exists() || file.length() == 0L) {
                    context.assets.open(MODEL_ASSET).use { input ->
                        file.outputStream().use { output -> input.copyTo(output) }
                    }
                }
                // One-time eviction of the retired open-unmix checkpoint (~9 MB).
                File(context.filesDir, STALE_MODEL_ASSET).delete()
                val options = OrtSession.SessionOptions().apply {
                    setIntraOpNumThreads(INFERENCE_THREADS)
                    setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                    // Same reasoning as BeatTracker: the arena retains every block it allocates for
                    // the life of the session, which a backgrounded music player cannot justify.
                    setCPUArenaAllocator(false)
                    setMemoryPatternOptimization(false)
                }
                OrtEnvironment.getEnvironment().createSession(file.absolutePath, options)
                    .also { session = it }
            }.onFailure { Log.w(TAG, "Vocal model unavailable; no mask will be produced", it) }
                .getOrNull()
        }
    }

    /**
     * Returns one vocal-presence value per STFT frame in [0, 1], or null when unavailable.
     *
     * The model's input width is fixed at [FIXED_FRAMES] (~22.8 s), which was chosen upstream to
     * cover a transition overlap plus padding. Shorter input is zero-padded; longer is refused
     * rather than chunked, because a transition never needs more than one window.
     */
    fun track(left: FloatArray, right: FloatArray, rate: Double): FloatArray? {
        if (!VocalSpectrogram.available) return null
        val resampledLeft = MelSpectrogram.resample(left, rate, VocalSpectrogram.sampleRate) ?: return null
        val resampledRight = MelSpectrogram.resample(right, rate, VocalSpectrogram.sampleRate) ?: return null

        val started = System.currentTimeMillis()
        val frames = (resampledLeft.size + MdxFront.HOP - 1) / MdxFront.HOP
        if (resampledLeft.size != resampledRight.size || frames > FIXED_FRAMES) {
            Log.d(TAG, "Window of $frames frames exceeds the model's $FIXED_FRAMES")
            return null
        }
        val active = session() ?: return null

        val ratios = MdxFront.ratios(resampledLeft, resampledRight, frames, active)
        if (ratios == null) {
            Log.w(TAG, "Vocal inference failed")
            return null
        }
        val lowBin = floor(LOW_HZ * MdxFront.N_FFT / VocalSpectrogram.sampleRate)
            .toInt().coerceAtLeast(0)
        val highBin = ceil(HIGH_HZ * MdxFront.N_FFT / VocalSpectrogram.sampleRate)
            .toInt().coerceAtMost(MdxFront.DIM_F - 1)
        if (highBin <= lowBin) return FloatArray(0)
        val curve = FloatArray(frames) { frame ->
            var sum = 0.0
            for (bin in lowBin..highBin) sum += ratios[frame][bin]
            (sum / (highBin - lowBin + 1)).toFloat()
        }
        Log.d(
            TAG,
            "vocal mask $frames frames in " +
                "${System.currentTimeMillis() - started}ms",
        )
        return curve
    }

    /**
     * The MDX-Net front end: complex STFT plus chunking that mirrors the reference
     * implementation bit-for-bit (periodic Hann, n_fft 6144/4096 with hop 1024,
     * center=True reflect padding, zero trim caps, GEN-sized steps), so the ONNX
     * export sees exactly the frames it was trained on.
     *
     * GEN and HOP share the model's frame grid on purpose: chunk k's frame f lands
     * on continuous frame `k * STRIDE + f - LEAD`, so ratios drop straight into the
     * caller's mask frames with no resampling. No inverse STFT anywhere — only the
     * magnitudes are needed for the ratio.
     *
     * Denoise mode (averaging the runs of x and -x) is deliberately off: it doubles
     * inference cost for a small quality gain the phone budget cannot afford.
     */
    private object MdxFront {
        const val N_FFT = 4096
        const val HOP = 1024
        const val DIM_F = 2048
        const val DIM_T = 256
        const val CHUNK = HOP * (DIM_T - 1)
        const val TRIM = N_FFT / 2
        const val GEN = CHUNK - 2 * TRIM
        const val STRIDE = GEN / HOP
        const val LEAD = TRIM / HOP

        private val HANN = FloatArray(N_FFT) {
            (0.5 - 0.5 * cos(2.0 * PI * it / N_FFT)).toFloat()
        }
        private val workRe = FloatArray(N_FFT)
        private val workIm = FloatArray(N_FFT)

        /**
         * Per-frame vocal ratios `[frame][bin]` in [0, 1], silence baked as 0, or
         * null when inference fails. Single-threaded by construction — both
         * callers run it on their own worker — so the scratch buffers are shared
         * rather than reallocated per chunk.
         */
        fun ratios(
            left: FloatArray,
            right: FloatArray,
            frames: Int,
            session: OrtSession,
        ): Array<FloatArray>? {
            val samples = left.size
            if (samples == 0 || frames <= 0) return null
            val out = Array(frames) { FloatArray(DIM_F) }
            val pad = GEN - samples % GEN
            val len = TRIM + samples + pad + TRIM
            val padL = FloatArray(len)
            val padR = FloatArray(len)
            left.copyInto(padL, TRIM, 0, samples)
            right.copyInto(padR, TRIM, 0, samples)

            val environment = OrtEnvironment.getEnvironment()
            val shape = longArrayOf(1, 4, DIM_F.toLong(), DIM_T.toLong())
            // Direct, so the ~12MB of input never lands on the Java heap and ORT
            // reads it where it lies — same reasoning as the old magnitude path,
            // which died on 256MB-heap devices from two copies of less than this.
            val backing = ByteBuffer
                .allocateDirect(4 * DIM_F * DIM_T * Float.SIZE_BYTES)
                .order(ByteOrder.nativeOrder())
            val mixRe = Array(2) { Array(DIM_T) { FloatArray(DIM_F) } }
            val mixIm = Array(2) { Array(DIM_T) { FloatArray(DIM_F) } }

            var k = 0
            while (k * GEN < TRIM + samples) {
                val start = k * GEN
                stftChunk(padL, padR, start, len, mixRe, mixIm)
                pack(backing.asFloatBuffer(), mixRe, mixIm)
                try {
                    // A fresh view per run rather than rewinding one: FloatBuffer's
                    // own rewind() and position(int) are Java 9 covariant overrides
                    // that Android's FloatBuffer does not declare, so they compile
                    // against the current SDK and throw NoSuchMethodError on the
                    // API 28 devices this has to run on. asFloatBuffer() hands back
                    // a view at position 0 and has been there since API 1.
                    OnnxTensor.createTensor(environment, backing.asFloatBuffer(), shape).use { tensor ->
                        session.run(mapOf(session.inputNames.first() to tensor)).use { outputs ->
                            // The output buffer, not `outputs.get(0).value`: that
                            // property boxes the [1, 4, 2048, 256] result into one
                            // FloatArray per cell when only magnitudes are read.
                            // Read inside use{} — the view dies with the outputs.
                            reduce((outputs.get(0) as OnnxTensor).floatBuffer, mixRe, mixIm, k, frames, out)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "MDX inference failed", e)
                    return null
                }
                k++
            }
            return out
        }

        /** One chunk's complex STFT per channel, torch center=True semantics. */
        private fun stftChunk(
            padL: FloatArray,
            padR: FloatArray,
            start: Int,
            len: Int,
            mixRe: Array<Array<FloatArray>>,
            mixIm: Array<Array<FloatArray>>,
        ) {
            for (channel in 0..1) {
                val src = if (channel == 0) padL else padR
                val dstRe = mixRe[channel]
                val dstIm = mixIm[channel]
                for (frame in 0 until DIM_T) {
                    val base = start + frame * HOP - TRIM
                    for (n in 0 until N_FFT) {
                        var idx = base + n
                        if (idx < 0) idx = -idx
                        else if (idx >= len) idx = 2 * (len - 1) - idx
                        workRe[n] = (if (idx < 0 || idx >= len) 0f else src[idx]) * HANN[n]
                        workIm[n] = 0f
                    }
                    fft(workRe, workIm)
                    val frameRe = dstRe[frame]
                    val frameIm = dstIm[frame]
                    for (bin in 0 until DIM_F) {
                        frameRe[bin] = workRe[bin]
                        frameIm[bin] = workIm[bin]
                    }
                }
            }
        }

        /** Iterative radix-2 FFT, in place. N_FFT is a power of two by construction. */
        internal fun fft(re: FloatArray, im: FloatArray) {
            val n = re.size
            var j = 0
            for (i in 1 until n) {
                var bit = n shr 1
                while (j and bit != 0) {
                    j = j xor bit
                    bit = bit shr 1
                }
                j = j xor bit
                if (i < j) {
                    val tr = re[i]; re[i] = re[j]; re[j] = tr
                    val ti = im[i]; im[i] = im[j]; im[j] = ti
                }
            }
            var size = 2
            while (size <= n) {
                val angle = -2.0 * PI / size
                val wr = cos(angle)
                val wi = sin(angle)
                var i = 0
                while (i < n) {
                    var wRe = 1.0
                    var wIm = 0.0
                    for (k in 0 until size / 2) {
                        val ur = re[i + k]
                        val ui = im[i + k]
                        val vr = re[i + k + size / 2] * wRe - im[i + k + size / 2] * wIm
                        val vi = re[i + k + size / 2] * wIm + im[i + k + size / 2] * wRe
                        re[i + k] = (ur + vr).toFloat()
                        im[i + k] = (ui + vi).toFloat()
                        re[i + k + size / 2] = (ur - vr).toFloat()
                        im[i + k + size / 2] = (ui - vi).toFloat()
                        val t = wRe
                        wRe = wRe * wr - wIm * wi
                        wIm = t * wi + wIm * wr
                    }
                    i += size
                }
                size = size shl 1
            }
        }

        /**
         * Packs `[leftReal, rightReal, leftImag, rightImag]` — the reference's
         * channel order — dropping the top bin the model never saw.
         */
        private fun pack(
            into: FloatBuffer,
            mixRe: Array<Array<FloatArray>>,
            mixIm: Array<Array<FloatArray>>,
        ) {
            for (c in 0..3) {
                val channel = c and 1
                val src = if (c < 2) mixRe[channel] else mixIm[channel]
                for (bin in 0 until DIM_F) {
                    for (frame in 0 until DIM_T) {
                        into.put(src[frame][bin])
                    }
                }
            }
        }

        /** `|vocals| / |mix|` per cell, averaged over channels, clamped. */
        private fun reduce(
            target: FloatBuffer,
            mixRe: Array<Array<FloatArray>>,
            mixIm: Array<Array<FloatArray>>,
            chunk: Int,
            frames: Int,
            out: Array<FloatArray>,
        ) {
            for (frame in 0 until DIM_T) {
                val t = chunk * STRIDE + frame - LEAD
                if (t < 0 || t >= frames) continue
                val row = out[t]
                for (bin in 0 until DIM_F) {
                    val lr = mixRe[0][frame][bin]
                    val li = mixIm[0][frame][bin]
                    val rr = mixRe[1][frame][bin]
                    val ri = mixIm[1][frame][bin]
                    val ml = sqrt(lr * lr + li * li)
                    val mr = sqrt(rr * rr + ri * ri)
                    if (ml <= 1e-6f || mr <= 1e-6f) continue
                    val base = bin * DIM_T + frame
                    val vl = sqrt(
                        target.get(base) * target.get(base) +
                            target.get(base + 2 * DIM_F * DIM_T) *
                                target.get(base + 2 * DIM_F * DIM_T),
                    )
                    val vr = sqrt(
                        target.get(base + DIM_F * DIM_T) * target.get(base + DIM_F * DIM_T) +
                            target.get(base + 3 * DIM_F * DIM_T) *
                                target.get(base + 3 * DIM_F * DIM_T),
                    )
                    row[bin] = ((vl / ml + vr / mr) * 0.5f).coerceIn(0f, 1f)
                }
            }
        }
    }

    /**
     * Per-frame, per-band vocal mask for karaoke: `target / mix` clamped to [0, 1], averaged over
     * both channels and over the model bins falling in each of [KaraokeBands] log bands, quantized
     * to a byte. Index `frame * KaraokeBands.COUNT + band`. Frames run at
     * [VocalSpectrogram.frameRate] from the window's first sample.
     *
     * Band-reduced rather than per-bin so a whole song's mask is a few MB, and so the real-time
     * processor (which runs its own, smaller STFT at the stream rate) can look it up by Hz.
     */
    fun separationMask(left: FloatArray, right: FloatArray, rate: Double): Pair<ByteArray, Int>? {
        if (!VocalSpectrogram.available) return null
        val resampledLeft = MelSpectrogram.resample(left, rate, VocalSpectrogram.sampleRate) ?: return null
        val resampledRight = MelSpectrogram.resample(right, rate, VocalSpectrogram.sampleRate) ?: return null
        if (resampledLeft.size != resampledRight.size) return null
        // Caller windows to FIXED_FRAMES; a hair over (resampler rounding) is truncated.
        val frames = ((resampledLeft.size + MdxGpu.HOP - 1) / MdxGpu.HOP).coerceAtMost(FIXED_FRAMES)
        val active = gpuSession() ?: return null
        val started = System.currentTimeMillis()
        val ratios = MdxGpu.ratios(resampledLeft, resampledRight, frames) { input, output ->
            active.interpreter.run(input, output)
        }
        if (ratios == null) {
            Log.w(TAG, "Karaoke mask failed")
            return null
        }
        Log.d(
            TAG,
            "karaoke mask $frames frames on ${active.backend} in " +
                "${System.currentTimeMillis() - started}ms",
        )
        val bandOfBin = KaraokeBands.bandOfBin(MdxGpu.DIM_F, VocalSpectrogram.sampleRate, MdxGpu.N_FFT)
        val out = ByteArray(frames * KaraokeBands.COUNT)
        val sum = FloatArray(KaraokeBands.COUNT)
        val count = IntArray(KaraokeBands.COUNT)
        for (frame in 0 until frames) {
            sum.fill(0f); count.fill(0)
            val row = ratios[frame]
            for (bin in 0 until MdxGpu.DIM_F) {
                val band = bandOfBin[bin]
                if (band < 0) continue
                sum[band] += row[bin]
                count[band]++
            }
            for (band in 0 until KaraokeBands.COUNT) {
                val v = if (count[band] > 0) sum[band] / count[band] else 0f
                out[frame * KaraokeBands.COUNT + band] = (v * 255f + 0.5f).toInt().toByte()
            }
        }
        return out to frames
    }

    /**
     * LiteRT session for the streaming-quality mask path (Sing): GPU delegate
     * when the device supports it, 4-thread XNNPACK CPU otherwise. The DJ
     * transition curve keeps the proven ORT session above; this one only feeds
     * [separationMask].
     */
    private data class GpuSession(
        val interpreter: Interpreter,
        val delegate: GpuDelegate?,
        val backend: String,
    )

    @Volatile private var gpuSession: GpuSession? = null
    private val gpuLock = Any()

    private fun gpuSession(): GpuSession? {
        gpuSession?.let { return it }
        synchronized(gpuLock) {
            gpuSession?.let { return it }
            return runCatching {
                val fd = context.assets.openFd(MODEL_TFLITE)
                val model = FileInputStream(fd.fileDescriptor).use { stream ->
                    stream.channel.use { channel ->
                        channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
                    }
                }
                val options = Interpreter.Options()
                var delegate: GpuDelegate? = null
                val backend = try {
                    if (CompatibilityList().isDelegateSupportedOnThisDevice) {
                        delegate = GpuDelegate(CompatibilityList().bestOptionsForThisDevice)
                        options.addDelegate(delegate)
                        "gpu"
                    } else {
                        options.setNumThreads(INFERENCE_THREADS)
                        "cpu"
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "GPU delegate unavailable, CPU fallback", e)
                    runCatching { delegate?.close() }
                    delegate = null
                    options.setNumThreads(INFERENCE_THREADS)
                    "cpu"
                }
                GpuSession(Interpreter(model, options), delegate, backend).also {
                    gpuSession = it
                    Log.d(TAG, "MDX LiteRT session on $backend")
                }
            }.onFailure { Log.w(TAG, "LiteRT session unavailable", it) }.getOrNull()
        }
    }

    /**
     * The Voc_FT front end (n_fft 6144) for the LiteRT mask path: same
     * reference-mirroring chunking as [MdxFront] (periodic Hann, center=True
     * reflect, zero trim caps, GEN steps), so the .tflite export sees exactly
     * the frames it was converted from.
     *
     * 6144 is 3 × 2048, so the FFT is mixed-radix: three radix-2 passes
     * ([MdxFront.fft], device-proven on the 9482 path) plus one radix-3
     * combine with precomputed twiddles — verified bit-close against numpy
     * (max err 2.4e-13) before shipping.
     */
    private object MdxGpu {
        const val N_FFT = 6144
        const val HOP = 1024
        const val DIM_F = 3072
        const val DIM_T = 256
        const val CHUNK = HOP * (DIM_T - 1)
        const val TRIM = N_FFT / 2
        const val GEN = CHUNK - 2 * TRIM
        const val STRIDE = GEN / HOP
        const val LEAD = TRIM / HOP
        private const val M = 2048

        private val HANN = FloatArray(N_FFT) {
            (0.5 - 0.5 * cos(2.0 * PI * it / N_FFT)).toFloat()
        }

        /** Twiddles W_N^{s·k} for the radix-3 combine, real/imag interleaved per s. */
        private val TWIDDLE: FloatArray by lazy {
            val tw = FloatArray(3 * N_FFT * 2)
            for (s in 0..2) {
                for (k in 0 until N_FFT) {
                    val angle = -2.0 * PI * s * k / N_FFT
                    tw[(s * N_FFT + k) * 2] = cos(angle).toFloat()
                    tw[(s * N_FFT + k) * 2 + 1] = sin(angle).toFloat()
                }
            }
            tw
        }

        private val workRe = FloatArray(N_FFT)
        private val workIm = FloatArray(N_FFT)
        private val decRe = FloatArray(M)
        private val decIm = FloatArray(M)
        private val accRe = Array(3) { FloatArray(M) }
        private val accIm = Array(3) { FloatArray(M) }

        /**
         * Per-frame vocal ratios `[frame][bin]`, silence baked as 0, or null on
         * failure. [run] executes one `[1, 4, DIM_F, DIM_T]` chunk through the
         * interpreter (GPU or CPU — the caller's session decides).
         */
        fun ratios(
            left: FloatArray,
            right: FloatArray,
            frames: Int,
            run: (ByteBuffer, ByteBuffer) -> Unit,
        ): Array<FloatArray>? {
            val samples = left.size
            if (samples == 0 || frames <= 0) return null
            val out = Array(frames) { FloatArray(DIM_F) }
            val pad = GEN - samples % GEN
            val len = TRIM + samples + pad + TRIM
            val padL = FloatArray(len)
            val padR = FloatArray(len)
            left.copyInto(padL, TRIM, 0, samples)
            right.copyInto(padR, TRIM, 0, samples)

            val shape = 4 * DIM_F * DIM_T
            val backingIn = ByteBuffer
                .allocateDirect(shape * Float.SIZE_BYTES)
                .order(ByteOrder.nativeOrder())
            val backingOut = ByteBuffer
                .allocateDirect(shape * Float.SIZE_BYTES)
                .order(ByteOrder.nativeOrder())
            val mixRe = Array(2) { Array(DIM_T) { FloatArray(DIM_F) } }
            val mixIm = Array(2) { Array(DIM_T) { FloatArray(DIM_F) } }

            var k = 0
            while (k * GEN < TRIM + samples) {
                val start = k * GEN
                stftChunk(padL, padR, start, len, mixRe, mixIm)
                pack(backingIn.asFloatBuffer(), mixRe, mixIm)
                try {
                    // Fresh views per run (see the API-28 note on the ORT path):
                    // absolute gets below don't touch positions anyway.
                    run(backingIn, backingOut)
                    reduce(backingOut.asFloatBuffer(), mixRe, mixIm, k, frames, out)
                } catch (e: Exception) {
                    Log.w(TAG, "MDX LiteRT inference failed", e)
                    return null
                }
                k++
            }
            return out
        }

        private fun stftChunk(
            padL: FloatArray,
            padR: FloatArray,
            start: Int,
            len: Int,
            mixRe: Array<Array<FloatArray>>,
            mixIm: Array<Array<FloatArray>>,
        ) {
            for (channel in 0..1) {
                val src = if (channel == 0) padL else padR
                val dstRe = mixRe[channel]
                val dstIm = mixIm[channel]
                for (frame in 0 until DIM_T) {
                    val base = start + frame * HOP - TRIM
                    for (n in 0 until N_FFT) {
                        var idx = base + n
                        if (idx < 0) idx = -idx
                        else if (idx >= len) idx = 2 * (len - 1) - idx
                        workRe[n] = (if (idx < 0 || idx >= len) 0f else src[idx]) * HANN[n]
                        workIm[n] = 0f
                    }
                    fft6144(workRe, workIm)
                    val frameRe = dstRe[frame]
                    val frameIm = dstIm[frame]
                    for (bin in 0 until DIM_F) {
                        frameRe[bin] = workRe[bin]
                        frameIm[bin] = workIm[bin]
                    }
                }
            }
        }

        private fun fft6144(re: FloatArray, im: FloatArray) {
            for (s in 0..2) {
                for (r in 0 until M) {
                    decRe[r] = re[3 * r + s]
                    decIm[r] = im[3 * r + s]
                }
                MdxFront.fft(decRe, decIm)
                for (r in 0 until M) {
                    accRe[s][r] = decRe[r]
                    accIm[s][r] = decIm[r]
                }
            }
            for (k in 0 until N_FFT) {
                val m = k % M
                var sr = 0f
                var si = 0f
                for (s in 0..2) {
                    val wr = TWIDDLE[(s * N_FFT + k) * 2]
                    val wi = TWIDDLE[(s * N_FFT + k) * 2 + 1]
                    val ar = accRe[s][m]
                    val ai = accIm[s][m]
                    sr += wr * ar - wi * ai
                    si += wr * ai + wi * ar
                }
                re[k] = sr
                im[k] = si
            }
        }

        private fun pack(
            into: FloatBuffer,
            mixRe: Array<Array<FloatArray>>,
            mixIm: Array<Array<FloatArray>>,
        ) {
            for (c in 0..3) {
                val channel = c and 1
                val src = if (c < 2) mixRe[channel] else mixIm[channel]
                for (bin in 0 until DIM_F) {
                    for (frame in 0 until DIM_T) {
                        into.put(src[frame][bin])
                    }
                }
            }
        }

        private fun reduce(
            target: FloatBuffer,
            mixRe: Array<Array<FloatArray>>,
            mixIm: Array<Array<FloatArray>>,
            chunk: Int,
            frames: Int,
            out: Array<FloatArray>,
        ) {
            for (frame in 0 until DIM_T) {
                val t = chunk * STRIDE + frame - LEAD
                if (t < 0 || t >= frames) continue
                val row = out[t]
                for (bin in 0 until DIM_F) {
                    val lr = mixRe[0][frame][bin]
                    val li = mixIm[0][frame][bin]
                    val rr = mixRe[1][frame][bin]
                    val ri = mixIm[1][frame][bin]
                    val ml = sqrt(lr * lr + li * li)
                    val mr = sqrt(rr * rr + ri * ri)
                    if (ml <= 1e-6f || mr <= 1e-6f) continue
                    val base = bin * DIM_T + frame
                    val vl = sqrt(
                        target.get(base) * target.get(base) +
                            target.get(base + 2 * DIM_F * DIM_T) *
                                target.get(base + 2 * DIM_F * DIM_T),
                    )
                    val vr = sqrt(
                        target.get(base + DIM_F * DIM_T) * target.get(base + DIM_F * DIM_T) +
                            target.get(base + 3 * DIM_F * DIM_T) *
                                target.get(base + 3 * DIM_F * DIM_T),
                    )
                    row[bin] = ((vl / ml + vr / mr) * 0.5f).coerceIn(0f, 1f)
                }
            }
        }
    }

    fun release() {
        synchronized(lock) {
            runCatching { session?.close() }
            session = null
        }
        synchronized(gpuLock) {
            runCatching { gpuSession?.interpreter?.close() }
            runCatching { gpuSession?.delegate?.close() }
            gpuSession = null
        }
    }

    companion object {
        private const val TAG = "ViviVocalTracker"
        private const val MODEL_ASSET = "uvr_mdxnet_9482.onnx"
        private const val MODEL_TFLITE = "uvr_mdx_voc_ft_fp32.tflite"
        private const val STALE_MODEL_ASSET = "vocals_umxhq_int8.onnx"
        private const val INFERENCE_THREADS = 4

        /** The model's fixed input width, ~22.8 s, chosen upstream to cover a transition overlap. */
        const val FIXED_FRAMES = 960

        /** The band a vocal actually occupies; below and above it the mask says little. */
        private const val LOW_HZ = 200.0
        private const val HIGH_HZ = 4000.0
    }
}

/**
 * Bands the karaoke mask is stored in, shared by the offline mask ([VocalTracker]) and the
 * real-time processor so both agree on what band a frequency is in.
 *
 * Fine enough that the mask stays a *vocal* mask: 21.5 Hz linear bands (two model bins) up to
 * 2 kHz, where voice harmonics sit a few tens of Hz from kick, bass and snare body, then 100 log
 * bands (~2% wide) to 16 kHz. Coarser bands would average a hi-hat or snare in with the voice
 * sharing its band and dim it too. Below 60 Hz nothing is touched.
 */
object KaraokeBands {
    private const val LINEAR_HZ = 44_100.0 / 4096.0 * 2.0
    private const val SPLIT_HZ = 2_000.0
    private const val TOP_HZ = 16_000.0
    private val LINEAR = (SPLIT_HZ / LINEAR_HZ).toInt()
    private const val LOG = 100
    val COUNT = LINEAR + LOG

    fun bandOf(hz: Double): Int = when {
        hz < 60.0 -> -1
        hz < SPLIT_HZ -> (hz / LINEAR_HZ).toInt().coerceAtMost(LINEAR - 1)
        else -> LINEAR + (kotlin.math.ln(hz / SPLIT_HZ) / kotlin.math.ln(TOP_HZ / SPLIT_HZ) * LOG)
            .toInt().coerceIn(0, LOG - 1)
    }

    fun bandOfBin(bins: Int, sampleRate: Double, fftSize: Int): IntArray =
        IntArray(bins) { bandOf(it * sampleRate / fftSize) }
}
