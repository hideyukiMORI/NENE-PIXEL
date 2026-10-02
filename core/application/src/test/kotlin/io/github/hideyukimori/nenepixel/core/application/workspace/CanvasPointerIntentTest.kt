package io.github.hideyukimori.nenepixel.core.application.workspace

import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.canvas
import io.github.hideyukimori.nenepixel.core.application.workspace.quickselect.CanvasPointerIntent
import io.github.hideyukimori.nenepixel.core.application.workspace.quickselect.EyedropperState
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceImage
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceImageResult
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceUnderlay
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.UnderlayInteraction
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.UnderlayVisibility
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** The canvas pointer intent derivation of ADR 0029 and ADR 0032. */
internal class CanvasPointerIntentTest {
    private val initial = WorkspaceState.create(canvas(4, 4))
    private val underlay = ReferenceUnderlay.placed(image(8, 4), canvas(4, 4))
    private val armed = initial.withQuickSelection(initial.quickSelection.armed())

    @Test
    fun `a shown adjusting underlay adjusts`() {
        val adjusting = initial.withUnderlay(underlay.adjusting())

        assertEquals(UnderlayVisibility.Shown, adjusting.underlay?.visibility)
        assertEquals(UnderlayInteraction.Adjusting, adjusting.underlay?.interaction)
        assertEquals(CanvasPointerIntent.AdjustUnderlay, adjusting.canvasPointerIntent)
    }

    @Test
    fun `a resting or hidden underlay draws`() {
        val resting = initial.withUnderlay(underlay)
        val hidden = initial.withUnderlay(underlay.adjusting().toggledVisibility().adjusting())

        assertEquals(CanvasPointerIntent.Draw, resting.canvasPointerIntent)
        assertEquals(UnderlayVisibility.Hidden, hidden.underlay?.visibility)
        assertEquals(CanvasPointerIntent.Draw, hidden.canvasPointerIntent)
        assertEquals(CanvasPointerIntent.Draw, initial.canvasPointerIntent)
    }

    @Test
    fun `adjusting wins over an armed eyedropper, which stays armed and picks after resting`() {
        val adjusting = armed.withUnderlay(underlay.adjusting())
        val rested = adjusting.withUnderlay(underlay.adjusting().rested())

        assertEquals(CanvasPointerIntent.AdjustUnderlay, adjusting.canvasPointerIntent)
        assertEquals(EyedropperState.Armed, adjusting.quickSelection.eyedropper)
        assertEquals(CanvasPointerIntent.PickPaletteEntry, rested.canvasPointerIntent)
    }

    @Test
    fun `an armed eyedropper picks under a resting or hidden underlay`() {
        val resting = armed.withUnderlay(underlay)
        val hidden = armed.withUnderlay(underlay.toggledVisibility())

        assertEquals(CanvasPointerIntent.PickPaletteEntry, resting.canvasPointerIntent)
        assertEquals(CanvasPointerIntent.PickPaletteEntry, hidden.canvasPointerIntent)
    }

    private fun image(
        width: Int,
        height: Int,
    ): ReferenceImage {
        val result = ReferenceImage.create(width, height, IntArray(width * height))
        check(result is ReferenceImageResult.Created) { "expected Created, got $result" }
        return result.image
    }
}
