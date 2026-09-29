package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResult
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceReductionResult

/**
 * What one [EditorLayerCallbacks] call produced: the published render state plus the untouched application result,
 * so a refusal reaches the caller instead of being dropped (the layer notice mapping reads it).
 */
internal sealed interface EditorLayerOutcome {
    val renderState: EditorRenderState

    /** A `DocumentCommand` ran; [result] is what `EditorRuntime.execute` returned. */
    data class Executed(
        override val renderState: EditorRenderState,
        val result: CommandResult,
    ) : EditorLayerOutcome

    /** A `WorkspaceAction` was reduced; [result] is what `EditorRuntime.reduce` returned. */
    data class Reduced(
        override val renderState: EditorRenderState,
        val result: WorkspaceReductionResult,
    ) : EditorLayerOutcome
}
