package io.github.hideyukimori.nenepixel.core.application.workspace.underlay

public sealed interface ReferenceImageRejection {
    /** A side is below 1 or above [ReferenceImage.MAX_SIDE]. */
    public data object InvalidSize : ReferenceImageRejection

    /** The raster length is not `width * height`. */
    public data object PixelCountMismatch : ReferenceImageRejection
}
