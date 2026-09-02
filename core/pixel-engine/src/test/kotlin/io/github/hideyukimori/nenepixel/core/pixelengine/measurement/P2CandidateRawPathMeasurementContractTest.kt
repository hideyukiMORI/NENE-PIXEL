package io.github.hideyukimori.nenepixel.core.pixelengine.measurement

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

internal class P2CandidateRawPathMeasurementContractTest {
    @Test
    fun `raw path matrix adds each shape factor configuration once and preserves legacy rows`() {
        val descriptors = P2CandidateRawPathMeasurement.descriptors()
        P2CandidateRawPathMeasurementMatrix.validate(descriptors)
        val duplicate = descriptors.filter { descriptor -> descriptor.operation == DUPLICATE_CHANGED }
        val legacy = descriptors.filterNot { descriptor -> descriptor.operation == DUPLICATE_CHANGED }

        assertEquals(P2CandidateRawPathMeasurementMatrix.DUPLICATE_METRIC_COUNT, duplicate.size)
        assertEquals(P2CandidateCanvasMatrix.shapes.toSet(), duplicate.map { it.canvas }.toSet())
        assertEquals(P2CandidateRawPathMeasurementMatrix.duplicateFactors, duplicate.map { it.repeatFactor }.distinct())
        duplicate.forEach(::assertDuplicateDescriptor)
        assertEquals(5, duplicate.count(::isExistingFactorTwoAnchor))

        assertEquals(15, legacy.size)
        assertEquals(P2CandidateRawPathOperationKind.entries.drop(1).toSet(), legacy.map { it.operation }.toSet())
        assertEquals(setOf(P2CandidateCanvasMatrix.denseAnchor), legacy.map { it.canvas }.toSet())
        legacy.forEach(::assertLegacyDescriptor)
        assertEquals(P2CandidateRawPathMeasurementMatrix.METRIC_COUNT, descriptors.size)
    }

    @Test
    fun `raw path matrix rejects missing and duplicate pairs`() {
        val descriptors = P2CandidateRawPathMeasurement.descriptors()

        assertThrows(IllegalStateException::class.java) {
            P2CandidateRawPathMeasurementMatrix.validate(descriptors.dropLast(1))
        }
        assertThrows(IllegalStateException::class.java) {
            P2CandidateRawPathMeasurementMatrix.validate(descriptors.dropLast(1) + descriptors.first())
        }
    }

    @Test
    fun `shared canvas matrix has one square and rectangular source of truth`() {
        assertEquals(7, P2CandidateCanvasMatrix.shapes.size)
        assertEquals(P2CandidateCanvasMatrix.shapes.dropLast(1), P2CandidatePatchMeasurementMatrix.sparseCanvases)
        assertEquals(P2CandidateCanvasMatrix.shapes.last(), P2CandidateCanvasMatrix.denseAnchor)
        assertFalse(P2CandidatePatchMeasurementMatrix.sparseCanvases.contains(P2CandidateCanvasMatrix.denseAnchor))
    }

    private fun assertDuplicateDescriptor(descriptor: P2CandidateRawPathMeasurementDescriptor) {
        val pixelCount = descriptor.canvas.pixelCount.toInt()
        assertEquals(Math.multiplyExact(pixelCount, descriptor.repeatFactor), descriptor.pathPositions)
        assertEquals(pixelCount, descriptor.uniquePathPositions)
        assertEquals(descriptor.pathPositions - pixelCount, descriptor.duplicatePathPositions)
        assertEquals(0, descriptor.unchangedUniquePositions)
        assertEquals(pixelCount, descriptor.changeCount)
        assertEquals(descriptor.repeatFactor.inputOrder(), descriptor.protocol.inputOrder)
    }

    private fun assertLegacyDescriptor(descriptor: P2CandidateRawPathMeasurementDescriptor) {
        val pixelCount = descriptor.canvas.pixelCount.toInt()
        assertEquals(1, descriptor.repeatFactor)
        assertEquals(pixelCount, descriptor.pathPositions)
        assertEquals(pixelCount, descriptor.uniquePathPositions)
        assertEquals(0, descriptor.duplicatePathPositions)
        assertEquals("row_major", descriptor.protocol.inputOrder)
    }

    private fun isExistingFactorTwoAnchor(descriptor: P2CandidateRawPathMeasurementDescriptor): Boolean =
        descriptor.canvas == P2CandidateCanvasMatrix.denseAnchor && descriptor.repeatFactor == 2

    private fun Int.inputOrder(): String =
        when (this) {
            1 -> "row_major"
            2 -> "paired_row_major"
            4 -> "quadrupled_row_major"
            8 -> "octupled_row_major"
            else -> error("Unexpected raw-path factor: $this")
        }

    private companion object {
        val DUPLICATE_CHANGED: P2CandidateRawPathOperationKind = P2CandidateRawPathOperationKind.DuplicateChanged
    }
}
