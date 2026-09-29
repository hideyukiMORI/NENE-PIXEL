package io.github.hideyukimori.nenepixel.core.application.document.command

import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerName

public data class RenameLayerCommand private constructor(
    override val admission: CommandSourceAdmission,
    public val layerId: LayerId,
    public val name: LayerName,
) : LayerCommand {
    public companion object {
        public fun create(
            admission: CommandSourceAdmission,
            layerId: LayerId,
            name: LayerName,
        ): RenameLayerCommand = RenameLayerCommand(admission, layerId, name)
    }
}
