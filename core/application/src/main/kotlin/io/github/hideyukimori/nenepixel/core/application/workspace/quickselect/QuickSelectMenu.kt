package io.github.hideyukimori.nenepixel.core.application.workspace.quickselect

/** Open quick-select menu: the items fixed when it opened and the highlighted item, if any (ADR 0029). */
public class QuickSelectMenu internal constructor(
    items: List<QuickSelectItem>,
    public val highlighted: QuickSelectItem?,
) {
    public val items: List<QuickSelectItem> = items.toList()

    internal fun withHighlight(item: QuickSelectItem?): QuickSelectMenu = QuickSelectMenu(items, item)

    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is QuickSelectMenu &&
                    items == other.items &&
                    highlighted == other.highlighted
            )

    override fun hashCode(): Int = items.hashCode() * HASH_MULTIPLIER + (highlighted?.hashCode() ?: 0)

    override fun toString(): String = "QuickSelectMenu(items=$items, highlighted=$highlighted)"

    private companion object {
        private const val HASH_MULTIPLIER: Int = 31
    }
}
