package io.github.hideyukimori.nenepixel.core.application.workspace

import io.github.hideyukimori.nenepixel.core.domain.drawing.DrawingTool
import io.github.hideyukimori.nenepixel.core.domain.drawing.StrokeEffect
import io.github.hideyukimori.nenepixel.core.domain.palette.Palette
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteEntry
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelLimits
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult

public class WorkspaceReducer private constructor(
    private val palette: Palette,
) {
    public fun reduce(
        state: WorkspaceState,
        action: WorkspaceAction,
    ): WorkspaceReductionResult =
        when (action) {
            is WorkspaceAction.SelectPaletteEntry -> selectPaletteEntry(state, action)
            is WorkspaceAction.SelectTool -> selectTool(state, action)
            is WorkspaceAction.BeginGesturePreview -> beginGesturePreview(state, action)
            is WorkspaceAction.ExtendGesturePreview -> extendGesturePreview(state, action)
            WorkspaceAction.CancelGesturePreview -> cancelGesturePreview(state)
            WorkspaceAction.PrepareGestureCommit -> prepareGestureCommit(state)
            is WorkspaceAction.SetViewport -> setViewport(state, action)
        }

    private fun selectPaletteEntry(
        state: WorkspaceState,
        action: WorkspaceAction.SelectPaletteEntry,
    ): WorkspaceReductionResult =
        when (palette.entryAt(action.index)) {
            is DomainValueResult.Rejected -> {
                rejected(
                    state,
                    WorkspaceActionRejection.PaletteIndexOutsidePalette(action.index, palette.entryCount),
                )
            }

            is DomainValueResult.Created -> {
                if (action.index == state.activePaletteIndex) {
                    unchanged(state, WorkspaceNoChangeReason.ActivePaletteEntryAlreadySelected)
                } else {
                    WorkspaceReductionResult.Reduced(state.withActivePaletteIndex(action.index))
                }
            }
        }

    private fun beginGesturePreview(
        state: WorkspaceState,
        action: WorkspaceAction.BeginGesturePreview,
    ): WorkspaceReductionResult =
        when {
            state.preview != null -> {
                rejected(state, WorkspaceActionRejection.PreviewAlreadyActive)
            }

            !action.canvas.contains(action.position) -> {
                outsideCanvas(state, action.canvas, action.position)
            }

            else -> {
                val preview = ToolGesture.begin(action.canvas, action.position, state.strokeEffect(palette))
                WorkspaceReductionResult.Reduced(state.withPreview(preview))
            }
        }

    private fun extendGesturePreview(
        state: WorkspaceState,
        action: WorkspaceAction.ExtendGesturePreview,
    ): WorkspaceReductionResult {
        val preview = state.preview ?: return rejected(state, WorkspaceActionRejection.NoActivePreview)
        return when {
            !preview.canvas.contains(action.position) -> {
                outsideCanvas(state, preview.canvas, action.position)
            }

            else -> {
                extendGesturePreview(state, preview, action.position)
            }
        }
    }

    private fun extendGesturePreview(
        state: WorkspaceState,
        preview: ToolGesture,
        position: io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition,
    ): WorkspaceReductionResult =
        when (val result = preview.extend(position)) {
            is ToolGestureExtensionResult.Extended -> {
                WorkspaceReductionResult.Reduced(state.withPreview(result.gesture))
            }

            ToolGestureExtensionResult.Duplicate -> {
                unchanged(state, WorkspaceNoChangeReason.DuplicatePreviewSample)
            }

            is ToolGestureExtensionResult.AboveSupportedMaximum -> {
                rejected(
                    state,
                    WorkspaceActionRejection.PreviewPathAboveSupportedMaximum(
                        result.attemptedCount,
                        PixelLimits.MAX_RAW_STROKE_POSITIONS,
                    ),
                )
            }
        }

    private fun cancelGesturePreview(state: WorkspaceState): WorkspaceReductionResult =
        if (state.preview == null) {
            rejected(state, WorkspaceActionRejection.NoActivePreview)
        } else {
            WorkspaceReductionResult.Reduced(state.withoutPreview())
        }

    private fun prepareGestureCommit(state: WorkspaceState): WorkspaceReductionResult =
        if (state.preview == null) {
            rejected(state, WorkspaceActionRejection.NoActivePreview)
        } else {
            WorkspaceReductionResult.CommitPrepared(
                nextState = state.withoutPreview(),
                stroke = state.preview.prepareStroke(),
            )
        }

    private fun setViewport(
        state: WorkspaceState,
        action: WorkspaceAction.SetViewport,
    ): WorkspaceReductionResult =
        if (action.viewport == state.viewport && state.preview == null) {
            unchanged(state, WorkspaceNoChangeReason.ViewportAlreadySet)
        } else {
            WorkspaceReductionResult.Reduced(state.withViewport(action.viewport))
        }

    private fun outsideCanvas(
        state: WorkspaceState,
        canvas: io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize,
        position: io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition,
    ): WorkspaceReductionResult =
        rejected(
            state,
            WorkspaceActionRejection.PreviewPositionOutsideCanvas(canvas, position),
        )

    private fun unchanged(
        state: WorkspaceState,
        reason: WorkspaceNoChangeReason,
    ): WorkspaceReductionResult = WorkspaceReductionResult.Unchanged(state, reason)

    private fun rejected(
        state: WorkspaceState,
        rejection: WorkspaceActionRejection,
    ): WorkspaceReductionResult = WorkspaceReductionResult.Rejected(state, rejection)

    public companion object {
        public fun create(palette: Palette): WorkspaceReducer = WorkspaceReducer(palette)
    }
}

private fun selectTool(
    state: WorkspaceState,
    action: WorkspaceAction.SelectTool,
): WorkspaceReductionResult =
    if (action.tool == state.activeTool) {
        WorkspaceReductionResult.Unchanged(state, WorkspaceNoChangeReason.ActiveToolAlreadySelected)
    } else {
        WorkspaceReductionResult.Reduced(state.withActiveTool(action.tool))
    }

private fun WorkspaceState.strokeEffect(palette: Palette): StrokeEffect =
    when (activeTool) {
        DrawingTool.Pencil -> StrokeEffect.Paint(palette.selectedEntry(this).color)
        DrawingTool.Eraser -> StrokeEffect.Erase
    }

private fun Palette.selectedEntry(state: WorkspaceState): PaletteEntry =
    when (val result = entryAt(state.activePaletteIndex)) {
        is DomainValueResult.Created -> result.value
        is DomainValueResult.Rejected -> error("Workspace palette selection is invalid: ${result.rejection}")
    }
