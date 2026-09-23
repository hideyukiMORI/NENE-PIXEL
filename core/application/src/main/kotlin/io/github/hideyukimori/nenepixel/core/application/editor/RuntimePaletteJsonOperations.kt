package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.persistence.PaletteJsonExportOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceFailure
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceLastOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceOperationHandle
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceRequestResult

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
