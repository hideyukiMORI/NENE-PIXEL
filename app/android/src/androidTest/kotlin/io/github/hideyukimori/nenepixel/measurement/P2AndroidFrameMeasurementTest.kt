package io.github.hideyukimori.nenepixel.measurement

import android.os.Build
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import io.github.hideyukimori.nenepixel.MainActivity
import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResult
import io.github.hideyukimori.nenepixel.presentation.compose.editor.EditorRenderState
import io.github.hideyukimori.nenepixel.presentation.compose.editor.P2FrameMeasurementEditor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

internal class P2AndroidFrameMeasurementTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun measureP2AffectedFramesOnPhysicalProfile() {
        val environment = P2AndroidMeasurementEnvironment.fromRunnerArguments()
        val identity = P2AndroidRunIdentity.fromRunnerArguments()
        check(!environment.emulatorDetection.isEmulator) {
            "Frame evidence requires the physical profile; emulator auxiliary mode is not accepted."
        }
        check(environment.frameSampleCount >= MINIMUM_FRAME_SAMPLE_COUNT) {
            "Frame evidence requires at least $MINIMUM_FRAME_SAMPLE_COUNT measured samples."
        }
        check(Build.VERSION.SDK_INT >= FRAME_TIMELINE_API) {
            "Physical frame evidence requires API $FRAME_TIMELINE_API for FRAME_TIMELINE_VSYNC_ID."
        }
        measureApi36(environment, identity)
    }

    @RequiresApi(FRAME_TIMELINE_API)
    private fun measureApi36(
        environment: P2AndroidMeasurementEnvironment,
        identity: P2AndroidRunIdentity,
    ) {
        val workload = P2AndroidFrameWorkload.create()
        val displayed = mutableStateOf(P2DisplayedFrame(INITIAL_GENERATION, workload.controller.renderState))
        val collector = P2AndroidFrameMetricsCollector(composeRule.activity.window)
        composeRule.runOnUiThread {
            collector.attach()
            composeRule.activity.setContent {
                val current = displayed.value
                P2FrameMeasurementEditor(
                    renderState = current.renderState,
                    callbacks = workload.controller.callbacks,
                    generation = current.generation,
                    onRenderStateChanged = { next ->
                        displayed.value = P2DisplayedFrame(current.generation, next)
                    },
                    onCanonicalContentDrawn = collector::recordCanonicalContentDrawn,
                    modifier = Modifier,
                )
            }
        }
        composeRule.waitForIdle()

        val samples = mutableListOf<P2AndroidFrameMeasurementSample>()
        val checkpoints = mutableListOf<P2AndroidPhysicalCheckpoint>()
        var generation = INITIAL_GENERATION
        try {
            repeat(environment.frameWarmupIterations) {
                generation = executeAndReset(workload, displayed, collector, generation, sampleIndex = null).generation
            }

            val baselineCheckpoint = captureCheckpoint("before_samples", 0)
            baselineCheckpoint.assertInitialValidity()
            checkpoints += baselineCheckpoint

            repeat(environment.frameSampleCount) { zeroBasedIndex ->
                val sampleIndex = zeroBasedIndex + 1
                val execution = executeAndReset(workload, displayed, collector, generation, sampleIndex)
                generation = execution.generation
                samples += requireNotNull(execution.sample)
                if (sampleIndex % P2AndroidPhysicalCheckpointPolicy.CHECKPOINT_INTERVAL == 0) {
                    captureCheckpoint("sample_$sampleIndex", sampleIndex)
                        .also { checkpoint -> checkpoint.assertCompatibleWith(baselineCheckpoint) }
                        .also(checkpoints::add)
                }
            }

            captureCheckpoint("after_samples", environment.frameSampleCount)
                .also { checkpoint -> checkpoint.assertCompatibleWith(baselineCheckpoint) }
                .also(checkpoints::add)
            collector.assertNoDroppedReports()
            val output = P2AndroidFrameMeasurementReport.write(environment, identity, checkpoints, samples)
            assertTrue(output.isFile)
            assertTrue(output.length() > 0L)
        } finally {
            composeRule.runOnUiThread { collector.close() }
        }
    }

    @RequiresApi(FRAME_TIMELINE_API)
    private fun executeAndReset(
        workload: P2AndroidFrameWorkload,
        displayed: MutableState<P2DisplayedFrame>,
        collector: P2AndroidFrameMetricsCollector,
        previousGeneration: Long,
        sampleIndex: Int?,
    ): P2FrameExecution {
        val measuredGeneration = previousGeneration + 1L
        lateinit var result: CommandResult
        lateinit var expectedRenderState: EditorRenderState
        var commandStartNanos = 0L
        var commandFinishNanos = 0L
        var renderStatePublishedNanos = 0L
        composeRule.runOnUiThread {
            val command = workload.createCommand()
            commandStartNanos = System.nanoTime()
            result = workload.execute(command)
            commandFinishNanos = System.nanoTime()
            expectedRenderState = workload.controller.renderState
            collector.arm(measuredGeneration, expectedRenderState)
            renderStatePublishedNanos = System.nanoTime()
            displayed.value = P2DisplayedFrame(measuredGeneration, expectedRenderState)
        }
        composeRule.waitForIdle()

        assertEquals(expectedRenderState, workload.verifyApplied(result))
        val correlated = collector.awaitFirstCorrectFrame(measuredGeneration, FRAME_TIMEOUT_MILLIS)
        correlated.assertExact(
            expectedState = expectedRenderState,
            expectedGeneration = measuredGeneration,
            commandStartNanos = commandStartNanos,
            commandFinishNanos = commandFinishNanos,
            renderStatePublishedNanos = renderStatePublishedNanos,
        )

        val correctness =
            sampleIndex?.let {
                captureCorrectness(workload.canvasEdge, workload.expectedRenderedArgb)
            }
        val frameCompletionNanos =
            Math.addExact(correlated.metrics.intendedVsyncNanos, correlated.metrics.totalDurationNanos)
        assertTrue(frameCompletionNanos >= correlated.marker.callbackNanos)
        val sample =
            sampleIndex?.let { index ->
                P2AndroidFrameMeasurementSample(
                    sampleIndex = index,
                    generation = measuredGeneration,
                    commandStartNanos = commandStartNanos,
                    commandFinishNanos = commandFinishNanos,
                    renderStatePublishedNanos = renderStatePublishedNanos,
                    frameCompletionNanos = frameCompletionNanos,
                    commandToFirstCorrectFrameNanos = frameCompletionNanos - commandStartNanos,
                    expectedRevision = expectedRenderState.snapshot.revision.value,
                    expectedSnapshotHash = expectedRenderState.snapshot.hashCode(),
                    correlatedFrame = correlated,
                    correctness = requireNotNull(correctness),
                )
            }

        val resetGeneration = measuredGeneration + 1L
        composeRule.runOnUiThread {
            val resetState = workload.resetAndVerify()
            collector.arm(resetGeneration, resetState)
            displayed.value = P2DisplayedFrame(resetGeneration, resetState)
        }
        composeRule.waitForIdle()
        return P2FrameExecution(resetGeneration, sample)
    }

    @RequiresApi(FRAME_TIMELINE_API)
    private fun P2CorrelatedFrame.assertExact(
        expectedState: EditorRenderState,
        expectedGeneration: Long,
        commandStartNanos: Long,
        commandFinishNanos: Long,
        renderStatePublishedNanos: Long,
    ) {
        assertTrue(commandFinishNanos >= commandStartNanos)
        assertTrue(renderStatePublishedNanos >= commandFinishNanos)
        assertEquals(expectedGeneration, marker.generation)
        assertTrue(marker.exactState)
        assertEquals(expectedState.snapshot.revision.value, marker.revision)
        assertEquals(expectedState.snapshot.hashCode(), marker.snapshotHash)
        assertTrue(marker.callbackNanos >= renderStatePublishedNanos)
        assertEquals(0, metrics.droppedReports)
        assertEquals(0L, metrics.firstDrawFrame)
        assertTrue(metrics.intendedVsyncNanos > 0L)
        assertTrue(metrics.vsyncNanos > 0L)
        assertTrue(metrics.frameTimelineVsyncId > 0L)
        assertTrue(metrics.totalDurationNanos >= 0L)
        assertTrue(metrics.deadlineNanos > 0L)
        assertTrue(metrics.gpuDurationNanos >= 0L)
        assertTrue(metrics.drawDurationNanos >= 0L)
        assertTrue(metrics.layoutMeasureDurationNanos >= 0L)
        assertTrue(metrics.syncDurationNanos >= 0L)
        assertTrue(metrics.commandIssueDurationNanos >= 0L)
        assertTrue(metrics.swapBuffersDurationNanos >= 0L)
        assertTrue(metrics.inputHandlingDurationNanos >= 0L)
        assertTrue(metrics.unknownDelayDurationNanos >= 0L)
    }

    private fun captureCorrectness(
        canvasEdge: Int,
        expectedArgb: Int,
    ): P2FrameImageCorrectness {
        val image =
            composeRule
                .onNodeWithContentDescription("$canvasEdge by $canvasEdge pixel canvas")
                .captureToImage()
        val pixels = image.toPixelMap()
        check(image.width > 0 && image.height > 0) { "The canonical PixelCanvas capture is empty." }
        var mismatchCount = 0
        var sampledArgbHash = 1
        repeat(canvasEdge) { logicalY ->
            val imageY = logicalPixelCenter(logicalY, canvasEdge, image.height)
            repeat(canvasEdge) { logicalX ->
                val imageX = logicalPixelCenter(logicalX, canvasEdge, image.width)
                val actualArgb = pixels[imageX, imageY].toArgb()
                if (actualArgb != expectedArgb) mismatchCount += 1
                sampledArgbHash = HASH_MULTIPLIER * sampledArgbHash + actualArgb
            }
        }
        assertEquals("Every logical pixel center must be the expected rendered color.", 0, mismatchCount)
        return P2FrameImageCorrectness(
            imageWidth = image.width,
            imageHeight = image.height,
            checkedLogicalPixels = canvasEdge * canvasEdge,
            mismatchCount = mismatchCount,
            sampledArgbHash = sampledArgbHash,
        )
    }

    private fun logicalPixelCenter(
        logicalCoordinate: Int,
        canvasEdge: Int,
        imageEdge: Int,
    ): Int =
        (((logicalCoordinate.toDouble() + PIXEL_CENTER) * imageEdge) / canvasEdge)
            .toInt()
            .coerceIn(0, imageEdge - 1)

    @RequiresApi(FRAME_TIMELINE_API)
    private fun captureCheckpoint(
        name: String,
        sampleIndex: Int,
    ): P2AndroidPhysicalCheckpoint =
        P2AndroidPhysicalCheckpointCapture.capture(
            context = composeRule.activity,
            display = composeRule.activity.window.decorView.display,
            name = name,
            sampleIndex = sampleIndex,
        )

    private data class P2DisplayedFrame(
        val generation: Long,
        val renderState: EditorRenderState,
    )

    private data class P2FrameExecution(
        val generation: Long,
        val sample: P2AndroidFrameMeasurementSample?,
    )

    private companion object {
        const val INITIAL_GENERATION: Long = 0L
        const val FRAME_TIMEOUT_MILLIS: Long = 10_000L
        const val MINIMUM_FRAME_SAMPLE_COUNT: Int = 100
        const val HASH_MULTIPLIER: Int = 31
        const val PIXEL_CENTER: Double = 0.5
    }
}
