package io.github.hideyukimori.nenepixel.core.pixelengine

import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.document.Revision
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasHeight
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasWidth
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelRegion
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelX
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelY
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelLimits
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult

public class PixelPatch private constructor(
    public val canvas: CanvasSize,
    public val affectedRegion: PixelRegion,
    private val storage: PixelPatchStorage,
    private val direction: PixelPatchDirection,
) {
    public val beforeRevision: Revision
        get() = if (direction == PixelPatchDirection.Forward) storage.beforeRevision else storage.afterRevision

    public val afterRevision: Revision
        get() = if (direction == PixelPatchDirection.Forward) storage.afterRevision else storage.beforeRevision

    public val changeCount: Int
        get() = storage.positions.size

    public fun applyTo(snapshot: PixelSnapshot): PixelPatchApplicationResult =
        when {
            snapshot.size != canvas -> {
                rejected(PixelPatchApplicationRejection.CanvasMismatch(canvas, snapshot.size))
            }

            snapshot.revision != beforeRevision -> {
                rejected(PixelPatchApplicationRejection.RevisionMismatch(beforeRevision, snapshot.revision))
            }

            else -> {
                applyToMatchingSnapshot(snapshot)
            }
        }

    private fun applyToMatchingSnapshot(snapshot: PixelSnapshot): PixelPatchApplicationResult {
        val surface = PixelSurface.from(snapshot)
        repeat(changeCount) { index ->
            val positionIndex = storage.positions[index]
            val actual = surface.packedRgba8888At(positionIndex)
            val expected = beforeAt(index)
            if (actual != expected) {
                return rejected(
                    PixelPatchApplicationRejection.BeforeValueMismatch(
                        position = canvas.positionAt(positionIndex),
                        expected = PixelColor.fromPackedRgba8888(expected),
                        actual = PixelColor.fromPackedRgba8888(actual),
                    ),
                )
            }
        }
        repeat(changeCount) { index ->
            surface.writePackedRgba8888(storage.positions[index], afterAt(index))
        }
        return PixelPatchApplicationResult.Applied(surface.snapshot(afterRevision))
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
                    beforeRevision == other.beforeRevision &&
                    afterRevision == other.afterRevision &&
                    affectedRegion == other.affectedRegion &&
                    changesEqual(other)
            )

    private fun changesEqual(other: PixelPatch): Boolean {
        if (!storage.positions.contentEquals(other.storage.positions)) return false
        return storage.positions.indices.all { index ->
            beforeAt(index) == other.beforeAt(index) && afterAt(index) == other.afterAt(index)
        }
    }

    override fun hashCode(): Int {
        var result = canvas.hashCode()
        result = HASH_MULTIPLIER * result + beforeRevision.hashCode()
        result = HASH_MULTIPLIER * result + afterRevision.hashCode()
        result = HASH_MULTIPLIER * result + affectedRegion.hashCode()
        repeat(changeCount) { index ->
            result = HASH_MULTIPLIER * result + storage.positions[index]
            result = HASH_MULTIPLIER * result + beforeAt(index)
            result = HASH_MULTIPLIER * result + afterAt(index)
        }
        return result
    }

    override fun toString(): String =
        "PixelPatch(canvas=$canvas, beforeRevision=$beforeRevision, " +
            "afterRevision=$afterRevision, changeCount=$changeCount)"

    private fun beforeAt(index: Int): Int =
        if (direction == PixelPatchDirection.Forward) storage.before[index] else storage.after[index]

    private fun afterAt(index: Int): Int =
        if (direction == PixelPatchDirection.Forward) storage.after[index] else storage.before[index]

    public companion object {
        private const val HASH_MULTIPLIER: Int = 31

        public fun create(
            canvas: CanvasSize,
            beforeRevision: Revision,
            changes: List<PixelChange>,
        ): PixelPatchCreationResult {
            val afterRevision = beforeRevision.nextOrNull()
            return when {
                afterRevision == null -> {
                    creationRejected(PixelPatchCreationRejection.RevisionOverflow)
                }

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
                        createdPatch(canvas, beforeRevision, afterRevision, canonicalChanges)
                    } else {
                        creationRejected(rejection)
                    }
                }
            }
        }

        internal fun createPackedRgba8888(
            canvas: CanvasSize,
            beforeRevision: Revision,
            positions: IntArray,
            before: IntArray,
            after: IntArray,
        ): PixelPatchCreationResult {
            val afterRevision = beforeRevision.nextOrNull()
            return when {
                afterRevision == null -> {
                    creationRejected(PixelPatchCreationRejection.RevisionOverflow)
                }

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
                    check(positions.indices.all { index -> positions[index] in 0 until canvas.pixelCount.toInt() })
                    check(positions.isStrictlyAscending())
                    check(positions.indices.all { index -> before[index] != after[index] })
                    PixelPatchCreationResult.Created(
                        PixelPatch(
                            canvas = canvas,
                            affectedRegion = affectedRegion(canvas, positions),
                            storage =
                                PixelPatchStorage(
                                    beforeRevision,
                                    afterRevision,
                                    positions,
                                    before,
                                    after,
                                ),
                            direction = PixelPatchDirection.Forward,
                        ),
                    )
                }
            }
        }

        private fun createdPatch(
            canvas: CanvasSize,
            beforeRevision: Revision,
            afterRevision: Revision,
            changes: List<PixelChange>,
        ): PixelPatchCreationResult {
            val positions = IntArray(changes.size) { index -> changes[index].position.rowMajorIndex(canvas) }
            return PixelPatchCreationResult.Created(
                PixelPatch(
                    canvas = canvas,
                    affectedRegion = affectedRegion(canvas, positions),
                    storage =
                        PixelPatchStorage(
                            beforeRevision,
                            afterRevision,
                            positions,
                            IntArray(changes.size) { index -> changes[index].before.toPackedRgba8888() },
                            IntArray(changes.size) { index -> changes[index].after.toPackedRgba8888() },
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
            return when {
                changes.isEmpty() -> PixelPatchCreationRejection.EmptyPatch
                outside != null -> PixelPatchCreationRejection.PositionOutsideCanvas(canvas, outside.position)
                unchanged != null -> PixelPatchCreationRejection.UnchangedPixel(unchanged.position)
                duplicate != null -> PixelPatchCreationRejection.DuplicatePosition(duplicate.first.position)
                else -> null
            }
        }

        private fun Revision.nextOrNull(): Revision? =
            when (val result = advance()) {
                is DomainValueResult.Created -> result.value
                is DomainValueResult.Rejected -> null
            }

        private fun creationRejected(rejection: PixelPatchCreationRejection): PixelPatchCreationResult =
            PixelPatchCreationResult.Rejected(rejection)

        private fun affectedRegion(
            canvas: CanvasSize,
            positions: IntArray,
        ): PixelRegion {
            val width = canvas.width.value
            var minimumX = width
            var minimumY = canvas.height.value
            var maximumX = 0
            var maximumY = 0
            positions.forEach { index ->
                val x = index % width
                val y = index / width
                minimumX = minOf(minimumX, x)
                minimumY = minOf(minimumY, y)
                maximumX = maxOf(maximumX, x)
                maximumY = maxOf(maximumY, y)
            }
            val origin = pixelPosition(minimumX, minimumY)
            val size =
                CanvasSize.create(
                    CanvasWidth.create(maximumX - minimumX + 1).requiredValue(),
                    CanvasHeight.create(maximumY - minimumY + 1).requiredValue(),
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
    }
}

private class PixelPatchStorage(
    val beforeRevision: Revision,
    val afterRevision: Revision,
    val positions: IntArray,
    val before: IntArray,
    val after: IntArray,
)

private enum class PixelPatchDirection {
    Forward,
    Reverse,
    ;

    fun inverse(): PixelPatchDirection = if (this == Forward) Reverse else Forward
}

private fun CanvasSize.positionAt(index: Int): PixelPosition = pixelPosition(index % width.value, index / width.value)

private fun PixelPosition.rowMajorIndex(size: CanvasSize): Int = y.value * size.width.value + x.value

private fun IntArray.isStrictlyAscending(): Boolean {
    for (index in 1 until size) {
        if (this[index - 1] >= this[index]) return false
    }
    return true
}

private fun pixelPosition(
    x: Int,
    y: Int,
): PixelPosition = PixelPosition.create(PixelX.create(x).requiredValue(), PixelY.create(y).requiredValue())

private fun <T> DomainValueResult<T>.requiredValue(): T =
    when (this) {
        is DomainValueResult.Created -> value
        is DomainValueResult.Rejected -> error("A validated pixel-patch invariant was rejected: $rejection")
    }
