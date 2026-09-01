package io.github.hideyukimori.nenepixel.core.pixelengine.measurement

import org.junit.jupiter.api.Test

internal class P2CandidateMeasurementTest {
    @Test
    fun `measure test-only pixel-engine candidates`() {
        val candidates = P2CandidateMeasurement.measure(P2ThreadAllocationCounter.create())

        P2CandidateMeasurementReport.write(candidates)
    }
}
