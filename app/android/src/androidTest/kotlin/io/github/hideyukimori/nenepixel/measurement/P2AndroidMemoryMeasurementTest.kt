package io.github.hideyukimori.nenepixel.measurement

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class P2AndroidMemoryMeasurementTest {
    @Test
    fun collectOneImmutableCurrentRetainedMemoryRun() {
        val environment = P2AndroidMeasurementEnvironment.fromRunnerArguments()
        P2AndroidMemoryProtocol.validateEnvironment(environment)
        val identity = P2AndroidMemoryProtocol.runIdentity()
        val display = P2AndroidPhysicalCheckpointCapture.defaultDisplay(environment.targetContext)
        val beforeBaseline =
            P2AndroidPhysicalCheckpointCapture
                .capture(environment.targetContext, display, "before_baseline", sampleIndex = 0)
                .also(P2AndroidPhysicalCheckpoint::assertInitialValidity)

        P2AndroidMemoryRetainedWorkload.preload()
        val baseline = PostGcMemorySnapshot.capture(BASELINE_RETAINED_MARKER)
        val owner = P2AndroidMemoryRetainedWorkload.prepareAndVerify()
        val retained = PostGcMemorySnapshot.capture(owner)
        val afterRetained =
            P2AndroidPhysicalCheckpointCapture
                .capture(environment.targetContext, display, "after_retained", sampleIndex = 1)
                .also { checkpoint -> checkpoint.assertCompatibleWith(beforeBaseline) }
        val prepared = P2AndroidMemoryRetainedWorkload.reportState(owner)
        val output =
            P2AndroidMemoryInvocationReport.write(
                P2AndroidMemoryInvocationInput(
                    environment,
                    identity,
                    P2AndroidMemoryInvocationObservations(
                        physicalCheckpoints = listOf(beforeBaseline, afterRetained),
                        memory = P2AndroidMemoryCheckpointPair(baseline, retained),
                        prepared = prepared,
                    ),
                ),
            )
        assertTrue(output.isFile)
        assertTrue(output.length() > 0L)
        println("P2_ANDROID_MEMORY_RUN_OUTPUT=${output.absolutePath}")
    }

    private companion object {
        val BASELINE_RETAINED_MARKER: Any = Any()
    }
}
