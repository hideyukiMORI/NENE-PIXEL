package io.github.hideyukimori.nenepixel.measurement

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class P2AndroidPackedCandidateMeasurementTest {
    @Test
    fun comparePackedCandidatesOnPhysicalProfile() {
        val environment = P2AndroidMeasurementEnvironment.fromRunnerArguments()
        val identity = P2AndroidRunIdentity.fromRunnerArguments()
        P2AndroidPackedCandidateProtocol.validate(environment, identity)
        val samples = mutableListOf<P2AndroidPackedCandidateSample>()

        P2AndroidPackedCandidateProtocol.specs.forEach { spec ->
            val expected = warmUp(spec)
            repeat(P2AndroidPackedCandidateProtocol.SAMPLES_PER_WORKLOAD) { zeroBasedIndex ->
                val execution = P2AndroidPackedCandidateWorkload.create(spec).executeMeasured()
                assertEquals(expected, execution.outcome)
                samples += P2AndroidPackedCandidateSample(spec, zeroBasedIndex + 1, execution)
            }
            assertEquals(expected, P2AndroidPackedCandidateWorkload.create(spec).executeAndVerifyFully())
        }

        val output = P2AndroidPackedCandidateReport.write(environment, identity, samples)
        assertTrue(output.isFile)
        assertTrue(output.length() > 0L)
        println("P2_ANDROID_PACKED_CANDIDATE_OUTPUT=${output.absolutePath}")
    }

    private fun warmUp(spec: P2PackedCandidateSpec): P2PackedCandidateOutcome {
        var expected: P2PackedCandidateOutcome? = null
        repeat(P2AndroidPackedCandidateProtocol.WARMUP_ITERATIONS) {
            val outcome = P2AndroidPackedCandidateWorkload.create(spec).executeAndVerifyFully()
            expected?.let { previous -> assertEquals(previous, outcome) }
            expected = outcome
        }
        return requireNotNull(expected)
    }
}
