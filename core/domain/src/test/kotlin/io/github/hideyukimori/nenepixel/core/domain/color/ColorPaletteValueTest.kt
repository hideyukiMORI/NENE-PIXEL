package io.github.hideyukimori.nenepixel.core.domain.color

import io.github.hideyukimori.nenepixel.core.domain.DomainValueAssertions.created
import io.github.hideyukimori.nenepixel.core.domain.DomainValueAssertions.rejected
import io.github.hideyukimori.nenepixel.core.domain.DomainValueTestValues.color
import io.github.hideyukimori.nenepixel.core.domain.palette.Palette
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueRejection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

internal class ColorPaletteValueTest {
    @Test
    fun `color channel accepts complete unsigned byte range`() {
        val minimum = created(ColorChannel.create(0))
        val maximum = created(ColorChannel.create(UByte.MAX_VALUE.toInt()))

        assertEquals(UByte.MIN_VALUE, minimum.value)
        assertEquals(UByte.MAX_VALUE, maximum.value)
        assertInstanceOf(
            DomainValueRejection.ColorChannelOutsideRange::class.java,
            rejected(ColorChannel.create(-1)),
        )
        assertInstanceOf(
            DomainValueRejection.ColorChannelOutsideRange::class.java,
            rejected(ColorChannel.create(UByte.MAX_VALUE.toInt() + 1)),
        )
    }

    @Test
    fun `pixel color and palette entry use typed structural equality`() {
        val firstColor = color(1, 2, 3, 4)
        val sameColor = color(1, 2, 3, 4)
        val firstEntry = created(Palette.create(listOf(firstColor))).entries().single()
        val sameEntry = created(Palette.create(listOf(sameColor))).entries().single()

        assertEquals(firstColor, sameColor)
        assertEquals(firstEntry, sameEntry)
    }

    @Test
    fun `negative palette index is rejected`() {
        assertInstanceOf(
            DomainValueRejection.NegativePaletteIndex::class.java,
            rejected(PaletteIndex.create(-1)),
        )
    }
}
