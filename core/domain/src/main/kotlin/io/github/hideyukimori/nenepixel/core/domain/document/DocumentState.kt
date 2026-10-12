package io.github.hideyukimori.nenepixel.core.domain.document

import io.github.hideyukimori.nenepixel.core.domain.frame.Frame
import io.github.hideyukimori.nenepixel.core.domain.frame.FrameDuration
import io.github.hideyukimori.nenepixel.core.domain.frame.FrameId
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.layer.Layer
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerName
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerVisibility
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.core.domain.validation.created
import io.github.hideyukimori.nenepixel.core.domain.validation.rejected

public class DocumentState private constructor(
    public val id: DocumentId,
    public val revision: Revision,
    public val definition: PaletteDefinition,
    public val frames: List<Frame>,
) {
    public val size: CanvasSize
        get() =
            frames
                .first()
                .layers
                .first()
                .snapshot.size

    public val totalLayerCount: Int get() = frames.sumOf { it.layers.size }

    public fun frameById(frameId: FrameId): Frame? = frames.firstOrNull { it.id == frameId }

    public fun frameContainingLayer(layerId: LayerId): Frame? =
        frames.firstOrNull { frame -> frame.layers.any { it.id == layerId } }

    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is DocumentState &&
                    id == other.id &&
                    revision == other.revision &&
                    definition == other.definition &&
                    frames == other.frames
            )

    override fun hashCode(): Int =
        31 * (31 * (31 * id.hashCode() + revision.hashCode()) + definition.hashCode()) + frames.hashCode()

    override fun toString(): String =
        "DocumentState(id=$id, revision=$revision, definition=$definition, frames=$frames)"

    public companion object {
        public fun create(
            id: DocumentId,
            revision: Revision,
            definition: PaletteDefinition,
            frames: List<Frame>,
        ): DomainValueResult<DocumentState> =
            documentFramesRejection(definition, frames)?.let { rejected(it) }
                ?: created(DocumentState(id, revision, definition, frames.toList()))

        public fun createSingleFrame(
            id: DocumentId,
            revision: Revision,
            definition: PaletteDefinition,
            layers: List<Layer>,
        ): DomainValueResult<DocumentState> =
            when (val frame = Frame.create(FrameId.first(), FrameDuration.default, layers)) {
                is DomainValueResult.Created -> create(id, revision, definition, listOf(frame.value))
                is DomainValueResult.Rejected -> frame
            }

        /** Thin convenience over [createSingleFrame] for a document holding exactly one visible, unnamed layer. */
        public fun createSingleLayer(
            id: DocumentId,
            revision: Revision,
            definition: PaletteDefinition,
            snapshot: PixelSnapshot,
        ): DomainValueResult<DocumentState> =
            createSingleFrame(
                id,
                revision,
                definition,
                listOf(Layer.create(LayerId.first(), LayerName.empty, LayerVisibility.Visible, snapshot)),
            )
    }
}
