package io.github.hideyukimori.nenepixel.core.pixelengine.measurement

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

internal class P2CandidatePatchContractTest {
    @Test
    fun `object inverse materializes records while packed inverse shares directional backing`() {
        val objectPair = patchPair(P2CandidateConfiguration.CurrentObjectMaterializedInverse)
        val packedPair = patchPair(P2CandidateConfiguration.FlatPackedSharedInverse)

        assertEquals(P2CandidatePatchStorageCounts(8L, 6L, 2L, 0L), objectPair.forward)
        assertEquals(objectPair.forward, objectPair.inverseAdditional)
        assertEquals(P2CandidatePatchStorageCounts.Empty, objectPair.shared)
        assertEquals(P2CandidatePatchStorageCounts(16L, 12L, 4L, 0L), objectPair.retainedUnion)

        assertEquals(P2CandidatePatchStorageCounts(24L, 0L, 0L, 3L), packedPair.forward)
        assertEquals(P2CandidatePatchStorageCounts.Empty, packedPair.inverseAdditional)
        assertEquals(packedPair.forward, packedPair.shared)
        assertEquals(packedPair.forward, packedPair.retainedUnion)
    }

    @Test
    fun `candidate apply returns closed shape revision and before mismatch results atomically`() {
        val configuration = P2CandidateConfiguration.FlatPackedSharedInverse
        val shape = P2CanvasShape(4, 4)
        val pixels = deterministicPixels(shape)
        val source = configuration.createSnapshot(shape, 0L, pixels)
        val patch = patch(configuration, source, intArrayOf(pixels.lastIndex))

        assertRepresentationMismatch(shape, pixels, patch)
        assertShapeMismatch(configuration, shape, pixels, patch)
        assertRevisionMismatch(configuration, shape, pixels, patch)
        assertBeforeValueMismatch(configuration, shape, pixels, patch)
    }

    private fun assertRepresentationMismatch(
        shape: P2CanvasShape,
        pixels: IntArray,
        patch: P2CandidatePatch,
    ) {
        val representationMismatchSnapshot =
            P2CandidateConfiguration.TiledCowT16SharedInverse.createSnapshot(shape, 0L, pixels)
        val representationMismatch =
            assertAtomicApplicationRejection<P2CandidatePatchApplicationRejection.SnapshotRepresentationMismatch>(
                representationMismatchSnapshot,
                patch,
            )
        assertEquals(P2CandidateRepresentation.FlatPackedRgba8888, representationMismatch.expected)
        assertEquals(P2CandidateRepresentation.TiledCowRgba8888T16, representationMismatch.actual)
    }

    private fun assertShapeMismatch(
        configuration: P2CandidateConfiguration,
        shape: P2CanvasShape,
        pixels: IntArray,
        patch: P2CandidatePatch,
    ) {
        val mismatchShape = P2CanvasShape(2, 8)
        val shapeMismatchSnapshot = configuration.createSnapshot(mismatchShape, 0L, pixels)
        val shapeMismatch =
            assertAtomicApplicationRejection<P2CandidatePatchApplicationRejection.ShapeMismatch>(
                shapeMismatchSnapshot,
                patch,
            )
        assertEquals(shape, shapeMismatch.expected)
        assertEquals(mismatchShape, shapeMismatch.actual)
    }

    private fun assertRevisionMismatch(
        configuration: P2CandidateConfiguration,
        shape: P2CanvasShape,
        pixels: IntArray,
        patch: P2CandidatePatch,
    ) {
        val revisionMismatchSnapshot = configuration.createSnapshot(shape, 1L, pixels)
        val revisionMismatch =
            assertAtomicApplicationRejection<P2CandidatePatchApplicationRejection.RevisionMismatch>(
                revisionMismatchSnapshot,
                patch,
            )
        assertEquals(0L, revisionMismatch.expected)
        assertEquals(1L, revisionMismatch.actual)
    }

    private fun assertBeforeValueMismatch(
        configuration: P2CandidateConfiguration,
        shape: P2CanvasShape,
        pixels: IntArray,
        patch: P2CandidatePatch,
    ) {
        val conflictedPixels = pixels.copyOf().also { values -> values[values.lastIndex] = patch.afterAt(0) }
        val conflicted = configuration.createSnapshot(shape, 0L, conflictedPixels)
        val rejected =
            assertAtomicApplicationRejection<P2CandidatePatchApplicationRejection.BeforeValueMismatch>(
                conflicted,
                patch,
            )
        assertEquals(pixels.lastIndex, rejected.position)
        assertEquals(patch.beforeAt(0), rejected.expected)
        assertEquals(patch.afterAt(0), rejected.actual)
    }

    @Test
    fun `candidate apply rejects every cross configuration pairing`() {
        val shape = P2CanvasShape(4, 4)
        val pixels = deterministicPixels(shape)
        P2CandidateConfiguration.entries.forEach { patchConfiguration ->
            val source = patchConfiguration.createSnapshot(shape, 0L, pixels)
            val patch = patch(patchConfiguration, source, intArrayOf(0))
            P2CandidateConfiguration.entries
                .filterNot { snapshotConfiguration -> snapshotConfiguration == patchConfiguration }
                .forEach { snapshotConfiguration ->
                    val snapshot = snapshotConfiguration.createSnapshot(shape, 0L, pixels)
                    assertCrossConfigurationRejection(snapshot, patch)
                }
        }
    }

    @Test
    fun `candidate patch owns shuffled input and reports revision overflow without exception`() {
        val configuration = P2CandidateConfiguration.FlatPackedSharedInverse
        val shape = P2CanvasShape(4, 4)
        val pixels = deterministicPixels(shape)
        val source = configuration.createSnapshot(shape, 0L, pixels)
        val positions = intArrayOf(15, 3, 0)
        val after =
            positions
                .map { position -> P2PackedRgba8888.unpack(pixels[position] xor ALPHA_XOR_MASK) }
                .toMutableList()
        val patch = P2CandidatePatchFactory.create(configuration, source, positions, after).requiredPatch()
        val expectedDigest = P2CandidateDigest.patch(patch)

        positions.fill(1)
        after.fill(P2PackedRgba8888.unpack(0))

        assertEquals(expectedDigest, P2CandidateDigest.patch(patch))
        assertEquals(listOf(0, 3, 15), List(patch.changeCount, patch::positionAt))

        val overflow = configuration.createSnapshot(shape, Long.MAX_VALUE, pixels)
        val overflowResult =
            P2CandidatePatchFactory.create(
                configuration,
                overflow,
                intArrayOf(0),
                listOf(P2PackedRgba8888.unpack(pixels[0] xor ALPHA_XOR_MASK)),
            )
        assertCreationRejection<P2CandidatePatchCreationRejection.RevisionOverflow>(overflowResult)
    }

    @Test
    fun `candidate patch factory closes empty size and snapshot representation rejection`() {
        val configuration = P2CandidateConfiguration.FlatPackedSharedInverse
        val shape = P2CanvasShape(4, 4)
        val pixels = deterministicPixels(shape)
        val source = configuration.createSnapshot(shape, 0L, pixels)
        val changed = P2PackedRgba8888.unpack(pixels[0] xor ALPHA_XOR_MASK)

        assertCreationRejection<P2CandidatePatchCreationRejection.EmptyPatch>(
            P2CandidatePatchFactory.create(configuration, source, intArrayOf(), emptyList()),
        )
        assertCreationRejection<P2CandidatePatchCreationRejection.InputSizeMismatch>(
            P2CandidatePatchFactory.create(configuration, source, intArrayOf(0), emptyList()),
        )
        val objectSource = P2CandidateConfiguration.CurrentObjectMaterializedInverse.createSnapshot(shape, 0L, pixels)
        assertCreationRejection<P2CandidatePatchCreationRejection.SnapshotRepresentationMismatch>(
            P2CandidatePatchFactory.create(configuration, objectSource, intArrayOf(0), listOf(changed)),
        )
    }

    @Test
    fun `candidate inverse swaps exact values and revisions without changing canonical region`() {
        P2CandidateConfiguration.entries.forEach { configuration ->
            val shape = P2CanvasShape(4, 4)
            val pixels = deterministicPixels(shape)
            val source = configuration.createSnapshot(shape, 7L, pixels)
            val forward = patch(configuration, source, intArrayOf(15, 0, 6))
            val inverse = forward.inverse()

            assertEquals(P2CandidateRevisionTransition(8L, 7L), inverse.revisions)
            assertEquals(forward.affectedRegion, inverse.affectedRegion)
            repeat(forward.changeCount) { index ->
                assertEquals(forward.positionAt(index), inverse.positionAt(index))
                assertEquals(forward.beforeAt(index), inverse.afterAt(index))
                assertEquals(forward.afterAt(index), inverse.beforeAt(index))
                assertFalse(forward.beforeAt(index) == forward.afterAt(index))
            }
        }
    }

    private fun patchPair(configuration: P2CandidateConfiguration): P2CandidatePatchPairStorage {
        val shape = P2CanvasShape(4, 4)
        val pixels = deterministicPixels(shape)
        val source = configuration.createSnapshot(shape, 0L, pixels)
        val forward = patch(configuration, source, intArrayOf(0, pixels.lastIndex))
        return forward.pairStorage(forward.inverse())
    }

    private fun patch(
        configuration: P2CandidateConfiguration,
        source: P2CandidateSnapshot,
        positions: IntArray,
    ): P2CandidatePatch {
        val after = positions.map { position -> P2PackedRgba8888.unpack(source.packedAt(position) xor ALPHA_XOR_MASK) }
        return P2CandidatePatchFactory.create(configuration, source, positions, after).requiredPatch()
    }

    private inline fun <reified T : P2CandidatePatchCreationRejection> assertCreationRejection(
        result: P2CandidatePatchCreationResult,
    ): T {
        val rejected = assertInstanceOf(P2CandidatePatchCreationResult.Rejected::class.java, result)
        return assertInstanceOf(T::class.java, rejected.rejection)
    }

    private inline fun <reified T : P2CandidatePatchApplicationRejection> assertApplicationRejection(
        result: P2CandidatePatchApplicationResult,
    ): T {
        val rejected = assertInstanceOf(P2CandidatePatchApplicationResult.Rejected::class.java, result)
        return assertInstanceOf(T::class.java, rejected.rejection)
    }

    private fun assertCrossConfigurationRejection(
        snapshot: P2CandidateSnapshot,
        patch: P2CandidatePatch,
    ) {
        assertAtomicApplicationRejection<P2CandidatePatchApplicationRejection.SnapshotRepresentationMismatch>(
            snapshot,
            patch,
        )
    }

    private inline fun <reified T : P2CandidatePatchApplicationRejection> assertAtomicApplicationRejection(
        snapshot: P2CandidateSnapshot,
        patch: P2CandidatePatch,
    ): T {
        val revision = snapshot.revision
        val digest = P2CandidateDigest.pixels(snapshot)
        val rejection = assertApplicationRejection<T>(snapshot.apply(patch))
        assertEquals(revision, snapshot.revision)
        assertEquals(digest, P2CandidateDigest.pixels(snapshot))
        return rejection
    }

    private fun deterministicPixels(shape: P2CanvasShape): IntArray =
        IntArray(shape.pixelCount.toInt()) { index ->
            (index shl RED_SHIFT) or (index * GREEN_MULTIPLIER shl GREEN_SHIFT) or OPAQUE_ALPHA
        }

    private companion object {
        const val RED_SHIFT: Int = 24
        const val GREEN_SHIFT: Int = 16
        const val GREEN_MULTIPLIER: Int = 17
        const val OPAQUE_ALPHA: Int = 0xff
        const val ALPHA_XOR_MASK: Int = 0x000000ff
    }
}
