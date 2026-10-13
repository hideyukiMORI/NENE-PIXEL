package io.github.hideyukimori.nenepixel.core.application.workspace

import io.github.hideyukimori.nenepixel.core.application.document.command.CommandSourceAdmission
import io.github.hideyukimori.nenepixel.core.domain.drawing.Stroke
import io.github.hideyukimori.nenepixel.core.domain.drawing.StrokeEffect
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.DocumentPoint
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelX
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelY
import io.github.hideyukimori.nenepixel.core.domain.geometry.toDocumentPoint
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelLimits
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import kotlin.math.abs
import kotlin.math.max

public class ToolGesture private constructor(
    internal val capture: ToolGestureAdmission,
    private val path: GesturePath,
) {
    public val canvas: CanvasSize get() = capture.canvas
    public val effect: StrokeEffect get() = capture.effect

    /** The layer captured with [effect] at gesture start; later selection changes never retarget it (ADR 0030). */
    public val layerId: LayerId get() = capture.layerId
    internal val admission: CommandSourceAdmission get() = capture.admission
    private val latestSample: GestureSample get() = path.latestSample
    private val sampleCount: Int get() = path.sampleCount
    private val sampleHash: Int get() = path.sampleHash

    /** Expanded traversal count, including cells outside [canvas], bounded before accepting each sample. */
    public val positionCount: Int get() = path.positionCount

    internal val lastPosition: DocumentPoint
        get() = latestSample.position

    public fun forEachPosition(action: (PixelPosition) -> Unit) {
        val samples = samplesInPathOrder()
        val containedAction: (DocumentPoint) -> Unit = { point ->
            if (canvas.contains(point)) action(pixelPosition(point.x, point.y))
        }
        containedAction(samples.first())
        for (index in 1 until samples.size) {
            forEachSegmentPosition(samples[index - 1], samples[index], containedAction)
        }
    }

    internal fun extend(position: DocumentPoint): ToolGestureExtensionResult {
        if (position == lastPosition) return ToolGestureExtensionResult.Duplicate
        val attemptedCount = positionCount.toLong() + segmentAddition(lastPosition, position)
        return if (attemptedCount > PixelLimits.MAX_RAW_STROKE_POSITIONS) {
            ToolGestureExtensionResult.AboveSupportedMaximum(attemptedCount)
        } else {
            ToolGestureExtensionResult.Extended(
                ToolGesture(
                    capture = capture,
                    path =
                        GesturePath(
                            latestSample = GestureSample(position, latestSample),
                            sampleCount = sampleCount + 1,
                            sampleHash = sampleHash * HASH_MULTIPLIER + position.hashCode(),
                            positionCount = attemptedCount.toInt(),
                        ),
                ),
            )
        }
    }

    internal fun prepareStroke(): Stroke {
        val path = buildList { forEachPosition(::add) }
        return when (val result = Stroke.create(canvas, path, effect)) {
            is DomainValueResult.Created -> {
                result.value
            }

            is DomainValueResult.Rejected -> {
                error(
                    "Validated ToolGesture produced an invalid Stroke: ${result.rejection}",
                )
            }
        }
    }

    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is ToolGesture &&
                    canvas == other.canvas &&
                    effect == other.effect &&
                    layerId == other.layerId &&
                    admission === other.admission &&
                    sampleCount == other.sampleCount &&
                    positionCount == other.positionCount &&
                    sampleHash == other.sampleHash &&
                    (
                        latestSample === other.latestSample ||
                            samplesInPathOrder().contentEquals(other.samplesInPathOrder())
                    )
            )

    override fun hashCode(): Int =
        (
            ((canvas.hashCode() * HASH_MULTIPLIER) + effect.hashCode()) * HASH_MULTIPLIER + layerId.hashCode()
        ) * HASH_MULTIPLIER + sampleHash

    override fun toString(): String =
        "ToolGesture(canvas=$canvas, sampleCount=$sampleCount, positionCount=$positionCount, effect=$effect, " +
            "layerId=$layerId)"

    private fun samplesInPathOrder(): Array<DocumentPoint> {
        val samples = Array(sampleCount) { latestSample.position }
        var sample: GestureSample? = latestSample
        for (index in samples.lastIndex downTo 0) {
            samples[index] = checkNotNull(sample).position
            sample = sample.previous
        }
        return samples
    }

    internal companion object {
        private const val HASH_MULTIPLIER: Int = 31

        fun begin(
            capture: ToolGestureAdmission,
            position: PixelPosition,
        ): ToolGesture =
            ToolGesture(
                capture = capture,
                path = GesturePath(GestureSample(position.toDocumentPoint(), null), 1, position.hashCode(), 1),
            )
    }
}

private class GesturePath(
    val latestSample: GestureSample,
    val sampleCount: Int,
    val sampleHash: Int,
    val positionCount: Int,
)

private class GestureSample(
    val position: DocumentPoint,
    val previous: GestureSample?,
)

internal sealed interface ToolGestureExtensionResult {
    data class Extended(
        val gesture: ToolGesture,
    ) : ToolGestureExtensionResult

    data object Duplicate : ToolGestureExtensionResult

    data class AboveSupportedMaximum(
        val attemptedCount: Long,
    ) : ToolGestureExtensionResult
}

private fun segmentAddition(
    start: DocumentPoint,
    end: DocumentPoint,
): Long = max(abs(end.x.toLong() - start.x), abs(end.y.toLong() - start.y))

private fun forEachSegmentPosition(
    start: DocumentPoint,
    end: DocumentPoint,
    action: (DocumentPoint) -> Unit,
) {
    val canonicalOrder = start.precedes(end)
    val canonicalStart = if (canonicalOrder) start else end
    val canonicalEnd = if (canonicalOrder) end else start
    val steps = segmentAddition(start, end).toInt()
    val indexes = if (canonicalOrder) 1..steps else (steps - 1) downTo 0
    indexes.forEach { index ->
        action(interpolatedPosition(canonicalStart, canonicalEnd, index, steps))
    }
}

private fun DocumentPoint.precedes(other: DocumentPoint): Boolean = y < other.y || (y == other.y && x < other.x)

private fun interpolatedPosition(
    start: DocumentPoint,
    end: DocumentPoint,
    index: Int,
    steps: Int,
): DocumentPoint =
    DocumentPoint.create(
        (start.x.toLong() + roundedRatio((end.x.toLong() - start.x) * index, steps)).toInt(),
        (start.y.toLong() + roundedRatio((end.y.toLong() - start.y) * index, steps)).toInt(),
    )

private fun roundedRatio(
    numerator: Long,
    denominator: Int,
): Long =
    if (numerator >= 0) {
        (numerator + denominator / 2) / denominator
    } else {
        -((-numerator + denominator / 2) / denominator)
    }

private fun pixelPosition(
    x: Int,
    y: Int,
): PixelPosition = PixelPosition.create(PixelX.create(x).requiredValue(), PixelY.create(y).requiredValue())

private fun <T> DomainValueResult<T>.requiredValue(): T =
    when (this) {
        is DomainValueResult.Created -> value
        is DomainValueResult.Rejected -> error("Interpolated gesture invariant was rejected: $rejection")
    }
