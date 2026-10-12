package io.github.hideyukimori.nenepixel.core.application.document.command

import io.github.hideyukimori.nenepixel.core.domain.frame.FrameId

public data class MoveFrameCommand private constructor(
    override val admission: CommandSourceAdmission,
    override val frameId: FrameId,
    public val toPosition: Int,
) : FrameCommand {
    public companion object {
        public fun create(
            admission: CommandSourceAdmission,
            frameId: FrameId,
            toPosition: Int,
        ): MoveFrameCommand = MoveFrameCommand(admission, frameId, toPosition)
    }
}
