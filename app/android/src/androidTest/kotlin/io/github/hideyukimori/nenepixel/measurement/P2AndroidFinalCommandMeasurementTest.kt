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
        val output = environment.finalCommandOutputFile(plan)
        val outputDirectory = requireNotNull(output.parentFile)
        check(outputDirectory.isDirectory || outputDirectory.mkdirs())
        val reservation = P2AndroidFinalCommandOutputPublication.reserve(output, plan.publicationPolicy)
        reservation.bindIdentity(plan, identity)
        var phase = "correctness"
        var completedCorrectness = 0
        var completedWarmups = 0
        var completedSamples = 0
        val samples = mutableListOf<P2AndroidFinalCommandSample>()
        try {
            runMeasurement(
                environment = environment,
                identity = identity,
                plan = plan,
                reservation = reservation,
                samples = samples,
                progress = { nextPhase, correctnessCount, warmupCount, sampleCount ->
                    phase = nextPhase
                    completedCorrectness = correctnessCount
                    completedWarmups = warmupCount
                    completedSamples = sampleCount
                },
            )
        } catch (failure: Throwable) {
            try {
                reservation.recordFailure(
                    phase,
                    completedCorrectness,
                    completedWarmups,
                    completedSamples,
                    samples,
                    failure,
                )
            } catch (publicationFailure: Throwable) {
                failure.addSuppressed(publicationFailure)
            }
            throw failure
        }
    }

    private fun runMeasurement(
        environment: P2AndroidMeasurementEnvironment,
        identity: P2AndroidRunIdentity,
        plan: P2AndroidFinalCommandPlan,
        reservation: P2AndroidFinalCommandOutputPublication.Reservation,
        samples: MutableList<P2AndroidFinalCommandSample>,
        progress: (String, Int, Int, Int) -> Unit,
    ) {
        val specs = plan.specs
        val correctness =
            specs.mapIndexed { index, spec ->
                P2AndroidCommandMeasurementRunner.verifyCorrectness(spec).also {
                    progress("correctness", index + 1, 0, 0)
                }
            }
        val expectedOutcomes =
            specs
                .mapIndexed { index, spec ->
                    val outcome =
                        P2AndroidCommandMeasurementRunner
                            .warmUp(spec, plan.warmupIterations)
                            .also {
                                assertEquals(correctness[index].outcome, it)
                                progress(
                                    "warmup",
                                    correctness.size,
                                    (index + 1) * plan.warmupIterations,
                                    0,
                                )
                            }
                    spec to outcome
                }.toMap()
        val baselineMemory = PostGcMemorySnapshot.captureBaseline(environment)
        val display = P2AndroidPhysicalCheckpointCapture.defaultDisplay(environment.targetContext)
        val baselineCheckpoint =
            P2AndroidPhysicalCheckpointCapture
                .capture(environment.targetContext, display, "before_samples", sampleIndex = 0)
                .also(P2AndroidFinalCommandProfile::validateBaselineCheckpoint)
        val checkpoints = mutableListOf(baselineCheckpoint)
        var globalSampleIndex = 0
        specs.forEach { spec ->
            repeat(plan.samplesPerWorkload) { zeroBasedIndex ->
                val localSampleIndex = zeroBasedIndex + 1
                progress(
                    "samples",
                    correctness.size,
                    plan.workloadCount * plan.warmupIterations,
                    globalSampleIndex,
                )
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
                progress(
                    "samples",
                    correctness.size,
                    plan.workloadCount * plan.warmupIterations,
                    globalSampleIndex,
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
        progress(
            "publication",
            correctness.size,
            plan.workloadCount * plan.warmupIterations,
            globalSampleIndex,
        )
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
                reservation,
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
