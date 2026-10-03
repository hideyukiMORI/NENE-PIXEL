package io.github.hideyukimori.nenepixel.core.application.workspace.underlay

import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

internal class RememberedUnderlayTest {
    private val image: ReferenceImage = image(200, 100)
    private val canvas = ApplicationTestValues.canvas(256, 256)
    private val opacity: UnderlayOpacity = UnderlayOpacity.create(200)

    @Test
    fun `create keeps every value`() {
        val placement = placement(10.5, -3.25, 1.5)

        val remembered = RememberedUnderlay.create(image, placement, opacity, UnderlayVisibility.Hidden)

        assertSame(image, remembered.image)
        assertEquals(placement, remembered.placement)
        assertEquals(opacity, remembered.opacity)
        assertEquals(UnderlayVisibility.Hidden, remembered.visibility)
    }

    @Test
    fun `a shown underlay comes back equal and resting on the same canvas`() {
        val original = moved()

        val restored = RememberedUnderlay.of(original).toUnderlay(canvas)

        assertEquals(original, restored)
        assertSame(image, restored.image)
        assertEquals(UnderlayVisibility.Shown, restored.visibility)
        assertEquals(UnderlayInteraction.Resting, restored.interaction)
    }

    @Test
    fun `a hidden underlay comes back hidden and resting`() {
        val original = moved().toggledVisibility()

        val restored = RememberedUnderlay.of(original).toUnderlay(canvas)

        assertEquals(original, restored)
        assertEquals(UnderlayVisibility.Hidden, restored.visibility)
        assertEquals(UnderlayInteraction.Resting, restored.interaction)
    }

    @Test
    fun `an underlay being adjusted comes back resting`() {
        val original = moved().adjusting()

        val restored = RememberedUnderlay.of(original).toUnderlay(canvas)

        assertEquals(UnderlayInteraction.Resting, restored.interaction)
        assertEquals(original.rested(), restored)
    }

    @Test
    fun `a different canvas clamps like withPlacement`() {
        val small = ApplicationTestValues.canvas(64, 32)
        val remembered =
            RememberedUnderlay.create(image, placement(200.0, -3.25, 1.5), opacity, UnderlayVisibility.Shown)

        val restored = remembered.toUnderlay(small)

        val expected = ReferenceUnderlay.placed(image, small).withPlacement(200.0, -3.25, 1.5).withOpacity(opacity)
        assertEquals(expected, restored)
        assertEquals(63.0, restored.placement.left)
        assertEquals(UnderlayInteraction.Resting, restored.interaction)
    }

    @Test
    fun `of keeps the same image instance and the current placement, opacity and visibility`() {
        val original = moved().toggledVisibility()

        val remembered = RememberedUnderlay.of(original)

        assertSame(image, remembered.image)
        assertEquals(placement(10.5, -3.25, 1.5), remembered.placement)
        assertEquals(opacity, remembered.opacity)
        assertEquals(UnderlayVisibility.Hidden, remembered.visibility)
    }

    @Test
    fun `equality compares the values and the image by identity`() {
        val first = RememberedUnderlay.of(moved())
        val second = RememberedUnderlay.of(moved())

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
        assertNotEquals(first, RememberedUnderlay.of(ReferenceUnderlay.placed(image(200, 100), canvas)))
        assertNotEquals(first, RememberedUnderlay.of(moved().withOpacity(UnderlayOpacity.MIN)))
        assertNotEquals(first, RememberedUnderlay.of(moved().toggledVisibility()))
        assertNotEquals(first, RememberedUnderlay.of(moved().withPlacement(11.0, -3.25, 1.5)))
    }

    @Test
    fun `toString does not show the pixels`() {
        val packed = IntArray(4) { 0x12345678 }
        val result = ReferenceImage.create(2, 2, packed)
        check(result is ReferenceImageResult.Created) { "expected Created, got $result" }
        val remembered = RememberedUnderlay.of(ReferenceUnderlay.placed(result.image, canvas))

        assertFalse(remembered.toString().contains(0x12345678.toString()))
        assertFalse(remembered.toString().contains("12345678"))
    }

    private fun moved(): ReferenceUnderlay =
        ReferenceUnderlay.placed(image, canvas).withPlacement(10.5, -3.25, 1.5).withOpacity(opacity)

    private fun placement(
        left: Double,
        top: Double,
        scale: Double,
    ): RememberedPlacement {
        val result = RememberedPlacement.create(left, top, scale)
        check(result is RememberedPlacementResult.Created) { "expected Created, got $result" }
        return result.placement
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
