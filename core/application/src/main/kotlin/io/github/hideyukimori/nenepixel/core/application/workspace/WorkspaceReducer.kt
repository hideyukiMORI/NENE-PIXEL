package io.github.hideyukimori.nenepixel.core.application.workspace

public class WorkspaceReducer private constructor() {
    public fun reduce(
        state: WorkspaceState,
        action: WorkspaceAction,
    ): WorkspaceReductionResult =
        when (action) {
            is WorkspaceAction.ChangeActiveColor -> changeActiveColor(state, action)
            is WorkspaceAction.BeginGesturePreview -> beginGesturePreview(state, action)
            is WorkspaceAction.ExtendGesturePreview -> extendGesturePreview(state, action)
            WorkspaceAction.CancelGesturePreview -> cancelGesturePreview(state)
            WorkspaceAction.PrepareGestureCommit -> prepareGestureCommit(state)
        }

    private fun changeActiveColor(
        state: WorkspaceState,
        action: WorkspaceAction.ChangeActiveColor,
    ): WorkspaceReductionResult =
        if (action.color == state.activeColor) {
            unchanged(state, WorkspaceNoChangeReason.ActiveColorAlreadySelected)
        } else {
            WorkspaceReductionResult.Reduced(state.withActiveColor(action.color))
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
                val preview = ToolGesture.begin(action.canvas, action.position, state.activeColor)
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

            action.position == preview.lastPosition -> {
                unchanged(state, WorkspaceNoChangeReason.DuplicatePreviewSample)
            }

            else -> {
                WorkspaceReductionResult.Reduced(state.withPreview(preview.extend(action.position)))
            }
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
