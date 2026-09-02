package io.github.hideyukimori.nenepixel.core.pixelengine.measurement

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class P2CandidateSparsePatchContractTest {
    @Test
    fun `native patch matrix preserves dense anchor and adds every sparse rectangular pair once`() {
        val descriptors = P2CandidatePatchMeasurement.descriptors()
        P2CandidatePatchMeasurementMatrix.validate(descriptors)

        val dense = descriptors.filter { descriptor -> descriptor.workload == DENSE_ANCHOR }
        val sparse = descriptors.filterNot { descriptor -> descriptor.workload == DENSE_ANCHOR }
        assertEquals(30, dense.size)
        assertEquals(P2CandidatePatchOperationKind.entries.toSet(), dense.map { it.operation }.toSet())
        assertEquals(P2CandidatePatchMeasurementMatrix.SPARSE_METRIC_COUNT, sparse.size)
        assertEquals(P2CandidatePatchMeasurementMatrix.sparseCanvases.toSet(), sparse.map { it.canvas }.toSet())
        assertEquals(
            P2CandidateNativePatchWorkloadKind.SparseRectangular.toSet(),
            sparse.map { descriptor -> descriptor.workload }.toSet(),
        )
        assertEquals(
            P2CandidatePatchMeasurementMatrix.sparseOperations.toSet(),
            sparse.map { descriptor -> descriptor.operation }.toSet(),
        )
        assertFalse(
            sparse.any { descriptor -> descriptor.operation == P2CandidatePatchOperationKind.ApplyForward },
        )
        assertEquals(P2CandidatePatchMeasurementMatrix.METRIC_COUNT, descriptors.size)
        assertEquals(
            1_675,
            490 +
                P2CandidatePatchMeasurementMatrix.SPARSE_METRIC_COUNT +
                P2CandidateRawPathMeasurementMatrix.ADDED_METRIC_COUNT,
        )
        assertEquals(
            16_750,
            4_900 +
                P2CandidatePatchMeasurementMatrix.SPARSE_RAW_SAMPLE_COUNT +
                P2CandidateRawPathMeasurementMatrix.ADDED_RAW_SAMPLE_COUNT,
        )
    }

    @Test
    fun `native patch matrix rejects missing and duplicate pairs`() {
        val descriptors = P2CandidatePatchMeasurement.descriptors()

        assertThrows(IllegalStateException::class.java) {
            P2CandidatePatchMeasurementMatrix.validate(descriptors.dropLast(1))
        }
        assertThrows(IllegalStateException::class.java) {
            P2CandidatePatchMeasurementMatrix.validate(descriptors.dropLast(1) + descriptors.first())
        }
    }

    @Test
    fun `shared workload audit proves canonical forward evidence across sparse shapes and configurations`() {
        P2CandidatePatchMeasurementMatrix.sparseCanvases.forEach { canvas ->
            P2CandidateNativePatchWorkloadKind.SparseRectangular.forEach { workload ->
                verifyWorkload(canvas, workload)
            }
        }
    }

    @Test
    fun `shared verification rejects configuration and representation mismatches`() {
        val actualConfiguration = P2CandidateConfiguration.FlatPackedSharedInverse
        val expectedConfiguration = P2CandidateConfiguration.CurrentObjectMaterializedInverse
        val semantic =
            P2CandidateWorkloadFixture.create(
                P2CanvasShape(64, 64),
                P2CandidatePathKind.OnePixel,
            )
        val initial = actualConfiguration.createSnapshot(semantic.shape, 0L, semantic.initialPixels)
        val patch = createPatch(actualConfiguration, initial, semantic, semantic.reverseCanonicalPositions)
        val inverse = patch.inverse()
        val applied = initial.apply(patch).requiredApplication().snapshot
        val lifecycle = P2CandidatePatchLifecycleFixture(initial, patch, inverse, applied)

        assertConfigurationRejected(expectedConfiguration, semantic, lifecycle)
        assertInitialRepresentationRejected(expectedConfiguration, actualConfiguration, semantic, lifecycle)
        assertEquivalenceConfigurationRejected(expectedConfiguration, semantic, patch)
    }

    private fun assertConfigurationRejected(
        expected: P2CandidateConfiguration,
        semantic: P2CandidateWorkloadFixture,
        lifecycle: P2CandidatePatchLifecycleFixture,
    ) {
        val forwardFailure =
            assertThrows(IllegalStateException::class.java) {
                P2CandidatePatchVerification.verifyForwardPatch(expected, lifecycle.forward, semantic)
            }
        assertEquals("Candidate patch configuration changed.", forwardFailure.message)
        val auditFailure =
            assertThrows(IllegalStateException::class.java) {
                P2CandidatePatchVerification.audit(
                    expected,
                    lifecycle,
                    semantic,
                    P2CandidatePatchOperationSnapshots(lifecycle.initial, lifecycle.applied),
                )
            }
        assertEquals("Candidate patch configuration changed.", auditFailure.message)
    }

    private fun assertInitialRepresentationRejected(
        wrongConfiguration: P2CandidateConfiguration,
        actualConfiguration: P2CandidateConfiguration,
        semantic: P2CandidateWorkloadFixture,
        lifecycle: P2CandidatePatchLifecycleFixture,
    ) {
        val wrongInitial =
            wrongConfiguration.createSnapshot(semantic.shape, 0L, semantic.initialPixels)
        val representationFailure =
            assertThrows(IllegalStateException::class.java) {
                P2CandidatePatchVerification.verifyLifecycle(
                    actualConfiguration,
                    lifecycle.copy(initial = wrongInitial),
                    semantic,
                )
            }
        assertEquals("Candidate initial representation changed.", representationFailure.message)
    }

    private fun assertEquivalenceConfigurationRejected(
        configuration: P2CandidateConfiguration,
        semantic: P2CandidateWorkloadFixture,
        patch: P2CandidatePatch,
    ) {
        val initial = configuration.createSnapshot(semantic.shape, 0L, semantic.initialPixels)
        val expectedPatch =
            createPatch(
                configuration,
                initial,
                semantic,
                semantic.reverseCanonicalPositions,
            )
        val equivalenceFailure =
            assertThrows(IllegalStateException::class.java) {
                P2CandidatePatchVerification.verifyEquivalent(patch, expectedPatch)
            }
        assertEquals("Candidate canonical configuration differed.", equivalenceFailure.message)
    }

    @Test
    fun `schema v7 tags only standalone native patch rows`() {
        val descriptors = P2CandidatePatchMeasurement.descriptors()
        val dense = descriptors.first { descriptor -> descriptor.workload == DENSE_ANCHOR }
        val sparse =
            descriptors.first { descriptor ->
                descriptor.workload == P2CandidateNativePatchWorkloadKind.OnePixel
            }
        val generic =
            P2CandidateMeasurementDescriptor(
                P2CandidateConfiguration.FlatPackedSharedInverse,
                P2CandidateOperationKind.ApplyOne,
                P2CanvasShape(64, 64),
                "prepared patch forward apply",
            )

        assertEquals("dense_full_canvas_anchor", patchReportValues(dense).getValue(WORKLOAD_COLUMN))
        assertEquals("one_pixel", patchReportValues(sparse).getValue(WORKLOAD_COLUMN))
        val genericValues = genericReportValues(generic)
        assertEquals("", genericValues.getValue(WORKLOAD_COLUMN))
        assertEquals("1", genericValues.getValue("change_count"))
        assertEquals("4095", genericValues.getValue("unaffected_pixel_count"))
        assertTrue(genericValues.getValue("canonical_order_digest_sha256").isSha256())
        assertTrue(genericValues.getValue("forward_patch_digest_sha256").isSha256())
        assertTrue(genericValues.getValue("inverse_patch_digest_sha256").isSha256())
    }

    private fun verifyWorkload(
        canvas: P2CanvasShape,
        workload: P2CandidateNativePatchWorkloadKind,
    ) {
        val audits =
            P2CandidateConfiguration.entries.map { configuration ->
                audit(configuration, P2CandidateWorkloadFixture.create(canvas, workload.pathKind))
            }
        assertEquals(1, audits.toSet().size)
    }

    private fun audit(
        configuration: P2CandidateConfiguration,
        semantic: P2CandidateWorkloadFixture,
    ): P2CandidatePatchSharedAudit {
        val initial = configuration.createSnapshot(semantic.shape, 0L, semantic.initialPixels)
        val pathPatch = createPatch(configuration, initial, semantic, semantic.pathPositions)
        val reversePatch = createPatch(configuration, initial, semantic, semantic.reverseCanonicalPositions)
        P2CandidatePatchVerification.verifyEquivalent(pathPatch, reversePatch)
        val inverse = reversePatch.inverse()
        val applied = initial.apply(reversePatch).requiredApplication().snapshot
        verifyConflict(configuration, semantic, reversePatch)
        return P2CandidatePatchVerification.audit(
            configuration,
            P2CandidatePatchLifecycleFixture(initial, reversePatch, inverse, applied),
            semantic,
            P2CandidatePatchOperationSnapshots(initial, applied),
        )
    }

    private fun createPatch(
        configuration: P2CandidateConfiguration,
        initial: P2CandidateSnapshot,
        semantic: P2CandidateWorkloadFixture,
        positions: IntArray,
    ): P2CandidatePatch =
        P2CandidatePatchFactory
            .create(configuration, initial, positions, semantic.afterColors(positions))
            .requiredPatch()

    private fun verifyConflict(
        configuration: P2CandidateConfiguration,
        semantic: P2CandidateWorkloadFixture,
        patch: P2CandidatePatch,
    ) {
        val conflicted = configuration.createSnapshot(semantic.shape, 0L, semantic.conflictedPixels)
        val digest = P2CandidateDigest.pixels(conflicted)
        val rejected =
            assertInstanceOf(
                P2CandidatePatchApplicationResult.Rejected::class.java,
                conflicted.apply(patch),
            )
        val mismatch =
            assertInstanceOf(
                P2CandidatePatchApplicationRejection.BeforeValueMismatch::class.java,
                rejected.rejection,
            )
        assertEquals(semantic.conflictPosition, mismatch.position)
        assertEquals(0L, conflicted.revision)
        assertEquals(digest, P2CandidateDigest.pixels(conflicted))
    }

    private fun patchReportValues(descriptor: P2CandidatePatchMeasurementDescriptor): Map<String, String> {
        val outcome = P2CandidatePatchMeasurementFixture.create(descriptor).executeAndVerify()
        val metric =
            P2CandidatePatchMeasurementMetric(
                descriptor,
                samples(),
                percentiles(),
                outcome,
            )
        return reportValues(P2CandidateMeasurementReport.patchMetricRows(metric).first())
    }

    private fun genericReportValues(descriptor: P2CandidateMeasurementDescriptor): Map<String, String> {
        val outcome = P2CandidateMeasurementFixture.create(descriptor).executeAndVerify()
        val metric = P2CandidateMeasurementMetric(descriptor, samples(), percentiles(), outcome)
        return reportValues(P2CandidateMeasurementReport.candidateMetricRows(metric).first())
    }

    private fun samples(): P2RawSamples =
        P2RawSamples(
            LongArray(10) { index -> index.toLong() },
            LongArray(10) { index -> index.toLong() },
        )

    private fun percentiles(): P2CandidatePercentiles =
        P2CandidatePercentiles(P2Percentiles(4L, 9L, 9L), P2Percentiles(4L, 9L, 9L))

    private fun reportValues(row: String): Map<String, String> =
        csvValues(P2CandidateMeasurementReportSchema.headerRow()).zip(csvValues(row)).toMap()

    private fun csvValues(row: String): List<String> =
        row
            .removePrefix("\"")
            .removeSuffix("\"")
            .split("\",\"")
            .map { value -> value.replace("\"\"", "\"") }

    private fun String.isSha256(): Boolean =
        length == 64 &&
            all { character ->
                character in '0'..'9' || character in 'A'..'F'
            }

    private companion object {
        val DENSE_ANCHOR: P2CandidateNativePatchWorkloadKind =
            P2CandidateNativePatchWorkloadKind.DenseFullCanvasAnchor
        const val WORKLOAD_COLUMN: String = "native_patch_workload_kind"
    }
}
