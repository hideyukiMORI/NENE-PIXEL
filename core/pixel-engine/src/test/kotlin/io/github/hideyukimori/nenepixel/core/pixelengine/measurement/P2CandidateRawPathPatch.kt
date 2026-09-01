package io.github.hideyukimori.nenepixel.core.pixelengine.measurement

import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor

internal sealed interface P2CandidateRawPathResult {
    data class Rasterized(
        val patch: P2CandidatePatch,
    ) : P2CandidateRawPathResult

    data object NoChanges : P2CandidateRawPathResult

    data class Rejected(
        val rejection: P2CandidateRawPathRejection,
    ) : P2CandidateRawPathResult
}

internal sealed interface P2CandidateRawPathRejection {
    data class SnapshotRepresentationMismatch(
        val expected: P2CandidateRepresentation,
        val actual: P2CandidateRepresentation,
    ) : P2CandidateRawPathRejection

    data object EmptyPath : P2CandidateRawPathRejection

    data class PositionOutsideCanvas(
        val position: Int,
    ) : P2CandidateRawPathRejection

    data object RevisionOverflow : P2CandidateRawPathRejection
}

internal object P2CandidateRawPathPatchFactory {
    fun create(
        configuration: P2CandidateConfiguration,
        snapshot: P2CandidateSnapshot,
        rawPositions: IntArray,
        target: PixelColor,
    ): P2CandidateRawPathResult {
        val rejection = validateInput(configuration, snapshot, rawPositions)
        return if (rejection != null) {
            P2CandidateRawPathResult.Rejected(rejection)
        } else {
            val canonicalPositions = effectivePositions(snapshot, rawPositions, target)
            if (canonicalPositions.isEmpty()) {
                P2CandidateRawPathResult.NoChanges
            } else {
                createPatch(configuration, snapshot, canonicalPositions, target)
            }
        }
    }

    private fun validateInput(
        configuration: P2CandidateConfiguration,
        snapshot: P2CandidateSnapshot,
        rawPositions: IntArray,
    ): P2CandidateRawPathRejection? =
        when {
            snapshot.representation != configuration.snapshotRepresentation -> {
                P2CandidateRawPathRejection.SnapshotRepresentationMismatch(
                    expected = configuration.snapshotRepresentation,
                    actual = snapshot.representation,
                )
            }

            rawPositions.isEmpty() -> {
                P2CandidateRawPathRejection.EmptyPath
            }

            else -> {
                rawPositions
                    .firstOrNull { position -> position !in 0 until snapshot.shape.pixelCount.toInt() }
                    ?.let(P2CandidateRawPathRejection::PositionOutsideCanvas)
            }
        }

    private fun effectivePositions(
        snapshot: P2CandidateSnapshot,
        rawPositions: IntArray,
        target: PixelColor,
    ): IntArray {
        val seen = BooleanArray(snapshot.shape.pixelCount.toInt())
        val positions = IntArray(rawPositions.size)
        var changeCount = 0
        rawPositions.forEach { position ->
            if (!seen[position]) {
                seen[position] = true
                if (!snapshot.matchesColor(position, target)) {
                    positions[changeCount] = position
                    changeCount += 1
                }
            }
        }
        return positions.copyOf(changeCount)
    }

    private fun createPatch(
        configuration: P2CandidateConfiguration,
        snapshot: P2CandidateSnapshot,
        positions: IntArray,
        target: PixelColor,
    ): P2CandidateRawPathResult =
        when (
            val result =
                P2CandidatePatchFactory.create(
                    configuration,
                    snapshot,
                    positions,
                    List(positions.size) { target },
                )
        ) {
            is P2CandidatePatchCreationResult.Created -> P2CandidateRawPathResult.Rasterized(result.patch)
            is P2CandidatePatchCreationResult.Rejected -> result.rejection.toRawPathResult()
        }

    private fun P2CandidatePatchCreationRejection.toRawPathResult(): P2CandidateRawPathResult =
        when (this) {
            P2CandidatePatchCreationRejection.RevisionOverflow -> {
                P2CandidateRawPathResult.Rejected(P2CandidateRawPathRejection.RevisionOverflow)
            }

            P2CandidatePatchCreationRejection.EmptyPatch,
            is P2CandidatePatchCreationRejection.SnapshotRepresentationMismatch,
            is P2CandidatePatchCreationRejection.InputSizeMismatch,
            is P2CandidatePatchCreationRejection.PositionOutsideCanvas,
            is P2CandidatePatchCreationRejection.DuplicatePosition,
            is P2CandidatePatchCreationRejection.UnchangedPixel,
            -> {
                error("Normalized candidate raw path produced an invalid patch: $this")
            }
        }
}
