package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.editor.DocumentDirtyState
import io.github.hideyukimori.nenepixel.core.application.workspace.ActualSizeWindow
import io.github.hideyukimori.nenepixel.core.application.workspace.EditorAppearance
import io.github.hideyukimori.nenepixel.core.application.workspace.ToolGesture
import io.github.hideyukimori.nenepixel.core.application.workspace.palette.PaletteEditSession
import io.github.hideyukimori.nenepixel.core.application.workspace.quickselect.QuickSelection
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceUnderlay
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportState
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.drawing.DrawingTool
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId
import io.github.hideyukimori.nenepixel.core.domain.palette.Palette
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult

public class EditorRenderState internal constructor(
    public val document: DocumentState,
    public val definition: PaletteDefinition,
    public val activePaletteIndex: PaletteIndex,
    public val activeTool: DrawingTool,
    public val preview: ToolGesture?,
    public val viewport: ViewportState,
    public val canUndo: Boolean,
    public val canRedo: Boolean,
    public val dirtyState: DocumentDirtyState,
    public val appearance: EditorAppearance,
    public val actualSizeWindow: ActualSizeWindow,
    public val paletteEditSession: PaletteEditSession?,
    public val paletteNotice: PaletteEditorNotice?,
    public val quickSelection: QuickSelection,
    internal val activeLayerId: LayerId,
    internal val layerNotice: LayerNotice?,
    internal val underlay: ReferenceUnderlay?,
) {
    public val palette: Palette
        get() = definition.palette

    public val activeColor: PixelColor
        get() =
            when (val result = palette.entryAt(activePaletteIndex)) {
                is DomainValueResult.Created -> result.value.color
                is DomainValueResult.Rejected -> error("Render palette selection is invalid: ${result.rejection}")
            }

    override fun equals(other: Any?): Boolean =
        this === other || (other is EditorRenderState && fields() == other.fields())

    override fun hashCode(): Int =
        fields().fold(INITIAL_HASH) { hash, value -> hash * HASH_MULTIPLIER + (value?.hashCode() ?: 0) }

    /** Every constructor value, in declaration order; equality and hashing compare exactly these. */
    private fun fields(): List<Any?> =
        listOf(
            document,
            definition,
            activePaletteIndex,
            activeTool,
            preview,
            viewport,
            canUndo,
            canRedo,
            dirtyState,
            appearance,
            actualSizeWindow,
            paletteEditSession,
            paletteNotice,
            quickSelection,
            activeLayerId,
            layerNotice,
            underlay,
        )

    override fun toString(): String =
        "EditorRenderState(" +
            "document=$document, palette=$palette, activePaletteIndex=$activePaletteIndex, activeTool=$activeTool, " +
            "preview=$preview, viewport=$viewport, " +
            "canUndo=$canUndo, canRedo=$canRedo, dirtyState=$dirtyState, appearance=$appearance, " +
            "actualSizeWindow=$actualSizeWindow, paletteEditSession=$paletteEditSession, " +
            "paletteNotice=$paletteNotice, quickSelection=$quickSelection, activeLayerId=$activeLayerId, " +
            "layerNotice=$layerNotice, underlay=$underlay)"

    private companion object {
        const val INITIAL_HASH: Int = 1
        const val HASH_MULTIPLIER: Int = 31
    }
}
