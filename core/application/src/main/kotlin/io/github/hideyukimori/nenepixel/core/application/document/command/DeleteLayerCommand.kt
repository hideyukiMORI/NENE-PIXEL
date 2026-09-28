package io.github.hideyukimori.nenepixel.core.application.document.command

import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId

public data class DeleteLayerCommand private constructor(
    override val admission: CommandSourceAdmission,
    public val layerId: LayerId,
) : LayerCommand {
    public companion object {
        public fun create(
            admission: CommandSourceAdmission,
            layerId: LayerId,
        ): DeleteLayerCommand = DeleteLayerCommand(admission, layerId)
    }
}
