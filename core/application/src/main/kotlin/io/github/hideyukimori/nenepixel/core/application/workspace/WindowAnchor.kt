package io.github.hideyukimori.nenepixel.core.application.workspace

/**
 * Normalized placement of a floating window inside the free space of the editor work area.
 *
 * The axes are physical and independent of the text reading direction, like `EditorControlEdge`:
 * `0.0` puts the window against the left or top edge and `1.0` against the right or bottom edge, so
 * every created value keeps the window fully inside the work area (ADR 0026).
 */
public class WindowAnchor private constructor(
    public val x: Double,
    public val y: Double,
) {
    override fun equals(other: Any?): Boolean =
        this === other || (other is WindowAnchor && x == other.x && y == other.y)

    override fun hashCode(): Int = x.hashCode() * HASH_MULTIPLIER + y.hashCode()

    override fun toString(): String = "WindowAnchor(x=$x, y=$y)"

    public companion object {
        /** Physical right edge, top row: the initial placement of the actual-size window. */
        public val topRight: WindowAnchor = WindowAnchor(MAXIMUM, MINIMUM)

        public fun create(
            x: Double,
            y: Double,
        ): WindowAnchor = WindowAnchor(clamped(x), clamped(y))

        private fun clamped(value: Double): Double =
            when {
                value.isNaN() -> MINIMUM
                value <= MINIMUM -> MINIMUM
                value >= MAXIMUM -> MAXIMUM
                else -> value
            }

        private const val MINIMUM: Double = 0.0
        private const val MAXIMUM: Double = 1.0
        private const val HASH_MULTIPLIER: Int = 31
    }
}
