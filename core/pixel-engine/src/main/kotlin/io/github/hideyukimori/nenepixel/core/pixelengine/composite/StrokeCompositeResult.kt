package io.github.hideyukimori.nenepixel.core.pixelengine.composite

import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex

public sealed interface StrokeCompositeResult {
    public data class Prepared internal constructor(
        public val composite: StrokeComposite,
    ) : StrokeCompositeResult

    public data class Rejected internal constructor(
        public val rejection: CompositeRejection,
    ) : StrokeCompositeResult

    public data class TargetLayerNotVisible internal constructor(
        public val layerId: LayerId,
    ) : StrokeCompositeResult

    public data class TargetIndexOutsidePalette internal constructor(
        public val index: PaletteIndex,
    ) : StrokeCompositeResult
}
