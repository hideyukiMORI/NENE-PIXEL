package io.github.hideyukimori.nenepixel.presentation.compose.editor

internal class P2RenderProjectionMeasurement(
    private val runner: P2HostMeasurementRunner = P2HostMeasurementRunner.create(),
) {
    fun measureAll(): List<P2RenderProjectionMetric> {
        P2RenderProjectionMatrix.validate(P2RenderProjectionMatrix.descriptors)
        val metrics = P2RenderProjectionMatrix.descriptors.map(::measure)
        validateMetrics(metrics)
        return metrics
    }

    private fun measure(descriptor: P2RenderProjectionDescriptor): P2RenderProjectionMetric {
        val fixture = P2RenderProjectionFixture.create(descriptor)
        return P2RenderProjectionMetric(
            descriptor = descriptor,
            measurement = runner.measure(P2RenderProjectionMatrix.sampling, fixture::prepare),
            colorCardinality = fixture.colorCardinality,
            firstArgb = fixture.firstArgb,
            lastArgb = fixture.lastArgb,
        )
    }

    private fun validateMetrics(metrics: List<P2RenderProjectionMetric>) {
        P2RenderProjectionMatrix.validate(metrics.map(P2RenderProjectionMetric::descriptor))
        check(metrics.all { metric -> metric.measurement.samples.latenciesNanos.size == EXPECTED_SAMPLES })
        check(metrics.all { metric -> metric.measurement.samples.allocatedBytes.size == EXPECTED_SAMPLES })
        check(metrics.all { metric -> metric.measurement.deterministicKey.status == "pass" })
        check(metrics.sumOf { metric -> metric.measurement.samples.latenciesNanos.size } == EXPECTED_RAW_SAMPLES)
    }

    private companion object {
        const val EXPECTED_SAMPLES: Int = P2RenderProjectionMatrix.SAMPLE_COUNT
        const val EXPECTED_RAW_SAMPLES: Int = P2RenderProjectionMatrix.RAW_SAMPLE_COUNT
    }
}

internal data class P2RenderProjectionMetric(
    val descriptor: P2RenderProjectionDescriptor,
    val measurement: P2HostMeasurement<P2RenderProjectionCorrectness>,
    val colorCardinality: Int,
    val firstArgb: Int,
    val lastArgb: Int,
)
