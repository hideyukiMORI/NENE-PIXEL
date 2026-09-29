package io.github.hideyukimori.nenepixel.core.pixelengine.composite

import io.github.hideyukimori.nenepixel.core.domain.drawing.StrokeEffect
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId

/** Which visible layer a running stroke writes, and with which effect. */
public class StrokeCompositeTarget(
    public val layerId: LayerId,
    public val effect: StrokeEffect,
)
