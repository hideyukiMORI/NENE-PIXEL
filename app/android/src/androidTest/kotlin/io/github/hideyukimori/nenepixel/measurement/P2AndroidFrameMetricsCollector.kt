package io.github.hideyukimori.nenepixel.measurement

import android.os.Handler
import android.os.HandlerThread
import android.view.FrameMetrics
import android.view.Window
import androidx.annotation.RequiresApi
import io.github.hideyukimori.nenepixel.presentation.compose.editor.EditorRenderState
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal data class P2CanonicalDrawMarker(
    val generation: Long,
    val exactState: Boolean,
    val revision: Long,
    val snapshotHash: Int,
    val drawingTimeMillis: Long,
    val callbackNanos: Long,
)

internal data class P2CopiedFrameMetrics(
    val droppedReports: Int,
    val firstDrawFrame: Long,
    val intendedVsyncNanos: Long,
    val vsyncNanos: Long,
    val frameTimelineVsyncId: Long,
    val totalDurationNanos: Long,
    val deadlineNanos: Long,
    val gpuDurationNanos: Long,
    val drawDurationNanos: Long,
    val layoutMeasureDurationNanos: Long,
    val syncDurationNanos: Long,
    val commandIssueDurationNanos: Long,
    val swapBuffersDurationNanos: Long,
    val inputHandlingDurationNanos: Long,
    val unknownDelayDurationNanos: Long,
) {
    val deadlineMet: Boolean
        get() = totalDurationNanos <= deadlineNanos

    val overrunNanos: Long
        get() = (totalDurationNanos - deadlineNanos).coerceAtLeast(0L)
}

internal data class P2CorrelatedFrame(
    val marker: P2CanonicalDrawMarker,
    val metrics: P2CopiedFrameMetrics,
)

@RequiresApi(FRAME_TIMELINE_API)
internal class P2AndroidFrameMetricsCollector(
    private val window: Window,
) : AutoCloseable {
    private val callbackThread = HandlerThread("nene-p2-frame-metrics").apply { start() }
    private val lock = ReentrantLock()
    private val updated = lock.newCondition()
    private val frames = mutableListOf<P2CopiedFrameMetrics>()
    private val markers = mutableListOf<P2CanonicalDrawMarker>()
    private var expectedDraw: ExpectedDraw? = null
    private var totalDroppedReports: Int = 0
    private var attached: Boolean = false

    private val listener =
        Window.OnFrameMetricsAvailableListener { _, metrics, droppedReports ->
            val copied = FrameMetrics(metrics)
            val frame = copied.toMeasurement(droppedReports)
            lock.withLock {
                totalDroppedReports += droppedReports
                frames += frame
                updated.signalAll()
            }
        }

    fun attach() {
        check(!attached) { "Frame metrics collector is already attached." }
        window.addOnFrameMetricsAvailableListener(listener, Handler(callbackThread.looper))
        attached = true
    }

    fun arm(
        generation: Long,
        expectedState: EditorRenderState,
    ) {
        lock.withLock {
            expectedDraw?.let { previous ->
                check(generation > previous.generation) { "Frame generations must increase monotonically." }
            }
            expectedDraw = ExpectedDraw(generation, expectedState, markers.size, frames.size)
        }
    }

    fun recordCanonicalContentDrawn(
        generation: Long,
        state: EditorRenderState,
    ) {
        val drawingTimeMillis = requireNotNull(window.decorView).drawingTime
        lock.withLock {
            val expected = expectedDraw
            val exactState =
                expected != null &&
                    expected.generation == generation &&
                    expected.state == state
            markers +=
                P2CanonicalDrawMarker(
                    generation = generation,
                    exactState = exactState,
                    revision = state.snapshot.revision.value,
                    snapshotHash = state.snapshot.hashCode(),
                    drawingTimeMillis = drawingTimeMillis,
                    callbackNanos = System.nanoTime(),
                )
            updated.signalAll()
        }
    }

    fun awaitFirstCorrectFrame(
        generation: Long,
        timeoutMillis: Long,
    ): P2CorrelatedFrame {
        val timeoutNanos = TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        val deadline = System.nanoTime() + timeoutNanos
        lock.withLock {
            while (true) {
                check(totalDroppedReports == 0) {
                    "FrameMetrics dropped $totalDroppedReports report(s); the batch is invalid."
                }
                val incorrect = markers.firstOrNull { marker -> marker.generation == generation && !marker.exactState }
                check(incorrect == null) {
                    "Generation $generation reached draw with a non-exact EditorRenderState."
                }
                correlatedFrame(generation)?.let { correlated -> return correlated }
                val remaining = deadline - System.nanoTime()
                check(remaining > 0L) { timeoutMessage(generation) }
                updated.awaitNanos(remaining)
            }
        }
    }

    fun assertNoDroppedReports() {
        lock.withLock {
            check(totalDroppedReports == 0) {
                "FrameMetrics dropped $totalDroppedReports report(s); the batch is invalid."
            }
        }
    }

    override fun close() {
        if (attached) {
            window.removeOnFrameMetricsAvailableListener(listener)
            attached = false
        }
        callbackThread.quitSafely()
        callbackThread.join(CALLBACK_THREAD_JOIN_MILLIS)
        check(!callbackThread.isAlive) {
            "Timed out stopping the frame metrics callback thread."
        }
    }

    private fun correlatedFrame(generation: Long): P2CorrelatedFrame? {
        val expected = expectedDraw?.takeIf { draw -> draw.generation == generation } ?: return null
        return markers
            .asSequence()
            .drop(expected.markerStartIndex)
            .filter { marker -> marker.generation == generation && marker.exactState }
            .sortedBy(P2CanonicalDrawMarker::callbackNanos)
            .mapNotNull { marker ->
                matchingFrame(marker, expected.frameStartIndex)?.let { frame -> P2CorrelatedFrame(marker, frame) }
            }.firstOrNull()
    }

    private fun matchingFrame(
        marker: P2CanonicalDrawMarker,
        frameStartIndex: Int,
    ): P2CopiedFrameMetrics? =
        frames
            .asSequence()
            .drop(frameStartIndex)
            .filter { frame -> frame.vsyncNanos > 0L }
            .firstOrNull { frame ->
                TimeUnit.NANOSECONDS.toMillis(frame.vsyncNanos) == marker.drawingTimeMillis
            }

    private fun timeoutMessage(generation: Long): String {
        val generationMarkers = markers.filter { candidate -> candidate.generation == generation }
        val marker = generationMarkers.lastOrNull()
        val latestFrame = frames.lastOrNull()
        return "Timed out waiting for exact frame generation $generation; " +
            "markerDrawingTimeMillis=${marker?.drawingTimeMillis}; " +
            "latestIntendedVsyncMillis=${latestFrame?.intendedVsyncNanos?.let(TimeUnit.NANOSECONDS::toMillis)}; " +
            "latestVsyncMillis=${latestFrame?.vsyncNanos?.let(TimeUnit.NANOSECONDS::toMillis)}; " +
            "closestVsyncDeltaMillis=${marker?.let(::closestVsyncDeltaMillis)}; " +
            "generationMarkerCount=${generationMarkers.size}; " +
            "exactGenerationMarkerCount=${generationMarkers.count(P2CanonicalDrawMarker::exactState)}; " +
            "markerCount=${markers.size}; frameCount=${frames.size}; droppedReports=$totalDroppedReports."
    }

    private fun closestVsyncDeltaMillis(marker: P2CanonicalDrawMarker): Long? =
        frames
            .asSequence()
            .map(P2CopiedFrameMetrics::vsyncNanos)
            .filter { vsyncNanos -> vsyncNanos > 0L }
            .map { vsyncNanos ->
                kotlin.math.abs(TimeUnit.NANOSECONDS.toMillis(vsyncNanos) - marker.drawingTimeMillis)
            }.minOrNull()

    private data class ExpectedDraw(
        val generation: Long,
        val state: EditorRenderState,
        val markerStartIndex: Int,
        val frameStartIndex: Int,
    )

    private companion object {
        const val CALLBACK_THREAD_JOIN_MILLIS: Long = 5_000L
    }
}

@RequiresApi(FRAME_TIMELINE_API)
private fun FrameMetrics.toMeasurement(droppedReports: Int): P2CopiedFrameMetrics =
    P2CopiedFrameMetrics(
        droppedReports = droppedReports,
        firstDrawFrame = getMetric(FrameMetrics.FIRST_DRAW_FRAME),
        intendedVsyncNanos = getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP),
        vsyncNanos = getMetric(FrameMetrics.VSYNC_TIMESTAMP),
        frameTimelineVsyncId = getMetric(FrameMetrics.FRAME_TIMELINE_VSYNC_ID),
        totalDurationNanos = getMetric(FrameMetrics.TOTAL_DURATION),
        deadlineNanos = getMetric(FrameMetrics.DEADLINE),
        gpuDurationNanos = getMetric(FrameMetrics.GPU_DURATION),
        drawDurationNanos = getMetric(FrameMetrics.DRAW_DURATION),
        layoutMeasureDurationNanos = getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION),
        syncDurationNanos = getMetric(FrameMetrics.SYNC_DURATION),
        commandIssueDurationNanos = getMetric(FrameMetrics.COMMAND_ISSUE_DURATION),
        swapBuffersDurationNanos = getMetric(FrameMetrics.SWAP_BUFFERS_DURATION),
        inputHandlingDurationNanos = getMetric(FrameMetrics.INPUT_HANDLING_DURATION),
        unknownDelayDurationNanos = getMetric(FrameMetrics.UNKNOWN_DELAY_DURATION),
    )

internal const val FRAME_TIMELINE_API: Int = 36
