package io.github.hideyukimori.nenepixel.adapters.persistence

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

internal class RecoveryHostEvidenceContractTest {
    @Test
    fun `candidate fixtures satisfy every untimed group boundary`() {
        verifyRecoveryHostEvidenceFixtures()
    }

    @Test
    fun `sample rows round trip through the fixed schema parser`() {
        val observation = RecoveryHostObservation("v2_max_candidate_publish", 19, 999L)

        assertEquals(
            observation,
            RecoveryHostEvidenceReport.parseSampleRow(RecoveryHostEvidenceReport.sampleRow(observation)),
        )
        assertThrows(IllegalArgumentException::class.java) {
            RecoveryHostEvidenceReport.parseSampleRow("unknown,0,1")
        }
    }

    @Test
    fun `candidate main rejects a baseline role before fixture creation`() {
        assertThrows(IllegalArgumentException::class.java) { main(arrayOf("baseline")) }
    }
}
