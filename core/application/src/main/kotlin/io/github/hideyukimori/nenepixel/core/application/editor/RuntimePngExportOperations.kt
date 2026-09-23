package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceFailure
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceLastOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceOperationHandle
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceRequestResult
import io.github.hideyukimori.nenepixel.core.application.persistence.PngExportOutcome

internal class RuntimePngExportOperations(
    private val runtime: EditorRuntime,
) {
    fun begin(): DocumentOutputStart =
        runtime.transact { transaction ->
            val lease = DocumentOutputTransitions.begin(transaction.coordination)
            val start =
                when (val result = lease.result) {
                    is DocumentOutputLease.Started -> {
                        DocumentOutputStart.Started(result.handle, transaction.documentState())
                    }

                    DocumentOutputLease.Busy -> {
                        DocumentOutputStart.Busy
                    }

                    DocumentOutputLease.RecoveryUnavailable -> {
                        DocumentOutputStart.RecoveryUnavailable
                    }

                    DocumentOutputLease.IdentityExhausted -> {
                        DocumentOutputStart.IdentityExhausted
                    }
                }
            PersistenceTransition(lease.next, start, lease.effect)
        }

    fun complete(
        handle: PersistenceOperationHandle,
        outcome: PngExportOutcome,
    ): PersistenceRequestResult =
        runtime.transact { transaction ->
            DocumentOutputTransitions.complete(transaction.coordination, handle, outcome.toLastOutcome())
        }

    fun cancel(handle: PersistenceOperationHandle): PersistenceRequestResult =
        runtime.transact { transaction ->
            CancellationTransitions.completeCancellation(transaction.coordination, handle)
        }

    private fun PngExportOutcome.toLastOutcome(): PersistenceLastOutcome =
        when (this) {
            PngExportOutcome.Exported -> PersistenceLastOutcome.PngExported
            PngExportOutcome.Cancelled -> PersistenceLastOutcome.Cancelled
            is PngExportOutcome.Failed -> PersistenceLastOutcome.Failed(PersistenceFailure.PngExport(failure, cleanup))
        }
}
