package io.github.hideyukimori.nenepixel.core.projectformat

import io.github.hideyukimori.nenepixel.core.domain.frame.FrameDuration
import io.github.hideyukimori.nenepixel.core.domain.frame.FrameId
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerVisibility
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult

/** Bounded metadata scan; every payload span and the global record budget precede raster allocation. */
internal class ProjectFormatV4Structure(
    private val source: ProjectFormatBytes,
    private val header: ProjectFormatIndexedHeader,
) {
    private var offset = ProjectFormatV3Layout.layerCountOffset(header.paletteEntryCount) + 1
    private val frames = ArrayList<ProjectFormatV4FrameSpan>()
    private val layerIds = mutableSetOf<Int>()

    fun scan(): ProjectFormatResult<List<ProjectFormatV4FrameSpan>> {
        val count = ProjectFormatV3Layout.unsignedByteAt(source, offset - 1)
        if (count !in 1..ProjectFormatV4Layout.MAX_FRAME_COUNT) {
            return rejected(ProjectFormatRejection.InvalidFrameCount(count))
        }
        var rejection: ProjectFormatRejection? = null
        while (frames.size < count && rejection == null) {
            when (val frame = scanFrame()) {
                is ProjectFormatResult.Accepted -> frames.add(frame.value)
                is ProjectFormatResult.Rejected -> rejection = frame.rejection
            }
        }
        return rejection?.let(::rejected) ?: verifyEnd()
    }

    private fun scanFrame(): ProjectFormatResult<ProjectFormatV4FrameSpan> =
        source.requireV4Bytes(offset.toLong() + ProjectFormatV4Layout.FRAME_FIXED_BYTE_COUNT).andThen {
            frameId().andThen { id ->
                duration().andThen { duration ->
                    layerCount().andThen { count ->
                        offset += ProjectFormatV4Layout.FRAME_FIXED_BYTE_COUNT
                        scanLayers(count).andThen { layers -> accepted(ProjectFormatV4FrameSpan(id, duration, layers)) }
                    }
                }
            }
        }

    private fun frameId(): ProjectFormatResult<FrameId> {
        val raw = ProjectFormatV4Layout.unsignedInt(source, offset)
        return when {
            raw !in 1L..Int.MAX_VALUE.toLong() -> rejected(ProjectFormatRejection.InvalidFrameId(raw))
            frames.any { it.id.value.toLong() == raw } -> rejected(ProjectFormatRejection.DuplicateFrameId(raw.toInt()))
            else -> accepted(validated(FrameId.create(raw.toInt())))
        }
    }

    private fun duration(): ProjectFormatResult<FrameDuration> {
        val raw = ProjectFormatV4Layout.unsignedInt(source, offset + ProjectFormatV4Layout.DURATION_OFFSET)
        return if (raw in FrameDuration.MIN_MILLISECONDS.toLong()..FrameDuration.MAX_MILLISECONDS.toLong()) {
            accepted(validated(FrameDuration.create(raw.toInt())))
        } else {
            rejected(ProjectFormatRejection.InvalidFrameDuration(frames.size, raw))
        }
    }

    private fun layerCount(): ProjectFormatResult<Int> {
        val count = ProjectFormatV3Layout.unsignedByteAt(source, offset + ProjectFormatV4Layout.LAYER_COUNT_OFFSET)
        val total = layerIds.size + count
        return when {
            count !in 1..ProjectFormatV3Layout.MAX_LAYER_COUNT -> {
                rejected(
                    ProjectFormatRejection.InvalidLayerCount(count),
                )
            }

            total > ProjectFormatV4Layout.MAX_TOTAL_LAYER_COUNT -> {
                rejected(
                    ProjectFormatRejection.InvalidTotalLayerCount(total),
                )
            }

            else -> {
                accepted(count)
            }
        }
    }

    private fun scanLayers(count: Int): ProjectFormatResult<List<ProjectFormatV4LayerSpan>> {
        val layers = ArrayList<ProjectFormatV4LayerSpan>(count)
        var rejection: ProjectFormatRejection? = null
        while (layers.size < count && rejection == null) {
            when (val layer = scanLayer()) {
                is ProjectFormatResult.Accepted -> layers.add(layer.value)
                is ProjectFormatResult.Rejected -> rejection = layer.rejection
            }
        }
        return rejection?.let(::rejected) ?: accepted(layers)
    }

    private fun scanLayer(): ProjectFormatResult<ProjectFormatV4LayerSpan> =
        source.requireV4Bytes(offset.toLong() + ProjectFormatV3Layout.LAYER_FIXED_BYTE_COUNT).andThen {
            layerId().andThen { id ->
                visibility().andThen { visibility ->
                    payloadEnd().andThen { end ->
                        val span = ProjectFormatV4LayerSpan(layerIds.size, offset, id, visibility)
                        layerIds.add(id.value)
                        offset = end
                        accepted(span)
                    }
                }
            }
        }

    private fun layerId(): ProjectFormatResult<LayerId> {
        val raw = ProjectFormatV4Layout.unsignedInt(source, offset)
        return when {
            raw !in 1L..Int.MAX_VALUE.toLong() -> rejected(ProjectFormatRejection.InvalidLayerId(raw))
            raw.toInt() in layerIds -> rejected(ProjectFormatRejection.DuplicateLayerId(raw.toInt()))
            else -> accepted(validated(LayerId.create(raw.toInt())))
        }
    }

    private fun visibility(): ProjectFormatResult<LayerVisibility> {
        val flags = ProjectFormatV3Layout.unsignedByteAt(source, offset + ProjectFormatV3Layout.FLAGS_OFFSET)
        return when {
            flags and ProjectFormatV3Layout.VISIBLE_FLAG.inv() != 0 -> {
                rejected(ProjectFormatRejection.UnknownLayerFlags(layerIds.size, flags))
            }

            flags == ProjectFormatV3Layout.VISIBLE_FLAG -> {
                accepted(LayerVisibility.Visible)
            }

            else -> {
                accepted(LayerVisibility.Hidden)
            }
        }
    }

    private fun payloadEnd(): ProjectFormatResult<Int> {
        val count = ProjectFormatV3Layout.unsignedByteAt(source, offset + ProjectFormatV3Layout.NAME_LENGTH_OFFSET)
        return if (count > ProjectFormatV3Layout.MAX_NAME_BYTE_COUNT) {
            rejected(ProjectFormatRejection.InvalidLayerName(layerIds.size))
        } else {
            val end = offset.toLong() + ProjectFormatV3Layout.layerByteCount(count, header.size.pixelCount.toInt())
            source.requireV4Bytes(end).andThen { accepted(end.toInt()) }
        }
    }

    private fun verifyEnd(): ProjectFormatResult<List<ProjectFormatV4FrameSpan>> {
        val expected = offset.toLong() + Int.SIZE_BYTES
        return when {
            expected > source.byteCount.toLong() -> {
                rejected(ProjectFormatRejection.Truncated(source.byteCount, expected.toInt()))
            }

            expected < source.byteCount.toLong() -> {
                rejected(ProjectFormatRejection.TrailingData(source.byteCount, expected.toInt()))
            }

            else -> {
                val computed = Crc32IsoHdlc.checksum(source, offset)
                val stored = ProjectFormatBigEndian.readInt(source, offset).toUInt()
                if (computed ==
                    stored
                ) {
                    accepted(frames)
                } else {
                    rejected(ProjectFormatRejection.ChecksumMismatch(computed, stored))
                }
            }
        }
    }
}

private fun <T> validated(result: DomainValueResult<T>): T =
    when (result) {
        is DomainValueResult.Created -> result.value
        is DomainValueResult.Rejected -> error("Admitted v4 metadata rejected: ${result.rejection}")
    }

internal class ProjectFormatV4FrameSpan(
    val id: FrameId,
    val duration: FrameDuration,
    val layers: List<ProjectFormatV4LayerSpan>,
)

internal class ProjectFormatV4LayerSpan(
    val index: Int,
    val offset: Int,
    val id: LayerId,
    val visibility: LayerVisibility,
)

private fun ProjectFormatBytes.requireV4Bytes(required: Long): ProjectFormatResult<Unit> =
    if (required > byteCount.toLong()) {
        rejected(ProjectFormatRejection.Truncated(byteCount, required.toInt()))
    } else {
        accepted(Unit)
    }
