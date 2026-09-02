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
            val expectedOutcome = P2AndroidCommandMeasurementRunner.warmUp(spec, environment.warmupIterations)
            repeat(environment.sampleCount) { sampleIndex ->
                val execution = P2AndroidCommandMeasurementRunner.executeMeasured(spec)
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
}
