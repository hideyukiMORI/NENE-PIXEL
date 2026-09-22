package io.github.hideyukimori.nenepixel.core.projectformat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

internal class ProjectFormatHostEvidenceContractTest {
    @Test
    fun `candidate fixtures satisfy every untimed group boundary`() {
        verifyProjectFormatHostEvidenceFixtures()
    }

    @Test
    fun `sample rows round trip through the fixed schema parser`() {
        val observation = ProjectFormatHostObservation("v2_max_decode", 19, 999L)

        assertEquals(
            observation,
            ProjectFormatHostEvidenceReport.parseSampleRow(
                ProjectFormatHostEvidenceReport.sampleRow(observation),
            ),
        )
        assertThrows(IllegalArgumentException::class.java) {
            ProjectFormatHostEvidenceReport.parseSampleRow("unknown,0,1")
        }
    }

    @Test
    fun `candidate main rejects a baseline role before fixture creation`() {
        assertThrows(IllegalArgumentException::class.java) { main(arrayOf("baseline")) }
    }
}
