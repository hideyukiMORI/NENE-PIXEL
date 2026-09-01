package io.github.hideyukimori.nenepixel.core.application.workspace.viewport

internal object ViewportNumbers {
    const val MIN_ZOOM: Double = 1.0
    const val MAX_ZOOM: Double = 64.0
    const val GRID_THRESHOLD_DP: Double = 8.0

    fun canonicalizeZero(value: Double): Double = if (value == 0.0) 0.0 else value

    fun areFinite(vararg values: Double): Boolean = values.all { value -> value.isFinite() }
}
