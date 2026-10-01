package io.github.hideyukimori.nenepixel.core.domain.importing

import io.github.hideyukimori.nenepixel.core.domain.DomainValueAssertions.created
import io.github.hideyukimori.nenepixel.core.domain.DomainValueAssertions.rejected
import io.github.hideyukimori.nenepixel.core.domain.DomainValueTestValues.canvasSize
import io.github.hideyukimori.nenepixel.core.domain.DomainValueTestValues.color
import io.github.hideyukimori.nenepixel.core.domain.palette.Palette
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueRejection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

internal class NewWorkImportPlanTest {
    private val definition =
        created(
            PaletteDefinition.create(
                created(Palette.create(listOf(color(255, 0, 0, 255), color(0, 255, 0, 255)))),
                PaletteIndex.first,
            ),
        )

    @Test
    fun `plan is created and keeps its values`() {
        val snapshot = filled(1)
        val plan = created(NewWorkImportPlan.create(definition, snapshot))

        assertSame(definition, plan.definition)
        assertSame(snapshot, plan.snapshot)
    }

    @Test
    fun `equality is identity and text holds only counts`() {
        val snapshot = filled(0)
        val first = created(NewWorkImportPlan.create(definition, snapshot))
        val second = created(NewWorkImportPlan.create(definition, snapshot))

        assertNotEquals(first, second)
        assertEquals("NewWorkImportPlan(entryCount=2, size=${snapshot.size})", first.toString())
    }

    @Test
    fun `snapshot without a covered cell is rejected`() {
        val empty = PixelSnapshot.createEmpty(canvasSize(3, 2))

        assertEquals(
            DomainValueRejection.ImportPlanWithoutPixels,
            rejected(NewWorkImportPlan.create(definition, empty)),
        )
    }

    @Test
    fun `snapshot index outside the palette is rejected`() {
        assertEquals(
            DomainValueRejection.PaletteIndexOutsidePalette(index(2), 2),
            rejected(NewWorkImportPlan.create(definition, filled(2))),
        )
    }

    private fun filled(value: Int): PixelSnapshot = created(PixelSnapshot.createFilled(canvasSize(3, 2), index(value)))

    private fun index(value: Int): PaletteIndex = created(PaletteIndex.create(value))
}
