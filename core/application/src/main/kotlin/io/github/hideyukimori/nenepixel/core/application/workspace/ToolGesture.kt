package io.github.hideyukimori.nenepixel.core.application.workspace

import io.github.hideyukimori.nenepixel.core.domain.drawing.Stroke
import io.github.hideyukimori.nenepixel.core.domain.drawing.StrokeEffect
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelX
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelY
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelLimits
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import kotlin.math.abs
import kotlin.math.max

public class ToolGesture private constructor(
    public val canvas: CanvasSize,
    private val latestSample: GestureSample,
    private val sampleCount: Int,
    private val sampleHash: Int,
    public val positionCount: Int,
    public val effect: StrokeEffect,
) {
    internal val lastPosition: PixelPosition
        get() = latestSample.position

    public fun forEachPosition(action: (PixelPosition) -> Unit) {
        val samples = samplesInPathOrder()
        action(samples.first())
        for (index in 1 until samples.size) {
            forEachSegmentPosition(samples[index - 1], samples[index], action)
        }
    }

    internal fun extend(position: PixelPosition): ToolGestureExtensionResult {
        if (position == lastPosition) return ToolGestureExtensionResult.Duplicate
        val attemptedCount = positionCount.toLong() + segmentAddition(lastPosition, position)
        return if (attemptedCount > PixelLimits.MAX_RAW_STROKE_POSITIONS) {
            ToolGestureExtensionResult.AboveSupportedMaximum(attemptedCount)
        } else {
            ToolGestureExtensionResult.Extended(
                ToolGesture(
                    canvas = canvas,
                    latestSample = GestureSample(position, latestSample),
                    sampleCount = sampleCount + 1,
                    sampleHash = sampleHash * HASH_MULTIPLIER + position.hashCode(),
                    positionCount = attemptedCount.toInt(),
                    effect = effect,
                ),
            )
        }
    }

    internal fun prepareStroke(): Stroke {
        val path = buildList(positionCount) { forEachPosition(::add) }
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
                    sampleCount == other.sampleCount &&
                    positionCount == other.positionCount &&
                    sampleHash == other.sampleHash &&
                    (
                        latestSample === other.latestSample ||
                            samplesInPathOrder().contentEquals(other.samplesInPathOrder())
                    )
            )

    override fun hashCode(): Int =
        ((canvas.hashCode() * HASH_MULTIPLIER) + effect.hashCode()) * HASH_MULTIPLIER + sampleHash

    override fun toString(): String =
        "ToolGesture(canvas=$canvas, sampleCount=$sampleCount, positionCount=$positionCount, effect=$effect)"

    private fun samplesInPathOrder(): Array<PixelPosition> {
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
            canvas: CanvasSize,
            position: PixelPosition,
            effect: StrokeEffect,
        ): ToolGesture =
            ToolGesture(
                canvas = canvas,
                latestSample = GestureSample(position, null),
                sampleCount = 1,
                sampleHash = position.hashCode(),
                positionCount = 1,
                effect = effect,
            )
    }
}

private class GestureSample(
    val position: PixelPosition,
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
    start: PixelPosition,
    end: PixelPosition,
): Int = max(abs(end.x.value - start.x.value), abs(end.y.value - start.y.value))

private fun forEachSegmentPosition(
    start: PixelPosition,
    end: PixelPosition,
    action: (PixelPosition) -> Unit,
) {
    val canonicalOrder = start.precedes(end)
    val canonicalStart = if (canonicalOrder) start else end
    val canonicalEnd = if (canonicalOrder) end else start
    val steps = segmentAddition(start, end)
    val indexes = if (canonicalOrder) 1..steps else (steps - 1) downTo 0
    indexes.forEach { index ->
        action(interpolatedPosition(canonicalStart, canonicalEnd, index, steps))
    }
}

private fun PixelPosition.precedes(other: PixelPosition): Boolean =
    y.value < other.y.value || (y.value == other.y.value && x.value < other.x.value)

private fun interpolatedPosition(
    start: PixelPosition,
    end: PixelPosition,
    index: Int,
    steps: Int,
): PixelPosition =
    pixelPosition(
        start.x.value + roundedRatio((end.x.value - start.x.value) * index, steps),
        start.y.value + roundedRatio((end.y.value - start.y.value) * index, steps),
    )

private fun roundedRatio(
    numerator: Int,
    denominator: Int,
): Int =
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
