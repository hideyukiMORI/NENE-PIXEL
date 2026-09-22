package io.github.hideyukimori.nenepixel.core.application.workspace

/** Session-only floating window that shows the committed document at an exact scale (ADR 0026). */
public class ActualSizeWindow private constructor(
    public val visible: Boolean,
    public val scale: ActualSizeScale,
    public val anchor: WindowAnchor,
) {
    public fun toggled(): ActualSizeWindow = ActualSizeWindow(!visible, scale, anchor)

    public fun withScale(scale: ActualSizeScale): ActualSizeWindow = ActualSizeWindow(visible, scale, anchor)

    public fun withAnchor(anchor: WindowAnchor): ActualSizeWindow = ActualSizeWindow(visible, scale, anchor)

    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is ActualSizeWindow &&
                    visible == other.visible &&
                    scale == other.scale &&
                    anchor == other.anchor
            )

    override fun hashCode(): Int =
        (visible.hashCode() * HASH_MULTIPLIER + scale.hashCode()) * HASH_MULTIPLIER + anchor.hashCode()

    override fun toString(): String = "ActualSizeWindow(visible=$visible, scale=$scale, anchor=$anchor)"

    public companion object {
        /** Hidden, quadrupled, anchored at the physical top-right corner of the work area. */
        public val initial: ActualSizeWindow =
            ActualSizeWindow(false, ActualSizeScale.X4, WindowAnchor.topRight)

        private const val HASH_MULTIPLIER: Int = 31
    }
}
