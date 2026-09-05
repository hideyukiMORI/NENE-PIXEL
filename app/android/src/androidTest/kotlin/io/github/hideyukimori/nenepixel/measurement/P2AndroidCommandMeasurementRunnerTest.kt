package io.github.hideyukimori.nenepixel.measurement

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class P2AndroidCommandMeasurementRunnerTest {
    @Test
    fun latencySamplesNeverRunFullStateVerification() {
        P2CommandWorkloadCatalog.squareSpecs(edge = 16).forEach { spec ->
            val workload = PreparedCommandWorkload.createLatency(spec)

            P2AndroidCommandMeasurementRunner.executeMeasured(workload)

            assertFalse(workload.correctnessOraclePrepared)
            assertFalse(workload.fullStateVerificationPerformed)
        }
    }

    @Test
    fun correctnessLaneRunsFullStateVerification() {
        val spec = P2CommandWorkloadCatalog.squareSpecs(edge = 16).first()
        val workload = PreparedCommandWorkload.createCorrectness(spec)

        workload.verifyCorrectness(workload.execute())

        assertTrue(workload.correctnessOraclePrepared)
        assertTrue(workload.fullStateVerificationPerformed)
    }
}
