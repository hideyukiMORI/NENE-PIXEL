package io.github.hideyukimori.nenepixel.core.application.document.command

import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId

/** Moves [layerId] so that it ends at [toPosition], counted from the bottom (0) in the order after the move. */
public data class MoveLayerCommand private constructor(
    override val admission: CommandSourceAdmission,
    public val layerId: LayerId,
    public val toPosition: Int,
) : LayerCommand {
    public companion object {
        public fun create(
            admission: CommandSourceAdmission,
            layerId: LayerId,
            toPosition: Int,
        ): MoveLayerCommand = MoveLayerCommand(admission, layerId, toPosition)
    }
}
