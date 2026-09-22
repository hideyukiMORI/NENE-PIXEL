package io.github.hideyukimori.nenepixel.core.domain.document

import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.core.domain.validation.created

public class DocumentState private constructor(
    public val id: DocumentId,
    public val definition: PaletteDefinition,
    public val snapshot: PixelSnapshot,
) {
    public val size: CanvasSize
        get() = snapshot.size

    public val revision: Revision
        get() = snapshot.revision

    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is DocumentState && id == other.id && definition == other.definition && snapshot == other.snapshot)

    override fun hashCode(): Int = 31 * (31 * id.hashCode() + definition.hashCode()) + snapshot.hashCode()

    override fun toString(): String = "DocumentState(id=$id, definition=$definition, snapshot=$snapshot)"

    public companion object {
        public fun create(
            id: DocumentId,
            definition: PaletteDefinition,
            snapshot: PixelSnapshot,
        ): DomainValueResult<DocumentState> =
            when (val membership = definition.palette.entryAt(snapshot.maximumIndex)) {
                is DomainValueResult.Created -> created(DocumentState(id, definition, snapshot))
                is DomainValueResult.Rejected -> membership
            }
    }
}
