/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.playback.dj

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.convx.music.db.MusicDatabase
import com.convx.music.db.entities.SongAnalysisEntity
import com.convx.music.playback.audio.DelayAudioProcessor
import com.convx.music.playback.audio.DjFilterAudioProcessor
import com.convx.music.playback.audio.DjTailAudioProcessor
import com.convx.music.playback.audio.DjMixPlan
import com.convx.music.playback.audio.DjMixPlanner
import com.convx.music.playback.audio.DjMixTier
import com.convx.music.playback.audio.TrackAnalyzerAudioProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/** Everything known about one track for mixing purposes. */
data class TrackAnalysis(
    val grid: BeatGrid,
    val key: MusicalKey? = null,
    /** Per-second energy curve; may be empty or cover only the start of the
     *  track when it came from a probe rather than a full play-through. */
    val energyProfile: ByteArray = ByteArray(0),
) {
    // ByteArray makes equals/hashCode identity-based; these are looked up by media id, never compared.
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)
}

/**
 * What the player surfaces about DJ mixing — reports the tier and style that
 * actually fired rather than what was hoped for, so fallbacks stay visible.
 */
data class DjState(
    val enabled: Boolean = false,
    val currentBpm: Float = 0f,
    val currentKey: String? = null,
    val nextBpm: Float = 0f,
    val nextKey: String? = null,
    val tier: DjMixTier? = null,
    val style: TransitionStyle? = null,
)

/**
 * Owns DJ-mode state and decisions: per-track analysis, persistence, the
 * pre-analysis probe, and when/how to transition. Deliberately does not own
 * players or crossfade mechanics, which MusicService keeps.
 */
@UnstableApi
class DjEngine(
    private val database: MusicDatabase,
    /** Creates a throwaway, non-published player for silent pre-analysis. */
    private val createProbePlayer: () -> ExoPlayer,
) {

    // Own scope: MusicService's is a var replaced on restart, so a captured
    // reference would silently become a cancelled scope.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Mirrors AutoDjMixingEnabledKey, kept fresh by MusicService's collector so
     *  the crossfade path never has to block on a DataStore read. */
    @Volatile
    var enabled: Boolean = false

    /** Mirrors CreativeTransitionsEnabledKey. Off by default, and subordinate to
     *  [enabled]: creative effects must not fire when DJ mode is off. */
    @Volatile
    private var creativePref: Boolean = false

    var creativeEnabled: Boolean
        get() = creativePref && enabled
        set(value) { creativePref = value }

    private val analyses = java.util.concurrent.ConcurrentHashMap<String, TrackAnalysis>()
    private val analyzers = java.util.concurrent.ConcurrentHashMap<Player, TrackAnalyzerAudioProcessor>()
    private val filters = java.util.concurrent.ConcurrentHashMap<Player, DjFilterAudioProcessor>()
    private val tails = java.util.concurrent.ConcurrentHashMap<Player, DjTailAudioProcessor>()
    /** Which track each player's analyzer is currently accumulating for.
     *  Per-player because the crossfade swaps which ExoPlayer is "the" player. */
    private val analyzingMediaId = java.util.concurrent.ConcurrentHashMap<Player, String>()
    private val delays = java.util.concurrent.ConcurrentHashMap<Player, DelayAudioProcessor>()

    /** Most-recent-first, so the selector can avoid repeating itself. */
    private val recentStyles = ArrayDeque<TransitionStyle>()
    private var tailJob: Job? = null

    /** Style chosen for the transition in progress. */
    var activeStyle: TransitionStyle = TransitionStyle.TRANSPARENT
        private set

    private val _state = MutableStateFlow(DjState())
    val state: StateFlow<DjState> = _state.asStateFlow()

    private var probeJob: Job? = null
    private var probePlayer: ExoPlayer? = null

    /** Plan for the transition in progress, so MusicService knows whether it
     *  has speed to unwind when the fade completes. */
    var activePlan: DjMixPlan? = null
        private set

    fun registerPlayer(
        player: Player,
        analyzer: TrackAnalyzerAudioProcessor,
        filter: DjFilterAudioProcessor,
        tail: DjTailAudioProcessor,
        delay: DelayAudioProcessor,
    ) {
        analyzers[player] = analyzer
        filters[player] = filter
        tails[player] = tail
        delays[player] = delay
    }

    fun unregisterPlayer(player: Player) {
        analyzers.remove(player)
        filters.remove(player)
        tails.remove(player)
        delays.remove(player)
        analyzingMediaId.remove(player)
    }

    fun filterFor(player: Player?): DjFilterAudioProcessor? = player?.let { filters[it] }

    /**
     * Bass swap: the outgoing track loses its low end while the incoming one
     * gains it, so they never fight over the same frequencies. [progress] runs
     * 0..1 across the crossfade; targets are stepped, the processor glides.
     */
    fun driveFilterSweep(outgoing: Player?, incoming: Player?, progress: Float) {
        val plan = activePlan ?: return
        if (plan.tier != DjMixTier.FULL_DJ) return
        val eased = progress.coerceIn(0f, 1f)

        filterFor(outgoing)?.lowPassHz = logLerp(
            DjFilterAudioProcessor.OPEN_LOW_PASS_HZ,
            DjFilterAudioProcessor.BASS_KILL_LOW_PASS_HZ,
            eased,
        )
        filterFor(incoming)?.highPassHz = logLerp(
            DjFilterAudioProcessor.BASS_KILL_HIGH_PASS_HZ,
            DjFilterAudioProcessor.OPEN_HIGH_PASS_HZ,
            eased,
        )
    }

    /** Puts the incoming player's filter where the sweep starts, before it
     *  makes a sound, so the first thing heard is already bass-cut. */
    fun armIncomingFilter(incoming: Player?) {
        val filter = filterFor(incoming) ?: return
        if (activePlan?.tier != DjMixTier.FULL_DJ) {
            filter.open()
            return
        }
        filter.highPassHz = DjFilterAudioProcessor.BASS_KILL_HIGH_PASS_HZ
        filter.lowPassHz = DjFilterAudioProcessor.OPEN_LOW_PASS_HZ
        filter.snapToTargets()
    }

    /** Filters must not outlive the transition; afterward the incoming player has to be full-range. */
    fun openFilters(vararg players: Player?) {
        players.forEach { filterFor(it)?.open() }
    }

    private fun logLerp(from: Float, to: Float, t: Float): Float =
        Math.exp(
            Math.log(from.toDouble()) + (Math.log(to.toDouble()) - Math.log(from.toDouble())) * t
        ).toFloat()

    /** A player moved to a new track: flush the old accumulation, then restart analysis. */
    fun onTrackChanged(player: Player, newMediaId: String?) {
        analyzingMediaId[player]?.let { captureEnergyProfile(player, it) }
        analyzers[player]?.resetForTrack()
        if (newMediaId != null) {
            analyzingMediaId[player] = newMediaId
        } else {
            analyzingMediaId.remove(player)
        }
        publishState(newMediaId, nextMediaId = null)
    }

    /** Refreshes the published readout. [nextMediaId] is null when the upcoming
     *  track isn't known or isn't analyzed yet. */
    fun publishState(currentMediaId: String?, nextMediaId: String?) {
        val current = analysisFor(currentMediaId)
        val next = analysisFor(nextMediaId)
        _state.value = DjState(
            enabled = enabled,
            currentBpm = current?.grid?.bpm ?: 0f,
            currentKey = current?.key?.takeIf { it.confidence >= MusicalKey.MIN_USABLE_CONFIDENCE }?.camelot,
            nextBpm = next?.grid?.bpm ?: 0f,
            nextKey = next?.key?.takeIf { it.confidence >= MusicalKey.MIN_USABLE_CONFIDENCE }?.camelot,
            tier = activePlan?.tier,
            style = activePlan?.let { activeStyle },
        )
    }

    /** Fired from the audio renderer thread once a track has had enough audio to
     *  analyze; ownership is looked up at call time, not capture time. */
    fun onAnalysisReady(analyzer: TrackAnalyzerAudioProcessor) {
        scope.launch {
            val owner = analyzers.entries.find { it.value === analyzer }?.key ?: return@launch
            val mediaId = owner.currentMediaItem?.mediaId ?: return@launch
            // Off main and audio threads: the tempo search is heavy enough to drop a frame.
            val (grid, key) = withContext(Dispatchers.Default) { analyzer.computeAnalysis() }
            if (grid.bpm <= 0f && key.pitchClass < 0) return@launch
            val analysis = TrackAnalysis(grid, key.takeIf { it.pitchClass >= 0 }, analyzer.energyProfile)
            analyses[mediaId] = analysis
            persist(mediaId, analysis)
            // Seeds the map for a player that never fired a transition event:
            // the crossfade's secondary player starts before it is listened to.
            analyzingMediaId[owner] = mediaId
            if (owner.currentMediaItem?.mediaId == mediaId) publishState(mediaId, null)
            Timber.tag(TAG).d(
                "ANALYSED $mediaId | %.1f BPM conf=%.2f | anchor=%.0fms | key=%s | profile=%ds"
                    .format(
                        grid.bpm,
                        grid.confidence,
                        grid.anchorBeatMs,
                        analysis.key?.camelot ?: "?",
                        analysis.energyProfile.size,
                    )
            )
        }
    }

    /** Re-read the energy profile for a track that has been playing: the grid is
     *  final after the analysis window, but the outro only exists once reached. */
    fun captureEnergyProfile(player: Player, mediaId: String) {
        val analyzer = analyzers[player] ?: return
        val profile = analyzer.energyProfile
        if (profile.isEmpty()) return
        val existing = analyses[mediaId] ?: return
        if (profile.size <= existing.energyProfile.size) return

        val updated = existing.copy(energyProfile = profile)
        analyses[mediaId] = updated
        persist(mediaId, updated)
    }

    /** Loads cached analysis for upcoming queue items so a known track needs no probe. */
    fun hydrate(mediaIds: List<String>) {
        val missing = mediaIds.filter { it.isNotEmpty() && !analyses.containsKey(it) }
        if (missing.isEmpty()) return
        scope.launch {
            runCatching {
                database.songAnalyses(missing, SongAnalysisEntity.CURRENT_ANALYSIS_VERSION)
            }.onSuccess { rows ->
                for (row in rows) {
                    analyses[row.id] = TrackAnalysis(
                        grid = BeatGrid(row.bpm, row.anchorBeatMs, row.bpmConfidence),
                        key = if (row.keyIndex >= 0) {
                            MusicalKey(row.keyIndex, row.keyIsMinor, row.keyConfidence)
                        } else {
                            null
                        },
                        energyProfile = row.energyProfile,
                    )
                }
                if (rows.isNotEmpty()) Timber.tag(TAG).d("Hydrated ${rows.size} cached analyses")
            }.onFailure { Timber.tag(TAG).w(it, "Analysis hydrate failed") }
        }
    }

    private fun persist(mediaId: String, analysis: TrackAnalysis) {
        database.query {
            runCatching {
                upsert(
                    SongAnalysisEntity(
                        id = mediaId,
                        bpm = analysis.grid.bpm,
                        anchorBeatMs = analysis.grid.anchorBeatMs,
                        bpmConfidence = analysis.grid.confidence,
                        keyIndex = analysis.key?.pitchClass ?: -1,
                        keyIsMinor = analysis.key?.isMinor == true,
                        keyConfidence = analysis.key?.confidence ?: 0f,
                        energyProfile = analysis.energyProfile,
                    )
                )
            }.onFailure { Timber.tag(TAG).w(it, "Analysis persist failed for $mediaId") }
        }
    }

    fun hasAnalysis(mediaId: String?): Boolean = mediaId != null && analyses.containsKey(mediaId)

    fun analysisFor(mediaId: String?): TrackAnalysis? = mediaId?.let { analyses[it] }

    /**
     * Approximates a chorus/drop cut point for [TransitionStyle.ECHO_FREEZE]:
     * the loudest moment mid-track (25–70%), a cheap stand-in for structure the
     * on-device analyzer can't detect. Null when there isn't enough profile.
     */
    fun echoPointMs(mediaId: String?): Long? {
        val profile = analysisFor(mediaId)?.energyProfile ?: return null
        if (profile.size < MIN_ECHO_POINT_PROFILE_SECONDS) return null
        val start = (profile.size * ECHO_POINT_WINDOW_START).toInt()
        val end = (profile.size * ECHO_POINT_WINDOW_END).toInt().coerceAtLeast(start + 1)
        var bestIndex = start
        var bestValue = -1
        for (i in start until end) {
            val value = profile[i].toInt() and 0xFF
            if (value > bestValue) {
                bestValue = value
                bestIndex = i
            }
        }
        return bestIndex * 1000L
    }

    /**
     * Silently pre-plays [item] through a throwaway muted player at [PROBE_SPEED]x
     * so the analyzer can cache a grid before the crossfade needs one. Rides the
     * normal ResolvingDataSource chain so resolution and cache work as in real
     * playback. [guardMediaId] is the track playing when scheduled; if it changed,
     * the transition it was for is no longer happening.
     */
    fun schedulePreAnalysis(
        item: MediaItem,
        afterMs: Long,
        guardMediaId: String?,
        currentMediaId: () -> String?,
    ) {
        if (afterMs <= 0L || hasAnalysis(item.mediaId)) return
        probeJob?.cancel()
        releaseProbe()
        probeJob = scope.launch {
            delay(afterMs)
            if (!isActive || currentMediaId() != guardMediaId) return@launch
            if (hasAnalysis(item.mediaId)) {
                Timber.tag(TAG).d("PROBE skipped, ${item.mediaId} already analysed")
                return@launch
            }
            runCatching {
                val probe = createProbePlayer()
                probePlayer = probe
                probe.setMediaItem(item)
                probe.volume = 0f
                probe.playbackParameters = PlaybackParameters(PROBE_SPEED, 1f)
                probe.prepare()
                probe.playWhenReady = true
                Timber.tag(TAG).d("Pre-analysing ${item.mediaId} at ${PROBE_SPEED}x")
            }.onFailure {
                Timber.tag(TAG).w(it, "DJ pre-analysis probe failed to start")
                releaseProbe()
            }
        }
    }

    fun releaseProbe() {
        probeJob?.cancel()
        probeJob = null
        probePlayer?.let { probe ->
            unregisterPlayer(probe)
            runCatching {
                probe.stop()
                probe.clearMediaItems()
                probe.release()
            }
        }
        probePlayer = null
    }

    /** Builds the plan for a transition, or null when DJ mixing is off. */
    fun planTransition(
        outgoingMediaId: String?,
        incomingMediaId: String?,
        outgoingDurationMs: Long,
    ): DjMixPlan? {
        if (!enabled) {
            activePlan = null
            return null
        }
        val outgoing = analysisFor(outgoingMediaId)
        val incoming = analysisFor(incomingMediaId)
        val plan = DjMixPlanner.plan(outgoing?.grid, incoming?.grid, outgoing?.key, incoming?.key)
        activePlan = plan
        activeStyle = TransitionSelector.select(
            tier = plan.tier,
            outgoing = outgoing,
            incoming = incoming,
            outgoingDurationMs = outgoingDurationMs,
            creativeEnabled = creativeEnabled,
            recentStyles = recentStyles.toList(),
        )
        Timber.tag(TAG).d(
            "PLAN ${outgoingMediaId ?: "?"} -> ${incomingMediaId ?: "?"} | ${plan.tier} | $activeStyle | " +
                "out=%.1fBPM/%s in=%.1fBPM/%s | speed=%.4f pitch=%+d st | creative=%b"
                    .format(
                        outgoing?.grid?.bpm ?: 0f,
                        outgoing?.key?.camelot ?: "?",
                        incoming?.grid?.bpm ?: 0f,
                        incoming?.key?.camelot ?: "?",
                        plan.incomingSpeedAdjustment,
                        plan.incomingPitchShiftSemitones,
                        creativeEnabled,
                    )
        )
        publishState(outgoingMediaId, incomingMediaId)
        return plan
    }

    /** Arms whatever the selector chose on the outgoing player for the length of
     *  the crossfade — effects act on the track that is *leaving*. */
    fun startTailEffect(outgoing: Player?, crossfadeMs: Long) {
        tailJob?.cancel()
        tailJob = null
        if (!creativeEnabled || outgoing == null) return

        // No measured beat, no effect; a guessed tempo would loop/freeze on a grid unrelated to the music.
        val beatMs = analysisFor(outgoing.currentMediaItem?.mediaId)?.grid?.beatMs
            ?.takeIf { it > 0f } ?: return

        when (activeStyle) {
            TransitionStyle.TRANSPARENT -> Unit

            TransitionStyle.TAPE_STOP -> {
                Timber.tag(TAG).d("Tape stop over ${(beatMs * TAPE_STOP_BEATS).toLong()}ms")
                tails[outgoing]?.startTapeStop((beatMs * TAPE_STOP_BEATS).toLong())
            }

            TransitionStyle.ECHO_FREEZE -> {
                val echo = delays[outgoing] ?: return
                // Eighth-note slap-back on the outgoing tempo; a beat-unrelated delay reads as washy echo.
                echo.setDelayMs((beatMs / 2f).toLong().coerceAtLeast(1L))
                Timber.tag(TAG).d("Echo freeze at ${(crossfadeMs * ECHO_FREEZE_AT).toLong()}ms into the fade")
                // Feed first — freezing an empty delay line is silence — then
                // freeze late, since freezing kills the dry signal outright.
                echo.frozen = false
                echo.wetMix = ECHO_WET_MIX
                tailJob = scope.launch {
                    delay((crossfadeMs * ECHO_FREEZE_AT).toLong().coerceAtLeast(beatMs.toLong()))
                    echo.frozen = true
                }
            }
        }
    }

    /** Effects must never outlive their transition. */
    fun stopTailEffects(vararg players: Player?) {
        if (activeStyle != TransitionStyle.TRANSPARENT) {
            Timber.tag(TAG).d("EFFECT END $activeStyle")
        }
        tailJob?.cancel()
        tailJob = null
        players.filterNotNull().forEach { player ->
            tails[player]?.stop()
            delays[player]?.let {
                it.frozen = false
                it.wetMix = 0f
            }
        }
    }

    fun clearActivePlan() {
        // Recorded even when transparent, so a stale creative move never sits at the head.
        recentStyles.addFirst(activeStyle)
        while (recentStyles.size > STYLE_HISTORY) recentStyles.removeLast()
        activePlan = null
        activeStyle = TransitionStyle.TRANSPARENT
    }

    /** Keeps the readout honest when the feature is toggled at runtime. */
    fun refreshState(currentMediaId: String?) = publishState(currentMediaId, null)

    val activePlanNeedsSpeedReset: Boolean
        get() = activePlan?.let { it.tier != DjMixTier.PLAIN_CROSSFADE } == true

    /**
     * Where the crossfade should start, in track time. Two refinements over a
     * flat `duration - crossfadeMs`: mix out from a nearby outro drop, and snap
     * to a phrase boundary. Falls back to the flat point when DJ mixing is off
     * or the track has no usable grid.
     */
    fun transitionPointMs(mediaId: String?, durationMs: Long, crossfadeMs: Long): Long {
        val flat = durationMs - crossfadeMs
        if (!enabled || durationMs <= 0L) return flat

        val analysis = analysisFor(mediaId) ?: return flat
        val grid = analysis.grid
        if (grid.bpm <= 0f || grid.confidence <= 0f) return flat

        var target = flat
        val profile = analysis.energyProfile
        if (EnergyProfile.coversOutro(profile, durationMs)) {
            val dropSecond = EnergyProfile.outroDropSecond(profile)
            if (dropSecond >= 0) {
                val dropMs = dropSecond * 1000L
                // Only honour a drop genuinely near the end; an early breakdown is not an invitation to cut.
                if (dropMs in (flat - MAX_OUTRO_PULL_MS)..(flat + crossfadeMs)) target = dropMs
            }
        }

        // Snap DOWN, never to the nearest boundary: rounding up can push the mix
        // later than `duration - crossfadeMs`, and the song would end mid-fade.
        val snapped = grid.phraseAtOrBeforeMs(target).toLong()
        // flat already leaves crossfadeMs of tail, but a short crossfade could
        // otherwise leave almost nothing after it; MIN_TAIL_MS is an extra floor.
        val maxResult = minOf(flat, durationMs - MIN_TAIL_MS).coerceAtLeast(0L)
        val minResult = (flat - MAX_OUTRO_PULL_MS).coerceAtMost(maxResult)
        val result = snapped.coerceIn(minResult, maxResult)
        Timber.tag(TAG).d(
            "MIXOUT $mediaId | duration=${durationMs}ms flat=${flat}ms " +
                "outroDrop=${if (target != flat) "${target}ms" else "none"} -> ${result}ms " +
                "(${flat - result}ms early)"
        )
        return result
    }

    /**
     * Where the incoming track should start, in its own track time — it must land
     * *on the incoming grid*, else the grids stay offset by its phase. Costs less
     * than one beat and stays inside buffered audio. Deliberately skips no quiet
     * intro: seeking a fresh player into an unbuffered range risks an arrival
     * that never comes, gated on the region already being buffered.
     */
    fun incomingStartMs(mediaId: String?): Long {
        if (!enabled) return 0L
        val analysis = analysisFor(mediaId) ?: return 0L
        val grid = analysis.grid
        if (grid.bpm <= 0f || grid.confidence <= 0f) return 0L
        return grid.beatAtOrAfter(0L).toLong().coerceAtLeast(0L)
    }

    /** Cancels in-flight work and drops all state. Cancels the scope's children,
     *  not the scope itself, so the engine still works if the service is recreated. */
    fun release() {
        releaseProbe()
        scope.coroutineContext.cancelChildren()
        analyzers.clear()
        filters.clear()
        tails.clear()
        delays.clear()
        analyzingMediaId.clear()
        recentStyles.clear()
        analyses.clear()
        activePlan = null
    }

    companion object {
        private const val TAG = "DjEngine"

        /** [echoPointMs] needs this much profile before "loudest second" means anything. */
        private const val MIN_ECHO_POINT_PROFILE_SECONDS = 40
        private const val ECHO_POINT_WINDOW_START = 0.25
        private const val ECHO_POINT_WINDOW_END = 0.70

        /** Probe playback rate — ample lead time without heavy background decode. */
        const val PROBE_SPEED = 4f

        /** How far ahead of the crossfade to start pre-analysing — slack for
         *  buffering beyond the probe's own decode time. */
        const val PROBE_LEAD_MS = 20_000L

        /** How far earlier than the flat point an outro drop may pull the mix. */
        private const val MAX_OUTRO_PULL_MS = 25_000L

        /** Always leave this much track after the transition point. */
        private const val MIN_TAIL_MS = 1_000L

        private const val TAPE_STOP_BEATS = 1.5f
        private const val ECHO_WET_MIX = 0.55f

        /** Fraction of the crossfade at which the echo stops taking new input — late,
         *  so the outgoing track is already mostly faded when its dry signal goes. */
        private const val ECHO_FREEZE_AT = 0.6f

        /** Long enough to serve the selector's effect cooldown. */
        private const val STYLE_HISTORY = 6
    }
}
