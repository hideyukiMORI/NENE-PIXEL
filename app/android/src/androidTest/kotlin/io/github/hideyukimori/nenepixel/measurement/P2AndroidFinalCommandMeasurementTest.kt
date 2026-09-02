package io.github.hideyukimori.nenepixel.measurement

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class P2AndroidFinalCommandMeasurementTest {
    @Test
    fun measureFinalCurrentCommandTailOnPhysicalProfile() {
        val environment = P2AndroidMeasurementEnvironment.fromRunnerArguments()
        val identity = P2AndroidRunIdentity.fromRunnerArguments()
        P2AndroidFinalCommandProtocol.validate(environment, identity)
        val specs = P2AndroidFinalCommandProtocol.specs
        val expectedOutcomes =
            specs.associateWith { spec ->
                P2AndroidCommandMeasurementRunner.warmUp(spec, environment.warmupIterations)
            }
        val baselineMemory = PostGcMemorySnapshot.capture(environment)
        val display = P2AndroidPhysicalCheckpointCapture.defaultDisplay(environment.targetContext)
        val baselineCheckpoint =
            P2AndroidPhysicalCheckpointCapture
                .capture(environment.targetContext, display, "before_samples", sampleIndex = 0)
                .also(P2AndroidPhysicalCheckpoint::assertInitialValidity)
        val checkpoints = mutableListOf(baselineCheckpoint)
        val samples = mutableListOf<P2AndroidFinalCommandSample>()

        var globalSampleIndex = 0
        specs.forEach { spec ->
            repeat(environment.sampleCount) { zeroBasedIndex ->
                val localSampleIndex = zeroBasedIndex + 1
                globalSampleIndex += 1
                val execution = P2AndroidCommandMeasurementRunner.executeMeasured(spec)
                assertEquals(expectedOutcomes.getValue(spec), execution.outcome)
                samples +=
                    P2AndroidFinalCommandSample(
                        spec = spec,
                        localSampleIndex = localSampleIndex,
                        globalSampleIndex = globalSampleIndex,
                        latencyNanos = execution.latencyNanos,
                        outcome = execution.outcome,
                        runtimeDelta = execution.runtimeDelta,
                        memory = PostGcMemorySnapshot.capture(execution.retainedWorkload),
                    )
                if (globalSampleIndex % P2AndroidPhysicalCheckpointPolicy.CHECKPOINT_INTERVAL == 0) {
                    captureCompatibleCheckpoint(
                        environment,
                        display,
                        baselineCheckpoint,
                        P2FinalCheckpointIdentity("after_$globalSampleIndex", globalSampleIndex),
                    ).also(checkpoints::add)
                }
            }
        }

        captureCompatibleCheckpoint(
            environment,
            display,
            baselineCheckpoint,
            P2FinalCheckpointIdentity("after_samples", globalSampleIndex),
        ).also(checkpoints::add)
        val output =
            P2AndroidFinalCommandMeasurementReport.write(
                P2AndroidFinalCommandReportInput(
                    environment = environment,
                    identity = identity,
                    baseline = baselineMemory,
                    checkpoints = checkpoints,
                    samples = samples,
                ),
            )
        assertTrue(output.isFile)
        assertTrue(output.length() > 0L)
        println("P2_ANDROID_FINAL_COMMAND_OUTPUT=${output.absolutePath}")
    }

    private fun captureCompatibleCheckpoint(
        environment: P2AndroidMeasurementEnvironment,
        display: android.view.Display,
        baseline: P2AndroidPhysicalCheckpoint,
        identity: P2FinalCheckpointIdentity,
    ): P2AndroidPhysicalCheckpoint =
        P2AndroidPhysicalCheckpointCapture
            .capture(environment.targetContext, display, identity.name, identity.sampleIndex)
            .also { checkpoint -> checkpoint.assertCompatibleWith(baseline) }
}

private data class P2FinalCheckpointIdentity(
    val name: String,
    val sampleIndex: Int,
)
