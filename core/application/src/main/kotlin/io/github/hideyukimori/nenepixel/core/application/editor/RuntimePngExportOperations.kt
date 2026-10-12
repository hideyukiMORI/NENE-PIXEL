package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceFailure
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceLastOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceOperationHandle
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceRequestResult
import io.github.hideyukimori.nenepixel.core.application.persistence.PngExportCapture
import io.github.hideyukimori.nenepixel.core.application.persistence.PngExportOutcome
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerVisibility

internal class RuntimePngExportOperations(
    private val runtime: EditorRuntime,
) {
    /**
     * Starts a PNG export unless the document has no visible layer. The visibility check runs in the same
     * transaction before any lease is taken, so a refused export leaves coordination untouched (ADR 0030).
     */
    fun begin(): PngExportStart =
        runtime.transact { transaction ->
            val capture =
                PngExportCapture.capture(
                    transaction.documentState(),
                    transaction.workspaceState().activeFrameId,
                )
            if (capture.frame.layers.none { it.visibility == LayerVisibility.Visible }) {
                PersistenceTransition(transaction.coordination, PngExportStart.NoVisibleLayer)
            } else {
                beginOutput(transaction, capture)
            }
        }

    private fun beginOutput(
        transaction: EditorRuntime.RuntimeTransaction,
        capture: PngExportCapture,
    ): PersistenceTransition<PngExportStart> {
        val lease = DocumentOutputTransitions.begin(transaction.coordination)
        val start =
            when (val result = lease.result) {
                is DocumentOutputLease.Started -> {
                    PngExportStart.Started(result.handle, capture)
                }

                DocumentOutputLease.Busy -> {
                    PngExportStart.Busy
                }

                DocumentOutputLease.RecoveryUnavailable -> {
                    PngExportStart.RecoveryUnavailable
                }

                DocumentOutputLease.IdentityExhausted -> {
                    PngExportStart.IdentityExhausted
                }
            }
        return PersistenceTransition(lease.next, start, lease.effect)
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

internal sealed interface PngExportStart {
    data class Started(
        val handle: PersistenceOperationHandle,
        val capture: PngExportCapture,
    ) : PngExportStart

    data object Busy : PngExportStart

    data object RecoveryUnavailable : PngExportStart

    data object IdentityExhausted : PngExportStart

    data object NoVisibleLayer : PngExportStart
}
