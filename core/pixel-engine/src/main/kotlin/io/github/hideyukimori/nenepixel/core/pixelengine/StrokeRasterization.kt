package io.github.hideyukimori.nenepixel.core.pixelengine

import io.github.hideyukimori.nenepixel.core.domain.drawing.Stroke
import io.github.hideyukimori.nenepixel.core.domain.drawing.StrokeEffect
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot

public fun rasterizeStroke(
    snapshot: PixelSnapshot,
    stroke: Stroke,
): StrokeRasterizationResult {
    val effect = stroke.effect
    return when {
        stroke.canvas != snapshot.size -> {
            rejected(StrokeRasterizationRejection.CanvasMismatch(stroke.canvas, snapshot.size))
        }

        effect is StrokeEffect.Paint && effect.targetIndex.value > U8_MASK -> {
            rejected(
                StrokeRasterizationRejection.TargetIndexAboveStorageMaximum(
                    effect.targetIndex,
                    U8_MASK,
                ),
            )
        }

        else -> {
            rasterizeMatchingCanvas(snapshot, stroke, StrokeTarget.of(effect))
        }
    }
}

private fun rasterizeMatchingCanvas(
    snapshot: PixelSnapshot,
    stroke: Stroke,
    target: StrokeTarget,
): StrokeRasterizationResult {
    val canvasPixels = snapshot.size.pixelCount.toInt()
    val surface = PixelSurface.from(snapshot)
    val collection =
        EffectivePositionCollector(
            canvasPixels = canvasPixels,
            capacity = minOf(stroke.positionCount, canvasPixels),
        ).collect(stroke, surface, target)
    val positions = collection.positions
    return if (positions.isEmpty()) {
        StrokeRasterizationResult.NoChanges
    } else {
        val before = ByteArray(positions.size) { index -> surface.packedIndexAt(positions[index]) }
        val packedPositions =
            IntArray(positions.size) { index ->
                val position = positions[index]
                packPatchPosition(
                    position,
                    beforeCovered = surface.isCoveredAt(position),
                    afterCovered = target.covered,
                )
            }
        PixelPatch
            .createFromValidatedPackedIndices(
                snapshot.size,
                packedPositions,
                before,
                ByteArray(positions.size) { target.packedIndex },
                positionsAreContiguous = collection.positionsAreContiguous,
            ).toRasterizationResult()
    }
}

private class EffectivePositionCollector(
    canvasPixels: Int,
    capacity: Int,
) {
    private val seen = BooleanArray(canvasPixels)
    private val effective = IntArray(capacity)
    private var changeCount = 0
    private var lastEffectiveIndex = -1
    private var isCanonicalOrder = true
    private var positionsAreContiguous = true

    fun collect(
        stroke: Stroke,
        surface: PixelSurface,
        target: StrokeTarget,
    ): EffectivePositionCollection {
        repeat(stroke.positionCount) { pathIndex ->
            accept(stroke.rowMajorIndexAt(pathIndex), surface, target)
        }
        val positions = effective.copyOf(changeCount)
        if (!isCanonicalOrder) positions.sort()
        return EffectivePositionCollection(
            positions,
            positionsAreContiguous = isCanonicalOrder && positionsAreContiguous,
        )
    }

    private fun accept(
        index: Int,
        surface: PixelSurface,
        target: StrokeTarget,
    ) {
        if (seen[index]) return
        seen[index] = true
        if (target.matches(surface, index)) return
        if (changeCount > 0 && index != lastEffectiveIndex + 1) positionsAreContiguous = false
        if (index <= lastEffectiveIndex) isCanonicalOrder = false
        effective[changeCount] = index
        changeCount += 1
        lastEffectiveIndex = index
    }
}

private data class EffectivePositionCollection(
    val positions: IntArray,
    val positionsAreContiguous: Boolean,
)

private fun PixelPatchCreationResult.toRasterizationResult(): StrokeRasterizationResult =
    when (this) {
        is PixelPatchCreationResult.Created -> StrokeRasterizationResult.Rasterized(patch)
        is PixelPatchCreationResult.Rejected -> rejection.toRasterizationResult()
    }

private fun PixelPatchCreationRejection.toRasterizationResult(): StrokeRasterizationResult =
    when (this) {
        PixelPatchCreationRejection.EmptyPatch -> {
            unexpectedPatchRejection(this)
        }

        is PixelPatchCreationRejection.ChangeCountAboveSupportedMaximum -> {
            unexpectedPatchRejection(this)
        }

        is PixelPatchCreationRejection.PositionOutsideCanvas -> {
            unexpectedPatchRejection(this)
        }

        is PixelPatchCreationRejection.DuplicatePosition -> {
            unexpectedPatchRejection(this)
        }

        is PixelPatchCreationRejection.UnchangedPixel -> {
            unexpectedPatchRejection(this)
        }

        is PixelPatchCreationRejection.IndexAboveStorageMaximum -> {
            unexpectedPatchRejection(this)
        }
    }

private fun unexpectedPatchRejection(rejection: PixelPatchCreationRejection): Nothing =
    error("Validated stroke rasterization produced an invalid patch: $rejection")

private fun rejected(rejection: StrokeRasterizationRejection): StrokeRasterizationResult =
    StrokeRasterizationResult.Rejected(rejection)

private const val U8_MASK: Int = 0xff
