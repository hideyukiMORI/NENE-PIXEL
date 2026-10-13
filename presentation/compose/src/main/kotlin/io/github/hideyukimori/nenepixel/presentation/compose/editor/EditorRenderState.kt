package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.editor.DocumentDirtyState
import io.github.hideyukimori.nenepixel.core.application.editor.EditorRuntimeState
import io.github.hideyukimori.nenepixel.core.application.workspace.ActualSizeWindow
import io.github.hideyukimori.nenepixel.core.application.workspace.EditorAppearance
import io.github.hideyukimori.nenepixel.core.application.workspace.ToolGesture
import io.github.hideyukimori.nenepixel.core.application.workspace.importing.PendingRasterImport
import io.github.hideyukimori.nenepixel.core.application.workspace.palette.PaletteEditSession
import io.github.hideyukimori.nenepixel.core.application.workspace.quickselect.QuickSelection
import io.github.hideyukimori.nenepixel.core.application.workspace.selection.SelectionSession
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceUnderlay
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportState
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.drawing.DrawingTool
import io.github.hideyukimori.nenepixel.core.domain.frame.FrameId
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId
import io.github.hideyukimori.nenepixel.core.domain.palette.Palette
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult

public class EditorRenderState internal constructor(
    state: EditorRuntimeState,
    notices: EditorRuntimeAdapter,
) {
    public val document: DocumentState = state.documentState
    public val definition: PaletteDefinition = state.documentState.definition
    public val activePaletteIndex: PaletteIndex = state.workspaceState.activePaletteIndex
    public val activeTool: DrawingTool = state.workspaceState.activeTool
    public val preview: ToolGesture? = state.workspaceState.preview
    public val viewport: ViewportState = state.workspaceState.viewport
    public val canUndo: Boolean = state.historyAvailability.canUndo
    public val canRedo: Boolean = state.historyAvailability.canRedo
    public val dirtyState: DocumentDirtyState = state.dirtyState
    public val appearance: EditorAppearance = state.workspaceState.appearance
    public val actualSizeWindow: ActualSizeWindow = state.workspaceState.actualSizeWindow
    public val paletteEditSession: PaletteEditSession? = state.workspaceState.paletteEditSession
    public val paletteNotice: PaletteEditorNotice? = notices.paletteNotice
    public val quickSelection: QuickSelection = state.workspaceState.quickSelection
    internal val activeLayerId: LayerId = state.workspaceState.activeLayerId
    internal val layerNotice: LayerNotice? = notices.layerNotices.validFor(document, activeLayerId)
    internal val underlay: ReferenceUnderlay? = state.workspaceState.underlay
    internal val pendingImport: PendingRasterImport? = state.workspaceState.pendingImport
    internal val selectionSession: SelectionSession? = state.workspaceState.selectionSession
    internal val selectionNotice: SelectionEditorNotice? = notices.selectionNotice.takeIf { selectionSession != null }
    internal val frames: EditorFrameProjection =
        EditorFrameProjection(state.workspaceState.activeFrameId, state.workspaceState.playback.view)
    internal val frameNotice: FrameEditorNotice? = notices.frameNotice
    internal val importNotice: FrameEditorNotice? =
        notices.importNotice.takeIf { notices.importNoticeSource === pendingImport }
    internal val activeFrameId: FrameId get() = frames.editingFrameId
    internal val displayedFrameId: FrameId get() = frames.displayedFrameId
    internal val playback get() = frames.playback
    internal val picture: FrameImageInputs =
        FrameImageInputs(document, displayedFrameId, definition, paletteEditSession)
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

    /** Every eager projected value, in original declaration order; equality and hashing compare exactly these. */
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
            pendingImport,
            selectionSession,
            selectionNotice,
            frames,
            frameNotice,
            importNotice,
        )

    override fun toString(): String =
        "EditorRenderState(" +
            "document=$document, palette=$palette, activePaletteIndex=$activePaletteIndex, activeTool=$activeTool, " +
            "preview=$preview, viewport=$viewport, " +
            "canUndo=$canUndo, canRedo=$canRedo, dirtyState=$dirtyState, appearance=$appearance, " +
            "actualSizeWindow=$actualSizeWindow, paletteEditSession=$paletteEditSession, " +
            "paletteNotice=$paletteNotice, quickSelection=$quickSelection, activeLayerId=$activeLayerId, " +
            "layerNotice=$layerNotice, underlay=$underlay, " +
            "pendingImport=$pendingImport, selectionSession=$selectionSession, selectionNotice=$selectionNotice, " +
            "activeFrameId=$activeFrameId, playback=$playback, frameNotice=$frameNotice, importNotice=$importNotice)"

    private companion object {
        const val INITIAL_HASH: Int = 1
        const val HASH_MULTIPLIER: Int = 31
    }
}
