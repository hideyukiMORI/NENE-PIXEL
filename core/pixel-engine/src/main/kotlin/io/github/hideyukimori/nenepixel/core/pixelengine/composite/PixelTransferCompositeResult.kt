package io.github.hideyukimori.nenepixel.core.pixelengine.composite

import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueRejection

public sealed interface PixelTransferCompositeResult {
    public class Prepared internal constructor(
        public val composite: PixelTransferComposite,
    ) : PixelTransferCompositeResult

    public class Rejected internal constructor(
        public val rejection: CompositeRejection,
    ) : PixelTransferCompositeResult

    public class TargetLayerNotVisible internal constructor(
        public val layerId: LayerId,
    ) : PixelTransferCompositeResult

    public class SourceRegionRejected internal constructor(
        public val rejection: DomainValueRejection,
    ) : PixelTransferCompositeResult
}
