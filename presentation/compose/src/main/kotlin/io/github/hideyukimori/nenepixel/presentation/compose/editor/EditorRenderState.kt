package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.workspace.ToolGesture
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot

public class EditorRenderState internal constructor(
    public val snapshot: PixelSnapshot,
    public val activeColor: PixelColor,
    public val preview: ToolGesture?,
    public val canUndo: Boolean,
    public val canRedo: Boolean,
) {
    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is EditorRenderState &&
                    snapshot == other.snapshot &&
                    activeColor == other.activeColor &&
                    preview == other.preview &&
                    canUndo == other.canUndo &&
                    canRedo == other.canRedo
            )

    override fun hashCode(): Int =
        listOf(snapshot, activeColor, preview, canUndo, canRedo)
            .fold(INITIAL_HASH) { hash, value -> hash * HASH_MULTIPLIER + (value?.hashCode() ?: 0) }

    override fun toString(): String =
        "EditorRenderState(" +
            "snapshot=$snapshot, activeColor=$activeColor, preview=$preview, " +
            "canUndo=$canUndo, canRedo=$canRedo)"

    private companion object {
        const val INITIAL_HASH: Int = 1
        const val HASH_MULTIPLIER: Int = 31
    }
}
