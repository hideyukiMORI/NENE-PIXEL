package io.github.hideyukimori.nenepixel.core.domain.document

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
    public val layers: List<Layer>,
) {
    public val size: CanvasSize
        get() = layers.first().snapshot.size

    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is DocumentState &&
                    id == other.id &&
                    revision == other.revision &&
                    definition == other.definition &&
                    layers == other.layers
            )

    override fun hashCode(): Int =
        31 * (31 * (31 * id.hashCode() + revision.hashCode()) + definition.hashCode()) + layers.hashCode()

    override fun toString(): String =
        "DocumentState(id=$id, revision=$revision, definition=$definition, layers=$layers)"

    public companion object {
        public fun createLayered(
            id: DocumentId,
            revision: Revision,
            definition: PaletteDefinition,
            layers: List<Layer>,
        ): DomainValueResult<DocumentState> =
            layers.toList().let { owned ->
                documentLayersRejection(definition, owned)
                    ?.let { rejected(it) }
                    ?: created(DocumentState(id, revision, definition, owned))
            }

        /** Thin convenience over [createLayered] for a document holding exactly one visible, unnamed layer. */
        public fun createSingleLayer(
            id: DocumentId,
            revision: Revision,
            definition: PaletteDefinition,
            snapshot: PixelSnapshot,
        ): DomainValueResult<DocumentState> =
            createLayered(
                id,
                revision,
                definition,
                listOf(Layer.create(LayerId.first(), LayerName.empty, LayerVisibility.Visible, snapshot)),
            )
    }
}
