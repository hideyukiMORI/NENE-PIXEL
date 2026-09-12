package io.github.hideyukimori.nenepixel.core.projectformat.palette

import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.palette.Palette
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteLimits
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult

internal class PaletteJsonReader(
    source: String,
) {
    private val cursor: PaletteJsonCursor = PaletteJsonCursor(source)
    private val seen: MutableSet<PaletteJsonField> = mutableSetOf()
    private var defaultIndex: Int? = null
    private var colors: List<PixelColor>? = null

    fun read(): PaletteJsonResult<PaletteDefinition> =
        cursor
            .expect('{')
            .andThen { PaletteJsonSequence(cursor).read('}', ::readMember) }
            .andThen { cursor.end() }
            .andThen { definition() }

    private fun readMember(): PaletteJsonResult<Unit> =
        cursor.readString().andThen { key ->
            field(key).andThen { field ->
                if (!seen.add(field)) {
                    rejected(PaletteJsonRejection.DuplicateField)
                } else {
                    cursor.expect(':').andThen { readField(field) }
                }
            }
        }

    private fun field(key: String): PaletteJsonResult<PaletteJsonField> =
        when (key) {
            "format" -> accepted(PaletteJsonField.Format)
            "version" -> accepted(PaletteJsonField.Version)
            "defaultIndex" -> accepted(PaletteJsonField.DefaultIndex)
            "colors" -> accepted(PaletteJsonField.Colors)
            else -> rejected(PaletteJsonRejection.UnknownField)
        }

    private fun readField(field: PaletteJsonField): PaletteJsonResult<Unit> =
        when (field) {
            PaletteJsonField.Format -> {
                cursor.readString().andThen { format ->
                    if (format ==
                        "nene-pixel-palette"
                    ) {
                        accepted(Unit)
                    } else {
                        rejected(PaletteJsonRejection.UnsupportedFormat)
                    }
                }
            }

            PaletteJsonField.Version -> {
                cursor.readInteger().andThen { version ->
                    if (version == 1) accepted(Unit) else rejected(PaletteJsonRejection.UnsupportedVersion)
                }
            }

            PaletteJsonField.DefaultIndex -> {
                cursor.readInteger().andThen { index ->
                    defaultIndex = index
                    accepted(Unit)
                }
            }

            PaletteJsonField.Colors -> {
                readColors().andThen { entries ->
                    colors = entries
                    accepted(Unit)
                }
            }
        }

    private fun readColors(): PaletteJsonResult<List<PixelColor>> {
        if (!cursor.consume('[')) return cursor.invalid()
        val entries = ArrayList<PixelColor>(PaletteLimits.MAX_ENTRY_COUNT)
        return PaletteJsonSequence(cursor)
            .read(']') {
                readColor(entries.size).andThen { color ->
                    entries.add(color)
                    accepted(Unit)
                }
            }.andThen { accepted(entries) }
    }

    private fun readColor(index: Int): PaletteJsonResult<PixelColor> =
        if (index == PaletteLimits.MAX_ENTRY_COUNT) {
            rejected(PaletteJsonRejection.TooManyColors)
        } else {
            cursor.readString().andThen { color(it, index) }
        }

    private fun color(
        text: String,
        index: Int,
    ): PaletteJsonResult<PixelColor> {
        if (text.length != COLOR_LENGTH || text.first() != '#' || text.drop(1).any { it !in HEX_DIGITS }) {
            return rejected(PaletteJsonRejection.InvalidColor(index))
        }
        return accepted(PixelColor.fromPackedRgba8888(text.substring(1).toUInt(HEX_RADIX).toInt()))
    }

    private fun definition(): PaletteJsonResult<PaletteDefinition> {
        val entries = colors
        val index = defaultIndex
        return if (seen.size != PaletteJsonField.entries.size || entries == null || index == null) {
            rejected(PaletteJsonRejection.MissingField)
        } else {
            fromDomain(Palette.create(entries)).andThen { palette ->
                fromDomain(PaletteIndex.create(index)).andThen { default ->
                    fromDomain(PaletteDefinition.create(palette, default))
                }
            }
        }
    }

    private fun <T> fromDomain(result: DomainValueResult<T>): PaletteJsonResult<T> =
        when (result) {
            is DomainValueResult.Created -> accepted(result.value)
            is DomainValueResult.Rejected -> rejected(PaletteJsonRejection.InvalidDefinition(result.rejection))
        }

    private companion object {
        const val COLOR_LENGTH: Int = 9
        const val HEX_RADIX: Int = 16
        const val HEX_DIGITS: String = "0123456789abcdefABCDEF"
    }
}

private enum class PaletteJsonField { Format, Version, DefaultIndex, Colors }
