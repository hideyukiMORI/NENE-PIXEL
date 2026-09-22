package io.github.hideyukimori.nenepixel.core.pixelengine.palette

import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelX
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelY
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteRemap
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelPatch
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelPatchCreationRejection
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelPatchCreationResult

public fun applyPaletteRemap(
    snapshot: PixelSnapshot,
    remap: PaletteRemap,
): PaletteRemapApplicationResult {
    val destinations = remap.copyPackedDestinations()
    return when {
        snapshot.maximumIndex.value >= destinations.size -> {
            PaletteRemapApplicationResult.Rejected(sourceIndexOutsidePalette(snapshot, remap))
        }

        destinations.mapEveryEntryToItself() -> {
            PaletteRemapApplicationResult.NoIndexChanges
        }

        else -> {
            remapResult(snapshot, collectRemapChanges(snapshot.copyPackedIndices(), destinations))
        }
    }
}

private fun remapResult(
    snapshot: PixelSnapshot,
    changes: PackedRemapChanges,
): PaletteRemapApplicationResult =
    if (changes.isEmpty) {
        PaletteRemapApplicationResult.NoIndexChanges
    } else {
        when (val patch = changes.toPatch(snapshot)) {
            is PixelPatchCreationResult.Created -> {
                PaletteRemapApplicationResult.Changed(patch.patch)
            }

            is PixelPatchCreationResult.Rejected -> {
                check(
                    patch.rejection is PixelPatchCreationRejection.RevisionOverflow,
                )
                PaletteRemapApplicationResult.Rejected(
                    PaletteRemapApplicationRejection.RevisionOverflow,
                )
            }
        }
    }

private fun sourceIndexOutsidePalette(
    snapshot: PixelSnapshot,
    remap: PaletteRemap,
): PaletteRemapApplicationRejection.SourceIndexOutsidePalette {
    val entryCount = remap.source.palette.entryCount
    val packed = snapshot.copyPackedIndices()
    val position = packed.indexOfFirst { value -> (value.toInt() and U8_MASK) >= entryCount }
    return PaletteRemapApplicationRejection.SourceIndexOutsidePalette(
        snapshot.size.positionAt(position),
        packed[position].toPaletteIndex(),
        entryCount,
    )
}

private fun collectRemapChanges(
    packed: ByteArray,
    destinations: ByteArray,
): PackedRemapChanges {
    val changeCount = countRemapChanges(packed, destinations)
    val positions = IntArray(changeCount)
    val before = ByteArray(changeCount)
    val after = ByteArray(changeCount)
    var collected = 0
    var positionsAreContiguous = true
    for (position in packed.indices) {
        val source = packed[position]
        val destination = destinations[source.toInt() and U8_MASK]
        if (destination != source) {
            if (collected > 0 && position != positions[collected - 1] + 1) positionsAreContiguous = false
            positions[collected] = position
            before[collected] = source
            after[collected] = destination
            collected += 1
        }
    }
    return PackedRemapChanges(positions, before, after, positionsAreContiguous)
}

private fun countRemapChanges(
    packed: ByteArray,
    destinations: ByteArray,
): Int {
    var changeCount = 0
    for (source in packed) {
        if (destinations[source.toInt() and U8_MASK] != source) changeCount += 1
    }
    return changeCount
}

private class PackedRemapChanges(
    private val positions: IntArray,
    private val before: ByteArray,
    private val after: ByteArray,
    private val positionsAreContiguous: Boolean,
) {
    val isEmpty: Boolean
        get() = positions.isEmpty()

    fun toPatch(snapshot: PixelSnapshot): PixelPatchCreationResult =
        PixelPatch.createFromValidatedPackedIndices(
            canvas = snapshot.size,
            beforeRevision = snapshot.revision,
            positions = positions,
            before = before,
            after = after,
            positionsAreContiguous = positionsAreContiguous,
        )
}

private fun ByteArray.mapEveryEntryToItself(): Boolean {
    for (entry in indices) {
        if ((this[entry].toInt() and U8_MASK) != entry) return false
    }
    return true
}

private fun Byte.toPaletteIndex(): PaletteIndex =
    when (val result = PaletteIndex.create(toInt() and U8_MASK)) {
        is DomainValueResult.Created -> result.value
        is DomainValueResult.Rejected -> error("An unsigned byte was rejected as a palette index.")
    }

private fun CanvasSize.positionAt(index: Int): PixelPosition {
    val x = PixelX.create(index % width.value).requiredValue()
    val y = PixelY.create(index / width.value).requiredValue()
    return PixelPosition.create(x, y)
}

private fun <T> DomainValueResult<T>.requiredValue(): T =
    when (this) {
        is DomainValueResult.Created -> value
        is DomainValueResult.Rejected -> error("A row-major position invariant was rejected: $rejection")
    }

private const val U8_MASK: Int = 0xff
