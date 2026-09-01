package io.github.hideyukimori.nenepixel.presentation.compose.editor

import com.sun.management.ThreadMXBean
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportGesture
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportState
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportSurface
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportSurfacePoint
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportTransform
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportValueResult
import io.github.hideyukimori.nenepixel.presentation.compose.EditorFixture
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues.canvas
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues.fixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.ceil

internal class P2ViewportInteractionMeasurementTest {
    private val allocationCounter: ThreadAllocationCounter = ThreadAllocationCounter.create()

    @Test
    fun `measure canonical viewport controller transform`() {
        writeReport(measureInteraction())
    }

    private fun measureInteraction(): InteractionMetric {
        var deterministicViewport: ViewportState? = null
        repeat(WARMUP_ITERATIONS) {
            val operation = prepareInteraction()
            val result = operation.execute()
            operation.verify(result)
            deterministicViewport = deterministicViewport.assertDeterministic(result.renderState.viewport)
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
            deterministicViewport = deterministicViewport.assertDeterministic(result.renderState.viewport)
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
        val fixture = fixture(canvas)
        val surface = ViewportSurface.create(SURFACE_EDGE, SURFACE_EDGE, PIXELS_PER_DP).requiredValue()
        val gesture = zoomAndPanGesture()
        val transform =
            ViewportTransform
                .create(canvas, surface, fixture.initialWorkspace.viewport)
                .requiredValue()
        val expectedViewport = transform.apply(gesture).requiredValue()
        val initialDocument = fixture.gateway.runtimeState.documentState
        fixture.controller.viewportStarted(surface)
        return InteractionOperation(
            execute = { fixture.controller.viewportTransformed(surface, gesture) },
            verify = { result -> verifyInteraction(result, fixture, expectedViewport, initialDocument) },
        )
    }

    private fun verifyInteraction(
        result: PointerInputAcknowledgement,
        fixture: EditorFixture,
        expectedViewport: ViewportState,
        initialDocument: io.github.hideyukimori.nenepixel.core.domain.document.DocumentState,
    ) {
        val accepted = assertInstanceOf(PointerInputAcknowledgement.Accepted::class.java, result)
        assertEquals(expectedViewport, accepted.renderState.viewport)
        assertEquals(expectedViewport, fixture.controller.workspaceState.viewport)
        assertEquals(initialDocument, fixture.gateway.runtimeState.documentState)
        assertNull(accepted.renderState.preview)
        assertFalse(accepted.renderState.canUndo)
        assertFalse(accepted.renderState.canRedo)
    }

    private fun zoomAndPanGesture(): ViewportGesture =
        ViewportGesture.create(
            previousFirst = surfacePoint(400.0, 400.0),
            previousSecond = surfacePoint(1200.0, 1200.0),
            currentFirst = surfacePoint(360.0, 440.0),
            currentSecond = surfacePoint(1320.0, 1400.0),
        )

    private fun surfacePoint(
        xPixels: Double,
        yPixels: Double,
    ): ViewportSurfacePoint = ViewportSurfacePoint.create(xPixels, yPixels).requiredValue()

    private fun writeReport(metric: InteractionMetric) {
        val outputDirectory = System.getProperty(OUTPUT_DIRECTORY_PROPERTY)?.let(Path::of) ?: return
        val output = outputDirectory.resolve("viewport-interaction-measurement.csv")
        Files.createDirectories(output.parent)
        val rows =
            listOf(
                csvRow(*REPORT_COLUMNS.toTypedArray()),
                metadataRow("schema", "nene-pixel-p2-viewport-measurement-v1"),
                metadataRow("profile", HOST_PROFILE),
                metadataRow("os", systemDescription()),
                metadataRow("jvm", jvmDescription()),
                metadataRow("build_variant", "presentation-debug-host-unit-test-worker"),
                metadataRow("warmup_iterations", WARMUP_ITERATIONS.toString()),
                metadataRow("sample_count", SAMPLE_COUNT.toString()),
                metadataRow("canvas_size", "${CANVAS_EDGE}x$CANVAS_EDGE"),
                metadataRow("surface_pixels", "${SURFACE_EDGE}x$SURFACE_EDGE"),
                metadataRow(
                    "allocation_boundary",
                    "current HotSpot test thread allocated bytes; retained heap and Android PSS excluded",
                ),
                csvRow(
                    "metric",
                    "viewport_controller_transform",
                    "",
                    CANVAS_EDGE,
                    SURFACE_EDGE,
                    WARMUP_ITERATIONS,
                    SAMPLE_COUNT,
                    metric.latencyMedianNanos,
                    metric.latencyP95Nanos,
                    metric.allocatedMedianBytes,
                    metric.allocatedP95Bytes,
                    "Validated gesture through canonical transform workspace reducer and render projection",
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

    private fun ViewportState?.assertDeterministic(result: ViewportState): ViewportState {
        if (this != null) assertEquals(this, result)
        return result
    }

    private fun <T> ViewportValueResult<T>.requiredValue(): T =
        when (this) {
            is ViewportValueResult.Created -> value
            is ViewportValueResult.Rejected -> fail("Measurement viewport value was rejected: $rejection")
        }

    private fun LongArray.percentile(percentile: Double): Long {
        val sorted = sortedArray()
        val index = ceil(sorted.size * percentile).toInt().coerceIn(1, sorted.size) - 1
        return sorted[index]
    }

    private data class InteractionOperation(
        val execute: () -> PointerInputAcknowledgement,
        val verify: (PointerInputAcknowledgement) -> Unit,
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
                    "The named P2 host profile requires HotSpot thread-allocation measurement support."
                }
                if (!bean.isThreadAllocatedMemoryEnabled) bean.isThreadAllocatedMemoryEnabled = true
                return ThreadAllocationCounter(bean)
            }
        }
    }

    private companion object {
        const val OUTPUT_DIRECTORY_PROPERTY: String = "nene.p2.viewport.measurement.outputDirectory"
        const val HOST_PROFILE: String = "NENE-P2-WINDOWS-I9-10850K-JBR21"
        const val CANVAS_EDGE: Int = 16
        const val SURFACE_EDGE: Int = 1600
        const val PIXELS_PER_DP: Double = 2.0
        const val WARMUP_ITERATIONS: Int = 20
        const val SAMPLE_COUNT: Int = 50
        const val MEDIAN_PERCENTILE: Double = 0.50
        const val P95_PERCENTILE: Double = 0.95
        val REPORT_COLUMNS: List<String> =
            listOf(
                "record_type",
                "name",
                "value",
                "canvas_edge",
                "surface_edge_pixels",
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
