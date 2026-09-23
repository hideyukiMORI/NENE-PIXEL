package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.persistence.PaletteJsonExportOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PaletteJsonImportOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceFailure
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceLastOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceOperationHandle
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceRequestResult
import io.github.hideyukimori.nenepixel.core.application.workspace.BeginPaletteEdit
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceAction
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition

internal class RuntimePaletteJsonOperations(
    private val runtime: EditorRuntime,
) {
    fun begin(): PaletteJsonExportStart =
        runtime.transact { transaction ->
            val lease = DocumentOutputTransitions.begin(transaction.coordination)
            val start =
                when (val result = lease.result) {
                    is DocumentOutputLease.Started -> {
                        val definition =
                            transaction.workspaceState().paletteEditSession?.draft
                                ?: transaction.documentState().definition
                        PaletteJsonExportStart.Started(result.handle, definition)
                    }

                    DocumentOutputLease.Busy -> {
                        PaletteJsonExportStart.Busy
                    }

                    DocumentOutputLease.RecoveryUnavailable -> {
                        PaletteJsonExportStart.RecoveryUnavailable
                    }

                    DocumentOutputLease.IdentityExhausted -> {
                        PaletteJsonExportStart.IdentityExhausted
                    }
                }
            PersistenceTransition(lease.next, start, lease.effect)
        }

    fun complete(
        handle: PersistenceOperationHandle,
        outcome: PaletteJsonExportOutcome,
    ): PersistenceRequestResult =
        runtime.transact { transaction ->
            DocumentOutputTransitions.complete(transaction.coordination, handle, outcome.toLastOutcome())
        }

    fun cancel(handle: PersistenceOperationHandle): PersistenceRequestResult =
        runtime.transact { transaction ->
            CancellationTransitions.completeCancellation(transaction.coordination, handle)
        }

    fun beginImport(): PaletteJsonImportStart =
        runtime.transact { transaction ->
            val lease = DocumentOutputTransitions.begin(transaction.coordination)
            val start =
                when (val result = lease.result) {
                    is DocumentOutputLease.Started -> {
                        PaletteJsonImportStart.Started(result.handle)
                    }

                    DocumentOutputLease.Busy -> {
                        PaletteJsonImportStart.Busy
                    }

                    DocumentOutputLease.RecoveryUnavailable -> {
                        PaletteJsonImportStart.RecoveryUnavailable
                    }

                    DocumentOutputLease.IdentityExhausted -> {
                        PaletteJsonImportStart.IdentityExhausted
                    }
                }
            PersistenceTransition(lease.next, start, lease.effect)
        }

    /**
     * Completes the import lease. An accepted `Imported` outcome opens the palette session when none is
     * open and stages the definition as its pending import, all inside the same transaction; a stale or
     * cancelling completion leaves the workspace untouched.
     */
    fun completeImport(
        handle: PersistenceOperationHandle,
        outcome: PaletteJsonImportOutcome,
    ): PersistenceRequestResult =
        runtime.transact { transaction ->
            val completion =
                DocumentOutputTransitions.complete(transaction.coordination, handle, outcome.toLastOutcome())
            val accepted = (completion.result as? PersistenceRequestResult.Completed)?.outcome
            val staged = outcome as? PaletteJsonImportOutcome.Imported
            if (staged != null && accepted == PersistenceLastOutcome.PaletteJsonImported) {
                stageImport(transaction, staged.definition)
            }
            completion
        }

    fun cancelImport(handle: PersistenceOperationHandle): PersistenceRequestResult = cancel(handle)

    private fun stageImport(
        transaction: EditorRuntime.RuntimeTransaction,
        definition: PaletteDefinition,
    ) {
        if (!transaction.paletteSessionActive()) {
            transaction.reduceWorkspace(
                BeginPaletteEdit(
                    transaction.switchContext().source,
                    transaction.documentState().definition,
                ),
            )
        }
        transaction.reduceWorkspace(WorkspaceAction.ImportPaletteDraft(definition))
    }

    private fun PaletteJsonImportOutcome.toLastOutcome(): PersistenceLastOutcome =
        when (this) {
            is PaletteJsonImportOutcome.Imported -> {
                PersistenceLastOutcome.PaletteJsonImported
            }

            PaletteJsonImportOutcome.Cancelled -> {
                PersistenceLastOutcome.Cancelled
            }

            is PaletteJsonImportOutcome.Failed -> {
                PersistenceLastOutcome.Failed(PersistenceFailure.PaletteJsonImport(failure))
            }
        }

    private fun PaletteJsonExportOutcome.toLastOutcome(): PersistenceLastOutcome =
        when (this) {
            PaletteJsonExportOutcome.Exported -> {
                PersistenceLastOutcome.PaletteJsonExported
            }

            PaletteJsonExportOutcome.Cancelled -> {
                PersistenceLastOutcome.Cancelled
            }

            is PaletteJsonExportOutcome.Failed -> {
                PersistenceLastOutcome.Failed(PersistenceFailure.PaletteJsonExport(failure, cleanup))
            }
        }
}
