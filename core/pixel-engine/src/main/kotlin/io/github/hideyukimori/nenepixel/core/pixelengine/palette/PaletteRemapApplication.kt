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
    val packed = snapshot.copyPackedIndices()
    val changes = PackedRemapChanges(packed.size)
    var rejection: PaletteRemapApplicationRejection.SourceIndexOutsidePalette? = null
    for (position in packed.indices) {
        val sourceIndex = packed[position].toPaletteIndex()
        val destination = remap.destinationAt(sourceIndex)
        when (destination) {
            is DomainValueResult.Created -> {
                if (sourceIndex != destination.value) {
                    changes.add(position, sourceIndex, destination.value)
                }
            }

            is DomainValueResult.Rejected -> {
                rejection =
                    PaletteRemapApplicationRejection.SourceIndexOutsidePalette(
                        snapshot.size.positionAt(position),
                        sourceIndex,
                        remap.source.palette.entryCount,
                    )
                break
            }
        }
    }
    return remapResult(snapshot, changes, rejection)
}

private fun remapResult(
    snapshot: PixelSnapshot,
    changes: PackedRemapChanges,
    rejection: PaletteRemapApplicationRejection.SourceIndexOutsidePalette?,
): PaletteRemapApplicationResult =
    when {
        rejection != null -> {
            PaletteRemapApplicationResult.Rejected(rejection)
        }

        changes.isEmpty -> {
            PaletteRemapApplicationResult.NoIndexChanges
        }

        else -> {
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
    }

private class PackedRemapChanges(
    maximumSize: Int,
) {
    private var positions = IntArray(minOf(INITIAL_CAPACITY, maximumSize))
    private var before = ByteArray(positions.size)
    private var after = ByteArray(positions.size)
    private var size: Int = 0

    val isEmpty: Boolean
        get() = size == 0

    fun add(
        position: Int,
        source: PaletteIndex,
        destination: PaletteIndex,
    ) {
        ensureCapacity()
        positions[size] = position
        before[size] = source.value.toByte()
        after[size] = destination.value.toByte()
        size += 1
    }

    fun toPatch(snapshot: PixelSnapshot): PixelPatchCreationResult =
        PixelPatch.createFromValidatedPackedIndices(
            canvas = snapshot.size,
            beforeRevision = snapshot.revision,
            positions = positions.exactSize(size),
            before = before.exactSize(size),
            after = after.exactSize(size),
            positionsAreContiguous = false,
        )

    private fun ensureCapacity() {
        if (size < positions.size) return
        val nextCapacity = positions.size * CAPACITY_GROWTH
        positions = positions.copyOf(nextCapacity)
        before = before.copyOf(nextCapacity)
        after = after.copyOf(nextCapacity)
    }

    private companion object {
        const val INITIAL_CAPACITY: Int = 256
        const val CAPACITY_GROWTH: Int = 2
    }
}

private fun IntArray.exactSize(size: Int): IntArray = if (this.size == size) this else copyOf(size)

private fun ByteArray.exactSize(size: Int): ByteArray = if (this.size == size) this else copyOf(size)

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
