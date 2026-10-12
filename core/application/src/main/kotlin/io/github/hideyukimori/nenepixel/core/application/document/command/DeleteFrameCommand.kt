package io.github.hideyukimori.nenepixel.core.application.document.command

import io.github.hideyukimori.nenepixel.core.domain.frame.FrameId

public data class DeleteFrameCommand private constructor(
    override val admission: CommandSourceAdmission,
    override val frameId: FrameId,
) : FrameCommand {
    public companion object {
        public fun create(
            admission: CommandSourceAdmission,
            frameId: FrameId,
        ): DeleteFrameCommand = DeleteFrameCommand(admission, frameId)
    }
}
