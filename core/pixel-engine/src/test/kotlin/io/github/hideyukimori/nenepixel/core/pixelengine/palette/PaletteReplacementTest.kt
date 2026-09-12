package io.github.hideyukimori.nenepixel.core.pixelengine.palette

import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueRejection
import io.github.hideyukimori.nenepixel.core.pixelengine.palette.PaletteRemapTestValues.indices
import io.github.hideyukimori.nenepixel.core.pixelengine.palette.PaletteRemapTestValues.palette
import io.github.hideyukimori.nenepixel.core.pixelengine.palette.PaletteRemapTestValues.planned
import io.github.hideyukimori.nenepixel.core.pixelengine.palette.PaletteRemapTestValues.rejection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

internal class PaletteReplacementTest {
    @Test
    fun `number replacement recolors while nearest replacement preserves exact colors`() {
        val source = palette(0xff0000ff, 0x0000ffff)
        val target = palette(0x0000ffff, 0xff0000ff, default = 1)
        val byNumber = planned(PaletteRemapPlanner.byNumber(source, target))
        val nearest = planned(PaletteRemapPlanner.nearest(source, target))
        assertEquals(indices(0, 1), byNumber.destinations())
        assertEquals(indices(1, 0), nearest.destinations())
        assertSame(target, byNumber.target)
        assertSame(target, nearest.target)
        assertEquals(1, byNumber.target.defaultIndex.value)
    }

    @Test
    fun `short palette rejects unused source slots rather than clamping or dropping them`() {
        val source = palette(0, 0xff0000ff, 0x0000ffff)
        val target = palette(0, 0xff0000ff)
        val invalid =
            assertInstanceOf(
                PaletteRemapRejection.InvalidMapping::class.java,
                rejection(PaletteRemapPlanner.byNumber(source, target)),
            )
        val missing =
            assertInstanceOf(
                DomainValueRejection.PaletteRemapDestinationOutsidePalette::class.java,
                invalid.rejection,
            )
        assertEquals(2, missing.sourceIndex.value)
        assertEquals(2, missing.destinationIndex.value)
        assertEquals(2, missing.targetEntryCount)
        assertEquals(
            indices(0, 1, 1),
            planned(PaletteRemapPlanner.explicit(source, target, indices(0, 1, 1))).destinations(),
        )
    }

    @Test
    fun `explicit mapping preserves duplicate slot identity and owns caller mapping`() {
        val source = palette(0x12345600, 0x12345600)
        val target = palette(0xabcdef00, 0xabcdef00)
        val inputs = indices(1, 0).toMutableList()
        val remap = planned(PaletteRemapPlanner.explicit(source, target, inputs))
        inputs.clear()
        assertEquals(indices(1, 0), remap.destinations())
        assertNotEquals(planned(PaletteRemapPlanner.byNumber(source, target)), remap)
    }

    @Test
    fun `explicit incomplete and out-of-range mappings retain typed domain rejection`() {
        val source = palette(0, 1)
        assertInstanceOf(
            PaletteRemapRejection.InvalidMapping::class.java,
            rejection(PaletteRemapPlanner.explicit(source, source, indices(0))),
        )
        assertInstanceOf(
            PaletteRemapRejection.InvalidMapping::class.java,
            rejection(PaletteRemapPlanner.explicit(source, source, indices(0, 256))),
        )
    }
}
