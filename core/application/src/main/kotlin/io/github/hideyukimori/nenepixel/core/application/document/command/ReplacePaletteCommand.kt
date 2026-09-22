package io.github.hideyukimori.nenepixel.core.application.document.command

import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteRemap

public data class ReplacePaletteCommand private constructor(
    public val admission: CommandSourceAdmission,
    public val remap: PaletteRemap,
) : DocumentCommand {
    public companion object {
        public fun create(
            admission: CommandSourceAdmission,
            remap: PaletteRemap,
        ): ReplacePaletteCommand = ReplacePaletteCommand(admission, remap)
    }
}
