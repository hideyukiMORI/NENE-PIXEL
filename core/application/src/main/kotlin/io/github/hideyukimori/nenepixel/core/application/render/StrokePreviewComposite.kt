package io.github.hideyukimori.nenepixel.core.application.render

import io.github.hideyukimori.nenepixel.core.application.workspace.ToolGesture
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.pixelengine.composite.StrokeComposite
import io.github.hideyukimori.nenepixel.core.pixelengine.composite.StrokeCompositeResult
import io.github.hideyukimori.nenepixel.core.pixelengine.composite.StrokeCompositeTarget
import io.github.hideyukimori.nenepixel.core.pixelengine.composite.prepareStrokeComposite

/**
 * What the canvas shows at a pixel if the running gesture were committed now (ADR 0030, Composite).
 */
public class StrokePreviewComposite private constructor(
    private val composite: StrokeComposite,
) {
    /** Packed RGBA8888 of the composite at [position]; `(0, 0, 0, 0)` when nothing contributes. */
    public fun packedRgba8888At(position: PixelPosition): Int {
        val size = composite.size
        require(size.contains(position)) { "Position $position is outside the canvas $size" }
        return composite.packedRgba8888At(position.y.value * size.width.value + position.x.value)
    }

    public companion object {
        /** Prepares the preview of [gesture] over [document]; the document invariants rule out rejection. */
        public fun prepare(
            document: DocumentState,
            gesture: ToolGesture,
        ): StrokePreviewComposite {
            check(gesture.canvas == document.size) {
                "Gesture canvas ${gesture.canvas} does not match the document size ${document.size}"
            }
            val target = StrokeCompositeTarget(gesture.layerId, gesture.effect)
            val result = prepareStrokeComposite(document.size, document.layers, document.definition, target)
            return when (result) {
                is StrokeCompositeResult.Prepared -> {
                    StrokePreviewComposite(result.composite)
                }

                is StrokeCompositeResult.Rejected,
                is StrokeCompositeResult.TargetLayerNotVisible,
                is StrokeCompositeResult.TargetIndexOutsidePalette,
                -> {
                    error("Stroke preview composite invariant was rejected: $result")
                }
            }
        }
    }
}
