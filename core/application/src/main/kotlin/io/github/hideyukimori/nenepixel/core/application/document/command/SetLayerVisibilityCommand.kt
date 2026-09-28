package io.github.hideyukimori.nenepixel.core.application.document.command

import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerVisibility

public data class SetLayerVisibilityCommand private constructor(
    override val admission: CommandSourceAdmission,
    public val layerId: LayerId,
    public val visibility: LayerVisibility,
) : LayerCommand {
    public companion object {
        public fun create(
            admission: CommandSourceAdmission,
            layerId: LayerId,
            visibility: LayerVisibility,
        ): SetLayerVisibilityCommand = SetLayerVisibilityCommand(admission, layerId, visibility)
    }
}
