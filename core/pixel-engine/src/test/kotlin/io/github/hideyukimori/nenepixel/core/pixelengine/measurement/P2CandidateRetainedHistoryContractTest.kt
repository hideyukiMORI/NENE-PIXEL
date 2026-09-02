package io.github.hideyukimori.nenepixel.core.pixelengine.measurement

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class P2CandidateRetainedHistoryContractTest {
    @Test
    fun `retained matrix contains exactly the valid pre-fixed entry and change pairs`() {
        val workloads = P2CandidateRetainedHistoryMatrix.workloads
        val expectedPairs =
            buildSet {
                add(0 to 0L)
                add(1 to PIXEL_COUNT)
                listOf(8, 16, 32, 64).forEach { historyEntries ->
                    listOf(1L, 2L, 4L, 8L).forEach { multiplier ->
                        add(historyEntries to PIXEL_COUNT * multiplier)
                    }
                }
            }

        assertEquals(18, workloads.size)
        assertEquals(
            expectedPairs,
            workloads.map { workload -> workload.historyEntries to workload.totalRetainedChanges }.toSet(),
        )
        workloads.forEach { workload ->
            assertTrue(
                P2CandidateRetainedHistoryMatrix.isValidPair(
                    workload.historyEntries,
                    workload.totalRetainedChanges,
                ),
            )
        }
        assertFalse(P2CandidateRetainedHistoryMatrix.isValidPair(0, PIXEL_COUNT))
        assertFalse(P2CandidateRetainedHistoryMatrix.isValidPair(1, 2L * PIXEL_COUNT))
        assertFalse(P2CandidateRetainedHistoryMatrix.isValidPair(8, 0L))
    }

    @Test
    fun `retained owner defensively owns entry wrappers while preserving prepared patch identities`() {
        val configuration = P2CandidateConfiguration.FlatPackedSharedInverse
        val shape = P2CanvasShape(4, 4)
        val source = configuration.createSnapshot(shape, 0L, IntArray(shape.pixelCount.toInt()) { OPAQUE_BLACK })
        val forward =
            P2CandidatePatchFactory
                .create(
                    configuration,
                    source,
                    intArrayOf(0, 1, 2, 3),
                    List(4) { P2PackedRgba8888.unpack(OPAQUE_RED) },
                ).requiredPatch()
        val current = source.apply(forward).requiredApplication().snapshot
        val prepared = mutableListOf(P2CandidateRetainedEntryReference(forward, forward.inverse()))
        val retained = P2CandidateRetainedHistory.retain(current, prepared)

        prepared.clear()

        assertEquals(1, retained.entryCount)
        assertSame(current, retained.currentSnapshot)
        assertSame(forward, retained.entryAt(0).forward)
        assertEquals(forward.pairStorage(retained.entryAt(0).inverse).forward, forward.storage)
    }

    @Test
    fun `retained fixtures preserve empty and positive storage semantics across configurations`() {
        val emptyOutcomes =
            P2CandidateConfiguration.entries.map { configuration ->
                fixture(configuration, historyEntries = 0, changeCountPerEntry = 0).executeAndVerify()
            }
        assertEquals(1, emptyOutcomes.map { outcome -> outcome.correctness }.toSet().size)
        emptyOutcomes.forEach { outcome -> assertEmptyPatchStorage(outcome.storage) }

        val positiveOutcomes =
            P2CandidateConfiguration.entries.associateWith { configuration ->
                fixture(configuration, historyEntries = 2, changeCountPerEntry = 4).executeAndVerify()
            }
        assertEquals(
            1,
            positiveOutcomes.values
                .map { outcome -> outcome.correctness }
                .toSet()
                .size,
        )
        assertObjectStorage(positiveOutcomes.getValue(P2CandidateConfiguration.CurrentObjectMaterializedInverse))
        assertPackedStorage(positiveOutcomes.getValue(P2CandidateConfiguration.FlatPackedSharedInverse))
    }

    @Test
    fun `retained report uses only schema v6 aggregate storage columns`() {
        val descriptor = descriptor(P2CandidateConfiguration.FlatPackedSharedInverse, 2, 4)
        val outcome = P2CandidateRetainedHistoryMeasurementFixture.create(descriptor).executeAndVerify()
        val samples = P2RawSamples(LongArray(10) { 100L + it }, LongArray(10) { 200L + it })
        val percentiles = P2CandidatePercentiles(P2Percentiles(104L, 109L, 109L), P2Percentiles(204L, 209L, 209L))
        val metric = P2CandidateRetainedHistoryMeasurementMetric(descriptor, samples, percentiles, outcome)

        val rows = P2CandidateRetainedHistoryMeasurementReport.metricRows(metric)
        val values = reportValues(rows.first())

        assertEquals(11, rows.size)
        assertEquals("", values.getValue("primitive_payload_bytes"))
        assertEquals("", values.getValue("forward_patch_primitive_bytes"))
        assertEquals("", values.getValue("inverse_additional_primitive_bytes"))
        assertEquals("", values.getValue("shared_patch_primitive_bytes"))
        assertEquals("", values.getValue("retained_patch_union_primitive_bytes"))
        assertEquals("64", values.getValue("retained_snapshot_primitive_bytes"))
        assertEquals("96", values.getValue("retained_forward_patch_primitive_bytes"))
        assertEquals("0", values.getValue("retained_inverse_additional_primitive_bytes"))
        assertEquals("96", values.getValue("retained_shared_patch_primitive_bytes"))
        assertEquals("96", values.getValue("retained_history_patch_union_primitive_bytes"))
        assertEquals("retained_analytical_history", values.getValue("operation_kind"))
        assertEquals("Retained", values.getValue("result_kind"))
    }

    private fun fixture(
        configuration: P2CandidateConfiguration,
        historyEntries: Int,
        changeCountPerEntry: Int,
    ): P2CandidateRetainedHistoryMeasurementFixture =
        P2CandidateRetainedHistoryMeasurementFixture.create(
            descriptor(configuration, historyEntries, changeCountPerEntry),
        )

    private fun descriptor(
        configuration: P2CandidateConfiguration,
        historyEntries: Int,
        changeCountPerEntry: Int,
    ): P2CandidateRetainedHistoryMeasurementDescriptor =
        P2CandidateRetainedHistoryMeasurementDescriptor(
            configuration,
            P2CandidateRetainedHistoryWorkload(historyEntries, changeCountPerEntry),
            P2CanvasShape(4, 4),
            workloadIndex = 0,
            executionOrder = 0,
        )

    private fun reportValues(row: String): Map<String, String> {
        val columns = csvValues(P2CandidateMeasurementReportSchema.headerRow())
        return columns.zip(csvValues(row)).toMap()
    }

    private fun csvValues(row: String): List<String> =
        row
            .removePrefix("\"")
            .removeSuffix("\"")
            .split("\",\"")
            .map { value -> value.replace("\"\"", "\"") }

    private fun assertEmptyPatchStorage(storage: P2CandidateRetainedHistoryStorageEvidence) {
        assertEquals(P2CandidatePatchStorageCounts.Empty, storage.forward)
        assertEquals(P2CandidatePatchStorageCounts.Empty, storage.inverseAdditional)
        assertEquals(P2CandidatePatchStorageCounts.Empty, storage.shared)
        assertEquals(P2CandidatePatchStorageCounts.Empty, storage.retainedUnion)
    }

    private fun assertObjectStorage(outcome: P2CandidateRetainedHistoryMeasurementOutcome) {
        val storage = outcome.storage
        assertEquals(P2CandidateStorageCounts(0L, 16L, 0L, 0L), storage.snapshot)
        assertEquals(P2CandidatePatchStorageCounts(32L, 24L, 8L, 0L), storage.forward)
        assertEquals(storage.forward, storage.inverseAdditional)
        assertEquals(P2CandidatePatchStorageCounts.Empty, storage.shared)
        assertEquals(P2CandidatePatchStorageCounts(64L, 48L, 16L, 0L), storage.retainedUnion)
    }

    private fun assertPackedStorage(outcome: P2CandidateRetainedHistoryMeasurementOutcome) {
        val storage = outcome.storage
        assertEquals(P2CandidateStorageCounts(64L, 0L, 0L, 0L), storage.snapshot)
        assertEquals(P2CandidatePatchStorageCounts(96L, 0L, 0L, 6L), storage.forward)
        assertEquals(P2CandidatePatchStorageCounts.Empty, storage.inverseAdditional)
        assertEquals(storage.forward, storage.shared)
        assertEquals(storage.forward, storage.retainedUnion)
    }

    private companion object {
        const val PIXEL_COUNT: Long = 65_536L
        const val OPAQUE_BLACK: Int = 0x000000ff
        const val OPAQUE_RED: Int = -0x00ffff01
    }
}
