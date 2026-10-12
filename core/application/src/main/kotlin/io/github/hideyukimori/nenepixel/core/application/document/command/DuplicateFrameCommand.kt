package io.github.hideyukimori.nenepixel.core.application.document.command

import io.github.hideyukimori.nenepixel.core.domain.frame.FrameId

public data class DuplicateFrameCommand private constructor(
    override val admission: CommandSourceAdmission,
    override val frameId: FrameId,
) : FrameCommand {
    public companion object {
        public fun create(
            admission: CommandSourceAdmission,
            frameId: FrameId,
        ): DuplicateFrameCommand = DuplicateFrameCommand(admission, frameId)
    }
}
