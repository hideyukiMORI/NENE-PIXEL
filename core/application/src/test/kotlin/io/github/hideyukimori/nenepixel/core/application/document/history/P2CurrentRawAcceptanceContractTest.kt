package io.github.hideyukimori.nenepixel.core.application.document.history

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

internal class P2CurrentRawAcceptanceContractTest {
    @Test
    fun `fixed matrix has every shape and factor exactly once in protocol order`() {
        val workloads = P2CurrentRawAcceptanceMatrix.workloads

        assertEquals(P2CurrentRawAcceptanceMatrix.METRIC_COUNT, workloads.size)
        assertEquals(7, workloads.map(P2RawAcceptanceWorkload::shape).distinct().size)
        assertEquals(listOf(1, 2, 4, 8), workloads.take(4).map(P2RawAcceptanceWorkload::factor))
        assertEquals(
            listOf("row_major", "paired_row_major", "quadrupled_row_major", "octupled_row_major"),
            workloads.take(4).map(P2RawAcceptanceWorkload::inputOrder),
        )
        assertEquals(524_288, workloads.maxOf(P2RawAcceptanceWorkload::pathPositions))
        assertEquals(
            P2CurrentRawAcceptanceMatrix.METRIC_COUNT * 10,
            P2CurrentRawAcceptanceMatrix.RAW_SAMPLE_COUNT,
        )
        workloads.forEach { workload ->
            assertEquals(workload.factor * workload.pixelCount, workload.pathPositions)
            assertEquals(workload.pathPositions - workload.pixelCount, workload.duplicatePositions)
        }
        P2CurrentRawAcceptanceMatrix.validateDescriptors(workloads.map(P2CurrentRawAcceptanceMatrix::descriptor))
    }

    @Test
    fun `fixed matrix rejects a missing or duplicate shape factor pair`() {
        val descriptors =
            P2CurrentRawAcceptanceMatrix.workloads.map(P2CurrentRawAcceptanceMatrix::descriptor)

        assertThrows(IllegalArgumentException::class.java) {
            P2CurrentRawAcceptanceMatrix.validateDescriptors(descriptors.dropLast(1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            P2CurrentRawAcceptanceMatrix.validateDescriptors(descriptors.dropLast(1) + descriptors.first())
        }
    }

    @Test
    fun `factor fixtures keep distinct raw identities and one canonical lifecycle per shape`() {
        P2CurrentRawAcceptanceMeasurement.verifyFixedFixtureContract()
    }
}
