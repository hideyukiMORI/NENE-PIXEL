package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.sun.management.ThreadMXBean
import io.github.hideyukimori.nenepixel.core.application.document.command.ApplyStrokeCommand
import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResult
import io.github.hideyukimori.nenepixel.core.domain.drawing.Stroke
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.presentation.compose.EditorFixture
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues.canvas
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues.fixture
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues.position
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues.red
import io.github.hideyukimori.nenepixel.presentation.compose.input.FixedCanvasTouchTranslator
import io.github.hideyukimori.nenepixel.presentation.compose.input.TouchTranslation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.ceil

internal class M1InteractionMeasurementTest {
    private val allocationCounter: ThreadAllocationCounter = ThreadAllocationCounter.create()

    @Test
    fun `measure translated controller interaction against the direct command result`() {
        val metric = measureInteraction()

        writeReport(metric)
    }

    private fun measureInteraction(): InteractionMetric {
        var deterministicResult: EditorInteractionResult? = null
        repeat(WARMUP_ITERATIONS) {
            val operation = prepareInteraction()
            val result = operation.execute()
            operation.verify(result)
            deterministicResult = deterministicResult.assertDeterministic(result)
        }
        val latencies = LongArray(SAMPLE_COUNT)
        val allocations = LongArray(SAMPLE_COUNT)
        repeat(SAMPLE_COUNT) { index ->
            val operation = prepareInteraction()
            val allocationBefore = allocationCounter.currentThreadBytes()
            val timeBefore = System.nanoTime()
            val result = operation.execute()
            latencies[index] = System.nanoTime() - timeBefore
            allocations[index] = allocationCounter.currentThreadBytes() - allocationBefore
            operation.verify(result)
            deterministicResult = deterministicResult.assertDeterministic(result)
        }
        return InteractionMetric(
            latencyMedianNanos = latencies.percentile(MEDIAN_PERCENTILE),
            latencyP95Nanos = latencies.percentile(P95_PERCENTILE),
            allocatedMedianBytes = allocations.percentile(MEDIAN_PERCENTILE),
            allocatedP95Bytes = allocations.percentile(P95_PERCENTILE),
        )
    }

    private fun prepareInteraction(): InteractionOperation {
        val canvas = canvas(CANVAS_EDGE, CANVAS_EDGE)
        val path = List(CANVAS_EDGE) { coordinate -> position(coordinate, coordinate) }
        val touchFixture = fixture(canvas)
        val directFixture = fixture(canvas)
        val directStroke = Stroke.create(canvas, path, red).requiredValue()
        val target = directFixture.gateway.runtimeState.documentState
        val directResult =
            directFixture.gateway.execute(
                ApplyStrokeCommand.create(target.id, target.revision, directStroke),
            )
        val expectedState = directFixture.gateway.runtimeState.documentState
        val offsets = path.map { point -> point.centerOffset() }
        val moveOffsets = offsets.subList(1, offsets.lastIndex)
        val translator = FixedCanvasTouchTranslator.create()
        val surface = Size(SURFACE_EDGE, SURFACE_EDGE)
        return InteractionOperation(
            execute = {
                touchFixture.controller.pointerDown(translator.mapped(offsets.first(), surface, canvas))
                moveOffsets.forEach { offset ->
                    touchFixture.controller.pointerMove(translator.mapped(offset, surface, canvas))
                }
                touchFixture.controller.pointerEnd(translator.mapped(offsets.last(), surface, canvas))
            },
            verify = { result -> verifyInteraction(result, touchFixture, directResult, expectedState) },
        )
    }

    private fun verifyInteraction(
        result: EditorInteractionResult,
        fixture: EditorFixture,
        directResult: CommandResult,
        expectedState: io.github.hideyukimori.nenepixel.core.domain.document.DocumentState,
    ) {
        val executed = assertInstanceOf(EditorInteractionResult.CommandExecuted::class.java, result)
        assertEquals(directResult, executed.commandResult)
        assertEquals(expectedState, fixture.gateway.runtimeState.documentState)
        assertEquals(expectedState.snapshot, executed.renderState.snapshot)
        assertNull(executed.renderState.preview)
        assertTrue(executed.renderState.canUndo)
        assertFalse(executed.renderState.canRedo)
    }

    private fun writeReport(metric: InteractionMetric) {
        val outputDirectory = System.getProperty(OUTPUT_DIRECTORY_PROPERTY)?.let(Path::of) ?: return
        val output = outputDirectory.resolve("interaction-measurement.csv")
        Files.createDirectories(output.parent)
        val rows =
            listOf(
                csvRow(*REPORT_COLUMNS.toTypedArray()),
                metadataRow("schema", "nene-pixel-m1-measurement-v1"),
                metadataRow("profile", HOST_PROFILE),
                metadataRow("os", systemDescription()),
                metadataRow("jvm", jvmDescription()),
                metadataRow("build_variant", "presentation-debug-host-unit-test-worker"),
                metadataRow("warmup_iterations", WARMUP_ITERATIONS.toString()),
                metadataRow("sample_count", SAMPLE_COUNT.toString()),
                metadataRow("canvas_sizes", "${CANVAS_EDGE}x$CANVAS_EDGE"),
                metadataRow(
                    "allocation_boundary",
                    "current HotSpot test thread allocated bytes; retained heap and Android PSS excluded",
                ),
                csvRow(
                    "metric",
                    "translated_controller_commit",
                    "",
                    CANVAS_EDGE,
                    CANVAS_EDGE,
                    WARMUP_ITERATIONS,
                    SAMPLE_COUNT,
                    metric.latencyMedianNanos,
                    metric.latencyP95Nanos,
                    metric.allocatedMedianBytes,
                    metric.allocatedP95Bytes,
                    "Offset translation through preview reducer one gateway command and render projection; " +
                        "Compose frame excluded",
                ),
            )
        Files.writeString(output, rows.joinToString(System.lineSeparator(), postfix = System.lineSeparator()))
    }

    private fun metadataRow(
        name: String,
        value: String,
    ): String = csvRow("metadata", name, value, "", "", "", "", "", "", "", "", "")

    private fun systemDescription(): String =
        listOf("os.name", "os.version", "os.arch").joinToString(" ") { property ->
            requiredSystemProperty(property)
        }

    private fun jvmDescription(): String =
        listOf("java.vm.name", "java.runtime.version").joinToString(" ") { property ->
            requiredSystemProperty(property)
        }

    private fun requiredSystemProperty(name: String): String =
        requireNotNull(System.getProperty(name)) { "Required JVM system property '$name' is unavailable." }

    private fun csvRow(vararg values: Any): String =
        values.joinToString(",") { value -> "\"${value.toString().replace("\"", "\"\"")}\"" }

    private fun FixedCanvasTouchTranslator.mapped(
        offset: Offset,
        surface: Size,
        canvas: io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize,
    ): PixelPosition =
        when (val translation = translate(offset, surface, canvas)) {
            is TouchTranslation.Mapped -> translation.position
            TouchTranslation.Outside -> fail("Measurement touch unexpectedly mapped outside")
        }

    private fun PixelPosition.centerOffset(): Offset =
        Offset(
            x = (x.value + HALF_CELL) * CELL_EDGE,
            y = (y.value + HALF_CELL) * CELL_EDGE,
        )

    private fun EditorInteractionResult?.assertDeterministic(result: EditorInteractionResult): EditorInteractionResult {
        if (this != null) {
            assertEquals(this, result)
        }
        return result
    }

    private fun <T> DomainValueResult<T>.requiredValue(): T =
        when (this) {
            is DomainValueResult.Created -> value
            is DomainValueResult.Rejected -> fail("Measurement value was rejected: $rejection")
        }

    private fun LongArray.percentile(percentile: Double): Long {
        val sorted = sortedArray()
        val index = ceil(sorted.size * percentile).toInt().coerceIn(1, sorted.size) - 1
        return sorted[index]
    }

    private data class InteractionOperation(
        val execute: () -> EditorInteractionResult,
        val verify: (EditorInteractionResult) -> Unit,
    )

    private data class InteractionMetric(
        val latencyMedianNanos: Long,
        val latencyP95Nanos: Long,
        val allocatedMedianBytes: Long,
        val allocatedP95Bytes: Long,
    )

    private class ThreadAllocationCounter private constructor(
        private val bean: ThreadMXBean,
    ) {
        fun currentThreadBytes(): Long = bean.getThreadAllocatedBytes(Thread.currentThread().threadId())

        companion object {
            fun create(): ThreadAllocationCounter {
                val bean = ManagementFactory.getThreadMXBean()
                require(bean is ThreadMXBean && bean.isThreadAllocatedMemorySupported) {
                    "The named M1 host profile requires HotSpot thread-allocation measurement support."
                }
                if (!bean.isThreadAllocatedMemoryEnabled) {
                    bean.isThreadAllocatedMemoryEnabled = true
                }
                return ThreadAllocationCounter(bean)
            }
        }
    }

    private companion object {
        const val OUTPUT_DIRECTORY_PROPERTY: String = "nene.m1.measurement.outputDirectory"
        const val HOST_PROFILE: String = "NENE-M1-WINDOWS-I9-10850K-JBR21"
        const val CANVAS_EDGE: Int = 16
        const val WARMUP_ITERATIONS: Int = 20
        const val SAMPLE_COUNT: Int = 50
        const val CELL_EDGE: Float = 100.0f
        const val HALF_CELL: Float = 0.5f
        const val SURFACE_EDGE: Float = CANVAS_EDGE * CELL_EDGE
        const val MEDIAN_PERCENTILE: Double = 0.50
        const val P95_PERCENTILE: Double = 0.95
        val REPORT_COLUMNS: List<String> =
            listOf(
                "record_type",
                "name",
                "value",
                "canvas_edge",
                "stroke_positions",
                "warmup",
                "samples",
                "latency_median_ns",
                "latency_p95_ns",
                "allocated_median_bytes",
                "allocated_p95_bytes",
                "boundary",
            )
    }
}
