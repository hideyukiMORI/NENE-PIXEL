package io.github.hideyukimori.nenepixel.core.application.workspace.importing

import io.github.hideyukimori.nenepixel.core.application.document.command.CommandSourceAdmission
import io.github.hideyukimori.nenepixel.core.domain.frame.FrameId
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId

/** One immutable pick-begin admission; retains at most one bounded old document while pending. */
public class LayerImportTarget private constructor(
    internal val admission: CommandSourceAdmission,
    public val frameId: FrameId,
    public val anchorLayerId: LayerId,
) {
    public val frameOrdinal: Int get() = admission.document.frames.indexOfFirst { it.id == frameId } + 1
    public val frameCount: Int get() = admission.document.frames.size

    internal companion object {
        fun capture(
            admission: CommandSourceAdmission,
            frameId: FrameId,
            anchorLayerId: LayerId,
        ): LayerImportTarget {
            val frame = requireNotNull(admission.document.frameById(frameId))
            require(frame.layers.any { it.id == anchorLayerId }) { "Picked import anchor is outside its frame" }
            return LayerImportTarget(admission, frameId, anchorLayerId)
        }
    }
}
