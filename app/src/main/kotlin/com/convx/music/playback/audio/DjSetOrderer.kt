/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.playback.audio

import com.convx.music.playback.dj.BeatGrid
import com.convx.music.playback.dj.MusicalKey
import kotlin.math.abs

/**
 * Deterministic set ordering: given a pool of songs (with whatever cached
 * BeatGrid/MusicalKey analysis exists — most won't have any until they've
 * played once), arrange them so consecutive pairs mix as well as possible.
 *
 * No AI involved on purpose — [DjMixPlanner] already knows how to score a
 * pair (harmonic/tempo compatibility, octave-aware), so this just runs a
 * greedy nearest-neighbor chain over that exact scoring. "Well ordered" here
 * means the same thing as "mixes well" at playback time, because it's the
 * same tiering.
 */
object DjSetOrderer {

    /** [analyses] maps song id -> cached (BeatGrid, MusicalKey), absent for
     *  anything never analyzed — those just rank lowest as a mix partner and
     *  fall back to a plain crossfade at playback time, same as today.
     *
     *  [candidateIds]'s first element anchors the chain (callers put a seed
     *  song there to open the set on it); the rest are reordered greedily. */
    fun order(candidateIds: List<String>, analyses: Map<String, Pair<BeatGrid, MusicalKey>>): List<String> {
        if (candidateIds.size <= 2) return candidateIds

        val remaining = candidateIds.toMutableList()
        val ordered = ArrayList<String>(candidateIds.size)
        ordered += remaining.removeAt(0)

        while (remaining.isNotEmpty()) {
            val (lastGrid, lastKey) = analyses[ordered.last()] ?: (BeatGrid.NONE to MusicalKey.NONE)
            var bestIndex = 0
            var bestScore = Double.NEGATIVE_INFINITY
            for (index in remaining.indices) {
                val (grid, key) = analyses[remaining[index]] ?: (BeatGrid.NONE to MusicalKey.NONE)
                val plan = DjMixPlanner.plan(lastGrid, grid, lastKey, key)
                val bpmDelta = if (lastGrid.bpm > 0f && grid.bpm > 0f) abs(lastGrid.bpm - grid.bpm) else 1000f
                // DjMixTier is declared FULL_DJ, SMART_CROSSFADE, PLAIN_CROSSFADE in
                // that order, so a lower ordinal is the better tier — weight it
                // heavily, then use bpm closeness only to break ties within a tier.
                val score = -plan.tier.ordinal * 10_000.0 - bpmDelta
                if (score > bestScore) {
                    bestScore = score
                    bestIndex = index
                }
            }
            ordered += remaining.removeAt(bestIndex)
        }
        return ordered
    }
}
