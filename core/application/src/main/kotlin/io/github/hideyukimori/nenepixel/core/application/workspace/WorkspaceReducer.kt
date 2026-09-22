package io.github.hideyukimori.nenepixel.core.application.workspace

import io.github.hideyukimori.nenepixel.core.application.document.command.CommandSourceAdmission
import io.github.hideyukimori.nenepixel.core.domain.drawing.DrawingTool
import io.github.hideyukimori.nenepixel.core.domain.drawing.StrokeEffect
import io.github.hideyukimori.nenepixel.core.domain.palette.Palette
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelLimits
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult

public class WorkspaceReducer private constructor() {
    public fun reduce(
        state: WorkspaceState,
        action: WorkspaceAction,
        source: CommandSourceAdmission,
    ): WorkspaceReductionResult =
        when (action) {
            is WorkspaceAction.SetAppearance -> {
                setAppearance(state, action.appearance)
            }

            is WorkspaceAction.SetActualSizeWindow -> {
                setActualSizeWindow(state, action.window)
            }

            is WorkspaceAction.SelectPaletteEntry -> {
                selectPaletteEntry(
                    state,
                    action,
                    source.document.definition.palette,
                )
            }

            is WorkspaceAction.SelectTool -> {
                selectTool(state, action)
            }

            is WorkspaceAction.BeginGesturePreview -> {
                beginGesturePreview(state, action, source)
            }

            is WorkspaceAction.ExtendGesturePreview -> {
                extendGesturePreview(state, action)
            }

            WorkspaceAction.CancelGesturePreview -> {
                cancelGesturePreview(state)
            }

            WorkspaceAction.PrepareGestureCommit -> {
                prepareGestureCommit(state)
            }

            is WorkspaceAction.SetViewport -> {
                setViewport(state, action)
            }

            is ReconcileDocumentPalette -> {
                reconcileDocumentPalette(state, action, source.document.definition.palette)
            }
        }

    private fun selectPaletteEntry(
        state: WorkspaceState,
        action: WorkspaceAction.SelectPaletteEntry,
        palette: Palette,
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
        source: CommandSourceAdmission,
    ): WorkspaceReductionResult =
        when {
            state.preview != null -> {
                rejected(state, WorkspaceActionRejection.PreviewAlreadyActive)
            }

            action.canvas != source.document.size -> {
                rejected(state, WorkspaceActionRejection.PreviewCanvasMismatch(source.document.size, action.canvas))
            }

            !action.canvas.contains(action.position) -> {
                outsideCanvas(state, action.canvas, action.position)
            }

            else -> {
                val preview =
                    ToolGesture.begin(
                        action.canvas,
                        action.position,
                        state.strokeEffect(source.document.definition),
                        source,
                    )
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
                admission = state.preview.admission,
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
        public fun create(): WorkspaceReducer = WorkspaceReducer()
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

private fun WorkspaceState.strokeEffect(definition: PaletteDefinition): StrokeEffect =
    when (activeTool) {
        DrawingTool.Pencil -> StrokeEffect.Paint(activePaletteIndex)
        DrawingTool.Eraser -> StrokeEffect.Erase(definition.defaultIndex)
    }

private fun reconcileDocumentPalette(
    state: WorkspaceState,
    action: ReconcileDocumentPalette,
    palette: Palette,
): WorkspaceReductionResult =
    when (val entry = palette.entryAt(action.index)) {
        is DomainValueResult.Created -> {
            WorkspaceReductionResult.Reduced(
                state.withActivePaletteIndex(action.index).withoutPreview(),
            )
        }

        is DomainValueResult.Rejected -> {
            error("Document transition produced an invalid selection: ${entry.rejection}")
        }
    }

private fun setAppearance(
    state: WorkspaceState,
    appearance: EditorAppearance,
): WorkspaceReductionResult =
    if (state.appearance == appearance && state.preview == null) {
        WorkspaceReductionResult.Unchanged(state, WorkspaceNoChangeReason.AppearanceAlreadySet)
    } else {
        WorkspaceReductionResult.Reduced(state.withAppearance(appearance))
    }

private fun setActualSizeWindow(
    state: WorkspaceState,
    window: ActualSizeWindow,
): WorkspaceReductionResult =
    if (state.actualSizeWindow == window) {
        WorkspaceReductionResult.Unchanged(state, WorkspaceNoChangeReason.ActualSizeWindowAlreadySet)
    } else {
        WorkspaceReductionResult.Reduced(state.withActualSizeWindow(window))
    }
