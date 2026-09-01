package io.github.hideyukimori.nenepixel.core.application.document.command

import com.sun.management.ThreadMXBean
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.black
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.canvas
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.defaultDocumentId
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.position
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.red
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.revision
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.snapshot
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.state
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.stroke
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.drawing.Stroke
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelPatchApplicationResult
import io.github.hideyukimori.nenepixel.core.pixelengine.StrokeRasterizationResult
import io.github.hideyukimori.nenepixel.core.pixelengine.rasterizeStroke
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.ceil

internal class M1CoreMeasurementTest {
    private val allocationCounter: ThreadAllocationCounter = ThreadAllocationCounter.create()

    @Test
    fun `measure canonical snapshot patch apply undo and redo operations`() {
        val metrics =
            CANVAS_EDGES.flatMap { edge ->
                listOf(
                    measureSnapshotCreation(edge),
                    measurePatchCreation(edge),
                    measureStrokeApplication(edge),
                    measureUndo(edge),
                    measureRedo(edge),
                )
            }

        writeReport(metrics)
    }

    private fun measureSnapshotCreation(edge: Int): MeasurementMetric {
        val size = canvas(edge, edge)
        val pixels = List(size.pixelCount.toInt()) { black }
        val expected = snapshot(size)
        return measure(
            name = "snapshot_create",
            edge = edge,
            strokePositions = 0,
            boundary = "PixelSnapshot.create defensive row-major ownership",
        ) {
            MeasuredOperation(
                execute = { PixelSnapshot.create(size, revision(0L), pixels) },
                verify = { result -> assertEquals(expected, result.requiredValue()) },
            )
        }
    }

    private fun measurePatchCreation(edge: Int): MeasurementMetric {
        val fixture = fixture(edge)
        return measure(
            name = "patch_create",
            edge = edge,
            strokePositions = fixture.stroke.positionCount,
            boundary = "canonical rasterizeStroke including PixelChange list and PixelPatch creation",
        ) {
            MeasuredOperation(
                execute = { rasterizeStroke(fixture.initial.snapshot, fixture.stroke) },
                verify = { result -> verifyRasterized(result, fixture) },
            )
        }
    }

    private fun measureStrokeApplication(edge: Int): MeasurementMetric =
        measure(
            name = "command_apply_stroke",
            edge = edge,
            strokePositions = edge,
            boundary = "CommandGateway.execute ApplyStrokeCommand including patch and next snapshot",
        ) {
            val fixture = fixture(edge)
            val gateway = CommandGateway.create(fixture.initial)
            MeasuredOperation(
                execute = { gateway.execute(fixture.applyCommand) },
                verify = { result ->
                    val applied = result.requiredApplied()
                    assertEquals(fixture.expectedApplied, gateway.runtimeState.documentState)
                    assertEquals(edge, applied.changeSet.patch.changeCount)
                },
            )
        }

    private fun measureUndo(edge: Int): MeasurementMetric =
        measure(
            name = "command_undo",
            edge = edge,
            strokePositions = edge,
            boundary = "CommandGateway.execute UndoCommand using recorded inverse patch",
        ) {
            val fixture = fixture(edge)
            val gateway = CommandGateway.create(fixture.initial)
            val original = gateway.execute(fixture.applyCommand).requiredApplied()
            val afterApply = gateway.runtimeState.documentState
            val command = UndoCommand.create(afterApply.id, afterApply.revision)
            MeasuredOperation(
                execute = { gateway.execute(command) },
                verify = { result ->
                    val undo = result.requiredApplied()
                    assertEquals(fixture.initial, gateway.runtimeState.documentState)
                    assertEquals(original.changeSet.inversePatch, undo.changeSet.patch)
                },
            )
        }

    private fun measureRedo(edge: Int): MeasurementMetric =
        measure(
            name = "command_redo",
            edge = edge,
            strokePositions = edge,
            boundary = "CommandGateway.execute RedoCommand using recorded forward patch",
        ) {
            val fixture = fixture(edge)
            val gateway = CommandGateway.create(fixture.initial)
            val original = gateway.execute(fixture.applyCommand).requiredApplied()
            val afterApply = gateway.runtimeState.documentState
            gateway.execute(UndoCommand.create(afterApply.id, afterApply.revision)).requiredApplied()
            val afterUndo = gateway.runtimeState.documentState
            val command = RedoCommand.create(afterUndo.id, afterUndo.revision)
            MeasuredOperation(
                execute = { gateway.execute(command) },
                verify = { result ->
                    val redo = result.requiredApplied()
                    assertEquals(fixture.expectedApplied, gateway.runtimeState.documentState)
                    assertEquals(original.changeSet.patch, redo.changeSet.patch)
                },
            )
        }

    private fun verifyRasterized(
        result: StrokeRasterizationResult,
        fixture: CoreMeasurementFixture,
    ) {
        val patch =
            when (result) {
                is StrokeRasterizationResult.Rasterized -> result.patch
                StrokeRasterizationResult.NoChanges -> fail("Measurement stroke unexpectedly made no change")
                is StrokeRasterizationResult.Rejected -> fail("Measurement stroke was rejected: ${result.rejection}")
            }
        assertEquals(fixture.stroke.positionCount, patch.changeCount)
        val applied = patch.applyTo(fixture.initial.snapshot)
        val snapshot =
            when (applied) {
                is PixelPatchApplicationResult.Applied -> applied.snapshot
                is PixelPatchApplicationResult.Rejected -> fail("Measurement patch was rejected: ${applied.rejection}")
            }
        assertEquals(fixture.expectedApplied.snapshot, snapshot)
    }

    private fun <T : Any> measure(
        name: String,
        edge: Int,
        strokePositions: Int,
        boundary: String,
        prepare: () -> MeasuredOperation<T>,
    ): MeasurementMetric {
        var deterministicResult: T? = null
        repeat(WARMUP_ITERATIONS) {
            val operation = prepare()
            val result = operation.execute()
            operation.verify(result)
            deterministicResult = deterministicResult.assertDeterministic(result)
        }
        val latencies = LongArray(SAMPLE_COUNT)
        val allocations = LongArray(SAMPLE_COUNT)
        repeat(SAMPLE_COUNT) { index ->
            val operation = prepare()
            val allocationBefore = allocationCounter.currentThreadBytes()
            val timeBefore = System.nanoTime()
            val result = operation.execute()
            latencies[index] = System.nanoTime() - timeBefore
            allocations[index] = allocationCounter.currentThreadBytes() - allocationBefore
            operation.verify(result)
            deterministicResult = deterministicResult.assertDeterministic(result)
        }
        return MeasurementMetric(
            name = name,
            edge = edge,
            strokePositions = strokePositions,
            latencyMedianNanos = latencies.percentile(MEDIAN_PERCENTILE),
            latencyP95Nanos = latencies.percentile(P95_PERCENTILE),
            allocatedMedianBytes = allocations.percentile(MEDIAN_PERCENTILE),
            allocatedP95Bytes = allocations.percentile(P95_PERCENTILE),
            boundary = boundary,
        )
    }

    private fun fixture(edge: Int): CoreMeasurementFixture {
        val size = canvas(edge, edge)
        val path = List(edge) { coordinate -> position(coordinate, coordinate) }
        val measuredStroke = stroke(size, path, red)
        val initial = state(size)
        val expectedPixels =
            List(size.pixelCount.toInt()) { index ->
                val x = index % edge
                val y = index / edge
                if (x == y) red else black
            }
        val expectedApplied = state(size, revision(1L), expectedPixels, defaultDocumentId)
        return CoreMeasurementFixture(
            initial = initial,
            expectedApplied = expectedApplied,
            stroke = measuredStroke,
            applyCommand = ApplyStrokeCommand.create(initial.id, initial.revision, measuredStroke),
        )
    }

    private fun writeReport(metrics: List<MeasurementMetric>) {
        val outputDirectory = System.getProperty(OUTPUT_DIRECTORY_PROPERTY)?.let(Path::of) ?: return
        val output = outputDirectory.resolve("core-measurement.csv")
        Files.createDirectories(output.parent)
        val rows =
            buildList {
                add(csvRow(*REPORT_COLUMNS.toTypedArray()))
                add(metadataRow("schema", "nene-pixel-m1-measurement-v1"))
                add(metadataRow("profile", HOST_PROFILE))
                add(metadataRow("os", systemDescription()))
                add(metadataRow("jvm", jvmDescription()))
                add(
                    metadataRow(
                        "build_variant",
                        "core-jvm-test-output-in-presentation-debug-host-worker",
                    ),
                )
                add(metadataRow("warmup_iterations", WARMUP_ITERATIONS.toString()))
                add(metadataRow("sample_count", SAMPLE_COUNT.toString()))
                add(metadataRow("canvas_sizes", CANVAS_EDGES.joinToString("|") { edge -> "${edge}x$edge" }))
                add(
                    metadataRow(
                        "allocation_boundary",
                        "current HotSpot test thread allocated bytes; " +
                            "retained heap and Android PSS excluded",
                    ),
                )
                metrics.forEach { metric -> add(metric.toCsvRow()) }
            }
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

    private fun MeasurementMetric.toCsvRow(): String =
        csvRow(
            "metric",
            name,
            "",
            edge,
            strokePositions,
            WARMUP_ITERATIONS,
            SAMPLE_COUNT,
            latencyMedianNanos,
            latencyP95Nanos,
            allocatedMedianBytes,
            allocatedP95Bytes,
            boundary,
        )

    private fun csvRow(vararg values: Any): String =
        values.joinToString(",") { value -> "\"${value.toString().replace("\"", "\"\"")}\"" }

    private fun <T : Any> T?.assertDeterministic(result: T): T {
        if (this != null) {
            assertEquals(this, result)
        }
        return result
    }

    private fun CommandResult.requiredApplied(): CommandResult.Applied =
        assertInstanceOf(CommandResult.Applied::class.java, this)

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

    private data class MeasuredOperation<T : Any>(
        val execute: () -> T,
        val verify: (T) -> Unit,
    )

    private data class MeasurementMetric(
        val name: String,
        val edge: Int,
        val strokePositions: Int,
        val latencyMedianNanos: Long,
        val latencyP95Nanos: Long,
        val allocatedMedianBytes: Long,
        val allocatedP95Bytes: Long,
        val boundary: String,
    )

    private data class CoreMeasurementFixture(
        val initial: DocumentState,
        val expectedApplied: DocumentState,
        val stroke: Stroke,
        val applyCommand: ApplyStrokeCommand,
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
        const val WARMUP_ITERATIONS: Int = 20
        const val SAMPLE_COUNT: Int = 50
        const val MEDIAN_PERCENTILE: Double = 0.50
        const val P95_PERCENTILE: Double = 0.95
        val CANVAS_EDGES: List<Int> = listOf(16, 64, 256)
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
