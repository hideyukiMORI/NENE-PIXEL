package io.github.hideyukimori.nenepixel.core.application.document.history

import org.junit.jupiter.api.Assertions.assertEquals
import kotlin.math.ceil

internal class P2HostMeasurementRunner(
    private val allocationCounter: P2ThreadAllocationCounter,
) {
    fun <T : Any, K : Any> measure(
        descriptor: P2MeasurementDescriptor,
        prepare: () -> P2MeasuredOperation<T, K>,
    ): P2MeasurementMetric {
        var deterministicKey: K? = null
        repeat(descriptor.sampling.warmupIterations) {
            val operation = prepare()
            val result = operation.execute()
            operation.verify(result)
            deterministicKey = deterministicKey.assertDeterministic(operation.deterministicKey(result))
        }
        val samples = measureSamples(descriptor, prepare, deterministicKey)
        return P2MeasurementMetric(
            descriptor = descriptor,
            samples = samples,
            latency = samples.latenciesNanos.percentiles(),
            allocation = samples.allocatedBytes.percentiles(),
        )
    }

    private fun <T : Any, K : Any> measureSamples(
        descriptor: P2MeasurementDescriptor,
        prepare: () -> P2MeasuredOperation<T, K>,
        warmupKey: K?,
    ): P2RawSamples {
        var deterministicKey = warmupKey
        val latencies = LongArray(descriptor.sampling.sampleCount)
        val allocations = LongArray(descriptor.sampling.sampleCount)
        repeat(descriptor.sampling.sampleCount) { index ->
            val operation = prepare()
            val allocationBefore = allocationCounter.currentThreadBytes()
            val timeBefore = System.nanoTime()
            val result = operation.execute()
            latencies[index] = System.nanoTime() - timeBefore
            allocations[index] = allocationCounter.currentThreadBytes() - allocationBefore
            operation.verify(result)
            deterministicKey = deterministicKey.assertDeterministic(operation.deterministicKey(result))
        }
        requireNotNull(deterministicKey)
        return P2RawSamples(latencies, allocations)
    }

    private fun LongArray.percentiles(): P2Percentiles =
        P2Percentiles(
            median = percentile(MEDIAN_PERCENTILE),
            p95 = percentile(P95_PERCENTILE),
            p99 = percentile(P99_PERCENTILE),
        )

    private fun LongArray.percentile(percentile: Double): Long {
        val sorted = sortedArray()
        val index = ceil(sorted.size * percentile).toInt().coerceIn(1, sorted.size) - 1
        return sorted[index]
    }

    private fun <T : Any> T?.assertDeterministic(actual: T): T {
        if (this != null) assertEquals(this, actual)
        return actual
    }

    private companion object {
        const val MEDIAN_PERCENTILE: Double = 0.50
        const val P95_PERCENTILE: Double = 0.95
        const val P99_PERCENTILE: Double = 0.99
    }
}
