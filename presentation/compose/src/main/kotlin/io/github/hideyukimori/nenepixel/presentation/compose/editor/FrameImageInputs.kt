package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.workspace.palette.PaletteEditSession
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.frame.FrameId
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition

/** One immutable display request; frame choice is projected from the runtime, never owned by the cache. */
internal class FrameImageInputs(
    val document: DocumentState,
    val frameId: FrameId,
    val definition: PaletteDefinition,
    val session: PaletteEditSession?,
) {
    override fun equals(other: Any?): Boolean =
        other is FrameImageInputs && document === other.document && frameId == other.frameId &&
            definition === other.definition && session === other.session

    override fun hashCode(): Int = System.identityHashCode(document) * HASH_MULTIPLIER + frameId.hashCode()

    companion object {
        private const val HASH_MULTIPLIER = 31

        fun committed(
            document: DocumentState,
            frameId: FrameId,
        ): FrameImageInputs = FrameImageInputs(document, frameId, document.definition, null)
    }
}
