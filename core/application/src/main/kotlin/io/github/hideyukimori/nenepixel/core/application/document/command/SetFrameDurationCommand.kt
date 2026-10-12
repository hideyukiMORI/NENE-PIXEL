package io.github.hideyukimori.nenepixel.core.application.document.command

import io.github.hideyukimori.nenepixel.core.domain.frame.FrameDuration
import io.github.hideyukimori.nenepixel.core.domain.frame.FrameId

public data class SetFrameDurationCommand private constructor(
    override val admission: CommandSourceAdmission,
    override val frameId: FrameId,
    public val duration: FrameDuration,
) : FrameCommand {
    public companion object {
        public fun create(
            admission: CommandSourceAdmission,
            frameId: FrameId,
            duration: FrameDuration,
        ): SetFrameDurationCommand = SetFrameDurationCommand(admission, frameId, duration)
    }
}
