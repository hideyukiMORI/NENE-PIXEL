package io.github.hideyukimori.nenepixel.core.application.render

import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.frame.FrameId
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelRegion
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId
import io.github.hideyukimori.nenepixel.core.domain.selection.PixelTransfer
import io.github.hideyukimori.nenepixel.core.pixelengine.composite.PixelTransferComposite
import io.github.hideyukimori.nenepixel.core.pixelengine.composite.PixelTransferCompositeResult
import io.github.hideyukimori.nenepixel.core.pixelengine.composite.PixelTransferCompositeTarget
import io.github.hideyukimori.nenepixel.core.pixelengine.composite.preparePixelTransferComposite

/** Source preparation at the same rendering boundary as StrokePreviewComposite (ADR 0039). */
public class SelectionPreviewComposite private constructor(
    private val composite: PixelTransferComposite,
) {
    /** Offset/mode changes reuse prepared cells/layers; no canvas-sized output is published. */
    public fun render(transfer: PixelTransfer): SelectionPreviewImage = SelectionPreviewImage(composite, transfer)

    public companion object {
        /** Prepares one admitted visible-layer rectangle; discard on source document/layer/rectangle change. */
        public fun prepare(
            document: DocumentState,
            frameId: FrameId,
            layerId: LayerId,
            source: PixelRegion,
        ): SelectionPreviewComposite =
            when (
                val result =
                    preparePixelTransferComposite(
                        document.size,
                        requireNotNull(document.frameById(frameId)).layers.also { layers ->
                            require(layers.any { it.id == layerId }) { "Selection target is outside its preview frame" }
                        },
                        document.definition,
                        PixelTransferCompositeTarget(layerId, source),
                    )
            ) {
                is PixelTransferCompositeResult.Prepared -> {
                    SelectionPreviewComposite(result.composite)
                }

                is PixelTransferCompositeResult.Rejected -> {
                    error(
                        "Selection composite invariant rejected: ${result.rejection}",
                    )
                }

                is PixelTransferCompositeResult.TargetLayerNotVisible -> {
                    error(
                        "Selection target not visible: ${result.layerId}",
                    )
                }

                is PixelTransferCompositeResult.SourceRegionRejected -> {
                    error(
                        "Selection source rejected: ${result.rejection}",
                    )
                }
            }
    }
}
