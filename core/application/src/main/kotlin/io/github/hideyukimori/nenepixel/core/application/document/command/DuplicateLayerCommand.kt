package io.github.hideyukimori.nenepixel.core.application.document.command

import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId

/** Inserts an exact copy of [layerId] directly above it, sharing its immutable snapshot (ADR 0030, #208). */
public data class DuplicateLayerCommand private constructor(
    override val admission: CommandSourceAdmission,
    public val layerId: LayerId,
) : LayerCommand {
    public companion object {
        public fun create(
            admission: CommandSourceAdmission,
            layerId: LayerId,
        ): DuplicateLayerCommand = DuplicateLayerCommand(admission, layerId)
    }
}
