package io.github.hideyukimori.nenepixel.core.application.persistence

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

internal class LegacyImportHostEvidenceContractTest {
    @Test
    fun `candidate fixtures satisfy every untimed group boundary`() {
        verifyLegacyImportHostEvidenceFixtures()
    }

    @Test
    fun `sample rows round trip through the fixed schema parser`() {
        val observation = LegacyImportHostObservation("reduce_65536_to_256", 19, 999L)

        assertEquals(
            observation,
            LegacyImportHostEvidenceReport.parseSampleRow(LegacyImportHostEvidenceReport.sampleRow(observation)),
        )
        assertThrows(IllegalArgumentException::class.java) {
            LegacyImportHostEvidenceReport.parseSampleRow("unknown,0,1")
        }
    }

    @Test
    fun `candidate main rejects a baseline role before fixture creation`() {
        assertThrows(IllegalArgumentException::class.java) { main(arrayOf("baseline")) }
    }
}
