package io.github.hideyukimori.nenepixel.measurement

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class P2AndroidCommandMeasurementTest {
    @Test
    fun measureRepresentativeCommandWorkloadsOnArt() {
        val environment = P2AndroidMeasurementEnvironment.fromRunnerArguments()
        val baseline = PostGcMemorySnapshot.capture(environment)
        val samples = mutableListOf<P2AndroidMeasurementSample>()

        P2CommandWorkloadCatalog.specs.forEach { spec ->
            val expectedOutcome = warmUp(environment, spec)
            repeat(environment.sampleCount) { sampleIndex ->
                val execution = executeMeasured(spec)
                assertEquals(expectedOutcome, execution.outcome)
                samples +=
                    P2AndroidMeasurementSample(
                        spec = spec,
                        sampleIndex = sampleIndex,
                        latencyNanos = execution.latencyNanos,
                        outcome = execution.outcome,
                        runtimeDelta = execution.runtimeDelta,
                        memory = PostGcMemorySnapshot.capture(execution.retainedWorkload),
                    )
            }
        }

        val output = P2AndroidMeasurementReport.write(environment, baseline, samples)
        println("P2_ANDROID_MEASUREMENT_OUTPUT=${output.absolutePath}")
    }

    private fun warmUp(
        environment: P2AndroidMeasurementEnvironment,
        spec: P2CommandWorkloadSpec,
    ): CommandOutcomeDescriptor {
        var expectedOutcome: CommandOutcomeDescriptor? = null
        repeat(environment.warmupIterations) {
            val workload = PreparedCommandWorkload.create(spec)
            val outcome = workload.verify(workload.execute())
            expectedOutcome?.let { previous -> assertEquals(previous, outcome) }
            expectedOutcome = outcome
        }
        return requireNotNull(expectedOutcome)
    }

    private fun executeMeasured(spec: P2CommandWorkloadSpec): MeasuredExecution {
        val workload = PreparedCommandWorkload.create(spec)
        val runtimeBefore = ArtRuntimeSnapshot.capture()
        val startedAtNanos = System.nanoTime()
        val result = workload.execute()
        val latencyNanos = System.nanoTime() - startedAtNanos
        val runtimeAfter = ArtRuntimeSnapshot.capture()
        val outcome = workload.verify(result)
        return MeasuredExecution(
            latencyNanos = latencyNanos,
            outcome = outcome,
            runtimeDelta = runtimeAfter.deltaFrom(runtimeBefore),
            retainedWorkload = workload,
        )
    }
}

private data class MeasuredExecution(
    val latencyNanos: Long,
    val outcome: CommandOutcomeDescriptor,
    val runtimeDelta: ArtRuntimeDelta,
    val retainedWorkload: Any,
)
