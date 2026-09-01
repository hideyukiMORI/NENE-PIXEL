package io.github.hideyukimori.nenepixel.core.domain.document

import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot

public data class DocumentState private constructor(
    public val id: DocumentId,
    public val snapshot: PixelSnapshot,
) {
    public val size: CanvasSize
        get() = snapshot.size

    public val revision: Revision
        get() = snapshot.revision

    public companion object {
        public fun create(
            id: DocumentId,
            snapshot: PixelSnapshot,
        ): DocumentState = DocumentState(id, snapshot)
    }
}
