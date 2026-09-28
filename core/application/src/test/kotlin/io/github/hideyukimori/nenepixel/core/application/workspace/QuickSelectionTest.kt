package io.github.hideyukimori.nenepixel.core.application.workspace

import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.paletteIndex
import io.github.hideyukimori.nenepixel.core.application.workspace.quickselect.EyedropperState
import io.github.hideyukimori.nenepixel.core.application.workspace.quickselect.QuickSelectItem
import io.github.hideyukimori.nenepixel.core.application.workspace.quickselect.QuickSelectMenu
import io.github.hideyukimori.nenepixel.core.application.workspace.quickselect.QuickSelection
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class QuickSelectionTest {
    @Test
    fun `initial selection is empty idle and closed`() {
        val initial = QuickSelection.initial

        assertEquals(emptyList<PaletteIndex>(), initial.recent)
        assertEquals(EyedropperState.Idle, initial.eyedropper)
        assertNull(initial.menu)
    }

    @Test
    fun `recording a painted slot moves it to the front without duplicates`() {
        val recorded = painted(0, 1, 2)
        val again = recorded.recordPainted(paletteIndex(0))

        assertEquals(indices(2, 1, 0), recorded.recent)
        assertEquals(indices(0, 2, 1), again.recent)
    }

    @Test
    fun `recording beyond the limit drops the oldest slot`() {
        val full = painted(0, 1, 2, 3, 4, 5, 6, 7)
        val overflowed = full.recordPainted(paletteIndex(8))

        assertEquals(QuickSelection.RECENT_LIMIT, full.recent.size)
        assertEquals(indices(8, 7, 6, 5, 4, 3, 2, 1), overflowed.recent)
    }

    @Test
    fun `re-recording a slot within a full list only reorders it`() {
        val full = painted(0, 1, 2, 3, 4, 5, 6, 7)
        val reordered = full.recordPainted(paletteIndex(0))

        assertEquals(indices(0, 7, 6, 5, 4, 3, 2, 1), reordered.recent)
    }

    @Test
    fun `opening lists recent slots in order followed by the eyedropper with no highlight`() {
        val menu = checkNotNull(painted(3, 5).opened().menu)

        assertEquals(
            listOf(
                QuickSelectItem.PaletteSlot(paletteIndex(5)),
                QuickSelectItem.PaletteSlot(paletteIndex(3)),
                QuickSelectItem.Eyedropper,
            ),
            menu.items,
        )
        assertNull(menu.highlighted)
    }

    @Test
    fun `opening with no recent slots lists only the eyedropper`() {
        val menu = checkNotNull(QuickSelection.initial.opened().menu)

        assertEquals(listOf<QuickSelectItem>(QuickSelectItem.Eyedropper), menu.items)
    }

    @Test
    fun `highlight changes only the highlighted item`() {
        val menu = checkNotNull(painted(2).opened().menu)
        val highlighted = menu.withHighlight(QuickSelectItem.Eyedropper)

        assertEquals(menu.items, highlighted.items)
        assertEquals(QuickSelectItem.Eyedropper, highlighted.highlighted)
        assertEquals(QuickSelectMenu(menu.items, QuickSelectItem.Eyedropper), highlighted)
    }

    @Test
    fun `slots outside the palette are dropped and order is kept`() {
        val trimmed = painted(0, 4, 1, 2).withoutSlotsOutside(2)

        assertEquals(indices(1, 0), trimmed.recent)
    }

    @Test
    fun `closing arming and idling change only their own field`() {
        val opened = painted(1).opened()
        val armed = opened.armed()

        assertEquals(EyedropperState.Armed, armed.eyedropper)
        assertEquals(opened.menu, armed.menu)
        assertNull(armed.closed().menu)
        assertEquals(EyedropperState.Armed, armed.closed().eyedropper)
        assertEquals(opened, armed.idle())
        assertEquals(opened.hashCode(), armed.idle().hashCode())
    }

    private fun painted(vararg values: Int): QuickSelection =
        values.fold(QuickSelection.initial) { selection, value -> selection.recordPainted(paletteIndex(value)) }

    private fun indices(vararg values: Int): List<PaletteIndex> = values.map(::paletteIndex)
}
