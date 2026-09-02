package io.github.hideyukimori.nenepixel.measurement

import org.junit.Assert.assertEquals

internal data class P2MeasuredCommandExecution(
    val latencyNanos: Long,
    val outcome: CommandOutcomeDescriptor,
    val runtimeDelta: ArtRuntimeDelta,
    val retainedWorkload: Any,
)

internal object P2AndroidCommandMeasurementRunner {
    fun warmUp(
        spec: P2CommandWorkloadSpec,
        iterations: Int,
    ): CommandOutcomeDescriptor {
        var expectedOutcome: CommandOutcomeDescriptor? = null
        repeat(iterations) {
            val workload = PreparedCommandWorkload.create(spec)
            val outcome = workload.verify(workload.execute())
            expectedOutcome?.let { previous -> assertEquals(previous, outcome) }
            expectedOutcome = outcome
        }
        return requireNotNull(expectedOutcome)
    }

    fun executeMeasured(spec: P2CommandWorkloadSpec): P2MeasuredCommandExecution {
        val workload = PreparedCommandWorkload.create(spec)
        val runtimeBefore = ArtRuntimeSnapshot.capture()
        val startedAtNanos = System.nanoTime()
        val result = workload.execute()
        val latencyNanos = System.nanoTime() - startedAtNanos
        val runtimeAfter = ArtRuntimeSnapshot.capture()
        val outcome = workload.verify(result)
        return P2MeasuredCommandExecution(
            latencyNanos = latencyNanos,
            outcome = outcome,
            runtimeDelta = runtimeAfter.deltaFrom(runtimeBefore),
            retainedWorkload = workload,
        )
    }
}
