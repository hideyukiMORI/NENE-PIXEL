package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceFailure
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceLastOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceOperationHandle
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceRequestResult
import io.github.hideyukimori.nenepixel.core.application.persistence.PngImportOutcome
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceAction
import io.github.hideyukimori.nenepixel.core.application.workspace.importing.PendingRasterImport

/** Runs the PNG-import pick on the shared document-output lease (ADR 0033). */
internal class RuntimePngImportOperations(
    private val runtime: EditorRuntime,
) {
    fun beginPick(): PngImportPickStart =
        runtime.transact { transaction ->
            if (transaction.paletteSessionActive()) {
                PersistenceTransition(transaction.coordination, PngImportPickStart.PaletteSessionActive)
            } else {
                val lease = DocumentOutputTransitions.begin(transaction.coordination)
                PersistenceTransition(lease.next, lease.result.toPickStart(), lease.effect)
            }
        }

    /** The installed document's canvas size and palette definition, read under the runtime lock. */
    fun planningSource(): PngImportPlanningSource =
        runtime.read { transaction ->
            val document = transaction.documentState()
            PngImportPlanningSource(document.size, document.definition)
        }

    /**
     * Completes the pick lease. An accepted `Picked` outcome stores [pending] in the workspace inside the same
     * transaction, unless a palette edit session is active; a stale or cancelling completion leaves the workspace
     * untouched.
     */
    fun completePick(
        handle: PersistenceOperationHandle,
        outcome: PngImportOutcome,
        pending: PendingRasterImport?,
    ): PersistenceRequestResult =
        runtime.transact { transaction ->
            val completion =
                DocumentOutputTransitions.complete(transaction.coordination, handle, outcome.toLastOutcome())
            val accepted = (completion.result as? PersistenceRequestResult.Completed)?.outcome
            if (pending != null && accepted == PersistenceLastOutcome.PngImportRead &&
                !transaction.paletteSessionActive()
            ) {
                transaction.reduceWorkspace(WorkspaceAction.SetPendingRasterImport(pending))
            }
            completion
        }

    fun cancelPick(handle: PersistenceOperationHandle): PersistenceRequestResult =
        runtime.transact { transaction ->
            CancellationTransitions.completeCancellation(transaction.coordination, handle)
        }

    private fun DocumentOutputLease.toPickStart(): PngImportPickStart =
        when (this) {
            is DocumentOutputLease.Started -> {
                PngImportPickStart.Started(handle)
            }

            DocumentOutputLease.Busy -> {
                PngImportPickStart.Busy
            }

            DocumentOutputLease.RecoveryUnavailable -> {
                PngImportPickStart.RecoveryUnavailable
            }

            DocumentOutputLease.IdentityExhausted -> {
                PngImportPickStart.IdentityExhausted
            }
        }

    private fun PngImportOutcome.toLastOutcome(): PersistenceLastOutcome =
        when (this) {
            is PngImportOutcome.Picked -> {
                PersistenceLastOutcome.PngImportRead
            }

            PngImportOutcome.Cancelled -> {
                PersistenceLastOutcome.Cancelled
            }

            is PngImportOutcome.Rejected -> {
                PersistenceLastOutcome.Failed(PersistenceFailure.PngImportRejected(reason))
            }

            is PngImportOutcome.Failed -> {
                PersistenceLastOutcome.Failed(PersistenceFailure.PngImportPick(failure))
            }
        }
}
