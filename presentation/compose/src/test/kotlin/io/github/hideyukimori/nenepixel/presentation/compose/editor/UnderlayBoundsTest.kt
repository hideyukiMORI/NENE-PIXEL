package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceImage
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceImageResult
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceUnderlay
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues.canvas
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Issue #170 A6: the one formula that puts the underlay image on the drawing surface (ADR 0032). */
internal class UnderlayBoundsTest {
    @Test
    fun `a fitted image is centred inside the document rectangle`() {
        // 2 x 2 on 4 x 3 fits at scale 1.5, left 0.5, top 0; one cell is 10 surface pixels.
        val underlay = ReferenceUnderlay.placed(image(2, 2), CANVAS)

        val shown = underlayBounds(UnderlayBounds(10f, 20f, 50f, 50f), CANVAS, underlay.image, underlay.placement)

        assertEquals(UnderlayBounds(15f, 20f, 45f, 50f), shown)
    }

    @Test
    fun `an image placed past the left edge starts left of the document rectangle`() {
        val underlay = ReferenceUnderlay.placed(image(2, 2), CANVAS).withPlacement(-1.0, 0.5, 1.0)

        val shown = underlayBounds(UnderlayBounds(0f, 0f, 40f, 30f), CANVAS, underlay.image, underlay.placement)

        assertEquals(UnderlayBounds(-10f, 5f, 10f, 25f), shown)
    }

    @Test
    fun `each axis uses its own cell size`() {
        // Cells 10 wide and 20 tall.
        val underlay = ReferenceUnderlay.placed(image(2, 2), CANVAS).withPlacement(-1.0, 0.5, 1.0)

        val shown = underlayBounds(UnderlayBounds(0f, 0f, 40f, 60f), CANVAS, underlay.image, underlay.placement)

        assertEquals(UnderlayBounds(-10f, 10f, 10f, 50f), shown)
    }

    private companion object {
        val CANVAS = canvas(4, 3)

        fun image(
            width: Int,
            height: Int,
        ): ReferenceImage =
            when (val result = ReferenceImage.create(width, height, IntArray(width * height))) {
                is ReferenceImageResult.Created -> result.image
                is ReferenceImageResult.Rejected -> error("Invalid underlay image: ${result.reason}")
            }
    }
}
