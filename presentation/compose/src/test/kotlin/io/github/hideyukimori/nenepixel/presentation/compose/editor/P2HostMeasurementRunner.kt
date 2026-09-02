package io.github.hideyukimori.nenepixel.presentation.compose.editor

import com.sun.management.ThreadMXBean
import org.junit.jupiter.api.Assertions.assertEquals
import java.lang.management.ManagementFactory
import kotlin.math.ceil

internal class P2HostMeasurementRunner private constructor(
    private val allocationCounter: ThreadAllocationCounter,
) {
    fun <T : Any, K : Any> measure(
        sampling: P2HostSampling,
        prepare: () -> P2HostMeasuredOperation<T, K>,
    ): P2HostMeasurement<K> {
        var deterministicKey: K? = null
        repeat(sampling.warmupIterations) {
            deterministicKey = executeWarmup(prepare, deterministicKey)
        }
        return measureSamples(sampling, prepare, deterministicKey)
    }

    private fun <T : Any, K : Any> executeWarmup(
        prepare: () -> P2HostMeasuredOperation<T, K>,
        previousKey: K?,
    ): K {
        val operation = prepare()
        val result = operation.execute()
        operation.verify(result)
        return previousKey.assertDeterministic(operation.deterministicKey(result))
    }

    private fun <T : Any, K : Any> measureSamples(
        sampling: P2HostSampling,
        prepare: () -> P2HostMeasuredOperation<T, K>,
        warmupKey: K?,
    ): P2HostMeasurement<K> {
        var deterministicKey = warmupKey
        val latencies = LongArray(sampling.sampleCount)
        val allocations = LongArray(sampling.sampleCount)
        repeat(sampling.sampleCount) { index ->
            val operation = prepare()
            val measured = measure(operation)
            latencies[index] = measured.latencyNanos
            allocations[index] = measured.allocatedBytes
            operation.verify(measured.result)
            deterministicKey = deterministicKey.assertDeterministic(operation.deterministicKey(measured.result))
        }
        return P2HostMeasurement(
            samples = P2HostRawSamples(latencies, allocations),
            latency = latencies.percentiles(),
            allocation = allocations.percentiles(),
            deterministicKey = requireNotNull(deterministicKey),
        )
    }

    private fun <T : Any> measure(operation: P2HostMeasuredOperation<T, *>): MeasuredResult<T> {
        val allocationBefore = allocationCounter.currentThreadBytes()
        val timeBefore = System.nanoTime()
        val result = operation.execute()
        val latencyNanos = System.nanoTime() - timeBefore
        val allocatedBytes = allocationCounter.currentThreadBytes() - allocationBefore
        return MeasuredResult(result, latencyNanos, allocatedBytes)
    }

    private fun LongArray.percentiles(): P2HostPercentiles =
        P2HostPercentiles(
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

    private data class MeasuredResult<T : Any>(
        val result: T,
        val latencyNanos: Long,
        val allocatedBytes: Long,
    )

    private class ThreadAllocationCounter private constructor(
        private val bean: ThreadMXBean,
    ) {
        fun currentThreadBytes(): Long = bean.getThreadAllocatedBytes(Thread.currentThread().threadId())

        companion object {
            fun create(): ThreadAllocationCounter {
                val bean = ManagementFactory.getThreadMXBean()
                require(bean is ThreadMXBean && bean.isThreadAllocatedMemorySupported) {
                    "The named P2 host profile requires HotSpot thread-allocation measurement support."
                }
                if (!bean.isThreadAllocatedMemoryEnabled) bean.isThreadAllocatedMemoryEnabled = true
                return ThreadAllocationCounter(bean)
            }
        }
    }

    companion object {
        fun create(): P2HostMeasurementRunner = P2HostMeasurementRunner(ThreadAllocationCounter.create())

        private const val MEDIAN_PERCENTILE: Double = 0.50
        private const val P95_PERCENTILE: Double = 0.95
        private const val P99_PERCENTILE: Double = 0.99
    }
}

internal data class P2HostSampling(
    val warmupIterations: Int,
    val sampleCount: Int,
) {
    init {
        require(warmupIterations > 0)
        require(sampleCount > 0)
    }
}

internal data class P2HostMeasuredOperation<T : Any, K : Any>(
    val execute: () -> T,
    val verify: (T) -> Unit,
    val deterministicKey: (T) -> K,
)

internal data class P2HostRawSamples(
    val latenciesNanos: LongArray,
    val allocatedBytes: LongArray,
) {
    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is P2HostRawSamples &&
                    latenciesNanos.contentEquals(other.latenciesNanos) &&
                    allocatedBytes.contentEquals(other.allocatedBytes)
            )

    override fun hashCode(): Int = 31 * latenciesNanos.contentHashCode() + allocatedBytes.contentHashCode()
}

internal data class P2HostPercentiles(
    val median: Long,
    val p95: Long,
    val p99: Long,
)

internal data class P2HostMeasurement<K : Any>(
    val samples: P2HostRawSamples,
    val latency: P2HostPercentiles,
    val allocation: P2HostPercentiles,
    val deterministicKey: K,
)
