package io.github.hideyukimori.nenepixel.core.projectformat.palette

import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.palette.Palette
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult

internal object PaletteJsonTestValues {
    const val MINIMAL: String =
        "{\"format\":\"nene-pixel-palette\",\"version\":1,\"defaultIndex\":0,\"colors\":[\"#00000000\",\"#ff0000ff\"]}\n"

    fun definition(count: Int): PaletteDefinition {
        val colors = List(count) { index -> PixelColor.fromPackedRgba8888((index shl 24) or index) }
        return domain(PaletteDefinition.create(domain(Palette.create(colors)), domain(PaletteIndex.create(count - 1))))
    }

    fun carrier(bytes: ByteArray): PaletteJsonBytes = accepted(PaletteJsonBytes.create(bytes))

    fun decode(text: String): PaletteJsonResult<PaletteDefinition> =
        PaletteJsonCodec.decode(carrier(text.encodeToByteArray()))

    fun <T> accepted(result: PaletteJsonResult<T>): T =
        when (result) {
            is PaletteJsonResult.Accepted -> result.value
            is PaletteJsonResult.Rejected -> error("Unexpected palette JSON rejection: ${result.rejection}")
        }

    fun rejected(result: PaletteJsonResult<*>): PaletteJsonRejection =
        when (result) {
            is PaletteJsonResult.Accepted -> error("Expected palette JSON rejection")
            is PaletteJsonResult.Rejected -> result.rejection
        }

    fun <T> domain(result: DomainValueResult<T>): T =
        when (result) {
            is DomainValueResult.Created -> result.value
            is DomainValueResult.Rejected -> error("Unexpected domain rejection: ${result.rejection}")
        }
}
