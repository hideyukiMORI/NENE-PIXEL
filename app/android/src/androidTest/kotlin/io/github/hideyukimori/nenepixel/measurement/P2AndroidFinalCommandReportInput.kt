package io.github.hideyukimori.nenepixel.measurement

internal data class P2AndroidFinalCommandReportInput(
    val plan: P2AndroidFinalCommandPlan,
    val run: Run,
    val observations: Observations,
) {
    data class Run(
        val environment: P2AndroidMeasurementEnvironment,
        val identity: P2AndroidRunIdentity,
    )

    data class Observations(
        val correctness: List<CommandCorrectnessDescriptor>,
        val baseline: PostGcMemorySnapshot,
        val checkpoints: List<P2AndroidPhysicalCheckpoint>,
        val samples: List<P2AndroidFinalCommandSample>,
    )

    val environment: P2AndroidMeasurementEnvironment
        get() = run.environment

    val identity: P2AndroidRunIdentity
        get() = run.identity

    val baseline: PostGcMemorySnapshot
        get() = observations.baseline

    val correctness: List<CommandCorrectnessDescriptor>
        get() = observations.correctness

    val checkpoints: List<P2AndroidPhysicalCheckpoint>
        get() = observations.checkpoints

    val samples: List<P2AndroidFinalCommandSample>
        get() = observations.samples
}
