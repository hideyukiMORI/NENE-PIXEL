package io.github.hideyukimori.nenepixel.core.pixelengine

import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.drawing.Stroke
import io.github.hideyukimori.nenepixel.core.domain.drawing.StrokeEffect
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot

public fun rasterizeStroke(
    snapshot: PixelSnapshot,
    stroke: Stroke,
): StrokeRasterizationResult =
    if (stroke.canvas != snapshot.size) {
        rejected(StrokeRasterizationRejection.CanvasMismatch(stroke.canvas, snapshot.size))
    } else {
        rasterizeMatchingCanvas(snapshot, stroke)
    }

private fun rasterizeMatchingCanvas(
    snapshot: PixelSnapshot,
    stroke: Stroke,
): StrokeRasterizationResult {
    val target = stroke.effect.targetColor().toPackedRgba8888()
    val canvasPixels = snapshot.size.pixelCount.toInt()
    val sourcePixels = snapshot.copyPackedRgba8888()
    val collection =
        EffectivePositionCollector(
            canvasPixels = canvasPixels,
            capacity = minOf(stroke.positionCount, canvasPixels),
        ).collect(stroke, sourcePixels, target)
    val positions = collection.positions
    return if (positions.isEmpty()) {
        StrokeRasterizationResult.NoChanges
    } else {
        val before = IntArray(positions.size) { index -> sourcePixels[positions[index]] }
        PixelPatch
            .createFromValidatedPackedRgba8888(
                snapshot.size,
                snapshot.revision,
                positions,
                before,
                IntArray(positions.size) { target },
                positionsAreContiguous = collection.positionsAreContiguous,
            ).toRasterizationResult()
    }
}

private fun StrokeEffect.targetColor(): PixelColor =
    when (this) {
        is StrokeEffect.Paint -> color
        StrokeEffect.Erase -> PixelColor.blank
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
        sourcePixels: IntArray,
        target: Int,
    ): EffectivePositionCollection {
        repeat(stroke.positionCount) { pathIndex ->
            accept(stroke.rowMajorIndexAt(pathIndex), sourcePixels, target)
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
        sourcePixels: IntArray,
        target: Int,
    ) {
        if (seen[index]) return
        seen[index] = true
        if (sourcePixels[index] == target) return
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
        PixelPatchCreationRejection.RevisionOverflow -> {
            rejected(StrokeRasterizationRejection.RevisionOverflow)
        }

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
    }

private fun unexpectedPatchRejection(rejection: PixelPatchCreationRejection): Nothing =
    error("Validated stroke rasterization produced an invalid patch: $rejection")

private fun rejected(rejection: StrokeRasterizationRejection): StrokeRasterizationResult =
    StrokeRasterizationResult.Rejected(rejection)
