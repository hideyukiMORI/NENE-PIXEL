package io.github.hideyukimori.nenepixel.core.application.workspace

import io.github.hideyukimori.nenepixel.core.application.document.command.CommandSourceAdmission
import io.github.hideyukimori.nenepixel.core.application.workspace.quickselect.EyedropperState
import io.github.hideyukimori.nenepixel.core.domain.drawing.DrawingTool
import io.github.hideyukimori.nenepixel.core.domain.drawing.StrokeEffect
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelLimits

/** Reduces the gesture preview actions; only a prepared commit hands a stroke to the document command path. */
internal fun reduceGesturePreview(
    state: WorkspaceState,
    action: WorkspaceAction.GesturePreviewAction,
    source: CommandSourceAdmission,
): WorkspaceReductionResult =
    when (action) {
        is WorkspaceAction.BeginGesturePreview -> beginGesturePreview(state, action, source)
        is WorkspaceAction.ExtendGesturePreview -> extendGesturePreview(state, action)
        WorkspaceAction.CancelGesturePreview -> cancelGesturePreview(state)
        WorkspaceAction.PrepareGestureCommit -> prepareGestureCommit(state)
    }

private fun beginGesturePreview(
    state: WorkspaceState,
    action: WorkspaceAction.BeginGesturePreview,
    source: CommandSourceAdmission,
): WorkspaceReductionResult =
    when {
        state.preview != null -> {
            WorkspaceReductionResult.Rejected(state, WorkspaceActionRejection.PreviewAlreadyActive)
        }

        state.quickSelection.menu != null -> {
            WorkspaceReductionResult.Rejected(state, WorkspaceActionRejection.QuickSelectMenuOpen)
        }

        state.quickSelection.eyedropper == EyedropperState.Armed -> {
            WorkspaceReductionResult.Rejected(state, WorkspaceActionRejection.EyedropperArmed)
        }

        source.document.isLayerHidden(state.activeLayerId) -> {
            WorkspaceReductionResult.Rejected(state, WorkspaceActionRejection.ActiveLayerHidden(state.activeLayerId))
        }

        action.canvas != source.document.size -> {
            WorkspaceReductionResult.Rejected(
                state,
                WorkspaceActionRejection.PreviewCanvasMismatch(source.document.size, action.canvas),
            )
        }

        !action.canvas.contains(action.position) -> {
            outsideCanvas(state, action.canvas, action.position)
        }

        else -> {
            val preview =
                ToolGesture.begin(
                    action.canvas,
                    action.position,
                    state.strokeEffect(),
                    state.activeLayerId,
                    source,
                )
            WorkspaceReductionResult.Reduced(state.withPreview(preview))
        }
    }

private fun extendGesturePreview(
    state: WorkspaceState,
    action: WorkspaceAction.ExtendGesturePreview,
): WorkspaceReductionResult {
    val preview =
        state.preview ?: return WorkspaceReductionResult.Rejected(state, WorkspaceActionRejection.NoActivePreview)
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
    position: PixelPosition,
): WorkspaceReductionResult =
    when (val result = preview.extend(position)) {
        is ToolGestureExtensionResult.Extended -> {
            WorkspaceReductionResult.Reduced(state.withPreview(result.gesture))
        }

        ToolGestureExtensionResult.Duplicate -> {
            WorkspaceReductionResult.Unchanged(state, WorkspaceNoChangeReason.DuplicatePreviewSample)
        }

        is ToolGestureExtensionResult.AboveSupportedMaximum -> {
            WorkspaceReductionResult.Rejected(
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
        WorkspaceReductionResult.Rejected(state, WorkspaceActionRejection.NoActivePreview)
    } else {
        WorkspaceReductionResult.Reduced(state.withPreview(null))
    }

private fun prepareGestureCommit(state: WorkspaceState): WorkspaceReductionResult =
    if (state.preview == null) {
        WorkspaceReductionResult.Rejected(state, WorkspaceActionRejection.NoActivePreview)
    } else {
        WorkspaceReductionResult.CommitPrepared(
            nextState = state.withPreview(null).recordingStroke(state.preview.effect),
            stroke = state.preview.prepareStroke(),
            layerId = state.preview.layerId,
            admission = state.preview.admission,
        )
    }

private fun outsideCanvas(
    state: WorkspaceState,
    canvas: CanvasSize,
    position: PixelPosition,
): WorkspaceReductionResult =
    WorkspaceReductionResult.Rejected(
        state,
        WorkspaceActionRejection.PreviewPositionOutsideCanvas(canvas, position),
    )

private fun WorkspaceState.strokeEffect(): StrokeEffect =
    when (activeTool) {
        DrawingTool.Pencil -> StrokeEffect.Paint(activePaletteIndex)
        DrawingTool.Eraser -> StrokeEffect.Erase
    }

/** A committed paint stroke records its slot as recently used; erase does not (ADR 0029). */
private fun WorkspaceState.recordingStroke(effect: StrokeEffect): WorkspaceState =
    when (effect) {
        is StrokeEffect.Paint -> withQuickSelection(quickSelection.recordPainted(effect.targetIndex))
        StrokeEffect.Erase -> this
    }
