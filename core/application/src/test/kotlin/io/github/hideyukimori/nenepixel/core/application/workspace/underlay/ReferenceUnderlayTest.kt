package io.github.hideyukimori.nenepixel.core.application.workspace.underlay

import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

internal class ReferenceUnderlayTest {
    private val image: ReferenceImage = image(200, 100)
    private val canvas = ApplicationTestValues.canvas(256, 256)

    @Test
    fun `placed is fitted, at the default opacity, shown and resting`() {
        val underlay = ReferenceUnderlay.placed(image, canvas)

        assertSame(image, underlay.image)
        assertEquals(canvas, underlay.canvas)
        assertEquals(UnderlayPlacement.fitted(image, canvas), underlay.placement)
        assertEquals(UnderlayOpacity.DEFAULT, underlay.opacity)
        assertEquals(UnderlayVisibility.Shown, underlay.visibility)
        assertEquals(UnderlayInteraction.Resting, underlay.interaction)
    }

    @Test
    fun `withOpacity changes only the opacity`() {
        val underlay = ReferenceUnderlay.placed(image, canvas).withOpacity(UnderlayOpacity.MAX)

        assertEquals(UnderlayOpacity.MAX, underlay.opacity)
        assertEquals(UnderlayPlacement.fitted(image, canvas), underlay.placement)
        assertEquals(UnderlayVisibility.Shown, underlay.visibility)
    }

    @Test
    fun `withPlacement clamps against its own image and canvas, and fitted restores the fit`() {
        val moved = ReferenceUnderlay.placed(image, canvas).withPlacement(-1000.0, 10.0, 1000.0)

        assertEquals(UnderlayPlacement.create(-1000.0, 10.0, 1000.0, image, canvas), moved.placement)
        assertEquals(20.48, moved.placement.scale, EPSILON)
        assertEquals(UnderlayPlacement.fitted(image, canvas), moved.fitted().placement)
    }

    @Test
    fun `hiding ends the adjustment and showing again stays resting`() {
        val hidden = ReferenceUnderlay.placed(image, canvas).adjusting().toggledVisibility()

        assertEquals(UnderlayVisibility.Hidden, hidden.visibility)
        assertEquals(UnderlayInteraction.Resting, hidden.interaction)

        val shown = hidden.toggledVisibility()

        assertEquals(UnderlayVisibility.Shown, shown.visibility)
        assertEquals(UnderlayInteraction.Resting, shown.interaction)
    }

    @Test
    fun `a shown underlay can be adjusted and rested`() {
        val adjusting = ReferenceUnderlay.placed(image, canvas).adjusting()

        assertEquals(UnderlayInteraction.Adjusting, adjusting.interaction)
        assertEquals(UnderlayInteraction.Resting, adjusting.rested().interaction)
    }

    @Test
    fun `a hidden underlay cannot be adjusted`() {
        val hidden = ReferenceUnderlay.placed(image, canvas).toggledVisibility()

        assertSame(hidden, hidden.adjusting())
    }

    @Test
    fun `derivations do not change the original`() {
        val original = ReferenceUnderlay.placed(image, canvas)

        original.withOpacity(UnderlayOpacity.MIN)
        original.withPlacement(1.0, 2.0, 3.0)
        original.toggledVisibility()
        original.adjusting()
        original.adjusting().rested()

        assertEquals(ReferenceUnderlay.placed(image, canvas), original)
        assertEquals(UnderlayInteraction.Resting, original.interaction)
        assertEquals(UnderlayVisibility.Shown, original.visibility)
    }

    @Test
    fun `equality compares the values and the image by identity`() {
        val first = ReferenceUnderlay.placed(image, canvas)
        val second = ReferenceUnderlay.placed(image, canvas)

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
        assertNotEquals(first, ReferenceUnderlay.placed(image(200, 100), canvas))
        assertNotEquals(first, first.withOpacity(UnderlayOpacity.MIN))
        assertNotEquals(first, first.adjusting())
        assertNotEquals(first, first.toggledVisibility())
    }

    private fun image(
        width: Int,
        height: Int,
    ): ReferenceImage {
        val result = ReferenceImage.create(width, height, IntArray(width * height))
        check(result is ReferenceImageResult.Created) { "expected Created, got $result" }
        return result.image
    }

    private companion object {
        /** Allowed error for computed scales, in document pixels per image pixel. */
        const val EPSILON: Double = 1e-9
    }
}
