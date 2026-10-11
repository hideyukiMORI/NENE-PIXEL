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
        val configuration = P2FinalCommandConfiguration(environment, identity, plan)
        val output = environment.finalCommandOutputFile(plan)
        val outputDirectory = requireNotNull(output.parentFile)
        check(outputDirectory.isDirectory || outputDirectory.mkdirs())
        val reservation = P2AndroidFinalCommandOutputPublication.reserve(output, plan.publicationPolicy)
        reservation.bindIdentity(plan, identity)
        val publication = P2FinalCommandPublication(reservation)
        runWithFailureCapture(configuration, publication)
    }

    private fun runWithFailureCapture(
        configuration: P2FinalCommandConfiguration,
        publication: P2FinalCommandPublication,
    ) {
        try {
            runMeasurement(configuration, publication)
        } catch (failure: Throwable) {
            try {
                publication.reservation.recordFailure(publication.progress, publication.samples, failure)
            } catch (publicationFailure: Throwable) {
                failure.addSuppressed(publicationFailure)
            }
            throw failure
        }
    }

    private fun runMeasurement(
        configuration: P2FinalCommandConfiguration,
        publication: P2FinalCommandPublication,
    ) {
        val specs = configuration.plan.specs
        val correctness = verifyCorrectness(specs, publication.progress)
        val expectedOutcomes = warmUp(specs, configuration.plan, correctness, publication.progress)
        val preparation = P2FinalCommandPreparation(specs, correctness, expectedOutcomes)
        val baselineMemory = PostGcMemorySnapshot.captureBaseline(configuration.environment)
        val checkpoints = baselineCheckpoints(configuration.environment)
        val globalSampleIndex = recordSamples(configuration, publication, preparation, checkpoints)
        captureCompatibleCheckpoint(
            configuration.environment,
            checkpoints.display,
            checkpoints.baseline,
            P2FinalCheckpointIdentity("after_samples", globalSampleIndex),
        ).also(checkpoints.values::add)
        publication.progress.update(
            "publication",
            correctness.size,
            configuration.plan.workloadCount * configuration.plan.warmupIterations,
            globalSampleIndex,
        )
        publish(
            configuration,
            publication,
            preparation,
            P2FinalCommandObservations(baselineMemory, checkpoints.values),
        )
    }

    private fun verifyCorrectness(
        specs: List<P2CommandWorkloadSpec>,
        progress: P2FinalCommandProgress,
    ): List<CommandCorrectnessDescriptor> =
        specs.mapIndexed { index, spec ->
            P2AndroidCommandMeasurementRunner.verifyCorrectness(spec).also {
                progress.update("correctness", index + 1, 0, 0)
            }
        }

    private fun warmUp(
        specs: List<P2CommandWorkloadSpec>,
        plan: P2AndroidFinalCommandPlan,
        correctness: List<CommandCorrectnessDescriptor>,
        progress: P2FinalCommandProgress,
    ): Map<P2CommandWorkloadSpec, CommandOutcomeDescriptor> =
        specs
            .mapIndexed { index, spec ->
                val outcome =
                    P2AndroidCommandMeasurementRunner
                        .warmUp(spec, plan.warmupIterations)
                        .also {
                            assertEquals(correctness[index].outcome, it)
                            progress.update("warmup", correctness.size, (index + 1) * plan.warmupIterations, 0)
                        }
                spec to outcome
            }.toMap()

    private fun baselineCheckpoints(environment: P2AndroidMeasurementEnvironment): P2FinalCommandCheckpoints {
        val display = P2AndroidPhysicalCheckpointCapture.defaultDisplay(environment.targetContext)
        val baseline =
            P2AndroidPhysicalCheckpointCapture
                .capture(environment.targetContext, display, "before_samples", sampleIndex = 0)
                .also(P2AndroidFinalCommandProfile::validateBaselineCheckpoint)
        return P2FinalCommandCheckpoints(display, baseline, mutableListOf(baseline))
    }

    private fun recordSamples(
        configuration: P2FinalCommandConfiguration,
        publication: P2FinalCommandPublication,
        preparation: P2FinalCommandPreparation,
        checkpoints: P2FinalCommandCheckpoints,
    ): Int {
        val plan = configuration.plan
        var globalSampleIndex = 0
        preparation.specs.forEach { spec ->
            repeat(plan.samplesPerWorkload) { zeroBasedIndex ->
                val localSampleIndex = zeroBasedIndex + 1
                publication.reportSampleProgress(plan, preparation.correctness.size, globalSampleIndex)
                globalSampleIndex += 1
                val execution = P2AndroidCommandMeasurementRunner.executeMeasured(spec)
                assertEquals(preparation.expectedOutcomes.getValue(spec), execution.outcome)
                publication.samples += sample(spec, localSampleIndex, globalSampleIndex, execution)
                publication.reportSampleProgress(plan, preparation.correctness.size, globalSampleIndex)
                if (globalSampleIndex % P2AndroidPhysicalCheckpointPolicy.CHECKPOINT_INTERVAL == 0) {
                    captureCompatibleCheckpoint(
                        configuration.environment,
                        checkpoints.display,
                        checkpoints.baseline,
                        P2FinalCheckpointIdentity("after_$globalSampleIndex", globalSampleIndex),
                    ).also(checkpoints.values::add)
                }
            }
        }
        return globalSampleIndex
    }

    private fun sample(
        spec: P2CommandWorkloadSpec,
        localSampleIndex: Int,
        globalSampleIndex: Int,
        execution: P2MeasuredCommandExecution,
    ): P2AndroidFinalCommandSample =
        P2AndroidFinalCommandSample(
            spec = spec,
            indices = P2AndroidFinalCommandSample.Indices(local = localSampleIndex, global = globalSampleIndex),
            observation =
                P2AndroidFinalCommandSample.Observation(
                    latencyNanos = execution.latencyNanos,
                    runtimeDelta = execution.runtimeDelta,
                ),
            outcome = execution.outcome,
        )

    private fun publish(
        configuration: P2FinalCommandConfiguration,
        publication: P2FinalCommandPublication,
        preparation: P2FinalCommandPreparation,
        observations: P2FinalCommandObservations,
    ) {
        val output =
            P2AndroidFinalCommandMeasurementReport.write(
                P2AndroidFinalCommandReportInput(
                    plan = configuration.plan,
                    run = P2AndroidFinalCommandReportInput.Run(configuration.environment, configuration.identity),
                    observations =
                        P2AndroidFinalCommandReportInput.Observations(
                            correctness = preparation.correctness,
                            baseline = observations.baselineMemory,
                            checkpoints = observations.checkpoints,
                            samples = publication.samples,
                        ),
                ),
                publication.reservation,
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

private data class P2FinalCommandConfiguration(
    val environment: P2AndroidMeasurementEnvironment,
    val identity: P2AndroidRunIdentity,
    val plan: P2AndroidFinalCommandPlan,
)

private class P2FinalCommandPublication(
    val reservation: P2AndroidFinalCommandOutputPublication.Reservation,
) {
    val samples = mutableListOf<P2AndroidFinalCommandSample>()
    val progress = P2FinalCommandProgress("correctness", 0, 0, 0)

    fun reportSampleProgress(
        plan: P2AndroidFinalCommandPlan,
        correctnessCount: Int,
        globalSampleIndex: Int,
    ) {
        progress.update("samples", correctnessCount, plan.workloadCount * plan.warmupIterations, globalSampleIndex)
    }
}

private data class P2FinalCommandPreparation(
    val specs: List<P2CommandWorkloadSpec>,
    val correctness: List<CommandCorrectnessDescriptor>,
    val expectedOutcomes: Map<P2CommandWorkloadSpec, CommandOutcomeDescriptor>,
)

private data class P2FinalCommandCheckpoints(
    val display: android.view.Display,
    val baseline: P2AndroidPhysicalCheckpoint,
    val values: MutableList<P2AndroidPhysicalCheckpoint>,
)

private data class P2FinalCommandObservations(
    val baselineMemory: PostGcMemorySnapshot,
    val checkpoints: List<P2AndroidPhysicalCheckpoint>,
)

private data class P2FinalCheckpointIdentity(
    val name: String,
    val sampleIndex: Int,
)
