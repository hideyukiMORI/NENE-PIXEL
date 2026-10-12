package io.github.hideyukimori.nenepixel.core.application.document.command

import io.github.hideyukimori.nenepixel.core.domain.frame.FrameId

/** One source-admitted saved frame edit (ADR0041). */
public sealed interface FrameCommand : DocumentCommand {
    public val admission: CommandSourceAdmission
    public val frameId: FrameId
}
