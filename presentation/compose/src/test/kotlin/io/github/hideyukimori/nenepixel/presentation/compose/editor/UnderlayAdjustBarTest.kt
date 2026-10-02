package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceImage
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceImageResult
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceUnderlay
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.UnderlayOpacity
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues.canvas
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** Issue #171 C3b/C3d: when the adjust bar shows, what it reads, and that "fit to drawing" keeps it shown. */
internal class UnderlayAdjustBarTest {
    @Test
    fun `no underlay shows no bar`() {
        assertNull(adjustedOpacity(null))
    }

    @Test
    fun `a resting underlay shows no bar`() {
        assertNull(adjustedOpacity(placed()))
    }

    @Test
    fun `an adjusting underlay shows the bar with its opacity`() {
        val adjusting = placed().adjusting().withOpacity(UnderlayOpacity.MIN)

        assertEquals(UnderlayOpacity.MIN, adjustedOpacity(adjusting))
    }

    @Test
    fun `moving the underlay does not change what the bar reads`() {
        val adjusting = placed().adjusting()
        val moved = adjusting.withPlacement(-1.0, 0.5, 1.0)
        assertNotEquals(adjusting.placement, moved.placement)

        assertEquals(adjustedOpacity(adjusting), adjustedOpacity(moved))
    }

    @Test
    fun `fitting keeps the bar and done hides it`() {
        val moved = placed().adjusting().withPlacement(-1.0, 0.5, 1.0)

        val fitted = moved.fitted()

        assertEquals(moved.opacity, adjustedOpacity(fitted))
        assertNull(adjustedOpacity(fitted.rested()))
    }

    @Test
    fun `hiding the underlay hides the bar`() {
        assertNull(adjustedOpacity(placed().adjusting().toggledVisibility()))
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
