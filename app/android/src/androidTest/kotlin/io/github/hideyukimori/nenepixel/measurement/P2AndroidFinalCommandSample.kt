package io.github.hideyukimori.nenepixel.measurement

internal data class P2AndroidFinalCommandSample(
    val spec: P2CommandWorkloadSpec,
    val indices: Indices,
    val observation: Observation,
    val outcome: CommandOutcomeDescriptor,
) {
    data class Indices(
        val local: Int,
        val global: Int,
    )

    data class Observation(
        val latencyNanos: Long,
        val runtimeDelta: ArtRuntimeDelta,
    )

    val localSampleIndex: Int
        get() = indices.local

    val globalSampleIndex: Int
        get() = indices.global

    val latencyNanos: Long
        get() = observation.latencyNanos

    val runtimeDelta: ArtRuntimeDelta
        get() = observation.runtimeDelta
}
