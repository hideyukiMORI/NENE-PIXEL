package io.github.hideyukimori.nenepixel.core.pixelengine.measurement

internal data class P2CandidatePatchLifecycleFixture(
    val initial: P2CandidateSnapshot,
    val forward: P2CandidatePatch,
    val inverse: P2CandidatePatch,
    val applied: P2CandidateSnapshot,
)

internal data class P2CandidatePatchOperationSnapshots(
    val input: P2CandidateSnapshot,
    val output: P2CandidateSnapshot,
)

internal object P2CandidatePatchVerification {
    fun verifyLifecycle(
        expectedConfiguration: P2CandidateConfiguration,
        lifecycle: P2CandidatePatchLifecycleFixture,
        semantic: P2CandidateWorkloadFixture,
    ) {
        verifyForwardPatch(expectedConfiguration, lifecycle.forward, semantic)
        check(lifecycle.initial.representation == expectedConfiguration.snapshotRepresentation) {
            "Candidate initial representation changed."
        }
        verifyInverse(lifecycle.forward, lifecycle.inverse)
        verifySnapshotStorage(lifecycle.initial)
        assertCandidatePixels(lifecycle.initial, semantic.initialPixels)
        assertCandidatePixels(lifecycle.applied, semantic.appliedPixels)
        assertCandidateUnaffectedPixels(lifecycle.initial, lifecycle.applied, lifecycle.forward)
        check(lifecycle.initial.revision == 0L) { "Candidate initial revision changed." }
        check(lifecycle.applied.revision == 1L) { "Candidate applied revision changed." }
        val restored =
            lifecycle.applied
                .apply(lifecycle.inverse)
                .requiredApplication()
                .snapshot
        assertCandidatePixels(restored, semantic.initialPixels)
        check(restored.revision == 0L) { "Candidate restored revision changed." }
        check(restored == lifecycle.initial) { "Candidate lifecycle did not restore the source snapshot." }
        verifyStorage(lifecycle.forward, lifecycle.inverse, semantic.changeCount)
        semantic.assertUnmodified()
    }

    fun verifyForwardPatch(
        expectedConfiguration: P2CandidateConfiguration,
        patch: P2CandidatePatch,
        semantic: P2CandidateWorkloadFixture,
    ) {
        check(patch.configuration == expectedConfiguration) { "Candidate patch configuration changed." }
        check(patch.shape == semantic.shape) { "Candidate patch shape changed." }
        check(patch.direction == P2CandidatePatchDirection.Forward) { "Candidate patch direction changed." }
        check(patch.revisions == P2CandidateRevisionTransition(0L, 1L)) { "Candidate patch revisions changed." }
        check(patch.changeCount == semantic.changeCount) { "Candidate patch count changed." }
        repeat(patch.changeCount) { index -> verifyChange(patch, semantic, index) }
        check(patch.affectedRegion == semantic.affectedRegion) { "Candidate patch affected region changed." }
    }

    fun verifyInverse(
        forward: P2CandidatePatch,
        inverse: P2CandidatePatch,
    ) {
        check(inverse.configuration == forward.configuration) { "Candidate inverse configuration changed." }
        check(inverse.shape == forward.shape) { "Candidate inverse shape changed." }
        check(inverse.direction == P2CandidatePatchDirection.Inverse) { "Candidate inverse direction changed." }
        check(inverse.revisions == forward.revisions.inverse()) { "Candidate inverse revisions changed." }
        check(inverse.affectedRegion == forward.affectedRegion) { "Candidate inverse region changed." }
        check(inverse.changeCount == forward.changeCount) { "Candidate inverse count changed." }
        repeat(forward.changeCount) { index -> verifyInverseChange(forward, inverse, index) }
    }

    fun audit(
        expectedConfiguration: P2CandidateConfiguration,
        lifecycle: P2CandidatePatchLifecycleFixture,
        semantic: P2CandidateWorkloadFixture,
        operation: P2CandidatePatchOperationSnapshots,
    ): P2CandidatePatchSharedAudit {
        verifyLifecycle(expectedConfiguration, lifecycle, semantic)
        val inputUnaffected = P2CandidateDigest.unaffectedPixels(operation.input, lifecycle.forward)
        val outputUnaffected = P2CandidateDigest.unaffectedPixels(operation.output, lifecycle.forward)
        check(inputUnaffected == outputUnaffected) { "Candidate operation changed an unaffected pixel." }
        return P2CandidatePatchSharedAudit(
            stateEvidence(lifecycle, semantic, operation, inputUnaffected),
            correctnessEvidence(lifecycle.forward, lifecycle.inverse),
        )
    }

    fun verifyEquivalent(
        expected: P2CandidatePatch,
        actual: P2CandidatePatch,
    ) {
        verifyExactPatch(expected, actual)
        val expectedInverse = expected.inverse()
        val actualInverse = actual.inverse()
        verifyExactPatch(expectedInverse, actualInverse)
        check(P2CandidateDigest.patch(expected) == P2CandidateDigest.patch(actual)) {
            "Candidate canonical patch differed by input order."
        }
        check(P2CandidateDigest.patch(expectedInverse) == P2CandidateDigest.patch(actualInverse)) {
            "Candidate canonical inverse differed by input order."
        }
        check(expected.pairStorage(expectedInverse) == actual.pairStorage(actualInverse)) {
            "Candidate canonical storage differed by input order."
        }
    }

    private fun verifyExactPatch(
        expected: P2CandidatePatch,
        actual: P2CandidatePatch,
    ) {
        check(actual.configuration == expected.configuration) { "Candidate canonical configuration differed." }
        check(actual.shape == expected.shape) { "Candidate canonical shape differed." }
        check(actual.revisions == expected.revisions) { "Candidate canonical revisions differed." }
        check(actual.direction == expected.direction) { "Candidate canonical direction differed." }
        check(actual.changeCount == expected.changeCount) { "Candidate canonical count differed." }
        check(actual.affectedRegion == expected.affectedRegion) { "Candidate canonical region differed." }
        check(actual.storage == expected.storage) { "Candidate canonical storage differed." }
        repeat(expected.changeCount) { index ->
            check(actual.positionAt(index) == expected.positionAt(index)) {
                "Candidate canonical position differed at $index."
            }
            check(actual.beforeAt(index) == expected.beforeAt(index)) {
                "Candidate canonical before value differed at $index."
            }
            check(actual.afterAt(index) == expected.afterAt(index)) {
                "Candidate canonical after value differed at $index."
            }
        }
    }

    private fun stateEvidence(
        lifecycle: P2CandidatePatchLifecycleFixture,
        semantic: P2CandidateWorkloadFixture,
        operation: P2CandidatePatchOperationSnapshots,
        unaffectedDigest: String,
    ): P2CandidatePatchStateEvidence =
        P2CandidatePatchStateEvidence(
            lifecycle = lifecycleEvidence(lifecycle),
            operation =
                P2CandidatePatchOperationEvidence(
                    operation.input.revision,
                    operation.output.revision,
                    P2CandidateDigest.pixels(operation.input),
                    P2CandidateDigest.pixels(operation.output),
                ),
            unaffected =
                P2CandidatePatchUnaffectedEvidence(
                    semantic.unaffectedPixelCount,
                    unaffectedDigest,
                    unaffectedDigest,
                ),
            affectedRegion = semantic.affectedRegion,
        )

    private fun lifecycleEvidence(lifecycle: P2CandidatePatchLifecycleFixture): P2CandidatePatchLifecycleEvidence =
        P2CandidatePatchLifecycleEvidence(
            revisions = P2CandidatePatchRevisionEvidence(0L, 1L, 0L),
            digests =
                P2CandidatePatchLifecycleDigests(
                    P2CandidateDigest.pixels(lifecycle.initial),
                    P2CandidateDigest.pixels(lifecycle.applied),
                    P2CandidateDigest.pixels(lifecycle.initial),
                ),
        )

    private fun correctnessEvidence(
        forward: P2CandidatePatch,
        inverse: P2CandidatePatch,
    ): P2CandidatePatchCorrectness =
        P2CandidatePatchCorrectness(
            P2CandidateDigest.canonicalOrder(forward),
            P2CandidateDigest.patch(forward),
            P2CandidateDigest.patch(inverse),
            "pass",
        )

    private fun verifyChange(
        patch: P2CandidatePatch,
        semantic: P2CandidateWorkloadFixture,
        index: Int,
    ) {
        val position = semantic.canonicalPositions[index]
        check(patch.positionAt(index) == position) { "Candidate patch ordering changed at $index." }
        check(patch.beforeAt(index) == semantic.initialPixels[position]) {
            "Candidate patch before value changed."
        }
        check(patch.afterAt(index) == semantic.afterPacked(position)) { "Candidate patch after value changed." }
    }

    private fun verifyInverseChange(
        forward: P2CandidatePatch,
        inverse: P2CandidatePatch,
        index: Int,
    ) {
        check(inverse.positionAt(index) == forward.positionAt(index)) { "Candidate inverse ordering changed." }
        check(inverse.beforeAt(index) == forward.afterAt(index)) { "Candidate inverse before value changed." }
        check(inverse.afterAt(index) == forward.beforeAt(index)) { "Candidate inverse after value changed." }
    }

    private fun verifyStorage(
        forward: P2CandidatePatch,
        inverse: P2CandidatePatch,
        changeCount: Int,
    ) {
        val pair = forward.pairStorage(inverse)
        val expectedForward = expectedPatchStorage(forward.configuration, changeCount)
        check(pair.forward == expectedForward) { "Candidate forward storage changed." }
        when (forward.configuration.patchLayout.inversePolicy) {
            P2CandidateInversePolicy.MaterializedRecords -> {
                check(pair.inverseAdditional == expectedForward) { "Materialized inverse storage changed." }
                check(pair.shared == P2CandidatePatchStorageCounts.Empty) { "Materialized inverse became shared." }
                check(pair.retainedUnion == expectedForward + expectedForward) { "Materialized union changed." }
            }

            P2CandidateInversePolicy.SharedDirectionalView -> {
                check(pair.inverseAdditional == P2CandidatePatchStorageCounts.Empty) {
                    "Shared inverse materialized."
                }
                check(pair.shared == expectedForward) { "Shared inverse storage changed." }
                check(pair.retainedUnion == expectedForward) { "Shared union double-counted backing." }
            }
        }
    }

    private fun expectedPatchStorage(
        configuration: P2CandidateConfiguration,
        changeCount: Int,
    ): P2CandidatePatchStorageCounts =
        when (configuration.patchLayout) {
            P2CandidatePatchLayout.ObjectRecordsMaterializedInverse -> {
                P2CandidatePatchStorageCounts(
                    primitivePayloadBytes = changeCount.toLong() * Int.SIZE_BYTES,
                    referenceSlots = changeCount.toLong() * OBJECT_REFERENCE_SLOTS_PER_CHANGE,
                    objectRecords = changeCount.toLong(),
                    primitiveBackingArrays = 0L,
                )
            }

            P2CandidatePatchLayout.PackedTripletsSharedDirectionalInverse -> {
                P2CandidatePatchStorageCounts(
                    primitivePayloadBytes = changeCount.toLong() * PACKED_FIELD_COUNT * Int.SIZE_BYTES,
                    referenceSlots = 0L,
                    objectRecords = 0L,
                    primitiveBackingArrays = PACKED_FIELD_COUNT,
                )
            }
        }

    private fun verifySnapshotStorage(snapshot: P2CandidateSnapshot) {
        val expected = expectedSnapshotStorage(snapshot.representation, snapshot.shape)
        check(snapshot.storage == expected) { "Candidate snapshot storage changed." }
    }

    private fun expectedSnapshotStorage(
        representation: P2CandidateRepresentation,
        shape: P2CanvasShape,
    ): P2CandidateStorageCounts =
        when (representation) {
            P2CandidateRepresentation.CurrentObjectList -> {
                P2CandidateStorageCounts(0L, shape.pixelCount, 0L, 0L)
            }

            P2CandidateRepresentation.FlatPackedRgba8888 -> {
                P2CandidateStorageCounts(shape.pixelCount * Int.SIZE_BYTES, 0L, 0L, 0L)
            }

            P2CandidateRepresentation.TiledCowRgba8888T16 -> {
                tiledStorage(shape, 16)
            }

            P2CandidateRepresentation.TiledCowRgba8888T32 -> {
                tiledStorage(shape, 32)
            }

            P2CandidateRepresentation.TiledCowRgba8888T64 -> {
                tiledStorage(shape, 64)
            }

            P2CandidateRepresentation.PaletteValueU8 -> {
                error("Palette is not a native patch configuration.")
            }
        }

    private fun tiledStorage(
        shape: P2CanvasShape,
        tileEdge: Int,
    ): P2CandidateStorageCounts {
        val columns = (shape.width + tileEdge - 1) / tileEdge
        val rows = (shape.height + tileEdge - 1) / tileEdge
        val tileCount = columns.toLong() * rows
        val primitiveBytes = tileCount * tileEdge * tileEdge * Int.SIZE_BYTES
        return P2CandidateStorageCounts(primitiveBytes, tileCount, 0L, 0L)
    }

    private const val OBJECT_REFERENCE_SLOTS_PER_CHANGE: Long = 3L
    private const val PACKED_FIELD_COUNT: Long = 3L
}
