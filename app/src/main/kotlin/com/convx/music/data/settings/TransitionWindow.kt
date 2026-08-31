package com.convx.music.data.settings

import com.convx.music.playback.smart.TransitionPlan

/**
 * The Automix transition the planner has lined up for the track that is playing, exposed to
 * the UI so the seek bar can show where the mix will happen before it arrives.
 *
 * Carries the overlap's real start and end on the outgoing track. It used to carry a single
 * `positionMs` computed as `transitionStart * 1000 / duration` — a 0..1 fraction assigned to a
 * Long, so it truncated to 0 for every track. Nothing read it, which is why that went
 * unnoticed.
 */
data class TransitionWindow(
    val plan: TransitionPlan,
    /** Where the planned overlap opens on the outgoing track, in ms. */
    val startMs: Long = 0,
    /** Where it closes, in ms. */
    val endMs: Long = 0,
    /** The outgoing track's duration, in ms. */
    val totalMs: Long = 0,
) {
    /**
     * The overlap as a span of the track in 0..1, or null when there is nothing sane to draw
     * (unknown duration, or a window that does not actually span anything).
     */
    val fractionRange: ClosedFloatingPointRange<Float>?
        get() {
            if (totalMs <= 0L || endMs <= startMs) return null
            val from = (startMs.toFloat() / totalMs).coerceIn(0f, 1f)
            val to = (endMs.toFloat() / totalMs).coerceIn(0f, 1f)
            return if (to > from) from..to else null
        }
}
