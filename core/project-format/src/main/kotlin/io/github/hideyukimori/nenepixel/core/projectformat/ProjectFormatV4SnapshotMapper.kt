package io.github.hideyukimori.nenepixel.core.projectformat

import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult

/** Internal allocation seam; only globally admitted records reach it. */
internal fun interface ProjectFormatV4SnapshotMapper {
    fun map(
        source: ProjectFormatBytes,
        size: CanvasSize,
        layer: ProjectFormatV4ValidatedLayer,
    ): PixelSnapshot
}

internal object CanonicalProjectFormatV4SnapshotMapper : ProjectFormatV4SnapshotMapper {
    override fun map(
        source: ProjectFormatBytes,
        size: CanvasSize,
        layer: ProjectFormatV4ValidatedLayer,
    ): PixelSnapshot {
        val offset = layer.span.offset
        val nameBytes = ProjectFormatV3Layout.unsignedByteAt(source, offset + ProjectFormatV3Layout.NAME_LENGTH_OFFSET)
        val coverageStart = offset + ProjectFormatV3Layout.NAME_OFFSET + nameBytes
        val count = size.pixelCount.toInt()
        val coverage = ByteArray(ProjectFormatV3Layout.coverageByteCount(count)) { source.byteAt(coverageStart + it) }
        val indexStart = coverageStart + coverage.size
        val indices = ByteArray(count) { source.byteAt(indexStart + it) }
        return when (val snapshot = PixelSnapshot.createPackedCells(size, indices, coverage)) {
            is DomainValueResult.Created -> snapshot.value
            is DomainValueResult.Rejected -> error("Admitted v4 cells rejected: ${snapshot.rejection}")
        }
    }
}
