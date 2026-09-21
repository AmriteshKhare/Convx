/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.playback.dj

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Tempo *and phase* of a track's beat grid. Phase is what makes beatmatching
 * possible: two 128 BPM tracks say nothing about where downbeats fall.
 */
data class BeatGrid(
    val bpm: Float,
    /** Absolute track time of one real detected beat, in ms; the grid is
     *  `anchorBeatMs + n * beatMs` for any n. Deliberately NOT reduced into
     *  [0, beatMs): that would compound the tempo error across the window —
     *  ~0.15% over 60 beats is already an inverted mix. */
    val anchorBeatMs: Float,
    /** 0..1 normalized autocorrelation at the detected period, gated by how
     *  percussive the material is ([DjMixPlanner] thresholds assume real coefficients). */
    val confidence: Float,
) {
    val beatMs: Float get() = if (bpm > 0f) 60_000f / bpm else 0f

    /** First beat of the grid at or after [positionMs]. */
    fun beatAtOrAfter(positionMs: Long): Float {
        val beat = beatMs
        if (beat <= 0f) return positionMs.toFloat()
        val beatsAway = Math.ceil((positionMs - anchorBeatMs) / beat.toDouble())
        return anchorBeatMs + (beatsAway * beat).toFloat()
    }

    /**
     * Phrase boundary nearest [positionMs]; starting a mix mid-phrase is the most
     * audible "a machine did this" tell. Boundaries come from a consistent 16-beat
     * lattice off [anchorBeatMs] — no downbeat detection — and both sides share it.
     */
    fun nearestPhraseMs(positionMs: Long, beatsPerPhrase: Int = BEATS_PER_PHRASE): Float {
        val phrase = beatMs * beatsPerPhrase
        if (phrase <= 0f) return positionMs.toFloat()
        val phrasesAway = Math.round((positionMs - anchorBeatMs) / phrase.toDouble())
        return anchorBeatMs + phrasesAway * phrase
    }

    /** Last phrase boundary at or before [positionMs]. */
    fun phraseAtOrBeforeMs(positionMs: Long, beatsPerPhrase: Int = BEATS_PER_PHRASE): Float {
        val phrase = beatMs * beatsPerPhrase
        if (phrase <= 0f) return positionMs.toFloat()
        val phrasesAway = Math.floor((positionMs - anchorBeatMs) / phrase.toDouble())
        return anchorBeatMs + (phrasesAway * phrase).toFloat()
    }

    /** First phrase boundary at or after [positionMs]. */
    fun phraseAtOrAfterMs(positionMs: Long, beatsPerPhrase: Int = BEATS_PER_PHRASE): Float {
        val phrase = beatMs * beatsPerPhrase
        if (phrase <= 0f) return positionMs.toFloat()
        val phrasesAway = Math.ceil((positionMs - anchorBeatMs) / phrase.toDouble())
        return anchorBeatMs + (phrasesAway * phrase).toFloat()
    }

    companion object {
        const val BEATS_PER_PHRASE = 16
        val NONE = BeatGrid(0f, 0f, 0f)
    }
}

/**
 * Beat tracker: onset-strength envelope -> autocorrelation tempo -> phase.
 * Pure Kotlin on purpose — no media3, no ByteBuffer, no Android — so all the
 * DSP lives here, testable on the JVM (see BeatTrackerTest).
 */
class BeatTracker(private val sampleRate: Int) {

    private val hopFrames = max(1, sampleRate / 100) // ~10 ms
    private val hopMs = hopFrames * 1000f / sampleRate

    // ponytail: one-pole low-pass for the kick band, not a biquad — BiquadFilter
    // has no low-pass and would drag eq types into this dependency-free class. Upgrade if band bleed misleads the tempo pick.
    private val lowPassAlpha: Double = run {
        val rc = 1.0 / (2.0 * PI * KICK_BAND_HZ)
        val dt = 1.0 / sampleRate
        dt / (rc + dt)
    }
    private var lowPassState = 0.0

    private var fullAccumulator = 0.0
    private var lowAccumulator = 0.0
    private var framesInHop = 0
    private var previousFullRms = 0.0
    private var previousLowRms = 0.0

    // Raw per-hop RMS, not flux — onset flux is derived at estimate() time after centered smoothing (see combinedEnvelope()).
    private val fullRms = FloatArray(MAX_ENVELOPE_FRAMES)
    private val lowRms = FloatArray(MAX_ENVELOPE_FRAMES)
    private var envelopeSize = 0

    /** Track position the first envelope frame corresponds to. */
    private var startPositionMs = 0L

    // Coarse one-second energy curve, accumulated for as long as audio keeps
    // arriving (unlike the capped beat envelope) — the cheapest way to know end-energy.
    private val secondEnergy = FloatArray(MAX_PROFILE_SECONDS)
    private var secondAccumulator = 0.0
    private var hopsInSecond = 0
    private var profileSize = 0

    /**
     * True once the beat envelope is full — when analysis is worth running.
     * A frame count, not a duration: integer division caps the ms total below
     * the nominal window at rates like 22050, so a `>= windowMs` test never fires.
     */
    val isAnalysisComplete: Boolean get() = envelopeSize >= MAX_ENVELOPE_FRAMES

    /** Track-time duration accepted so far. Counts audio frames, so playback
     *  speed (e.g. a 6x probe) doesn't change the track time needed. */
    val analyzedMs: Long get() = (envelopeSize * hopMs).toLong()

    /** Track position at which the next accepted sample sits. Call before the
     *  first [accept] of a fresh analysis run. */
    fun beginAt(positionMs: Long) {
        reset()
        startPositionMs = positionMs
    }

    /** One mono frame, normalized to roughly [-1, 1]. */
    fun accept(sample: Float) {
        // Keep consuming past the beat-envelope cap: the energy profile runs for the whole track.
        if (profileSize >= MAX_PROFILE_SECONDS && envelopeSize >= MAX_ENVELOPE_FRAMES) return

        val value = sample.toDouble()
        lowPassState += lowPassAlpha * (value - lowPassState)

        fullAccumulator += value * value
        lowAccumulator += lowPassState * lowPassState
        framesInHop++

        if (framesInHop < hopFrames) return

        val hopFullRms = Math.sqrt(fullAccumulator / framesInHop).toFloat()
        if (envelopeSize < MAX_ENVELOPE_FRAMES) {
            fullRms[envelopeSize] = hopFullRms
            lowRms[envelopeSize] = Math.sqrt(lowAccumulator / framesInHop).toFloat()
            envelopeSize++
        }

        secondAccumulator += hopFullRms
        hopsInSecond++
        if (hopsInSecond >= HOPS_PER_SECOND && profileSize < MAX_PROFILE_SECONDS) {
            secondEnergy[profileSize++] = (secondAccumulator / hopsInSecond).toFloat()
            secondAccumulator = 0.0
            hopsInSecond = 0
        }

        fullAccumulator = 0.0
        lowAccumulator = 0.0
        framesInHop = 0
    }

    /**
     * Energy curve as one unsigned byte per second, scaled so the loudest second
     * is 255; relative shape is all the selector needs, and absolute level is
     * thrown away. Meaningless unless analysis started at 0 ([startPositionMs]).
     */
    fun energyProfile(): ByteArray {
        if (profileSize == 0 || startPositionMs != 0L) return ByteArray(0)

        var peak = 0f
        for (i in 0 until profileSize) if (secondEnergy[i] > peak) peak = secondEnergy[i]
        if (peak <= 0f) return ByteArray(0)

        return ByteArray(profileSize) { i ->
            ((secondEnergy[i] / peak) * 255f).roundToInt().coerceIn(0, 255).toByte()
        }
    }

    fun estimate(): BeatGrid {
        if (envelopeSize < MIN_ENVELOPE_FRAMES) return BeatGrid.NONE

        val envelope = combinedEnvelope()
        val n = envelope.size
        val maxLag = min(MAX_LAG_FRAMES, n / 2)
        if (maxLag <= MIN_LAG_FRAMES) return BeatGrid.NONE

        val mean = envelope.average()
        var zeroLagEnergy = 0.0
        for (value in envelope) {
            val centered = value - mean
            zeroLagEnergy += centered * centered
        }
        zeroLagEnergy /= n
        if (zeroLagEnergy <= 1e-12) return BeatGrid.NONE

        // Biased estimator (divide by n, not overlap count) so confidence is a true coefficient capped at 1.
        val correlation = DoubleArray(maxLag + 1)
        for (lag in MIN_LAG_FRAMES..maxLag) {
            var sum = 0.0
            for (i in 0 until n - lag) {
                sum += (envelope[i] - mean) * (envelope[i + lag] - mean)
            }
            correlation[lag] = sum / n / zeroLagEnergy
        }

        // Enhanced score: adding correlation at 2x/4x the lag separates the true
        // beat from its half-time reading; tempo preference then resolves the octave.
        var bestLag = -1
        var bestScore = Double.NEGATIVE_INFINITY
        for (lag in MIN_LAG_FRAMES..maxLag) {
            var score = correlation[lag]
            if (2 * lag <= maxLag) score += 0.5 * correlation[2 * lag]
            if (4 * lag <= maxLag) score += 0.25 * correlation[4 * lag]
            score *= tempoPreference(lagToBpm(lag.toDouble()))
            if (score > bestScore) {
                bestScore = score
                bestLag = lag
            }
        }
        if (bestLag < 0) return BeatGrid.NONE

        // Sub-frame refinement: integer lags at a 10 ms hop are ~2.7 BPM apart at
        // 128 BPM — parabolic interpolation on the peak fixes the drift for free.
        val refinedLag = refinePeak(correlation, bestLag, maxLag)

        var coarseBpm = lagToBpm(refinedLag)
        // Safety net below the preference weight: never report a tempo outside
        // usable range. Folded before the fine search so it describes the reported tempo.
        while (coarseBpm > 0.0 && coarseBpm < MIN_REPORTED_BPM) coarseBpm *= 2.0
        while (coarseBpm > MAX_REPORTED_BPM) coarseBpm /= 2.0
        if (coarseBpm <= 0.0 || !coarseBpm.isFinite()) return BeatGrid.NONE

        // Joint tempo + phase refinement: 0.15% ACF error compounds to several
        // frames of drift across the window, so optimize what matters — beat alignment.
        val (period, offset) = refineTempoAndPhase(envelope, 60_000.0 / coarseBpm / hopMs)
        val bpm = (60_000.0 / (period * hopMs)).toFloat()
        if (bpm <= 0f || !bpm.isFinite()) return BeatGrid.NONE

        // Periodicity AND transients — either alone generates false positives (see transientDensity()).
        val density = transientDensity()
        val rhythmic = ((density - MIN_TRANSIENT_DENSITY) /
            (FULL_TRANSIENT_DENSITY - MIN_TRANSIENT_DENSITY)).coerceIn(0.0, 1.0)

        return BeatGrid(
            bpm = bpm,
            anchorBeatMs = startPositionMs + offset * hopMs,
            confidence = (correlation[bestLag].coerceIn(0.0, 1.0) * rhythmic).toFloat(),
        )
    }

    fun reset() {
        lowPassState = 0.0
        fullAccumulator = 0.0
        lowAccumulator = 0.0
        framesInHop = 0
        previousFullRms = 0.0
        previousLowRms = 0.0
        envelopeSize = 0
        startPositionMs = 0L
        secondAccumulator = 0.0
        hopsInSecond = 0
        profileSize = 0
    }

    /** Weighted mix of kick and full band, each normalized to unit mean first so
     *  a bass-light track still contributes instead of being drowned out. */
    private fun combinedEnvelope(): FloatArray {
        val n = envelopeSize
        val lowOnsets = onsetStrength(lowRms, n)
        val fullOnsets = onsetStrength(fullRms, n)

        var lowMean = 0.0
        var fullMean = 0.0
        for (i in 0 until n) {
            lowMean += lowOnsets[i]
            fullMean += fullOnsets[i]
        }
        lowMean /= n
        fullMean /= n

        val lowWeight = if (lowMean > 1e-9) LOW_BAND_WEIGHT / lowMean else 0.0
        val fullWeight = if (fullMean > 1e-9) FULL_BAND_WEIGHT / fullMean else 0.0
        if (lowWeight == 0.0 && fullWeight == 0.0) return FloatArray(0)

        return FloatArray(n) { i -> (lowOnsets[i] * lowWeight + fullOnsets[i] * fullWeight).toFloat() }
    }

    /** Onset strength = half-wave-rectified rising energy — what marks note and
     *  drum attacks. */
    private fun onsetStrength(rms: FloatArray, n: Int): FloatArray {
        val onsets = FloatArray(n)
        for (i in 1 until n) {
            onsets[i] = max(0f, rms[i] - rms[i - 1])
        }
        return onsets
    }

    /**
     * Fraction of frames carrying a real transient: a jump of at least [TRANSIENT_JUMP]
     * times the track's mean level within one frame. Scale-invariant autocorrelation
     * can't see depth — a held detuned chord scored 0.91 confidence — so this gates "rhythmic".
     */
    private fun transientDensity(): Double {
        val n = envelopeSize

        // Smoothed *only here*: un-anti-aliased per-hop RMS folds modulation above 50 Hz
        // down into fake transients; never applied to onset strength, where it smears attacks.
        val level = FloatArray(n)
        for (i in 0 until n) {
            var sum = 0.0
            var count = 0
            for (k in -SMOOTH_RADIUS..SMOOTH_RADIUS) {
                val j = i + k
                if (j in 0 until n) {
                    sum += fullRms[j]
                    count++
                }
            }
            level[i] = (sum / count).toFloat()
        }

        var meanLevel = 0.0
        for (value in level) meanLevel += value
        meanLevel /= n
        if (meanLevel <= 1e-9) return 0.0

        var hits = 0
        for (i in 1 until n) {
            if ((level[i] - level[i - 1]) / meanLevel > TRANSIENT_JUMP) hits++
        }
        return hits.toDouble() / n
    }

    /** Grid search over a narrow band around [coarsePeriodFrames] for the
     *  (period, phase) pair whose comb collects the most onset energy per hit.
     *  Returns both, in envelope frames. */
    private fun refineTempoAndPhase(envelope: FloatArray, coarsePeriodFrames: Double): Pair<Double, Int> {
        var bestPeriod = coarsePeriodFrames
        var bestOffset = 0
        var bestScore = -1.0

        for (step in -REFINE_STEPS..REFINE_STEPS) {
            val period = coarsePeriodFrames * (1.0 + step * REFINE_STEP_FRACTION)
            if (period < 1.0) continue
            val offsetRange = max(1, period.roundToInt())
            for (offset in 0 until offsetRange) {
                var energy = 0.0
                var hits = 0
                var position = offset.toDouble()
                while (position < envelope.size) {
                    energy += envelope[position.roundToInt().coerceAtMost(envelope.size - 1)]
                    hits++
                    position += period
                }
                if (hits == 0) continue
                val score = energy / hits
                if (score > bestScore) {
                    bestScore = score
                    bestPeriod = period
                    bestOffset = offset
                }
            }
        }
        return bestPeriod to bestOffset
    }

    private fun refinePeak(correlation: DoubleArray, lag: Int, maxLag: Int): Double {
        if (lag <= MIN_LAG_FRAMES || lag >= maxLag) return lag.toDouble()
        val left = correlation[lag - 1]
        val center = correlation[lag]
        val right = correlation[lag + 1]
        val denominator = left - 2.0 * center + right
        if (abs(denominator) < 1e-12) return lag.toDouble()
        val delta = 0.5 * (left - right) / denominator
        return if (abs(delta) > 1.0) lag.toDouble() else lag + delta
    }

    private fun lagToBpm(lag: Double): Double = 60_000.0 / (lag * hopMs)

    /** Log-normal preference around [PREFERRED_BPM] to disambiguate the octave;
     *  deliberately wide so a genuine 170 BPM track isn't dragged to half time. */
    private fun tempoPreference(bpm: Double): Double {
        if (bpm <= 0.0) return 0.0
        val octaves = ln(bpm / PREFERRED_BPM) / LN_2
        return exp(-0.5 * (octaves / TEMPO_PREFERENCE_OCTAVES) * (octaves / TEMPO_PREFERENCE_OCTAVES))
    }

    companion object {
        private const val KICK_BAND_HZ = 200.0
        private const val LOW_BAND_WEIGHT = 0.65
        private const val FULL_BAND_WEIGHT = 0.35

        /** Centered moving-average radius, in ~10 ms frames, applied to the RMS
         *  series before transient counting only. */
        private const val SMOOTH_RADIUS = 2

        /** A frame counts as a transient when the level jumps by this much of
         *  the track's mean level in one ~10 ms frame. */
        private const val TRANSIENT_JUMP = 0.5

        /** Below this, material has no rhythm; at/above FULL, fully percussive.
         *  Calibrated on BeatTrackerTest fixtures (percussive 0.03-0.11, tonal 0). */
        private const val MIN_TRANSIENT_DENSITY = 0.004
        private const val FULL_TRANSIENT_DENSITY = 0.015

        /** 10 ms hop, so these are lags of 0.3 s .. 1.0 s = 200 .. 60 BPM. */
        private const val MIN_LAG_FRAMES = 30
        private const val MAX_LAG_FRAMES = 100

        /** 10 s of audio before an estimate is worth anything, 25 s cap — also how
         *  long a track must play before it has a grid (far more frames than needed). */
        private const val MIN_ENVELOPE_FRAMES = 1000
        private const val MAX_ENVELOPE_FRAMES = 2500

        private const val HOPS_PER_SECOND = 100

        /** 15 minutes of energy profile, one byte per second when serialized. */
        private const val MAX_PROFILE_SECONDS = 900

        private const val MIN_REPORTED_BPM = 70.0
        private const val MAX_REPORTED_BPM = 190.0

        /** Fine search: +/- 2 % of coarse tempo in 0.05 % steps — wide enough for
         *  the ACF peak's interpolation error, fine enough for a few ms drift. */
        private const val REFINE_STEPS = 40
        private const val REFINE_STEP_FRACTION = 0.0005

        private const val PREFERRED_BPM = 120.0
        private const val TEMPO_PREFERENCE_OCTAVES = 0.9
        private val LN_2 = ln(2.0)
    }
}
