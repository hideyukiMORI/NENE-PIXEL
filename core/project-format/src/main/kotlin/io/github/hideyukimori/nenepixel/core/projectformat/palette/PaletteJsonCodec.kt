package io.github.hideyukimori.nenepixel.core.projectformat.palette

import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import kotlin.text.CharacterCodingException

public object PaletteJsonCodec {
    public fun encode(definition: PaletteDefinition): PaletteJsonBytes {
        val text =
            buildString {
                append("{\"format\":\"nene-pixel-palette\",\"version\":1,\"defaultIndex\":")
                append(definition.defaultIndex.value)
                append(",\"colors\":[")
                definition.palette.entries().forEachIndexed { index, entry ->
                    if (index > 0) append(',')
                    append("\"#")
                    append(
                        entry.color
                            .toPackedRgba8888()
                            .toUInt()
                            .toString(HEX_RADIX)
                            .padStart(HEX_WIDTH, '0'),
                    )
                    append('"')
                }
                append("]}\n")
            }
        return when (val result = PaletteJsonBytes.create(text.encodeToByteArray())) {
            is PaletteJsonResult.Accepted -> result.value
            is PaletteJsonResult.Rejected -> error("Validated palette exceeded the JSON envelope: ${result.rejection}")
        }
    }

    public fun decode(bytes: PaletteJsonBytes): PaletteJsonResult<PaletteDefinition> {
        if (bytes.byteCount > PaletteJsonBytes.MAX_FILE_BYTE_COUNT) {
            return rejected(
                PaletteJsonRejection.ResourceLimitExceeded(bytes.byteCount, PaletteJsonBytes.MAX_FILE_BYTE_COUNT),
            )
        }
        return decodeUtf8(bytes).andThen { text -> PaletteJsonReader(text).read() }
    }

    private fun decodeUtf8(bytes: PaletteJsonBytes): PaletteJsonResult<String> =
        try {
            accepted(bytes.copyBytes().decodeToString(throwOnInvalidSequence = true))
        } catch (_: CharacterCodingException) {
            rejected(PaletteJsonRejection.InvalidUtf8)
        }

    private const val HEX_RADIX: Int = 16
    private const val HEX_WIDTH: Int = 8
}
