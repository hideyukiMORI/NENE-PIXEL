package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceImage
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceImageResult
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceUnderlay
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.UnderlayInteraction
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues.canvas
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/** Issue #171 C3b: when the adjust bar shows, and that "fit to drawing" keeps it shown. */
internal class UnderlayAdjustBarTest {
    @Test
    fun `no underlay shows no bar`() {
        assertNull(adjustedUnderlay(null))
    }

    @Test
    fun `a resting underlay shows no bar`() {
        assertNull(adjustedUnderlay(placed()))
    }

    @Test
    fun `an adjusting underlay shows the bar for that underlay`() {
        val adjusting = placed().adjusting()

        assertSame(adjusting, adjustedUnderlay(adjusting))
    }

    @Test
    fun `fitting keeps the bar and done hides it`() {
        val moved = placed().adjusting().withPlacement(-1.0, 0.5, 1.0)

        val fitted = moved.fitted()

        assertEquals(UnderlayInteraction.Adjusting, adjustedUnderlay(fitted)?.interaction)
        assertNull(adjustedUnderlay(fitted.rested()))
    }

    @Test
    fun `hiding the underlay hides the bar`() {
        assertNull(adjustedUnderlay(placed().adjusting().toggledVisibility()))
    }

    private companion object {
        val CANVAS = canvas(4, 3)

        fun placed(): ReferenceUnderlay =
            when (val result = ReferenceImage.create(2, 2, IntArray(4))) {
                is ReferenceImageResult.Created -> ReferenceUnderlay.placed(result.image, CANVAS)
                is ReferenceImageResult.Rejected -> error("Invalid underlay image: ${result.reason}")
            }
    }
}
