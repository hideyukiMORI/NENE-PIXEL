package io.github.hideyukimori.nenepixel.core.pixelengine.measurement

internal enum class P2CandidatePatchLayout(
    val candidateId: String,
    val inversePolicy: P2CandidateInversePolicy,
) {
    ObjectRecordsMaterializedInverse(
        "current-object-records-materialized-inverse-v1",
        P2CandidateInversePolicy.MaterializedRecords,
    ),
    PackedTripletsSharedDirectionalInverse(
        "packed-rgba8888-triplets-shared-directional-inverse-v1",
        P2CandidateInversePolicy.SharedDirectionalView,
    ),
}

internal enum class P2CandidateInversePolicy(
    val csvName: String,
) {
    MaterializedRecords("materialized_records"),
    SharedDirectionalView("shared_directional_view"),
}

internal enum class P2CandidatePatchDirection {
    Forward,
    Inverse,
}

internal enum class P2CandidateConfiguration(
    val configurationId: String,
    val snapshotRepresentation: P2CandidateRepresentation,
    val patchLayout: P2CandidatePatchLayout,
) {
    CurrentObjectMaterializedInverse(
        "current-object-list__int-position-object-records-materialized-inverse-v1",
        P2CandidateRepresentation.CurrentObjectList,
        P2CandidatePatchLayout.ObjectRecordsMaterializedInverse,
    ),
    FlatPackedSharedInverse(
        "flat-packed-rgba8888__packed-triplets-shared-inverse-v1",
        P2CandidateRepresentation.FlatPackedRgba8888,
        P2CandidatePatchLayout.PackedTripletsSharedDirectionalInverse,
    ),
    TiledCowT16SharedInverse(
        "tiled-cow-rgba8888-t16__packed-triplets-shared-inverse-v1",
        P2CandidateRepresentation.TiledCowRgba8888T16,
        P2CandidatePatchLayout.PackedTripletsSharedDirectionalInverse,
    ),
    TiledCowT32SharedInverse(
        "tiled-cow-rgba8888-t32__packed-triplets-shared-inverse-v1",
        P2CandidateRepresentation.TiledCowRgba8888T32,
        P2CandidatePatchLayout.PackedTripletsSharedDirectionalInverse,
    ),
    TiledCowT64SharedInverse(
        "tiled-cow-rgba8888-t64__packed-triplets-shared-inverse-v1",
        P2CandidateRepresentation.TiledCowRgba8888T64,
        P2CandidatePatchLayout.PackedTripletsSharedDirectionalInverse,
    ),
}

internal data class P2CandidateAffectedRegion(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
)

internal data class P2CandidatePatchStorageCounts(
    val primitivePayloadBytes: Long,
    val referenceSlots: Long,
    val objectRecords: Long,
    val primitiveBackingArrays: Long,
) {
    operator fun plus(other: P2CandidatePatchStorageCounts): P2CandidatePatchStorageCounts =
        P2CandidatePatchStorageCounts(
            primitivePayloadBytes + other.primitivePayloadBytes,
            referenceSlots + other.referenceSlots,
            objectRecords + other.objectRecords,
            primitiveBackingArrays + other.primitiveBackingArrays,
        )

    companion object {
        val Empty: P2CandidatePatchStorageCounts = P2CandidatePatchStorageCounts(0L, 0L, 0L, 0L)
    }
}

internal data class P2CandidatePatchPairStorage(
    val forward: P2CandidatePatchStorageCounts,
    val inverseAdditional: P2CandidatePatchStorageCounts,
    val shared: P2CandidatePatchStorageCounts,
    val retainedUnion: P2CandidatePatchStorageCounts,
)
