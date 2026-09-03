package io.github.hideyukimori.nenepixel.core.pixelengine

import io.github.hideyukimori.nenepixel.core.domain.drawing.Stroke
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult

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
    val target = stroke.color.toPackedRgba8888()
    val canvasPixels = snapshot.size.pixelCount.toInt()
    val sourcePixels = snapshot.copyPackedRgba8888()
    val seen = BooleanArray(canvasPixels)
    val effective = IntArray(minOf(stroke.positionCount, canvasPixels))
    var changeCount = 0
    stroke.forEachPosition { position ->
        val index = position.rowMajorIndex(snapshot.size)
        if (!seen[index]) {
            seen[index] = true
            if (sourcePixels[index] != target) {
                effective[changeCount] = index
                changeCount += 1
            }
        }
    }
    return if (changeCount == 0) {
        StrokeRasterizationResult.NoChanges
    } else {
        val positions = effective.copyOf(changeCount).also(IntArray::sort)
        val before = IntArray(changeCount) { index -> sourcePixels[positions[index]] }
        PixelPatch
            .createPackedRgba8888(
                snapshot.size,
                snapshot.revision,
                positions,
                before,
                IntArray(changeCount) { target },
            ).toRasterizationResult()
    }
}

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

private fun PixelPosition.rowMajorIndex(size: CanvasSize): Int = y.value * size.width.value + x.value

private fun <T> DomainValueResult<T>.requiredValue(): T =
    when (this) {
        is DomainValueResult.Created -> value
        is DomainValueResult.Rejected -> error("A validated stroke position was rejected: $rejection")
    }
