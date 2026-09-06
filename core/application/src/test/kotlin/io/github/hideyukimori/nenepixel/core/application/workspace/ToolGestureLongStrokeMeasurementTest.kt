package io.github.hideyukimori.nenepixel.core.application.workspace

import io.github.hideyukimori.nenepixel.core.domain.color.ColorChannel
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.drawing.StrokeEffect
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasHeight
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasWidth
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelX
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelY
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelLimits
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

internal class ToolGestureLongStrokeMeasurementTest {
    @Test
    fun `record bounded long stroke enumeration and commit preparation cost`() {
        assumeTrue(
            java.lang.Boolean.getBoolean(OPT_IN_PROPERTY),
            "Long-stroke timing is an explicit, finite-budget diagnostic.",
        )
        workloads().forEach(::measure)
    }

    private fun measure(workload: LongStrokeWorkload) {
        repeat(ENUMERATION_WARMUPS) { enumerate(workload.gesture) }
        val enumerationResults = List(ENUMERATION_SAMPLES) { timed { enumerate(workload.gesture) } }

        repeat(PREPARATION_WARMUPS) { workload.gesture.prepareStroke() }
        val preparationResults = List(PREPARATION_SAMPLES) { timed { workload.gesture.prepareStroke() } }

        val expectedDigest = enumerate(workload.gesture)
        assertEquals(workload.expectedPositionCount, expectedDigest.count)
        enumerationResults.forEach { result -> assertEquals(expectedDigest, result.value) }
        preparationResults.forEach { result ->
            assertEquals(workload.expectedPositionCount, result.value.positionCount)
        }
        assertEquals(preparationResults.first().value, preparationResults.last().value)
        println(report(workload, "enumeration", enumerationResults.map(TimedValue<*>::elapsedNanos)))
        println(report(workload, "prepare_stroke", preparationResults.map(TimedValue<*>::elapsedNanos)))
    }

    private fun enumerate(gesture: ToolGesture): GestureDigest {
        var count = 0
        var hash = 1
        gesture.forEachPosition { position ->
            count += 1
            hash = hash * 31 + position.hashCode()
        }
        return GestureDigest(count, hash)
    }

    private fun workloads(): List<LongStrokeWorkload> =
        listOf(
            workload("continuous_pencil", continuousEndpoints(), StrokeEffect.Paint(red), canvas.pixelCount.toInt()),
            workload("continuous_eraser", continuousEndpoints(), StrokeEffect.Erase, canvas.pixelCount.toInt()),
            workload(
                "repeated_pencil",
                repeatedEndpoints(),
                StrokeEffect.Paint(red),
                PixelLimits.MAX_RAW_STROKE_POSITIONS,
            ),
            workload(
                "repeated_eraser",
                repeatedEndpoints(),
                StrokeEffect.Erase,
                PixelLimits.MAX_RAW_STROKE_POSITIONS,
            ),
        )

    private fun workload(
        name: String,
        endpoints: List<PixelPosition>,
        effect: StrokeEffect,
        expectedPositionCount: Int,
    ): LongStrokeWorkload {
        var gesture = ToolGesture.begin(canvas, endpoints.first(), effect)
        endpoints.drop(1).forEach { endpoint ->
            gesture =
                when (val result = gesture.extend(endpoint)) {
                    is ToolGestureExtensionResult.Extended -> {
                        result.gesture
                    }

                    ToolGestureExtensionResult.Duplicate -> {
                        error("Measurement endpoint was duplicated.")
                    }

                    is ToolGestureExtensionResult.AboveSupportedMaximum -> {
                        error("Measurement path exceeded the raw-stroke cap: ${result.attemptedCount}.")
                    }
                }
        }
        assertEquals(expectedPositionCount, gesture.positionCount)
        return LongStrokeWorkload(name, gesture, expectedPositionCount)
    }

    private fun continuousEndpoints(): List<PixelPosition> =
        buildList {
            add(position(0, 0))
            repeat(CANVAS_EDGE) { y ->
                val farX = if (y % 2 == 0) CANVAS_EDGE - 1 else 0
                if (last() != position(farX, y)) add(position(farX, y))
                if (y < CANVAS_EDGE - 1) add(position(farX, y + 1))
            }
        }

    private fun repeatedEndpoints(): List<PixelPosition> =
        buildList {
            add(position(0, 0))
            repeat(REPEATED_FULL_WIDTH_SEGMENTS) { index ->
                add(position(if (index % 2 == 0) CANVAS_EDGE - 1 else 0, 0))
            }
            add(position(REPEATED_REMAINDER, 0))
        }

    private fun report(
        workload: LongStrokeWorkload,
        operation: String,
        samples: List<Long>,
    ): String =
        "LONG_STROKE_MEASUREMENT," +
            "workload=${workload.name},effect=${workload.gesture.effect},positions=${workload.expectedPositionCount}," +
            "operation=$operation,samples=${samples.size},p50_nanos=${nearestRank(samples, 0.50)}," +
            "p95_nanos=${nearestRank(samples, 0.95)},max_nanos=${samples.max()}"

    private fun nearestRank(
        samples: List<Long>,
        percentile: Double,
    ): Long = samples.sorted()[kotlin.math.ceil(samples.size * percentile).toInt() - 1]

    private fun <T> timed(block: () -> T): TimedValue<T> {
        val startedAt = System.nanoTime()
        val value = block()
        val elapsed = System.nanoTime() - startedAt
        return TimedValue(elapsed, value)
    }

    private fun position(
        x: Int,
        y: Int,
    ): PixelPosition = PixelPosition.create(PixelX.create(x).requiredValue(), PixelY.create(y).requiredValue())

    private val canvas: CanvasSize =
        CanvasSize.create(
            CanvasWidth.create(CANVAS_EDGE).requiredValue(),
            CanvasHeight.create(CANVAS_EDGE).requiredValue(),
        )
    private val red: PixelColor =
        PixelColor.create(
            ColorChannel.create(255).requiredValue(),
            ColorChannel.create(0).requiredValue(),
            ColorChannel.create(0).requiredValue(),
            ColorChannel.create(255).requiredValue(),
        )

    private companion object {
        const val CANVAS_EDGE: Int = 256
        const val ENUMERATION_WARMUPS: Int = 5
        const val ENUMERATION_SAMPLES: Int = 20
        const val PREPARATION_WARMUPS: Int = 3
        const val PREPARATION_SAMPLES: Int = 10
        const val REPEATED_FULL_WIDTH_SEGMENTS: Int = 1_028
        const val REPEATED_REMAINDER: Int = 3
        const val OPT_IN_PROPERTY: String = "nene.longStrokeMeasurement"
    }
}

private data class LongStrokeWorkload(
    val name: String,
    val gesture: ToolGesture,
    val expectedPositionCount: Int,
)

private data class GestureDigest(
    val count: Int,
    val hash: Int,
)

private data class TimedValue<T>(
    val elapsedNanos: Long,
    val value: T,
)

private fun <T> DomainValueResult<T>.requiredValue(): T =
    when (this) {
        is DomainValueResult.Created -> value
        is DomainValueResult.Rejected -> error("Long-stroke fixture rejected: $rejection")
    }
