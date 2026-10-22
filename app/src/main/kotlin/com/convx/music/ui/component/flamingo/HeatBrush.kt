package com.convx.music.ui.component.flamingo

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor

/** Cool inner edge to hot outer rim; whatever the sweep has passed settles to the sung color. */
private val HeatRamp = listOf(
    Color(0xFF4FE3FF),
    Color(0xFF2F6BFF),
    Color(0xFFFF9A1F),
    Color(0xFFFF2D4D),
)

/**
 * Brush for one character of the line being sung. [percent] is how far the sweep
 * has travelled over the character and [spread] is half the width of the heat band.
 */
internal fun heatBrush(percent: Float, spread: Float, sung: Color, unsung: Color): Brush {
    if (percent >= 1f + spread) return SolidColor(sung)
    if (percent <= -spread) return SolidColor(unsung)
    val start = percent - spread
    val stops = ArrayList<Pair<Float, Color>>(HeatRamp.size + 3)
    stops += 0f to sung
    stops += start.coerceIn(0f, 1f) to sung
    HeatRamp.forEachIndexed { index, color ->
        stops += (start + 2f * spread * (index + 1) / (HeatRamp.size + 1)).coerceIn(0f, 1f) to color
    }
    stops += (percent + spread).coerceIn(0f, 1f) to unsung
    return Brush.horizontalGradient(colorStops = stops.toTypedArray())
}
