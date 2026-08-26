package com.convx.music.data.settings

import com.convx.music.playback.smart.TransitionPlan

/**
 * The plan a crossfade is currently executing, exposed to the UI so it
 * can show the blend style and progress.
 */
data class TransitionWindow(
    val plan: TransitionPlan,
    val positionMs: Long = 0,
    val totalMs: Long = 0,
)
