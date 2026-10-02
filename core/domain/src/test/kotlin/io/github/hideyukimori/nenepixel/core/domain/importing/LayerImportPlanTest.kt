package io.github.hideyukimori.nenepixel.core.domain.importing

import io.github.hideyukimori.nenepixel.core.domain.DomainValueAssertions.created
import io.github.hideyukimori.nenepixel.core.domain.DomainValueAssertions.rejected
import io.github.hideyukimori.nenepixel.core.domain.DomainValueTestValues.canvasSize
import io.github.hideyukimori.nenepixel.core.domain.DomainValueTestValues.color
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.palette.Palette
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueRejection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

internal class LayerImportPlanTest {
    private val red = color(255, 0, 0, 255)
    private val green = color(0, 255, 0, 255)
    private val blue = color(0, 0, 255, 255)
    private val white = color(255, 255, 255, 255)
    private val source = definition(listOf(red, green), 1)
    private val loss = created(LayerImportLoss.create(1, 2))

    @Test
    fun `plan that keeps the palette is created`() {
        val snapshot = filled(1)
        val plan = created(LayerImportPlan.create(source, source, snapshot, loss))

        assertSame(source, plan.source)
        assertSame(source, plan.target)
        assertSame(snapshot, plan.snapshot)
        assertSame(loss, plan.loss)
        assertEquals(0, plan.appendedColorCount)
    }

    @Test
    fun `plan that appends colours is created and counts them`() {
        val target = definition(listOf(red, green, blue, white), 1)
        val plan = created(LayerImportPlan.create(source, target, filled(3), loss))

        assertSame(target, plan.target)
        assertEquals(2, plan.appendedColorCount)
    }

    @Test
    fun `equality is identity and text holds only counts`() {
        val snapshot = filled(0)
        val first = created(LayerImportPlan.create(source, source, snapshot, loss))
        val second = created(LayerImportPlan.create(source, source, snapshot, loss))

        assertNotEquals(first, second)
        assertEquals(
            "LayerImportPlan(sourceCount=2, targetCount=2, size=${snapshot.size}, " +
                "nearestColorCount=1, droppedPixelCount=2)",
            first.toString(),
        )
    }

    @Test
    fun `target with a different leading colour is rejected`() {
        val target = definition(listOf(red, blue, green), 1)

        assertEquals(
            DomainValueRejection.LayerImportPlanPaletteNotExtended,
            rejected(LayerImportPlan.create(source, target, filled(0), loss)),
        )
    }

    @Test
    fun `target with the leading colours reordered is rejected`() {
        val target = definition(listOf(green, red, blue), 1)

        assertEquals(
            DomainValueRejection.LayerImportPlanPaletteNotExtended,
            rejected(LayerImportPlan.create(source, target, filled(0), loss)),
        )
    }

    @Test
    fun `target shorter than the source is rejected`() {
        val longSource = definition(listOf(red, green, blue), 1)

        assertEquals(
            DomainValueRejection.LayerImportPlanPaletteNotExtended,
            rejected(LayerImportPlan.create(longSource, source, filled(0), loss)),
        )
    }

    @Test
    fun `target with a different default slot is rejected`() {
        val target = definition(listOf(red, green, blue), 0)

        assertEquals(
            DomainValueRejection.LayerImportPlanPaletteNotExtended,
            rejected(LayerImportPlan.create(source, target, filled(0), loss)),
        )
    }

    @Test
    fun `snapshot without a covered cell is rejected`() {
        val empty = PixelSnapshot.createEmpty(canvasSize(3, 2))

        assertEquals(
            DomainValueRejection.LayerImportPlanWithoutPixels,
            rejected(LayerImportPlan.create(source, source, empty, loss)),
        )
    }

    @Test
    fun `snapshot index outside the target palette is rejected`() {
        val target = definition(listOf(red, green, blue), 1)

        assertEquals(
            DomainValueRejection.PaletteIndexOutsidePalette(index(3), 3),
            rejected(LayerImportPlan.create(source, target, filled(3), loss)),
        )
    }

    private fun filled(value: Int): PixelSnapshot = created(PixelSnapshot.createFilled(canvasSize(3, 2), index(value)))

    private fun index(value: Int): PaletteIndex = created(PaletteIndex.create(value))

    private fun definition(
        colors: List<PixelColor>,
        defaultIndex: Int,
    ): PaletteDefinition = created(PaletteDefinition.create(created(Palette.create(colors)), index(defaultIndex)))
}
