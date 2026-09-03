package io.github.hideyukimori.nenepixel.presentation.compose.editor

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
import java.nio.file.Files
import java.nio.file.Path

internal class P2ViewportInteractionMeasurementTest {
    private val measurementRunner: P2HostMeasurementRunner = P2HostMeasurementRunner.create()

    @Test
    fun `measure canonical viewport controller transform`() {
        writeReport(measureInteraction())
    }

    private fun measureInteraction(): P2HostMeasurement<ViewportState> =
        measurementRunner.measure(SAMPLING) { prepareInteraction() }

    private fun prepareInteraction(): P2HostMeasuredOperation<PointerInputAcknowledgement, ViewportState> {
        val canvas = canvas(CANVAS_EDGE, CANVAS_EDGE)
        val fixture = fixture(canvas)
        val surface = ViewportSurface.create(SURFACE_EDGE, SURFACE_EDGE, PIXELS_PER_DP).requiredValue()
        val gesture = zoomAndPanGesture()
        val transform =
            ViewportTransform
                .create(canvas, surface, fixture.initialWorkspace.viewport)
                .requiredValue()
        val expectedViewport = transform.apply(gesture).requiredValue()
        val initialDocument = fixture.runtime.state.documentState
        fixture.controller.viewportStarted(surface)
        return P2HostMeasuredOperation(
            execute = { fixture.controller.viewportTransformed(surface, gesture) },
            verify = { result -> verifyInteraction(result, fixture, expectedViewport, initialDocument) },
            deterministicKey = { result -> result.renderState.viewport },
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
        assertEquals(initialDocument, fixture.runtime.state.documentState)
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

    private fun writeReport(metric: P2HostMeasurement<ViewportState>) {
        val outputDirectory = System.getProperty(OUTPUT_DIRECTORY_PROPERTY)?.let(Path::of) ?: return
        val output = outputDirectory.resolve("viewport-interaction-measurement.csv")
        Files.createDirectories(output.parent)
        val rows =
            listOf(
                P2HostMeasurementReport.csvRow(*REPORT_COLUMNS.toTypedArray()),
                metadataRow("schema", "nene-pixel-p2-viewport-measurement-v1"),
                metadataRow("profile", HOST_PROFILE),
                metadataRow("os", P2HostMeasurementReport.systemDescription()),
                metadataRow("jvm", P2HostMeasurementReport.jvmDescription()),
                metadataRow("build_variant", "presentation-debug-host-unit-test-worker"),
                metadataRow("warmup_iterations", WARMUP_ITERATIONS.toString()),
                metadataRow("sample_count", SAMPLE_COUNT.toString()),
                metadataRow("canvas_size", "${CANVAS_EDGE}x$CANVAS_EDGE"),
                metadataRow("surface_pixels", "${SURFACE_EDGE}x$SURFACE_EDGE"),
                metadataRow(
                    "allocation_boundary",
                    "current HotSpot test thread allocated bytes; retained heap and Android PSS excluded",
                ),
                P2HostMeasurementReport.csvRow(
                    "metric",
                    "viewport_controller_transform",
                    "",
                    CANVAS_EDGE,
                    SURFACE_EDGE,
                    WARMUP_ITERATIONS,
                    SAMPLE_COUNT,
                    metric.latency.median,
                    metric.latency.p95,
                    metric.allocation.median,
                    metric.allocation.p95,
                    "Validated gesture through canonical transform workspace reducer and render projection",
                ),
            )
        Files.writeString(output, rows.joinToString(System.lineSeparator(), postfix = System.lineSeparator()))
    }

    private fun metadataRow(
        name: String,
        value: String,
    ): String = P2HostMeasurementReport.metadataRow(REPORT_COLUMNS.size, name, value)

    private fun <T> ViewportValueResult<T>.requiredValue(): T =
        when (this) {
            is ViewportValueResult.Created -> value
            is ViewportValueResult.Rejected -> fail("Measurement viewport value was rejected: $rejection")
        }

    private companion object {
        const val OUTPUT_DIRECTORY_PROPERTY: String = "nene.p2.viewport.measurement.outputDirectory"
        const val HOST_PROFILE: String = "NENE-P2-WINDOWS-I9-10850K-JBR21"
        const val CANVAS_EDGE: Int = 16
        const val SURFACE_EDGE: Int = 1600
        const val PIXELS_PER_DP: Double = 2.0
        const val WARMUP_ITERATIONS: Int = 20
        const val SAMPLE_COUNT: Int = 50
        val SAMPLING: P2HostSampling = P2HostSampling(WARMUP_ITERATIONS, SAMPLE_COUNT)
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
