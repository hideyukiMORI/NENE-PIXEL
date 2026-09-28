package io.github.hideyukimori.nenepixel.core.application.document.command

import io.github.hideyukimori.nenepixel.core.domain.drawing.Stroke
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId

public data class ApplyStrokeCommand private constructor(
    public val admission: CommandSourceAdmission,
    public val layerId: LayerId,
    public val stroke: Stroke,
) : DocumentCommand {
    public companion object {
        public fun create(
            admission: CommandSourceAdmission,
            layerId: LayerId,
            stroke: Stroke,
        ): ApplyStrokeCommand = ApplyStrokeCommand(admission, layerId, stroke)
    }
}
