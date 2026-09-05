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
        val plan = P2AndroidFinalCommandProtocol.resolve(environment, identity)
        val specs = plan.specs
        val correctness = specs.map(P2AndroidCommandMeasurementRunner::verifyCorrectness)
        val expectedOutcomes =
            specs.associateWith { spec ->
                P2AndroidCommandMeasurementRunner
                    .warmUp(spec, plan.warmupIterations)
                    .also { outcome ->
                        assertEquals(
                            correctness.single { descriptor -> descriptor.spec == spec }.outcome,
                            outcome,
                        )
                    }
            }
        val baselineMemory = PostGcMemorySnapshot.captureBaseline(environment)
        val display = P2AndroidPhysicalCheckpointCapture.defaultDisplay(environment.targetContext)
        val baselineCheckpoint =
            P2AndroidPhysicalCheckpointCapture
                .capture(environment.targetContext, display, "before_samples", sampleIndex = 0)
                .also(P2AndroidFinalCommandProfile::validateBaselineCheckpoint)
        val checkpoints = mutableListOf(baselineCheckpoint)
        val samples = mutableListOf<P2AndroidFinalCommandSample>()

        var globalSampleIndex = 0
        specs.forEach { spec ->
            repeat(plan.samplesPerWorkload) { zeroBasedIndex ->
                val localSampleIndex = zeroBasedIndex + 1
                globalSampleIndex += 1
                val execution = P2AndroidCommandMeasurementRunner.executeMeasured(spec)
                assertEquals(expectedOutcomes.getValue(spec), execution.outcome)
                samples +=
                    P2AndroidFinalCommandSample(
                        spec = spec,
                        indices =
                            P2AndroidFinalCommandSample.Indices(
                                local = localSampleIndex,
                                global = globalSampleIndex,
                            ),
                        observation =
                            P2AndroidFinalCommandSample.Observation(
                                latencyNanos = execution.latencyNanos,
                                runtimeDelta = execution.runtimeDelta,
                            ),
                        outcome = execution.outcome,
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
                    plan = plan,
                    run = P2AndroidFinalCommandReportInput.Run(environment, identity),
                    observations =
                        P2AndroidFinalCommandReportInput.Observations(
                            correctness = correctness,
                            baseline = baselineMemory,
                            checkpoints = checkpoints,
                            samples = samples,
                        ),
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
