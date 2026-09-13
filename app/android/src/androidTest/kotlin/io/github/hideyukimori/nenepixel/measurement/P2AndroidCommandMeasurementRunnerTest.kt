package io.github.hideyukimori.nenepixel.measurement

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
internal class P2AndroidCommandMeasurementRunnerTest {
    @Test
    fun latencySamplesNeverRunFullStateVerification() {
        P2CommandWorkloadCatalog
            .shapeSpecs(width = 16, height = 16, kinds = P2CommandWorkloadCatalog.candidateKinds)
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
            .shapeSpecs(width = 16, height = 16, kinds = P2CommandWorkloadCatalog.candidateKinds)
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
    fun commandProtocolFixesOrderedP4BaselineAndCandidatePopulations() {
        val baseline =
            P2AndroidFinalCommandProtocol.resolve(
                P2AndroidRunIdentity("p4-indexed-command-baseline-v1", 1, "a".repeat(40)),
            )
        val candidate =
            P2AndroidFinalCommandProtocol.resolve(
                P2AndroidRunIdentity("p4-indexed-command-candidate-v1", 1, "b".repeat(40)),
            )

        assertEquals(P2CommandWorkloadCatalog.commonKinds, baseline.specs.map(P2CommandWorkloadSpec::kind))
        assertEquals(6, baseline.workloadCount)
        assertEquals(1_200, baseline.totalSampleCount)
        assertEquals(P2CommandWorkloadCatalog.candidateKinds, candidate.specs.map(P2CommandWorkloadSpec::kind))
        assertEquals(11, candidate.workloadCount)
        assertEquals(2_200, candidate.totalSampleCount)
        assertEquals("nene-pixel-p4-indexed-command-latency-v1", baseline.schema)
        assertEquals(baseline.schema, candidate.schema)
        assertEquals(P2AndroidFinalCommandPlan.PublicationPolicy.KeepPartial, baseline.publicationPolicy)
        assertEquals(P2AndroidFinalCommandPlan.PublicationPolicy.KeepPartial, candidate.publicationPolicy)
    }

    @Test
    fun keepPartialReservationConsumesAttemptAndPreservesBoundedFailureFacts() {
        val directory = File.createTempFile("p4-command", "reservation").also { assertTrue(it.delete()) }
        assertTrue(directory.mkdir())
        val output = File(directory, "run.csv")
        val identity = P2AndroidRunIdentity("p4-indexed-command-candidate-v1", 1, "a".repeat(40))
        val plan = P2AndroidFinalCommandProtocol.resolve(identity)
        val reservation =
            P2AndroidFinalCommandOutputPublication.reserve(
                output,
                P2AndroidFinalCommandPlan.PublicationPolicy.KeepPartial,
            )
        reservation.bindIdentity(plan, identity)

        reservation.recordFailure("warmup", 11, 27, 0, emptyList(), IllegalStateException("fixture failed"))

        val preserved = output.readText()
        assertTrue(preserved.contains("metadata,run_status,reserved"))
        assertTrue(preserved.contains("metadata,measurement_build_commit,\"${identity.sourceCommit}\""))
        assertTrue(preserved.contains("metadata,run_status,invalid"))
        assertTrue(preserved.contains("metadata,completed_correctness,11"))
        assertTrue(preserved.contains("metadata,completed_warmups,27"))
        val secondAttempt =
            runCatching {
                P2AndroidFinalCommandOutputPublication.reserve(
                    output,
                    P2AndroidFinalCommandPlan.PublicationPolicy.KeepPartial,
                )
            }
        assertTrue(secondAttempt.isFailure)
        assertTrue(output.delete())
        assertTrue(directory.delete())
    }
}
