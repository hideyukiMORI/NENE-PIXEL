package io.github.hideyukimori.nenepixel.core.pixelengine.importing

import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentId
import io.github.hideyukimori.nenepixel.core.domain.document.LegacyRgbaSource
import io.github.hideyukimori.nenepixel.core.domain.document.Revision
import io.github.hideyukimori.nenepixel.core.domain.palette.Palette
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelEngineTestValues.canvas
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelEngineTestValues.position
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

internal class LegacyImportPlannerTest {
    @Test
    fun `one transparent black and one nonblack use the exact accepted defaults`() {
        val black = classify(intArrayOf(0))
        val blackDocument = assertInstanceOf(LegacyImportResult.Lossless::class.java, black).document
        assertEquals(
            listOf(0, 0),
            blackDocument
                .definition
                .palette
                .entries()
                .map { it.color.toPackedRgba8888() },
        )
        assertEquals(index(0), blackDocument.definition.defaultIndex)

        val red = 0xff0000ff.toInt()
        val nonblackDocument =
            assertInstanceOf(LegacyImportResult.Lossless::class.java, classify(intArrayOf(red))).document
        assertEquals(
            listOf(red, red, 0),
            nonblackDocument
                .definition
                .palette
                .entries()
                .map { it.color.toPackedRgba8888() },
        )
        assertEquals(index(2), nonblackDocument.definition.defaultIndex)
    }

    @Test
    fun `hidden RGB is exact and 256 boundary stays lossless`() {
        val values = IntArray(256) { it + 1 }
        values[0] = 0x01020300
        val document = assertInstanceOf(LegacyImportResult.Lossless::class.java, classify(values)).document
        assertEquals(256, document.definition.palette.entryCount)
        assertEquals(index(0), document.definition.defaultIndex)
        assertEquals(
            0x01020300,
            document
                .definition
                .palette
                .entries()
                .first()
                .color
                .toPackedRgba8888(),
        )
    }

    @Test
    fun `257 and maximum canvas classify exact count without constructing document`() {
        val source257 = source(IntArray(257) { it })
        val required257 =
            assertInstanceOf(
                LegacyImportResult.ConversionRequired::class.java,
                LegacyImportPlanner.classify(source257),
            )
        assertSame(source257, required257.source)
        assertEquals(257, required257.distinctColorCount)

        val maximum = IntArray(65_536) { it }
        val requiredMaximum =
            assertInstanceOf(LegacyImportResult.ConversionRequired::class.java, classify(maximum))
        assertEquals(65_536, requiredMaximum.distinctColorCount)
    }

    @Test
    fun `reduction uses exact then nearest mapping and returns revision zero bound preview`() {
        val candidate =
            assertInstanceOf(
                LegacyImportResult.ConversionRequired::class.java,
                LegacyImportPlanner.classify(source(IntArray(257) { it shl 8 or 0xff })),
            )
        val black = PixelColor.fromPackedRgba8888(0x000000ff)
        val white = PixelColor.fromPackedRgba8888(-1)
        val target = PaletteDefinition.create(Palette.create(listOf(black, white)).value(), PaletteIndex.first).value()
        val preview = LegacyImportPlanner.reduce(candidate, target)

        assertSame(target, preview.definition)
        assertEquals(Revision.initial(), preview.snapshot.revision)
        assertEquals(candidate.source.size, preview.snapshot.size)
        assertEquals(
            index(0),
            preview.snapshot
                .indexAt(position(0, 0))
                .value(),
        )
    }

    private fun classify(values: IntArray): LegacyImportResult = LegacyImportPlanner.classify(source(values))

    private fun source(values: IntArray): LegacyRgbaSource =
        run {
            val width = minOf(values.size, 256)
            val height = (values.size + width - 1) / width
            val packed = IntArray(width * height) { values[it % values.size] }
            LegacyRgbaSource.createPackedRgba8888(ID, Revision.create(7).value(), canvas(width, height), packed).value()
        }

    private fun index(value: Int): PaletteIndex = PaletteIndex.create(value).value()

    private fun <T> DomainValueResult<T>.value(): T =
        when (this) {
            is DomainValueResult.Created -> value
            is DomainValueResult.Rejected -> fail("Value rejected: $rejection")
        }

    private companion object {
        val ID =
            when (val result = DocumentId.create("b".repeat(32))) {
                is DomainValueResult.Created -> result.value
                is DomainValueResult.Rejected -> error("Invalid test id")
            }
    }
}
