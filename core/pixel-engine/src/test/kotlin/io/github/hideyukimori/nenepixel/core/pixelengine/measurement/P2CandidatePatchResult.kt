package io.github.hideyukimori.nenepixel.core.pixelengine.measurement

internal sealed interface P2CandidatePatchCreationResult {
    data class Created(
        val patch: P2CandidatePatch,
    ) : P2CandidatePatchCreationResult

    data class Rejected(
        val rejection: P2CandidatePatchCreationRejection,
    ) : P2CandidatePatchCreationResult
}

internal sealed interface P2CandidatePatchCreationRejection {
    data object EmptyPatch : P2CandidatePatchCreationRejection

    data object RevisionOverflow : P2CandidatePatchCreationRejection

    data class InputSizeMismatch(
        val positionCount: Int,
        val valueCount: Int,
    ) : P2CandidatePatchCreationRejection

    data class SnapshotRepresentationMismatch(
        val expected: P2CandidateRepresentation,
        val actual: P2CandidateRepresentation,
    ) : P2CandidatePatchCreationRejection

    data class PositionOutsideCanvas(
        val position: Int,
    ) : P2CandidatePatchCreationRejection

    data class DuplicatePosition(
        val position: Int,
    ) : P2CandidatePatchCreationRejection

    data class UnchangedPixel(
        val position: Int,
    ) : P2CandidatePatchCreationRejection
}

internal sealed interface P2CandidatePatchApplicationResult {
    data class Applied(
        val application: P2CandidateApplication,
    ) : P2CandidatePatchApplicationResult

    data class Rejected(
        val rejection: P2CandidatePatchApplicationRejection,
    ) : P2CandidatePatchApplicationResult
}

internal sealed interface P2CandidatePatchApplicationRejection {
    data class SnapshotRepresentationMismatch(
        val expected: P2CandidateRepresentation,
        val actual: P2CandidateRepresentation,
    ) : P2CandidatePatchApplicationRejection

    data class ShapeMismatch(
        val expected: P2CanvasShape,
        val actual: P2CanvasShape,
    ) : P2CandidatePatchApplicationRejection

    data class RevisionMismatch(
        val expected: Long,
        val actual: Long,
    ) : P2CandidatePatchApplicationRejection

    data class BeforeValueMismatch(
        val position: Int,
        val expected: Int,
        val actual: Int,
    ) : P2CandidatePatchApplicationRejection
}

internal fun P2CandidatePatchApplicationResult.requiredApplication(): P2CandidateApplication =
    when (this) {
        is P2CandidatePatchApplicationResult.Applied -> application
        is P2CandidatePatchApplicationResult.Rejected -> error("Candidate fixture apply was rejected: $rejection")
    }
