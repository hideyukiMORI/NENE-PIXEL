package io.github.hideyukimori.nenepixel.core.application.workspace.viewport

import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelX
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelY
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.min

public class ViewportTransform private constructor(
    private val canvas: CanvasSize,
    private val surface: ViewportSurface,
    public val viewport: ViewportState,
    private val geometry: TransformGeometry,
) {
    public val gridVisibility: ViewportGridVisibility
        get() = geometry.gridVisibility

    public fun toSurfaceBounds(position: PixelPosition): ViewportMappingResult<ViewportSurfaceBounds> =
        if (!canvas.contains(position)) {
            ViewportMappingResult.OutsideCanvas
        } else {
            viewportMapped(
                ViewportSurfaceBounds(
                    left = edge(geometry.origin.x, geometry.cellScale, position.x.value),
                    top = edge(geometry.origin.y, geometry.cellScale, position.y.value),
                    right = edge(geometry.origin.x, geometry.cellScale, position.x.value + 1),
                    bottom = edge(geometry.origin.y, geometry.cellScale, position.y.value + 1),
                ),
            )
        }

    public fun toPixelPosition(point: ViewportSurfacePoint): ViewportMappingResult<PixelPosition> =
        when {
            !surfaceContains(point) -> {
                ViewportMappingResult.OutsideSurface
            }

            !projectedCanvasContains(point) -> {
                ViewportMappingResult.OutsideCanvas
            }

            else -> {
                val x = canonicalPixelIndex(point.xPixels, geometry.origin.x, canvas.width.value)
                val y = canonicalPixelIndex(point.yPixels, geometry.origin.y, canvas.height.value)
                viewportMapped(PixelPosition.create(pixelX(x), pixelY(y)))
            }
        }

    public fun apply(gesture: ViewportGesture): ViewportValueResult<ViewportState> {
        val measure = gesture.measure()
        return if (measure.isFinite()) {
            applyFiniteMeasure(measure)
        } else {
            viewportRejected(ViewportValueRejection.UnsafeDerivedGesture)
        }
    }

    private fun applyFiniteMeasure(measure: GestureMeasure): ViewportValueResult<ViewportState> {
        val ratio =
            if (measure.previousDistance <= surface.pixelsPerDp) {
                ViewportNumbers.MIN_ZOOM
            } else {
                measure.currentDistance / measure.previousDistance
            }
        val zoomValue = viewport.zoom.value * ratio
        return if (ViewportNumbers.areFinite(ratio, zoomValue)) {
            applyMeasure(measure, ViewportZoom.bounded(zoomValue))
        } else {
            viewportRejected(ViewportValueRejection.UnsafeDerivedGesture)
        }
    }

    private fun applyMeasure(
        measure: GestureMeasure,
        nextZoom: ViewportZoom,
    ): ViewportValueResult<ViewportState> {
        val nextScale = geometry.fit * nextZoom.value
        val midpoint = surface.midpoint()
        val anchor =
            AxisPair(
                viewport.center.x + (measure.previousCentroid.x - midpoint.x) / geometry.cellScale,
                viewport.center.y + (measure.previousCentroid.y - midpoint.y) / geometry.cellScale,
            )
        val nextCenter =
            AxisPair(
                anchor.x - (measure.currentCentroid.x - midpoint.x) / nextScale,
                anchor.y - (measure.currentCentroid.y - midpoint.y) / nextScale,
            )
        if (!ViewportNumbers.areFinite(nextScale, anchor.x, anchor.y, nextCenter.x, nextCenter.y)) {
            return viewportRejected(ViewportValueRejection.UnsafeDerivedGesture)
        }
        return createAppliedViewport(nextZoom, nextCenter)
    }

    private fun createAppliedViewport(
        nextZoom: ViewportZoom,
        nextCenter: AxisPair,
    ): ViewportValueResult<ViewportState> =
        when (val center = ViewportCenter.create(nextCenter.x, nextCenter.y)) {
            is ViewportValueResult.Created -> {
                val preferred = ViewportState.create(nextZoom, center.value)
                when (val transform = create(canvas, surface, preferred)) {
                    is ViewportValueResult.Created -> viewportCreated(transform.value.viewport)
                    is ViewportValueResult.Rejected -> transform
                }
            }

            is ViewportValueResult.Rejected -> {
                viewportRejected(ViewportValueRejection.UnsafeDerivedGesture)
            }
        }

    private fun surfaceContains(point: ViewportSurfacePoint): Boolean =
        point.xPixels >= 0.0 &&
            point.xPixels < surface.widthPixels.toDouble() &&
            point.yPixels >= 0.0 &&
            point.yPixels < surface.heightPixels.toDouble()

    private fun projectedCanvasContains(point: ViewportSurfacePoint): Boolean =
        point.xPixels >= edge(geometry.origin.x, geometry.cellScale, 0) &&
            point.xPixels < edge(geometry.origin.x, geometry.cellScale, canvas.width.value) &&
            point.yPixels >= edge(geometry.origin.y, geometry.cellScale, 0) &&
            point.yPixels < edge(geometry.origin.y, geometry.cellScale, canvas.height.value)

    private fun canonicalPixelIndex(
        coordinate: Double,
        origin: Double,
        extent: Int,
    ): Int {
        val quotient = floor((coordinate - origin) / geometry.cellScale)
        val initial = quotient.toLong().coerceIn(0L, extent.toLong() - 1L).toInt()
        return when {
            coordinate < edge(origin, geometry.cellScale, initial) -> initial - 1
            initial < extent - 1 && coordinate >= edge(origin, geometry.cellScale, initial + 1) -> initial + 1
            else -> initial
        }
    }

    public companion object {
        public fun create(
            canvas: CanvasSize,
            surface: ViewportSurface,
            viewport: ViewportState,
        ): ViewportValueResult<ViewportTransform> {
            val fit =
                min(
                    surface.widthPixels.toDouble() / canvas.width.value.toDouble(),
                    surface.heightPixels.toDouble() / canvas.height.value.toDouble(),
                )
            val scale = fit * viewport.zoom.value
            val halfVisible =
                AxisPair(
                    surface.widthPixels.toDouble() / (DIVISOR * scale),
                    surface.heightPixels.toDouble() / (DIVISOR * scale),
                )
            val effectiveCenter = effectiveCenter(canvas, viewport.center, halfVisible)
            val origin =
                AxisPair(
                    surface.widthPixels.toDouble() / DIVISOR - effectiveCenter.x * scale,
                    surface.heightPixels.toDouble() / DIVISOR - effectiveCenter.y * scale,
                )
            val gridCellDp = scale / surface.pixelsPerDp
            if (!derivedTransformIsSafe(canvas, fit, scale, halfVisible, effectiveCenter, origin, gridCellDp)) {
                return viewportRejected(ViewportValueRejection.UnsafeDerivedTransform)
            }
            return createValidated(canvas, surface, viewport.zoom, fit, scale, effectiveCenter, origin, gridCellDp)
        }

        private fun createValidated(
            canvas: CanvasSize,
            surface: ViewportSurface,
            zoom: ViewportZoom,
            fit: Double,
            scale: Double,
            effectiveCenter: AxisPair,
            origin: AxisPair,
            gridCellDp: Double,
        ): ViewportValueResult<ViewportTransform> =
            when (val center = ViewportCenter.create(effectiveCenter.x, effectiveCenter.y)) {
                is ViewportValueResult.Created -> {
                    val geometry =
                        TransformGeometry(
                            fit,
                            scale,
                            origin,
                            gridVisibility(gridCellDp),
                        )
                    viewportCreated(
                        ViewportTransform(
                            canvas,
                            surface,
                            ViewportState.create(zoom, center.value),
                            geometry,
                        ),
                    )
                }

                is ViewportValueResult.Rejected -> {
                    viewportRejected(ViewportValueRejection.UnsafeDerivedTransform)
                }
            }

        private fun effectiveCenter(
            canvas: CanvasSize,
            preferred: ViewportCenter,
            halfVisible: AxisPair,
        ): AxisPair =
            AxisPair(
                effectiveAxis(canvas.width.value, preferred.x, halfVisible.x),
                effectiveAxis(canvas.height.value, preferred.y, halfVisible.y),
            )

        private fun effectiveAxis(
            extent: Int,
            preferred: Double,
            halfVisible: Double,
        ): Double {
            val midpoint = extent.toDouble() / DIVISOR
            return if (halfVisible >= midpoint) {
                midpoint
            } else {
                preferred.coerceIn(halfVisible, extent.toDouble() - halfVisible)
            }
        }

        private fun derivedTransformIsSafe(
            canvas: CanvasSize,
            fit: Double,
            scale: Double,
            halfVisible: AxisPair,
            effectiveCenter: AxisPair,
            origin: AxisPair,
            gridCellDp: Double,
        ): Boolean =
            ViewportNumbers.areFinite(
                fit,
                scale,
                halfVisible.x,
                halfVisible.y,
                effectiveCenter.x,
                effectiveCenter.y,
                origin.x,
                origin.y,
                gridCellDp,
            ) &&
                scale > 0.0 &&
                axisEdgesAreStrict(origin.x, scale, canvas.width.value) &&
                axisEdgesAreStrict(origin.y, scale, canvas.height.value)

        private fun axisEdgesAreStrict(
            origin: Double,
            scale: Double,
            extent: Int,
        ): Boolean {
            val first = edge(origin, scale, 0)
            val second = edge(origin, scale, 1)
            val penultimate = edge(origin, scale, extent - 1)
            val last = edge(origin, scale, extent)
            return ViewportNumbers.areFinite(first, second, penultimate, last) &&
                first < second &&
                penultimate < last
        }

        private fun gridVisibility(cellDp: Double): ViewportGridVisibility =
            if (cellDp >= ViewportNumbers.GRID_THRESHOLD_DP) {
                ViewportGridVisibility.Visible
            } else {
                ViewportGridVisibility.Hidden
            }

        private const val DIVISOR: Double = 2.0
    }
}

private data class AxisPair(
    val x: Double,
    val y: Double,
)

private data class TransformGeometry(
    val fit: Double,
    val cellScale: Double,
    val origin: AxisPair,
    val gridVisibility: ViewportGridVisibility,
)

private data class GestureMeasure(
    val previousCentroid: AxisPair,
    val currentCentroid: AxisPair,
    val previousDistance: Double,
    val currentDistance: Double,
) {
    fun isFinite(): Boolean =
        ViewportNumbers.areFinite(
            previousCentroid.x,
            previousCentroid.y,
            currentCentroid.x,
            currentCentroid.y,
            previousDistance,
            currentDistance,
        )
}

private fun ViewportGesture.measure(): GestureMeasure =
    GestureMeasure(
        previousCentroid = previousFirst.midpoint(previousSecond),
        currentCentroid = currentFirst.midpoint(currentSecond),
        previousDistance = previousFirst.distanceTo(previousSecond),
        currentDistance = currentFirst.distanceTo(currentSecond),
    )

private fun ViewportSurfacePoint.midpoint(other: ViewportSurfacePoint): AxisPair =
    AxisPair(
        xPixels / 2.0 + other.xPixels / 2.0,
        yPixels / 2.0 + other.yPixels / 2.0,
    )

private fun ViewportSurfacePoint.distanceTo(other: ViewportSurfacePoint): Double =
    hypot(xPixels - other.xPixels, yPixels - other.yPixels)

private fun ViewportSurface.midpoint(): AxisPair = AxisPair(widthPixels / 2.0, heightPixels / 2.0)

private fun edge(
    origin: Double,
    scale: Double,
    index: Int,
): Double = origin + index.toDouble() * scale

private fun pixelX(value: Int): PixelX =
    when (val result = PixelX.create(value)) {
        is DomainValueResult.Created -> result.value
        is DomainValueResult.Rejected -> error("Validated viewport produced an invalid PixelX: ${result.rejection}")
    }

private fun pixelY(value: Int): PixelY =
    when (val result = PixelY.create(value)) {
        is DomainValueResult.Created -> result.value
        is DomainValueResult.Rejected -> error("Validated viewport produced an invalid PixelY: ${result.rejection}")
    }
