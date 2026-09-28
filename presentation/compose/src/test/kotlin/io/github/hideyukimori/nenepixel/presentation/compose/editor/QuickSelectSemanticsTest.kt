package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.workspace.quickselect.EyedropperState
import io.github.hideyukimori.nenepixel.core.application.workspace.quickselect.QuickSelectItem
import io.github.hideyukimori.nenepixel.core.application.workspace.quickselect.QuickSelection
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportSurface
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportSurfacePoint
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportValueResult
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues.fixture
import io.github.hideyukimori.nenepixel.presentation.compose.R
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

internal class QuickSelectSemanticsTest {
    @Test
    fun `items carry one-based slot tags and descriptions like the palette UI`() {
        val slot = QuickSelectItem.PaletteSlot(paletteIndex(11))

        assertEquals("editor_quick_select_slot_12", QuickSelectSemantics.itemTag(slot))
        assertEquals("editor_quick_select_eyedropper", QuickSelectSemantics.itemTag(QuickSelectItem.Eyedropper))
        assertEquals(QuickSelectLabel(R.string.quick_select_slot, listOf(12)), QuickSelectSemantics.itemLabel(slot))
        assertEquals(
            QuickSelectLabel(R.string.quick_select_eyedropper, emptyList()),
            QuickSelectSemantics.itemLabel(QuickSelectItem.Eyedropper),
        )
    }

    @Test
    fun `the control state names the active slot until the eyedropper is armed`() {
        assertEquals(
            QuickSelectLabel(R.string.quick_select_state_slot, listOf(3)),
            QuickSelectSemantics.controlState(EyedropperState.Idle, paletteIndex(2)),
        )
        assertEquals(
            QuickSelectLabel(R.string.quick_select_state_eyedropper, emptyList()),
            QuickSelectSemantics.controlState(EyedropperState.Armed, paletteIndex(2)),
        )
    }

    @Test
    fun `the control shows a highlighted slot and otherwise the active slot`() {
        val fixture = fixture()
        val surface = ViewportSurface.create(SURFACE_EDGE, SURFACE_EDGE, PIXELS_PER_DP).requiredValue()
        val point = ViewportSurfacePoint.create(CELL_CENTER, CELL_CENTER).requiredValue()
        fixture.controller.callbacks.onSelectPaletteEntry(paletteIndex(1))
        fixture.controller.pointerDown(surface, point)
        fixture.controller.pointerEnd(surface, point)
        fixture.controller.callbacks.onSelectPaletteEntry(paletteIndex(0))
        val quickSelect = fixture.controller.callbacks.quickSelect
        val opened = quickSelect.onOpen().quickSelection

        assertEquals(paletteIndex(0), QuickSelectSemantics.shownSlot(opened, paletteIndex(0)))
        assertNull(QuickSelectSemantics.highlightedSlotNumber(opened))
        val highlighted = quickSelect.onHighlight(QuickSelectItem.PaletteSlot(paletteIndex(1))).quickSelection
        assertEquals(paletteIndex(1), QuickSelectSemantics.shownSlot(highlighted, paletteIndex(0)))
        assertEquals(2, QuickSelectSemantics.highlightedSlotNumber(highlighted))
        val eyedropper = quickSelect.onHighlight(QuickSelectItem.Eyedropper).quickSelection
        assertEquals(paletteIndex(0), QuickSelectSemantics.shownSlot(eyedropper, paletteIndex(0)))
        assertNull(QuickSelectSemantics.highlightedSlotNumber(eyedropper))
        assertEquals(paletteIndex(4), QuickSelectSemantics.shownSlot(QuickSelection.initial, paletteIndex(4)))
    }

    private fun paletteIndex(value: Int): PaletteIndex =
        when (val result = PaletteIndex.create(value)) {
            is DomainValueResult.Created -> result.value
            is DomainValueResult.Rejected -> fail("Palette index fixture was rejected: ${result.rejection}")
        }

    private fun <T> ViewportValueResult<T>.requiredValue(): T =
        when (this) {
            is ViewportValueResult.Created -> value
            is ViewportValueResult.Rejected -> fail("Viewport test value was rejected: $rejection")
        }

    private companion object {
        const val SURFACE_EDGE: Int = 400
        const val PIXELS_PER_DP: Double = 2.0
        const val CELL_CENTER: Double = 50.0
    }
}
