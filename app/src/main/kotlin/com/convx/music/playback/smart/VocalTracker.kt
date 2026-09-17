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
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.ceil
import kotlin.math.floor

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
 * Vocal-presence tracking with open-unmix's "vocals" target (Stöter & Liutkus, Inria/SigSep).
 *
 * The transition policy uses this to avoid mixing two vocals over each other: a blend where both
 * tracks are singing is the one case that reliably sounds wrong however well the beats line up.
 *
 * Chosen because its **weights** are MIT, confirmed on the Zenodo deposit rather than inferred from
 * the code repository. Meta's htdemucs separates better but releases its pretrained weights under
 * CC-BY-NC-4.0, which a distributed application cannot ship, and its ONNX export additionally has
 * unresolved blockers around complex-valued STFT ops.
 *
 * Only the vocals target is used. open-unmix trains four independent checkpoints; BitChord needs to
 * know how much vocal content is present at an instant, not to reconstruct four stems.
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
        val spectrogram = VocalSpectrogram.compute(resampledLeft, resampledRight) ?: return null
        if (spectrogram.frames > FIXED_FRAMES) {
            Log.d(TAG, "Window of ${spectrogram.frames} frames exceeds the model's $FIXED_FRAMES")
            return null
        }
        val active = session() ?: return null

        return runCatching {
            val bins = spectrogram.bins
            // Direct, and sized for the model rather than for the input, so the
            // ~16MB of mix never lands on the Java heap and ORT reads it where it
            // lies instead of copying it into native memory. Both matter: this
            // runs on devices whose whole Java heap is 256MB, and the two copies
            // this replaces were together enough to end the process.
            val backing = ByteBuffer
                .allocateDirect(VocalSpectrogram.CHANNELS * bins * FIXED_FRAMES * Float.SIZE_BYTES)
                .order(ByteOrder.nativeOrder())
            fillFixedFrames(backing.asFloatBuffer(), spectrogram.values, bins, spectrogram.frames)
            val environment = OrtEnvironment.getEnvironment()
            val shape = longArrayOf(1, VocalSpectrogram.CHANNELS.toLong(), bins.toLong(), FIXED_FRAMES.toLong())

            // A fresh view per reader rather than rewinding one: FloatBuffer's own
            // rewind() and position(int) are Java 9 covariant overrides that
            // Android's FloatBuffer does not declare, so they compile against the
            // current SDK and throw NoSuchMethodError on the API 28 devices this
            // has to run on. asFloatBuffer() hands back a view at position 0 and
            // has been there since API 1.
            OnnxTensor.createTensor(environment, backing.asFloatBuffer(), shape).use { tensor ->
                active.run(mapOf(active.inputNames.first() to tensor)).use { outputs ->
                    // The output tensor's buffer, not `outputs.get(0).value`: that
                    // property boxes this [1, 2, bins, FIXED_FRAMES] result into
                    // one FloatArray per bin per channel — 4098 objects and ~16MB
                    // per call — when the only thing read from it is a band
                    // average.
                    val target = (outputs.get(0) as OnnxTensor).floatBuffer
                    val curve = reduceToBandCurve(backing.asFloatBuffer(), target, bins, spectrogram.frames)
                    Log.d(
                        TAG,
                        "vocal mask ${spectrogram.frames} frames in " +
                            "${System.currentTimeMillis() - started}ms",
                    )
                    curve
                }
            }
        }.onFailure { Log.w(TAG, "Vocal inference failed", it) }.getOrNull()
    }

    /**
     * Writes the spectrogram into the model's fixed width, zero-padding each bin's tail.
     *
     * The stride changes as well as the length: the source is stored at `frames` per bin and the
     * model wants [FIXED_FRAMES], so this is a re-stride rather than an append.
     *
     * Sequential relative puts only. Seeking to each bin's start would be the obvious way to write
     * it, but that needs `FloatBuffer.position(int)`, which Android declares on `Buffer` alone;
     * writing the pad out explicitly keeps every call on a member that has existed since API 1.
     */
    private fun fillFixedFrames(into: FloatBuffer, values: FloatArray, bins: Int, frames: Int) {
        if (frames == FIXED_FRAMES) {
            into.put(values)
            return
        }
        val pad = FloatArray(FIXED_FRAMES - frames)
        for (channel in 0 until VocalSpectrogram.CHANNELS) {
            for (bin in 0 until bins) {
                into.put(values, (channel * bins + bin) * frames, frames)
                into.put(pad)
            }
        }
    }

    /**
     * Averages `mask = target / (mix + eps)` across a frequency band, then across channels.
     *
     * Band-averaging rather than a full per-bin mask: the only consumer is a single number per
     * instant (how vocal this moment is), so per-bin resolution would be work with no reader.
     * Only the frames carrying real audio are reduced; the padded tail's mask is meaningless and
     * folding it in would drag every short window toward silence.
     */
    private fun reduceToBandCurve(
        mix: FloatBuffer,
        target: FloatBuffer,
        bins: Int,
        usableFrames: Int,
    ): FloatArray {
        val lowBin = floor(LOW_HZ * VocalSpectrogram.fftSize / VocalSpectrogram.sampleRate)
            .toInt().coerceAtLeast(0)
        val highBin = ceil(HIGH_HZ * VocalSpectrogram.fftSize / VocalSpectrogram.sampleRate)
            .toInt().coerceAtMost(bins - 1)
        if (highBin <= lowBin || usableFrames <= 0) return FloatArray(0)

        val curve = FloatArray(usableFrames)
        for (frame in 0 until usableFrames) {
            var sum = 0.0
            var count = 0
            for (channel in 0 until VocalSpectrogram.CHANNELS) {
                for (bin in lowBin..highBin) {
                    // Both buffers carry the model's [1, 2, bins, FIXED_FRAMES]
                    // layout, so one index reads the same cell of each. Absolute
                    // get, so neither view's position matters.
                    val index = (channel * bins + bin) * FIXED_FRAMES + frame
                    val mixValue = mix.get(index)
                    if (mixValue <= 1e-6f) continue
                    val ratio = target.get(index) / mixValue
                    sum += ratio.coerceIn(0f, 1f)
                    count += 1
                }
            }
            curve[frame] = if (count > 0) (sum / count).toFloat() else 0f
        }
        return curve
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
        val spectrogram = VocalSpectrogram.compute(resampledLeft, resampledRight) ?: return null
        val frames = spectrogram.frames.coerceAtMost(FIXED_FRAMES)
        val active = session() ?: return null
        return runCatching {
            val bins = spectrogram.bins
            val backing = ByteBuffer
                .allocateDirect(VocalSpectrogram.CHANNELS * bins * FIXED_FRAMES * Float.SIZE_BYTES)
                .order(ByteOrder.nativeOrder())
            if (spectrogram.frames > FIXED_FRAMES) {
                // Caller windows to FIXED_FRAMES; a hair over (resampler rounding) is truncated.
                val into = backing.asFloatBuffer()
                for (channel in 0 until VocalSpectrogram.CHANNELS) for (bin in 0 until bins) {
                    into.put(spectrogram.values, (channel * bins + bin) * spectrogram.frames, FIXED_FRAMES)
                }
            } else {
                fillFixedFrames(backing.asFloatBuffer(), spectrogram.values, bins, spectrogram.frames)
            }
            val shape = longArrayOf(1, VocalSpectrogram.CHANNELS.toLong(), bins.toLong(), FIXED_FRAMES.toLong())
            OnnxTensor.createTensor(OrtEnvironment.getEnvironment(), backing.asFloatBuffer(), shape).use { tensor ->
                active.run(mapOf(active.inputNames.first() to tensor)).use { outputs ->
                    val target = (outputs.get(0) as OnnxTensor).floatBuffer
                    val mix = backing.asFloatBuffer()
                    val bandOfBin = KaraokeBands.bandOfBin(bins, VocalSpectrogram.sampleRate, VocalSpectrogram.fftSize)
                    val out = ByteArray(frames * KaraokeBands.COUNT)
                    val sum = FloatArray(KaraokeBands.COUNT)
                    val count = IntArray(KaraokeBands.COUNT)
                    for (frame in 0 until frames) {
                        sum.fill(0f); count.fill(0)
                        for (channel in 0 until VocalSpectrogram.CHANNELS) {
                            for (bin in 0 until bins) {
                                val band = bandOfBin[bin]
                                if (band < 0) continue
                                val index = (channel * bins + bin) * FIXED_FRAMES + frame
                                val m = mix.get(index)
                                if (m <= 1e-6f) continue
                                sum[band] += (target.get(index) / m).coerceIn(0f, 1f)
                                count[band]++
                            }
                        }
                        for (band in 0 until KaraokeBands.COUNT) {
                            val v = if (count[band] > 0) sum[band] / count[band] else 0f
                            out[frame * KaraokeBands.COUNT + band] = (v * 255f + 0.5f).toInt().toByte()
                        }
                    }
                    out to frames
                }
            }
        }.onFailure { Log.w(TAG, "Karaoke mask failed", it) }.getOrNull()
    }

    fun release() {
        synchronized(lock) {
            runCatching { session?.close() }
            session = null
        }
    }

    companion object {
        private const val TAG = "ViviVocalTracker"
        private const val MODEL_ASSET = "vocals_umxhq_int8.onnx"
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
