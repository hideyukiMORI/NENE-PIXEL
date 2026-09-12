package io.github.hideyukimori.nenepixel.core.projectformat.palette

import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.palette.Palette
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.projectformat.palette.PaletteJsonTestValues.MINIMAL
import io.github.hideyukimori.nenepixel.core.projectformat.palette.PaletteJsonTestValues.accepted
import io.github.hideyukimori.nenepixel.core.projectformat.palette.PaletteJsonTestValues.decode
import io.github.hideyukimori.nenepixel.core.projectformat.palette.PaletteJsonTestValues.definition
import io.github.hideyukimori.nenepixel.core.projectformat.palette.PaletteJsonTestValues.domain
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class PaletteJsonCodecTest {
    @Test
    fun `independent two-color golden is canonical including final LF`() {
        val palette =
            domain(Palette.create(listOf(PixelColor.blank, PixelColor.fromPackedRgba8888(0xff0000ff.toInt()))))
        val definition = domain(PaletteDefinition.create(palette, PaletteIndex.first))
        assertArrayEquals(MINIMAL.encodeToByteArray(), PaletteJsonCodec.encode(definition).copyBytes())
        assertEquals(definition, accepted(decode(MINIMAL)))
    }

    @Test
    fun `all permitted sizes round trip with default at last slot`() {
        (2..256).forEach { count ->
            val expected = definition(count)
            val bytes = PaletteJsonCodec.encode(expected)
            assertEquals(expected, accepted(PaletteJsonCodec.decode(bytes)), "count=$count")
            assertEquals(bytes, PaletteJsonCodec.encode(accepted(PaletteJsonCodec.decode(bytes))))
        }
    }

    @Test
    fun `duplicate and alpha-zero hidden RGB slots remain ordered and distinct`() {
        val text = MINIMAL.replace("#00000000", "#ABCDEF00").replace("#ff0000ff", "#ABCDEF00")
        val palette = accepted(decode(text)).palette
        assertEquals(
            listOf(0xabcdef00.toInt(), 0xabcdef00.toInt()),
            palette.entries().map { it.color.toPackedRgba8888() },
        )
        assertEquals(
            MINIMAL.replace("#00000000", "#abcdef00").replace("#ff0000ff", "#abcdef00"),
            PaletteJsonCodec.encode(accepted(decode(text))).copyBytes().decodeToString(),
        )
    }

    @Test
    fun `BOM whitespace property order ASCII escapes and negative zero canonicalize`() {
        val text =
            "\uFEFF \r\n{\"colors\":[\"\\u002300000000\",\"#FF0000FF\"],\"defaultIndex\":-0," +
                "\"version\":1,\"\\u0066ormat\":\"nene-pixel-palette\"}\t"
        assertArrayEquals(MINIMAL.encodeToByteArray(), PaletteJsonCodec.encode(accepted(decode(text))).copyBytes())
    }
}
