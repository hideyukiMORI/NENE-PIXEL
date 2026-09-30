package io.github.hideyukimori.nenepixel.core.application.workspace.underlay

/**
 * The alpha the reference underlay is drawn with, an integer from 26 to 255 (10% to 100%).
 * Hiding is the visibility, not an opacity of zero (ADR 0032).
 */
public class UnderlayOpacity private constructor(
    public val alpha: Int,
) {
    override fun equals(other: Any?): Boolean = this === other || (other is UnderlayOpacity && alpha == other.alpha)

    override fun hashCode(): Int = alpha

    override fun toString(): String = "UnderlayOpacity(alpha=$alpha)"

    public companion object {
        public val MIN: UnderlayOpacity = UnderlayOpacity(MINIMUM_ALPHA)
        public val MAX: UnderlayOpacity = UnderlayOpacity(MAXIMUM_ALPHA)
        public val DEFAULT: UnderlayOpacity = UnderlayOpacity(DEFAULT_ALPHA)

        public fun create(alpha: Int): UnderlayOpacity = UnderlayOpacity(alpha.coerceIn(MINIMUM_ALPHA, MAXIMUM_ALPHA))

        private const val MINIMUM_ALPHA: Int = 26
        private const val MAXIMUM_ALPHA: Int = 255
        private const val DEFAULT_ALPHA: Int = 128
    }
}
