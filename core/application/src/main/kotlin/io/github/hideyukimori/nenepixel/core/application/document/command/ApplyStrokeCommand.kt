package io.github.hideyukimori.nenepixel.core.application.document.command

import io.github.hideyukimori.nenepixel.core.domain.drawing.Stroke

public data class ApplyStrokeCommand private constructor(
    public val admission: CommandSourceAdmission,
    public val stroke: Stroke,
) : DocumentCommand {
    public companion object {
        public fun create(
            admission: CommandSourceAdmission,
            stroke: Stroke,
        ): ApplyStrokeCommand = ApplyStrokeCommand(admission, stroke)
    }
}
