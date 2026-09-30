package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.document.command.CommandFailure
import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResult
import io.github.hideyukimori.nenepixel.core.application.document.command.RejectionReason
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceActionRejection

/**
 * The notice a layer command's [result] raises (#144 U6): none when it applied or changed nothing,
 * [LayerNotice.Kind.Busy] while saving or loading holds the document, and [LayerNotice.Kind.Failed] for every other
 * refusal or failure. A successful deletion's notice is chosen by the caller, which knows the command.
 */
internal fun layerNoticeKindOf(result: CommandResult): LayerNotice.Kind? =
    when (result) {
        is CommandResult.Applied -> null
        is CommandResult.Rejected -> layerNoticeKindOf(result.reason)
        is CommandResult.Failed -> layerNoticeKindOf(result.failure)
    }

/** The notice a layer command's refusal raises: none for [RejectionReason.NoEffectiveChange], otherwise a failure. */
internal fun layerNoticeKindOf(reason: RejectionReason): LayerNotice.Kind? =
    if (reason == RejectionReason.NoEffectiveChange) null else LayerNotice.Kind.Failed

/** The notice a layer command's failure raises: busy while saving or loading, otherwise a failure. */
internal fun layerNoticeKindOf(failure: CommandFailure): LayerNotice.Kind =
    if (failure == CommandFailure.PersistenceBusy) LayerNotice.Kind.Busy else LayerNotice.Kind.Failed

/**
 * The notice a refused workspace action raises: busy while saving or loading, a hidden target when drawing or the
 * eyedropper met a hidden active layer, otherwise a failure.
 */
internal fun layerNoticeKindOf(rejection: WorkspaceActionRejection): LayerNotice.Kind =
    when (rejection) {
        WorkspaceActionRejection.PersistenceBusy -> LayerNotice.Kind.Busy
        is WorkspaceActionRejection.ActiveLayerHidden -> LayerNotice.Kind.HiddenTarget
        else -> LayerNotice.Kind.Failed
    }
