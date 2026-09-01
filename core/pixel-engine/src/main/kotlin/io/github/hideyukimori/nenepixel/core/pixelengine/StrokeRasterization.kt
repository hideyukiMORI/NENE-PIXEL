package io.github.hideyukimori.nenepixel.core.pixelengine

import io.github.hideyukimori.nenepixel.core.domain.drawing.Stroke
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
    val changes = stroke.pixelChanges(snapshot)
    return if (changes.isEmpty()) {
        StrokeRasterizationResult.NoChanges
    } else {
        changes.toRasterizationResult(snapshot)
    }
}

private fun List<PixelChange>.toRasterizationResult(snapshot: PixelSnapshot): StrokeRasterizationResult =
    when (val result = PixelPatch.create(snapshot.size, snapshot.revision, this)) {
        is PixelPatchCreationResult.Created -> StrokeRasterizationResult.Rasterized(result.patch)
        is PixelPatchCreationResult.Rejected -> result.rejection.toRasterizationResult()
    }

private fun Stroke.pixelChanges(snapshot: PixelSnapshot): List<PixelChange> {
    val seen = mutableSetOf<PixelPosition>()
    val changes = mutableListOf<PixelChange>()
    forEachPosition { position ->
        if (seen.add(position)) {
            val before = snapshot.colorAt(position).requiredValue()
            if (before != color) {
                changes.add(PixelChange.create(position, before, color))
            }
        }
    }
    return changes
}

private fun PixelPatchCreationRejection.toRasterizationResult(): StrokeRasterizationResult =
    when (this) {
        PixelPatchCreationRejection.RevisionOverflow -> {
            rejected(StrokeRasterizationRejection.RevisionOverflow)
        }

        PixelPatchCreationRejection.EmptyPatch -> {
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

private fun <T> DomainValueResult<T>.requiredValue(): T =
    when (this) {
        is DomainValueResult.Created -> value
        is DomainValueResult.Rejected -> error("A validated stroke position was rejected: $rejection")
    }
