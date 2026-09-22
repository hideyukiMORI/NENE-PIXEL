package io.github.hideyukimori.nenepixel.core.pixelengine.importing

import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.document.LegacyRgbaSource
import io.github.hideyukimori.nenepixel.core.domain.document.Revision
import io.github.hideyukimori.nenepixel.core.domain.palette.Palette
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteLimits
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.core.pixelengine.palette.NearestPaletteEntry

public object LegacyImportPlanner {
    public fun classify(source: LegacyRgbaSource): LegacyImportResult {
        val packed = source.copyPackedRgba8888()
        val slots = LinkedHashMap<Int, Int>()
        packed.forEach { rgba -> slots.getOrPut(rgba) { slots.size } }
        if (slots.size > PaletteLimits.MAX_ENTRY_COUNT) {
            return LegacyImportResult.ConversionRequired(source, slots.size)
        }

        val colors = slots.keys.map(PixelColor::fromPackedRgba8888).toMutableList()
        if (colors.size == 1) colors += colors.single()
        val transparentBlack = colors.indexOfFirst { it.toPackedRgba8888() == TRANSPARENT_BLACK }
        val defaultIndex =
            when {
                transparentBlack >= 0 -> {
                    index(transparentBlack)
                }

                colors.size < PaletteLimits.MAX_ENTRY_COUNT -> {
                    colors += PixelColor.blank
                    index(colors.lastIndex)
                }

                else -> {
                    PaletteIndex.first
                }
            }
        val definition = PaletteDefinition.create(Palette.create(colors).requiredValue(), defaultIndex).requiredValue()
        val indices = ByteArray(packed.size) { position -> slots.getValue(packed[position]).toByte() }
        val snapshot = PixelSnapshot.createPackedIndices(source.size, source.revision, indices).requiredValue()
        val document = DocumentState.create(source.id, definition, snapshot).requiredValue()
        return LegacyImportResult.Lossless(document)
    }

    public fun reduce(
        candidate: LegacyImportResult.ConversionRequired,
        target: PaletteDefinition,
    ): LegacyReductionPreview {
        val packed = candidate.source.copyPackedRgba8888()
        val targets = target.palette.entries()
        val cache = HashMap<Int, PaletteIndex>(minOf(candidate.distinctColorCount, packed.size))
        val indices =
            ByteArray(packed.size) { position ->
                cache
                    .getOrPut(packed[position]) {
                        NearestPaletteEntry.find(PixelColor.fromPackedRgba8888(packed[position]), targets)
                    }.value
                    .toByte()
            }
        val snapshot =
            PixelSnapshot.createPackedIndices(candidate.source.size, Revision.initial(), indices).requiredValue()
        return LegacyReductionPreview(target, snapshot)
    }

    private const val TRANSPARENT_BLACK: Int = 0

    private fun index(value: Int): PaletteIndex = PaletteIndex.create(value).requiredValue()

    private fun <T> DomainValueResult<T>.requiredValue(): T =
        when (this) {
            is DomainValueResult.Created -> value
            is DomainValueResult.Rejected -> error("A validated legacy-import invariant was rejected: $rejection")
        }
}
