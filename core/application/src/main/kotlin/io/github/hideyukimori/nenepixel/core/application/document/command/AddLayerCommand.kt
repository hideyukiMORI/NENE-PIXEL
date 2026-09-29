package io.github.hideyukimori.nenepixel.core.application.document.command

import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId

/** Inserts an all-Empty, unnamed, visible layer directly above [aboveLayerId]. */
public data class AddLayerCommand private constructor(
    override val admission: CommandSourceAdmission,
    public val aboveLayerId: LayerId,
) : LayerCommand {
    public companion object {
        public fun create(
            admission: CommandSourceAdmission,
            aboveLayerId: LayerId,
        ): AddLayerCommand = AddLayerCommand(admission, aboveLayerId)
    }
}
