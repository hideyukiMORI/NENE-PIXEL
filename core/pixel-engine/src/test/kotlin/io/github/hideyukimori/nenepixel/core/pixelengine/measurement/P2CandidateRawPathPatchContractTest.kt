package io.github.hideyukimori.nenepixel.core.pixelengine.measurement

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

internal class P2CandidateRawPathPatchContractTest {
    @Test
    fun `candidate raw path preserves one canonical patch across duplicate factors`() {
        val shape = P2CanvasShape(4, 4)
        P2CandidateConfiguration.entries.forEach { configuration ->
            val evidence =
                P2CandidateRawPathMeasurementMatrix.duplicateFactors.map { factor ->
                    verifyDuplicateFactor(configuration, shape, factor)
                }
            assertEquals(evidence.size, evidence.map { item -> item.rawInputDigest }.toSet().size)
            assertEquals(1, evidence.map { item -> item.patchDigest }.toSet().size)
            assertEquals(1, evidence.map { item -> item.inverseDigest }.toSet().size)
            assertEquals(1, evidence.map { item -> item.storage }.toSet().size)
        }
    }

    private fun verifyDuplicateFactor(
        configuration: P2CandidateConfiguration,
        shape: P2CanvasShape,
        factor: Int,
    ): DuplicateFactorEvidence {
        val blackPixels = IntArray(shape.pixelCount.toInt()) { OPAQUE_BLACK }
        val red = P2PackedRgba8888.unpack(OPAQUE_RED)
        val rawPositions = IntArray(Math.multiplyExact(blackPixels.size, factor)) { index -> index / factor }
        val source = configuration.createSnapshot(shape, 0L, blackPixels)
        val sourceDigest = P2CandidateDigest.pixels(source)
        val rawInputDigest = P2CandidateDigest.rawInput(source, rawPositions, red)
        val result = P2CandidateRawPathPatchFactory.create(configuration, source, rawPositions, red)
        val patch = assertInstanceOf(P2CandidateRawPathResult.Rasterized::class.java, result).patch
        verifyPatch(shape, patch, blackPixels.size)
        val inverse = patch.inverse()
        val applied = source.apply(patch).requiredApplication().snapshot
        val restored = applied.apply(inverse).requiredApplication().snapshot
        assertCandidatePixels(applied, IntArray(blackPixels.size) { OPAQUE_RED })
        assertEquals(source, restored)
        assertEquals(sourceDigest, P2CandidateDigest.pixels(source))
        val evidence = duplicateFactorEvidence(rawInputDigest, patch, inverse)
        rawPositions.fill(0)
        assertEquals(evidence.patchDigest, P2CandidateDigest.patch(patch))
        resetRepeatedRowMajor(rawPositions, factor)
        return evidence
    }

    private fun verifyPatch(
        shape: P2CanvasShape,
        patch: P2CandidatePatch,
        expectedCount: Int,
    ) {
        assertEquals(expectedCount, patch.changeCount)
        assertEquals((0 until expectedCount).toList(), List(patch.changeCount, patch::positionAt))
        assertEquals(P2CandidateAffectedRegion(0, 0, shape.width, shape.height), patch.affectedRegion)
        assertEquals(P2CandidateRevisionTransition(0L, 1L), patch.revisions)
        repeat(patch.changeCount) { index ->
            assertEquals(OPAQUE_BLACK, patch.beforeAt(index))
            assertEquals(OPAQUE_RED, patch.afterAt(index))
        }
    }

    private fun duplicateFactorEvidence(
        rawInputDigest: String,
        patch: P2CandidatePatch,
        inverse: P2CandidatePatch,
    ): DuplicateFactorEvidence =
        DuplicateFactorEvidence(
            rawInputDigest = rawInputDigest,
            patchDigest = P2CandidateDigest.patch(patch),
            inverseDigest = P2CandidateDigest.patch(inverse),
            storage = patch.pairStorage(inverse),
        )

    @Test
    fun `candidate raw path rasterizes changed reference clear and returns typed no changes`() {
        val shape = P2CanvasShape(4, 4)
        val rowMajor = IntArray(shape.pixelCount.toInt()) { index -> index }
        val black = P2PackedRgba8888.unpack(OPAQUE_BLACK)
        val red = P2PackedRgba8888.unpack(OPAQUE_RED)

        P2CandidateConfiguration.entries.forEach { configuration ->
            val redSource = configuration.createSnapshot(shape, 0L, IntArray(rowMajor.size) { OPAQUE_RED })
            val clear =
                assertInstanceOf(
                    P2CandidateRawPathResult.Rasterized::class.java,
                    P2CandidateRawPathPatchFactory.create(configuration, redSource, rowMajor, black),
                )
            assertEquals(rowMajor.size, clear.patch.changeCount)
            assertCandidatePixels(
                redSource.apply(clear.patch).requiredApplication().snapshot,
                IntArray(rowMajor.size) { OPAQUE_BLACK },
            )

            val redDigest = P2CandidateDigest.pixels(redSource)
            assertEquals(
                P2CandidateRawPathResult.NoChanges,
                P2CandidateRawPathPatchFactory.create(configuration, redSource, rowMajor, red),
            )
            assertEquals(redDigest, P2CandidateDigest.pixels(redSource))

            val blackSource = configuration.createSnapshot(shape, 0L, IntArray(rowMajor.size) { OPAQUE_BLACK })
            val blackDigest = P2CandidateDigest.pixels(blackSource)
            assertEquals(
                P2CandidateRawPathResult.NoChanges,
                P2CandidateRawPathPatchFactory.create(configuration, blackSource, rowMajor, black),
            )
            assertEquals(blackDigest, P2CandidateDigest.pixels(blackSource))
        }
    }

    @Test
    fun `candidate raw path closes validation and preserves no-op before revision overflow`() {
        val shape = P2CanvasShape(4, 4)
        val blackPixels = IntArray(shape.pixelCount.toInt()) { OPAQUE_BLACK }
        val black = P2PackedRgba8888.unpack(OPAQUE_BLACK)
        val red = P2PackedRgba8888.unpack(OPAQUE_RED)
        val configuration = P2CandidateConfiguration.FlatPackedSharedInverse
        val source = configuration.createSnapshot(shape, 0L, blackPixels)

        val objectSource =
            P2CandidateConfiguration.CurrentObjectMaterializedInverse.createSnapshot(shape, 0L, blackPixels)
        val mismatch =
            requiredRejection<P2CandidateRawPathRejection.SnapshotRepresentationMismatch>(
                P2CandidateRawPathPatchFactory.create(configuration, objectSource, intArrayOf(), red),
            )
        assertEquals(P2CandidateRepresentation.FlatPackedRgba8888, mismatch.expected)
        assertEquals(P2CandidateRepresentation.CurrentObjectList, mismatch.actual)

        requiredRejection<P2CandidateRawPathRejection.EmptyPath>(
            P2CandidateRawPathPatchFactory.create(configuration, source, intArrayOf(), red),
        )
        val outside =
            requiredRejection<P2CandidateRawPathRejection.PositionOutsideCanvas>(
                P2CandidateRawPathPatchFactory.create(
                    configuration,
                    source,
                    intArrayOf(0, blackPixels.size, -1),
                    red,
                ),
            )
        assertEquals(blackPixels.size, outside.position)

        val overflow = configuration.createSnapshot(shape, Long.MAX_VALUE, blackPixels)
        assertEquals(
            P2CandidateRawPathResult.NoChanges,
            P2CandidateRawPathPatchFactory.create(configuration, overflow, intArrayOf(0), black),
        )
        requiredRejection<P2CandidateRawPathRejection.RevisionOverflow>(
            P2CandidateRawPathPatchFactory.create(configuration, overflow, intArrayOf(0), red),
        )
    }

    private inline fun <reified T : P2CandidateRawPathRejection> requiredRejection(
        result: P2CandidateRawPathResult,
    ): T {
        val rejected = assertInstanceOf(P2CandidateRawPathResult.Rejected::class.java, result)
        return assertInstanceOf(T::class.java, rejected.rejection)
    }

    private fun resetRepeatedRowMajor(
        rawPositions: IntArray,
        factor: Int,
    ) {
        rawPositions.indices.forEach { index -> rawPositions[index] = index / factor }
    }

    private data class DuplicateFactorEvidence(
        val rawInputDigest: String,
        val patchDigest: String,
        val inverseDigest: String,
        val storage: P2CandidatePatchPairStorage,
    )

    private companion object {
        const val OPAQUE_BLACK: Int = 0x000000ff
        const val OPAQUE_RED: Int = -0x00ffff01
    }
}
