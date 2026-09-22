package io.github.hideyukimori.nenepixel.core.application.workspace

/** Closed device-pixel multiple shown by the actual-size window (ADR 0026). */
public enum class ActualSizeScale(
    public val devicePixelsPerCell: Int,
) {
    X1(devicePixelsPerCell = 1),
    X2(devicePixelsPerCell = 2),
    X4(devicePixelsPerCell = 4),
    X8(devicePixelsPerCell = 8),
    X16(devicePixelsPerCell = 16),
    X32(devicePixelsPerCell = 32),
    ;

    /** The next scale in declaration order, wrapping after the largest one. */
    public fun next(): ActualSizeScale = entries[(ordinal + 1) % entries.size]
}
