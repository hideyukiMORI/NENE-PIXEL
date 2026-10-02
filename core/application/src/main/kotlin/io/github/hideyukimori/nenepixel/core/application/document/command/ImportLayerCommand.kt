package io.github.hideyukimori.nenepixel.core.application.document.command

import io.github.hideyukimori.nenepixel.core.domain.importing.LayerImportPlan
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId

/**
 * Inserts the layer [plan] describes directly above [aboveLayerId] and applies its palette, as one history entry
 * (ADR 0033). It is not a [LayerCommand] because it may also change the palette.
 */
public data class ImportLayerCommand private constructor(
    public val admission: CommandSourceAdmission,
    public val aboveLayerId: LayerId,
    public val plan: LayerImportPlan,
) : DocumentCommand {
    public companion object {
        public fun create(
            admission: CommandSourceAdmission,
            aboveLayerId: LayerId,
            plan: LayerImportPlan,
        ): ImportLayerCommand = ImportLayerCommand(admission, aboveLayerId, plan)
    }
}
