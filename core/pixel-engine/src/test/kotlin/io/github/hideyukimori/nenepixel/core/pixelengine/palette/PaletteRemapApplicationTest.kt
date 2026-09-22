package io.github.hideyukimori.nenepixel.core.pixelengine.palette

import io.github.hideyukimori.nenepixel.core.domain.color.ColorChannel
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.document.Revision
import io.github.hideyukimori.nenepixel.core.domain.palette.Palette
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteRemap
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelLimits
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelEngineTestValues.canvas
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelEngineTestValues.position
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelPatchApplicationResult
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

internal class PaletteRemapApplicationTest {
    @Test
    fun `identity mapping and unused mapping return no index changes`() {
        val definition = definition(listOf(rgba(0), rgba(1)))
        val snapshot = snapshot(listOf(0, 0))
        val identity = remap(definition, definition, listOf(0, 1))
        assertEquals(PaletteRemapApplicationResult.NoIndexChanges, applyPaletteRemap(snapshot, identity))
    }

    @Test
    fun `same RGBA slots and many to one mapping remain exact and invertible`() {
        val duplicate = rgba(7)
        val source = definition(listOf(duplicate, duplicate, rgba(8)))
        val target = definition(listOf(duplicate, rgba(8)))
        val snapshot = snapshot(listOf(0, 1, 2))
        val result = applyPaletteRemap(snapshot, remap(source, target, listOf(0, 0, 1)))
        val patch = assertInstanceOf(PaletteRemapApplicationResult.Changed::class.java, result).patch
        val changed = applied(patch.applyTo(snapshot))

        assertEquals(listOf(0, 0, 1), changed.copyPackedIndices().map { it.toInt() and 0xff })
        assertEquals(snapshot, applied(patch.inverse().applyTo(changed)))
    }

    @Test
    fun `maximum canvas many to one remap matches scalar index oracle and remains invertible`() {
        val source = definition(List(256, ::rgba))
        val target = definition(listOf(rgba(0), rgba(1)))
        val sourceValues = List(PixelLimits.MAX_CANVAS_PIXELS) { position -> position % 256 }
        val size = canvas(PixelLimits.MAX_CANVAS_AXIS, PixelLimits.MAX_CANVAS_AXIS)
        val snapshot = PixelSnapshot.create(size, Revision.initial(), sourceValues.map(::index)).value()
        val mapping = List(256) { sourceIndex -> sourceIndex % 2 }

        val result = applyPaletteRemap(snapshot, remap(source, target, mapping))
        val patch = assertInstanceOf(PaletteRemapApplicationResult.Changed::class.java, result).patch
        val changed = applied(patch.applyTo(snapshot))

        val expected = ByteArray(PixelLimits.MAX_CANVAS_PIXELS) { position -> (position % 2).toByte() }
        assertArrayEquals(expected, changed.copyPackedIndices())
        assertEquals(snapshot, applied(patch.inverse().applyTo(changed)))
    }

    @Test
    fun `invalid source membership and revision overflow are typed`() {
        val definition = definition(listOf(rgba(0), rgba(1)))
        val badSnapshot = snapshot(listOf(2))
        val invalid = applyPaletteRemap(badSnapshot, remap(definition, definition, listOf(0, 1)))
        val rejection = assertInstanceOf(PaletteRemapApplicationResult.Rejected::class.java, invalid).rejection
        val outside =
            assertInstanceOf(PaletteRemapApplicationRejection.SourceIndexOutsidePalette::class.java, rejection)
        assertEquals(position(0, 0), outside.position)
        assertEquals(index(2), outside.index)

        val overflow =
            snapshot(listOf(0), Revision.create(Long.MAX_VALUE).value())
        val overflowResult = applyPaletteRemap(overflow, remap(definition, definition, listOf(1, 0)))
        assertEquals(
            PaletteRemapApplicationRejection.RevisionOverflow,
            assertInstanceOf(PaletteRemapApplicationResult.Rejected::class.java, overflowResult).rejection,
        )
    }

    private fun snapshot(
        values: List<Int>,
        revision: Revision = Revision.initial(),
    ): PixelSnapshot = PixelSnapshot.create(canvas(values.size, 1), revision, values.map(::index)).value()

    private fun definition(colors: List<PixelColor>): PaletteDefinition =
        PaletteDefinition.create(Palette.create(colors).value(), PaletteIndex.first).value()

    private fun remap(
        source: PaletteDefinition,
        target: PaletteDefinition,
        destinations: List<Int>,
    ): PaletteRemap = PaletteRemap.create(source, target, destinations.map(::index)).value()

    private fun rgba(red: Int): PixelColor = PixelColor.create(channel(red), channel(0), channel(0), channel(255))

    private fun channel(value: Int): ColorChannel = ColorChannel.create(value).value()

    private fun index(value: Int): PaletteIndex = PaletteIndex.create(value).value()

    private fun applied(result: PixelPatchApplicationResult): PixelSnapshot =
        when (result) {
            is PixelPatchApplicationResult.Applied -> result.snapshot
            is PixelPatchApplicationResult.Rejected -> fail("Patch rejected: ${result.rejection}")
        }

    private fun <T> DomainValueResult<T>.value(): T =
        when (this) {
            is DomainValueResult.Created -> value
            is DomainValueResult.Rejected -> fail("Value rejected: $rejection")
        }
}
