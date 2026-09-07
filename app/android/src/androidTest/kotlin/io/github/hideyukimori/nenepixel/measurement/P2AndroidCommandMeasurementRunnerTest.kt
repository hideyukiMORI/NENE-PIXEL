package io.github.hideyukimori.nenepixel.measurement

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class P2AndroidCommandMeasurementRunnerTest {
    @Test
    fun latencySamplesNeverRunFullStateVerification() {
        P2CommandWorkloadCatalog
            .shapeSpecs(width = 16, height = 16, kinds = P2CommandWorkloadCatalog.m2Kinds)
            .forEach { spec ->
                val workload = PreparedCommandWorkload.createLatency(spec)

                P2AndroidCommandMeasurementRunner.executeMeasured(workload)

                assertFalse(workload.correctnessOraclePrepared)
                assertFalse(workload.fullStateVerificationPerformed)
            }
    }

    @Test
    fun correctnessLaneRunsFullStateVerification() {
        P2CommandWorkloadCatalog
            .shapeSpecs(width = 16, height = 16, kinds = P2CommandWorkloadCatalog.m2Kinds)
            .forEach { spec ->
                val workload = PreparedCommandWorkload.createCorrectness(spec)

                workload.verifyCorrectness(workload.execute())

                assertTrue(workload.correctnessOraclePrepared)
                assertTrue(workload.fullStateVerificationPerformed)
            }
    }

    @Test
    fun eraserLatencyFixtureDoesNotConstructCorrectnessOracle() {
        val spec = P2CommandWorkloadSpec(P2CommandWorkloadKind.DenseEraser, 16, 16)
        P2CommandOraclePreparationTracker.resetEraserExpectedDocumentCount()

        PreparedCommandWorkload.createLatency(spec)

        assertEquals(0, P2CommandOraclePreparationTracker.eraserExpectedDocumentCount())
        PreparedCommandWorkload.createCorrectness(spec)
        assertEquals(1, P2CommandOraclePreparationTracker.eraserExpectedDocumentCount())
    }

    @Test
    fun commandProtocolKeepsHistoricalPlanAndAddsSixWorkloadM2Plan() {
        val historical =
            P2AndroidFinalCommandProtocol.resolve(
                P2AndroidRunIdentity("flat-packed-command-256-lane-separated-v1", 1, "a".repeat(40)),
            )
        val m2 =
            P2AndroidFinalCommandProtocol.resolve(
                P2AndroidRunIdentity("m2-production-command-256-lane-separated-v2", 1, "b".repeat(40)),
            )

        assertEquals(P2CommandWorkloadCatalog.legacyKinds, historical.specs.map(P2CommandWorkloadSpec::kind))
        assertEquals("nene-pixel-p2-android-clean-command-latency-v2", historical.schema)
        assertEquals(P2CommandWorkloadCatalog.m2Kinds, m2.specs.map(P2CommandWorkloadSpec::kind))
        assertEquals(6, m2.workloadCount)
        assertEquals(1_200, m2.totalSampleCount)
        assertEquals("nene-pixel-m2-android-command-latency-v2", m2.schema)
    }
}
