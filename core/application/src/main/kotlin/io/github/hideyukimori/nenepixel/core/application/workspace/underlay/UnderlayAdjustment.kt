package io.github.hideyukimori.nenepixel.core.application.workspace.underlay

import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportGesture
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportMappingResult
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportSurfaceBounds
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportSurfacePoint
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportTransform
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelX
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelY
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import kotlin.math.hypot

/**
 * The adjust-mode arithmetic of ADR 0032: surface-pixel finger motion under the current
 * [ViewportTransform] becomes a clamped [UnderlayPlacement]. Rotation is ignored; only the gesture
 * centroid and the distance between the two pointers are used. When the transform cannot map the
 * document's first cell, the underlay is returned unchanged.
 */
public object UnderlayAdjustment {
    /** A one-pointer drag from [from] to [to] translates the placement by the same document distance. */
    public fun moved(
        underlay: ReferenceUnderlay,
        from: ViewportSurfacePoint,
        to: ViewportSurfacePoint,
        transform: ViewportTransform,
    ): ReferenceUnderlay =
        originCell(transform)?.let { cell ->
            underlay.withPlacement(
                underlay.placement.left + (to.xPixels - from.xPixels) / cell.width(),
                underlay.placement.top + (to.yPixels - from.yPixels) / cell.height(),
                underlay.placement.scale,
            )
        } ?: underlay

    /**
     * A two-pointer gesture scales the placement by the change in pointer distance and keeps the
     * image point under the previous centroid under the current centroid.
     */
    public fun transformed(
        underlay: ReferenceUnderlay,
        gesture: ViewportGesture,
        transform: ViewportTransform,
    ): ReferenceUnderlay = originCell(transform)?.let { cell -> transformedIn(underlay, gesture, cell) } ?: underlay

    private fun transformedIn(
        underlay: ReferenceUnderlay,
        gesture: ViewportGesture,
        cell: ViewportSurfaceBounds,
    ): ReferenceUnderlay {
        val factor = factor(gesture)
        val previousX = documentX(midpoint(gesture.previousFirst.xPixels, gesture.previousSecond.xPixels), cell)
        val previousY = documentY(midpoint(gesture.previousFirst.yPixels, gesture.previousSecond.yPixels), cell)
        val currentX = documentX(midpoint(gesture.currentFirst.xPixels, gesture.currentSecond.xPixels), cell)
        val currentY = documentY(midpoint(gesture.currentFirst.yPixels, gesture.currentSecond.yPixels), cell)
        val placement = underlay.placement
        return underlay.withPlacement(
            currentX - (previousX - placement.left) * factor,
            currentY - (previousY - placement.top) * factor,
            placement.scale * factor,
        )
    }

    private fun factor(gesture: ViewportGesture): Double {
        val previous = distance(gesture.previousFirst, gesture.previousSecond)
        val factor = distance(gesture.currentFirst, gesture.currentSecond) / previous
        return if (previous < MIN_DISTANCE || !factor.isFinite() || factor <= 0.0) 1.0 else factor
    }

    private fun distance(
        first: ViewportSurfacePoint,
        second: ViewportSurfacePoint,
    ): Double = hypot(second.xPixels - first.xPixels, second.yPixels - first.yPixels)

    private fun midpoint(
        first: Double,
        second: Double,
    ): Double = first / 2.0 + second / 2.0

    private fun documentX(
        surfaceX: Double,
        cell: ViewportSurfaceBounds,
    ): Double = (surfaceX - cell.left) / cell.width()

    private fun documentY(
        surfaceY: Double,
        cell: ViewportSurfaceBounds,
    ): Double = (surfaceY - cell.top) / cell.height()

    private fun ViewportSurfaceBounds.width(): Double = right - left

    private fun ViewportSurfaceBounds.height(): Double = bottom - top

    /** The surface rectangle of document cell (0, 0), or null when it cannot be mapped or is degenerate. */
    private fun originCell(transform: ViewportTransform): ViewportSurfaceBounds? {
        val x = PixelX.create(0)
        val y = PixelY.create(0)
        val mapped =
            if (x is DomainValueResult.Created && y is DomainValueResult.Created) {
                transform.toSurfaceBounds(PixelPosition.create(x.value, y.value))
            } else {
                ViewportMappingResult.OutsideCanvas
            }
        return (mapped as? ViewportMappingResult.Mapped)?.value?.takeIf { cell ->
            cell.width().isFinite() && cell.width() > 0.0 && cell.height().isFinite() && cell.height() > 0.0
        }
    }

    private const val MIN_DISTANCE: Double = 1e-6
}
