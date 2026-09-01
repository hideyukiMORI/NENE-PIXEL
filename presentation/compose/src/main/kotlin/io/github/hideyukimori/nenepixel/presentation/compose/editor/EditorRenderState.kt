package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.workspace.ToolGesture
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot

public class EditorRenderState internal constructor(
    public val snapshot: PixelSnapshot,
    public val activeColor: PixelColor,
    public val preview: ToolGesture?,
) {
    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is EditorRenderState &&
                    snapshot == other.snapshot &&
                    activeColor == other.activeColor &&
                    preview == other.preview
            )

    override fun hashCode(): Int =
        ((snapshot.hashCode() * HASH_MULTIPLIER) + activeColor.hashCode()) * HASH_MULTIPLIER +
            (preview?.hashCode() ?: 0)

    override fun toString(): String =
        "EditorRenderState(snapshot=$snapshot, activeColor=$activeColor, preview=$preview)"

    private companion object {
        const val HASH_MULTIPLIER: Int = 31
    }
}
