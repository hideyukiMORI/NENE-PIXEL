package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.document.command.CommandFailure
import io.github.hideyukimori.nenepixel.core.application.document.command.RejectionReason
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceActionRejection
import io.github.hideyukimori.nenepixel.core.application.workspace.palette.PaletteDraftRejection
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueRejection
import io.github.hideyukimori.nenepixel.presentation.compose.R

/** Why the latest palette-editor request did not take effect; the palette route clears it on its next success. */
public sealed interface PaletteEditorNotice {
    /** The runtime refused the palette action or the Apply before any command. */
    public data class Rejected internal constructor(
        public val rejection: WorkspaceActionRejection,
    ) : PaletteEditorNotice

    /** The command gateway rejected the Apply command; the draft is unchanged. */
    public data class ApplyRejected internal constructor(
        public val reason: RejectionReason,
    ) : PaletteEditorNotice

    /** The command gateway failed the Apply command; the draft is unchanged. */
    public data class ApplyFailed internal constructor(
        public val failure: CommandFailure,
    ) : PaletteEditorNotice
}

/** The localized message for this notice; every case is mapped by an exhaustive `when` (design S6b 3). */
internal fun PaletteEditorNotice.noticeResource(): Int =
    when (this) {
        is PaletteEditorNotice.Rejected -> rejection.noticeResource()
        is PaletteEditorNotice.ApplyRejected -> reason.noticeResource()
        is PaletteEditorNotice.ApplyFailed -> failure.noticeResource()
    }

private fun WorkspaceActionRejection.noticeResource(): Int =
    when (this) {
        WorkspaceActionRejection.PersistenceBusy -> R.string.palette_notice_persistence_busy

        WorkspaceActionRejection.PaletteSessionActive -> R.string.palette_notice_session_active

        WorkspaceActionRejection.PaletteSessionAlreadyActive -> R.string.palette_notice_session_already_active

        WorkspaceActionRejection.NoPaletteSession -> R.string.palette_notice_no_session

        is WorkspaceActionRejection.PaletteIndexOutsidePalette -> R.string.palette_notice_index_outside

        is WorkspaceActionRejection.PaletteDraftRejected -> reason.noticeResource()

        WorkspaceActionRejection.PreviewAlreadyActive,
        WorkspaceActionRejection.NoActivePreview,
        is WorkspaceActionRejection.PreviewCanvasMismatch,
        is WorkspaceActionRejection.PreviewPositionOutsideCanvas,
        is WorkspaceActionRejection.PreviewPathAboveSupportedMaximum,
        -> R.string.palette_editor_rejected
    }

private fun PaletteDraftRejection.noticeResource(): Int =
    when (this) {
        is PaletteDraftRejection.InvalidDefinition -> rejection.definitionNoticeResource()

        is PaletteDraftRejection.OrderSizeMismatch,
        is PaletteDraftRejection.OrderIndexOutsidePalette,
        is PaletteDraftRejection.RepeatedOrderIndex,
        -> R.string.palette_notice_invalid_order

        is PaletteDraftRejection.RemovedIndexOutsidePalette,
        is PaletteDraftRejection.ReplacementIndexOutsidePalette,
        is PaletteDraftRejection.ImportSourceOutsidePalette,
        is PaletteDraftRejection.ImportDestinationOutsidePalette,
        -> R.string.palette_notice_index_outside

        PaletteDraftRejection.ReplacementIsRemovedIndex -> R.string.palette_notice_replacement_is_removed

        PaletteDraftRejection.BelowDefinitionMinimum -> R.string.palette_notice_below_minimum

        PaletteDraftRejection.NoUndoAvailable -> R.string.palette_notice_no_undo

        PaletteDraftRejection.NoRedoAvailable -> R.string.palette_notice_no_redo

        PaletteDraftRejection.StaleBase -> R.string.palette_notice_stale_base

        PaletteDraftRejection.NoPendingImport -> R.string.palette_notice_no_pending_import

        PaletteDraftRejection.ImportPending -> R.string.palette_notice_import_pending

        is PaletteDraftRejection.UnresolvedImportSources -> R.string.palette_notice_unresolved_import
    }

private fun DomainValueRejection.definitionNoticeResource(): Int =
    when (this) {
        is DomainValueRejection.PaletteAboveSupportedMaximum -> R.string.palette_notice_above_maximum
        is DomainValueRejection.PaletteBelowDefinitionMinimum -> R.string.palette_notice_below_minimum
        else -> R.string.palette_notice_invalid_definition
    }

private fun RejectionReason.noticeResource(): Int =
    when (this) {
        is RejectionReason.PaletteSourceMismatch -> R.string.palette_notice_source_mismatch

        RejectionReason.NoEffectiveChange -> R.string.palette_notice_no_change

        is RejectionReason.TargetDocumentMismatch,
        is RejectionReason.CanvasMismatch,
        is RejectionReason.RevisionMismatch,
        is RejectionReason.PixelBeforeValueMismatch,
        RejectionReason.RevisionOverflow,
        RejectionReason.NoUndoAvailable,
        RejectionReason.NoRedoAvailable,
        is RejectionReason.HistoryEntryAboveRetainedChangeMaximum,
        RejectionReason.HistoryPositionExhausted,
        RejectionReason.SourceOwnerMismatch,
        RejectionReason.SourceHistoryMismatch,
        is RejectionReason.InvalidIndexedValue,
        is RejectionReason.HistoryEntryAboveRetainedPayloadMaximum,
        -> R.string.palette_editor_apply_failed
    }

private fun CommandFailure.noticeResource(): Int =
    when (this) {
        CommandFailure.PersistenceBusy -> R.string.palette_notice_persistence_busy
        CommandFailure.PaletteSessionActive -> R.string.palette_notice_session_active
    }
