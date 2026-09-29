package io.github.hideyukimori.nenepixel.core.pixelengine

import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasHeight
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasWidth
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelRegion
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelX
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelY
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelCell
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelLimits
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult

public class PixelPatch private constructor(
    public val canvas: CanvasSize,
    public val affectedRegion: PixelRegion,
    private val storage: PixelPatchStorage,
    private val direction: PixelPatchDirection,
) {
    private val changes: PixelPatchChanges = PixelPatchChanges(storage, direction)

    public val changeCount: Int
        get() = storage.positions.size

    public fun applyTo(snapshot: PixelSnapshot): PixelPatchApplicationResult =
        when {
            snapshot.size != canvas -> {
                rejected(PixelPatchApplicationRejection.CanvasMismatch(canvas, snapshot.size))
            }

            else -> {
                applyToMatchingSnapshot(snapshot)
            }
        }

    private fun applyToMatchingSnapshot(snapshot: PixelSnapshot): PixelPatchApplicationResult {
        val surface = PixelSurface.from(snapshot)
        repeat(changeCount) { index ->
            val positionIndex = changes.positionAt(index)
            val actual = surface.cellAt(positionIndex)
            val expected = changes.beforeCellAt(index)
            if (actual != expected) {
                return rejected(
                    PixelPatchApplicationRejection.BeforeValueMismatch(
                        position = canvas.positionAt(positionIndex),
                        expected = expected,
                        actual = actual,
                    ),
                )
            }
            surface.writePackedCell(positionIndex, changes.afterCoveredAt(index), changes.afterAt(index))
        }
        return PixelPatchApplicationResult.Applied(surface.snapshot())
    }

    public fun inverse(): PixelPatch =
        PixelPatch(
            canvas = canvas,
            affectedRegion = affectedRegion,
            storage = storage,
            direction = direction.inverse(),
        )

    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is PixelPatch &&
                    canvas == other.canvas &&
                    affectedRegion == other.affectedRegion &&
                    changesEqual(other)
            )

    private fun changesEqual(other: PixelPatch): Boolean =
        changeCount == other.changeCount &&
            (0 until changeCount).all { index ->
                changes.positionAt(index) == other.changes.positionAt(index) &&
                    changes.beforeCoveredAt(index) == other.changes.beforeCoveredAt(index) &&
                    changes.afterCoveredAt(index) == other.changes.afterCoveredAt(index) &&
                    changes.beforeAt(index) == other.changes.beforeAt(index) &&
                    changes.afterAt(index) == other.changes.afterAt(index)
            }

    override fun hashCode(): Int {
        var result = canvas.hashCode()
        result = HASH_MULTIPLIER * result + affectedRegion.hashCode()
        repeat(changeCount) { index ->
            result = HASH_MULTIPLIER * result + changes.positionAt(index)
            result = HASH_MULTIPLIER * result + changes.beforeCoveredAt(index).hashCode()
            result = HASH_MULTIPLIER * result + changes.afterCoveredAt(index).hashCode()
            result = HASH_MULTIPLIER * result + changes.beforeAt(index)
            result = HASH_MULTIPLIER * result + changes.afterAt(index)
        }
        return result
    }

    override fun toString(): String = "PixelPatch(canvas=$canvas, changeCount=$changeCount)"

    public companion object {
        private const val HASH_MULTIPLIER: Int = 31

        public fun create(
            canvas: CanvasSize,
            changes: List<PixelChange>,
        ): PixelPatchCreationResult =
            when {
                changes.size > PixelLimits.MAX_PATCH_CHANGES -> {
                    creationRejected(
                        PixelPatchCreationRejection.ChangeCountAboveSupportedMaximum(
                            changes.size,
                            PixelLimits.MAX_PATCH_CHANGES,
                        ),
                    )
                }

                else -> {
                    val canonicalChanges = changes.sortedWith(ROW_MAJOR_ORDER)
                    val rejection = validateChanges(canvas, canonicalChanges)
                    if (rejection == null) {
                        createdPatch(canvas, canonicalChanges)
                    } else {
                        creationRejected(rejection)
                    }
                }
            }

        internal fun createFromValidatedPackedIndices(
            canvas: CanvasSize,
            positions: IntArray,
            before: ByteArray,
            after: ByteArray,
            positionsAreContiguous: Boolean,
        ): PixelPatchCreationResult =
            when {
                positions.size > PixelLimits.MAX_PATCH_CHANGES -> {
                    creationRejected(
                        PixelPatchCreationRejection.ChangeCountAboveSupportedMaximum(
                            positions.size,
                            PixelLimits.MAX_PATCH_CHANGES,
                        ),
                    )
                }

                else -> {
                    check(positions.isNotEmpty() && positions.size == before.size && positions.size == after.size)
                    check(
                        positions.first() and POSITION_MASK >= 0 &&
                            positions.last() and POSITION_MASK < canvas.pixelCount.toInt(),
                    )
                    PixelPatchCreationResult.Created(
                        PixelPatch(
                            canvas = canvas,
                            affectedRegion = affectedRegion(canvas, positions, positionsAreContiguous),
                            storage =
                                PixelPatchStorage(
                                    positions,
                                    before,
                                    after,
                                ),
                            direction = PixelPatchDirection.Forward,
                        ),
                    )
                }
            }

        private fun createdPatch(
            canvas: CanvasSize,
            changes: List<PixelChange>,
        ): PixelPatchCreationResult {
            val positions =
                IntArray(changes.size) { index ->
                    val change = changes[index]
                    packPatchPosition(
                        change.position.rowMajorIndex(canvas),
                        beforeCovered = change.before is PixelCell.Covered,
                        afterCovered = change.after is PixelCell.Covered,
                    )
                }
            return PixelPatchCreationResult.Created(
                PixelPatch(
                    canvas = canvas,
                    affectedRegion = affectedRegion(canvas, positions, positionsAreContiguous = false),
                    storage =
                        PixelPatchStorage(
                            positions,
                            ByteArray(changes.size) { index -> changes[index].before.packedIndex() },
                            ByteArray(changes.size) { index -> changes[index].after.packedIndex() },
                        ),
                    direction = PixelPatchDirection.Forward,
                ),
            )
        }

        private fun validateChanges(
            canvas: CanvasSize,
            changes: List<PixelChange>,
        ): PixelPatchCreationRejection? {
            val outside = changes.firstOrNull { change -> !canvas.contains(change.position) }
            val unchanged = changes.firstOrNull { change -> change.before == change.after }
            val duplicate = changes.zipWithNext().firstOrNull { (first, second) -> first.position == second.position }
            val outsideStorage =
                changes.firstNotNullOfOrNull { change ->
                    val before = change.before.indexOrNull()
                    val after = change.after.indexOrNull()
                    when {
                        before != null && before.value > U8_MASK -> change.position to before
                        after != null && after.value > U8_MASK -> change.position to after
                        else -> null
                    }
                }
            return when {
                changes.isEmpty() -> {
                    PixelPatchCreationRejection.EmptyPatch
                }

                outside != null -> {
                    PixelPatchCreationRejection.PositionOutsideCanvas(canvas, outside.position)
                }

                unchanged != null -> {
                    PixelPatchCreationRejection.UnchangedPixel(unchanged.position)
                }

                duplicate != null -> {
                    PixelPatchCreationRejection.DuplicatePosition(duplicate.first.position)
                }

                outsideStorage != null -> {
                    PixelPatchCreationRejection.IndexAboveStorageMaximum(
                        outsideStorage.first,
                        outsideStorage.second,
                        U8_MASK,
                    )
                }

                else -> {
                    null
                }
            }
        }

        private fun creationRejected(rejection: PixelPatchCreationRejection): PixelPatchCreationResult =
            PixelPatchCreationResult.Rejected(rejection)

        private fun affectedRegion(
            canvas: CanvasSize,
            positions: IntArray,
            positionsAreContiguous: Boolean,
        ): PixelRegion {
            val bounds =
                if (positionsAreContiguous) {
                    contiguousPositionBounds(canvas, positions)
                } else {
                    positionBounds(canvas, positions)
                }
            return affectedRegion(canvas, bounds)
        }

        private fun positionBounds(
            canvas: CanvasSize,
            positions: IntArray,
        ): PixelBounds {
            val width = canvas.width.value
            var minimumX = width
            var minimumY = canvas.height.value
            var maximumX = 0
            var maximumY = 0
            positions.forEach { packed ->
                val index = packed and POSITION_MASK
                val x = index % width
                val y = index / width
                minimumX = minOf(minimumX, x)
                minimumY = minOf(minimumY, y)
                maximumX = maxOf(maximumX, x)
                maximumY = maxOf(maximumY, y)
            }
            return PixelBounds(minimumX, minimumY, maximumX, maximumY)
        }

        private fun contiguousPositionBounds(
            canvas: CanvasSize,
            positions: IntArray,
        ): PixelBounds {
            val width = canvas.width.value
            val first = positions.first() and POSITION_MASK
            val last = positions.last() and POSITION_MASK
            val minimumY = first / width
            val maximumY = last / width
            val withinOneRow = minimumY == maximumY
            return PixelBounds(
                minimumX = if (withinOneRow) first % width else 0,
                minimumY = minimumY,
                maximumX = if (withinOneRow) last % width else width - 1,
                maximumY = maximumY,
            )
        }

        private fun affectedRegion(
            canvas: CanvasSize,
            bounds: PixelBounds,
        ): PixelRegion {
            val origin = pixelPosition(bounds.minimumX, bounds.minimumY)
            val size =
                CanvasSize.create(
                    CanvasWidth.create(bounds.maximumX - bounds.minimumX + 1).requiredValue(),
                    CanvasHeight.create(bounds.maximumY - bounds.minimumY + 1).requiredValue(),
                )
            return PixelRegion.create(canvas, origin, size).requiredValue()
        }

        private fun rejected(rejection: PixelPatchApplicationRejection): PixelPatchApplicationResult =
            PixelPatchApplicationResult.Rejected(rejection)

        private val ROW_MAJOR_ORDER: Comparator<PixelChange> =
            compareBy(
                { change -> change.position.y.value },
                { change -> change.position.x.value },
            )

        private const val U8_MASK: Int = 0xff
    }
}

private data class PixelBounds(
    val minimumX: Int,
    val minimumY: Int,
    val maximumX: Int,
    val maximumY: Int,
)

private class PixelPatchStorage(
    val positions: IntArray,
    val before: ByteArray,
    val after: ByteArray,
)

private class PixelPatchChanges(
    private val storage: PixelPatchStorage,
    direction: PixelPatchDirection,
) {
    private val forward: Boolean = direction == PixelPatchDirection.Forward

    fun positionAt(index: Int): Int = storage.positions[index] and POSITION_MASK

    fun beforeCoveredAt(index: Int): Boolean =
        storage.positions[index] and (if (forward) BEFORE_COVERED_BIT else AFTER_COVERED_BIT) != 0

    fun afterCoveredAt(index: Int): Boolean =
        storage.positions[index] and (if (forward) AFTER_COVERED_BIT else BEFORE_COVERED_BIT) != 0

    fun beforeCellAt(index: Int): PixelCell =
        if (beforeCoveredAt(index)) PixelCell.Covered(beforeAt(index).toPaletteIndex()) else PixelCell.Empty

    fun beforeAt(index: Int): Byte = if (forward) storage.before[index] else storage.after[index]

    fun afterAt(index: Int): Byte = if (forward) storage.after[index] else storage.before[index]
}

private enum class PixelPatchDirection {
    Forward,
    Reverse,
    ;

    fun inverse(): PixelPatchDirection = if (this == Forward) Reverse else Forward
}

internal fun packPatchPosition(
    rowMajorIndex: Int,
    beforeCovered: Boolean,
    afterCovered: Boolean,
): Int {
    var packed = rowMajorIndex
    if (beforeCovered) packed = packed or BEFORE_COVERED_BIT
    if (afterCovered) packed = packed or AFTER_COVERED_BIT
    return packed
}

private const val POSITION_MASK: Int = 0xffff
private const val BEFORE_COVERED_BIT: Int = 1 shl 16
private const val AFTER_COVERED_BIT: Int = 1 shl 17

private fun PixelCell.indexOrNull(): PaletteIndex? = (this as? PixelCell.Covered)?.index

private fun PixelCell.packedIndex(): Byte = indexOrNull()?.value?.toByte() ?: 0

private fun CanvasSize.positionAt(index: Int): PixelPosition = pixelPosition(index % width.value, index / width.value)

private fun PixelPosition.rowMajorIndex(size: CanvasSize): Int = y.value * size.width.value + x.value

private fun pixelPosition(
    x: Int,
    y: Int,
): PixelPosition = PixelPosition.create(PixelX.create(x).requiredValue(), PixelY.create(y).requiredValue())

private fun Byte.toPaletteIndex(): PaletteIndex = PaletteIndex.create(toInt() and UNSIGNED_BYTE_MASK).requiredValue()

private const val UNSIGNED_BYTE_MASK: Int = 0xff

private fun <T> DomainValueResult<T>.requiredValue(): T =
    when (this) {
        is DomainValueResult.Created -> value
        is DomainValueResult.Rejected -> error("A validated pixel-patch invariant was rejected: $rejection")
    }
