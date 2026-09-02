package io.github.hideyukimori.nenepixel.core.pixelengine.measurement

import org.junit.jupiter.api.Test

internal class P2CandidateMeasurementTest {
    @Test
    fun `measure test-only pixel-engine candidates`() {
        val allocationCounter = P2ThreadAllocationCounter.create()
        val candidates = P2CandidateMeasurement.measure(allocationCounter)
        val patchCandidates = P2CandidatePatchMeasurement.measure(allocationCounter)
        val rawPathCandidates = P2CandidateRawPathMeasurement.measure(allocationCounter)
        val retainedHistoryCandidates = P2CandidateRetainedHistoryMeasurement.measure(allocationCounter)

        P2CandidateMeasurementReport.write(
            candidates,
            patchCandidates,
            rawPathCandidates,
            retainedHistoryCandidates,
        )
    }
}
