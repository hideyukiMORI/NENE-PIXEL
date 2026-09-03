package io.github.hideyukimori.nenepixel.measurement

import android.app.ActivityManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.hideyukimori.nenepixel.core.application.document.command.ApplyStrokeCommand
import io.github.hideyukimori.nenepixel.core.application.document.command.CommandGateway
import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResult
import io.github.hideyukimori.nenepixel.core.application.document.command.RedoCommand
import io.github.hideyukimori.nenepixel.core.application.document.command.RejectionReason
import io.github.hideyukimori.nenepixel.core.application.document.command.UndoCommand
import io.github.hideyukimori.nenepixel.core.application.document.history.HistoryAvailability
import io.github.hideyukimori.nenepixel.core.domain.color.ColorChannel
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentId
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.document.Revision
import io.github.hideyukimori.nenepixel.core.domain.drawing.Stroke
import io.github.hideyukimori.nenepixel.core.domain.drawing.StrokeEffect
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasHeight
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasWidth
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelX
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelY
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelLimits
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.max

@RunWith(AndroidJUnit4::class)
internal class P2ProductionHistoryRetentionMeasurementTest {
    @Test
    fun measureProductionHistoryAtAcceptedRetentionCaps() {
        val environment = P2AndroidMeasurementEnvironment.fromRunnerArguments()
        val runIndex = requiredRunIndex()
        assertPhysicalEnvironment(environment)
        val values = ProductionHistoryValues()
        val gateway = CommandGateway.create(values.initialDocument())
        val baseline = PostGcMemorySnapshot.captureBaseline(gateway)

        values.populateAtBothCaps(gateway)
        values.assertExactRoundTrip(gateway)
        val retained = PostGcMemorySnapshot.captureRetainedMemory(gateway)
        assertRetainedMemoryPolicy(environment, baseline, retained)

        values.exerciseUndoRedoCycles(gateway)
        val afterCycles = PostGcMemorySnapshot.captureRetainedMemory(gateway)
        assertPostGcChurnPolicy(retained, afterCycles)
        println(report(runIndex, environment, baseline, retained, afterCycles))
    }

    private fun requiredRunIndex(): Int {
        val raw = InstrumentationRegistry.getArguments().getString(RUN_INDEX_ARGUMENT)
        return requireNotNull(raw?.toIntOrNull()?.takeIf { value -> value > 0 }) {
            "Runner argument '$RUN_INDEX_ARGUMENT' must be a positive integer."
        }
    }

    private fun assertPhysicalEnvironment(environment: P2AndroidMeasurementEnvironment) {
        val display = P2AndroidPhysicalCheckpointCapture.defaultDisplay(environment.targetContext)
        P2AndroidPhysicalCheckpointCapture
            .capture(environment.targetContext, display, "history_retention", sampleIndex = 0)
            .assertInitialValidity()
    }

    private fun assertRetainedMemoryPolicy(
        environment: P2AndroidMeasurementEnvironment,
        baseline: PostGcMemorySnapshot,
        retained: PostGcMemorySnapshot,
    ) {
        val runtimeMaximum = Runtime.getRuntime().maxMemory()
        assertTrue(retained.javaHeapUsedBytes <= runtimeMaximum * STEADY_HEAP_PERCENT / PERCENT_DENOMINATOR)
        val activityManager = environment.targetContext.getSystemService(ActivityManager::class.java)
        val memoryClassBytes = activityManager.memoryClass.toLong() * BYTES_PER_MEBIBYTE
        val pssLimitKilobytes =
            baseline.totalPssKilobytes +
                (memoryClassBytes * INDIVIDUAL_PSS_PERCENT / PERCENT_DENOMINATOR / BYTES_PER_KIBIBYTE).toInt()
        assertTrue(retained.totalPssKilobytes <= pssLimitKilobytes)
    }

    private fun assertPostGcChurnPolicy(
        retained: PostGcMemorySnapshot,
        afterCycles: PostGcMemorySnapshot,
    ) {
        val churn = (afterCycles.javaHeapUsedBytes - retained.javaHeapUsedBytes).coerceAtLeast(0L)
        val maximum = max(BYTES_PER_MEBIBYTE, Runtime.getRuntime().maxMemory() / PERCENT_DENOMINATOR)
        assertTrue(churn <= maximum)
    }

    private fun report(
        runIndex: Int,
        environment: P2AndroidMeasurementEnvironment,
        baseline: PostGcMemorySnapshot,
        retained: PostGcMemorySnapshot,
        afterCycles: PostGcMemorySnapshot,
    ): String =
        listOf(
            "P2_PRODUCTION_HISTORY_RETENTION",
            "run=$runIndex",
            "profile=${environment.profileId}",
            "entries=${PixelLimits.MAX_HISTORY_ENTRIES}",
            "changes=${PixelLimits.MAX_RETAINED_CHANGES}",
            "baseline_java_bytes=${baseline.javaHeapUsedBytes}",
            "retained_java_bytes=${retained.javaHeapUsedBytes}",
            "retained_java_delta_bytes=${retained.javaHeapUsedBytes - baseline.javaHeapUsedBytes}",
            "after_cycles_java_bytes=${afterCycles.javaHeapUsedBytes}",
            "baseline_pss_kib=${baseline.totalPssKilobytes}",
            "retained_pss_kib=${retained.totalPssKilobytes}",
            "retained_pss_delta_kib=${retained.totalPssKilobytes - baseline.totalPssKilobytes}",
        ).joinToString(separator = " ")

    private companion object {
        const val RUN_INDEX_ARGUMENT: String = "nene.p2.historyRunIndex"
        const val STEADY_HEAP_PERCENT: Long = 50L
        const val INDIVIDUAL_PSS_PERCENT: Long = 60L
        const val PERCENT_DENOMINATOR: Long = 100L
        const val BYTES_PER_KIBIBYTE: Long = 1_024L
        const val BYTES_PER_MEBIBYTE: Long = 1_048_576L
    }
}

private class ProductionHistoryValues {
    private val canvas: CanvasSize =
        CanvasSize.create(
            CanvasWidth.create(PixelLimits.MAX_CANVAS_AXIS).requiredValue(),
            CanvasHeight.create(PixelLimits.MAX_CANVAS_AXIS).requiredValue(),
        )
    private val documentId: DocumentId = DocumentId.create("4".repeat(DOCUMENT_ID_LENGTH)).requiredValue()
    private val red: PixelColor = color(CHANNEL_MAX, CHANNEL_MIN, CHANNEL_MIN)
    private val green: PixelColor = color(CHANNEL_MIN, CHANNEL_MAX, CHANNEL_MIN)

    fun initialDocument(): DocumentState =
        DocumentState.create(
            documentId,
            PixelSnapshot.createFilled(canvas, Revision.initial(), PixelColor.blank),
        )

    fun populateAtBothCaps(gateway: CommandGateway) {
        val path =
            List(CHANGES_PER_ENTRY) { index ->
                position(index % canvas.width.value, index / canvas.width.value)
            }
        repeat(PixelLimits.MAX_HISTORY_ENTRIES) { index ->
            val current = gateway.runtimeState.documentState
            val effect = StrokeEffect.Paint(if (index % 2 == 0) red else green)
            val stroke = Stroke.create(canvas, path, effect).requiredValue()
            assertApplied(
                gateway.execute(ApplyStrokeCommand.create(current.id, current.revision, stroke)),
            )
        }
        assertEquals(PixelLimits.MAX_HISTORY_ENTRIES.toLong(), gateway.runtimeState.documentState.revision.value)
        assertEquals(HistoryAvailability.UndoAvailable, gateway.runtimeState.historyAvailability)
        assertFinalPixels(gateway.runtimeState.documentState.snapshot)
    }

    fun assertExactRoundTrip(gateway: CommandGateway) {
        repeat(PixelLimits.MAX_HISTORY_ENTRIES) {
            executeUndo(gateway)
        }
        assertEquals(Revision.initial(), gateway.runtimeState.documentState.revision)
        assertEquals(HistoryAvailability.RedoAvailable, gateway.runtimeState.historyAvailability)
        assertTrue(
            gateway.runtimeState.documentState.snapshot
                .copyPackedRgba8888()
                .all { packed -> packed == PixelColor.blank.toPackedRgba8888() },
        )
        assertEquals(
            RejectionReason.NoUndoAvailable,
            assertRejected(
                gateway.execute(
                    UndoCommand.create(documentId, gateway.runtimeState.documentState.revision),
                ),
            ),
        )
        repeat(PixelLimits.MAX_HISTORY_ENTRIES) {
            executeRedo(gateway)
        }
        assertFinalPixels(gateway.runtimeState.documentState.snapshot)
    }

    fun exerciseUndoRedoCycles(gateway: CommandGateway) {
        repeat(UNDO_REDO_CYCLES) {
            executeUndo(gateway)
            executeRedo(gateway)
        }
        assertEquals(PixelLimits.MAX_HISTORY_ENTRIES.toLong(), gateway.runtimeState.documentState.revision.value)
        assertEquals(HistoryAvailability.UndoAvailable, gateway.runtimeState.historyAvailability)
    }

    private fun executeUndo(gateway: CommandGateway) {
        val current = gateway.runtimeState.documentState
        assertApplied(gateway.execute(UndoCommand.create(current.id, current.revision)))
    }

    private fun executeRedo(gateway: CommandGateway) {
        val current = gateway.runtimeState.documentState
        assertApplied(gateway.execute(RedoCommand.create(current.id, current.revision)))
    }

    private fun assertFinalPixels(snapshot: PixelSnapshot) {
        val expectedChanged = green.toPackedRgba8888()
        val expectedBlank = PixelColor.blank.toPackedRgba8888()
        snapshot.copyPackedRgba8888().forEachIndexed { index, packed ->
            assertEquals(if (index < CHANGES_PER_ENTRY) expectedChanged else expectedBlank, packed)
        }
    }

    private fun position(
        x: Int,
        y: Int,
    ): PixelPosition =
        PixelPosition.create(
            PixelX.create(x).requiredValue(),
            PixelY.create(y).requiredValue(),
        )

    private fun color(
        red: Int,
        green: Int,
        blue: Int,
    ): PixelColor =
        PixelColor.create(
            ColorChannel.create(red).requiredValue(),
            ColorChannel.create(green).requiredValue(),
            ColorChannel.create(blue).requiredValue(),
            ColorChannel.create(CHANNEL_MAX).requiredValue(),
        )

    private fun assertApplied(result: CommandResult) {
        assertTrue("Expected applied command but was $result", result is CommandResult.Applied)
    }

    private fun assertRejected(result: CommandResult): RejectionReason {
        assertTrue("Expected rejected command but was $result", result is CommandResult.Rejected)
        return (result as CommandResult.Rejected).reason
    }

    private companion object {
        const val DOCUMENT_ID_LENGTH: Int = 32
        const val CHANNEL_MIN: Int = 0
        const val CHANNEL_MAX: Int = 255
        const val CHANGES_PER_ENTRY: Int = PixelLimits.MAX_RETAINED_CHANGES / PixelLimits.MAX_HISTORY_ENTRIES
        const val UNDO_REDO_CYCLES: Int = 10
    }
}

private fun <T> DomainValueResult<T>.requiredValue(): T =
    when (this) {
        is DomainValueResult.Created -> value
        is DomainValueResult.Rejected -> error("Invalid production-history measurement fixture: $rejection")
    }
