package io.github.hideyukimori.nenepixel.core.application.persistence

import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.frame.Frame
import io.github.hideyukimori.nenepixel.core.domain.frame.FrameId

/** Runtime-issued committed document and its owned frame; later workspace choices cannot retarget it. */
public class PngExportCapture private constructor(
    public val document: DocumentState,
    public val frame: Frame,
) {
    public val frameId: FrameId get() = frame.id

    internal companion object {
        fun capture(
            document: DocumentState,
            frameId: FrameId,
        ): PngExportCapture = PngExportCapture(document, requireNotNull(document.frameById(frameId)))
    }
}
