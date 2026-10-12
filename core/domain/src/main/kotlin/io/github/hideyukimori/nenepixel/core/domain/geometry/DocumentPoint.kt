package io.github.hideyukimori.nenepixel.core.domain.geometry

/** Signed integer document-pixel coordinate that may lie outside the canvas, such as a raw gesture sample. */
public data class DocumentPoint private constructor(
    public val x: Int,
    public val y: Int,
) {
    public companion object {
        public fun create(
            x: Int,
            y: Int,
        ): DocumentPoint = DocumentPoint(x, y)
    }
}

/** The same document pixel as this in-canvas position, as a signed document coordinate. */
public fun PixelPosition.toDocumentPoint(): DocumentPoint = DocumentPoint.create(x.value, y.value)
