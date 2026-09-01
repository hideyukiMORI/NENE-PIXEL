package io.github.hideyukimori.nenepixel.core.pixelengine.measurement

import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor

internal sealed class P2CandidatePatch {
    abstract val configuration: P2CandidateConfiguration
    abstract val shape: P2CanvasShape
    abstract val revisions: P2CandidateRevisionTransition
    abstract val affectedRegion: P2CandidateAffectedRegion
    abstract val direction: P2CandidatePatchDirection
    abstract val changeCount: Int
    abstract val storage: P2CandidatePatchStorageCounts

    abstract fun positionAt(index: Int): Int

    abstract fun beforeAt(index: Int): Int

    abstract fun afterAt(index: Int): Int

    abstract fun beforeMatches(
        snapshot: P2CandidateSnapshot,
        index: Int,
    ): Boolean

    open fun colorAfterAt(index: Int): PixelColor = P2PackedRgba8888.unpack(afterAt(index))

    abstract fun inverse(): P2CandidatePatch

    protected abstract fun sharesBackingWith(other: P2CandidatePatch): Boolean

    fun pairStorage(inverse: P2CandidatePatch): P2CandidatePatchPairStorage {
        check(inverse.configuration == configuration) { "Candidate inverse configuration changed." }
        return when (configuration.patchLayout.inversePolicy) {
            P2CandidateInversePolicy.MaterializedRecords -> materializedPairStorage(inverse)
            P2CandidateInversePolicy.SharedDirectionalView -> sharedPairStorage(inverse)
        }
    }

    fun applicationRejection(snapshot: P2CandidateSnapshot): P2CandidatePatchApplicationRejection? =
        when {
            snapshot.representation != configuration.snapshotRepresentation -> {
                P2CandidatePatchApplicationRejection.SnapshotRepresentationMismatch(
                    configuration.snapshotRepresentation,
                    snapshot.representation,
                )
            }

            snapshot.shape != shape -> {
                P2CandidatePatchApplicationRejection.ShapeMismatch(shape, snapshot.shape)
            }

            snapshot.revision != revisions.before -> {
                P2CandidatePatchApplicationRejection.RevisionMismatch(revisions.before, snapshot.revision)
            }

            else -> {
                firstValueMismatch(snapshot)
            }
        }

    private fun firstValueMismatch(snapshot: P2CandidateSnapshot): P2CandidatePatchApplicationRejection? {
        repeat(changeCount) { index ->
            val position = positionAt(index)
            if (!beforeMatches(snapshot, index)) {
                val actual = snapshot.packedAt(position)
                return P2CandidatePatchApplicationRejection.BeforeValueMismatch(
                    position = position,
                    expected = beforeAt(index),
                    actual = actual,
                )
            }
        }
        return null
    }

    private fun materializedPairStorage(inverse: P2CandidatePatch): P2CandidatePatchPairStorage {
        check(!sharesBackingWith(inverse)) { "Materialized candidate inverse unexpectedly shared storage." }
        return P2CandidatePatchPairStorage(
            forward = storage,
            inverseAdditional = inverse.storage,
            shared = P2CandidatePatchStorageCounts.Empty,
            retainedUnion = storage + inverse.storage,
        )
    }

    private fun sharedPairStorage(inverse: P2CandidatePatch): P2CandidatePatchPairStorage {
        check(sharesBackingWith(inverse)) { "Directional candidate inverse did not share storage." }
        return P2CandidatePatchPairStorage(
            forward = storage,
            inverseAdditional = P2CandidatePatchStorageCounts.Empty,
            shared = storage,
            retainedUnion = storage,
        )
    }
}

internal object P2CandidatePatchFactory {
    fun create(
        configuration: P2CandidateConfiguration,
        snapshot: P2CandidateSnapshot,
        positions: IntArray,
        after: List<PixelColor>,
    ): P2CandidatePatchCreationResult {
        val rejection = validateInput(configuration, snapshot, positions, after)
        return if (rejection != null) {
            P2CandidatePatchCreationResult.Rejected(rejection)
        } else {
            createValidatedPatch(configuration, snapshot, positions, after)
        }
    }

    private fun createValidatedPatch(
        configuration: P2CandidateConfiguration,
        snapshot: P2CandidateSnapshot,
        positions: IntArray,
        after: List<PixelColor>,
    ): P2CandidatePatchCreationResult {
        val canonical = canonicalInput(positions, after)
        val canonicalRejection = validateCanonical(snapshot, canonical)
        return if (canonicalRejection != null) {
            P2CandidatePatchCreationResult.Rejected(canonicalRejection)
        } else {
            createPatch(configuration, snapshot, canonical)
        }
    }

    private fun validateInput(
        configuration: P2CandidateConfiguration,
        snapshot: P2CandidateSnapshot,
        positions: IntArray,
        after: List<PixelColor>,
    ): P2CandidatePatchCreationRejection? =
        when {
            snapshot.representation != configuration.snapshotRepresentation -> {
                P2CandidatePatchCreationRejection.SnapshotRepresentationMismatch(
                    expected = configuration.snapshotRepresentation,
                    actual = snapshot.representation,
                )
            }

            positions.size != after.size -> {
                P2CandidatePatchCreationRejection.InputSizeMismatch(positions.size, after.size)
            }

            positions.isEmpty() -> {
                P2CandidatePatchCreationRejection.EmptyPatch
            }

            snapshot.revision == Long.MAX_VALUE -> {
                P2CandidatePatchCreationRejection.RevisionOverflow
            }

            else -> {
                null
            }
        }

    private fun canonicalInput(
        positions: IntArray,
        after: List<PixelColor>,
    ): P2CanonicalCandidatePatchInput {
        val orderedIndexes = positions.indices.sortedBy(positions::get)
        val canonicalPositions = IntArray(orderedIndexes.size) { index -> positions[orderedIndexes[index]] }
        val canonicalAfter = orderedIndexes.map(after::get)
        return P2CanonicalCandidatePatchInput(canonicalPositions, canonicalAfter)
    }

    private fun validateCanonical(
        snapshot: P2CandidateSnapshot,
        input: P2CanonicalCandidatePatchInput,
    ): P2CandidatePatchCreationRejection? {
        val outside = input.positions.firstOrNull { position -> position !in 0 until snapshot.shape.pixelCount.toInt() }
        val duplicateIndex =
            (1 until input.positions.size).firstOrNull { index ->
                input.positions[index - 1] == input.positions[index]
            }
        val unchangedIndex =
            input.positions.indices.firstOrNull { index ->
                val position = input.positions[index]
                position in 0 until snapshot.shape.pixelCount.toInt() &&
                    snapshot.matchesColor(position, input.after[index])
            }
        return when {
            outside != null -> {
                P2CandidatePatchCreationRejection.PositionOutsideCanvas(outside)
            }

            duplicateIndex != null -> {
                P2CandidatePatchCreationRejection.DuplicatePosition(input.positions[duplicateIndex])
            }

            unchangedIndex != null -> {
                P2CandidatePatchCreationRejection.UnchangedPixel(input.positions[unchangedIndex])
            }

            else -> {
                null
            }
        }
    }

    private fun createPatch(
        configuration: P2CandidateConfiguration,
        snapshot: P2CandidateSnapshot,
        input: P2CanonicalCandidatePatchInput,
    ): P2CandidatePatchCreationResult {
        val revisions = P2CandidateRevisionTransition(snapshot.revision, snapshot.revision + 1L)
        val region = affectedRegion(snapshot.shape, input.positions)
        val identity = P2CandidatePatchIdentity(configuration, snapshot.shape, revisions, region)
        val patch =
            when (configuration.patchLayout) {
                P2CandidatePatchLayout.ObjectRecordsMaterializedInverse -> {
                    P2ObjectCandidatePatch.create(identity, snapshot, input)
                }

                P2CandidatePatchLayout.PackedTripletsSharedDirectionalInverse -> {
                    P2PackedCandidatePatch.create(identity, snapshot, input)
                }
            }
        return P2CandidatePatchCreationResult.Created(patch)
    }

    private fun affectedRegion(
        shape: P2CanvasShape,
        positions: IntArray,
    ): P2CandidateAffectedRegion {
        val minimumX = positions.minOf { position -> position % shape.width }
        val minimumY = positions.minOf { position -> position / shape.width }
        val maximumX = positions.maxOf { position -> position % shape.width }
        val maximumY = positions.maxOf { position -> position / shape.width }
        return P2CandidateAffectedRegion(
            left = minimumX,
            top = minimumY,
            width = maximumX - minimumX + 1,
            height = maximumY - minimumY + 1,
        )
    }
}

private data class P2CanonicalCandidatePatchInput(
    val positions: IntArray,
    val after: List<PixelColor>,
)

private data class P2CandidatePatchIdentity(
    val configuration: P2CandidateConfiguration,
    val shape: P2CanvasShape,
    val revisions: P2CandidateRevisionTransition,
    val affectedRegion: P2CandidateAffectedRegion,
)

private data class P2ObjectCandidateChange(
    val position: Int,
    val before: PixelColor,
    val after: PixelColor,
) {
    fun inverse(): P2ObjectCandidateChange = P2ObjectCandidateChange(position, after, before)
}

private class P2ObjectCandidatePatch private constructor(
    private val identity: P2CandidatePatchIdentity,
    private val changes: List<P2ObjectCandidateChange>,
    override val direction: P2CandidatePatchDirection,
) : P2CandidatePatch() {
    override val configuration: P2CandidateConfiguration
        get() = identity.configuration

    override val shape: P2CanvasShape
        get() = identity.shape

    override val revisions: P2CandidateRevisionTransition
        get() = identity.revisions

    override val affectedRegion: P2CandidateAffectedRegion
        get() = identity.affectedRegion

    override val changeCount: Int
        get() = changes.size

    override val storage: P2CandidatePatchStorageCounts =
        P2CandidatePatchStorageCounts(
            primitivePayloadBytes = changes.size.toLong() * Int.SIZE_BYTES,
            referenceSlots = changes.size.toLong() * OBJECT_REFERENCE_SLOTS_PER_CHANGE,
            objectRecords = changes.size.toLong(),
            primitiveBackingArrays = 0L,
        )

    override fun positionAt(index: Int): Int = changes[index].position

    override fun beforeAt(index: Int): Int = P2PackedRgba8888.pack(changes[index].before)

    override fun afterAt(index: Int): Int = P2PackedRgba8888.pack(changes[index].after)

    override fun beforeMatches(
        snapshot: P2CandidateSnapshot,
        index: Int,
    ): Boolean = snapshot.colorAt(positionAt(index)) == changes[index].before

    override fun colorAfterAt(index: Int): PixelColor = changes[index].after

    override fun inverse(): P2CandidatePatch =
        P2ObjectCandidatePatch(
            identity = identity.copy(revisions = revisions.inverse()),
            changes = changes.map(P2ObjectCandidateChange::inverse),
            direction = direction.inverse(),
        )

    override fun sharesBackingWith(other: P2CandidatePatch): Boolean =
        other is P2ObjectCandidatePatch && changes === other.changes

    companion object {
        fun create(
            identity: P2CandidatePatchIdentity,
            snapshot: P2CandidateSnapshot,
            input: P2CanonicalCandidatePatchInput,
        ): P2ObjectCandidatePatch {
            val changes =
                input.positions.indices.map { index ->
                    P2ObjectCandidateChange(
                        position = input.positions[index],
                        before = snapshot.colorAt(input.positions[index]),
                        after = input.after[index],
                    )
                }
            return P2ObjectCandidatePatch(
                identity,
                changes,
                P2CandidatePatchDirection.Forward,
            )
        }

        private const val OBJECT_REFERENCE_SLOTS_PER_CHANGE: Long = 3L
    }
}

private data class P2PackedCandidatePatchStorage(
    val positions: IntArray,
    val before: IntArray,
    val after: IntArray,
)

private class P2PackedCandidatePatch private constructor(
    private val identity: P2CandidatePatchIdentity,
    private val values: P2PackedCandidatePatchStorage,
    override val direction: P2CandidatePatchDirection,
) : P2CandidatePatch() {
    override val configuration: P2CandidateConfiguration
        get() = identity.configuration

    override val shape: P2CanvasShape
        get() = identity.shape

    override val revisions: P2CandidateRevisionTransition
        get() = identity.revisions

    override val affectedRegion: P2CandidateAffectedRegion
        get() = identity.affectedRegion

    override val changeCount: Int
        get() = values.positions.size

    override val storage: P2CandidatePatchStorageCounts =
        P2CandidatePatchStorageCounts(
            primitivePayloadBytes = values.positions.size.toLong() * PACKED_FIELD_COUNT * Int.SIZE_BYTES,
            referenceSlots = 0L,
            objectRecords = 0L,
            primitiveBackingArrays = PACKED_FIELD_COUNT,
        )

    override fun positionAt(index: Int): Int = values.positions[index]

    override fun beforeAt(index: Int): Int =
        if (direction == P2CandidatePatchDirection.Forward) values.before[index] else values.after[index]

    override fun afterAt(index: Int): Int =
        if (direction == P2CandidatePatchDirection.Forward) values.after[index] else values.before[index]

    override fun beforeMatches(
        snapshot: P2CandidateSnapshot,
        index: Int,
    ): Boolean = snapshot.packedAt(positionAt(index)) == beforeAt(index)

    override fun inverse(): P2CandidatePatch =
        P2PackedCandidatePatch(
            identity.copy(revisions = revisions.inverse()),
            values,
            direction.inverse(),
        )

    override fun sharesBackingWith(other: P2CandidatePatch): Boolean =
        other is P2PackedCandidatePatch && values === other.values

    companion object {
        fun create(
            identity: P2CandidatePatchIdentity,
            snapshot: P2CandidateSnapshot,
            input: P2CanonicalCandidatePatchInput,
        ): P2PackedCandidatePatch =
            P2PackedCandidatePatch(
                identity,
                P2PackedCandidatePatchStorage(
                    positions = input.positions,
                    before = IntArray(input.positions.size) { index -> snapshot.packedAt(input.positions[index]) },
                    after = input.after.map(P2PackedRgba8888::pack).toIntArray(),
                ),
                P2CandidatePatchDirection.Forward,
            )

        private const val PACKED_FIELD_COUNT: Long = 3L
    }
}

internal fun P2CandidatePatchCreationResult.requiredPatch(): P2CandidatePatch =
    when (this) {
        is P2CandidatePatchCreationResult.Created -> patch
        is P2CandidatePatchCreationResult.Rejected -> error("Candidate fixture patch was rejected: $rejection")
    }

private fun P2CandidatePatchDirection.inverse(): P2CandidatePatchDirection =
    when (this) {
        P2CandidatePatchDirection.Forward -> P2CandidatePatchDirection.Inverse
        P2CandidatePatchDirection.Inverse -> P2CandidatePatchDirection.Forward
    }
