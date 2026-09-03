package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.editor.DocumentDirtyState
import io.github.hideyukimori.nenepixel.core.application.workspace.ToolGesture
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportState
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.drawing.DrawingTool
import io.github.hideyukimori.nenepixel.core.domain.palette.Palette
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult

public class EditorRenderState internal constructor(
    public val snapshot: PixelSnapshot,
    public val palette: Palette,
    public val activePaletteIndex: PaletteIndex,
    public val activeTool: DrawingTool,
    public val preview: ToolGesture?,
    public val viewport: ViewportState,
    public val canUndo: Boolean,
    public val canRedo: Boolean,
    public val dirtyState: DocumentDirtyState,
) {
    public val activeColor: PixelColor
        get() =
            when (val result = palette.entryAt(activePaletteIndex)) {
                is DomainValueResult.Created -> result.value.color
                is DomainValueResult.Rejected -> error("Render palette selection is invalid: ${result.rejection}")
            }

    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is EditorRenderState &&
                    snapshot == other.snapshot &&
                    palette == other.palette &&
                    activePaletteIndex == other.activePaletteIndex &&
                    activeTool == other.activeTool &&
                    preview == other.preview &&
                    viewport == other.viewport &&
                    canUndo == other.canUndo &&
                    canRedo == other.canRedo &&
                    dirtyState == other.dirtyState
            )

    override fun hashCode(): Int =
        listOf(snapshot, palette, activePaletteIndex, activeTool, preview, viewport, canUndo, canRedo, dirtyState)
            .fold(INITIAL_HASH) { hash, value -> hash * HASH_MULTIPLIER + (value?.hashCode() ?: 0) }

    override fun toString(): String =
        "EditorRenderState(" +
            "snapshot=$snapshot, palette=$palette, activePaletteIndex=$activePaletteIndex, activeTool=$activeTool, " +
            "preview=$preview, viewport=$viewport, " +
            "canUndo=$canUndo, canRedo=$canRedo, dirtyState=$dirtyState)"

    private companion object {
        const val INITIAL_HASH: Int = 1
        const val HASH_MULTIPLIER: Int = 31
    }
}
