package io.github.hideyukimori.nenepixel.core.application.workspace.quickselect

import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex

/**
 * Session-only quick-select state (ADR 0029): the slots recently used for drawing, the eyedropper
 * state and the open menu. Every value other than [initial] is derived by a named function.
 */
public class QuickSelection private constructor(
    recent: List<PaletteIndex>,
    public val eyedropper: EyedropperState,
    public val menu: QuickSelectMenu?,
) {
    /** Distinct slots used for drawing, most recent first, at most [RECENT_LIMIT]. */
    public val recent: List<PaletteIndex> = recent.toList()

    /** Moves [index] to the front of [recent]; the entry beyond [RECENT_LIMIT] falls off. */
    internal fun recordPainted(index: PaletteIndex): QuickSelection =
        QuickSelection(
            (listOf(index) + recent.filter { it != index }).take(RECENT_LIMIT),
            eyedropper,
            menu,
        )

    /** Drops [recent] slots that do not exist in a palette of [entryCount] entries. */
    internal fun withoutSlotsOutside(entryCount: Int): QuickSelection =
        QuickSelection(recent.filter { it.value < entryCount }, eyedropper, menu)

    /** Opens the menu with [recent] in order followed by the eyedropper, nothing highlighted. */
    internal fun opened(): QuickSelection =
        QuickSelection(
            recent,
            eyedropper,
            QuickSelectMenu(recent.map(QuickSelectItem::PaletteSlot) + QuickSelectItem.Eyedropper, null),
        )

    internal fun closed(): QuickSelection = QuickSelection(recent, eyedropper, null)

    internal fun armed(): QuickSelection = QuickSelection(recent, EyedropperState.Armed, menu)

    internal fun idle(): QuickSelection = QuickSelection(recent, EyedropperState.Idle, menu)

    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is QuickSelection &&
                    recent == other.recent &&
                    eyedropper == other.eyedropper &&
                    menu == other.menu
            )

    override fun hashCode(): Int =
        (recent.hashCode() * HASH_MULTIPLIER + eyedropper.hashCode()) * HASH_MULTIPLIER + (menu?.hashCode() ?: 0)

    override fun toString(): String = "QuickSelection(recent=$recent, eyedropper=$eyedropper, menu=$menu)"

    public companion object {
        public const val RECENT_LIMIT: Int = 8

        /** No recent slots, eyedropper idle, menu closed. */
        public val initial: QuickSelection = QuickSelection(emptyList(), EyedropperState.Idle, null)

        private const val HASH_MULTIPLIER: Int = 31
    }
}
